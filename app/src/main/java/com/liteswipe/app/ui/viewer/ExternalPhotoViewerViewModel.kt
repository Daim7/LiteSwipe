package com.liteswipe.app.ui.viewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.data.repository.ExternalMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 外部来源照片的浏览：维护当前列表、临时隐藏项与导入到本地。 */
@HiltViewModel
class ExternalPhotoViewerViewModel @Inject constructor(
    private val externalMediaRepository: ExternalMediaRepository
) : ViewModel() {

    private val _items = MutableLiveData<List<MediaItem>>()
    val items: LiveData<List<MediaItem>> = _items

    private val hiddenIds = mutableSetOf<Long>()

    /** 设置或替换当前查看器中的媒体列表（使用副本避免外部修改列表）。 */
    fun setItems(itemList: List<MediaItem>) {
        _items.value = itemList.toList()
    }

    /** 从列表移除指定位置项并记录其 id，用于滑走忽略等不影响源文件的交互。 */
    fun hideItem(position: Int) {
        val current = _items.value?.toMutableList() ?: return
        if (position >= current.size) return
        hiddenIds.add(current[position].id)
        current.removeAt(position)
        _items.value = current
    }

    /**
     * 在后台将外部媒体导入本地图库；通过 [onProgress] 报告进度，结束时 [onComplete] 返回成功与失败数量。
     */
    fun importItems(items: List<MediaItem>, onProgress: (Int, Int) -> Unit, onComplete: (Int, Int) -> Unit) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val result = externalMediaRepository.importToLocal(items, onProgress)
            onComplete(result.success, result.failed)
        }
    }
}
