package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ReleaseUpdateInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkName: String?,
    val apkDownloadUrl: String?,
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

    data class Error(
        val message: String,
        val isManual: Boolean
    ) : AppUpdateState
}

class AppUpdateManager {

    private val apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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
                val matchesBrand = name.contains("InCloud", ignoreCase = true) ||
                    name.contains("CloudDrop", ignoreCase = true)
                if (bestAsset == null || matchesBrand) {
                    bestAsset = asset
                }
            }
        }

        val htmlUrl = releaseObj.optString("html_url").trim()
            .ifBlank { "https://github.com/$cleanRepo/releases/latest" }
        val apkDownloadUrl = bestAsset?.optString("browser_download_url")
            ?.trim()
            ?.takeIf { it.startsWith("https://", ignoreCase = true) }

        ReleaseUpdateInfo(
            tagName = tagName,
            releaseTitle = releaseObj.optString("name").trim().ifBlank { "InCloud $tagName" },
            releaseNotes = releaseObj.optString("body").trim(),
            apkName = bestAsset?.optString("name")?.trim(),
            apkDownloadUrl = apkDownloadUrl,
            apkSizeBytes = bestAsset?.optLong("size", 0L) ?: 0L,
            htmlUrl = htmlUrl
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

    /**
     * Google Play Policy compliant update handoff:
     * Opens the official GitHub Release URL in the user's browser via standard Intent.ACTION_VIEW
     * without requiring REQUEST_INSTALL_PACKAGES or downloading executables inside the app.
     */
    fun openReleaseInBrowser(context: Context, release: ReleaseUpdateInfo) {
        val targetUrl = release.apkDownloadUrl ?: release.htmlUrl
        if (!targetUrl.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(context, "Liên kết phát hành không hợp lệ.", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "Không tìm thấy trình duyệt để mở trang cập nhật.", Toast.LENGTH_SHORT).show()
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
        private const val USER_AGENT = "InCloudAndroid/1.0"
    }
}
