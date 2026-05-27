package com.rejowan.pdfreaderpro.presentation.screens.tools.multipages

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.rejowan.pdfreaderpro.R
import com.rejowan.pdfreaderpro.presentation.navigation.navigateToReader
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

private val AccentOrange = Color(0xFFE87722)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPagesScreen(
    navController: NavController,
    initialFilePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: MultiPagesViewModel = koinViewModel(parameters = { parametersOf(context) })
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialFilePath) {
        if (initialFilePath.isNotBlank()) {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", File(initialFilePath)
            )
            viewModel.setSourceFile(uri)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.setSourceFile(it) } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Multiple Pages per Sheet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.result != null -> {
                    val res = state.result!!
                    MultiPagesSuccessState(
                        result = res,
                        onOpenInApp = { navController.navigateToReader(res.outputPath) },
                        onOpenWith = {
                            val uri = FileProvider.getUriForFile(context,
                                "${context.packageName}.provider", File(res.outputPath))
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Open with"))
                        },
                        onShare = {
                            val uri = FileProvider.getUriForFile(context,
                                "${context.packageName}.provider", File(res.outputPath))
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Share PDF"))
                        },
                        onProcessMore = { viewModel.reset() },
                        onDone = { navController.popBackStack() }
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // ── SOURCE FILE ───────────────────────────────────
                        SectionHeader("SOURCE FILE")

                        if (state.sourceFile == null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(110.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp),
                                onClick = { filePicker.launch(arrayOf("application/pdf")) }
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.FolderOpen, null,
                                            modifier = Modifier.size(30.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        Spacer(Modifier.height(8.dp))
                                        Text("Tap to choose a PDF",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        } else {
                            val sf = state.sourceFile!!
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Description, null,
                                        modifier = Modifier.size(30.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(sf.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1)
                                        Text("${sf.pageCount} pages • ${formatSize(sf.size)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.reset()
                                            filePicker.launch(arrayOf("application/pdf"))
                                        },
                                        modifier = Modifier.height(36.dp)
                                    ) { Text("Change", style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }

                        // ── PAGES PER SHEET ───────────────────────────────
                        SectionHeader("PAGES PER SHEET")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PagesPerSheet.entries.forEach { pps ->
                                FilterChip(
                                    selected = state.pagesPerSheet == pps,
                                    onClick = { viewModel.setPagesPerSheet(pps) },
                                    label = { Text(pps.count.toString(), fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        // ── PAGE ORIENTATION ──────────────────────────────
                        SectionHeader("PAGE ORIENTATION")

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.orientation == PageOrientation.PORTRAIT,
                                onClick = { viewModel.setOrientation(PageOrientation.PORTRAIT) },
                                label = { Text("Portrait", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            FilterChip(
                                selected = state.orientation == PageOrientation.LANDSCAPE,
                                onClick = { viewModel.setOrientation(PageOrientation.LANDSCAPE) },
                                label = { Text("Landscape", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }

                        // ── PAGE BORDER ───────────────────────────────────
                        SectionHeader("PAGE BORDER")

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("With border",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                    Text("Black outline around each page",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = state.withBorder,
                                    onCheckedChange = { viewModel.setWithBorder(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AccentOrange
                                    )
                                )
                            }
                        }

                        // ── OUTER MARGIN ──────────────────────────────────
                        SectionHeader("OUTER MARGIN")

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Space between content and page edge",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f))
                                    Text("${"%.0f".format(state.outerMarginMm)} mm",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = AccentOrange)
                                }
                                Spacer(Modifier.height(4.dp))
                                Slider(
                                    value = state.outerMarginMm,
                                    onValueChange = { viewModel.setOuterMarginMm(it) },
                                    valueRange = 0f..30f,
                                    steps = 29,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // ── INNER MARGIN ──────────────────────────────────
                        SectionHeader("INNER MARGIN")

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Space between pages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f))
                                    Text("${"%.0f".format(state.innerMarginMm)} mm",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = AccentOrange)
                                }
                                Spacer(Modifier.height(4.dp))
                                Slider(
                                    value = state.innerMarginMm,
                                    onValueChange = { viewModel.setInnerMarginMm(it) },
                                    valueRange = 0f..20f,
                                    steps = 19,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // ── LIVE PREVIEW ──────────────────────────────────
                        SectionHeader("LIVE PREVIEW")

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val orientLabel = if (state.orientation == PageOrientation.LANDSCAPE) "Landscape" else "Portrait"
                                Text(
                                    "A4 $orientLabel — ${state.pagesPerSheet.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))

                                val isLandscape = state.orientation == PageOrientation.LANDSCAPE
                                val previewAspect = if (isLandscape) 297f / 210f else 210f / 297f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(if (isLandscape) 0.85f else 0.65f)
                                        .aspectRatio(previewAspect)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                            RoundedCornerShape(4.dp))
                                ) {
                                    SheetPreviewCanvas(
                                        cols = state.pagesPerSheet.cols(state.orientation),
                                        rows = state.pagesPerSheet.rows(state.orientation),
                                        outerMarginMm = state.outerMarginMm,
                                        innerMarginMm = state.innerMarginMm,
                                        withBorder = state.withBorder,
                                        isLandscape = isLandscape,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Outer: ${"%.0f".format(state.outerMarginMm)} mm  •  Inner: ${"%.0f".format(state.innerMarginMm)} mm  •  Border: ${if (state.withBorder) "on" else "off"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (state.sourceFile != null) {
                                    val srcPages = state.sourceFile!!.pageCount
                                    val pps = state.pagesPerSheet.count
                                    val outPages = kotlin.math.ceil(srcPages.toDouble() / pps).toInt()
                                    Text(
                                        "$srcPages source pages → $outPages output sheet${if (outPages != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        // ── PROGRESS ──────────────────────────────────────
                        AnimatedVisibility(visible = state.isProcessing, enter = fadeIn(), exit = fadeOut()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(state.progressLabel.ifBlank { "Processing…" },
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // ── OUTPUT FILE NAME ──────────────────────────────
                        SectionHeader("OUTPUT FILE NAME")

                        OutlinedTextField(
                            value = state.outputFileName,
                            onValueChange = { viewModel.setOutputFileName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    "e.g. my_document (optional)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Text(
                                    ".pdf",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        )

                        // ── ACTION BUTTON ──────────────────────────────────
                        if (!state.isProcessing && state.result == null) {
                            Button(
                                onClick = { viewModel.process() },
                                enabled = state.sourceFile != null,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.GridView, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Create Multi-Page PDF",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun MultiPagesSuccessState(
    result: MultiPagesResult,
    onOpenInApp: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onProcessMore: () -> Unit,
    onDone: () -> Unit
) {
    val AccentGreen = Color(0xFF81C784)
    val AccentRed   = Color(0xFFEF5350)
    Column(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
                    .background(AccentGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, null,
                    modifier = Modifier.size(48.dp), tint = AccentGreen)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Done! ${result.outputPageCount} sheet${if (result.outputPageCount != 1) "s" else ""} created",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GridView, null,
                        modifier = Modifier.size(24.dp), tint = AccentRed)
                    Spacer(Modifier.width(12.dp))
                    Text(File(result.outputPath).name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Text("${result.outputPageCount} sheet(s) • ${formatSize(result.fileSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(result.outputPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(32.dp))

        Button(onClick = onOpenInApp, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open in App", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onOpenWith, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open With", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onProcessMore, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Text("Process More Files", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Text("Done", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Live preview canvas showing outer margin, inner margin, and optional page borders.
 */
@Composable
private fun SheetPreviewCanvas(
    cols: Int,
    rows: Int,
    outerMarginMm: Float,
    innerMarginMm: Float,
    withBorder: Boolean,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pageColor    = Color(0xFFF8F8F8)
    val cellBorder   = Color(0xFFCCCCCC)
    val textLine     = Color(0xFFBBBBBB)
    val outerShade   = Color(0xFFEEEEEE)
    val innerShade   = Color(0xFFE0E0E0)
    val blackBorder  = Color(0xFF222222)

    Canvas(modifier = modifier.background(Color.White)) {
        val w = size.width
        val h = size.height

        // Map mm → canvas pixels using the wider dimension
        val pageWidthMm = if (isLandscape) 297f else 210f
        val mmToPx = w / pageWidthMm
        val outerPx = outerMarginMm * mmToPx
        val innerPx = innerMarginMm * mmToPx

        val contentW = w - outerPx * 2
        val contentH = h - outerPx * 2
        val cellW = if (cols > 1) (contentW - innerPx * (cols - 1)) / cols else contentW
        val cellH = if (rows > 1) (contentH - innerPx * (rows - 1)) / rows else contentH

        // Sheet background
        drawRect(color = Color.White, size = size)

        // Outer margin shading
        if (outerMarginMm > 0f) {
            drawRect(color = outerShade, size = size)
            // Clear content area
            drawRect(color = Color.White,
                topLeft = Offset(outerPx, outerPx),
                size = Size(contentW, contentH))
        }

        // Draw inner gap shading between cells
        if (innerMarginMm > 0f && (cols > 1 || rows > 1)) {
            // Fill content area with inner shade, then overdraw cells
            drawRect(color = innerShade,
                topLeft = Offset(outerPx, outerPx),
                size = Size(contentW, contentH))
        }

        // Draw each page cell
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = outerPx + col * (cellW + innerPx)
                val y = outerPx + row * (cellH + innerPx)

                // Page background
                drawRect(color = pageColor, topLeft = Offset(x, y), size = Size(cellW, cellH))

                // Subtle cell outline (always, to show page boundary)
                drawRect(color = cellBorder, topLeft = Offset(x, y), size = Size(cellW, cellH),
                    style = Stroke(width = 1f))

                // Black border if enabled
                if (withBorder) {
                    drawRect(color = blackBorder, topLeft = Offset(x, y), size = Size(cellW, cellH),
                        style = Stroke(width = 2.5f))
                }

                // Fake text lines
                val lineCount   = 5
                val lineSpacing = cellH / (lineCount + 2)
                val lineMarginX = cellW * 0.1f
                for (li in 1..lineCount) {
                    val lineY     = y + li * lineSpacing
                    val lineWidth = if (li == lineCount) cellW * 0.5f else cellW * 0.8f
                    drawLine(color = textLine,
                        start = Offset(x + lineMarginX, lineY),
                        end   = Offset(x + lineMarginX + lineWidth, lineY),
                        strokeWidth = 1.5f)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val i = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(bytes / Math.pow(1024.0, i.toDouble()), units[i])
}
