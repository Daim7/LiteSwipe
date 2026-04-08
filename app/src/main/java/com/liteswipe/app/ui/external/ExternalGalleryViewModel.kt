package com.liteswipe.app.ui.external

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liteswipe.app.data.model.DateGroup
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.data.repository.ExternalMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 外置相册的数据与交互状态：加载分组、列数缩放、导入与删除外置文件。 */
@HiltViewModel
class ExternalGalleryViewModel @Inject constructor(
    private val externalMediaRepository: ExternalMediaRepository
) : ViewModel() {

    private val _dateGroups = MutableLiveData<List<DateGroup>>()
    val dateGroups: LiveData<List<DateGroup>> = _dateGroups

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _selectedItems = MutableLiveData<Set<Long>>(emptySet())
    val selectedItems: LiveData<Set<Long>> = _selectedItems

    private val _hasExternalStorage = MutableLiveData(false)
    val hasExternalStorage: LiveData<Boolean> = _hasExternalStorage

    private val _isMultiSelectMode = MutableLiveData(false)
    val isMultiSelectMode: LiveData<Boolean> = _isMultiSelectMode

    private val _columnCount = MutableLiveData(3)
    val columnCount: LiveData<Int> = _columnCount

    /** 当前已加载分组中的全部媒体项（扁平列表）。 */
    val allItems: List<MediaItem>
        get() = _dateGroups.value?.flatMap { it.items } ?: emptyList()

    /** 同步外置存储是否可用的标记（供 UI 显示空态或列表）。 */
    fun checkExternalStorage() {
        _hasExternalStorage.value = externalMediaRepository.hasExternalStorage()
    }

    /** 首次进入时加载外置媒体分组；若已有缓存则不再重复请求。 */
    fun loadExternalMedia() {
        if (_dateGroups.value != null) return
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val groups = externalMediaRepository.loadExternalMedia()
                _dateGroups.value = groups
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 强制重新扫描外置媒体（删除或导入后用于刷新列表）。 */
    fun reloadExternalMedia() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val groups = externalMediaRepository.loadExternalMedia()
                _dateGroups.value = groups
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 切换多选模式；关闭时清空 ViewModel 内记录的选中 ID。 */
    fun toggleMultiSelect() {
        val newMode = _isMultiSelectMode.value != true
        _isMultiSelectMode.value = newMode
        if (!newMode) {
            _selectedItems.value = emptySet()
        }
    }

    /** 在 ViewModel 选中集合中切换指定媒体 ID。 */
    fun toggleSelection(itemId: Long) {
        val set = _selectedItems.value?.toMutableSet() ?: mutableSetOf()
        if (set.contains(itemId)) set.remove(itemId) else set.add(itemId)
        _selectedItems.value = set
    }

    /** 将当前全部外置项标为选中。 */
    fun selectAll() {
        _selectedItems.value = allItems.map { it.id }.toSet()
    }

    /** 清空 ViewModel 内的选中集合。 */
    fun deselectAll() {
        _selectedItems.value = emptySet()
    }

    /** 按日期分组全选或全不选：若该组已全部选中则移除，否则并入该组全部 ID。 */
    fun selectGroup(dateKey: String) {
        val groupIds = _dateGroups.value
            ?.find { it.dateKey == dateKey }
            ?.items?.map { it.id }?.toSet() ?: return
        val current = _selectedItems.value?.toMutableSet() ?: mutableSetOf()
        if (groupIds.all { it in current }) current.removeAll(groupIds)
        else current.addAll(groupIds)
        _selectedItems.value = current
    }

    /** 根据当前选中 ID 返回对应的 [MediaItem] 列表。 */
    fun getSelectedMediaItems(): List<MediaItem> {
        val ids = _selectedItems.value ?: return emptyList()
        return allItems.filter { it.id in ids }
    }

    /** 在预设列数档位间切换，实现网格「放大/缩小」观感。 */
    fun adjustZoom(zoomIn: Boolean) {
        val cols = listOf(2, 3, 5, 7)
        val current = _columnCount.value ?: 3
        val idx = cols.indexOf(current).coerceAtLeast(0)
        val newIdx = if (zoomIn) (idx - 1).coerceAtLeast(0) else (idx + 1).coerceAtMost(cols.lastIndex)
        _columnCount.value = cols[newIdx]
    }

    /** 将 ViewModel 当前选中的项导入本地，完成后退出多选并清空选中。 */
    fun importSelected(onProgress: (Int, Int) -> Unit, onComplete: (Int, Int) -> Unit) {
        val items = getSelectedMediaItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            val result = externalMediaRepository.importToLocal(items, onProgress)
            _selectedItems.value = emptySet()
            _isMultiSelectMode.value = false
            onComplete(result.success, result.failed)
        }
    }

    /** 将指定列表（如导入队列）导入本地，不改变多选状态。 */
    fun importSelected(items: List<MediaItem>, onProgress: (Int, Int) -> Unit, onComplete: (Int, Int) -> Unit) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val result = externalMediaRepository.importToLocal(items, onProgress)
            onComplete(result.success, result.failed)
        }
    }

    /** 按文件路径删除外置文件，结束后刷新分组并回调成功/失败数量。 */
    fun deleteItems(items: List<MediaItem>, onComplete: (Int, Int) -> Unit) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            var success = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                for (item in items) {
                    try {
                        val path = item.uri.path ?: continue
                        val file = File(path)
                        if (file.exists() && file.delete()) {
                            success++
                        } else {
                            failed++
                        }
                    } catch (_: Exception) {
                        failed++
                    }
                }
            }
            reloadExternalMedia()
            onComplete(success, failed)
        }
    }

    /** 删除当前 ViewModel 选中项对应外置文件，并退出多选后刷新列表。 */
    fun deleteSelected(onComplete: (Int, Int) -> Unit) {
        val items = getSelectedMediaItems()
        if (items.isEmpty()) return
        viewModelScope.launch {
            var success = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                for (item in items) {
                    try {
                        val path = item.uri.path ?: continue
                        val file = File(path)
                        if (file.exists() && file.delete()) {
                            success++
                        } else {
                            failed++
                        }
                    } catch (_: Exception) {
                        failed++
                    }
                }
            }
            _selectedItems.value = emptySet()
            _isMultiSelectMode.value = false
            reloadExternalMedia()
            onComplete(success, failed)
        }
    }
}
