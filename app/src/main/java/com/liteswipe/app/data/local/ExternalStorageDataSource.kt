package com.liteswipe.app.data.local

import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.data.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 通过 [StorageManager] 访问可移动外置存储并扫描其中的媒体文件。 */
@Singleton
class ExternalStorageDataSource @Inject constructor(
    private val storageManager: StorageManager
) {

    private val supportedExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp",
        "mp4", "mkv", "avi", "mov", "3gp", "webm"
    )

    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp")

    /** 返回当前已挂载的可移动存储卷。 */
    fun getExternalVolumes(): List<StorageVolume> {
        return storageManager.storageVolumes.filter {
            it.isRemovable && it.state == Environment.MEDIA_MOUNTED
        }
    }

    /** 是否存在可用的外置存储（至少一个可移动卷已挂载）。 */
    fun hasExternalStorage(): Boolean = getExternalVolumes().isNotEmpty()

    /** 扫描所有外置卷中的图片与视频，按修改时间从新到旧排序后返回。 */
    suspend fun scanExternalMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        var idCounter = -1L

        for (volume in getExternalVolumes()) {
            val dir = volume.directory ?: continue
            scanDirectory(dir, items, idCounter)
        }

        items.sortedByDescending { it.dateModified }
    }

    // 深度遍历目录，将符合扩展名的文件转为 [MediaItem] 并写入列表（id 递减分配）。
    private fun scanDirectory(dir: File, items: MutableList<MediaItem>, startId: Long) {
        var id = startId
        dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            .forEach { file ->
                id--
                val ext = file.extension.lowercase()
                val isImage = ext in imageExtensions
                val mimeType = if (isImage) "image/$ext" else "video/$ext"

                items.add(
                    MediaItem(
                        id = id,
                        uri = Uri.fromFile(file),
                        displayName = file.name,
                        mimeType = mimeType,
                        dateAdded = file.lastModified() / 1000,
                        dateModified = file.lastModified(),
                        size = file.length(),
                        source = MediaSource.EXTERNAL
                    )
                )
            }
    }
}
