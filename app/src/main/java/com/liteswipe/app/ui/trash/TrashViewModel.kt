package com.liteswipe.app.ui.trash

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.liteswipe.app.data.model.TrashItem
import com.liteswipe.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 回收站：列表观察、多选、恢复与永久删除（含系统删除授权后的收尾）。 */
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val trashItems: LiveData<List<TrashItem>> = mediaRepository.getTrashItems().asLiveData()
    val trashCount: LiveData<Int> = mediaRepository.getTrashCount().asLiveData()

    private val _isMultiSelectMode = MutableLiveData(false)
    val isMultiSelectMode: LiveData<Boolean> = _isMultiSelectMode

    private val _selectedIds = MutableLiveData<Set<Long>>(emptySet())
    val selectedIds: LiveData<Set<Long>> = _selectedIds

    private var pendingDeleteItems: List<TrashItem>? = null

    /** 切换多选模式；关闭时清空已选 id。 */
    fun toggleMultiSelect() {
        val current = _isMultiSelectMode.value ?: false
        _isMultiSelectMode.value = !current
        if (current) _selectedIds.value = emptySet()
    }

    /** 切换单条回收站项的勾选状态。 */
    fun toggleSelection(id: Long) {
        val set = _selectedIds.value?.toMutableSet() ?: mutableSetOf()
        if (set.contains(id)) set.remove(id) else set.add(id)
        _selectedIds.value = set
    }

    /** 选中当前列表中的全部回收站项。 */
    fun selectAll() {
        val ids = trashItems.value?.map { it.id }?.toSet() ?: emptySet()
        _selectedIds.value = ids
    }

    /** 将勾选项从回收站恢复到正常图库，并退出多选。 */
    fun restoreSelected() {
        val ids = _selectedIds.value ?: return
        val items = trashItems.value?.filter { it.id in ids } ?: return
        viewModelScope.launch {
            items.forEach { mediaRepository.restoreFromTrash(it) }
            _selectedIds.value = emptySet()
            _isMultiSelectMode.value = false
        }
    }

    /** 返回当前多选中的 [TrashItem] 列表，供确认删除等 UI 使用。 */
    fun getSelectedItemsForDelete(): List<TrashItem> {
        val ids = _selectedIds.value ?: return emptyList()
        return trashItems.value?.filter { it.id in ids } ?: emptyList()
    }

    /** 解析待删项对应的文件 URI，用于触发系统级删除确认（如 MediaStore）。 */
    fun getDeleteUris(items: List<TrashItem>): List<Uri> {
        return mediaRepository.getPendingDeleteUris(items)
    }

    /** 异步从数据库永久移除传入项或当前多选项，并退出多选。 */
    fun permanentlyDeleteSelected(items: List<TrashItem>? = null) {
        val toDelete = items ?: getSelectedItemsForDelete()
        if (toDelete.isEmpty()) return
        pendingDeleteItems = toDelete
        viewModelScope.launch {
            mediaRepository.permanentlyDeleteFromDb(toDelete)
            _selectedIds.value = emptySet()
            _isMultiSelectMode.value = false
            pendingDeleteItems = null
        }
    }

    /** 用户同意系统删除权限后，若有暂存的待删项则再次执行数据库侧永久删除并清空状态。 */
    fun onMediaStoreDeleteResult(granted: Boolean) {
        if (!granted) return
        val items = pendingDeleteItems ?: return
        viewModelScope.launch {
            mediaRepository.permanentlyDeleteFromDb(items)
            _selectedIds.value = emptySet()
            _isMultiSelectMode.value = false
            pendingDeleteItems = null
        }
    }
}
