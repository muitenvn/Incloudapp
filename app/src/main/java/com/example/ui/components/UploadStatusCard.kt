package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SharedFilePayload
import com.example.data.model.UploadUiState
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.FileMetadataResolver

@Composable
fun FileThumbnailBadge(
    mimeType: String,
    previewUri: Any?,
    modifier: Modifier = Modifier
) {
    val isImage = mimeType.startsWith("image/", ignoreCase = true)
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (isImage && previewUri != null) {
            AsyncImage(
                model = previewUri,
                contentDescription = stringResource(R.string.cd_file_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp)
            )
        } else {
            Icon(
                imageVector = iconForMimeType(mimeType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

fun iconForMimeType(mimeType: String): ImageVector = when {
    mimeType.startsWith("image/", ignoreCase = true) -> Icons.Filled.Image
    mimeType.startsWith("video/", ignoreCase = true) -> Icons.Filled.VideoFile
    mimeType.startsWith("audio/", ignoreCase = true) -> Icons.Filled.AudioFile
    mimeType.startsWith("text/", ignoreCase = true) -> Icons.Filled.Description
    else -> Icons.Filled.InsertDriveFile
}

@Composable
fun UploadStatusSection(
    state: UploadUiState,
    compact: Boolean = false,
    onCancel: () -> Unit,
    onRetry: (List<SharedFilePayload>) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        UploadUiState.Idle -> Unit

        is UploadUiState.Ready -> Unit

        is UploadUiState.Preparing -> {
            ProgressCardShell(
                title = state.fileName,
                subtitle = stringResource(R.string.status_preparing),
                progress = null,
                detailRight = null,
                onCancel = onCancel,
                modifier = modifier
            )
        }

        is UploadUiState.SelectingServer -> {
            val counter = if (state.totalFiles > 1) {
                " (${state.currentIndex}/${state.totalFiles})"
            } else {
                ""
            }
            ProgressCardShell(
                title = "${state.file.fileName}$counter",
                subtitle = stringResource(R.string.status_selecting_server),
                progress = null,
                detailRight = FileMetadataResolver.formatFileSize(state.file.sizeBytes),
                previewPayload = state.file,
                onCancel = onCancel,
                modifier = modifier
            )
        }

        is UploadUiState.Uploading -> {
            val counter = if (state.totalFiles > 1) {
                " (${state.currentIndex}/${state.totalFiles})"
            } else {
                ""
            }
            val statusText = if (state.isFailover) {
                stringResource(R.string.status_retrying, state.providerName)
            } else {
                stringResource(R.string.status_uploading, state.providerName)
            }
            val percent = (state.progress * 100).toInt()
            val speed = FileMetadataResolver.formatSpeed(state.bytesPerSecond)
            val latencyPart = state.providerLatencyMs?.let { " · ${it}ms" }.orEmpty()
            val detailRight = buildString {
                append("$percent%")
                if (speed.isNotEmpty()) append(" · $speed")
                append(latencyPart)
            }

            ProgressCardShell(
                title = "${state.file.fileName}$counter",
                subtitle = statusText,
                progress = state.progress,
                detailLeft = "${FileMetadataResolver.formatFileSize(state.bytesUploaded)} / ${
                    FileMetadataResolver.formatFileSize(state.totalBytes)
                }",
                detailRight = detailRight,
                previewPayload = state.file,
                onCancel = onCancel,
                modifier = modifier
            )
        }

        is UploadUiState.Success -> {
            var selectedIndex by remember(state.results) { mutableIntStateOf(0) }
            val safeIndex = selectedIndex.coerceIn(0, (state.results.size - 1).coerceAtLeast(0))

            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.results.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        itemsIndexed(state.results) { idx, item ->
                            FilterChip(
                                selected = idx == safeIndex,
                                onClick = { selectedIndex = idx },
                                label = {
                                    Text(
                                        text = "${idx + 1}. ${item.fileName}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(120.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (state.failedNames.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Không tải được ${state.failedNames.size} tệp: ${state.failedNames.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                ShareResultCard(
                    item = state.results[safeIndex],
                    compact = compact
                )

                if (!compact) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("upload_another_button"),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(stringResource(R.string.action_upload_another))
                    }
                }
            }
        }

        is UploadUiState.Error -> {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.status_failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.originalFiles.isNotEmpty()) {
                            Button(
                                onClick = { onRetry(state.originalFiles) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("retry_upload_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_retry_upload))
                            }
                        }
                        OutlinedButton(
                            onClick = onReset,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dismiss_error_button")
                        ) {
                            Text(stringResource(R.string.action_close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCardShell(
    title: String,
    subtitle: String,
    progress: Float?,
    detailLeft: String? = null,
    detailRight: String? = null,
    previewPayload: SharedFilePayload? = null,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (previewPayload != null) {
                    FileThumbnailBadge(
                        mimeType = previewPayload.mimeType,
                        previewUri = previewPayload.uri
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }

            if (detailLeft != null || detailRight != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = detailLeft.orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = detailRight.orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cancel_upload_button"),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_cancel_upload))
            }
        }
    }
}
