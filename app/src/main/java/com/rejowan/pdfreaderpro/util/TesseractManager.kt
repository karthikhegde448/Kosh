package com.rejowan.pdfreaderpro.util

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class TesseractManager(private val context: Context) {

    companion object {
        private const val LANG = "eng"
        private const val ASSET_PATH = "tessdata/eng.traineddata"
    }

    private val tessDataDir  get() = File(context.filesDir, "tessdata")
    private val tessDataFile get() = File(tessDataDir, "$LANG.traineddata")

    /** Copy tessdata from assets to filesDir on first use. Very fast — no download. */
    suspend fun ensureReady(): Boolean = withContext(Dispatchers.IO) {
        if (tessDataFile.exists() && tessDataFile.length() > 100_000L) return@withContext true
        return@withContext try {
            tessDataDir.mkdirs()
            context.assets.open(ASSET_PATH).use { input ->
                tessDataFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tessDataFile.length() > 100_000L
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy tessdata from assets")
            false
        }
    }

    /** Runs OCR on a bitmap. Call ensureReady() first. */
    fun recognizeText(bitmap: Bitmap): String {
        val api = TessBaseAPI()
        return try {
            api.init(context.filesDir.absolutePath, LANG)
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(bitmap)
            val text = api.utF8Text ?: ""
            api.clear()
            text.trim()
        } catch (e: Exception) {
            Timber.e(e, "OCR failed")
            ""
        } finally {
            try { api.recycle() } catch (_: Exception) {}
        }
    }
}
