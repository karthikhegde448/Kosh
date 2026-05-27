package com.rejowan.pdfreaderpro.presentation.screens.tools.texttopdf

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
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
import kotlin.math.ceil
import kotlin.math.max

// ── Font definitions ────────────────────────────────────────────────────────

enum class TextFont(
    val displayName: String,
    val standardFont: String,       // iText StandardFonts constant
    val assetPath: String? = null,  // only set for custom fonts like Handwriting
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isMono: Boolean = false
) {
    SANS_REGULAR("Arial (Regular)",        StandardFonts.HELVETICA),
    SANS_BOLD("Arial (Bold)",              StandardFonts.HELVETICA_BOLD,        isBold = true),
    SANS_ITALIC("Arial (Italic)",          StandardFonts.HELVETICA_OBLIQUE,     isItalic = true),
    SANS_BOLD_ITALIC("Arial (Bold Italic)",StandardFonts.HELVETICA_BOLDOBLIQUE, isBold = true, isItalic = true),
    SERIF_REGULAR("Times New Roman",       StandardFonts.TIMES_ROMAN),
    SERIF_BOLD("Times New Roman Bold",     StandardFonts.TIMES_BOLD,            isBold = true),
    SERIF_ITALIC("Times New Roman Italic", StandardFonts.TIMES_ITALIC,          isItalic = true),
    MONO("Courier New",                    StandardFonts.COURIER,               isMono = true),
    HANDWRITING("Handwriting",             StandardFonts.HELVETICA,             assetPath = "fonts/Handwriting.ttf")
}

// ── PDF mode ─────────────────────────────────────────────────────────────────

enum class TextPdfMode { CUSTOM, NOTEBOOK }

// ── State ─────────────────────────────────────────────────────────────────────

data class TextToPdfState(
    val inputText: String = "",
    val fontType: TextFont = TextFont.SANS_REGULAR,
    val fontSize: Float = 12f,
    val textColor: Color = Color.Black,
    val lineSpacingCm: Float = 0.6f,
    val horizontalMarginCm: Float = 2f,
    val verticalMarginCm: Float = 2f,
    val mode: TextPdfMode = TextPdfMode.CUSTOM,
    val outputFileName: String = "",
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val outputPath: String? = null,
    val estimatedPages: Int = 0
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class TextToPdfViewModel(private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(TextToPdfState())
    val state: StateFlow<TextToPdfState> = _state.asStateFlow()

    fun setInputText(t: String)       { _state.update { it.copy(inputText = t, outputPath = null).withEstimate() } }
    fun setFontType(f: TextFont)      { _state.update { it.copy(fontType = f, outputPath = null).withEstimate() } }
    fun setFontSize(v: Float)         { _state.update { it.copy(fontSize = v, outputPath = null).withEstimate() } }
    fun setTextColor(c: Color)        { _state.update { it.copy(textColor = c, outputPath = null) } }
    fun setLineSpacing(v: Float)      { _state.update { it.copy(lineSpacingCm = v, outputPath = null).withEstimate() } }
    fun setHorizontalMargin(v: Float) { _state.update { it.copy(horizontalMarginCm = v, outputPath = null).withEstimate() } }
    fun setVerticalMargin(v: Float)   { _state.update { it.copy(verticalMarginCm = v, outputPath = null).withEstimate() } }
    fun setMode(m: TextPdfMode)       { _state.update { it.copy(mode = m, outputPath = null).withEstimate() } }
    fun setOutputFileName(v: String)  { _state.update { it.copy(outputFileName = v, outputPath = null) } }
    fun clearError()                  { _state.update { it.copy(error = null) } }
    fun reset()                       { _state.update { TextToPdfState() } }

    // ── Page estimator ────────────────────────────────────────────────────────

    private fun TextToPdfState.withEstimate(): TextToPdfState {
        if (inputText.isBlank()) return copy(estimatedPages = 0)
        val ptPerCm = 28.346f
        val pageH = 841.89f
        val isNB = mode == TextPdfMode.NOTEBOOK

        val hMarginPt = if (isNB) 56.69f else horizontalMarginCm * ptPerCm
        val vMarginPt = if (isNB) 56.69f else verticalMarginCm * ptPerCm
        val fs = if (isNB) 13f else fontSize
        val ls = if (isNB) 0.85f * ptPerCm else lineSpacingCm * ptPerCm
        val lineHeight = max(fs * 1.2f, ls)

        val contentH = pageH - vMarginPt * 2
        val linesPerPage = max(1, (contentH / lineHeight).toInt())

        // Rough char-based estimation without font metrics (fast)
        val pageW = 595.28f
        val contentW = pageW - hMarginPt * 2
        val approxCharsPerLine = max(1, (contentW / (fs * 0.55f)).toInt())
        val lineCount = inputText.split("\n").sumOf { para ->
            if (para.isBlank()) 1
            else max(1, ceil(para.length.toDouble() / approxCharsPerLine).toInt())
        }
        val pages = max(1, ceil(lineCount.toDouble() / linesPerPage).toInt())
        return copy(estimatedPages = pages)
    }

    // ── PDF generation ────────────────────────────────────────────────────────

    fun convert() {
        val text = _state.value.inputText.trim()
        if (text.isEmpty()) { _state.update { it.copy(error = "Please enter some text first") }; return }
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, progress = 0f, error = null, outputPath = null) }
            val result = withContext(Dispatchers.IO) { runCatching { buildPdf(text) } }
            result.fold(
                onSuccess = { path -> _state.update { it.copy(isProcessing = false, progress = 1f, outputPath = path) } },
                onFailure = { e ->
                    Timber.e(e, "TextToPdf failed")
                    _state.update { it.copy(isProcessing = false, error = e.message ?: "Conversion failed") }
                }
            )
        }
    }

    private fun buildPdf(text: String): String {
        val st = _state.value
        val isNB = st.mode == TextPdfMode.NOTEBOOK
        val ptPerCm = 28.346f

        // Page size A4
        val pageW = 595.28f
        val pageH = 841.89f

        // Effective settings
        val fontStyle = if (isNB) TextFont.HANDWRITING else st.fontType
        val fontSize = if (isNB) 13f else st.fontSize
        val hMarginPt = if (isNB) 56.69f else st.horizontalMarginCm * ptPerCm  // 2cm default notebook
        val vMarginPt = if (isNB) 56.69f else st.verticalMarginCm * ptPerCm
        val lineSpacingPt = if (isNB) 0.85f * ptPerCm else st.lineSpacingCm * ptPerCm
        val lineHeight = max(fontSize * 1.2f, lineSpacingPt)

        val color = if (isNB) Color.Black else st.textColor
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()

        val isUnderline = !isNB && st.fontType.isUnderline

        // Load font from assets
        val font: PdfFont = loadFontForStyle(fontStyle)

        val contentW = pageW - hMarginPt * 2
        val contentH = pageH - vMarginPt * 2
        val linesPerPage = max(1, (contentH / lineHeight).toInt())

        // Word-wrap text
        val wrappedLines = wrapText(text, font, fontSize, contentW)
        val totalPages = max(1, ceil(wrappedLines.size.toDouble() / linesPerPage).toInt())

        val outputPath = buildOutputPath(_state.value.outputFileName)
        val outDoc = PdfDocument(PdfWriter(FileOutputStream(outputPath)))

        var lineIdx = 0
        for (pageNum in 0 until totalPages) {
            _state.update { it.copy(progress = pageNum.toFloat() / totalPages) }

            val page = outDoc.addNewPage(PageSize(pageW, pageH))
            val canvas = PdfCanvas(page)

            // White background
            canvas.setFillColor(DeviceRgb(255, 255, 255))
            canvas.rectangle(0.0, 0.0, pageW.toDouble(), pageH.toDouble())
            canvas.fill()

            // Notebook ruled lines
            if (isNB) {
                canvas.setStrokeColor(DeviceRgb(180, 210, 240))
                canvas.setLineWidth(0.5f)
                var ry = pageH - vMarginPt
                while (ry >= vMarginPt) {
                    canvas.moveTo(hMarginPt.toDouble(), ry.toDouble())
                    canvas.lineTo((pageW - hMarginPt).toDouble(), ry.toDouble())
                    canvas.stroke()
                    ry -= lineHeight
                }
                // Red left margin
                canvas.setStrokeColor(DeviceRgb(210, 70, 70))
                canvas.setLineWidth(1.2f)
                val redX = (hMarginPt - 10).toDouble()
                canvas.moveTo(redX, pageH.toDouble())
                canvas.lineTo(redX, 0.0)
                canvas.stroke()
            }

            // Draw text lines
            val linesOnPage = minOf(linesPerPage, wrappedLines.size - lineIdx)
            for (i in 0 until linesOnPage) {
                // Baseline sits exactly on the ruled line
                val textY = pageH - vMarginPt - (i * lineHeight)
                val lineText = wrappedLines[lineIdx]
                lineIdx++

                canvas.beginText()
                canvas.setFontAndSize(font, fontSize)
                canvas.setFillColor(DeviceRgb(r, g, b))
                canvas.setTextMatrix(hMarginPt.toFloat(), textY.toFloat())
                canvas.showText(lineText)
                canvas.endText()

                // Underline
                if (isUnderline && lineText.isNotEmpty()) {
                    val textW = font.getWidth(lineText, fontSize)
                    val ulY = textY - fontSize * 0.15
                    canvas.setStrokeColor(DeviceRgb(r, g, b))
                    canvas.setLineWidth(0.8f)
                    canvas.moveTo(hMarginPt.toDouble(), ulY)
                    canvas.lineTo((hMarginPt + textW).toDouble(), ulY)
                    canvas.stroke()
                }
            }
            canvas.release()
        }

        outDoc.close()
        MediaScannerConnection.scanFile(context, arrayOf(outputPath), arrayOf("application/pdf"), null)
        return outputPath
    }

    private fun loadFontForStyle(fontType: TextFont): PdfFont {
        return try {
            // Use asset TTF only for Handwriting, standard built-in fonts for everything else
            if (fontType.assetPath != null) {
                val bytes = context.assets.open(fontType.assetPath).readBytes()
                PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H)
            } else {
                PdfFontFactory.createFont(fontType.standardFont)
            }
        } catch (e: Exception) {
            Timber.e(e, "Font load failed for ${fontType.displayName}, falling back to Helvetica")
            PdfFontFactory.createFont(StandardFonts.HELVETICA)
        }
    }

    private fun wrapText(text: String, font: PdfFont, size: Float, maxW: Float): List<String> {
        val result = mutableListOf<String>()
        for (para in text.split("\n")) {
            if (para.isBlank()) { result.add(""); continue }
            val words = para.split(" ")
            var current = StringBuilder()
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (font.getWidth(test, size) <= maxW) {
                    current = StringBuilder(test)
                } else {
                    if (current.isNotEmpty()) result.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) result.add(current.toString())
        }
        return result.ifEmpty { listOf("") }
    }

    private fun buildOutputPath(customName: String = ""): String {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Kosh"
        ).apply { mkdirs() }
        val base = if (customName.isNotBlank()) customName.trim().removeSuffix(".pdf")
                   else "TextToPdf_${System.currentTimeMillis()}"
        return "${dir.absolutePath}/$base.pdf"
    }
}
