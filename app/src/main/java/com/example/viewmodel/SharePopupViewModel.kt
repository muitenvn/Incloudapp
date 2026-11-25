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
import com.example.data.model.UploadedItemEntity
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
            val completed = mutableListOf<UploadedItemEntity>()
            val failedNames = mutableListOf<String>()

            try {
                files.forEachIndexed { index, payload ->
                    _uiState.value = UploadUiState.Preparing(payload.fileName)
                    try {
                        val uploaded = repository.uploadAndRecord(
                            payload = payload,
                            retention = _selectedRetention.value,
                            overrideProviderId = _selectedProvider.value,
                            onSelecting = {
                                _uiState.value = UploadUiState.SelectingServer(
                                    file = payload,
                                    currentIndex = index + 1,
                                    totalFiles = files.size
                                )
                            },
                            onProgress = { progress ->
                                _uiState.value = UploadUiState.Uploading(
                                    file = payload,
                                    providerName = progress.provider.displayName,
                                    providerLatencyMs = progress.providerLatencyMs,
                                    bytesUploaded = progress.bytesUploaded,
                                    totalBytes = progress.totalBytes,
                                    bytesPerSecond = progress.bytesPerSecond,
                                    isFailover = progress.isFailover,
                                    currentIndex = index + 1,
                                    totalFiles = files.size
                                )
                            }
                        )
                        completed.add(uploaded)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        if (files.size == 1) {
                            _uiState.value = UploadUiState.Error(
                                message = e.message ?: "Tải lên thất bại. Vui lòng thử lại.",
                                failedFile = payload,
                                originalFiles = files
                            )
                            return@launch
                        } else {
                            failedNames.add(payload.fileName)
                        }
                    }
                }

                if (completed.isNotEmpty()) {
                    _uiState.value = UploadUiState.Success(
                        results = completed,
                        failedNames = failedNames
                    )
                } else {
                    _uiState.value = UploadUiState.Error(
                        message = "Không thể tải lên các tệp đã chia sẻ.",
                        originalFiles = files
                    )
                }
            } catch (_: CancellationException) {
                _uiState.value = UploadUiState.Ready(files)
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        if (pendingFiles.isNotEmpty()) {
            _uiState.value = UploadUiState.Ready(pendingFiles)
        }
    }
}
