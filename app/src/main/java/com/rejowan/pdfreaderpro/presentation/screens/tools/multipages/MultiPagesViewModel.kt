package com.rejowan.pdfreaderpro.presentation.screens.tools.multipages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.min

enum class PageOrientation { PORTRAIT, LANDSCAPE }

enum class PagesPerSheet(
    val count: Int,
    val label: String,
    val portraitCols: Int,
    val portraitRows: Int
) {
    TWO(2, "2 pages per sheet", 1, 2),
    FOUR(4, "4 pages per sheet", 2, 2),
    SIX(6, "6 pages per sheet", 2, 3),
    NINE(9, "9 pages per sheet", 3, 3);

    fun cols(orientation: PageOrientation): Int =
        if (orientation == PageOrientation.LANDSCAPE) portraitRows else portraitCols

    fun rows(orientation: PageOrientation): Int =
        if (orientation == PageOrientation.LANDSCAPE) portraitCols else portraitRows
}

data class MultiPagesSourceFile(
    val uri: Uri?,
    val path: String,
    val name: String,
    val size: Long,
    val pageCount: Int
)

data class MultiPagesResult(
    val outputPath: String,
    val outputPageCount: Int,
    val fileSize: Long
)

data class MultiPagesState(
    val sourceFile: MultiPagesSourceFile? = null,
    val pagesPerSheet: PagesPerSheet = PagesPerSheet.FOUR,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val withBorder: Boolean = false,
    val outerMarginMm: Float = 15f,
    val innerMarginMm: Float = 5f,
    val outputFileName: String = "",
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val result: MultiPagesResult? = null
)

class MultiPagesViewModel(
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(MultiPagesState())
    val state: StateFlow<MultiPagesState> = _state.asStateFlow()

    fun setSourceFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val tempPath = copyUriToCache(uri) ?: run {
                    _state.update { it.copy(error = "Failed to load file") }
                    return@launch
                }
                val file = File(tempPath)
                val pageCount = getPdfPageCount(tempPath)
                _state.update {
                    it.copy(
                        sourceFile = MultiPagesSourceFile(
                            uri = uri,
                            path = tempPath,
                            name = getFileNameFromUri(uri) ?: file.name,
                            size = file.length(),
                            pageCount = pageCount
                        ),
                        error = null,
                        result = null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set source file")
                _state.update { it.copy(error = e.message ?: "Failed to load file") }
            }
        }
    }

    fun setPagesPerSheet(pps: PagesPerSheet) { _state.update { it.copy(pagesPerSheet = pps, result = null) } }
    fun setOrientation(o: PageOrientation)   { _state.update { it.copy(orientation = o, result = null) } }
    fun setWithBorder(v: Boolean)            { _state.update { it.copy(withBorder = v, result = null) } }
    fun setOuterMarginMm(v: Float)           { _state.update { it.copy(outerMarginMm = v, result = null) } }
    fun setInnerMarginMm(v: Float)           { _state.update { it.copy(innerMarginMm = v, result = null) } }
    fun setOutputFileName(v: String)         { _state.update { it.copy(outputFileName = v, result = null) } }

    fun process() {
        val sf = _state.value.sourceFile ?: run {
            _state.update { it.copy(error = "No PDF file selected") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, progress = 0f, error = null, result = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { buildMultiPagePdf(sf) }
            }
            result.fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(isProcessing = false, progress = 1f,
                            progressLabel = "Done!", result = res)
                    }
                },
                onFailure = { e ->
                    Timber.e(e, "MultiPages failed")
                    _state.update {
                        it.copy(isProcessing = false,
                            error = e.message ?: "Processing failed")
                    }
                }
            )
        }
    }

    private suspend fun buildMultiPagePdf(sf: MultiPagesSourceFile): MultiPagesResult {
        val pps          = _state.value.pagesPerSheet
        val orientation  = _state.value.orientation
        val withBorder   = _state.value.withBorder
        val outerMm      = _state.value.outerMarginMm
        val innerMm      = _state.value.innerMarginMm
        val ptPerMm      = 2.8346f

        val a4Short = 595.28f
        val a4Long  = 841.89f
        val pageW = if (orientation == PageOrientation.LANDSCAPE) a4Long else a4Short
        val pageH = if (orientation == PageOrientation.LANDSCAPE) a4Short else a4Long

        val cols = pps.cols(orientation)
        val rows = pps.rows(orientation)

        val outerPt = outerMm * ptPerMm
        val innerPt = innerMm * ptPerMm

        // Available content area after outer margins
        val contentW = pageW - outerPt * 2
        val contentH = pageH - outerPt * 2

        // Cell size: content area minus inner gaps between cells
        val cellW = (contentW - innerPt * (cols - 1)) / cols
        val cellH = (contentH - innerPt * (rows - 1)) / rows

        val tempFile = File(sf.path)
        val parcelFd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(parcelFd)
        val totalSrc = renderer.pageCount

        val outputPath = buildOutputPath(sf.name, _state.value.outputFileName)
        val outDoc     = PdfDocument(PdfWriter(FileOutputStream(outputPath)))

        var srcIdx   = 0
        val totalOut = ceil(totalSrc.toDouble() / pps.count).toInt()

        while (srcIdx < totalSrc) {
            val sheetNum = srcIdx / pps.count + 1
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        progress = srcIdx.toFloat() / totalSrc,
                        progressLabel = "Building sheet $sheetNum of $totalOut…"
                    )
                }
            }

            val outPage   = outDoc.addNewPage(PageSize(pageW, pageH))
            val pdfCanvas = PdfCanvas(outPage)

            // White background
            pdfCanvas.setFillColor(ColorConstants.WHITE)
            pdfCanvas.rectangle(0.0, 0.0, pageW.toDouble(), pageH.toDouble())
            pdfCanvas.fill()

            for (slot in 0 until pps.count) {
                if (srcIdx >= totalSrc) break

                val col = slot % cols
                val row = slot / cols

                // Cell origin (bottom-left PDF coords)
                val cellX = outerPt + col * (cellW + innerPt)
                val cellY = pageH - outerPt - (row + 1) * cellH - row * innerPt

                val srcPage = renderer.openPage(srcIdx)
                val srcPxW  = srcPage.width
                val srcPxH  = srcPage.height

                val srcAspect  = srcPxW.toFloat() / srcPxH.toFloat()
                val cellAspect = cellW / cellH

                val drawW: Float
                val drawH: Float
                if (srcAspect > cellAspect) {
                    drawW = cellW; drawH = cellW / srcAspect
                } else {
                    drawH = cellH; drawW = cellH * srcAspect
                }

                val drawX = cellX + (cellW - drawW) / 2f
                val drawY = cellY + (cellH - drawH) / 2f

                // 200 DPI — sharp text at cell scale, avoids pixelation
                val dpi      = 200f
                val ptToPx   = dpi / 72f
                val targetW  = (drawW * ptToPx).toInt().coerceAtLeast(1)
                val targetH  = (drawH * ptToPx).toInt().coerceAtLeast(1)
                // Cap at 2048px — prevents OOM on large landscape source pages
                val maxPx    = 2048
                val capScale = min(maxPx.toFloat() / maxOf(targetW, targetH), 1f)
                val bmpW     = (targetW * capScale).toInt().coerceAtLeast(1)
                val bmpH     = (targetH * capScale).toInt().coerceAtLeast(1)

                // ARGB_8888 — required for clean text rendering, no banding
                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val cvs    = Canvas(bitmap)
                cvs.drawColor(Color.WHITE)
                srcPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                srcPage.close()

                val bos = ByteArrayOutputStream()
                // PNG for crisp text — no JPEG compression artifacts on small text at cell scale
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
                bitmap.recycle()

                val imgData = ImageDataFactory.create(bos.toByteArray())
                bos.reset() // release byte buffer memory immediately
                pdfCanvas.addImageFittedIntoRectangle(
                    imgData,
                    Rectangle(drawX, drawY, drawW, drawH),
                    false
                )

                // Draw black border outline around page image if enabled
                if (withBorder) {
                    pdfCanvas.setStrokeColor(ColorConstants.BLACK)
                    pdfCanvas.setLineWidth(0.75f)
                    pdfCanvas.rectangle(
                        drawX.toDouble(), drawY.toDouble(),
                        drawW.toDouble(), drawH.toDouble()
                    )
                    pdfCanvas.stroke()
                }

                srcIdx++
            }

            pdfCanvas.release()
        }

        renderer.close()
        parcelFd.close()
        outDoc.close()

        MediaScannerConnection.scanFile(
            context, arrayOf(outputPath), arrayOf("application/pdf"), null
        )

        val outFile = File(outputPath)
        return MultiPagesResult(
            outputPath      = outputPath,
            outputPageCount = totalOut,
            fileSize        = outFile.length()
        )
    }

    private fun buildOutputPath(sourceName: String, customName: String = ""): String {
        val dir  = getOutputDirectory()
        val base = if (customName.isNotBlank()) customName.trim().removeSuffix(".pdf")
                   else sourceName.removeSuffix(".pdf")
        var path = "$dir/${base}_multipages.pdf"
        var counter = 1
        while (File(path).exists()) {
            path = "$dir/${base}_multipages_$counter.pdf"
            counter++
        }
        return path
    }

    private fun getOutputDirectory(): String {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val dir  = File(docs, "Kosh")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    private fun getPdfPageCount(path: String): Int {
        return try {
            val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            val r  = PdfRenderer(fd)
            val n  = r.pageCount
            r.close(); fd.close()
            n
        } catch (e: Exception) { 0 }
    }

    private fun copyUriToCache(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName    = getFileNameFromUri(uri) ?: "multipages_temp_${System.currentTimeMillis()}.pdf"
            val cacheFile   = File(context.cacheDir, "multipages_temp/$fileName")
            cacheFile.parentFile?.mkdirs()
            cacheFile.outputStream().use { out -> inputStream.copyTo(out) }
            cacheFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy URI to cache")
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
            }
        } catch (e: Exception) { null }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
    fun reset()      { _state.update { MultiPagesState() } }
}
