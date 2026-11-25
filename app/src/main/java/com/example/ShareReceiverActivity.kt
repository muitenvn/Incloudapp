package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.ui.screens.SharePopupScreen
import com.example.ui.theme.CloudDropTheme
import com.example.util.FileMetadataResolver
import com.example.viewmodel.SharePopupViewModel
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: SharePopupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val app = application as CloudDropApplication
        lifecycleScope.launch {
            val prefs = app.repository.preferencesRepository.currentPreferences()
            if (!prefs.sharePopupMode) {
                forwardToMainActivity()
                return@launch
            }

            val incomingPayloads = FileMetadataResolver.extractPayloadsFromIntent(
                this@ShareReceiverActivity,
                intent
            )
            viewModel.initializeIncomingFiles(incomingPayloads)

            setContent {
                CloudDropTheme(darkTheme = true) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val livePrefs by viewModel.preferences.collectAsStateWithLifecycle()
                    val selectedRetention by viewModel.selectedRetention.collectAsStateWithLifecycle()
                    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()

                    SharePopupScreen(
                        uiState = uiState,
                        preferences = livePrefs,
                        selectedRetention = selectedRetention,
                        selectedProvider = selectedProvider,
                        onSelectRetention = viewModel::selectRetention,
                        onSelectProvider = viewModel::selectProvider,
                        onStartUpload = viewModel::startUpload,
                        onCancelUpload = viewModel::cancelUpload,
                        onOpenFullApp = {
                            startActivity(
                                Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                            )
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    private fun forwardToMainActivity() {
        val forward = Intent(intent).apply {
            setClass(this@ShareReceiverActivity, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(forward)
        finish()
    }
}
