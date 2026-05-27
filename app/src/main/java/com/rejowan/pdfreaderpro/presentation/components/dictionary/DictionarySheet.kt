package com.rejowan.pdfreaderpro.presentation.components.dictionary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rejowan.pdfreaderpro.data.repository.DictionaryRepository
import com.rejowan.pdfreaderpro.data.repository.DictionaryState
import com.rejowan.pdfreaderpro.data.repository.DownloadState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val Orange = Color(0xFFE87722)
private val Green  = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(onDismiss: () -> Unit) {
    val repository: DictionaryRepository = koinInject()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var query     by remember { mutableStateOf("") }
    var dictState by remember { mutableStateOf<DictionaryState>(DictionaryState.Idle) }
    val downloadState by repository.downloadState.collectAsState(initial = DownloadState.Idle)
    var isOffline by remember { mutableStateOf(repository.isOfflineAvailable) }

    // Sync isOffline with download completion
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Done) {
            isOffline = true
        }
    }

    // Auto-focus search box
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    fun search() {
        if (query.isBlank()) return
        keyboard?.hide()
        dictState = DictionaryState.Loading
        scope.launch { dictState = repository.lookup(query.trim()) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.MenuBook, null, tint = Orange, modifier = Modifier.size(22.dp))
                    Text("Dictionary",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                // Status pill
                Surface(shape = RoundedCornerShape(20.dp),
                    color = if (isOffline) Green.copy(0.12f)
                            else Orange.copy(0.12f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            if (isOffline) Icons.Default.CheckCircle else Icons.Default.Download,
                            null,
                            modifier = Modifier.size(13.dp),
                            tint = if (isOffline) Green else Orange
                        )
                        Text(
                            if (isOffline) "Offline ready" else "Needs download",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOffline) Green else Orange
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Search box ────────────────────────────────────────────────────
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("Type a word to look up…") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Orange) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; dictState = DictionaryState.Idle }) {
                            Icon(Icons.Default.Clear, null)
                        }
                    }
                },
                singleLine = true,
                enabled = isOffline,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange,
                    cursorColor = Orange
                )
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { search() },
                enabled = isOffline && query.isNotBlank() && dictState !is DictionaryState.Loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                if (dictState is DictionaryState.Loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Looking up…")
                } else {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Search", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Results ───────────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 300.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    when (val s = dictState) {
                        is DictionaryState.Idle -> {
                            if (!isOffline) {
                                Text(
                                    "Download the dictionary below to search words offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                            } else {
                                Text(
                                    "Enter a word above to look up its definition.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                            }
                        }
                        is DictionaryState.Loading -> {
                            Box(Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Orange)
                            }
                        }
                        is DictionaryState.NotDownloaded -> {
                            ResultCard(
                                icon = Icons.Default.Download,
                                iconTint = Orange,
                                title = "Dictionary not downloaded",
                                body = "Download the offline dictionary below first."
                            )
                        }
                        is DictionaryState.NotFound -> {
                            ResultCard(
                                icon = Icons.Default.Warning,
                                iconTint = MaterialTheme.colorScheme.error,
                                title = "Word not found",
                                body = "Check spelling or try a different word."
                            )
                        }
                        is DictionaryState.Error -> {
                            ResultCard(
                                icon = Icons.Default.Error,
                                iconTint = MaterialTheme.colorScheme.error,
                                title = "Error",
                                body = s.message
                            )
                        }
                        is DictionaryState.Success -> {
                            val r = s.result
                            Card(Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)) {

                                    // Word
                                    Text(r.word,
                                        style = MaterialTheme.typography.headlineSmall
                                            .copy(fontWeight = FontWeight.Bold, color = Orange))

                                    if (r.phonetic != null) {
                                        Text(r.phonetic,
                                            style = MaterialTheme.typography.bodyMedium
                                                .copy(fontStyle = FontStyle.Italic),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    HorizontalDivider(color = Orange.copy(0.2f))

                                    r.meanings.forEach { meaning ->
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (meaning.partOfSpeech.isNotBlank()) {
                                                Surface(shape = RoundedCornerShape(4.dp),
                                                    color = Orange.copy(0.12f)) {
                                                    Text(meaning.partOfSpeech,
                                                        style = MaterialTheme.typography.labelSmall
                                                            .copy(fontWeight = FontWeight.Bold,
                                                                color = Orange),
                                                        modifier = Modifier.padding(
                                                            horizontal = 8.dp, vertical = 3.dp))
                                                }
                                            }
                                            meaning.definitions.forEachIndexed { i, def ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text("${i + 1}.",
                                                        style = MaterialTheme.typography.bodySmall
                                                            .copy(fontWeight = FontWeight.Bold,
                                                                color = Orange),
                                                        modifier = Modifier.width(20.dp))
                                                    Text(def,
                                                        style = MaterialTheme.typography.bodyMedium)
                                                }
                                            }
                                            if (meaning.example != null) {
                                                Text("e.g. \"${meaning.example}\"",
                                                    style = MaterialTheme.typography.bodySmall
                                                        .copy(fontStyle = FontStyle.Italic),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (meaning.synonyms.isNotEmpty()) {
                                                Text("Synonyms: ${meaning.synonyms.joinToString(", ")}",
                                                    style = MaterialTheme.typography.bodySmall,
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

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Download section ──────────────────────────────────────────────
            Text("Offline Dictionary",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold, color = Orange))
            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {

                    Text(
                        if (isOffline)
                            "Dictionary installed. All lookups work offline — no internet needed."
                        else
                            "Download once (~10 MB). After that, look up any word without internet — forever.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when (val ds = downloadState) {
                        is DownloadState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { ds.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Orange,
                                    trackColor = Orange.copy(0.2f)
                                )
                                Text(
                                    buildString {
                                        append("Downloading… ${(ds.progress * 100).toInt()}%")
                                        if (ds.bytesDownloaded > 0) {
                                            append(" (${ds.bytesDownloaded / 1024 / 1024} MB)")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is DownloadState.Done -> {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = Green, modifier = Modifier.size(18.dp))
                                Text("Downloaded successfully!",
                                    style = MaterialTheme.typography.bodySmall, color = Green)
                            }
                        }
                        is DownloadState.Error -> {
                            Text(ds.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        else -> {}
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!isOffline && downloadState !is DownloadState.Downloading) {
                            Button(
                                onClick = { scope.launch { repository.downloadDictionary() } },
                                colors = ButtonDefaults.buttonColors(containerColor = Orange),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Download (~10 MB)",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (isOffline) {
                            OutlinedButton(
                                onClick = {
                                    repository.deleteDictionary()
                                    isOffline = false
                                    dictState = DictionaryState.Idle
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remove", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    body: String
) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
