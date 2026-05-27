package com.rejowan.pdfreaderpro.presentation.screens.tools.texttopdf

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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

private val TEXT_COLORS = listOf(
    Color.Black        to "Black",
    Color(0xFF1565C0)  to "Blue",
    Color(0xFF1B5E20)  to "Green",
    Color(0xFFB71C1C)  to "Red",
    Color(0xFF4A148C)  to "Purple",
    Color(0xFF37474F)  to "Gray"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToPdfScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: TextToPdfViewModel = koinViewModel(parameters = { parametersOf(context) })
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    val isNB = state.mode == TextPdfMode.NOTEBOOK

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Text to PDF",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
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
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (state.outputPath != null) {
                SuccessState(
                    outputPath = state.outputPath!!,
                    onOpenInApp = { navController.navigateToReader(state.outputPath!!) },
                    onShare = {
                        val uri = FileProvider.getUriForFile(context,
                            "${context.packageName}.provider", File(state.outputPath!!))
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share PDF"))
                    },
                    onConvertMore = { viewModel.reset() }
                )
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    // ── MODE SELECTOR ───────────────────────────────────────
                    SectionLabel("PDF STYLE")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeCard(
                            label = "Custom",
                            icon = Icons.Default.FormatColorText,
                            desc = "Choose font, color & spacing",
                            selected = !isNB,
                            onClick = { viewModel.setMode(TextPdfMode.CUSTOM) },
                            modifier = Modifier.weight(1f)
                        )
                        ModeCard(
                            label = "Notebook",
                            icon = Icons.Default.MenuBook,
                            desc = "Ruled lines, red margin, handwriting font",
                            selected = isNB,
                            onClick = { viewModel.setMode(TextPdfMode.NOTEBOOK) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ── FONT TYPE ───────────────────────────────────────────
                    SectionLabel("FONT${if (isNB) "  (Handwriting in Notebook)" else ""}")
                    FontSelector(
                        selected = state.fontType,
                        enabled = !isNB,
                        onSelect = { viewModel.setFontType(it) }
                    )

                    // ── FONT SIZE ───────────────────────────────────────────
                    SectionLabel("FORMATTING${if (isNB) "  (fixed in Notebook)" else ""}")
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                .copy(alpha = if (isNB) 0.5f else 1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                            // Font size
                            SliderRow(
                                label = "Font Size",
                                value = "${"%.0f".format(state.fontSize)} pt",
                                enabled = !isNB
                            ) {
                                Slider(value = state.fontSize, onValueChange = { if (!isNB) viewModel.setFontSize(it) },
                                    valueRange = 8f..36f, steps = 27, enabled = !isNB,
                                    modifier = Modifier.fillMaxWidth())
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))

                            // Text color
                            Column {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    OptionLabel("Text Color", !isNB)
                                    OptionValue(TEXT_COLORS.firstOrNull { it.first == state.textColor }?.second ?: "Custom", !isNB)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TEXT_COLORS.forEach { (color, _) ->
                                        val sel = state.textColor == color
                                        Box(
                                            Modifier.size(32.dp).clip(CircleShape)
                                                .background(color.copy(alpha = if (isNB) 0.3f else 1f))
                                                .border(if (sel) 3.dp else 1.dp,
                                                    if (sel) AccentOrange else Color.Gray.copy(0.4f), CircleShape)
                                                .clickable(enabled = !isNB) { viewModel.setTextColor(color) }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))

                            // Line spacing in cm
                            SliderRow(
                                label = "Line Spacing",
                                value = "${"%.1f".format(state.lineSpacingCm)} cm",
                                enabled = !isNB
                            ) {
                                Slider(value = state.lineSpacingCm,
                                    onValueChange = { if (!isNB) viewModel.setLineSpacing(it) },
                                    valueRange = 0.4f..2.0f, steps = 31, enabled = !isNB,
                                    modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    // ── MARGINS ─────────────────────────────────────────────
                    SectionLabel("MARGINS (cm)${if (isNB) "  (fixed in Notebook)" else ""}")
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                .copy(alpha = if (isNB) 0.5f else 1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

                            // Horizontal margin with box input + slider
                            MarginControl(
                                label = "Horizontal Margin",
                                sublabel = "Left & Right",
                                value = state.horizontalMarginCm,
                                enabled = !isNB,
                                onValue = { if (!isNB) viewModel.setHorizontalMargin(it) }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))

                            // Vertical margin
                            MarginControl(
                                label = "Vertical Margin",
                                sublabel = "Top & Bottom",
                                value = state.verticalMarginCm,
                                enabled = !isNB,
                                onValue = { if (!isNB) viewModel.setVerticalMargin(it) }
                            )
                        }
                    }

                    // ── NOTEBOOK PREVIEW ────────────────────────────────────
                    AnimatedVisibility(visible = isNB, enter = fadeIn(), exit = fadeOut()) {
                        Column {
                            SectionLabel("NOTEBOOK PREVIEW")
                            NotebookPreview()
                        }
                    }

                    // ── TEXT INPUT ───────────────────────────────────────────
                    SectionLabel("YOUR TEXT")
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = { viewModel.setInputText(it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                        placeholder = {
                            Text("Paste or type your text here…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                        },
                        shape = RoundedCornerShape(12.dp),
                        maxLines = Int.MAX_VALUE,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    // ── PAGE ESTIMATOR ───────────────────────────────────────
                    if (state.inputText.isNotBlank()) {
                        PageEstimator(state)
                    }

                    // ── PROGRESS ──────────────────────────────────────────────
                    AnimatedVisibility(visible = state.isProcessing, enter = fadeIn(), exit = fadeOut()) {
                        Card(Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("Creating PDF…", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { state.progress }, Modifier.fillMaxWidth())
                            }
                        }
                    }

                    // ── OUTPUT FILE NAME ─────────────────────────────────────
                    SectionLabel("OUTPUT FILE NAME")
                    OutlinedTextField(
                        value = state.outputFileName,
                        onValueChange = { viewModel.setOutputFileName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "e.g. my_notes (optional)",
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

                    // ── CONVERT BUTTON ────────────────────────────────────────
                    if (!state.isProcessing && state.outputPath == null) {
                        Button(
                            onClick = { viewModel.convert() },
                            enabled = state.inputText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Convert to PDF",
                                style = MaterialTheme.typography.bodyLarge
                                    .copy(fontWeight = FontWeight.SemiBold))
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ── FONT SELECTOR ─────────────────────────────────────────────────────────────

@Composable
private fun FontSelector(selected: TextFont, enabled: Boolean, onSelect: (TextFont) -> Unit) {
    val groups = listOf(
        "Sans-serif" to listOf(TextFont.SANS_REGULAR, TextFont.SANS_BOLD,
            TextFont.SANS_ITALIC, TextFont.SANS_BOLD_ITALIC),
        "Serif" to listOf(TextFont.SERIF_REGULAR, TextFont.SERIF_BOLD, TextFont.SERIF_ITALIC),
        "Other" to listOf(TextFont.MONO, TextFont.HANDWRITING)
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                .copy(alpha = if (enabled) 1f else 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { (groupName, fonts) ->
                Text(groupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (enabled) 0.7f else 0.4f),
                    modifier = Modifier.padding(start = 2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fonts.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { font ->
                                FontChip(
                                    font = font,
                                    selected = selected == font,
                                    enabled = enabled,
                                    onClick = { if (enabled) onSelect(font) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontChip(
    font: TextFont, selected: Boolean, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val alpha = if (enabled) 1f else 0.4f
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .border(if (selected) 2.dp else 1.dp,
                if (selected) AccentOrange.copy(alpha) else MaterialTheme.colorScheme.outlineVariant.copy(alpha),
                RoundedCornerShape(8.dp)),
        color = if (selected) AccentOrange.copy(0.08f * alpha)
                else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Preview text showing font style
            val previewStyle = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (font.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (font.isItalic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = when {
                    font == TextFont.MONO || font == TextFont.HANDWRITING -> FontFamily.Monospace
                    font.displayName.contains("Serif") && !font.displayName.contains("Sans") -> FontFamily.Serif
                    else -> FontFamily.SansSerif
                },
                textDecoration = if (font.isUnderline) TextDecoration.Underline else null,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
            Text("Aa", style = previewStyle.copy(fontSize = 14.sp))
            Text(font.displayName,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (selected) AccentOrange.copy(alpha)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha),
                maxLines = 1)
        }
    }
}

// ── MARGIN CONTROL ────────────────────────────────────────────────────────────

@Composable
private fun MarginControl(
    label: String, sublabel: String, value: Float,
    enabled: Boolean, onValue: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                OptionLabel(label, enabled)
                Text(sublabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(if (enabled) 0.6f else 0.3f))
            }
            // Number box showing cm value
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AccentOrange.copy(if (enabled) 0.1f else 0.04f),
                modifier = Modifier.border(1.dp,
                    AccentOrange.copy(if (enabled) 0.4f else 0.15f), RoundedCornerShape(6.dp))
            ) {
                Text(
                    "${"%.1f".format(value)} cm",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AccentOrange.copy(if (enabled) 1f else 0.4f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 0f..4f,
            steps = 39,  // 0.1cm steps
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── PAGE ESTIMATOR ────────────────────────────────────────────────────────────

@Composable
private fun PageEstimator(state: TextToPdfState) {
    val words = state.inputText.trim().split("\\s+".toRegex()).size
    val chars = state.inputText.length
    val pages = state.estimatedPages

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AccentOrange.copy(0.07f)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, null, Modifier.size(22.dp), tint = AccentOrange)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Page Estimate",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AccentOrange)
                Text("$chars characters · $words words",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$pages", style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold, color = AccentOrange))
                Text(if (pages == 1) "page" else "pages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── NOTEBOOK PREVIEW ──────────────────────────────────────────────────────────

@Composable
private fun NotebookPreview() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            val lineGapDp = 26.dp
            val startYDp  = 26.dp
            val marginXDp = 44.dp
            val sampleLines = listOf(
                "This is how your text",
                "will appear on the page.",
                "Each line follows the ruled",
                "notebook lines neatly."
            )

            Canvas(Modifier.fillMaxSize()) {
                val lineColor   = Color(0xFFBBD4F0)
                val marginColor = Color(0xFFE57373)
                val marginX     = marginXDp.toPx()
                val startY      = startYDp.toPx()
                val lineGap     = lineGapDp.toPx()

                // Draw ruled lines
                var y = startY
                while (y < size.height) {
                    drawLine(lineColor, Offset(marginX, y), Offset(size.width - 12.dp.toPx(), y), 1.5f)
                    y += lineGap
                }
                // Red left margin
                drawLine(marginColor, Offset(marginX - 4.dp.toPx(), 0f),
                    Offset(marginX - 4.dp.toPx(), size.height), 1.5f)

                // Draw text with baseline exactly ON each ruled line
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.BLACK
                    textSize = 11.sp.toPx()
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                drawIntoCanvas { canvas ->
                    sampleLines.forEachIndexed { index, line ->
                        val baselineY = startY + index * lineGap
                        canvas.nativeCanvas.drawText(
                            line,
                            marginX + 4.dp.toPx(),
                            baselineY,
                            textPaint
                        )
                    }
                }
            }
        }
    }
}

// ── SMALL HELPERS ──────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = AccentOrange, modifier = Modifier.padding(start = 2.dp))
}

@Composable
private fun OptionLabel(text: String, enabled: Boolean) {
    Text(text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface.copy(if (enabled) 1f else 0.4f))
}

@Composable
private fun OptionValue(text: String, enabled: Boolean) {
    Text(text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = AccentOrange.copy(if (enabled) 1f else 0.4f))
}

@Composable
private fun SliderRow(label: String, value: String, enabled: Boolean, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            OptionLabel(label, enabled)
            OptionValue(value, enabled)
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun ModeCard(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .border(if (selected) 2.dp else 1.dp,
                if (selected) AccentOrange else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) AccentOrange.copy(0.08f)
                             else MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(22.dp),
                tint = if (selected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) AccentOrange else MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 13.sp)
        }
    }
}

// ── SUCCESS STATE ─────────────────────────────────────────────────────────────

@Composable
private fun SuccessState(
    outputPath: String, onOpenInApp: () -> Unit,
    onShare: () -> Unit, onConvertMore: () -> Unit
) {
    val AccentGreen = Color(0xFF81C784)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(80.dp).clip(RoundedCornerShape(16.dp))
            .background(AccentGreen.copy(0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(48.dp), tint = AccentGreen)
        }
        Spacer(Modifier.height(20.dp))
        Text("PDF Created!", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(File(outputPath).name, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onOpenInApp, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Open in App")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onShare, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Share PDF")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onConvertMore, Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text("Convert Another")
        }
    }
}
