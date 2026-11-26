package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.update.AppUpdateState
import com.example.update.ReleaseUpdateInfo
import com.example.util.FileMetadataResolver
import java.io.File

@Composable
fun AppUpdateDialog(
    state: AppUpdateState,
    currentVersionLabel: String,
    onCheckLatest: () -> Unit,
    onDownloadAndInstall: (ReleaseUpdateInfo) -> Unit,
    onOpenInstaller: (File) -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val shouldShow = when (state) {
        AppUpdateState.Idle -> false
        is AppUpdateState.Checking -> state.isManual
        is AppUpdateState.UpToDate -> state.isManual
        is AppUpdateState.UpdateAvailable -> state.showDialog
        is AppUpdateState.Downloading -> true
        is AppUpdateState.ReadyToInstall -> true
        is AppUpdateState.Error -> state.isManual
    }

    if (!shouldShow) return

    Dialog(
        onDismissRequest = {
            if (state !is AppUpdateState.Downloading) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .widthIn(max = 480.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = when (state) {
                                is AppUpdateState.UpToDate -> Icons.Filled.CheckCircle
                                is AppUpdateState.Error -> Icons.Filled.ErrorOutline
                                else -> Icons.Filled.SystemUpdate
                            },
                            contentDescription = null,
                            tint = when (state) {
                                is AppUpdateState.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = when (state) {
                                is AppUpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                                is AppUpdateState.Error -> "Không thể cập nhật"
                                is AppUpdateState.Checking -> stringResource(R.string.update_section_title)
                                else -> stringResource(R.string.update_dialog_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (state !is AppUpdateState.Downloading) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("update_dialog_close")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.action_close)
                            )
                        }
                    }
                }

                when (state) {
                    is AppUpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = stringResource(R.string.update_checking),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is AppUpdateState.UpToDate -> {
                        Text(
                            text = "Phiên bản hiện tại: ${state.currentVersionLabel}" +
                                (state.latestTag?.let { "\nBản mới nhất trên GitHub: $it" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCheckLatest,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("update_recheck_button")
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kiểm tra lại")
                            }
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text(stringResource(R.string.action_close))
                            }
                        }
                    }

                    is AppUpdateState.UpdateAvailable -> {
                        ReleaseDetailsBlock(
                            release = state.release,
                            currentVersionLabel = currentVersionLabel
                        )

                        Button(
                            onClick = { onDownloadAndInstall(state.release) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("update_download_now_button"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.update_download_and_install))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onCheckLatest,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("update_check_latest_in_dialog"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Kiểm tra mới nhất",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("update_later_button"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(stringResource(R.string.update_later))
                            }
                        }
                    }

                    is AppUpdateState.Downloading -> {
                        Text(
                            text = "${stringResource(R.string.update_downloading)} (${state.release.tagName})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val progress = state.progress
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(100.dp)),
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(100.dp)),
                                strokeCap = StrokeCap.Round
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val left = if (state.totalBytes > 0L) {
                                "${FileMetadataResolver.formatFileSize(state.bytesDownloaded)} / ${
                                    FileMetadataResolver.formatFileSize(state.totalBytes)
                                }"
                            } else {
                                FileMetadataResolver.formatFileSize(state.bytesDownloaded)
                            }
                            val speed = FileMetadataResolver.formatSpeed(state.bytesPerSecond)
                            val pct = progress?.let { "${(it * 100).toInt()}%" }.orEmpty()
                            val right = listOf(pct, speed).filter { it.isNotEmpty() }.joinToString(" · ")

                            Text(
                                text = left,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = right,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        OutlinedButton(
                            onClick = onCancelDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("update_cancel_download_button")
                        ) {
                            Text(stringResource(R.string.action_close))
                        }
                    }

                    is AppUpdateState.ReadyToInstall -> {
                        Text(
                            text = if (state.needsInstallPermission) {
                                stringResource(R.string.update_permission_needed)
                            } else {
                                stringResource(R.string.update_ready_install)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = { onOpenInstaller(state.apkFile) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("update_open_installer_button")
                        ) {
                            Icon(
                                imageVector = if (state.needsInstallPermission) {
                                    Icons.Filled.Security
                                } else {
                                    Icons.Filled.InstallMobile
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.needsInstallPermission) {
                                    stringResource(R.string.update_open_permission_settings)
                                } else {
                                    stringResource(R.string.update_open_installer)
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(stringResource(R.string.update_later))
                        }
                    }

                    is AppUpdateState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val rel = state.release
                                    if (rel != null) onDownloadAndInstall(rel) else onCheckLatest()
                                },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_retry_upload))
                            }
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text(stringResource(R.string.action_close))
                            }
                        }
                    }

                    AppUpdateState.Idle -> Unit
                }
            }
        }
    }
}

@Composable
private fun ReleaseDetailsBlock(
    release: ReleaseUpdateInfo,
    currentVersionLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = currentVersionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = "→",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (release.apkSizeBytes > 0L) {
                Text(
                    text = FileMetadataResolver.formatFileSize(release.apkSizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        if (release.releaseNotes.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
            ) {
                Text(
                    text = release.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                )
            }
        }
    }
}
