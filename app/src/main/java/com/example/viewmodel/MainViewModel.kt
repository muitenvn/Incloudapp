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
import com.example.update.ReleaseUpdateInfo
import java.io.File
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

    private val _pendingInstallApk = MutableStateFlow<File?>(null)
    val pendingInstallApk: StateFlow<File?> = _pendingInstallApk.asStateFlow()

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
                            message = "Chưa liên kết kho GitHub. Hãy cài APK từ GitHub Releases hoặc nhập tên repo (vd: ten_ban/CloudDrop) trong mục Nguồn GitHub.",
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

    fun downloadUpdate(release: ReleaseUpdateInfo, onReadyToInstall: (File) -> Unit) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _updateState.value = AppUpdateState.Downloading(
                release = release,
                bytesDownloaded = 0L,
                totalBytes = release.apkSizeBytes,
                bytesPerSecond = 0L
            )
            try {
                val apkFile = updateManager.downloadApk(release) { downloaded, total, speed ->
                    _updateState.value = AppUpdateState.Downloading(
                        release = release,
                        bytesDownloaded = downloaded,
                        totalBytes = total,
                        bytesPerSecond = speed
                    )
                }
                val needsPerm = !updateManager.canInstallPackages()
                _pendingInstallApk.value = apkFile
                _updateState.value = AppUpdateState.ReadyToInstall(
                    release = release,
                    apkFile = apkFile,
                    needsInstallPermission = needsPerm
                )
                onReadyToInstall(apkFile)
            } catch (ce: CancellationException) {
                _updateState.value = AppUpdateState.UpdateAvailable(release = release, showDialog = true)
            } catch (e: Exception) {
                _updateState.value = AppUpdateState.Error(
                    message = e.message ?: "Tải tệp cập nhật APK thất bại.",
                    isManual = true,
                    release = release
                )
            }
        }
    }

    fun cancelUpdateDownload() {
        updateJob?.cancel()
        updateJob = null
        _updateState.value = AppUpdateState.Idle
    }

    fun dismissUpdateDialog() {
        val current = _updateState.value
        _updateState.value = when (current) {
            is AppUpdateState.UpdateAvailable -> current.copy(showDialog = false)
            is AppUpdateState.Downloading -> current
            else -> AppUpdateState.Idle
        }
    }

    fun markNeedsInstallPermission(apkFile: File) {
        val current = _updateState.value
        if (current is AppUpdateState.ReadyToInstall) {
            _pendingInstallApk.value = apkFile
            _updateState.value = current.copy(needsInstallPermission = true)
        }
    }

    fun markInstallPermissionGranted() {
        val current = _updateState.value
        if (current is AppUpdateState.ReadyToInstall) {
            _updateState.value = current.copy(needsInstallPermission = false)
        }
    }

    fun clearPendingInstallApk() {
        _pendingInstallApk.value = null
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
            val completed = mutableListOf<UploadedItemEntity>()
            val failedNames = mutableListOf<String>()

            try {
                files.forEachIndexed { index, payload ->
                    _uploadState.value = UploadUiState.Preparing(payload.fileName)
                    try {
                        val uploaded = repository.uploadAndRecord(
                            payload = payload,
                            retention = retention,
                            overrideProviderId = overrideProvider,
                            onSelecting = {
                                _uploadState.value = UploadUiState.SelectingServer(
                                    file = payload,
                                    currentIndex = index + 1,
                                    totalFiles = files.size
                                )
                            },
                            onProgress = { progress ->
                                _uploadState.value = UploadUiState.Uploading(
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
                            _uploadState.value = UploadUiState.Error(
                                message = e.message ?: "Đã xảy ra lỗi khi tải tệp lên đám mây.",
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
                    _uploadState.value = UploadUiState.Success(
                        results = completed,
                        failedNames = failedNames
                    )
                } else {
                    _uploadState.value = UploadUiState.Error(
                        message = "Không thể tải lên các tệp đã chọn. Vui lòng kiểm tra mạng hoặc đổi máy chủ.",
                        originalFiles = files
                    )
                }
            } catch (_: CancellationException) {
                _uploadState.value = UploadUiState.Idle
            }
        }
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
