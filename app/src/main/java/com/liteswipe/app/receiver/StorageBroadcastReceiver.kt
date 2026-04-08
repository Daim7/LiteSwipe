package com.liteswipe.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.storage.StorageManager
import android.os.storage.StorageVolume

/**
 * 响应外置存储挂载/卸载等系统广播，向回调方告知是否存在可移动卷及卷列表。
 */
class StorageBroadcastReceiver(
    private val onStorageChanged: (Boolean, List<StorageVolume>) -> Unit
) : BroadcastReceiver() {

    /**
     * 查询已挂载的可移动存储卷，并调用 [onStorageChanged] 传递是否存在外置存储与卷集合。
     */
    override fun onReceive(context: Context, intent: Intent) {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = storageManager.storageVolumes.filter { it.isRemovable && it.state == "mounted" }
        val hasExternal = volumes.isNotEmpty()
        onStorageChanged(hasExternal, volumes)
    }

    companion object {
        /** 构建需注册的媒体挂载、卸载、移除、弹出等 Action，并限定 file scheme。 */
        fun getIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addDataScheme("file")
            }
        }
    }
}
