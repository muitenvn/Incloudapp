package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CloudAppPreferences
import com.example.data.model.CloudProviderCatalog
import com.example.data.model.CloudProviderId
import com.example.data.model.CloudProviderSpec
import com.example.data.model.ProviderHealth
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.update.AppUpdateState

@Composable
fun CloudServicesScreen(
    preferences: CloudAppPreferences,
    providerHealth: Map<CloudProviderId, ProviderHealth>,
    isProbing: Boolean,
    currentVersionLabel: String,
    resolvedUpdateRepo: String,
    updateState: AppUpdateState,
    onProbeAll: () -> Unit,
    onCheckUpdatesNow: () -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onSaveUpdateRepo: (String) -> Unit,
    onAutoUploadChange: (Boolean) -> Unit,
    onPopupModeChange: (Boolean) -> Unit,
    onToggleProvider: (CloudProviderId, Boolean) -> Unit,
    onSaveProviderKey: (CloudProviderId, String) -> Unit,
    onSaveCustomConfig: (endpoint: String, field: String, authHeader: String, jsonPath: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingProvider by remember { mutableStateOf<CloudProviderSpec?>(null) }
    var showRepoDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cloud_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = onProbeAll,
                    enabled = !isProbing,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("probe_servers_button")
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.cloud_probing))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.cloud_probe_all))
                    }
                }
            }
        }

        // Share sheet behavior settings
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cloud_section_behavior),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    SettingToggleRow(
                        title = stringResource(R.string.cloud_auto_upload_title),
                        subtitle = stringResource(R.string.cloud_auto_upload_subtitle),
                        checked = preferences.autoUploadOnShare,
                        onCheckedChange = onAutoUploadChange,
                        testTag = "toggle_auto_upload"
                    )

                    SettingToggleRow(
                        title = stringResource(R.string.cloud_popup_mode_title),
                        subtitle = stringResource(R.string.cloud_popup_mode_subtitle),
                        checked = preferences.sharePopupMode,
                        onCheckedChange = onPopupModeChange,
                        testTag = "toggle_popup_mode"
                    )
                }
            }
        }

        // App Auto-Update section
        item {
            AppUpdateSettingsCard(
                currentVersionLabel = currentVersionLabel,
                resolvedUpdateRepo = resolvedUpdateRepo,
                autoCheckEnabled = preferences.autoCheckUpdates,
                updateState = updateState,
                onAutoCheckChange = onAutoCheckUpdatesChange,
                onCheckNow = onCheckUpdatesNow,
                onConfigureRepo = { showRepoDialog = true }
            )
        }

        item {
            Text(
                text = stringResource(R.string.cloud_section_providers),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(CloudProviderCatalog.allProviders, key = { it.providerId.id }) { spec ->
            ProviderCard(
                spec = spec,
                preferences = preferences,
                health = providerHealth[spec.providerId],
                onToggle = { enabled -> onToggleProvider(spec.providerId, enabled) },
                onConfigure = { editingProvider = spec }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }

    val providerToEdit = editingProvider
    if (providerToEdit != null) {
        if (providerToEdit.providerId == CloudProviderId.CUSTOM) {
            CustomServerConfigDialog(
                preferences = preferences,
                onDismiss = { editingProvider = null },
                onSave = { endpoint, field, auth, path ->
                    editingProvider = null
                    onSaveCustomConfig(endpoint, field, auth, path)
                }
            )
        } else {
            val currentValue = when (providerToEdit.providerId) {
                CloudProviderId.IMGBB -> preferences.imgbbApiKey
                CloudProviderId.GOFILE -> preferences.gofileToken
                CloudProviderId.CATBOX -> preferences.catboxUserHash
                else -> ""
            }
            ProviderTokenDialog(
                spec = providerToEdit,
                initialValue = currentValue,
                onDismiss = { editingProvider = null },
                onSave = { token ->
                    editingProvider = null
                    onSaveProviderKey(providerToEdit.providerId, token)
                }
            )
        }
    }

    if (showRepoDialog) {
        UpdateRepoConfigDialog(
            initialRepo = resolvedUpdateRepo,
            onDismiss = { showRepoDialog = false },
            onSave = { repo ->
                showRepoDialog = false
                onSaveUpdateRepo(repo)
            }
        )
    }
}

@Composable
private fun AppUpdateSettingsCard(
    currentVersionLabel: String,
    resolvedUpdateRepo: String,
    autoCheckEnabled: Boolean,
    updateState: AppUpdateState,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onConfigureRepo: () -> Unit
) {
    val isChecking = updateState is AppUpdateState.Checking
    val statusBadge = when (updateState) {
        is AppUpdateState.UpdateAvailable -> "Có bản mới ${updateState.release.tagName}"
        is AppUpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is AppUpdateState.Checking -> stringResource(R.string.update_checking)
        is AppUpdateState.Downloading -> stringResource(R.string.update_downloading)
        is AppUpdateState.ReadyToInstall -> "Đã tải xong bản mới"
        else -> "Phiên bản $currentVersionLabel"
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.update_section_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (updateState is AppUpdateState.UpdateAvailable) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (updateState is AppUpdateState.UpdateAvailable) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            SettingToggleRow(
                title = stringResource(R.string.update_auto_check_title),
                subtitle = stringResource(R.string.update_auto_check_subtitle),
                checked = autoCheckEnabled,
                onCheckedChange = onAutoCheckChange,
                testTag = "toggle_auto_check_updates"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCheckNow,
                    enabled = !isChecking,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("settings_check_updates_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.update_check_latest_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onConfigureRepo,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("settings_update_repo_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(stringResource(R.string.update_repo_button))
                }
            }

            if (resolvedUpdateRepo.isNotBlank()) {
                Text(
                    text = "GitHub: $resolvedUpdateRepo · Hiện tại: $currentVersionLabel",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun UpdateRepoConfigDialog(
    initialRepo: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var repoText by remember(initialRepo) { mutableStateOf(initialRepo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_repo_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Bản APK tạo từ GitHub Actions sẽ tự gắn sẵn kho GitHub. Bạn có thể chỉnh tại đây nếu cần kiểm tra từ kho khác.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = repoText,
                    onValueChange = { repoText = it },
                    label = { Text(stringResource(R.string.update_repo_input_label)) },
                    placeholder = { Text(stringResource(R.string.update_repo_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(repoText) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun ProviderCard(
    spec: CloudProviderSpec,
    preferences: CloudAppPreferences,
    health: ProviderHealth?,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit
) {
    val enabled = preferences.isProviderEnabled(spec.providerId)
    val configured = preferences.isProviderConfigured(spec.providerId)
    val hasKey = preferences.hasOptionalKey(spec.providerId)
    val canConfigure = spec.requiresApiKey || spec.supportsOptionalKey

    val latencyLabel = when {
        health == null -> stringResource(R.string.cloud_latency_unmeasured)
        health.isReachable == true && health.latencyMs != null -> "${health.latencyMs} ms"
        health.isReachable == false -> stringResource(R.string.cloud_latency_offline)
        else -> health.statusNote ?: stringResource(R.string.cloud_latency_unmeasured)
    }

    val latencyContainerColor = when (health?.isReachable) {
        true -> MaterialTheme.colorScheme.primaryContainer
        false -> MaterialTheme.colorScheme.errorContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val latencyTextColor = when (health?.isReachable) {
        true -> MaterialTheme.colorScheme.onPrimaryContainer
        false -> MaterialTheme.colorScheme.onErrorContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled && configured) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = if (enabled && configured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = spec.shortSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("provider_switch_${spec.providerId.id}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = latencyContainerColor
                    ) {
                        Text(
                            text = latencyLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = latencyTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Tối đa ${spec.maxFileSizeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (spec.requiresApiKey && !configured) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.cloud_needs_setup),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (hasKey) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.cloud_configured),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (canConfigure) {
                    OutlinedButton(
                        onClick = onConfigure,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("configure_provider_${spec.providerId.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.cloud_configure),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderTokenDialog(
    spec: CloudProviderSpec,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var token by remember(initialValue) { mutableStateOf(initialValue) }

    val hintText = when (spec.providerId) {
        CloudProviderId.IMGBB -> "Nhập API Key miễn phí từ api.imgbb.com để tải ảnh trực tiếp."
        CloudProviderId.GOFILE -> "Tùy chọn: nhập Account Token từ gofile.io/myProfile để lưu tệp vào tài khoản riêng."
        CloudProviderId.CATBOX -> "Tùy chọn: nhập Userhash Catbox để quản lý tệp trong tài khoản cá nhân."
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Liên kết ${spec.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.cloud_config_api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(token) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun CustomServerConfigDialog(
    preferences: CloudAppPreferences,
    onDismiss: () -> Unit,
    onSave: (endpoint: String, field: String, authHeader: String, jsonPath: String) -> Unit
) {
    var endpoint by remember(preferences) { mutableStateOf(preferences.customEndpointUrl) }
    var field by remember(preferences) { mutableStateOf(preferences.customFieldName) }
    var authHeader by remember(preferences) { mutableStateOf(preferences.customAuthHeader) }
    var jsonPath by remember(preferences) { mutableStateOf(preferences.customJsonUrlPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cấu hình máy chủ riêng") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text(stringResource(R.string.cloud_config_endpoint)) },
                    placeholder = { Text("https://upload.domain.com/api") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text(stringResource(R.string.cloud_config_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = authHeader,
                    onValueChange = { authHeader = it },
                    label = { Text(stringResource(R.string.cloud_config_auth_header)) },
                    placeholder = { Text("Bearer ...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = jsonPath,
                    onValueChange = { jsonPath = it },
                    label = { Text(stringResource(R.string.cloud_config_json_path)) },
                    placeholder = { Text("data.url hoặc url") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(endpoint, field, authHeader, jsonPath) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}
