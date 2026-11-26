package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CloudAppPreferences
import com.example.data.model.CloudProviderCatalog
import com.example.data.model.CloudProviderId
import com.example.data.model.ProviderHealth
import com.example.data.model.RetentionPreference
import com.example.data.model.SharedFilePayload
import com.example.data.model.UploadUiState
import com.example.ui.components.UploadStatusSection
import com.example.util.FileMetadataResolver

@Composable
fun UploadScreen(
    uploadState: UploadUiState,
    preferences: CloudAppPreferences,
    providerHealth: Map<CloudProviderId, ProviderHealth>,
    selectedRetention: RetentionPreference,
    selectedProviderOverride: CloudProviderId?,
    onSelectRetention: (RetentionPreference) -> Unit,
    onSelectProviderOverride: (CloudProviderId?) -> Unit,
    onUploadFiles: (List<SharedFilePayload>) -> Unit,
    onCancelUpload: () -> Unit,
    onResetUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showQuickTextDialog by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker for Photos & Videos (up to 30 items grouped into 1 link)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val payloads = uris.map { FileMetadataResolver.resolveUri(context, it) }
            onUploadFiles(payloads)
        }
    }

    // Zero-permission Storage Access Framework picker for any file type
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val payloads = uris.map { FileMetadataResolver.resolveUri(context, it) }
            onUploadFiles(payloads)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeroShareBanner()
                    QuickPickSection(
                        onPickMedia = {
                            mediaPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onPickFiles = { documentPickerLauncher.launch(arrayOf("*/*")) },
                        onQuickText = { showQuickTextDialog = true }
                    )
                    ServerAndRetentionControls(
                        preferences = preferences,
                        providerHealth = providerHealth,
                        selectedRetention = selectedRetention,
                        selectedProviderOverride = selectedProviderOverride,
                        onSelectRetention = onSelectRetention,
                        onSelectProviderOverride = onSelectProviderOverride
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uploadState is UploadUiState.Idle) {
                        ShareSheetGuideCard()
                    } else {
                        UploadStatusSection(
                            state = uploadState,
                            compact = false,
                            onCancel = onCancelUpload,
                            onRetry = onUploadFiles,
                            onReset = onResetUpload
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedVisibility(visible = uploadState !is UploadUiState.Idle) {
                    UploadStatusSection(
                        state = uploadState,
                        compact = false,
                        onCancel = onCancelUpload,
                        onRetry = onUploadFiles,
                        onReset = onResetUpload
                    )
                }

                if (uploadState is UploadUiState.Idle) {
                    HeroShareBanner()
                }

                QuickPickSection(
                    onPickMedia = {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onPickFiles = { documentPickerLauncher.launch(arrayOf("*/*")) },
                    onQuickText = { showQuickTextDialog = true }
                )

                ServerAndRetentionControls(
                    preferences = preferences,
                    providerHealth = providerHealth,
                    selectedRetention = selectedRetention,
                    selectedProviderOverride = selectedProviderOverride,
                    onSelectRetention = onSelectRetention,
                    onSelectProviderOverride = onSelectProviderOverride
                )

                if (uploadState is UploadUiState.Idle) {
                    ShareSheetGuideCard()
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showQuickTextDialog) {
        QuickTextUploadDialog(
            onDismiss = { showQuickTextDialog = false },
            onSubmit = { fileName, content ->
                showQuickTextDialog = false
                onUploadFiles(listOf(FileMetadataResolver.createTextPayload(fileName, content)))
            }
        )
    }
}

@Composable
private fun HeroShareBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_cloud_hero_1795607925007),
                    contentDescription = stringResource(R.string.cd_hero_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Share Sheet → Cloud → QR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.hero_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.hero_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickPickSection(
    onPickMedia: () -> Unit,
    onPickFiles: () -> Unit,
    onQuickText: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.section_quick_upload),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionTile(
                icon = Icons.Filled.PermMedia,
                title = stringResource(R.string.action_pick_media),
                subtitle = "Photo Picker",
                accent = MaterialTheme.colorScheme.primary,
                onClick = onPickMedia,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pick_media_button")
            )
            QuickActionTile(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.action_pick_file),
                subtitle = "PDF, ZIP, APK…",
                accent = MaterialTheme.colorScheme.secondary,
                onClick = onPickFiles,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pick_file_button")
            )
            QuickActionTile(
                icon = Icons.Filled.Description,
                title = stringResource(R.string.action_quick_text),
                subtitle = "Tạo tệp .txt",
                accent = MaterialTheme.colorScheme.tertiary,
                onClick = onQuickText,
                modifier = Modifier
                    .weight(1f)
                    .testTag("pick_text_button")
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ServerAndRetentionControls(
    preferences: CloudAppPreferences,
    providerHealth: Map<CloudProviderId, ProviderHealth>,
    selectedRetention: RetentionPreference,
    selectedProviderOverride: CloudProviderId?,
    onSelectRetention: (RetentionPreference) -> Unit,
    onSelectProviderOverride: (CloudProviderId?) -> Unit
) {
    val availableProviders = remember(preferences) {
        CloudProviderCatalog.allProviders.filter { spec ->
            preferences.isProviderEnabled(spec.providerId) &&
                (!spec.requiresApiKey || preferences.isProviderConfigured(spec.providerId))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.section_server_mode),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedProviderOverride == null,
                        onClick = { onSelectProviderOverride(null) },
                        label = { Text(stringResource(R.string.server_auto)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("server_chip_auto")
                    )
                }

                items(availableProviders, key = { it.providerId.id }) { spec ->
                    val latency = providerHealth[spec.providerId]?.latencyMs
                    val label = if (latency != null) {
                        "${spec.displayName} (${latency}ms)"
                    } else {
                        spec.displayName
                    }
                    FilterChip(
                        selected = selectedProviderOverride == spec.providerId,
                        onClick = { onSelectProviderOverride(spec.providerId) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("server_chip_${spec.providerId.id}")
                    )
                }
            }

            Text(
                text = stringResource(R.string.section_retention),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 4.dp)
            ) {
                items(RetentionPreference.entries, key = { it.id }) { option ->
                    FilterChip(
                        selected = selectedRetention == option,
                        onClick = { onSelectRetention(option) },
                        label = { Text(option.labelVi) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("retention_chip_${option.id}")
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareSheetGuideCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Cách chia sẻ nhanh bằng pop-up",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            GuideStepRow("1", "Mở Ảnh, Video hoặc Tệp (có thể chọn nhiều ảnh/tệp cùng lúc).")
            GuideStepRow("2", "Nhấn nút Chia sẻ (Share) và chọn \"Tải lên InCloud\".")
            GuideStepRow("3", "Bảng pop-up tự gộp nhóm ảnh/tệp vào chung 1 đường link + 1 mã QR duy nhất để gửi và lưu trữ cực gọn.")
        }
    }
}

@Composable
private fun GuideStepRow(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickTextUploadDialog(
    onDismiss: () -> Unit,
    onSubmit: (fileName: String, content: String) -> Unit
) {
    var fileName by remember { mutableStateOf("ghi-chu-nhanh") }
    var textContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_text_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(stringResource(R.string.quick_text_filename_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_text_filename_input")
                )
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    label = { Text(stringResource(R.string.quick_text_content_label)) },
                    placeholder = { Text(stringResource(R.string.quick_text_placeholder)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_text_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(fileName, textContent) },
                enabled = textContent.isNotBlank(),
                modifier = Modifier.testTag("quick_text_submit_button")
            ) {
                Text(stringResource(R.string.action_upload_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
