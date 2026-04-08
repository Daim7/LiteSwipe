package com.liteswipe.app.ui.viewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 大图查看页状态：当前媒体列表、移入回收站与简版撤销。 */
@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    // 内部可变的当前列表数据源。
    private val _items = MutableLiveData<List<MediaItem>>()

    /** 供界面观察的当前查看媒体列表。 */
    val items: LiveData<List<MediaItem>> = _items

    // 最近一次移入回收站的项，用于 [undoTrash] 仅恢复界面列表（未从回收站物理还原）。
    private var lastDeletedItem: MediaItem? = null

    /** 设置查看器要展示的媒体序列（拷贝一份避免外部可变列表副作用）。 */
    fun setItems(itemList: List<MediaItem>) {
        _items.value = itemList.toList()
    }

    /** 从当前列表移除该项并异步交给仓库删除（进回收站）；同时记下以便撤销。 */
    fun moveToTrash(item: MediaItem) {
        lastDeletedItem = item
        val current = _items.value?.toMutableList() ?: return
        current.remove(item)
        _items.value = current

        viewModelScope.launch {
            mediaRepository.deleteMedia(item)
        }
    }

    /** 将最近一次删除的项重新加入列表并按添加时间排序（简版撤销，未从回收站恢复文件）。 */
    fun undoTrash() {
        val item = lastDeletedItem ?: return
        lastDeletedItem = null
        val current = _items.value?.toMutableList() ?: mutableListOf()
        current.add(item)
        current.sortByDescending { it.dateAdded }
        _items.value = current
    }
}
