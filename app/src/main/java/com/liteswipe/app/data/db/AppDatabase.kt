package com.liteswipe.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.liteswipe.app.data.model.TrashItem

/** 应用本地 Room 数据库，用于回收站等持久化数据。 */
@Database(entities = [TrashItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    /** 回收站条目的数据访问接口。 */
    abstract fun trashDao(): TrashDao
}
