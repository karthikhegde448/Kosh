package com.rejowan.pdfreaderpro.presentation.screens.tools.imagetopdf

import android.graphics.ColorMatrix as AndroidColorMatrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.filled.ZoomOut
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.navigation.NavController
import sh.calvin.reorderable.*
import kotlin.math.abs

private val AccentTeal   = Color(0xFF26A69A)
private val AccentRed    = Color(0xFFEF5350)
private val AccentGreen  = Color(0xFF81C784)
private val AccentOrange = Color(0xFFFFB74D)
private val AccentPurple = Color(0xFFE87722)

private val PEN_COLORS = listOf(
    Color.Black, Color.White, AccentRed,
    Color.Blue, AccentGreen, AccentOrange, AccentPurple
)

private enum class RightMenu { NONE, VISIBLE }
private enum class BottomPanel { NONE, FILTERS, REORDER, CROP, PEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    state: ImageToPdfState,
    viewModel: ImageToPdfViewModel,
    navController: NavController,
    onAddCamera: () -> Unit,
    onAddGallery: () -> Unit
) {
    var currentPageIndex by remember { mutableIntStateOf(0) }
    val safeIndex = currentPageIndex.coerceIn(0, (state.images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState { state.images.size }

    // Keep pagerState and currentPageIndex in sync
    LaunchedEffect(pagerState.currentPage) {
        currentPageIndex = pagerState.currentPage
    }
    LaunchedEffect(currentPageIndex) {
        if (pagerState.currentPage != currentPageIndex) {
            pagerState.animateScrollToPage(currentPageIndex)
        }
    }

    var rightMenu by remember { mutableStateOf(RightMenu.NONE) }
    var bottomPanel by remember { mutableStateOf(BottomPanel.NONE) }
    var showConvertDialog by remember { mutableStateOf(false) }
    var showPageStrip by remember { mutableStateOf(false) }    // Fix 2: hidden by default
    var showExitConfirm by remember { mutableStateOf(false) }  // Fix 1: back confirmation

    val currentImage = state.images.getOrNull(safeIndex)

    // Fix 1: intercept back — show confirm dialog
    fun handleBack() {
        when {
            bottomPanel != BottomPanel.NONE -> bottomPanel = BottomPanel.NONE
            rightMenu   != RightMenu.NONE   -> rightMenu   = RightMenu.NONE
            else -> showExitConfirm = true
        }
    }

    // Intercept system/hardware/gesture back press
    BackHandler { handleBack() }

    Box(Modifier.fillMaxSize()) {

        // ── MAIN IMAGE DISPLAY — swipe left/right to navigate pages ──────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().background(Color.Black),
            userScrollEnabled = bottomPanel == BottomPanel.NONE
        ) { page ->
            val img = state.images.getOrNull(page)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (img != null) {
                    val bmp = img.filteredPreview ?: img.thumbnail
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), null,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
        }

        // ── TOP BAR ───────────────────────────────────────────────────────
        TopEditorBar(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            pageNum = safeIndex + 1,
            total = state.images.size,
            onBack = { handleBack() },
            onAddCamera = { rightMenu = RightMenu.NONE; onAddCamera() },
            onAddGallery = { rightMenu = RightMenu.NONE; onAddGallery() },
            onTogglePageStrip = { showPageStrip = !showPageStrip },  // Fix 2: toggle
            onMore = { rightMenu = if (rightMenu == RightMenu.VISIBLE) RightMenu.NONE else RightMenu.VISIBLE },
            onDone = { showConvertDialog = true }
        )

        // ── FIX 2: Page strip - only when toggled ON ──────────────────────
        AnimatedVisibility(
            visible = showPageStrip && state.images.size > 1 && bottomPanel == BottomPanel.NONE,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .padding(top = 52.dp, bottom = 80.dp, start = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(state.images) { idx, img ->
                    val sel = idx == safeIndex
                    val bmp = img.filteredPreview ?: img.thumbnail
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(6.dp))
                            .border(if (sel) 2.dp else 0.dp, AccentTeal, RoundedCornerShape(6.dp))
                            .clickable { currentPageIndex = idx }
                    ) {
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), null,
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                        }
                        Text("${idx+1}",
                            Modifier.align(Alignment.BottomCenter)
                                .background(Color.Black.copy(0.5f)).fillMaxWidth().padding(1.dp),
                            fontSize = 8.sp, color = Color.White, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // ── FIX 3: Right popup — centered, not overlapping left strip ─────
        if (rightMenu == RightMenu.VISIBLE) {
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null) { rightMenu = RightMenu.NONE }
            )
            // Centered vertically and horizontally
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(min = 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF0202020))
                    .padding(vertical = 8.dp)
            ) {
                RightMenuItem("Crop", Icons.Default.Crop) {
                    rightMenu = RightMenu.NONE; bottomPanel = BottomPanel.CROP
                }
                HorizontalDivider(color = Color.White.copy(0.08f))
                RightMenuItem("Rotate", Icons.Default.RotateRight) {
                    rightMenu = RightMenu.NONE
                    currentImage?.let { viewModel.rotateImage(it.id) }
                }
                HorizontalDivider(color = Color.White.copy(0.08f))
                RightMenuItem("Delete", Icons.Default.Delete, color = AccentRed) {
                    rightMenu = RightMenu.NONE
                    currentImage?.let { viewModel.requestDeleteImage(it.id) }
                }
            }
        }

        // ── BOTTOM TOOLBAR ────────────────────────────────────────────────
        if (bottomPanel == BottomPanel.NONE) {
            BottomToolbar(
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                onFilters = { bottomPanel = BottomPanel.FILTERS },
                onReorder = { bottomPanel = BottomPanel.REORDER },
                onPen = { bottomPanel = BottomPanel.PEN }
            )
        }

        // ── PANELS ────────────────────────────────────────────────────────
        when (bottomPanel) {
            BottomPanel.FILTERS -> FiltersPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                images = state.images, currentIndex = safeIndex,
                onPageSelected = { currentPageIndex = it },
                onApplyFilter = { id, f -> viewModel.applyFilter(id, f) },
                onApplyToAll = { f -> viewModel.applyFilterToAllPages(f) },
                onClose = { bottomPanel = BottomPanel.NONE }
            )
            BottomPanel.REORDER -> ReorderPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                images = state.images,
                onMove = { from, to -> viewModel.moveImage(from, to) },
                onClose = { bottomPanel = BottomPanel.NONE }
            )
            BottomPanel.CROP -> if (currentImage != null) {
                // Fix 4: full freestyle crop overlay
                CropOverlayFull(
                    image = currentImage,
                    onCropDone = { rect ->
                        viewModel.setQuadCrop(currentImage.id, rect)
                        bottomPanel = BottomPanel.NONE
                    },
                    onCancel = { bottomPanel = BottomPanel.NONE }
                )
            }
            BottomPanel.PEN -> if (currentImage != null) {
                // Fix 5: redo, dim when empty, preview update
                PenOverlay(
                    image = currentImage,
                    penColor = state.penColor, penWidth = state.penWidth,
                    onColorChange = { viewModel.setPenColor(it) },
                    onWidthChange = { viewModel.setPenWidth(it) },
                    onStrokeAdded = { viewModel.addPenStroke(currentImage.id, it) },
                    onUndo = { viewModel.undoPenStroke(currentImage.id) },
                    onRedo = { viewModel.redoPenStroke(currentImage.id) },
                    onClear = { viewModel.clearPenStrokes(currentImage.id) },
                    onClose = { bottomPanel = BottomPanel.NONE }
                )
            }
            BottomPanel.NONE -> {}
        }
    }

    // ── FIX 1: Exit confirmation dialog ───────────────────────────────────
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Go back?") },
            text = { Text("Are you sure you want to go back? Unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    viewModel.backToReview()
                }) { Text("Yes", color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("No") }
            }
        )
    }

    // ── Delete confirmation ───────────────────────────────────────────────
    state.deleteConfirmId?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete Page?") },
            text = { Text("Are you sure? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDeleteImage() },
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            }
        )
    }

    // ── Convert dialog ────────────────────────────────────────────────────
    if (showConvertDialog) {
        ConvertDialog(
            outputFileName = state.outputFileName, imageCount = state.images.size,
            pdfMode = state.pdfMode, isProcessing = state.isProcessing, progress = state.progress,
            onFileNameChange = { viewModel.setOutputFileName(it) },
            onModeChange = { viewModel.setPdfMode(it) },
            onConfirm = { viewModel.convertToPdf(); showConvertDialog = false },
            onDismiss = { showConvertDialog = false }
        )
    }
}

// ── TOP BAR ───────────────────────────────────────────────────────────────────

@Composable
private fun TopEditorBar(
    modifier: Modifier = Modifier,
    pageNum: Int, total: Int,
    onBack: () -> Unit, onAddCamera: () -> Unit, onAddGallery: () -> Unit,
    onTogglePageStrip: () -> Unit,
    onMore: () -> Unit, onDone: () -> Unit
) {
    Row(
        modifier.fillMaxWidth().background(Color.Black.copy(0.6f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, Modifier.size(44.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
        // Fix 2: page strip toggle button (layers icon)
        IconButton(onClick = onTogglePageStrip, Modifier.size(44.dp)) {
            Icon(Icons.Default.Layers, null, tint = Color.White)
        }
        Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(0.55f)) {
            Text("$pageNum/$total", Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onAddCamera, Modifier.size(44.dp)) {
            Icon(Icons.Default.CameraAlt, null, tint = Color.White)
        }
        IconButton(onClick = onAddGallery, Modifier.size(44.dp)) {
            Icon(Icons.Default.Add, null, tint = Color.White)
        }
        IconButton(onClick = onMore, Modifier.size(44.dp)) {
            Icon(Icons.Default.MoreVert, null, tint = Color.White)
        }
        TextButton(onClick = onDone, Modifier.padding(end = 4.dp)) {
            Text("Done", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun RightMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
        Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

// ── BOTTOM TOOLBAR ────────────────────────────────────────────────────────────

@Composable
private fun BottomToolbar(modifier: Modifier, onFilters: () -> Unit, onReorder: () -> Unit, onPen: () -> Unit) {
    Row(modifier.fillMaxWidth().background(Color.Black.copy(0.75f)).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        ToolbarTab(Icons.Default.FilterAlt, "Filters", onFilters)
        ToolbarTab(Icons.Default.SwapVert, "Reorder", onReorder)
        ToolbarTab(Icons.Default.Edit, "Pen", onPen)
    }
}

@Composable
private fun ToolbarTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, onClick: () -> Unit
) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = Color.White)
    }
}

// ── FILTERS PANEL ─────────────────────────────────────────────────────────────

@Composable
private fun FiltersPanel(
    modifier: Modifier,
    images: List<ImageItem>, currentIndex: Int,
    onPageSelected: (Int) -> Unit,
    onApplyFilter: (String, ImageFilter) -> Unit,
    onApplyToAll: (ImageFilter) -> Unit,
    onClose: () -> Unit
) {
    val current = images.getOrNull(currentIndex)
    var selected by remember(current?.id) { mutableStateOf(current?.filter ?: ImageFilter.NONE) }

    Column(modifier.fillMaxWidth().background(Color.Black.copy(0.9f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Filters", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            TextButton(onClick = { onApplyToAll(selected) }) {
                Text("Apply to all", color = AccentTeal, fontSize = 13.sp)
            }
            IconButton(onClick = onClose, Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().height(140.dp).navigationBarsPadding()
        ) {
            items(ImageFilter.entries) { filter ->
                val sel = selected == filter
                val bmp = current?.thumbnail
                Column(
                    Modifier.width(76.dp).clickable {
                        selected = filter
                        current?.let { onApplyFilter(it.id, filter) }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.size(66.dp).clip(RoundedCornerShape(6.dp))
                        .border(if (sel) 2.dp else 0.dp, AccentTeal, RoundedCornerShape(6.dp))) {
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), null,
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                colorFilter = filterPreviewColorFilter(filter))
                        } else {
                            Box(Modifier.fillMaxSize().background(filter.accentColor.copy(0.3f)))
                        }
                        if (sel) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.15f)))
                            Icon(Icons.Default.CheckCircle, null,
                                Modifier.align(Alignment.Center).size(20.dp), tint = AccentTeal)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(filter.label, color = if (sel) AccentTeal else Color.White,
                        fontSize = 11.sp, textAlign = TextAlign.Center,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp))
                }
            }
        }
    }
}

private fun filterPreviewColorFilter(f: ImageFilter): ColorFilter? {
    fun acm(v: FloatArray) = ColorFilter.colorMatrix(ColorMatrix(v))
    fun sat(s: Float): ColorFilter {
        val m = AndroidColorMatrix(); m.setSaturation(s)
        return ColorFilter.colorMatrix(ColorMatrix(m.array))
    }
    return when (f) {
        ImageFilter.NONE -> null
        ImageFilter.MONO, ImageFilter.GRAYSCALE, ImageFilter.BW, ImageFilter.WHITEBOARD -> sat(0f)
        ImageFilter.LOMOISH -> sat(1.8f)
        ImageFilter.PHOTO   -> sat(1.5f)
        ImageFilter.SEPIA   -> acm(floatArrayOf(
            0.393f,0.769f,0.189f,0f,0f, 0.349f,0.686f,0.168f,0f,0f,
            0.272f,0.534f,0.131f,0f,0f, 0f,0f,0f,1f,0f))
        ImageFilter.NEGATIVE -> acm(floatArrayOf(
            -1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))
        else -> null
    }
}

// ── REORDER PANEL ─────────────────────────────────────────────────────────────

@Composable
private fun ReorderPanel(
    modifier: Modifier, images: List<ImageItem>,
    onMove: (Int, Int) -> Unit, onClose: () -> Unit
) {
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to -> onMove(from.index, to.index) }

    Column(modifier.fillMaxWidth().fillMaxHeight(0.55f).background(Color.Black.copy(0.92f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Reorder — long-press & drag", color = Color.White, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onClose, Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4), state = gridState,
            modifier = Modifier.weight(1f).navigationBarsPadding(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(images, key = { _, img -> img.id }) { idx, img ->
                ReorderableItem(reorderableState, key = img.id) { isDragging ->
                    Box(Modifier.aspectRatio(0.75f).clip(RoundedCornerShape(6.dp))
                        .border(if (isDragging) 2.dp else 0.dp, AccentTeal, RoundedCornerShape(6.dp))
                        .longPressDraggableHandle()) {
                        val bmp = img.filteredPreview ?: img.thumbnail
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize().background(Color(0x44FFFFFF)))
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color.Black.copy(0.5f)).padding(2.dp),
                            contentAlignment = Alignment.Center) {
                            Text("${idx+1}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.DragHandle, null,
                            Modifier.align(Alignment.TopEnd).size(16.dp).padding(2.dp),
                            tint = Color.White.copy(0.8f))
                    }
                }
            }
        }
    }
}

// ── CROP OVERLAY — corners as small white circles, mid-edge bars are draggable ─

@Composable
private fun CropOverlayFull(
    image: ImageItem,
    onCropDone: (QuadCrop) -> Unit,
    onCancel: () -> Unit
) {
    val bmp = image.thumbnail ?: return

    var tl by remember { mutableStateOf(image.quadCrop.topLeft) }
    var tr by remember { mutableStateOf(image.quadCrop.topRight) }
    var br by remember { mutableStateOf(image.quadCrop.bottomRight) }
    var bl by remember { mutableStateOf(image.quadCrop.bottomLeft) }

    // active: 0=TL 1=TR 2=BR 3=BL 4=midTop 5=midRight 6=midBottom 7=midLeft -1=none
    var active by remember { mutableIntStateOf(-1) }
    var imgRect by remember { mutableStateOf(android.graphics.RectF(0f, 0f, 1f, 1f)) }

    val cornerHitPx = 56f
    val midHitPx    = 48f

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val r = imgRect
                            fun toC(p: PointF2) = Offset(r.left + p.x * r.width(), r.top + p.y * r.height())
                            val corners = listOf(toC(tl), toC(tr), toC(br), toC(bl))
                            val mT = Offset((corners[0].x+corners[1].x)/2, (corners[0].y+corners[1].y)/2)
                            val mR = Offset((corners[1].x+corners[2].x)/2, (corners[1].y+corners[2].y)/2)
                            val mB = Offset((corners[3].x+corners[2].x)/2, (corners[3].y+corners[2].y)/2)
                            val mL = Offset((corners[0].x+corners[3].x)/2, (corners[0].y+corners[3].y)/2)
                            fun dist(a: Offset, b: Offset) = kotlin.math.sqrt((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y).toDouble()).toFloat()
                            val ci = corners.indexOfFirst { dist(it, pos) < cornerHitPx }
                            active = if (ci >= 0) ci else {
                                val mi = listOf(mT,mR,mB,mL).indexOfFirst { dist(it, pos) < midHitPx }
                                if (mi >= 0) mi + 4 else -1
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (active < 0) return@detectDragGestures
                            val r = imgRect
                            val nx = ((change.position.x - r.left) / r.width()).coerceIn(0f, 1f)
                            val ny = ((change.position.y - r.top)  / r.height()).coerceIn(0f, 1f)
                            when (active) {
                                0 -> tl = PointF2(nx, ny)
                                1 -> tr = PointF2(nx, ny)
                                2 -> br = PointF2(nx, ny)
                                3 -> bl = PointF2(nx, ny)
                                4 -> { tl = PointF2(tl.x, ny); tr = PointF2(tr.x, ny) }
                                5 -> { tr = PointF2(nx, tr.y); br = PointF2(nx, br.y) }
                                6 -> { bl = PointF2(bl.x, ny); br = PointF2(br.x, ny) }
                                7 -> { tl = PointF2(nx, tl.y); bl = PointF2(nx, bl.y) }
                            }
                        },
                        onDragEnd    = { active = -1 },
                        onDragCancel = { active = -1 }
                    )
                }
        ) {
            val cW = size.width; val cH = size.height
            val padS = 52.dp.toPx(); val padT = 88.dp.toPx(); val padB = 96.dp.toPx()
            val availW = cW - padS * 2; val availH = cH - padT - padB
            val imgW = bmp.width.toFloat(); val imgH = bmp.height.toFloat()
            val scale = minOf(availW / imgW, availH / imgH)
            val rW = imgW * scale; val rH = imgH * scale
            val rL = padS + (availW - rW) / 2f; val rT = padT + (availH - rH) / 2f
            imgRect = android.graphics.RectF(rL, rT, rL + rW, rT + rH)

            drawContext.canvas.nativeCanvas.drawBitmap(
                bmp,
                android.graphics.Rect(0, 0, bmp.width, bmp.height),
                android.graphics.RectF(rL, rT, rL+rW, rT+rH),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            )

            fun cx(p: PointF2) = Offset(rL + p.x * rW, rT + p.y * rH)
            val pTL = cx(tl); val pTR = cx(tr); val pBR = cx(br); val pBL = cx(bl)

            val dim = Color.Black.copy(0.6f)
            drawPath(Path().apply { moveTo(rL,rT); lineTo(rL+rW,rT); lineTo(pTR.x,pTR.y); lineTo(pTL.x,pTL.y); close() }, dim)
            drawPath(Path().apply { moveTo(rL+rW,rT); lineTo(rL+rW,rT+rH); lineTo(pBR.x,pBR.y); lineTo(pTR.x,pTR.y); close() }, dim)
            drawPath(Path().apply { moveTo(rL+rW,rT+rH); lineTo(rL,rT+rH); lineTo(pBL.x,pBL.y); lineTo(pBR.x,pBR.y); close() }, dim)
            drawPath(Path().apply { moveTo(rL,rT+rH); lineTo(rL,rT); lineTo(pTL.x,pTL.y); lineTo(pBL.x,pBL.y); close() }, dim)
            drawRect(Color.Black.copy(0.92f), Offset(0f,0f), androidx.compose.ui.geometry.Size(cW, rT))
            drawRect(Color.Black.copy(0.92f), Offset(0f,rT+rH), androidx.compose.ui.geometry.Size(cW, cH-rT-rH))
            drawRect(Color.Black.copy(0.92f), Offset(0f,rT), androidx.compose.ui.geometry.Size(rL, rH))
            drawRect(Color.Black.copy(0.92f), Offset(rL+rW,rT), androidx.compose.ui.geometry.Size(cW-rL-rW, rH))

            drawPath(Path().apply {
                moveTo(pTL.x,pTL.y); lineTo(pTR.x,pTR.y); lineTo(pBR.x,pBR.y); lineTo(pBL.x,pBL.y); close()
            }, Color.White, style = Stroke(width = 2.dp.toPx()))

            // Mid-edge draggable bars
            val midT = Offset((pTL.x+pTR.x)/2, (pTL.y+pTR.y)/2)
            val midR = Offset((pTR.x+pBR.x)/2, (pTR.y+pBR.y)/2)
            val midB = Offset((pBL.x+pBR.x)/2, (pBL.y+pBR.y)/2)
            val midL = Offset((pTL.x+pBL.x)/2, (pTL.y+pBL.y)/2)
            val barLong = 28.dp.toPx(); val barShort = 5.dp.toPx(); val barRad = 3.dp.toPx()
            // Top & Bottom: horizontal bar
            listOf(midT to (active==4), midB to (active==6)).forEach { (pt, isAct) ->
                drawRoundRect(
                    if (isAct) Color.White else Color.White.copy(0.9f),
                    topLeft = Offset(pt.x - barLong/2, pt.y - barShort/2),
                    size = androidx.compose.ui.geometry.Size(barLong, barShort),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barRad)
                )
            }
            // Left & Right: vertical bar
            listOf(midL to (active==7), midR to (active==5)).forEach { (pt, isAct) ->
                drawRoundRect(
                    if (isAct) Color.White else Color.White.copy(0.9f),
                    topLeft = Offset(pt.x - barShort/2, pt.y - barLong/2),
                    size = androidx.compose.ui.geometry.Size(barShort, barLong),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barRad)
                )
            }

            // Corner handles — small solid white circles
            val cr = 7.dp.toPx()
            listOf(pTL, pTR, pBR, pBL).forEachIndexed { i, pt ->
                if (i == active) drawCircle(Color.White.copy(0.25f), cr + 8.dp.toPx(), pt)
                drawCircle(Color.White, cr, pt)
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(0.85f)).navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, null, Modifier.size(28.dp), tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Drag corners or edges to crop", color = Color.White.copy(0.7f), fontSize = 11.sp)
                TextButton(onClick = {
                    tl = PointF2(0f,0f); tr = PointF2(1f,0f)
                    br = PointF2(1f,1f); bl = PointF2(0f,1f)
                }) { Text("Reset", color = AccentTeal, fontSize = 12.sp) }
            }
            IconButton(onClick = {
                onCropDone(QuadCrop(topLeft=tl, topRight=tr, bottomRight=br, bottomLeft=bl))
            }) {
                Icon(Icons.Default.Check, null, Modifier.size(28.dp), tint = AccentPurple)
            }
        }
    }
}

// ── PEN OVERLAY — instant live drawing + two-finger zoom ─────────────────────

@Composable
private fun PenOverlay(
    image: ImageItem,
    penColor: Color, penWidth: Float,
    onColorChange: (Color) -> Unit, onWidthChange: (Float) -> Unit,
    onStrokeAdded: (PenStroke) -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit,
    onClear: () -> Unit, onClose: () -> Unit
) {
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Zoom state: scale and pan offset in canvas pixels
    var zoom   by remember { mutableFloatStateOf(1f) }
    var panX   by remember { mutableFloatStateOf(0f) }
    var panY   by remember { mutableFloatStateOf(0f) }

    val bmp = image.filteredPreview ?: image.thumbnail
    val canUndo = image.penStrokes.isNotEmpty()
    val canRedo = image.redoStrokes.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier
            .fillMaxSize()
            .pointerInput(penColor, penWidth, bmp) {
                // Two-pointer state
                var prevDist = 0f
                var prevMidX = 0f
                var prevMidY = 0f
                var isZooming = false

                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    firstDown.consume()

                    // Check almost immediately if a second finger comes down
                    val secondDown = withTimeoutOrNull(80L) {
                        var ev = awaitPointerEvent()
                        while (ev.changes.size < 2) {
                            if (ev.changes.none { it.pressed }) return@withTimeoutOrNull null
                            ev = awaitPointerEvent()
                        }
                        ev
                    }

                    if (secondDown != null && secondDown.changes.size >= 2) {
                        // ── ZOOM MODE ─────────────────────────────────────────
                        isZooming = true
                        val p0 = secondDown.changes[0]
                        val p1 = secondDown.changes[1]
                        prevDist = kotlin.math.sqrt(
                            (p0.position.x - p1.position.x).let { it * it } +
                            (p0.position.y - p1.position.y).let { it * it }.toDouble()
                        ).toFloat()
                        prevMidX = (p0.position.x + p1.position.x) / 2f
                        prevMidY = (p0.position.y + p1.position.y) / 2f
                        currentPoints = emptyList()

                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size < 2) break
                            val a = pressed[0]; val b = pressed[1]
                            a.consume(); b.consume()
                            val dist = kotlin.math.sqrt(
                                (a.position.x - b.position.x).let { it * it } +
                                (a.position.y - b.position.y).let { it * it }.toDouble()
                            ).toFloat()
                            val midX = (a.position.x + b.position.x) / 2f
                            val midY = (a.position.y + b.position.y) / 2f
                            if (prevDist > 0f) {
                                val scaleFactor = dist / prevDist
                                val newZoom = (zoom * scaleFactor).coerceIn(1f, 6f)
                                val actualScale = newZoom / zoom

                                // Correct zoom-toward-pinch:
                                // panX/panY shift so the point under the pinch midpoint
                                // stays fixed on screen after the scale change.
                                // panX = midX - (midX - panX) * scale  ← wrong: midX is screen coord
                                // Correct: the rendered origin is at (baseL*zoom + panX, baseT*zoom + panY)
                                // After zoom, we want: midX = baseL*newZoom + newPanX + (midX - baseL*zoom - panX) * actualScale
                                // Solving: newPanX = midX*(1 - actualScale) + panX*actualScale - baseL*(newZoom - zoom*actualScale)
                                // Since newZoom = zoom*actualScale: newPanX = midX*(1-actualScale) + panX*actualScale
                                panX = midX * (1f - actualScale) + panX * actualScale
                                panY = midY * (1f - actualScale) + panY * actualScale
                                zoom = newZoom
                                // Apply translation from finger movement
                                panX += midX - prevMidX
                                panY += midY - prevMidY
                            }
                            prevDist = dist
                        prevMidX = midX
                        prevMidY = midY
                        } while (true)
                        isZooming = false

                    } else {
                        // ── DRAW MODE ─────────────────────────────────────────
                        // Compute base image rect (ContentScale.Fit, no zoom)
                        val cw = size.width.toFloat()
                        val ch = size.height.toFloat()
                        val iw = bmp?.width?.toFloat() ?: cw
                        val ih = bmp?.height?.toFloat() ?: ch
                        val fitScale = minOf(cw / iw, ch / ih)
                        val sw = iw * fitScale; val sh = ih * fitScale
                        val baseL = (cw - sw) / 2f; val baseT = (ch - sh) / 2f

                        // Actual rendered rect after zoom+pan
                        val rL = baseL * zoom + panX
                        val rT = baseT * zoom + panY
                        val rW = sw * zoom; val rH = sh * zoom

                        fun norm(pos: Offset) = Offset(
                            ((pos.x - rL) / rW).coerceIn(0f, 1f),
                            ((pos.y - rT) / rH).coerceIn(0f, 1f)
                        )
                        currentPoints = listOf(norm(firstDown.position))
                        do {
                            val event = awaitPointerEvent()
                            // If a second finger appears mid-stroke, cancel stroke and start zoom
                            if (event.changes.count { it.pressed } >= 2) {
                                currentPoints = emptyList()
                                break
                            }
                            val drag = event.changes.firstOrNull { it.id == firstDown.id } ?: break
                            if (drag.isConsumed) break
                            drag.consume()
                            currentPoints = currentPoints + norm(drag.position)
                        } while (event.changes.any { it.pressed })

                        if (currentPoints.size >= 2)
                            onStrokeAdded(PenStroke(currentPoints, penColor, penWidth))
                        currentPoints = emptyList()
                    }
                }
            }
        ) {
            val cw = size.width; val ch = size.height
            val iw = bmp?.width?.toFloat() ?: cw
            val ih = bmp?.height?.toFloat() ?: ch
            val fitScale = if (bmp != null) minOf(cw / iw, ch / ih) else 1f
            val sw = iw * fitScale; val sh = ih * fitScale
            val baseL = (cw - sw) / 2f; val baseT = (ch - sh) / 2f

            // Clamp pan so image edges never go past screen edges
            // rL = baseL*zoom + panX should be <= 0 when zoomed (left edge at or left of screen)
            // rL + rW = baseL*zoom + panX + sw*zoom should be >= cw (right edge at or right of screen)
            if (zoom > 1f) {
                val rL = baseL * zoom + panX
                val rT = baseT * zoom + panY
                val rW = sw * zoom
                val rH = sh * zoom
                // Don't allow left edge to move right of 0, or right edge left of cw
                if (rL > 0f) panX -= rL
                if (rL + rW < cw) panX += cw - (rL + rW)
                if (rT > 0f) panY -= rT
                if (rT + rH < ch) panY += ch - (rT + rH)
            } else {
                panX = 0f
                panY = 0f
            }

            // Rendered image rect after zoom+pan
            val rL = baseL * zoom + panX
            val rT = baseT * zoom + panY
            val rW = sw * zoom; val rH = sh * zoom

            // Draw background black
            drawRect(Color.Black)

            // Draw the base image (filteredPreview with all previously committed strokes)
            if (bmp != null) {
                drawContext.canvas.nativeCanvas.drawBitmap(
                    bmp,
                    android.graphics.Rect(0, 0, bmp.width, bmp.height),
                    android.graphics.RectF(rL, rT, rL + rW, rT + rH),
                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                )
            }

            // Draw live current stroke in progress — instant, no async delay
            if (currentPoints.size >= 2) {
                fun toCanvas(p: Offset) = Offset(rL + p.x * rW, rT + p.y * rH)
                val path = Path()
                path.moveTo(toCanvas(currentPoints[0]).x, toCanvas(currentPoints[0]).y)
                currentPoints.drop(1).forEach { val c = toCanvas(it); path.lineTo(c.x, c.y) }
                drawPath(path, penColor,
                    style = Stroke(penWidth * rW / 1000f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        // Top controls
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black.copy(0.65f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (canUndo) onUndo() },
                modifier = Modifier.size(44.dp).alpha(if (canUndo) 1f else 0.3f)) {
                Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(22.dp), tint = Color.White)
            }
            IconButton(onClick = { if (canRedo) onRedo() },
                modifier = Modifier.size(44.dp).alpha(if (canRedo) 1f else 0.3f)) {
                Icon(Icons.AutoMirrored.Filled.Redo, null, Modifier.size(22.dp), tint = Color.White)
            }
            IconButton(onClick = onClear, Modifier.size(44.dp)) {
                Icon(Icons.Default.Clear, null, Modifier.size(22.dp), tint = Color.White)
            }
            // Reset zoom
            if (zoom > 1.05f) {
                IconButton(onClick = { zoom = 1f; panX = 0f; panY = 0f }, Modifier.size(44.dp)) {
                    Icon(Icons.Default.ZoomOut, null, Modifier.size(22.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text("Done", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Bottom controls
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(0.78f)).navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(PEN_COLORS) { color ->
                    val sel = penColor == color
                    Box(Modifier.size(32.dp).clip(CircleShape).background(color)
                        .border(if (sel) 3.dp else 1.dp,
                            if (sel) AccentPurple else Color.White.copy(0.4f), CircleShape)
                        .clickable { onColorChange(color) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Size ${penWidth.toInt()}", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.width(56.dp))
                Slider(value = penWidth, onValueChange = onWidthChange,
                    valueRange = 3f..30f, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── CONVERT DIALOG ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConvertDialog(
    outputFileName: String, imageCount: Int, pdfMode: PdfMode,
    isProcessing: Boolean, progress: Float,
    onFileNameChange: (String) -> Unit, onModeChange: (PdfMode) -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text("Create PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$imageCount page${if (imageCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = outputFileName, onValueChange = onFileNameChange,
                    label = { Text("File name") }, suffix = { Text(".pdf") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { focusManager.clearFocus() })
                )
                Text("PDF Layout", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = pdfMode == PdfMode.FULL_PAGE,
                        onClick = { onModeChange(PdfMode.FULL_PAGE) },
                        label = { Text("Full Page", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = pdfMode == PdfMode.WITH_MARGINS,
                        onClick = { onModeChange(PdfMode.WITH_MARGINS) },
                        label = { Text("With Margins", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                }
                if (isProcessing) {
                    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isProcessing && outputFileName.isNotBlank()) {
                Text(if (isProcessing) "Creating…" else "Create PDF")
            }
        },
        dismissButton = {
            if (!isProcessing) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
