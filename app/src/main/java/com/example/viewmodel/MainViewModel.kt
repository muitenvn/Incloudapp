package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CloudDropApplication
import com.example.data.CloudDropRepository
import com.example.data.model.CloudAppPreferences
import com.example.data.model.CloudProviderId
import com.example.data.model.ProviderHealth
import com.example.data.model.RetentionPreference
import com.example.data.model.SharedFilePayload
import com.example.data.model.UploadUiState
import com.example.data.model.UploadedItemEntity
import com.example.update.AppUpdateManager
import com.example.update.AppUpdateState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter {
    ALL, IMAGES, VIDEOS, OTHER
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CloudDropRepository =
        (application as CloudDropApplication).repository

    val updateManager: AppUpdateManager =
        (application as CloudDropApplication).updateManager

    val historyItems: StateFlow<List<UploadedItemEntity>> = repository.historyFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val preferences: StateFlow<CloudAppPreferences> = repository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CloudAppPreferences()
        )

    val providerHealth: StateFlow<Map<CloudProviderId, ProviderHealth>> = repository.providerHealth

    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _isProbingServers = MutableStateFlow(false)
    val isProbingServers: StateFlow<Boolean> = _isProbingServers.asStateFlow()

    private val _selectedRetention = MutableStateFlow<RetentionPreference?>(null)
    val selectedRetention: StateFlow<RetentionPreference?> = _selectedRetention.asStateFlow()

    private val _selectedProviderOverride = MutableStateFlow<CloudProviderId?>(null)
    val selectedProviderOverride: StateFlow<CloudProviderId?> = _selectedProviderOverride.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private var activeUploadJob: Job? = null
    private var updateJob: Job? = null
    private var didAutoCheckUpdates = false

    fun checkForUpdatesOnAppOpen() {
        if (didAutoCheckUpdates) return
        didAutoCheckUpdates = true
        viewModelScope.launch {
            val prefs = repository.preferencesRepository.currentPreferences()
            if (!prefs.autoCheckUpdates) return@launch
            val repo = updateManager.resolveRepoSlug(prefs.updateRepoOverride)
            if (repo.isEmpty()) return@launch
            checkForUpdates(manual = false)
        }
    }

    fun checkForUpdates(manual: Boolean) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _updateState.value = AppUpdateState.Checking(isManual = manual)
            try {
                val prefs = repository.preferencesRepository.currentPreferences()
                val repoSlug = updateManager.resolveRepoSlug(prefs.updateRepoOverride)
                if (repoSlug.isEmpty()) {
                    _updateState.value = if (manual) {
                        AppUpdateState.Error(
                            message = "Chưa liên kết kho GitHub. Hãy cài APK từ GitHub Releases hoặc nhập tên repo (vd: ten_ban/InCloud) trong mục Nguồn GitHub.",
                            isManual = true
                        )
                    } else {
                        AppUpdateState.Idle
                    }
                    return@launch
                }

                val latest = updateManager.checkLatestRelease(repoSlug)
                if (latest == null) {
                    _updateState.value = if (manual) {
                        AppUpdateState.UpToDate(
                            latestTag = null,
                            currentVersionLabel = updateManager.currentVersionDisplay(),
                            isManual = true
                        )
                    } else {
                        AppUpdateState.Idle
                    }
                    return@launch
                }

                if (updateManager.isRemoteNewer(latest.tagName)) {
                    _updateState.value = AppUpdateState.UpdateAvailable(
                        release = latest,
                        showDialog = true
                    )
                } else {
                    _updateState.value = AppUpdateState.UpToDate(
                        latestTag = latest.tagName,
                        currentVersionLabel = updateManager.currentVersionDisplay(),
                        isManual = manual
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _updateState.value = if (manual) {
                    AppUpdateState.Error(
                        message = e.message ?: "Không thể kiểm tra bản cập nhật từ GitHub.",
                        isManual = true
                    )
                } else {
                    AppUpdateState.Idle
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        val current = _updateState.value
        _updateState.value = when (current) {
            is AppUpdateState.UpdateAvailable -> current.copy(showDialog = false)
            else -> AppUpdateState.Idle
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setAutoCheckUpdates(enabled)
        }
    }

    fun setUpdateRepoOverride(repoSlug: String) {
        viewModelScope.launch {
            repository.preferencesRepository.setUpdateRepoOverride(
                updateManager.sanitizeRepoSlug(repoSlug)
            )
        }
    }

    fun setSessionRetention(retention: RetentionPreference) {
        _selectedRetention.value = retention
        viewModelScope.launch {
            repository.preferencesRepository.setDefaultRetention(retention)
        }
    }

    fun setSessionProviderOverride(providerId: CloudProviderId?) {
        _selectedProviderOverride.value = providerId
        viewModelScope.launch {
            repository.preferencesRepository.setPreferredProvider(providerId)
        }
    }

    fun startUpload(files: List<SharedFilePayload>) {
        if (files.isEmpty()) return
        activeUploadJob?.cancel()
        activeUploadJob = viewModelScope.launch {
            val prefs = repository.preferencesRepository.currentPreferences()
            val retention = _selectedRetention.value ?: prefs.defaultRetention
            val overrideProvider = _selectedProviderOverride.value ?: prefs.preferredProviderId
            val displayPayload = buildGroupDisplayPayload(files)

            try {
                _uploadState.value = UploadUiState.Preparing(displayPayload.fileName)
                val uploaded = repository.uploadGroupAndRecord(
                    payloads = files,
                    retention = retention,
                    overrideProviderId = overrideProvider,
                    onSelecting = {
                        _uploadState.value = UploadUiState.SelectingServer(
                            file = displayPayload,
                            currentIndex = 1,
                            totalFiles = 1
                        )
                    },
                    onProgress = { progress ->
                        _uploadState.value = UploadUiState.Uploading(
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
                _uploadState.value = UploadUiState.Success(results = listOf(uploaded))
            } catch (_: CancellationException) {
                _uploadState.value = UploadUiState.Idle
            } catch (e: Exception) {
                _uploadState.value = UploadUiState.Error(
                    message = e.message ?: "Đã xảy ra lỗi khi tải tệp lên đám mây.",
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
        activeUploadJob?.cancel()
        activeUploadJob = null
        _uploadState.value = UploadUiState.Idle
    }

    fun resetUploadState() {
        _uploadState.value = UploadUiState.Idle
    }

    fun probeAllServers() {
        if (_isProbingServers.value) return
        viewModelScope.launch {
            _isProbingServers.value = true
            try {
                repository.probeAllProviders()
            } finally {
                _isProbingServers.value = false
            }
        }
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    fun setAutoUploadOnShare(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setAutoUploadOnShare(enabled)
        }
    }

    fun setSharePopupMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setSharePopupMode(enabled)
        }
    }

    fun setProviderEnabled(providerId: CloudProviderId, enabled: Boolean) {
        viewModelScope.launch {
            repository.preferencesRepository.setProviderEnabled(providerId, enabled)
        }
    }

    fun updateProviderKey(providerId: CloudProviderId, token: String) {
        viewModelScope.launch {
            repository.preferencesRepository.updateProviderKey(providerId, token)
        }
    }

    fun updateCustomServerConfig(
        endpointUrl: String,
        fieldName: String,
        authHeader: String,
        jsonUrlPath: String
    ) {
        viewModelScope.launch {
            repository.preferencesRepository.updateCustomServerConfig(
                endpointUrl = endpointUrl,
                fieldName = fieldName,
                authHeader = authHeader,
                jsonUrlPath = jsonUrlPath
            )
        }
    }
}
