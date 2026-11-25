package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CloudAppPreferences
import com.example.data.model.CloudProviderId
import com.example.data.model.RetentionPreference
import com.example.data.model.SharedFilePayload
import com.example.data.model.UploadUiState
import com.example.ui.components.FileThumbnailBadge
import com.example.ui.components.UploadStatusSection
import com.example.util.FileMetadataResolver

@Composable
fun SharePopupScreen(
    uiState: UploadUiState,
    preferences: CloudAppPreferences,
    selectedRetention: RetentionPreference,
    selectedProvider: CloudProviderId?,
    onSelectRetention: (RetentionPreference) -> Unit,
    onSelectProvider: (CloudProviderId?) -> Unit,
    onStartUpload: () -> Unit,
    onCancelUpload: () -> Unit,
    onOpenFullApp: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        val isWide = maxWidth >= 600.dp
        val alignment = if (isWide) Alignment.Center else Alignment.BottomCenter
        val sheetShape = if (isWide) {
            RoundedCornerShape(26.dp)
        } else {
            RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        }

        Surface(
            modifier = Modifier
                .align(alignment)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = maxHeight * 0.92f)
                .padding(if (isWide) 20.dp else 0.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // absorb clicks inside sheet
                ),
            shape = sheetShape,
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Drag handle on mobile bottom sheet
                if (!isWide) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }

                // Header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onOpenFullApp,
                            modifier = Modifier.testTag("popup_open_app_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_open_full_app),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("popup_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close_popup)
                            )
                        }
                    }
                }

                when (uiState) {
                    is UploadUiState.Ready -> {
                        ReadyFilesPreview(files = uiState.files)
                        ServerAndRetentionControls(
                            preferences = preferences,
                            providerHealth = emptyMap(),
                            selectedRetention = selectedRetention,
                            selectedProviderOverride = selectedProvider,
                            onSelectRetention = onSelectRetention,
                            onSelectProviderOverride = onSelectProvider
                        )
                        Button(
                            onClick = onStartUpload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("popup_upload_now_button"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_upload_now))
                        }
                    }

                    else -> {
                        UploadStatusSection(
                            state = uiState,
                            compact = true,
                            onCancel = onCancelUpload,
                            onRetry = { onStartUpload() },
                            onReset = onDismiss
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ReadyFilesPreview(files: List<SharedFilePayload>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val first = files.first()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileThumbnailBadge(
                    mimeType = first.mimeType,
                    previewUri = first.uri
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (files.size == 1) {
                            first.fileName
                        } else {
                            "${files.size} tệp đã sẵn sàng tải lên"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val totalSize = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
                    Text(
                        text = "${FileMetadataResolver.formatFileSize(totalSize)} · ${first.mimeType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
