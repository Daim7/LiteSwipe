package com.liteswipe.app.util

import com.liteswipe.app.data.model.MediaItem

/**
 * 进程内共享的浏览列表，供相册与大图浏览等界面传递当前数据集。
 */
object SharedMediaData {
    private var viewerItems: List<MediaItem>? = null

    /** 设置大图/浏览流程使用的媒体项列表。 */
    fun setViewerItems(items: List<MediaItem>) {
        viewerItems = items
    }

    /** 返回已保存的列表；未设置时为空列表，避免调用方判空。 */
    fun getViewerItems(): List<MediaItem> {
        return viewerItems ?: emptyList()
    }

    /** 释放引用，避免长期持有大列表。 */
    fun clear() {
        viewerItems = null
    }
}
