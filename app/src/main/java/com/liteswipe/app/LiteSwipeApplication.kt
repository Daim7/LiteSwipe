package com.liteswipe.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用进程入口：启用 Hilt，并向 WorkManager 提供可注入 Worker 的工厂。
 */
@HiltAndroidApp
class LiteSwipeApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** WorkManager 启动时使用的配置，使 [HiltWorker] 能通过 Hilt 注入依赖。 */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
