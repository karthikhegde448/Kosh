package com.rejowan.pdfreaderpro.presentation.screens.tools.imagetopdf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.rejowan.pdfreaderpro.R
import com.rejowan.pdfreaderpro.presentation.navigation.navigateToReader
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File

private val AccentTeal  = Color(0xFF26A69A)
private val AccentGreen = Color(0xFF81C784)
private val AccentBlue  = Color(0xFF64B5F6)

@Composable
fun ImageToPdfScreen(
    navController: NavController,
    viewModel: ImageToPdfViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showCloseConfirm by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    var cameraImageFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraImageFile?.let { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            viewModel.addImages(listOf(uri))
        }
    }

    fun launchCamera() {
        if (hasCameraPermission) {
            val file = viewModel.createCameraImageFile()
            cameraImageFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addImages(uris) }

    // Intercept system/hardware/gesture back on PICK_SOURCE and REVIEW stages
    val isInPickOrReview = state.stage == ImageToPdfStage.PICK_SOURCE ||
            state.stage == ImageToPdfStage.REVIEW
    BackHandler(enabled = isInPickOrReview) { showCloseConfirm = true }

    when (state.stage) {
        ImageToPdfStage.PICK_SOURCE ->
            PickSourceScreen(
                isLoading = state.isLoading,
                onCamera = { launchCamera() },
                onGallery = { galleryLauncher.launch(arrayOf("image/*")) },
                onBack = { showCloseConfirm = true }
            )
        ImageToPdfStage.REVIEW ->
            ReviewScreen(
                state = state,
                viewModel = viewModel,
                onAddCamera = { launchCamera() },
                onAddGallery = { galleryLauncher.launch(arrayOf("image/*")) },
                onOpenEditor = { viewModel.openEditor() },
                onBack = { showCloseConfirm = true }
            )
        ImageToPdfStage.EDITING, ImageToPdfStage.CONVERTING ->
            ImageEditorScreen(
                state = state, viewModel = viewModel, navController = navController,
                onAddCamera = { launchCamera() },
                onAddGallery = { galleryLauncher.launch(arrayOf("image/*")) }
            )
        ImageToPdfStage.DONE ->
            state.result?.let { result ->
                SuccessScreen(
                    result = result, navController = navController,
                    context = context,
                    onConvertMore = { viewModel.reset() },
                    onDone = { navController.popBackStack() }
                )
            }
    }

    // Global close confirm dialog — shown from any stage
    if (showCloseConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { androidx.compose.material3.Text("Are you sure?") },
            text = { androidx.compose.material3.Text("Do you want to go back? Any progress will be lost.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showCloseConfirm = false
                    viewModel.reset()
                    navController.popBackStack()
                }) { androidx.compose.material3.Text("Yes", color = androidx.compose.ui.graphics.Color(0xFFEF5350)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showCloseConfirm = false }) {
                    androidx.compose.material3.Text("No")
                }
            }
        )
    }
}

// ── PICK SOURCE ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickSourceScreen(
    isLoading: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onBack: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tool_image_to_pdf), fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }) { pv ->
        Box(Modifier.fillMaxSize().padding(pv)) {
            Column(Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                    .background(AccentTeal.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, null, Modifier.size(40.dp), tint = AccentTeal)
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.tool_image_to_pdf),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.tool_image_to_pdf_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp))
                SourceCard("Camera", "Take a photo right now",
                    Icons.Default.CameraAlt, AccentTeal, onCamera)
                Spacer(Modifier.height(12.dp))
                SourceCard("From Device", "Choose from your gallery",
                    Icons.Default.PhotoLibrary, AccentBlue, onGallery)
            }
            if (isLoading) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)),
                    contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentTeal) }
            }
        }
    }
}

@Composable
private fun SourceCard(title: String, sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp), border = CardDefaults.outlinedCardBorder()) {
        Row(Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(26.dp), tint = color)
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── REVIEW SCREEN ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewScreen(
    state: ImageToPdfState,
    viewModel: ImageToPdfViewModel,
    onAddCamera: () -> Unit,
    onAddGallery: () -> Unit,
    onOpenEditor: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { state.images.size }
    var showModeSheet by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text("${state.images.size} image${if (state.images.size != 1) "s" else ""}",
                    fontWeight = FontWeight.SemiBold)
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            },
            actions = {
                IconButton(onClick = onAddCamera) { Icon(Icons.Default.CameraAlt, null, tint = AccentTeal) }
                IconButton(onClick = onAddGallery) { Icon(Icons.Default.Add, null, tint = AccentTeal) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }) { pv ->
        Column(Modifier.fillMaxSize().padding(pv)) {

            // ── LARGE PREVIEW ─────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val img = state.images[page]
                    val bmp = img.filteredPreview ?: img.thumbnail
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), null,
                            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
                // Page counter
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(0.55f)
                ) {
                    Text("${pagerState.currentPage + 1}/${state.images.size}",
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            // ── THUMBNAIL STRIP ───────────────────────────────────────────
            LazyRow(
                Modifier.fillMaxWidth().height(80.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(state.images) { idx, img ->
                    val selected = idx == pagerState.currentPage
                    val bmp = img.filteredPreview ?: img.thumbnail
                    Box(
                        Modifier.size(62.dp).clip(RoundedCornerShape(6.dp))
                            .border(if (selected) 2.dp else 0.dp, AccentTeal, RoundedCornerShape(6.dp))
                            .clickable { scope.launch { pagerState.animateScrollToPage(idx) } }
                    ) {
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), null,
                                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Image, null, Modifier.size(20.dp))
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color.Black.copy(0.4f)).padding(vertical = 2.dp),
                            contentAlignment = Alignment.Center) {
                            Text("${idx+1}", fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            }

            // ── BOTTOM ACTIONS ────────────────────────────────────────────
            Surface(Modifier.fillMaxWidth(), shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {

                    // PDF mode chip
                    FilterChip(
                        selected = true,
                        onClick = { showModeSheet = true },
                        label = {
                            Text(if (state.pdfMode == PdfMode.FULL_PAGE) "Full Page"
                                 else "With Margins", fontSize = 11.sp)
                        },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, null,
                            Modifier.size(14.dp)) },
                        modifier = Modifier.height(38.dp)
                    )

                    // Open Editor (center)
                    OutlinedButton(
                        onClick = onOpenEditor,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Editor")
                    }

                    // Finish (right)
                    Button(
                        onClick = { viewModel.convertToPdf() },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Finish")
                    }
                }
            }
        }

        // PDF Mode sheet
        if (showModeSheet) {
            PdfModeSheet(
                current = state.pdfMode,
                onSelect = { mode -> viewModel.setPdfMode(mode); showModeSheet = false },
                onDismiss = { showModeSheet = false }
            )
        }
    }

    // Processing overlay
    if (state.isProcessing) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)),
            contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(32.dp).width(200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(progress = { state.progress },
                        modifier = Modifier.size(52.dp), color = AccentTeal)
                    Spacer(Modifier.height(12.dp))
                    Text("Creating PDF…", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { state.progress }, Modifier.fillMaxWidth())
                    Text("${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── PDF MODE SHEET ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfModeSheet(current: PdfMode, onSelect: (PdfMode) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text("PDF Layout", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp))

            PdfModeOption(
                icon = Icons.Default.Fullscreen, title = "Full Page",
                desc = "Image fills the entire page — no margins, exact size",
                selected = current == PdfMode.FULL_PAGE,
                onClick = { onSelect(PdfMode.FULL_PAGE) }
            )
            Spacer(Modifier.height(8.dp))
            PdfModeOption(
                icon = Icons.Default.Article, title = "With Margins",
                desc = "Image placed on A4 white page with standard margins",
                selected = current == PdfMode.WITH_MARGINS,
                onClick = { onSelect(PdfMode.WITH_MARGINS) }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PdfModeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, desc: String, selected: Boolean, onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = if (selected) AccentTeal.copy(0.1f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, AccentTeal) else null,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(28.dp),
                tint = if (selected) AccentTeal else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = AccentTeal)
        }
    }
}

// ── SUCCESS ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(
    result: ImageToPdfResult,
    navController: NavController,
    context: android.content.Context,
    onConvertMore: () -> Unit,
    onDone: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.tool_image_to_pdf),
            fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    }) { pv ->
        Column(Modifier.fillMaxSize().padding(pv).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                .background(AccentGreen.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = AccentGreen)
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.pdf_created),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.images_converted_count, result.pageCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, null, Modifier.size(20.dp), tint = AccentBlue)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(File(result.outputPath).name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.pages_size_format, result.pageCount,
                            formatSize(result.fileSize)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { navController.navigateToReader(result.outputPath) },
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.open), maxLines = 1)
                }
                OutlinedButton(onClick = {
                    val uri = FileProvider.getUriForFile(context,
                        "${context.packageName}.provider", File(result.outputPath))
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share PDF"))
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.share), maxLines = 1)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onConvertMore, Modifier.weight(1f)) {
                    Text(stringResource(R.string.new_conversion), maxLines = 1)
                }
                Button(onClick = onDone, Modifier.weight(1f)) {
                    Text(stringResource(R.string.done), maxLines = 1)
                }
            }
        }
    }
}

private fun formatSize(b: Long) = when {
    b >= 1024*1024 -> "%.1f MB".format(b/(1024.0*1024.0))
    b >= 1024 -> "%.1f KB".format(b/1024.0)
    else -> "$b B"
}
