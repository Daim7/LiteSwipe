package com.liteswipe.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 回收站中的媒体条目：保留原 URI、落盘路径与尺寸等信息，并记录删除与过期时间。 */
@Entity(tableName = "trash_items")
data class TrashItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalUri: String,
    val trashFilePath: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0,
    val deletedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = deletedAt + RETENTION_DAYS * 24 * 60 * 60 * 1000L
) {
    /** 距离自动永久删除还剩多少整天；已过期或当天内不足一天时视为 0。 */
    val daysRemaining: Int
        get() {
            val remaining = (expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000L)
            return remaining.toInt().coerceAtLeast(0)
        }

    /** 当前时间是否已超过保留期限，可用于判定是否可被清理任务删除。 */
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt
    /** 是否为视频资源，便于 UI 与播放逻辑区分图片与视频。 */
    val isVideo: Boolean get() = mimeType.startsWith("video/")

    companion object {
        /** 移入回收站后默认可恢复的保留天数。 */
        const val RETENTION_DAYS = 30
    }
}
