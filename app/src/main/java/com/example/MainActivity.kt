package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.InstantQrGeneratorDialog
import com.example.ui.screens.CloudServicesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.theme.CloudDropTheme
import com.example.util.FileMetadataResolver
import com.example.viewmodel.MainViewModel

enum class MainTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    UPLOAD(
        labelRes = R.string.nav_upload,
        selectedIcon = Icons.Filled.CloudUpload,
        unselectedIcon = Icons.Outlined.CloudUpload,
        tag = "nav_tab_upload"
    ),
    HISTORY(
        labelRes = R.string.nav_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        tag = "nav_tab_history"
    ),
    CLOUD(
        labelRes = R.string.nav_cloud,
        selectedIcon = Icons.Filled.Cloud,
        unselectedIcon = Icons.Outlined.Cloud,
        tag = "nav_tab_cloud"
    )
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        handleIncomingShareIfPresent(intent)

        setContent {
            CloudDropTheme(darkTheme = true) {
                CloudDropAppShell(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIfPresent(intent)
    }

    private fun handleIncomingShareIfPresent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            val payloads = FileMetadataResolver.extractPayloadsFromIntent(this, intent)
            if (payloads.isNotEmpty()) {
                viewModel.startUpload(payloads)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudDropAppShell(viewModel: MainViewModel) {
    var activeTab by rememberSaveable { mutableStateOf(MainTab.UPLOAD) }
    var showQuickQrDialog by rememberSaveable { mutableStateOf(false) }
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val providerHealth by viewModel.providerHealth.collectAsStateWithLifecycle()
    val isProbing by viewModel.isProbingServers.collectAsStateWithLifecycle()
    val sessionRetention by viewModel.selectedRetention.collectAsStateWithLifecycle()
    val sessionProvider by viewModel.selectedProviderOverride.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()
    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()

    val effectiveRetention = sessionRetention ?: preferences.defaultRetention
    val effectiveProvider = sessionProvider ?: preferences.preferredProviderId

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { showQuickQrDialog = true },
                            modifier = Modifier.testTag("top_bar_quick_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QrCode2,
                                contentDescription = stringResource(R.string.quick_qr_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        MainTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (activeTab) {
                        MainTab.UPLOAD -> UploadScreen(
                            uploadState = uploadState,
                            preferences = preferences,
                            providerHealth = providerHealth,
                            selectedRetention = effectiveRetention,
                            selectedProviderOverride = effectiveProvider,
                            onSelectRetention = viewModel::setSessionRetention,
                            onSelectProviderOverride = viewModel::setSessionProviderOverride,
                            onUploadFiles = viewModel::startUpload,
                            onCancelUpload = viewModel::cancelUpload,
                            onResetUpload = viewModel::resetUploadState
                        )

                        MainTab.HISTORY -> HistoryScreen(
                            items = historyItems,
                            searchQuery = searchQuery,
                            activeFilter = historyFilter,
                            onSearchQueryChange = viewModel::setHistorySearchQuery,
                            onFilterChange = viewModel::setHistoryFilter,
                            onDeleteItem = viewModel::deleteHistoryItem,
                            onClearAll = viewModel::clearAllHistory
                        )

                        MainTab.CLOUD -> CloudServicesScreen(
                            preferences = preferences,
                            providerHealth = providerHealth,
                            isProbing = isProbing,
                            onProbeAll = viewModel::probeAllServers,
                            onAutoUploadChange = viewModel::setAutoUploadOnShare,
                            onPopupModeChange = viewModel::setSharePopupMode,
                            onToggleProvider = viewModel::setProviderEnabled,
                            onSaveProviderKey = viewModel::updateProviderKey,
                            onSaveCustomConfig = viewModel::updateCustomServerConfig
                        )
                    }
                }
            }
        }
    }

    if (showQuickQrDialog) {
        InstantQrGeneratorDialog(onDismiss = { showQuickQrDialog = false })
    }
}
