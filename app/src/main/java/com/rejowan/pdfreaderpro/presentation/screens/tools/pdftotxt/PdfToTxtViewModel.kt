package com.rejowan.pdfreaderpro.presentation.screens.tools.pdftotxt

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
import com.rejowan.pdfreaderpro.util.TesseractManager
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.ReaderProperties
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PdfToTxtSourceFile(
    val uri: Uri?,
    val path: String,
    val name: String,
    val size: Long,
    val pageCount: Int
)

data class PdfToTxtResult(
    val outputPath: String,
    val pageCount: Int,
    val charCount: Int,
    val fileSize: Long
)

enum class PdfToTxtPageMode { ALL, CUSTOM }

data class PdfToTxtState(
    val sourceFile: PdfToTxtSourceFile? = null,
    val useOcr: Boolean = false,
    val pageMode: PdfToTxtPageMode = PdfToTxtPageMode.ALL,
    val pageInput: String = "",
    val pageInputError: String? = null,
    val outputFileName: String = "",
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val result: PdfToTxtResult? = null
)

class PdfToTxtViewModel(
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(PdfToTxtState())
    val state: StateFlow<PdfToTxtState> = _state.asStateFlow()

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
                        sourceFile = PdfToTxtSourceFile(
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

    fun setUseOcr(enabled: Boolean) {
        _state.update { it.copy(useOcr = enabled, result = null) }
    }

    fun setPageMode(mode: PdfToTxtPageMode) {
        _state.update { it.copy(pageMode = mode, pageInput = "", pageInputError = null, result = null) }
    }

    fun setPageInput(input: String) {
        val maxPages = _state.value.sourceFile?.pageCount ?: Int.MAX_VALUE
        val error = validatePageInput(input, maxPages)
        _state.update { it.copy(pageInput = input, pageInputError = error, result = null) }
    }

    fun setOutputFileName(name: String) {
        _state.update { it.copy(outputFileName = name, result = null) }
    }

    private fun validatePageInput(input: String, maxPages: Int): String? {
        if (input.isBlank()) return "Please enter page numbers"
        val pages = parsePageInput(input, maxPages)
        if (pages.isEmpty()) return "No valid pages found. Use format: 1,3,5-8"
        return null
    }

    private fun parsePageInput(input: String, maxPages: Int): List<Int> {
        val pages = mutableSetOf<Int>()
        input.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val sides = trimmed.split("-")
                val start = sides.getOrNull(0)?.trim()?.toIntOrNull()
                val end   = sides.getOrNull(1)?.trim()?.toIntOrNull()
                if (start != null && end != null && start <= end) {
                    (start..end).filter { it in 1..maxPages }.forEach { pages.add(it) }
                }
            } else {
                trimmed.toIntOrNull()?.let { if (it in 1..maxPages) pages.add(it) }
            }
        }
        return pages.sorted()
    }

    fun convert() {
        val sf = _state.value.sourceFile ?: run {
            _state.update { it.copy(error = "No PDF file selected") }
            return
        }
        if (_state.value.pageMode == PdfToTxtPageMode.CUSTOM) {
            val pages = parsePageInput(_state.value.pageInput, sf.pageCount)
            if (pages.isEmpty()) {
                _state.update { it.copy(error = "No valid pages selected. Use format: 1,3,5-8") }
                return
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, progress = 0f, error = null, result = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (_state.value.useOcr) convertWithOcr(sf) else convertNative(sf)
                }
            }
            result.fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(isProcessing = false, progress = 1f,
                            progressLabel = "Done!", result = res)
                    }
                },
                onFailure = { e ->
                    Timber.e(e, "PDF to TXT failed")
                    _state.update {
                        it.copy(isProcessing = false,
                            error = e.message ?: "Conversion failed")
                    }
                }
            )
        }
    }

    // ── NATIVE TEXT EXTRACTION (no OCR) ───────────────────────────────────
    private fun convertNative(sf: PdfToTxtSourceFile): PdfToTxtResult {
        val outputPath = buildOutputPath(sf.name)
        val sb = StringBuilder()

        val reader = openLenientPdfReader(sf.path)
        val doc = PdfDocument(reader)
        val total = doc.numberOfPages

        val pagesToProcess = if (_state.value.pageMode == PdfToTxtPageMode.CUSTOM)
            parsePageInput(_state.value.pageInput, total)
        else
            (1..total).toList()

        pagesToProcess.forEachIndexed { idx, i ->
            updateProgress(idx.toFloat() / pagesToProcess.size, "Extracting page $i of $total…")
            val pageText = PdfTextExtractor.getTextFromPage(doc.getPage(i))
            if (pageText.isNotBlank()) {
                sb.appendLine("--- Page $i ---")
                sb.appendLine(pageText.trim())
                sb.appendLine()
            }
        }

        doc.close()

        val text = sb.toString()
        FileOutputStream(outputPath).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        MediaScannerConnection.scanFile(context, arrayOf(outputPath), arrayOf("text/plain"), null)

        return PdfToTxtResult(
            outputPath = outputPath,
            pageCount = pagesToProcess.size,
            charCount = text.length,
            fileSize = File(outputPath).length()
        )
    }

    // ── OCR TEXT EXTRACTION ───────────────────────────────────────────────
    // Renders each page to Bitmap via Android PdfRenderer, then runs
    // Tesseract OCR (open source, Apache 2.0). Tessdata downloads once on
    // first use (~4 MB) then works fully offline forever.
    private suspend fun convertWithOcr(sf: PdfToTxtSourceFile): PdfToTxtResult {
        val outputPath = buildOutputPath(sf.name, ocr = true)
        val sb = StringBuilder()

        // Ensure Tesseract model is downloaded (downloads once, then permanent)
        val tess = org.koin.java.KoinJavaComponent.getKoin().get<TesseractManager>()
        val ready = tess.ensureReady()
        if (!ready) {
            throw Exception("OCR engine failed to initialize. Please reinstall the app.")
        }

        val tempFile = File(sf.path)
        val parcelFd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(parcelFd)
        val total = renderer.pageCount

        try {
            val pagesToProcess = if (_state.value.pageMode == PdfToTxtPageMode.CUSTOM)
                parsePageInput(_state.value.pageInput, total).map { it - 1 }
            else
                (0 until total).toList()

            pagesToProcess.forEachIndexed { idx, i ->
                updateProgress(idx.toFloat() / pagesToProcess.size, "OCR page ${i + 1} of $total…")

                val page = renderer.openPage(i)
                val maxDim = 2048
                val scale = minOf(2.0f, maxDim.toFloat() / maxOf(page.width, page.height))
                val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
                val bmpH = (page.height * scale).toInt().coerceAtLeast(1)

                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                val cvs = Canvas(bitmap)
                cvs.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Run Tesseract OCR on IO dispatcher (blocking call)
                val ocrText = withContext(Dispatchers.IO) {
                    val text = tess.recognizeText(bitmap)
                    bitmap.recycle()
                    text
                }

                if (ocrText.isNotBlank()) {
                    sb.appendLine("--- Page ${i + 1} ---")
                    sb.appendLine(ocrText)
                    sb.appendLine()
                }
            }
        } finally {
            renderer.close()
            parcelFd.close()
        }

        val text = sb.toString()
        FileOutputStream(outputPath).use { it.write(text.toByteArray(Charsets.UTF_8)) }

        MediaScannerConnection.scanFile(
            context, arrayOf(outputPath), arrayOf("text/plain"), null
        )

        return PdfToTxtResult(
            outputPath = outputPath,
            pageCount = total,
            charCount = text.length,
            fileSize = File(outputPath).length()
        )
    }

    private fun updateProgress(progress: Float, label: String) {
        // Post to main thread from IO
        viewModelScope.launch(Dispatchers.Main) {
            _state.update { it.copy(progress = progress, progressLabel = label) }
        }
    }

    private fun buildOutputPath(sourceName: String, ocr: Boolean = false): String {
        val dir = getOutputDirectory()
        val customName = _state.value.outputFileName.trim()
        val base = if (customName.isNotBlank()) customName.removeSuffix(".txt")
                   else sourceName.removeSuffix(".pdf").removeSuffix(".PDF")
        val suffix = if (ocr) "_ocr" else ""
        var path = "$dir/${base}${suffix}.txt"
        var counter = 1
        while (File(path).exists()) {
            path = "$dir/${base}${suffix}_$counter.txt"
            counter++
        }
        return path
    }

    private fun getOutputDirectory(): String {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val pdfToolsDir = File(documentsDir, "Kosh")
        if (!pdfToolsDir.exists()) pdfToolsDir.mkdirs()
        return pdfToolsDir.absolutePath
    }

    private fun getPdfPageCount(path: String): Int {
        return try {
            val fd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(fd)
            val n = r.pageCount
            r.close(); fd.close()
            n
        } catch (e: Exception) { 0 }
    }

    /** Opens a PdfReader tolerantly, patching malformed %PDF- version headers. */
    private fun openLenientPdfReader(path: String): PdfReader {
        val bytes = File(path).readBytes()
        val headerStr = String(bytes.take(minOf(1024, bytes.size)).toByteArray(), Charsets.ISO_8859_1)
        val pdfIdx = headerStr.indexOf("%PDF-")
        val needsPatch = if (pdfIdx < 0) true else {
            val v = headerStr.drop(pdfIdx + 5).take(3)
            !(v.length >= 3 && v[0].isDigit() && v[1] == '.' && v[2].isDigit())
        }
        val stream = if (needsPatch) {
            val patched = if (pdfIdx >= 0)
                bytes.slice(0 until pdfIdx).toByteArray() +
                    "%PDF-1.4".toByteArray(Charsets.ISO_8859_1) +
                    bytes.drop(pdfIdx + 5).drop(3).toByteArray()
            else "%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1) + bytes
            ByteArrayInputStream(patched)
        } else ByteArrayInputStream(bytes)
        return PdfReader(stream, ReaderProperties())
            .setStrictnessLevel(PdfReader.StrictnessLevel.LENIENT)
            .also { it.setUnethicalReading(true) }
    }

    private fun copyUriToCache(uri: Uri): String? {
        return try {
            if (uri.scheme == "file") return uri.path
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = getFileNameFromUri(uri) ?: "pdftotxt_temp_${System.currentTimeMillis()}.pdf"
            val cacheFile = File(context.cacheDir, "pdftotxt_temp/$fileName")
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
    fun reset() { _state.update { PdfToTxtState() } }
}
