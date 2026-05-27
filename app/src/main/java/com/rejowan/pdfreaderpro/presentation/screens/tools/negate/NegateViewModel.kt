package com.rejowan.pdfreaderpro.presentation.screens.tools.negate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.media.MediaScannerConnection
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class NegateState(
    val sourceFile: NegateSourceFile? = null,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val outputPath: String? = null,
    val isSuccess: Boolean = false
)

data class NegateSourceFile(
    val uri: Uri?,
    val path: String,
    val name: String,
    val size: Long,
    val pageCount: Int
)

class NegateViewModel(
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(NegateState())
    val state: StateFlow<NegateState> = _state.asStateFlow()

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
                        sourceFile = NegateSourceFile(
                            uri = uri,
                            path = tempPath,
                            name = getFileNameFromUri(uri) ?: file.name,
                            size = file.length(),
                            pageCount = pageCount
                        ),
                        error = null,
                        isSuccess = false,
                        outputPath = null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to set source file")
                _state.update { it.copy(error = e.message ?: "Failed to load file") }
            }
        }
    }

    fun negate() {
        val sourceFile = _state.value.sourceFile ?: run {
            _state.update { it.copy(error = "No PDF file selected") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, progress = 0f, error = null, isSuccess = false) }

            val result = withContext(Dispatchers.IO) {
                runCatching { invertPdfColors(sourceFile) }
            }

            result.fold(
                onSuccess = { outputPath ->
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            progress = 1f,
                            progressLabel = "Done!",
                            isSuccess = true,
                            outputPath = outputPath
                        )
                    }
                },
                onFailure = { e ->
                    Timber.e(e, "Negate failed")
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            error = e.message ?: "Color inversion failed"
                        )
                    }
                }
            )
        }
    }

    private suspend fun invertPdfColors(sourceFile: NegateSourceFile): String {
        val outputDir = getOutputDirectory()
        val baseName = sourceFile.name.removeSuffix(".pdf")
        var outputPath = "$outputDir/negated_$baseName.pdf"
        var counter = 1
        while (File(outputPath).exists()) {
            outputPath = "$outputDir/negated_${baseName}_$counter.pdf"
            counter++
        }

        val tempFile = File(sourceFile.path)
        val parcelFd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(parcelFd)
        val total = renderer.pageCount

        val writer = PdfWriter(FileOutputStream(outputPath))
        val outDoc = PdfDocument(writer)

        for (i in 0 until total) {
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        progress = i.toFloat() / total,
                        progressLabel = "Inverting page ${i + 1} of $total…"
                    )
                }
            }

            val page = renderer.openPage(i)

            // Cap at 2048px to avoid OOM on large PDFs
            val maxDim = 2048
            val scale = minOf(2.0f, maxDim.toFloat() / maxOf(page.width, page.height))
            val bmpW = (page.width * scale).toInt()
            val bmpH = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            // White background before render
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // Invert colors pixel-by-pixel (keep alpha)
            val pixels = IntArray(bmpW * bmpH)
            bitmap.getPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)
            for (j in pixels.indices) {
                val px = pixels[j]
                val a = px and 0xFF000000.toInt()
                val r = 255 - ((px shr 16) and 0xFF)
                val g = 255 - ((px shr 8) and 0xFF)
                val b = 255 - (px and 0xFF)
                pixels[j] = a or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)

            // Compress to JPEG and embed
            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            bitmap.recycle()

            val imgData = ImageDataFactory.create(bos.toByteArray())

            // Page size in PDF points (96 dpi screen → 72 pt/in)
            val pgW = page.width.toFloat() * 72f / 96f
            val pgH = page.height.toFloat() * 72f / 96f

            val outPage = outDoc.addNewPage(PageSize(pgW, pgH))
            val pdfCanvas = PdfCanvas(outPage)
            pdfCanvas.addImageFittedIntoRectangle(
                imgData,
                Rectangle(0f, 0f, pgW, pgH),
                false
            )
            pdfCanvas.release()
        }

        renderer.close()
        parcelFd.close()
        outDoc.close()

        // Notify media scanner so file appears in file manager like other tools
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(outputPath),
            arrayOf("application/pdf"),
            null
        )

        return outputPath
    }

    private fun getPdfPageCount(path: String): Int {
        return try {
            val parcelFd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(parcelFd)
            val count = renderer.pageCount
            renderer.close()
            parcelFd.close()
            count
        } catch (e: Exception) {
            0
        }
    }

    private fun getOutputDirectory(): String {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val pdfToolsDir = File(documentsDir, "PdfReaderPro")
        if (!pdfToolsDir.exists()) {
            pdfToolsDir.mkdirs()
        }
        return pdfToolsDir.absolutePath
    }

    private fun copyUriToCache(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileNameFromUri(uri) ?: "negate_temp_${System.currentTimeMillis()}.pdf"
            val cacheFile = File(context.cacheDir, "negate_temp/$fileName")
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
        } catch (e: Exception) {
            null
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun reset() {
        _state.update { NegateState() }
    }
}
