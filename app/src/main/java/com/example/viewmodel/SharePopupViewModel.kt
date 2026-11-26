package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CloudDropApplication
import com.example.data.CloudDropRepository
import com.example.data.model.CloudAppPreferences
import com.example.data.model.CloudProviderId
import com.example.data.model.RetentionPreference
import com.example.data.model.SharedFilePayload
import com.example.data.model.UploadUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SharePopupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CloudDropRepository =
        (application as CloudDropApplication).repository

    val preferences: StateFlow<CloudAppPreferences> = repository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CloudAppPreferences()
        )

    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    private val _selectedRetention = MutableStateFlow(RetentionPreference.AUTO_24H)
    val selectedRetention: StateFlow<RetentionPreference> = _selectedRetention.asStateFlow()

    private val _selectedProvider = MutableStateFlow<CloudProviderId?>(null)
    val selectedProvider: StateFlow<CloudProviderId?> = _selectedProvider.asStateFlow()

    private var pendingFiles: List<SharedFilePayload> = emptyList()
    private var initialized = false
    private var uploadJob: Job? = null

    fun initializeIncomingFiles(files: List<SharedFilePayload>) {
        if (initialized) return
        initialized = true
        pendingFiles = files

        if (files.isEmpty()) {
            _uiState.value = UploadUiState.Error(
                message = "Không đọc được tệp hoặc nội dung được chia sẻ."
            )
            return
        }

        viewModelScope.launch {
            val prefs = repository.preferencesRepository.currentPreferences()
            _selectedRetention.value = prefs.defaultRetention
            _selectedProvider.value = prefs.preferredProviderId
            if (prefs.autoUploadOnShare) {
                startUpload()
            } else {
                _uiState.value = UploadUiState.Ready(files)
            }
        }
    }

    fun selectRetention(retention: RetentionPreference) {
        _selectedRetention.value = retention
    }

    fun selectProvider(providerId: CloudProviderId?) {
        _selectedProvider.value = providerId
    }

    fun startUpload() {
        val files = pendingFiles
        if (files.isEmpty()) return
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            val displayPayload = buildGroupDisplayPayload(files)
            try {
                _uiState.value = UploadUiState.Preparing(displayPayload.fileName)
                val uploaded = repository.uploadGroupAndRecord(
                    payloads = files,
                    retention = _selectedRetention.value,
                    overrideProviderId = _selectedProvider.value,
                    onSelecting = {
                        _uiState.value = UploadUiState.SelectingServer(
                            file = displayPayload,
                            currentIndex = 1,
                            totalFiles = 1
                        )
                    },
                    onProgress = { progress ->
                        _uiState.value = UploadUiState.Uploading(
                            file = displayPayload,
                            providerName = progress.provider.displayName,
                            providerLatencyMs = progress.providerLatencyMs,
                            bytesUploaded = progress.bytesUploaded,
                            totalBytes = progress.totalBytes,
                            bytesPerSecond = progress.bytesPerSecond,
                            isFailover = progress.isFailover,
                            currentIndex = 1,
                            totalFiles = 1
                        )
                    }
                )
                _uiState.value = UploadUiState.Success(results = listOf(uploaded))
            } catch (_: CancellationException) {
                _uiState.value = UploadUiState.Ready(files)
            } catch (e: Exception) {
                _uiState.value = UploadUiState.Error(
                    message = e.message ?: "Tải lên thất bại. Vui lòng thử lại.",
                    failedFile = displayPayload,
                    originalFiles = files
                )
            }
        }
    }

    private fun buildGroupDisplayPayload(files: List<SharedFilePayload>): SharedFilePayload {
        if (files.size == 1) return files.first()
        val allImages = files.all { it.isImage }
        val totalSize = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val label = if (allImages) {
            "Nhóm ${files.size} ảnh"
        } else {
            "Nhóm ${files.size} tệp"
        }
        return files.first().copy(
            fileName = label,
            sizeBytes = totalSize
        )
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        if (pendingFiles.isNotEmpty()) {
            _uiState.value = UploadUiState.Ready(pendingFiles)
        }
    }
}
