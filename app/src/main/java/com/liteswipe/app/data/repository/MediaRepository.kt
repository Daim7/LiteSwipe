package com.liteswipe.app.data.repository

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.liteswipe.app.data.db.TrashDao
import com.liteswipe.app.data.local.MediaStoreDataSource
import com.liteswipe.app.data.model.DateGroup
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.data.model.TrashItem
import com.liteswipe.app.util.MotionPhotoHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地媒体与回收站相关数据源：查询、按日分组、软删除/彻底删除及导入目录解析。
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val trashDao: TrashDao
) {

    /**
     * 从 MediaStore 加载本地媒体，过滤回收站中条目并按添加日期分组展示。
     */
    suspend fun loadLocalMedia(): List<DateGroup> = withContext(Dispatchers.IO) {
        val items = mediaStoreDataSource.queryLocalMedia()
        val trashedUris = trashDao.getAllTrashItemsSync().map { it.originalUri }.toSet()
        val filtered = items.filter { it.uri.toString() !in trashedUris }
        groupByDate(filtered)
    }

    /**
     * 按添加日期分组，并对「今天」「昨天」及跨年日期生成本地化分组标题。
     */
    fun groupByDate(items: List<MediaItem>): List<DateGroup> {
        val calendar = Calendar.getInstance()
        val today = calendar.clone() as Calendar
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val yesterday = today.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val displayFormat = SimpleDateFormat("M月d日", Locale.getDefault())

        return items
            .groupBy { item ->
                val itemCal = Calendar.getInstance().apply {
                    timeInMillis = item.dateAdded * 1000
                }
                dateFormat.format(itemCal.time)
            }
            .map { (dateKey, groupItems) ->
                val firstItem = groupItems.first()
                val itemCal = Calendar.getInstance().apply {
                    timeInMillis = firstItem.dateAdded * 1000
                }
                val label = when {
                    itemCal.timeInMillis >= today.timeInMillis -> "今天"
                    itemCal.timeInMillis >= yesterday.timeInMillis -> "昨天"
                    itemCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) ->
                        displayFormat.format(itemCal.time)
                    else -> {
                        val yearFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
                        yearFormat.format(itemCal.time)
                    }
                }
                DateGroup(
                    dateKey = dateKey,
                    displayLabel = label,
                    items = groupItems
                )
            }
            .sortedByDescending { it.dateKey }
    }

    /**
     * 将媒体复制到应用回收目录并写入回收站表，便于后续恢复或彻底删除。
     */
    suspend fun deleteMedia(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashDir = File(context.filesDir, "trash")
            if (!trashDir.exists()) trashDir.mkdirs()

            val trashFile = File(trashDir, "${System.currentTimeMillis()}_${item.displayName}")

            context.contentResolver.openInputStream(item.uri)?.use { input ->
                trashFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val trashItem = TrashItem(
                originalUri = item.uri.toString(),
                trashFilePath = trashFile.absolutePath,
                displayName = item.displayName,
                mimeType = item.mimeType,
                size = item.size,
                width = item.width,
                height = item.height,
                duration = item.duration
            )
            trashDao.insert(trashItem)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 删除回收站中的备份文件并移除数据库记录（原图库条目需由其他流程恢复）。
     */
    suspend fun restoreFromTrash(trashItem: TrashItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashFile = File(trashItem.trashFilePath)
            if (trashFile.exists()) trashFile.delete()
            trashDao.delete(trashItem)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 删除回收站备份、尝试从 MediaStore 移除原条目，并清除对应回收站记录。
     */
    suspend fun permanentlyDelete(trashItem: TrashItem) = withContext(Dispatchers.IO) {
        val trashFile = File(trashItem.trashFilePath)
        if (trashFile.exists()) trashFile.delete()
        try {
            val uri = Uri.parse(trashItem.originalUri)
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) { }
        trashDao.delete(trashItem)
    }

    /**
     * 将回收站项的原始 URI 解析为列表，供系统批量删除等 API 使用。
     */
    fun getPendingDeleteUris(items: List<TrashItem>): List<Uri> {
        return items.mapNotNull { 
            try { Uri.parse(it.originalUri) } catch (_: Exception) { null }
        }
    }

    /**
     * 仅删除磁盘上的回收站备份与数据库行，不通过 ContentResolver 操作媒体库。
     */
    suspend fun permanentlyDeleteFromDb(items: List<TrashItem>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            val trashFile = File(item.trashFilePath)
            if (trashFile.exists()) trashFile.delete()
            trashDao.delete(item)
        }
    }

    /** 回收站条目列表的响应式数据流。 */
    fun getTrashItems(): Flow<List<TrashItem>> = trashDao.getAllTrashItems()

    /** 回收站中条目数量的响应式数据流。 */
    fun getTrashCount(): Flow<Int> = trashDao.getTrashCount()

    /** 回收站占用总字节数的响应式数据流（可能为空）。 */
    fun getTrashTotalSize(): Flow<Long?> = trashDao.getTotalSize()

    /**
     * 返回导入媒体时的目标目录：优先可写的公共 DCIM/Camera，否则回退到应用专属目录。
     */
    fun getImportTargetDir(): File {
        val dcim = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "Camera"
        )
        return if (dcim.exists() && dcim.canWrite()) {
            dcim
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: File(context.filesDir, "pictures").also { it.mkdirs() }
        }
    }

    // 触发 MediaScanner，使新写入文件尽快出现在系统图库中。
    private fun scanFile(file: File) {
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, file.parentFile?.path ?: "")
        }
        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(file.absolutePath), null, null
        )
    }
}
