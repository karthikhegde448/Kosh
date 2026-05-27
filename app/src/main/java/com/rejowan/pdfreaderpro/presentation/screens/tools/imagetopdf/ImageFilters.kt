package com.rejowan.pdfreaderpro.presentation.screens.tools.imagetopdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.sqrt

enum class ImageFilter(val label: String, val accentColor: Color) {
    NONE      ("None",       Color(0xFF9E9E9E)),
    PHOTO     ("Photo",      Color(0xFF42A5F5)),
    MONO      ("Mono",       Color(0xFF616161)),
    LOMOISH   ("Lomoish",    Color(0xFFEC407A)),
    POSTER    ("Poster",     Color(0xFFAB47BC)),
    PROCESS   ("Process",    Color(0xFF26A69A)),
    VIGNETTE  ("Vignette",   Color(0xFF5D4037)),
    NEGATIVE  ("Negative",   Color(0xFF1A237E)),
    SEPIA     ("Sepia",      Color(0xFF8D6E63)),
    GRAIN     ("Grain",      Color(0xFFBCAAA4)),
    DOCUMENT  ("Document",   Color(0xFFE87722)),
    LIGHTEN   ("Lighten",    Color(0xFFFFF176)),
    BW        ("B&W",        Color(0xFF212121)),
    GRAYSCALE ("Grayscale",  Color(0xFF90A4AE)),
    WHITEBOARD("Whiteboard", Color(0xFFFFB74D));
}

object ImageFilterEngine {

    fun applyFilter(source: Bitmap, filter: ImageFilter): Bitmap = when (filter) {
        ImageFilter.NONE       -> source
        ImageFilter.PHOTO      -> applyPhoto(source)
        ImageFilter.MONO       -> applyMono(source)
        ImageFilter.LOMOISH    -> applyLomoish(source)
        ImageFilter.POSTER     -> applyPoster(source)
        ImageFilter.PROCESS    -> applyProcess(source)
        ImageFilter.VIGNETTE   -> applyVignette(source)
        ImageFilter.NEGATIVE   -> applyNegative(source)
        ImageFilter.SEPIA      -> applySepia(source)
        ImageFilter.GRAIN      -> applyGrain(source)
        ImageFilter.DOCUMENT   -> applyDocument(source)
        ImageFilter.LIGHTEN    -> applyLighten(source)
        ImageFilter.BW         -> applyBW(source)
        ImageFilter.GRAYSCALE  -> applyGrayscale(source)
        ImageFilter.WHITEBOARD -> applyWhiteboard(source)
    }

    // ── 1. PHOTO ─────────────────────────────────────────────────────────────
    // Bright, vivid, slightly warm — like a well-exposed photo
    private fun applyPhoto(src: Bitmap): Bitmap = cm(src, floatArrayOf(
        1.15f, 0.00f, 0.00f, 0f, 10f,
        0.00f, 1.10f, 0.00f, 0f,  5f,
        0.00f, 0.00f, 0.95f, 0f,  0f,
        0.00f, 0.00f, 0.00f, 1f,  0f
    ))

    // ── 2. MONO ──────────────────────────────────────────────────────────────
    // Clean neutral desaturation using standard luminance weights
    private fun applyMono(src: Bitmap): Bitmap = cm(src, floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.000f, 0.000f, 0.000f, 1f, 0f
    ))

    // ── 3. LOMOISH ───────────────────────────────────────────────────────────
    // Warm golden-yellow cast, crushed blacks, heavy corner vignette
    // Background turns creamy yellow, dark areas turn deep navy
    private fun applyLomoish(src: Bitmap): Bitmap {
        val tinted = cm(src, floatArrayOf(
             1.20f,  0.05f,  0.00f, 0f,  20f,   // R: big boost → warm
             0.00f,  1.10f,  0.00f, 0f,  10f,   // G: moderate boost → yellow
            -0.15f, -0.10f,  0.60f, 0f, -30f,   // B: heavily suppressed → kills cyan/blue
             0.00f,  0.00f,  0.00f, 1f,   0f
        ))
        val out = tinted.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val cx = out.width / 2f
        val cy = out.height / 2f
        // Tight vignette radius — edges go very dark
        val radius = max(out.width, out.height) * 0.60f
        val gradient = RadialGradient(
            cx, cy, radius,
            intArrayOf(0x00000000, 0x44000000, 0xBB000000.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.35f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = gradient
                xfermode = android.graphics.PorterDuffXfermode(
                    android.graphics.PorterDuff.Mode.MULTIPLY)
            })
        return out
    }

    // ── 4. POSTER ────────────────────────────────────────────────────────────
    // 4-level posterization: flattens image into discrete tonal bands
    private fun applyPoster(src: Bitmap): Bitmap {
        val grey = cm(src, floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        val pixels = IntArray(grey.width * grey.height)
        grey.getPixels(pixels, 0, grey.width, 0, 0, grey.width, grey.height)
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            val l = (pixels[i] ushr 16) and 0xFF
            val v = when {
                l < 64  -> 0
                l < 128 -> 85
                l < 192 -> 170
                else    -> 255
            }
            pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
        }
        grey.setPixels(pixels, 0, grey.width, 0, 0, grey.width, grey.height)
        return grey
    }

    // ── 5. PROCESS ───────────────────────────────────────────────────────────
    // Cross-process: over-saturated, yellow-green cast, shifted channels
    private fun applyProcess(src: Bitmap): Bitmap = cm(src, floatArrayOf(
         1.40f,  0.00f,  0.00f, 0f,  15f,
         0.00f,  1.20f,  0.00f, 0f,   5f,
        -0.20f, -0.15f,  0.80f, 0f, -10f,
         0.00f,  0.00f,  0.00f, 1f,   0f
    ))

    // ── 6. VIGNETTE ──────────────────────────────────────────────────────────
    // Desaturated with strong circular edge darkening
    private fun applyVignette(src: Bitmap): Bitmap {
        val grey = cm(src, floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        val out = grey.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val cx = out.width / 2f
        val cy = out.height / 2f
        val radius = sqrt(cx * cx + cy * cy)
        val gradient = RadialGradient(
            cx, cy, radius,
            intArrayOf(0x00000000, 0x00000000, 0x99000000.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.40f, 0.72f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient })
        return out
    }

    // ── 7. NEGATIVE ──────────────────────────────────────────────────────────
    // Full channel inversion
    private fun applyNegative(src: Bitmap): Bitmap = cm(src, floatArrayOf(
        -1f,  0f,  0f, 0f, 255f,
         0f, -1f,  0f, 0f, 255f,
         0f,  0f, -1f, 0f, 255f,
         0f,  0f,  0f, 1f,   0f
    ))

    // ── 8. SEPIA ─────────────────────────────────────────────────────────────
    // Classic warm antique brown tint
    private fun applySepia(src: Bitmap): Bitmap = cm(src, floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0.000f, 0.000f, 0.000f, 1f, 0f
    ))

    // ── 9. GRAIN ─────────────────────────────────────────────────────────────
    // Desaturated + per-pixel film grain noise
    private fun applyGrain(src: Bitmap): Bitmap {
        val grey = cm(src, floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        val pixels = IntArray(grey.width * grey.height)
        grey.getPixels(pixels, 0, grey.width, 0, 0, grey.width, grey.height)
        val rng = java.util.Random()
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            val v = (pixels[i] ushr 16) and 0xFF
            val noise = rng.nextInt(25) - 12
            val nv = ((v * 0.86f).toInt() + noise).coerceIn(0, 255)
            pixels[i] = (a shl 24) or (nv shl 16) or (nv shl 8) or nv
        }
        grey.setPixels(pixels, 0, grey.width, 0, 0, grey.width, grey.height)
        return grey
    }

    // ── 10. DOCUMENT ─────────────────────────────────────────────────────────
    // High-contrast B&W scan: threshold at 140 — mimics photocopier scan
    private fun applyDocument(src: Bitmap): Bitmap {
        val lut = IntArray(256) { i -> if (i >= 140) 255 else 0 }
        return applyLUT(src, lut)
    }

    // ── 11. LIGHTEN ──────────────────────────────────────────────────────────
    // Desaturate + lift shadows significantly (compressed range)
    private fun applyLighten(src: Bitmap): Bitmap {
        val grey = cm(src, floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        return cm(grey, floatArrayOf(
            0.70f, 0f, 0f, 0f, 70f,
            0f, 0.70f, 0f, 0f, 70f,
            0f, 0f, 0.70f, 0f, 70f,
            0f, 0f, 0f, 1f,  0f
        ))
    }

    // ── 12. B&W ──────────────────────────────────────────────────────────────
    // Pure binary black/white: extreme contrast push after desaturation
    // Produces pure white background, pure black strokes — no gray
    private fun applyBW(src: Bitmap): Bitmap {
        val grey = cm(src, floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        // Extreme contrast: scale=8, offset=-800 → anything above 100 → white, below → black
        return cm(grey, floatArrayOf(
            8f, 0f, 0f, 0f, -800f,
            0f, 8f, 0f, 0f, -800f,
            0f, 0f, 8f, 0f, -800f,
            0f, 0f, 0f, 1f,    0f
        ))
    }

    // ── 13. GRAYSCALE ────────────────────────────────────────────────────────
    // Pure BT.709 desaturation — perceptually accurate gray
    private fun applyGrayscale(src: Bitmap): Bitmap = cm(src, floatArrayOf(
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.2126f, 0.7152f, 0.0722f, 0f, 0f,
        0.0000f, 0.0000f, 0.0000f, 1f, 0f
    ))

    // ── 14. WHITEBOARD ───────────────────────────────────────────────────────
    // Adaptive bleach: amplify luma, blast highlights to white, keep dark lines
    private fun applyWhiteboard(src: Bitmap): Bitmap {
        val lut = IntArray(256) { i ->
            val b = (i * 1.25f).toInt()
            if (b > 175) 255 else max(0, (b * 0.35f).toInt())
        }
        return applyLUT(src, lut)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applyLUT(src: Bitmap, lut: IntArray): Bitmap {
        val grey = cm(src, floatArrayOf(
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        val pixels = IntArray(grey.width * grey.height)
        grey.getPixels(pixels, 0, grey.width, 0, 0, grey.width, grey.height)
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            val y = ((pixels[i] ushr 16) and 0xFF).coerceIn(0, 255)
            val v = lut[y]
            pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
        }
        grey.setPixels(pixels, 0, grey.width, 0, 0, grey.width, grey.height)
        return grey
    }

    private fun cm(src: Bitmap, values: FloatArray): Bitmap {
        val m = ColorMatrix(); m.set(values)
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(m)
            isAntiAlias = true
        })
        return out
    }
}
