package com.example.data

import com.example.cloud.CloudUploadEngine
import com.example.cloud.UploadProgressUpdate
import com.example.data.local.CloudPreferencesRepository
import com.example.data.local.UploadHistoryDao
import com.example.data.model.CloudAppPreferences
import com.example.data.model.CloudProviderId
import com.example.data.model.ProviderHealth
import com.example.data.model.RetentionPreference
import com.example.data.model.SharedFilePayload
import com.example.data.model.UploadedItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CloudDropRepository(
    private val historyDao: UploadHistoryDao,
    val preferencesRepository: CloudPreferencesRepository,
    private val uploadEngine: CloudUploadEngine
) {
    val historyFlow: Flow<List<UploadedItemEntity>> = historyDao.observeAll()
    val preferencesFlow: Flow<CloudAppPreferences> = preferencesRepository.preferencesFlow

    private val _providerHealth = MutableStateFlow<Map<CloudProviderId, ProviderHealth>>(emptyMap())
    val providerHealth: StateFlow<Map<CloudProviderId, ProviderHealth>> = _providerHealth.asStateFlow()

    suspend fun probeAllProviders(): Map<CloudProviderId, ProviderHealth> {
        val prefs = preferencesRepository.currentPreferences()
        val result = uploadEngine.probeAllProviders(prefs)
        _providerHealth.value = result
        return result
    }

    suspend fun uploadGroupAndRecord(
        payloads: List<SharedFilePayload>,
        retention: RetentionPreference,
        overrideProviderId: CloudProviderId?,
        onSelecting: () -> Unit,
        onProgress: (UploadProgressUpdate) -> Unit
    ): UploadedItemEntity {
        val prefs = preferencesRepository.currentPreferences()
        val uploaded = uploadEngine.uploadGroupAsSingleLink(
            payloads = payloads,
            retention = retention,
            overrideProviderId = overrideProviderId,
            prefs = prefs,
            cachedHealth = _providerHealth.value,
            onHealthUpdated = { updated -> _providerHealth.value = updated },
            onSelecting = onSelecting,
            onProgress = onProgress
        )
        val newId = historyDao.insert(uploaded)
        return uploaded.copy(id = newId)
    }

    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteById(id)

    suspend fun clearAllHistory() = historyDao.clearAll()
}
