package com.example.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.example.data.model.CloudAppPreferences
import com.example.data.model.CloudProviderCatalog
import com.example.data.model.CloudProviderId
import com.example.data.model.CloudProviderSpec
import com.example.data.model.ProviderHealth
import com.example.data.model.RetentionPreference
import com.example.data.model.SharedFilePayload
import com.example.data.model.UploadedItemEntity
import com.example.util.FileMetadataResolver
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UploadProgressUpdate(
    val provider: CloudProviderSpec,
    val providerLatencyMs: Long?,
    val bytesUploaded: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val isFailover: Boolean
)

class CloudUploadEngine(private val context: Context) {

    private val probeClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val uploadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun probeProvider(
        spec: CloudProviderSpec,
        prefs: CloudAppPreferences
    ): ProviderHealth = withContext(Dispatchers.IO) {
        val targetUrl = when (spec.providerId) {
            CloudProviderId.CUSTOM -> prefs.customEndpointUrl.trim()
            else -> spec.probeUrl
        }
        if (targetUrl.isBlank() || !targetUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext ProviderHealth(
                providerId = spec.providerId,
                isReachable = null,
                latencyMs = null,
                checkedAtMillis = System.currentTimeMillis(),
                statusNote = "Chưa cấu hình URL HTTPS"
            )
        }

        val startMs = SystemClock.elapsedRealtime()
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", USER_AGENT)

        val request = if (spec.providerId == CloudProviderId.GOFILE) {
            requestBuilder.get().build()
        } else {
            requestBuilder.head().build()
        }

        try {
            probeClient.newCall(request).await().use { response ->
                val elapsed = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                // Treat any HTTP response < 500 (including 405 Method Not Allowed on HEAD) as reachable
                val reachable = response.code in 200..499
                ProviderHealth(
                    providerId = spec.providerId,
                    isReachable = reachable,
                    latencyMs = if (reachable) elapsed else null,
                    checkedAtMillis = System.currentTimeMillis(),
                    statusNote = if (reachable) "HTTP ${response.code}" else "Lỗi máy chủ (${response.code})"
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            ProviderHealth(
                providerId = spec.providerId,
                isReachable = false,
                latencyMs = null,
                checkedAtMillis = System.currentTimeMillis(),
                statusNote = "Không phản hồi"
            )
        }
    }

    suspend fun probeAllProviders(prefs: CloudAppPreferences): Map<CloudProviderId, ProviderHealth> =
        coroutineScope {
            CloudProviderCatalog.allProviders.map { spec ->
                async {
                    if (spec.requiresApiKey && !prefs.isProviderConfigured(spec.providerId)) {
                        spec.providerId to ProviderHealth(
                            providerId = spec.providerId,
                            isReachable = null,
                            latencyMs = null,
                            checkedAtMillis = System.currentTimeMillis(),
                            statusNote = "Chưa liên kết"
                        )
                    } else {
                        spec.providerId to probeProvider(spec, prefs)
                    }
                }
            }.awaitAll().toMap()
        }

    suspend fun uploadFileWithSmartSelection(
        payload: SharedFilePayload,
        retention: RetentionPreference,
        overrideProviderId: CloudProviderId?,
        prefs: CloudAppPreferences,
        cachedHealth: Map<CloudProviderId, ProviderHealth>,
        onHealthUpdated: (Map<CloudProviderId, ProviderHealth>) -> Unit,
        onSelecting: () -> Unit,
        onProgress: (UploadProgressUpdate) -> Unit
    ): UploadedItemEntity = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            throw IOException("Không có kết nối mạng. Vui lòng kiểm tra Wi-Fi hoặc 4G/5G.")
        }

        val stagedFile = FileMetadataResolver.stageToCacheFile(context, payload)
        val actualSize = stagedFile.length()
        val effectivePayload = payload.copy(sizeBytes = actualSize)

        try {
            onSelecting()

            val compatible = CloudProviderCatalog.allProviders.filter { spec ->
                isCompatible(spec, effectivePayload, prefs)
            }
            if (compatible.isEmpty()) {
                throw IllegalStateException(
                    "Không có máy chủ nào đang bật hỗ trợ tệp này (${FileMetadataResolver.formatFileSize(actualSize)})."
                )
            }

            // Probe stale (>60s) or unmeasured compatible servers concurrently to get real latency
            val now = System.currentTimeMillis()
            val mergedHealth = cachedHealth.toMutableMap()
            val toProbe = compatible.filter { spec ->
                val h = mergedHealth[spec.providerId]
                h?.checkedAtMillis == null || (now - h.checkedAtMillis) > PROBE_TTL_MS
            }
            if (toProbe.isNotEmpty()) {
                val fresh = coroutineScope {
                    toProbe.map { spec ->
                        async { spec.providerId to probeProvider(spec, prefs) }
                    }.awaitAll().toMap()
                }
                mergedHealth.putAll(fresh)
                onHealthUpdated(mergedHealth.toMap())
            }

            val ranked = rankProviders(
                candidates = compatible,
                payload = effectivePayload,
                retention = retention,
                preferredId = overrideProviderId ?: prefs.preferredProviderId,
                prefs = prefs,
                healthMap = mergedHealth
            )

            val errors = mutableListOf<String>()
            ranked.forEachIndexed { index, spec ->
                val health = mergedHealth[spec.providerId]
                val isFailover = index > 0
                try {
                    val resultUrl = uploadToSpecificProvider(
                        spec = spec,
                        file = stagedFile,
                        payload = effectivePayload,
                        retention = retention,
                        prefs = prefs
                    ) { written, total, speed ->
                        onProgress(
                            UploadProgressUpdate(
                                provider = spec,
                                providerLatencyMs = health?.latencyMs,
                                bytesUploaded = written,
                                totalBytes = total,
                                bytesPerSecond = speed,
                                isFailover = isFailover
                            )
                        )
                    }

                    return@withContext UploadedItemEntity(
                        fileName = effectivePayload.fileName,
                        mimeType = effectivePayload.mimeType,
                        sizeBytes = actualSize,
                        downloadUrl = resultUrl,
                        providerId = spec.providerId.id,
                        providerName = spec.displayName,
                        retentionLabel = resolveRetentionLabel(spec.providerId, retention),
                        measuredLatencyMs = health?.latencyMs,
                        createdAtMillis = System.currentTimeMillis(),
                        localPreviewUri = effectivePayload.uri?.toString()
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    errors.add("${spec.displayName}: ${e.message ?: "Lỗi kết nối"}")
                }
            }

            throw IOException(
                "Đã thử ${ranked.size} máy chủ nhưng không thể tải lên.\n${errors.take(2).joinToString("\n")}"
            )
        } finally {
            FileMetadataResolver.cleanupStagedFile(stagedFile)
        }
    }

    private fun isCompatible(
        spec: CloudProviderSpec,
        payload: SharedFilePayload,
        prefs: CloudAppPreferences
    ): Boolean {
        if (!prefs.isProviderEnabled(spec.providerId)) return false
        if (spec.requiresApiKey && !prefs.isProviderConfigured(spec.providerId)) return false
        if (spec.supportsImagesOnly && !payload.isImage) return false
        if (payload.sizeBytes > spec.maxFileSizeBytes) return false
        return true
    }

    private fun rankProviders(
        candidates: List<CloudProviderSpec>,
        payload: SharedFilePayload,
        retention: RetentionPreference,
        preferredId: CloudProviderId?,
        prefs: CloudAppPreferences,
        healthMap: Map<CloudProviderId, ProviderHealth>
    ): List<CloudProviderSpec> {
        return candidates.sortedBy { spec ->
            var score = 0L

            // 1. Explicit user preference always gets top priority
            if (preferredId != null && spec.providerId == preferredId) {
                score -= 1_000_000L
            }

            // 2. Reachability from real probe
            val health = healthMap[spec.providerId]
            when (health?.isReachable) {
                true -> score -= 100_000L
                null -> score -= 20_000L
                false -> score += 200_000L
            }

            // 3. Configured user custom server / ImgBB for images gets bonus
            if (spec.providerId == CloudProviderId.CUSTOM && prefs.isProviderConfigured(CloudProviderId.CUSTOM)) {
                score -= 40_000L
            }
            if (spec.providerId == CloudProviderId.IMGBB && payload.isImage && prefs.isProviderConfigured(CloudProviderId.IMGBB)) {
                score -= 35_000L
            }

            // 4. Retention match
            val wantsPermanent = retention == RetentionPreference.PERMANENT
            if (wantsPermanent && spec.supportsPermanent) {
                score -= 30_000L
            } else if (!wantsPermanent && spec.supportsTemporary) {
                score -= 25_000L
            }

            // 5. File size / type specialization
            if (payload.sizeBytes > 180L * 1024L * 1024L &&
                (spec.providerId == CloudProviderId.GOFILE || spec.providerId == CloudProviderId.LITTERBOX)
            ) {
                score -= 20_000L
            }

            // 6. Real measured latency in milliseconds (tie-breaker)
            score += (health?.latencyMs ?: 1500L)
            score
        }
    }

    private suspend fun uploadToSpecificProvider(
        spec: CloudProviderSpec,
        file: File,
        payload: SharedFilePayload,
        retention: RetentionPreference,
        prefs: CloudAppPreferences,
        onProgress: (Long, Long, Long) -> Unit
    ): String {
        val mediaType = payload.mimeType.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
        val progressBody = ProgressRequestBody(file, mediaType, onProgress)

        return when (spec.providerId) {
            CloudProviderId.LITTERBOX -> uploadLitterbox(payload.fileName, progressBody, retention)
            CloudProviderId.CATBOX -> uploadCatbox(payload.fileName, progressBody, prefs.catboxUserHash)
            CloudProviderId.TMPFILES -> uploadTmpFiles(payload.fileName, progressBody)
            CloudProviderId.GOFILE -> uploadGoFile(payload.fileName, progressBody, prefs.gofileToken)
            CloudProviderId.ZEROX0 -> uploadZeroX0(payload.fileName, progressBody)
            CloudProviderId.IMGBB -> uploadImgBB(payload.fileName, progressBody, retention, prefs.imgbbApiKey)
            CloudProviderId.CUSTOM -> uploadCustom(payload.fileName, progressBody, prefs)
        }
    }

    private suspend fun uploadLitterbox(
        fileName: String,
        body: ProgressRequestBody,
        retention: RetentionPreference
    ): String {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("time", retention.litterboxParam)
            .addFormDataPart("fileToUpload", fileName, body)
            .build()

        val request = Request.Builder()
            .url("https://litterbox.catbox.moe/resources/internals/api.php")
            .header("User-Agent", USER_AGENT)
            .post(multipart)
            .build()

        return executeAndParsePlainUrl(request, "Litterbox")
    }

    private suspend fun uploadCatbox(
        fileName: String,
        body: ProgressRequestBody,
        userHash: String
    ): String {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("userhash", userHash.trim())
            .addFormDataPart("fileToUpload", fileName, body)
            .build()

        val request = Request.Builder()
            .url("https://catbox.moe/user/api.php")
            .header("User-Agent", USER_AGENT)
            .post(multipart)
            .build()

        return executeAndParsePlainUrl(request, "Catbox")
    }

    private suspend fun uploadTmpFiles(
        fileName: String,
        body: ProgressRequestBody
    ): String {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, body)
            .build()

        val request = Request.Builder()
            .url("https://tmpfiles.org/api/v1/upload")
            .header("User-Agent", USER_AGENT)
            .post(multipart)
            .build()

        uploadClient.newCall(request).await().use { response ->
            val raw = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val json = JSONObject(raw)
            val url = json.optJSONObject("data")?.optString("url").orEmpty().trim()
            if (!url.startsWith("http")) {
                throw IOException("Phản hồi TmpFiles không chứa liên kết hợp lệ.")
            }
            return url
        }
    }

    private suspend fun uploadGoFile(
        fileName: String,
        body: ProgressRequestBody,
        token: String
    ): String {
        // Step 1: Discover active optimal GoFile server
        val serverRequest = Request.Builder()
            .url("https://api.gofile.io/servers")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val serverName = probeClient.newCall(serverRequest).await().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Không lấy được nút máy chủ GoFile (${response.code})")
            val servers = JSONObject(raw).optJSONObject("data")?.optJSONArray("servers")
            if (servers == null || servers.length() == 0) {
                throw IOException("Không có máy chủ GoFile khả dụng.")
            }
            servers.getJSONObject(0).getString("name")
        }

        // Step 2: Upload file to discovered server
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, body)
            .build()

        val reqBuilder = Request.Builder()
            .url("https://$serverName.gofile.io/contents/uploadfile")
            .header("User-Agent", USER_AGENT)
            .post(multipart)

        if (token.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${token.trim()}")
        }

        uploadClient.newCall(reqBuilder.build()).await().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("GoFile HTTP ${response.code}")
            val json = JSONObject(raw)
            val downloadPage = json.optJSONObject("data")?.optString("downloadPage").orEmpty().trim()
            if (!downloadPage.startsWith("http")) {
                throw IOException("GoFile không trả về liên kết tải.")
            }
            return downloadPage
        }
    }

    private suspend fun uploadZeroX0(
        fileName: String,
        body: ProgressRequestBody
    ): String {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, body)
            .build()

        val request = Request.Builder()
            .url("https://0x0.st")
            .header("User-Agent", USER_AGENT)
            .post(multipart)
            .build()

        return executeAndParsePlainUrl(request, "0x0.st")
    }

    private suspend fun uploadImgBB(
        fileName: String,
        body: ProgressRequestBody,
        retention: RetentionPreference,
        apiKey: String
    ): String {
        if (apiKey.isBlank()) throw IllegalStateException("Chưa nhập API Key ImgBB.")
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("key", apiKey.trim())
            .addFormDataPart("image", fileName, body)

        retention.imgbbSeconds?.let { seconds ->
            multipartBuilder.addFormDataPart("expiration", seconds.toString())
        }

        val request = Request.Builder()
            .url("https://api.imgbb.com/1/upload")
            .header("User-Agent", USER_AGENT)
            .post(multipartBuilder.build())
            .build()

        uploadClient.newCall(request).await().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("ImgBB HTTP ${response.code}")
            val data = JSONObject(raw).optJSONObject("data")
            val url = data?.optString("url")?.ifBlank { data.optString("display_url") }.orEmpty().trim()
            if (!url.startsWith("http")) {
                throw IOException("ImgBB không trả về liên kết ảnh hợp lệ.")
            }
            return url
        }
    }

    private suspend fun uploadCustom(
        fileName: String,
        body: ProgressRequestBody,
        prefs: CloudAppPreferences
    ): String {
        val endpoint = prefs.customEndpointUrl.trim()
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            throw IllegalStateException("Máy chủ tùy chỉnh phải sử dụng giao thức HTTPS.")
        }
        val fieldName = prefs.customFieldName.ifBlank { "file" }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(fieldName, fileName, body)
            .build()

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("User-Agent", USER_AGENT)
            .post(multipart)

        if (prefs.customAuthHeader.isNotBlank()) {
            reqBuilder.header("Authorization", prefs.customAuthHeader.trim())
        }

        uploadClient.newCall(reqBuilder.build()).await().use { response ->
            val raw = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) throw IOException("Máy chủ riêng HTTP ${response.code}")

            val jsonPath = prefs.customJsonUrlPath.trim()
            val extracted = if (jsonPath.isNotEmpty() && raw.startsWith("{")) {
                extractJsonPath(JSONObject(raw), jsonPath)
            } else {
                raw.lineSequence().firstOrNull { it.trim().startsWith("http") }?.trim() ?: raw
            }

            if (!extracted.startsWith("http://") && !extracted.startsWith("https://")) {
                throw IOException("Không tìm thấy liên kết hợp lệ trong phản hồi máy chủ riêng.")
            }
            return extracted
        }
    }

    private suspend fun executeAndParsePlainUrl(request: Request, label: String): String {
        uploadClient.newCall(request).await().use { response ->
            val raw = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                throw IOException("$label HTTP ${response.code}")
            }
            val url = raw.lineSequence().firstOrNull { it.trim().startsWith("http") }?.trim()
                ?: throw IOException("$label không trả về liên kết hợp lệ.")
            return url
        }
    }

    private fun extractJsonPath(root: JSONObject, dotPath: String): String {
        val parts = dotPath.split('.').filter { it.isNotBlank() }
        var current: Any? = root
        for (part in parts) {
            current = when (current) {
                is JSONObject -> current.opt(part)
                else -> null
            }
        }
        return current?.toString()?.trim().orEmpty()
    }

    private fun resolveRetentionLabel(
        providerId: CloudProviderId,
        requested: RetentionPreference
    ): String = when (providerId) {
        CloudProviderId.LITTERBOX -> "Tự xóa sau ${requested.litterboxParam}"
        CloudProviderId.TMPFILES -> "Tự xóa sau 60 phút"
        CloudProviderId.CATBOX -> "Lưu lâu dài"
        CloudProviderId.ZEROX0 -> "Lưu tối thiểu 30 ngày"
        CloudProviderId.GOFILE -> "Lưu theo chính sách GoFile"
        CloudProviderId.IMGBB -> requested.imgbbSeconds?.let { "Tự xóa sau ${requested.labelVi}" } ?: "Lưu lâu dài"
        CloudProviderId.CUSTOM -> "Máy chủ riêng"
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) {
                    cont.resume(response)
                } else {
                    response.close()
                }
            }
        })
    }

    companion object {
        private const val USER_AGENT = "CloudDropAndroid/1.0"
        private const val PROBE_TTL_MS = 60_000L
    }
}
