package com.liteswipe.app.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 动态照片（Motion Photo / 微视频）检测与内嵌视频抽取，结果写入应用缓存。
 */
object MotionPhotoHelper {

    private val FTYP_SIGNATURE = byteArrayOf(0x66, 0x74, 0x79, 0x70) // "ftyp"
    private val XMP_READ_SIZE = 65536

    /**
     * 读取文件头部 XMP，根据厂商标记判断该 URI 是否对应动态照片。
     */
    fun isMotionPhoto(contentResolver: ContentResolver, uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readLimited(XMP_READ_SIZE)
                val xmpStr = String(bytes, Charsets.ISO_8859_1)
                checkXmpForMotionPhoto(xmpStr)
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    // 匹配常见 XMP 字段，判断是否声明为动态照片或微视频。
    private fun checkXmpForMotionPhoto(xmp: String): Boolean {
        return xmp.contains("MicroVideo=\"1\"") ||
            xmp.contains("MicroVideo='1'") ||
            xmp.contains("MotionPhoto=\"1\"") ||
            xmp.contains("MotionPhoto='1'") ||
            xmp.contains(":MotionPhoto>1<") ||
            xmp.contains(":MicroVideo>1<")
    }

    /**
     * 从 XMP 中解析 MicroVideoOffset（内嵌视频相对文件末尾的字节数），失败返回 0。
     */
    fun parseMicroVideoOffset(contentResolver: ContentResolver, uri: Uri): Long {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bytes = stream.readLimited(XMP_READ_SIZE)
                val xmpStr = String(bytes, Charsets.ISO_8859_1)
                val regex = Regex("""MicroVideoOffset[=">]+(\d+)""")
                regex.find(xmpStr)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 将动态照片中的内嵌视频导出为缓存目录下的临时 MP4；无法解析时返回 null。
     */
    suspend fun extractVideo(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val offset = parseMicroVideoOffset(resolver, uri)

            if (offset > 0) {
                extractByOffset(context, resolver, uri, offset)
            } else {
                extractByFtypScan(context, resolver, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    // 已知尾部偏移时，从文件末尾向前截取 MP4 数据写入缓存文件。
    private fun extractByOffset(
        context: Context, resolver: ContentResolver, uri: Uri, microVideoOffset: Long
    ): File? {
        val fileSize = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: return null
        val videoStart = fileSize - microVideoOffset
        if (videoStart <= 0) return null

        return resolver.openInputStream(uri)?.use { stream ->
            stream.skip(videoStart)
            val cacheDir = File(context.cacheDir, "motion_video")
            cacheDir.mkdirs()
            val videoFile = File(cacheDir, "motion_${System.currentTimeMillis()}.mp4")
            FileOutputStream(videoFile).use { fos ->
                stream.copyTo(fos)
            }
            videoFile
        }
    }

    // 无有效偏移时读入整文件，通过扫描 ftyp 定位 MP4 起点再写出片段。
    private fun extractByFtypScan(
        context: Context, resolver: ContentResolver, uri: Uri
    ): File? {
        return resolver.openInputStream(uri)?.use { stream ->
            val allBytes = stream.readBytes()
            val videoOffset = findVideoOffset(allBytes) ?: return@use null

            val cacheDir = File(context.cacheDir, "motion_video")
            cacheDir.mkdirs()
            val videoFile = File(cacheDir, "motion_${System.currentTimeMillis()}.mp4")
            FileOutputStream(videoFile).use { fos ->
                fos.write(allBytes, videoOffset, allBytes.size - videoOffset)
            }
            videoFile
        }
    }

    // 在字节数组中查找 MP4「ftyp」盒起始位置，作为视频流起点。
    private fun findVideoOffset(data: ByteArray): Int? {
        for (i in data.indices) {
            if (i + 7 < data.size &&
                data[i + 4] == FTYP_SIGNATURE[0] &&
                data[i + 5] == FTYP_SIGNATURE[1] &&
                data[i + 6] == FTYP_SIGNATURE[2] &&
                data[i + 7] == FTYP_SIGNATURE[3]
            ) {
                return i
            }
        }
        return null
    }

    // 限制单次读取上限，避免对大文件一次性分配过大缓冲区。
    private fun InputStream.readLimited(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var total = 0
        while (total < limit) {
            val read = read(buffer, total, limit - total)
            if (read == -1) break
            total += read
        }
        return buffer.copyOf(total)
    }

    /** 删除动态视频抽取缓存目录，释放磁盘空间。 */
    fun clearCache(context: Context) {
        val cacheDir = File(context.cacheDir, "motion_video")
        cacheDir.deleteRecursively()
    }
}
