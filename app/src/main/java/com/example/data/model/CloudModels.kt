package com.example.data.model

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CloudProviderId(val id: String) {
    LITTERBOX("litterbox"),
    CATBOX("catbox"),
    TMPFILES("tmpfiles"),
    GOFILE("gofile"),
    ZEROX0("0x0st"),
    IMGBB("imgbb"),
    CUSTOM("custom");

    companion object {
        fun fromId(value: String?): CloudProviderId? = entries.firstOrNull { it.id == value }
    }
}

enum class RetentionPreference(
    val id: String,
    val labelVi: String,
    val litterboxParam: String,
    val imgbbSeconds: Int?
) {
    ONE_HOUR("1h", "1 giờ", "1h", 3600),
    TWELVE_HOURS("12h", "12 giờ", "12h", 43200),
    AUTO_24H("24h", "24 giờ", "24h", 86400),
    SEVENTY_TWO_HOURS("72h", "72 giờ", "72h", 259200),
    PERMANENT("permanent", "Lưu lâu dài", "72h", null);

    companion object {
        fun fromId(value: String?): RetentionPreference =
            entries.firstOrNull { it.id == value } ?: AUTO_24H
    }
}

data class CloudProviderSpec(
    val providerId: CloudProviderId,
    val displayName: String,
    val shortSummary: String,
    val maxFileSizeBytes: Long,
    val maxFileSizeLabel: String,
    val supportsImagesOnly: Boolean = false,
    val supportsTemporary: Boolean = true,
    val supportsPermanent: Boolean = false,
    val requiresApiKey: Boolean = false,
    val supportsOptionalKey: Boolean = false,
    val probeUrl: String
)

object CloudProviderCatalog {
    val allProviders: List<CloudProviderSpec> = listOf(
        CloudProviderSpec(
            providerId = CloudProviderId.LITTERBOX,
            displayName = "Litterbox Cloud",
            shortSummary = "Tạm thời 1h–72h, ổn định cho video & tệp lớn",
            maxFileSizeBytes = 1024L * 1024L * 1024L, // 1 GB
            maxFileSizeLabel = "1 GB",
            supportsTemporary = true,
            supportsPermanent = false,
            probeUrl = "https://litterbox.catbox.moe/"
        ),
        CloudProviderSpec(
            providerId = CloudProviderId.CATBOX,
            displayName = "Catbox Cloud",
            shortSummary = "Lưu trữ trực tiếp lâu dài, tải nhanh",
            maxFileSizeBytes = 200L * 1024L * 1024L, // 200 MB
            maxFileSizeLabel = "200 MB",
            supportsTemporary = false,
            supportsPermanent = true,
            supportsOptionalKey = true,
            probeUrl = "https://catbox.moe/"
        ),
        CloudProviderSpec(
            providerId = CloudProviderId.GOFILE,
            displayName = "GoFile Cloud",
            shortSummary = "Tự động chọn cụm máy chủ gần nhất cho tệp & video lớn",
            maxFileSizeBytes = 5L * 1024L * 1024L * 1024L, // 5 GB
            maxFileSizeLabel = "5 GB+",
            supportsTemporary = true,
            supportsPermanent = true,
            supportsOptionalKey = true,
            probeUrl = "https://api.gofile.io/servers"
        ),
        CloudProviderSpec(
            providerId = CloudProviderId.TMPFILES,
            displayName = "TmpFiles",
            shortSummary = "Tự động xóa sau 60 phút, gọn nhẹ cho chia sẻ nhanh",
            maxFileSizeBytes = 100L * 1024L * 1024L, // 100 MB
            maxFileSizeLabel = "100 MB",
            supportsTemporary = true,
            supportsPermanent = false,
            probeUrl = "https://tmpfiles.org/"
        ),
        CloudProviderSpec(
            providerId = CloudProviderId.ZEROX0,
            displayName = "0x0.st",
            shortSummary = "Liên kết siêu ngắn gọn, lưu tối thiểu 30 ngày",
            maxFileSizeBytes = 512L * 1024L * 1024L, // 512 MB
            maxFileSizeLabel = "512 MB",
            supportsTemporary = true,
            supportsPermanent = true,
            probeUrl = "https://0x0.st/"
        ),
        CloudProviderSpec(
            providerId = CloudProviderId.IMGBB,
            displayName = "ImgBB",
            shortSummary = "Chuyên dụng cho hình ảnh (cần API Key miễn phí)",
            maxFileSizeBytes = 32L * 1024L * 1024L, // 32 MB
            maxFileSizeLabel = "32 MB",
            supportsImagesOnly = true,
            supportsTemporary = true,
            supportsPermanent = true,
            requiresApiKey = true,
            probeUrl = "https://api.imgbb.com/"
        ),
        CloudProviderSpec(
            providerId = CloudProviderId.CUSTOM,
            displayName = "Máy chủ riêng (Custom)",
            shortSummary = "Liên kết máy chủ HTTPS / Webhook cá nhân",
            maxFileSizeBytes = 10L * 1024L * 1024L * 1024L, // 10 GB
            maxFileSizeLabel = "Tùy cấu hình",
            supportsTemporary = true,
            supportsPermanent = true,
            requiresApiKey = true,
            probeUrl = ""
        )
    )

    fun byId(providerId: CloudProviderId): CloudProviderSpec =
        allProviders.first { it.providerId == providerId }
}

data class ProviderHealth(
    val providerId: CloudProviderId,
    val isReachable: Boolean? = null,
    val latencyMs: Long? = null,
    val checkedAtMillis: Long? = null,
    val statusNote: String? = null
)

data class SharedFilePayload(
    val id: String,
    val uri: Uri? = null,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val textContent: String? = null
) {
    val isImage: Boolean get() = mimeType.startsWith("image/", ignoreCase = true)
    val isVideo: Boolean get() = mimeType.startsWith("video/", ignoreCase = true)
    val isAudio: Boolean get() = mimeType.startsWith("audio/", ignoreCase = true)
    val isText: Boolean get() = textContent != null || mimeType.startsWith("text/", ignoreCase = true)
}

sealed interface UploadUiState {
    data object Idle : UploadUiState
    data class Ready(val files: List<SharedFilePayload>) : UploadUiState
    data class Preparing(val fileName: String) : UploadUiState
    data class SelectingServer(
        val file: SharedFilePayload,
        val currentIndex: Int = 1,
        val totalFiles: Int = 1
    ) : UploadUiState

    data class Uploading(
        val file: SharedFilePayload,
        val providerName: String,
        val providerLatencyMs: Long?,
        val bytesUploaded: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
        val isFailover: Boolean = false,
        val currentIndex: Int = 1,
        val totalFiles: Int = 1
    ) : UploadUiState {
        val progress: Float
            get() = if (totalBytes > 0L) {
                (bytesUploaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
    }

    data class Success(
        val results: List<UploadedItemEntity>,
        val failedNames: List<String> = emptyList()
    ) : UploadUiState

    data class Error(
        val message: String,
        val failedFile: SharedFilePayload? = null,
        val originalFiles: List<SharedFilePayload> = emptyList()
    ) : UploadUiState
}

@Entity(tableName = "upload_history")
data class UploadedItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val providerId: String,
    val providerName: String,
    val retentionLabel: String,
    val measuredLatencyMs: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val localPreviewUri: String? = null
) {
    val isImage: Boolean get() = mimeType.startsWith("image/", ignoreCase = true)
    val isVideo: Boolean get() = mimeType.startsWith("video/", ignoreCase = true)
}

data class CloudAppPreferences(
    val autoUploadOnShare: Boolean = true,
    val sharePopupMode: Boolean = true,
    val defaultRetention: RetentionPreference = RetentionPreference.AUTO_24H,
    val preferredProviderId: CloudProviderId? = null, // null = Tự động chọn tối ưu
    val disabledProviders: Set<String> = emptySet(),
    val imgbbApiKey: String = "",
    val gofileToken: String = "",
    val catboxUserHash: String = "",
    val customEndpointUrl: String = "",
    val customFieldName: String = "file",
    val customAuthHeader: String = "",
    val customJsonUrlPath: String = "url",
    val autoCheckUpdates: Boolean = true,
    val updateRepoOverride: String = ""
) {
    fun isProviderEnabled(providerId: CloudProviderId): Boolean =
        providerId.id !in disabledProviders

    fun isProviderConfigured(providerId: CloudProviderId): Boolean = when (providerId) {
        CloudProviderId.IMGBB -> imgbbApiKey.isNotBlank()
        CloudProviderId.CUSTOM -> customEndpointUrl.trim().startsWith("https://", ignoreCase = true)
        else -> true
    }

    fun hasOptionalKey(providerId: CloudProviderId): Boolean = when (providerId) {
        CloudProviderId.CATBOX -> catboxUserHash.isNotBlank()
        CloudProviderId.GOFILE -> gofileToken.isNotBlank()
        CloudProviderId.IMGBB -> imgbbApiKey.isNotBlank()
        CloudProviderId.CUSTOM -> customEndpointUrl.isNotBlank()
        else -> false
    }
}
