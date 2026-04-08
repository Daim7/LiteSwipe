package com.liteswipe.app.data.db

import androidx.room.*
import com.liteswipe.app.data.model.TrashItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {

    /** 按删除时间倒序观察回收站全部条目，供界面实时刷新。 */
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun getAllTrashItems(): Flow<List<TrashItem>>

    /** 一次性读取回收站全部条目，用于后台任务或无需响应式更新的场景。 */
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    suspend fun getAllTrashItemsSync(): List<TrashItem>

    /** 观察回收站条目数量，用于角标或空状态判断。 */
    @Query("SELECT COUNT(*) FROM trash_items")
    fun getTrashCount(): Flow<Int>

    /** 写入或更新一条回收站记录（冲突时替换）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrashItem): Long

    /** 从数据库移除单条回收站记录（如恢复或用户手动清空）。 */
    @Delete
    suspend fun delete(item: TrashItem)

    /** 批量移除回收站记录，减少多次单独删除的开销。 */
    @Delete
    suspend fun deleteAll(items: List<TrashItem>)

    /** 删除所有已超过保留截止时间的条目，供定时清理调用。 */
    @Query("DELETE FROM trash_items WHERE expiresAt < :currentTime")
    suspend fun deleteExpired(currentTime: Long = System.currentTimeMillis())

    /** 按主键查询单条记录，用于详情或针对单条的操作。 */
    @Query("SELECT * FROM trash_items WHERE id = :id")
    suspend fun getById(id: Long): TrashItem?

    /** 观察回收站内文件占用的总字节数，用于存储占用展示。 */
    @Query("SELECT SUM(size) FROM trash_items")
    fun getTotalSize(): Flow<Long?>
}
