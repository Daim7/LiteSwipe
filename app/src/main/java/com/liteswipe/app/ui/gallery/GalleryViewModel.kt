package com.liteswipe.app.ui.gallery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liteswipe.app.data.model.DateGroup
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 本地相册网格：加载分组数据、多选、列数缩放与批量删除。 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _dateGroups = MutableLiveData<List<DateGroup>>()
    val dateGroups: LiveData<List<DateGroup>> = _dateGroups

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isMultiSelectMode = MutableLiveData(false)
    val isMultiSelectMode: LiveData<Boolean> = _isMultiSelectMode

    private val _selectedItems = MutableLiveData<Set<Long>>(emptySet())
    val selectedItems: LiveData<Set<Long>> = _selectedItems

    private val _columnCount = MutableLiveData(3)
    val columnCount: LiveData<Int> = _columnCount

    /** 当前所有日期分组下的媒体项扁平列表，供全选等逻辑使用。 */
    val allItems: List<MediaItem>
        get() = _dateGroups.value?.flatMap { it.items } ?: emptyList()

    /** 从仓库加载本地媒体分组；仅在首次加载时置为加载中，避免刷新时闪烁。 */
    fun loadMedia() {
        if (_isLoading.value == true) return
        val isFirstLoad = _dateGroups.value == null
        if (isFirstLoad) _isLoading.value = true
        viewModelScope.launch {
            try {
                val groups = mediaRepository.loadLocalMedia()
                _dateGroups.value = groups
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (isFirstLoad) _isLoading.value = false
            }
        }
    }

    /** 切换多选模式；关闭多选时清空已选项。 */
    fun toggleMultiSelect() {
        val current = _isMultiSelectMode.value ?: false
        _isMultiSelectMode.value = !current
        if (current) {
            _selectedItems.value = emptySet()
        }
    }

    /** 切换指定媒体项在多选中的勾选状态。 */
    fun toggleSelection(itemId: Long) {
        val current = _selectedItems.value?.toMutableSet() ?: mutableSetOf()
        if (current.contains(itemId)) {
            current.remove(itemId)
        } else {
            current.add(itemId)
        }
        _selectedItems.value = current
    }

    /** 将当前列表中全部媒体 id 设为已选。 */
    fun selectAll() {
        val allIds = allItems.map { it.id }.toSet()
        _selectedItems.value = allIds
    }

    /** 清空多选集合。 */
    fun deselectAll() {
        _selectedItems.value = emptySet()
    }

    /** 按日期键切换该组内全部项：若已全选则整组取消，否则整组选中。 */
    fun selectGroup(dateKey: String) {
        val groupItems = _dateGroups.value
            ?.find { it.dateKey == dateKey }
            ?.items
            ?.map { it.id }
            ?.toSet() ?: return

        val current = _selectedItems.value?.toMutableSet() ?: mutableSetOf()
        val allSelected = groupItems.all { it in current }
        if (allSelected) {
            current.removeAll(groupItems)
        } else {
            current.addAll(groupItems)
        }
        _selectedItems.value = current
    }

    /** 设置网格列数，限制在 2～7 之间以防布局极端。 */
    fun setColumnCount(count: Int) {
        _columnCount.value = count.coerceIn(2, 7)
    }

    /** 在预设列数档位间移动，实现缩略图放大（列少）或缩小（列多）。 */
    fun adjustZoom(zoomIn: Boolean) {
        val cols = listOf(2, 3, 5, 7)
        val current = _columnCount.value ?: 3
        val currentIdx = cols.indexOf(current).takeIf { it >= 0 } ?: 1
        val newIdx = if (zoomIn) {
            (currentIdx - 1).coerceAtLeast(0)
        } else {
            (currentIdx + 1).coerceAtMost(cols.lastIndex)
        }
        _columnCount.value = cols[newIdx]
    }

    /** 永久删除当前选中项，清空多选并重新加载列表。 */
    fun deleteSelected() {
        val selectedIds = _selectedItems.value ?: return
        if (selectedIds.isEmpty()) return
        val items = allItems.filter { it.id in selectedIds }
        viewModelScope.launch {
            items.forEach { mediaRepository.deleteMedia(it) }
            _selectedItems.value = emptySet()
            _isMultiSelectMode.value = false
            loadMedia()
        }
    }
}
