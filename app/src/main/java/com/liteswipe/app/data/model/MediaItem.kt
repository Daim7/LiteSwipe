package com.liteswipe.app.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 单条媒体的展示与元数据模型，支持 Parcel 传递及本地/外部来源区分。
 */
@Parcelize
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0,
    val bucketId: String = "",
    val bucketName: String = "",
    val source: MediaSource = MediaSource.LOCAL,
    val isMotionPhoto: Boolean = false
) : Parcelable {

    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")

    /** 将毫秒时长格式化为「分:秒」展示文案；无效时长返回空串。 */
    val formattedDuration: String
        get() {
            if (duration <= 0) return ""
            val totalSeconds = duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }

    /** 将字节大小格式化为带单位的可读字符串（B/KB/MB/GB）。 */
    val formattedSize: String
        get() {
            return when {
                size >= 1024 * 1024 * 1024 -> "%.1f GB".format(size / (1024.0 * 1024 * 1024))
                size >= 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024))
                size >= 1024 -> "%.1f KB".format(size / 1024.0)
                else -> "$size B"
            }
        }
}

/** 媒体条目来自设备本地图库或外部挂载存储。 */
enum class MediaSource {
    LOCAL, EXTERNAL
}
