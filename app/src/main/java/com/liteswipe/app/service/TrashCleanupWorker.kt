package com.liteswipe.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.liteswipe.app.data.db.TrashDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 后台定期清理回收站过期条目，并通过 WorkManager 唯一周期任务调度。
 */
@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val trashDao: TrashDao
) : CoroutineWorker(context, workerParams) {

    /**
     * 删除数据库中已过期回收项；异常时返回重试以便后续再次执行。
     */
    override suspend fun doWork(): Result {
        return try {
            val expired = trashDao.getAllTrashItems()
            trashDao.deleteExpired()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "trash_cleanup"

        /**
         * 以「保留已有任务」策略注册每日一次、低电量不运行的清理任务。
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(
                1, TimeUnit.DAYS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
