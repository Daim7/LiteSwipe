package com.liteswipe.app.di

import android.content.ContentResolver
import android.content.Context
import android.os.storage.StorageManager
import androidx.room.Room
import com.liteswipe.app.data.db.AppDatabase
import com.liteswipe.app.data.db.TrashDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 全局单例依赖的 Hilt 模块：系统服务与本地数据库等。 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** 提供 ContentResolver，用于通过 content URI 读写媒体等资源。 */
    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    /** 提供 StorageManager，用于访问存储卷与路径相关信息。 */
    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager {
        return context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    }

    /** 构建 Room 数据库单例，供 DAO 与仓库层持久化使用。 */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "liteswipe.db"
        ).build()
    }

    /** 提供回收站表 DAO，与数据库单例配套注入。 */
    @Provides
    @Singleton
    fun provideTrashDao(database: AppDatabase): TrashDao {
        return database.trashDao()
    }
}
