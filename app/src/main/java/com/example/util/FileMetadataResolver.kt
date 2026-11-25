package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.IntentCompat
import com.example.data.model.SharedFilePayload
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.ln
import kotlin.math.pow

object FileMetadataResolver {

    fun extractPayloadsFromIntent(context: Context, intent: Intent?): List<SharedFilePayload> {
        if (intent == null) return emptyList()
        val action = intent.action ?: return emptyList()

        return when (action) {
            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
                if (streamUri != null) {
                    listOf(resolveUri(context, streamUri, intent.type))
                } else {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
                    if (!sharedText.isNullOrEmpty()) {
                        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?: "van-ban-chia-se"
                        listOf(createTextPayload(subject, sharedText))
                    } else {
                        emptyList()
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                ).orEmpty()
                uris.map { uri -> resolveUri(context, uri, intent.type) }
            }

            else -> emptyList()
        }
    }

    fun resolveUri(context: Context, uri: Uri, fallbackMime: String? = null): SharedFilePayload {
        val resolver = context.contentResolver
        var displayName: String? = null
        var sizeBytes = -1L

        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }

        val resolvedMime = resolver.getType(uri)
            ?.takeIf { it.isNotBlank() && it != "*/*" }
            ?: fallbackMime?.takeIf { it.isNotBlank() && it != "*/*" }
            ?: guessMimeFromName(displayName)
            ?: "application/octet-stream"

        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedMime)
        val safeName = sanitizeFileName(
            displayName?.takeIf { it.isNotBlank() }
                ?: "clouddrop_${System.currentTimeMillis()}${if (ext != null) ".$ext" else ""}"
        )

        val finalSize = if (sizeBytes > 0L) {
            sizeBytes
        } else {
            runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L).coerceAtLeast(0L)
        }

        return SharedFilePayload(
            id = UUID.randomUUID().toString(),
            uri = uri,
            fileName = safeName,
            mimeType = resolvedMime,
            sizeBytes = finalSize
        )
    }

    fun createTextPayload(rawName: String, textContent: String): SharedFilePayload {
        val cleanBase = sanitizeFileName(rawName.ifBlank { "ghi-chu" })
        val fileName = if (cleanBase.endsWith(".txt", ignoreCase = true)) {
            cleanBase
        } else {
            "$cleanBase.txt"
        }
        val bytes = textContent.toByteArray(Charsets.UTF_8)
        return SharedFilePayload(
            id = UUID.randomUUID().toString(),
            uri = null,
            fileName = fileName,
            mimeType = "text/plain",
            sizeBytes = bytes.size.toLong(),
            textContent = textContent
        )
    }

    /**
     * Stages the incoming Uri or text into a temporary cache file so upload retries /
     * server failovers can stream cleanly without losing temporary URI permissions.
     */
    fun stageToCacheFile(context: Context, payload: SharedFilePayload): File {
        val stagingDir = File(context.cacheDir, "upload_staging").apply { mkdirs() }
        val safeName = sanitizeFileName(payload.fileName)
        val target = File(stagingDir, "${System.currentTimeMillis()}_$safeName")

        if (payload.textContent != null) {
            target.writeText(payload.textContent, Charsets.UTF_8)
            return target
        }

        val uri = payload.uri ?: throw IllegalArgumentException("Không tìm thấy đường dẫn tệp.")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Không thể đọc nội dung tệp từ hệ thống.")

        inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        return target
    }

    fun cleanupStagedFile(file: File?) {
        if (file != null && file.exists()) {
            runCatching { file.delete() }
        }
    }

    private fun guessMimeFromName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.lastIndex) return null
        val ext = name.substring(dot + 1).lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    fun sanitizeFileName(name: String): String {
        val trimmed = name.trim().replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
        return trimmed.ifBlank { "tep_${System.currentTimeMillis()}" }.take(120)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Chưa rõ dung lượng"
        val unit = 1024.0
        if (bytes < unit) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(unit)).toInt().coerceAtMost(4)
        val prefix = "KMGT"[exp - 1]
        val value = bytes / unit.pow(exp.toDouble())
        return String.format(Locale.US, "%.1f %sB", value, prefix)
    }

    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0L) return ""
        return "${formatFileSize(bytesPerSecond)}/s"
    }

    fun formatTimestamp(millis: Long): String {
        val formatter = SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale("vi", "VN"))
        return formatter.format(Date(millis))
    }
}
