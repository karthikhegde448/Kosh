package com.rejowan.pdfreaderpro.presentation.screens.tools.imagetopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rejowan.pdfreaderpro.domain.repository.PdfToolsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Editor screens/tabs ──────────────────────────────────────────────────────
enum class EditorTab { CROP, DELETE, REORDER, FILTERS, PEN }

// ── Pen stroke data ──────────────────────────────────────────────────────────
data class PenStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

// ── Crop rect (normalized 0..1) ───────────────────────────────────────────────
/**
 * Perspective crop — 4 independent corners as normalized (0..1) coords.
 * Allows free quadrilateral selection for perspective correction.
 * Default: inset 8% from each edge so handles sit inside the screen.
 */
data class PointF2(val x: Float, val y: Float)

data class QuadCrop(
    val topLeft:     PointF2 = PointF2(0.0f, 0.0f),
    val topRight:    PointF2 = PointF2(1.0f, 0.0f),
    val bottomRight: PointF2 = PointF2(1.0f, 1.0f),
    val bottomLeft:  PointF2 = PointF2(0.0f, 1.0f)
)

// ── Per-image data ────────────────────────────────────────────────────────────
data class ImageItem(
    val id: String,
    val uri: Uri,
    val path: String,           // current path (may be filtered/cropped temp)
    val originalPath: String,   // original untouched path
    val name: String,
    val size: Long,
    val thumbnail: Bitmap? = null,
    val filter: ImageFilter = ImageFilter.NONE,
    val filteredPreview: Bitmap? = null,
    val penStrokes: List<PenStroke> = emptyList(),
    val redoStrokes: List<PenStroke> = emptyList(),  // redo stack
    val quadCrop: QuadCrop = QuadCrop(),
    val rotation: Int = 0  // 0, 90, 180, 270
)

enum class PdfMode { FULL_PAGE, WITH_MARGINS }

// ── Screen stages ─────────────────────────────────────────────────────────────
enum class ImageToPdfStage { PICK_SOURCE, REVIEW, EDITING, CONVERTING, DONE }

data class ImageToPdfState(
    val stage: ImageToPdfStage = ImageToPdfStage.PICK_SOURCE,
    val images: List<ImageItem> = emptyList(),
    val outputFileName: String = "",
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
    val result: ImageToPdfResult? = null,
    // Editor state
    val activeTab: EditorTab = EditorTab.FILTERS,
    val filterEditorImageId: String? = null,
    // Pen state
    val penColor: Color = Color.Black,
    val penWidth: Float = 6f,
    // Delete confirmation
    val deleteConfirmId: String? = null,
    // Apply filter to all
    val applyFilterToAll: Boolean = false,
    // PDF creation mode
    val pdfMode: PdfMode = PdfMode.FULL_PAGE
)

data class ImageToPdfResult(
    val outputPath: String,
    val pageCount: Int,
    val fileSize: Long
)

class ImageToPdfViewModel(
    private val pdfToolsRepository: PdfToolsRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ImageToPdfState())
    val state: StateFlow<ImageToPdfState> = _state.asStateFlow()

    // Full-res bitmap cache
    private val bitmapCache = mutableMapOf<String, Bitmap>()
    // Tracks the latest pen-preview coroutine per image so we can cancel stale ones
    private val penPreviewJobs = mutableMapOf<String, Job>()

    init { generateDefaultFileName() }

    private fun generateDefaultFileName() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        _state.update { it.copy(outputFileName = "images_$ts") }
    }

    // ── SOURCE SELECTION ──────────────────────────────────────────────────────

    fun addImages(uris: List<Uri>) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val newImages = uris.mapNotNull { uri -> processImage(uri) }
            _state.update { cur ->
                val updated = cur.images + newImages
                cur.copy(
                    images = updated,
                    isLoading = false,
                    stage = if (updated.isNotEmpty()) ImageToPdfStage.REVIEW else cur.stage
                )
            }
        }
    }

    private suspend fun processImage(uri: Uri): ImageItem? = withContext(Dispatchers.IO) {
        try {
            val path = copyUriToCache(uri) ?: return@withContext null
            val file = File(path)
            val thumbnail = generateThumbnail(path)
            val id = "${System.currentTimeMillis()}_${uri.hashCode()}"
            val full = loadFullBitmap(path)
            if (full != null) bitmapCache[id] = full
            ImageItem(
                id = id, uri = uri, path = path, originalPath = path,
                name = getFileNameFromUri(uri) ?: file.name,
                size = file.length(), thumbnail = thumbnail
            )
        } catch (e: Exception) { Timber.e(e); null }
    }

    // ── EDITOR NAVIGATION ─────────────────────────────────────────────────────

    fun openEditor() { _state.update { it.copy(stage = ImageToPdfStage.EDITING) } }
    fun backToReview() { _state.update { it.copy(stage = ImageToPdfStage.REVIEW) } }
    fun setPdfMode(mode: PdfMode) { _state.update { it.copy(pdfMode = mode) } }

    fun rotateImage(imageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val img = _state.value.images.find { it.id == imageId } ?: return@launch
            val newRotation = (img.rotation + 90) % 360

            // Rotate the cached full-res bitmap so filter/compose pipeline uses it
            val cached = bitmapCache[imageId]
            if (cached != null) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(90f)
                val rotated = android.graphics.Bitmap.createBitmap(
                    cached, 0, 0, cached.width, cached.height, matrix, true)
                bitmapCache[imageId] = rotated
                cached.recycle()
            }

            _state.update { cur ->
                cur.copy(images = cur.images.map { i ->
                    if (i.id == imageId) i.copy(
                        rotation = newRotation,
                        filteredPreview = null
                    ) else i
                })
            }
            // Rebuild preview with all edits (filter + crop + new rotation + pen)
            updatePenPreview(imageId)
        }
    }
    fun setActiveTab(tab: EditorTab) { _state.update { it.copy(activeTab = tab) } }

    // ── FILTER ────────────────────────────────────────────────────────────────

    fun openFilterEditor(imageId: String) {
        _state.update { it.copy(filterEditorImageId = imageId) }
    }
    fun closeFilterEditor() { _state.update { it.copy(filterEditorImageId = null) } }

    fun applyFilter(imageId: String, filter: ImageFilter) {
        viewModelScope.launch(Dispatchers.IO) {
            val original = bitmapCache[imageId]
            val filtered = if (original != null && filter != ImageFilter.NONE)
                ImageFilterEngine.applyFilter(original, filter) else null
            val preview = (filtered ?: original)?.let { scaleThumbnail(it, 1200) }
            _state.update { cur ->
                cur.copy(images = cur.images.map { img ->
                    if (img.id == imageId) img.copy(filter = filter, filteredPreview = preview)
                    else img
                })
            }
        }
    }

    fun applyFilterToAllPages(filter: ImageFilter) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedImages = _state.value.images.map { img ->
                val original = bitmapCache[img.id]
                val filtered = if (original != null && filter != ImageFilter.NONE)
                    ImageFilterEngine.applyFilter(original, filter) else null
                val preview = (filtered ?: original)?.let { scaleThumbnail(it, 1200) }
                img.copy(filter = filter, filteredPreview = preview)
            }
            _state.update { it.copy(images = updatedImages) }
        }
    }

    // ── PEN ───────────────────────────────────────────────────────────────────

    fun setPenColor(color: Color) { _state.update { it.copy(penColor = color) } }
    fun setPenWidth(width: Float) { _state.update { it.copy(penWidth = width) } }

    fun addPenStroke(imageId: String, stroke: PenStroke) {
        _state.update { cur ->
            cur.copy(images = cur.images.map { img ->
                if (img.id == imageId) img.copy(
                    penStrokes = img.penStrokes + stroke,
                    redoStrokes = emptyList()  // clear redo on new stroke
                )
                else img
            })
        }
        updatePenPreview(imageId)
    }

    fun undoPenStroke(imageId: String) {
        _state.update { cur ->
            cur.copy(images = cur.images.map { img ->
                if (img.id == imageId && img.penStrokes.isNotEmpty()) {
                    val last = img.penStrokes.last()
                    img.copy(
                        penStrokes = img.penStrokes.dropLast(1),
                        redoStrokes = img.redoStrokes + last
                    )
                } else img
            })
        }
        updatePenPreview(imageId)
    }

    fun redoPenStroke(imageId: String) {
        _state.update { cur ->
            cur.copy(images = cur.images.map { img ->
                if (img.id == imageId && img.redoStrokes.isNotEmpty()) {
                    val next = img.redoStrokes.last()
                    img.copy(
                        penStrokes = img.penStrokes + next,
                        redoStrokes = img.redoStrokes.dropLast(1)
                    )
                } else img
            })
        }
        updatePenPreview(imageId)
    }

    fun clearPenStrokes(imageId: String) {
        _state.update { cur ->
            cur.copy(images = cur.images.map { img ->
                if (img.id == imageId) img.copy(penStrokes = emptyList(), redoStrokes = emptyList()) else img
            })
        }
        updatePenPreview(imageId)
    }

    /**
     * After pen changes, rebuild the filteredPreview bitmap so the editor
     * main view immediately reflects strokes. Runs on IO thread.
     */
    private fun updatePenPreview(imageId: String) {
        penPreviewJobs[imageId]?.cancel()
        penPreviewJobs[imageId] = viewModelScope.launch(Dispatchers.IO) {
            val img = _state.value.images.find { it.id == imageId } ?: return@launch
            val base = bitmapCache[imageId] ?: loadFullBitmap(img.originalPath) ?: return@launch
            // Full pipeline: filter → crop → rotation → pen strokes
            val filtered = if (img.filter != ImageFilter.NONE)
                ImageFilterEngine.applyFilter(base, img.filter)
            else
                base.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val cropped = perspectiveWarp(filtered, img.quadCrop)
            if (filtered !== base && filtered !== cropped) filtered.recycle()
            val rotated = if (img.rotation != 0) {
                val m = android.graphics.Matrix().apply { postRotate(img.rotation.toFloat()) }
                android.graphics.Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, m, true)
                    .also { if (cropped !== base) cropped.recycle() }
            } else cropped
            val result = rotated.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            if (img.penStrokes.isNotEmpty()) {
                val canvas = android.graphics.Canvas(result)
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                for (stroke in img.penStrokes) {
                    if (stroke.points.size < 2) continue
                    paint.color = android.graphics.Color.argb(
                        (stroke.color.alpha * 255).toInt(),
                        (stroke.color.red * 255).toInt(),
                        (stroke.color.green * 255).toInt(),
                        (stroke.color.blue * 255).toInt()
                    )
                    paint.strokeWidth = stroke.strokeWidth * result.width / 1000f
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points[0].x * result.width, stroke.points[0].y * result.height)
                    for (i in 1 until stroke.points.size) {
                        path.lineTo(stroke.points[i].x * result.width, stroke.points[i].y * result.height)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            if (rotated !== result) rotated.recycle()
            val preview = scaleThumbnail(result, 1200)
            if (result !== preview) result.recycle()
            _state.update { cur ->
                cur.copy(images = cur.images.map { i ->
                    if (i.id == imageId) i.copy(filteredPreview = preview) else i
                })
            }
        }
    }

    // ── CROP ─────────────────────────────────────────────────────────────────

    fun setQuadCrop(imageId: String, quad: QuadCrop) {
        _state.update { cur ->
            cur.copy(images = cur.images.map { img ->
                if (img.id == imageId) img.copy(quadCrop = quad) else img
            })
        }
        updateCropPreview(imageId, quad)
    }

    private fun updateCropPreview(imageId: String, quad: QuadCrop) {
        viewModelScope.launch(Dispatchers.IO) {
            val img = _state.value.images.find { it.id == imageId } ?: return@launch
            val base = bitmapCache[imageId] ?: loadFullBitmap(img.originalPath) ?: return@launch
            // Full pipeline: filter → crop → rotation → pen strokes
            val filtered = if (img.filter != ImageFilter.NONE)
                ImageFilterEngine.applyFilter(base, img.filter) else base
            val cropped = perspectiveWarp(filtered, quad)
            if (filtered !== base && filtered !== cropped) filtered.recycle()
            val rotated = if (img.rotation != 0) {
                val m = android.graphics.Matrix().apply { postRotate(img.rotation.toFloat()) }
                android.graphics.Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, m, true)
                    .also { if (cropped !== base) cropped.recycle() }
            } else cropped
            val result = rotated.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            if (img.penStrokes.isNotEmpty()) {
                val canvas = android.graphics.Canvas(result)
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                for (stroke in img.penStrokes) {
                    if (stroke.points.size < 2) continue
                    paint.color = android.graphics.Color.argb(
                        (stroke.color.alpha * 255).toInt(),
                        (stroke.color.red * 255).toInt(),
                        (stroke.color.green * 255).toInt(),
                        (stroke.color.blue * 255).toInt()
                    )
                    paint.strokeWidth = stroke.strokeWidth * result.width / 1000f
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points[0].x * result.width, stroke.points[0].y * result.height)
                    for (i in 1 until stroke.points.size) {
                        path.lineTo(stroke.points[i].x * result.width, stroke.points[i].y * result.height)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            if (rotated !== result) rotated.recycle()
            val preview = scaleThumbnail(result, 1200)
            if (result !== preview) result.recycle()
            _state.update { cur ->
                cur.copy(images = cur.images.map { i ->
                    if (i.id == imageId) i.copy(filteredPreview = preview) else i
                })
            }
        }
    }

    /**
     * Applies perspective warp: maps the 4 user-placed corners of [quad]
     * to the 4 corners of a new output rectangle.
     * Uses Android Matrix.setPolyToPoly() — native and fast.
     */
    fun perspectiveWarp(src: Bitmap, quad: QuadCrop): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()

        // Convert normalised corners to pixel positions
        val tlX = quad.topLeft.x * w;     val tlY = quad.topLeft.y * h
        val trX = quad.topRight.x * w;    val trY = quad.topRight.y * h
        val brX = quad.bottomRight.x * w; val brY = quad.bottomRight.y * h
        val blX = quad.bottomLeft.x * w;  val blY = quad.bottomLeft.y * h

        // Output size = pixel distances between corners
        fun pxDist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1; val dy = y2 - y1
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
        val outW = maxOf(pxDist(tlX,tlY,trX,trY), pxDist(blX,blY,brX,brY)).toInt().coerceAtLeast(1)
        val outH = maxOf(pxDist(tlX,tlY,blX,blY), pxDist(trX,trY,brX,brY)).toInt().coerceAtLeast(1)

        val src4 = floatArrayOf(tlX, tlY, trX, trY, brX, brY, blX, blY)
        val dst4 = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())

        val matrix = android.graphics.Matrix()
        matrix.setPolyToPoly(src4, 0, dst4, 0, 4)

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(src, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }



    // ── DELETE ────────────────────────────────────────────────────────────────

    fun requestDeleteImage(imageId: String) {
        _state.update { it.copy(deleteConfirmId = imageId) }
    }

    fun confirmDeleteImage() {
        val id = _state.value.deleteConfirmId ?: return
        bitmapCache.remove(id)
        _state.update { cur ->
            val updated = cur.images.filter { it.id != id }
            cur.copy(
                images = updated,
                deleteConfirmId = null,
                stage = if (updated.isEmpty()) ImageToPdfStage.PICK_SOURCE else cur.stage
            )
        }
    }

    fun cancelDelete() { _state.update { it.copy(deleteConfirmId = null) } }

    // ── REORDER ───────────────────────────────────────────────────────────────

    fun moveImage(fromIndex: Int, toIndex: Int) {
        _state.update { cur ->
            val list = cur.images.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            cur.copy(images = list)
        }
    }

    // ── CONVERT ───────────────────────────────────────────────────────────────

    fun setOutputFileName(name: String) { _state.update { it.copy(outputFileName = name) } }

    fun convertToPdf() {
        val cur = _state.value
        if (cur.images.isEmpty()) { _state.update { it.copy(error = "Add at least one image") }; return }
        if (cur.outputFileName.isBlank()) { _state.update { it.copy(error = "Enter a file name") }; return }

        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, progress = 0f, error = null) }

            val outputDir = getOutputDirectory()
            var outputPath = "$outputDir/${cur.outputFileName}.pdf"
            var counter = 1
            while (File(outputPath).exists()) {
                outputPath = "$outputDir/${cur.outputFileName}_$counter.pdf"
                counter++
            }

            // Compose filter + pen + crop into final image files
            val finalPaths = withContext(Dispatchers.IO) {
                cur.images.mapIndexed { idx, img ->
                    withContext(Dispatchers.Main) {
                        _state.update { it.copy(progress = idx.toFloat() / cur.images.size * 0.6f) }
                    }
                    composeFinalImage(img)
                }
            }

            val mode = _state.value.pdfMode
            val result = pdfToolsRepository.imagesToPdf(
                imagePaths = finalPaths,
                outputPath = outputPath,
                onProgress = { p: Float -> _state.update { it.copy(progress = 0.6f + p * 0.4f) } }
            )

            // Clean up temp files
            withContext(Dispatchers.IO) {
                finalPaths.filter { it.contains("compose_temp") }.forEach { File(it).delete() }
            }

            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isProcessing = false, progress = 1f,
                            stage = ImageToPdfStage.DONE,
                            result = ImageToPdfResult(outputPath, cur.images.size, File(outputPath).length())
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(isProcessing = false, error = e.message ?: "Failed") }
                }
            )
        }
    }

    /**
     * Compose filter + pen strokes + crop into a single JPEG file.
     * Returns the path of the composed image.
     */
    private suspend fun composeFinalImage(img: ImageItem): String = withContext(Dispatchers.IO) {
        // Always load from original file at full resolution for PDF output
        // Do NOT use bitmapCache — it may be downsampled
        val original = BitmapFactory.decodeFile(img.originalPath)
            ?: return@withContext img.originalPath

        // Work on a mutable copy
        val workBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()

        // 1. Apply filter
        val filtered = if (img.filter != ImageFilter.NONE)
            ImageFilterEngine.applyFilter(workBitmap, img.filter)
        else workBitmap

        // 2. Apply perspective crop (warp always produces a NEW bitmap)
        val cropped = perspectiveWarp(filtered, img.quadCrop)

        // 3. Apply rotation
        val rotated = if (img.rotation != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(img.rotation.toFloat())
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        } else cropped

        // 4. Draw pen strokes on top
        val composed = rotated.copy(Bitmap.Config.ARGB_8888, true)
        if (img.penStrokes.isNotEmpty()) {
            val canvas = Canvas(composed)
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (stroke in img.penStrokes) {
                if (stroke.points.size < 2) continue
                paint.color = android.graphics.Color.argb(
                    (stroke.color.alpha * 255).toInt(),
                    (stroke.color.red * 255).toInt(),
                    (stroke.color.green * 255).toInt(),
                    (stroke.color.blue * 255).toInt()
                )
                paint.strokeWidth = stroke.strokeWidth * composed.width / 1000f
                val path = android.graphics.Path()
                path.moveTo(stroke.points[0].x * composed.width, stroke.points[0].y * composed.height)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x * composed.width, stroke.points[i].y * composed.height)
                }
                canvas.drawPath(path, paint)
            }
        }

        // 5. Write to temp file
        val tempFile = File(context.cacheDir, "compose_temp/${img.id}_final.jpg")
        tempFile.parentFile?.mkdirs()
        FileOutputStream(tempFile).use { composed.compress(Bitmap.CompressFormat.JPEG, 92, it) }

        // Safe to recycle intermediate bitmaps that are not the original or composed output
        if (filtered !== workBitmap) filtered.recycle()
        workBitmap.recycle()
        // cropped and rotated are intermediate — recycle if not same reference as composed
        if (cropped !== composed && cropped !== rotated) cropped.recycle()
        if (rotated !== composed) rotated.recycle()

        tempFile.absolutePath
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private fun loadFullBitmap(path: String): Bitmap? = try {
        // Load at full resolution — no downsampling for final output quality
        BitmapFactory.decodeFile(path)
    } catch (e: Exception) { null }

    private fun scaleThumbnail(src: Bitmap, px: Int): Bitmap {
        val scale = px.toFloat() / maxOf(src.width, src.height)
        return if (scale >= 1f) src
        else Bitmap.createScaledBitmap(src, (src.width*scale).toInt(), (src.height*scale).toInt(), true)
    }

    private fun generateThumbnail(path: String): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        val s = maxOf(o.outWidth / 1200, o.outHeight / 1200).coerceAtLeast(1)
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Exception) { null }

    private suspend fun copyUriToCache(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") return@withContext uri.path
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val fileName = getFileNameFromUri(uri) ?: "image_${System.currentTimeMillis()}.jpg"
            val cacheFile = File(context.cacheDir, "imagetopdf_temp/$fileName")
            cacheFile.parentFile?.mkdirs()
            cacheFile.outputStream().use { inputStream.copyTo(it) }
            cacheFile.absolutePath
        } catch (e: Exception) { null }
    }

    private fun getFileNameFromUri(uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val i = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && i >= 0) cursor.getString(i) else null
        }
    } catch (e: Exception) { null }

    fun createCameraImageFile(): File {
        val dir = File(context.filesDir, "camera_images").also { it.mkdirs() }
        return File(dir, "camera_${System.currentTimeMillis()}.jpg")
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    fun reset() {
        bitmapCache.values.forEach { it.recycle() }
        bitmapCache.clear()
        _state.update { ImageToPdfState() }
        generateDefaultFileName()
    }

    private fun getOutputDirectory(): String {
        val dir = File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS), "Kosh")
        if (!dir.exists()) dir.mkdirs()
        return dir.absolutePath
    }

    override fun onCleared() {
        super.onCleared()
        penPreviewJobs.values.forEach { it.cancel() }
        penPreviewJobs.clear()
        bitmapCache.values.forEach { it.recycle() }
        bitmapCache.clear()
    }
}
