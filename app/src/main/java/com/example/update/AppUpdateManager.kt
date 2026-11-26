package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReleaseUpdateInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data class Checking(val isManual: Boolean) : AppUpdateState
    data class UpToDate(
        val latestTag: String?,
        val currentVersionLabel: String,
        val isManual: Boolean
    ) : AppUpdateState

    data class UpdateAvailable(
        val release: ReleaseUpdateInfo,
        val showDialog: Boolean = true
    ) : AppUpdateState

    data class Downloading(
        val release: ReleaseUpdateInfo,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long
    ) : AppUpdateState {
        val progress: Float?
            get() = if (totalBytes > 0L) {
                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class ReadyToInstall(
        val release: ReleaseUpdateInfo,
        val apkFile: File,
        val needsInstallPermission: Boolean = false
    ) : AppUpdateState

    data class Error(
        val message: String,
        val isManual: Boolean,
        val release: ReleaseUpdateInfo? = null
    ) : AppUpdateState
}

sealed interface InstallLaunchResult {
    data object Launched : InstallLaunchResult
    data class NeedsPermission(val settingsIntent: Intent) : InstallLaunchResult
    data class Failed(val message: String) : InstallLaunchResult
}

class AppUpdateManager(private val context: Context) {

    private val apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun currentVersionDisplay(): String {
        val buildTag = BuildConfig.APP_BUILD_TAG.trim()
        return if (buildTag.isNotEmpty()) {
            buildTag
        } else if (BuildConfig.APP_BUILD_NUMBER > 0) {
            "v${BuildConfig.VERSION_NAME}-b${BuildConfig.APP_BUILD_NUMBER}"
        } else {
            "v${BuildConfig.VERSION_NAME}"
        }
    }

    fun resolveRepoSlug(overrideRepo: String): String {
        val raw = overrideRepo.trim().ifBlank { BuildConfig.GITHUB_UPDATE_REPO.trim() }
        return sanitizeRepoSlug(raw)
    }

    fun sanitizeRepoSlug(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else ""
    }

    suspend fun checkLatestRelease(repoSlug: String): ReleaseUpdateInfo? = withContext(Dispatchers.IO) {
        val cleanRepo = sanitizeRepoSlug(repoSlug)
        if (cleanRepo.isEmpty()) {
            throw IllegalStateException("MISSING_REPO")
        }

        // Try /releases/latest first; fallback to /releases?per_page=5 if only prereleases exist
        val latestJson = fetchJson("https://api.github.com/repos/$cleanRepo/releases/latest")
        val releaseObj = if (latestJson != null) {
            JSONObject(latestJson)
        } else {
            val listRaw = fetchJson("https://api.github.com/repos/$cleanRepo/releases?per_page=5")
                ?: throw IOException("Không thể kết nối GitHub Releases của $cleanRepo.")
            val arr = JSONArray(listRaw)
            var found: JSONObject? = null
            for (i in 0 until arr.length()) {
                val candidate = arr.getJSONObject(i)
                if (!candidate.optBoolean("draft", false)) {
                    found = candidate
                    break
                }
            }
            found
        } ?: return@withContext null

        val tagName = releaseObj.optString("tag_name").trim()
        if (tagName.isEmpty()) return@withContext null

        val assets = releaseObj.optJSONArray("assets") ?: JSONArray()
        var bestAsset: JSONObject? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                if (bestAsset == null || name.contains("CloudDrop", ignoreCase = true)) {
                    bestAsset = asset
                }
            }
        }

        val apkAsset = bestAsset ?: return@withContext null
        val downloadUrl = apkAsset.optString("browser_download_url").trim()
        if (!downloadUrl.startsWith("https://", ignoreCase = true)) return@withContext null

        ReleaseUpdateInfo(
            tagName = tagName,
            releaseTitle = releaseObj.optString("name").trim().ifBlank { "CloudDrop $tagName" },
            releaseNotes = releaseObj.optString("body").trim(),
            apkName = apkAsset.optString("name").trim().ifBlank { "CloudDrop-$tagName.apk" },
            apkDownloadUrl = downloadUrl,
            apkSizeBytes = apkAsset.optLong("size", 0L),
            htmlUrl = releaseObj.optString("html_url").trim()
        )
    }

    fun isRemoteNewer(remoteTag: String): Boolean {
        val installedTag = BuildConfig.APP_BUILD_TAG.trim()
        if (installedTag.isNotEmpty() && installedTag.equals(remoteTag.trim(), ignoreCase = true)) {
            return false
        }

        val remote = parseVersionTuple(remoteTag)
        val installedBase = if (installedTag.isNotEmpty()) {
            parseVersionTuple(installedTag)
        } else {
            val base = parseVersionTuple(BuildConfig.VERSION_NAME)
            base.copy(buildNumber = BuildConfig.APP_BUILD_NUMBER)
        }

        if (remote.major != installedBase.major) return remote.major > installedBase.major
        if (remote.minor != installedBase.minor) return remote.minor > installedBase.minor
        if (remote.patch != installedBase.patch) return remote.patch > installedBase.patch
        if (remote.buildNumber != installedBase.buildNumber) {
            return remote.buildNumber > installedBase.buildNumber
        }
        return false
    }

    suspend fun downloadApk(
        release: ReleaseUpdateInfo,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, bytesPerSecond: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "apk_updates").apply { mkdirs() }
        // Clean up any stale APKs
        updatesDir.listFiles()?.forEach { old -> runCatching { old.delete() } }

        val partFile = File(updatesDir, "clouddrop_update.apk.part")
        val finalFile = File(updatesDir, "clouddrop_update.apk")

        val request = Request.Builder()
            .url(release.apkDownloadUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/octet-stream")
            .get()
            .build()

        try {
            downloadClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Tải APK thất bại (HTTP ${response.code}).")
                }
                val body = response.body ?: throw IOException("Máy chủ không trả về dữ liệu APK.")
                val totalBytes = body.contentLength().takeIf { it > 0L } ?: release.apkSizeBytes

                val startMs = SystemClock.elapsedRealtime()
                var lastEmitMs = 0L
                var downloaded = 0L
                val buffer = ByteArray(32 * 1024)

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val nowMs = SystemClock.elapsedRealtime()
                            if (nowMs - lastEmitMs >= 100L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                val elapsedSec = ((nowMs - startMs).coerceAtLeast(1L)) / 1000.0
                                val speed = (downloaded / elapsedSec).toLong()
                                onProgress(downloaded, totalBytes, speed)
                                lastEmitMs = nowMs
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }
            if (!finalFile.exists() || finalFile.length() == 0L) {
                throw IOException("Tệp APK tải về không hợp lệ.")
            }
            finalFile
        } catch (ce: CancellationException) {
            runCatching { partFile.delete() }
            throw ce
        } catch (e: Exception) {
            runCatching { partFile.delete() }
            throw e
        }
    }

    fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun launchApkInstaller(activityContext: Context, apkFile: File): InstallLaunchResult {
        if (!apkFile.exists()) {
            return InstallLaunchResult.Failed("Không tìm thấy tệp APK đã tải về.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activityContext.packageManager.canRequestPackageInstalls()
        ) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activityContext.packageName}")
            )
            return InstallLaunchResult.NeedsPermission(permIntent)
        }

        return runCatching {
            val contentUri = FileProvider.getUriForFile(
                activityContext,
                "${activityContext.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activityContext.startActivity(installIntent)
            InstallLaunchResult.Launched
        }.getOrElse { err ->
            InstallLaunchResult.Failed(err.message ?: "Không thể mở trình cài đặt Android.")
        }
    }

    private suspend fun fetchJson(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        apiClient.newCall(request).await().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) {
                throw IOException("GitHub API trả về mã lỗi ${response.code}.")
            }
            return response.body?.string()
        }
    }

    private data class VersionTuple(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val buildNumber: Int
    )

    private fun parseVersionTuple(raw: String): VersionTuple {
        val clean = raw.trim().lowercase().removePrefix("v")
        val buildMatch = Regex("(?:-b|build[._-]?)(\\d+)").find(clean)
        val buildNum = buildMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val mainPart = clean.substringBefore('-').substringBefore('+')
        val nums = mainPart.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        return VersionTuple(
            major = nums.getOrElse(0) { 0 },
            minor = nums.getOrElse(1) { 0 },
            patch = nums.getOrElse(2) { 0 },
            buildNumber = buildNum
        )
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

    companion object {
        private const val USER_AGENT = "CloudDropAndroid-Updater/1.0"
    }
}
