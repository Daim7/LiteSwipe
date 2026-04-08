package com.liteswipe.app.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.data.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通过 MediaStore 读取外置存储中的图片与视频，并组装为应用内 [MediaItem]。
 */
@Singleton
class MediaStoreDataSource @Inject constructor(
    private val contentResolver: ContentResolver
) {

    /** 并行查询图片与视频后合并，按添加时间从新到旧排序。 */
    suspend fun queryLocalMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        items.addAll(queryImages())
        items.addAll(queryVideos())
        items.sortedByDescending { it.dateAdded }
    }

    // 遍历 Images 集合；依文件名规则预判实况照片并写入 isMotionPhoto。
    private fun queryImages(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                val name = cursor.getString(nameCol) ?: ""
                val isMotion = name.startsWith("MVIMG", ignoreCase = true) ||
                    name.matches(Regex(".*MP\\.(jpg|jpeg|heic|avif)", RegexOption.IGNORE_CASE))
                items.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        displayName = name,
                        mimeType = cursor.getString(mimeCol) ?: "image/*",
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModCol),
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        bucketId = cursor.getString(bucketIdCol) ?: "",
                        bucketName = cursor.getString(bucketNameCol) ?: "",
                        source = MediaSource.LOCAL,
                        isMotionPhoto = isMotion
                    )
                )
            }
        }
        return items
    }

    // 遍历 Video 集合，填充时长与相册桶等视频特有字段。
    private fun queryVideos(): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
            val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                )
                items.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "",
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModCol),
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        duration = cursor.getLong(durationCol),
                        bucketId = cursor.getString(bucketIdCol) ?: "",
                        bucketName = cursor.getString(bucketNameCol) ?: "",
                        source = MediaSource.LOCAL
                    )
                )
            }
        }
        return items
    }
}
