package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CloudAppPreferences
import com.example.data.model.CloudProviderId
import com.example.data.model.RetentionPreference
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cloudPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clouddrop_settings"
)

class CloudPreferencesRepository(private val context: Context) {

    private object Keys {
        val AUTO_UPLOAD_ON_SHARE = booleanPreferencesKey("auto_upload_on_share")
        val SHARE_POPUP_MODE = booleanPreferencesKey("share_popup_mode")
        val DEFAULT_RETENTION = stringPreferencesKey("default_retention")
        val PREFERRED_PROVIDER = stringPreferencesKey("preferred_provider")
        val DISABLED_PROVIDERS = stringSetPreferencesKey("disabled_providers")
        val IMGBB_API_KEY = stringPreferencesKey("imgbb_api_key")
        val GOFILE_TOKEN = stringPreferencesKey("gofile_token")
        val CATBOX_USER_HASH = stringPreferencesKey("catbox_user_hash")
        val CUSTOM_ENDPOINT_URL = stringPreferencesKey("custom_endpoint_url")
        val CUSTOM_FIELD_NAME = stringPreferencesKey("custom_field_name")
        val CUSTOM_AUTH_HEADER = stringPreferencesKey("custom_auth_header")
        val CUSTOM_JSON_URL_PATH = stringPreferencesKey("custom_json_url_path")
    }

    val preferencesFlow: Flow<CloudAppPreferences> = context.cloudPrefsDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            CloudAppPreferences(
                autoUploadOnShare = prefs[Keys.AUTO_UPLOAD_ON_SHARE] ?: true,
                sharePopupMode = prefs[Keys.SHARE_POPUP_MODE] ?: true,
                defaultRetention = RetentionPreference.fromId(prefs[Keys.DEFAULT_RETENTION]),
                preferredProviderId = CloudProviderId.fromId(prefs[Keys.PREFERRED_PROVIDER]),
                disabledProviders = prefs[Keys.DISABLED_PROVIDERS] ?: emptySet(),
                imgbbApiKey = prefs[Keys.IMGBB_API_KEY] ?: "",
                gofileToken = prefs[Keys.GOFILE_TOKEN] ?: "",
                catboxUserHash = prefs[Keys.CATBOX_USER_HASH] ?: "",
                customEndpointUrl = prefs[Keys.CUSTOM_ENDPOINT_URL] ?: "",
                customFieldName = prefs[Keys.CUSTOM_FIELD_NAME]?.ifBlank { "file" } ?: "file",
                customAuthHeader = prefs[Keys.CUSTOM_AUTH_HEADER] ?: "",
                customJsonUrlPath = prefs[Keys.CUSTOM_JSON_URL_PATH] ?: "url"
            )
        }

    suspend fun currentPreferences(): CloudAppPreferences = preferencesFlow.first()

    suspend fun setAutoUploadOnShare(enabled: Boolean) {
        context.cloudPrefsDataStore.edit { it[Keys.AUTO_UPLOAD_ON_SHARE] = enabled }
    }

    suspend fun setSharePopupMode(enabled: Boolean) {
        context.cloudPrefsDataStore.edit { it[Keys.SHARE_POPUP_MODE] = enabled }
    }

    suspend fun setDefaultRetention(retention: RetentionPreference) {
        context.cloudPrefsDataStore.edit { it[Keys.DEFAULT_RETENTION] = retention.id }
    }

    suspend fun setPreferredProvider(providerId: CloudProviderId?) {
        context.cloudPrefsDataStore.edit { prefs ->
            if (providerId == null) {
                prefs.remove(Keys.PREFERRED_PROVIDER)
            } else {
                prefs[Keys.PREFERRED_PROVIDER] = providerId.id
            }
        }
    }

    suspend fun setProviderEnabled(providerId: CloudProviderId, enabled: Boolean) {
        context.cloudPrefsDataStore.edit { prefs ->
            val current = (prefs[Keys.DISABLED_PROVIDERS] ?: emptySet()).toMutableSet()
            if (enabled) {
                current.remove(providerId.id)
            } else {
                current.add(providerId.id)
            }
            prefs[Keys.DISABLED_PROVIDERS] = current
        }
    }

    suspend fun updateProviderKey(providerId: CloudProviderId, token: String) {
        val clean = token.trim()
        context.cloudPrefsDataStore.edit { prefs ->
            when (providerId) {
                CloudProviderId.IMGBB -> prefs[Keys.IMGBB_API_KEY] = clean
                CloudProviderId.GOFILE -> prefs[Keys.GOFILE_TOKEN] = clean
                CloudProviderId.CATBOX -> prefs[Keys.CATBOX_USER_HASH] = clean
                else -> Unit
            }
        }
    }

    suspend fun updateCustomServerConfig(
        endpointUrl: String,
        fieldName: String,
        authHeader: String,
        jsonUrlPath: String
    ) {
        context.cloudPrefsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_ENDPOINT_URL] = endpointUrl.trim()
            prefs[Keys.CUSTOM_FIELD_NAME] = fieldName.trim().ifBlank { "file" }
            prefs[Keys.CUSTOM_AUTH_HEADER] = authHeader.trim()
            prefs[Keys.CUSTOM_JSON_URL_PATH] = jsonUrlPath.trim()
        }
    }
}
