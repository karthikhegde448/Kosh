package com.rejowan.pdfreaderpro.presentation.screens.tools.pdftotxt

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Pages
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.rejowan.pdfreaderpro.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

private val AccentGreen  = Color(0xFF81C784)
private val AccentTeal   = Color(0xFF4DB6AC)
private val AccentRed    = Color(0xFFEF5350)
private val AccentPurple = Color(0xFFE87722)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToTxtScreen(
    navController: NavController,
    initialFilePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: PdfToTxtViewModel = koinViewModel(parameters = { parametersOf(context) })
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
                        "PDF to TXT",
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
                    containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                // ── SUCCESS STATE ─────────────────────────────────────────
                state.result != null -> {
                    val res = state.result!!
                    PdfToTxtSuccessState(
                        result = res,
                        onOpenWith = {
                            val file = File(res.outputPath)
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "text/plain")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open text file"))
                        },
                        onShare = {
                            val file = File(res.outputPath)
                            val uri = FileProvider.getUriForFile(
                                context, "${context.packageName}.provider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share TXT"))
                        },
                        onConvertMore = { viewModel.reset() },
                        onDone = { navController.popBackStack() }
                    )
                }

                // ── EMPTY / WORKING STATE ─────────────────────────────────
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))

                        // ── FILE SELECTION ────────────────────────────────
                        Text("SOURCE FILE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp))

                        if (state.sourceFile == null) {
                            PdfToTxtEmptyState(
                                onPickFile = { filePicker.launch(arrayOf("application/pdf")) }
                            )
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
                                        modifier = Modifier.size(32.dp), tint = AccentRed)
                                    Column(Modifier.weight(1f)) {
                                        Text(sf.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                    ) {
                                        Text("Change", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        // ── OCR TOGGLE ────────────────────────────────────
                        Text("EXTRACTION MODE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            // Native mode row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Article, null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (!state.useOcr) AccentTeal
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                Column(Modifier.weight(1f)) {
                                    Text("Native Extraction",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = if (!state.useOcr) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Text("Fast • reads embedded text directly from PDF",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = if (!state.useOcr) 0.7f else 0.3f))
                                }
                            }

                            // Divider with switch
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (state.useOcr) "OCR is ON" else "OCR is OFF",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (state.useOcr) AccentPurple
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Switch(
                                    checked = state.useOcr,
                                    onCheckedChange = { viewModel.setUseOcr(it) }
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            // OCR mode row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.DocumentScanner, null,
                                    modifier = Modifier.size(22.dp),
                                    tint = if (state.useOcr) AccentPurple
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                Column(Modifier.weight(1f)) {
                                    Text("OCR (Optical Character Recognition)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = if (state.useOcr) MaterialTheme.colorScheme.onSurface
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Text("Slower • use for scanned or image-based PDFs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = if (state.useOcr) 0.7f else 0.3f))
                                }
                            }
                        }

                        // ── PAGE SELECTION ───────────────────────────────
                        if (state.sourceFile != null) {
                            Text("PAGE SELECTION",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                    // Mode chips: All Pages / Custom
                                    Row(Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilterChip(
                                            selected = state.pageMode == PdfToTxtPageMode.ALL,
                                            onClick = { viewModel.setPageMode(PdfToTxtPageMode.ALL) },
                                            label = { Text("All Pages (${state.sourceFile!!.pageCount})") },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                        FilterChip(
                                            selected = state.pageMode == PdfToTxtPageMode.CUSTOM,
                                            onClick = { viewModel.setPageMode(PdfToTxtPageMode.CUSTOM) },
                                            label = { Text("Custom") },
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }

                                    // Custom page input — only visible when CUSTOM selected
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = state.pageMode == PdfToTxtPageMode.CUSTOM
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedTextField(
                                                value = state.pageInput,
                                                onValueChange = { viewModel.setPageInput(it) },
                                                modifier = Modifier.fillMaxWidth(),
                                                label = { Text("Pages") },
                                                placeholder = { Text("e.g.  1, 3, 5-8, 10") },
                                                isError = state.pageInputError != null,
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            if (state.pageInputError != null) {
                                                Text(state.pageInputError!!,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(start = 4.dp))
                                            } else if (state.pageInput.isNotBlank()) {
                                                Text("Format: single pages (2,3,8) or ranges (5-8) or mixed (1,3,5-8,10)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(start = 4.dp))
                                            }
                                            // Format hint card
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primary.copy(0.06f)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Column(Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Text("Examples:",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.primary)
                                                    listOf(
                                                        "2,3,8,10" to "Pages 2, 3, 8 and 10",
                                                        "5-8" to "Pages 5 through 8",
                                                        "1,4-6,9" to "Page 1, pages 4–6, and page 9"
                                                    ).forEach { (ex, desc) ->
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text(ex,
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontWeight = FontWeight.Bold),
                                                                color = MaterialTheme.colorScheme.primary)
                                                            Text("→ $desc",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── INFO CARD ─────────────────────────────────────
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (state.useOcr)
                                    AccentPurple.copy(alpha = 0.08f)
                                else
                                    AccentTeal.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    if (state.useOcr) Icons.Default.DocumentScanner
                                    else Icons.Default.Article,
                                    null,
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp),
                                    tint = if (state.useOcr) AccentPurple else AccentTeal
                                )
                                Text(
                                    if (state.useOcr)
                                        "OCR mode: Each page is rendered as an image and Tesseract (open source, Apache 2.0) reads the text. Works fully offline — no internet needed, ever."
                                    else
                                        "Native mode: Text is read directly from the PDF structure. Instant for text-based PDFs. Produces no output for scanned/image PDFs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.useOcr) AccentPurple else AccentTeal
                                )
                            }
                        }

                        // ── PROGRESS ──────────────────────────────────────
                        AnimatedVisibility(visible = state.isProcessing) {
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
                        Text(
                            "OUTPUT FILE NAME",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        OutlinedTextField(
                            value = state.outputFileName,
                            onValueChange = { viewModel.setOutputFileName(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. extracted_text (optional)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                Text(".txt", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 12.dp))
                            }
                        )

                        // ── ACTION BUTTON ─────────────────────────────────
                        if (!state.isProcessing) {
                            Button(
                                onClick = { viewModel.convert() },
                                enabled = state.sourceFile != null &&
                                    (state.pageMode == PdfToTxtPageMode.ALL ||
                                    (state.pageInput.isNotBlank() && state.pageInputError == null)),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    if (state.useOcr) Icons.Default.DocumentScanner
                                    else Icons.Default.Article,
                                    null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (state.useOcr) "Extract with OCR" else "Extract Text",
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
private fun PdfToTxtEmptyState(onPickFile: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "float offset")
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.offset(y = (-floatOffset).dp).size(80.dp)
                .clip(RoundedCornerShape(16.dp)).background(AccentTeal.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Article, null,
                modifier = Modifier.size(40.dp), tint = AccentTeal)
        }
        Spacer(Modifier.height(24.dp))
        Text("PDF to Text", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Extract text from any PDF — native or OCR for scanned documents",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(32.dp))
        Button(onClick = onPickFile, modifier = Modifier.fillMaxWidth(0.6f)) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text("Select PDF")
        }
    }
}

@Composable
private fun PdfToTxtSuccessState(
    result: PdfToTxtResult,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onConvertMore: () -> Unit,
    onDone: () -> Unit
) {
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
        Text("Extraction Complete!",
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
                    Icon(Icons.Default.Article, null,
                        modifier = Modifier.size(24.dp), tint = AccentTeal)
                    Spacer(Modifier.width(12.dp))
                    Text(File(result.outputPath).name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Text("${result.pageCount} pages • ${result.charCount} characters • ${formatSize(result.fileSize)}",
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

        // Open With (primary — no in-app reader for TXT, open externally)
        Button(onClick = onOpenWith, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Text File", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(onClick = onConvertMore, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Text("Convert Another PDF", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))

        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Text("Done", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val i = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(bytes / Math.pow(1024.0, i.toDouble()), units[i])
}
