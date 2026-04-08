package com.liteswipe.app.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.liteswipe.app.data.repository.ExternalMediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 主界面 ViewModel：维护外部存储（如 U 盘）是否可用的状态。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val externalMediaRepository: ExternalMediaRepository
) : ViewModel() {

    private val _hasExternalStorage = MutableLiveData(false)
    val hasExternalStorage: LiveData<Boolean> = _hasExternalStorage

    /** 检测当前是否存在可用的外部存储并更新 LiveData。 */
    fun checkExternalStorage() {
        _hasExternalStorage.value = externalMediaRepository.hasExternalStorage()
    }

    /** 系统存储挂载/卸载等变化时调用，重新检测外部存储。 */
    fun onStorageChanged() {
        checkExternalStorage()
    }
}
