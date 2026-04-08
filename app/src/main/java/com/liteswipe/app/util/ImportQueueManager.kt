package com.liteswipe.app.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.liteswipe.app.data.model.MediaItem

/** 全局导入队列：LiveData 暴露待导入媒体，并在内存侧按 id 去重。 */
object ImportQueueManager {

    private val _queue = MutableLiveData<List<MediaItem>>(emptyList())

    /** 可观察的待导入媒体列表。 */
    val queue: LiveData<List<MediaItem>> = _queue

    /** 当前队列中的条目数量。 */
    val count: Int get() = _queue.value?.size ?: 0

    /** 将单条媒体加入队列；相同 id 不会重复加入。 */
    fun add(item: MediaItem) {
        val current = _queue.value?.toMutableList() ?: mutableListOf()
        if (current.none { it.id == item.id }) {
            current.add(item)
            _queue.value = current
        }
    }

    /** 批量加入队列，跳过队列中已存在的 id。 */
    fun addAll(items: List<MediaItem>) {
        val current = _queue.value?.toMutableList() ?: mutableListOf()
        val existingIds = current.map { it.id }.toSet()
        for (item in items) {
            if (item.id !in existingIds) {
                current.add(item)
            }
        }
        _queue.value = current
    }

    /** 从队列中移除与给定媒体 id 相同的所有项。 */
    fun remove(item: MediaItem) {
        val current = _queue.value?.toMutableList() ?: return
        current.removeAll { it.id == item.id }
        _queue.value = current
    }

    /** 清空整个导入队列。 */
    fun clear() {
        _queue.value = emptyList()
    }

    /** 返回当前队列快照（无数据时为空列表）。 */
    fun getItems(): List<MediaItem> = _queue.value ?: emptyList()
}
