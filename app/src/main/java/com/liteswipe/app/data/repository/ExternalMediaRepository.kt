package com.liteswipe.app.data.repository

import android.content.Context
import android.media.MediaScannerConnection
import com.liteswipe.app.data.local.ExternalStorageDataSource
import com.liteswipe.app.data.model.DateGroup
import com.liteswipe.app.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 负责外部存储媒体的扫描，以及将文件导入到应用本地图库。 */
@Singleton
class ExternalMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val externalDataSource: ExternalStorageDataSource,
    private val mediaRepository: MediaRepository
) {

    /** 判断当前环境是否可访问外部存储（如可移除存储）。 */
    fun hasExternalStorage(): Boolean = externalDataSource.hasExternalStorage()

    /** 扫描外部目录中的媒体文件，并按日期分组供界面展示。 */
    suspend fun loadExternalMedia(): List<DateGroup> = withContext(Dispatchers.IO) {
        val items = externalDataSource.scanExternalMedia()
        mediaRepository.groupByDate(items)
    }

    /**
     * 将外部媒体复制到本地导入目录，冲突时自动重命名；并触发系统媒体扫描以便相册可见。
     * [onProgress] 传入已完成数量与总数，便于 UI 显示进度。
     */
    suspend fun importToLocal(items: List<MediaItem>, onProgress: (Int, Int) -> Unit): ImportResult {
        return withContext(Dispatchers.IO) {
            val targetDir = mediaRepository.getImportTargetDir()
            var success = 0
            var failed = 0

            items.forEachIndexed { index, item ->
                try {
                    val sourceFile = File(item.uri.path ?: return@forEachIndexed)
                    val targetFile = File(targetDir, item.displayName)

                    if (targetFile.exists()) {
                        val name = item.displayName.substringBeforeLast(".")
                        val ext = item.displayName.substringAfterLast(".")
                        val newName = "${name}_${System.currentTimeMillis()}.${ext}"
                        sourceFile.copyTo(File(targetDir, newName))
                        MediaScannerConnection.scanFile(
                            context, arrayOf(File(targetDir, newName).absolutePath), null, null
                        )
                    } else {
                        sourceFile.copyTo(targetFile)
                        MediaScannerConnection.scanFile(
                            context, arrayOf(targetFile.absolutePath), null, null
                        )
                    }
                    success++
                } catch (e: Exception) {
                    e.printStackTrace()
                    failed++
                }
                onProgress(index + 1, items.size)
            }

            ImportResult(success, failed)
        }
    }

    /** 批量导入结束后的成功条数与失败条数统计。 */
    data class ImportResult(val success: Int, val failed: Int)
}
