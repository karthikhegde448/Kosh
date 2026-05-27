package com.rejowan.pdfreaderpro.presentation.screens.tools.negate

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
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
import com.rejowan.pdfreaderpro.presentation.navigation.navigateToReader
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

private val AccentGreen  = Color(0xFF81C784)
private val AccentBlue   = Color(0xFF64B5F6)
private val AccentPurple = Color(0xFFE87722)
private val AccentRed    = Color(0xFFEF5350)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NegateScreen(
    navController: NavController,
    initialFilePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: NegateViewModel = koinViewModel(parameters = { parametersOf(context) })
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
                    Text("Negate PDF",
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
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.isSuccess && state.outputPath != null -> {
                    NegateSuccessState(
                        outputPath = state.outputPath!!,
                        onOpenInApp = { navController.navigateToReader(state.outputPath!!) },
                        onOpenWith = {
                            val uri = FileProvider.getUriForFile(context,
                                "${context.packageName}.provider", File(state.outputPath!!))
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Open with"))
                        },
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
                        onNegateMore = { viewModel.reset() },
                        onDone = { navController.popBackStack() }
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Default.InvertColors, null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp))
                                Text("Inverts all page colors — ideal for dark mode reading and printing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }

                        Text("SOURCE FILE",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp))

                        if (state.sourceFile == null) {
                            NegateEmptyState(onPickFile = { filePicker.launch(arrayOf("application/pdf")) })
                        } else {
                            val sf = state.sourceFile!!
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Description, null,
                                        modifier = Modifier.size(32.dp), tint = AccentRed)
                                    Column(Modifier.weight(1f)) {
                                        Text(sf.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${sf.pageCount} pages",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(onClick = {
                                        viewModel.reset()
                                        filePicker.launch(arrayOf("application/pdf"))
                                    }, modifier = Modifier.height(36.dp)) {
                                        Text("Change", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = state.isProcessing) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(state.progressLabel.ifBlank { "Processing…" },
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { state.progress },
                                        modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }

                        if (!state.isProcessing) {
                            Button(
                                onClick = { viewModel.negate() },
                                enabled = state.sourceFile != null,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.InvertColors, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Invert Colors",
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
private fun NegateEmptyState(onPickFile: () -> Unit) {
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
                .clip(RoundedCornerShape(16.dp)).background(AccentPurple.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.InvertColors, null,
                modifier = Modifier.size(40.dp), tint = AccentPurple)
        }
        Spacer(Modifier.height(24.dp))
        Text("Negate PDF Colors", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Inverts all page colors — ideal for dark mode reading and printing",
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
private fun NegateSuccessState(
    outputPath: String,
    onOpenInApp: () -> Unit,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onNegateMore: () -> Unit,
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
        Text("Negate Complete!", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PictureAsPdf, null,
                        modifier = Modifier.size(24.dp), tint = AccentRed)
                    Spacer(Modifier.width(12.dp))
                    Text(File(outputPath).name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Text(outputPath,
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

        OutlinedButton(onClick = onNegateMore, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)) {
            Text("Negate More Files", style = MaterialTheme.typography.labelMedium)
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
