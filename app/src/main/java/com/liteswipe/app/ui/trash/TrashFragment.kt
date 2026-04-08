package com.liteswipe.app.ui.trash

import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liteswipe.app.R
import com.liteswipe.app.data.model.TrashItem
import com.liteswipe.app.databinding.FragmentTrashBinding
import com.liteswipe.app.util.GridSpacingDecoration
import com.liteswipe.app.util.toast
import com.liteswipe.app.util.visibleIf
import dagger.hilt.android.AndroidEntryPoint

/**
 * 回收站网格页：多选、恢复与永久删除；Android 11+ 通过系统删除确认 Intent 处理真实文件。
 */
@AndroidEntryPoint
class TrashFragment : Fragment() {

    private var _binding: FragmentTrashBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrashViewModel by viewModels()
    private lateinit var trashAdapter: TrashAdapter

    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onMediaStoreDeleteResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrashBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 初始化网格列表、观察回收站数据与多选状态，并绑定工具栏与底栏操作。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupObservers()
        setupClickListeners()
    }

    // 三列网格与间距；勾选通过 ViewModel 切换选中 id。
    private fun setupRecyclerView() {
        trashAdapter = TrashAdapter(
            onItemClick = { /* preview */ },
            onCheckClick = { viewModel.toggleSelection(it.id) }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = trashAdapter
        val spacingPx = (2 * resources.displayMetrics.density).toInt()
        binding.recyclerView.addItemDecoration(GridSpacingDecoration(spacingPx))
    }

    // 列表与空态、多选横幅/底栏及主界面底部导航显隐与选中数量联动。
    private fun setupObservers() {
        viewModel.trashItems.observe(viewLifecycleOwner) { items ->
            val isMulti = viewModel.isMultiSelectMode.value ?: false
            val selected = viewModel.selectedIds.value ?: emptySet()
            trashAdapter.submitList(items.map {
                TrashListItem(it, it.id in selected, isMulti)
            })
            binding.emptyView.visibleIf(items.isEmpty())
            binding.recyclerView.visibleIf(items.isNotEmpty())
        }

        viewModel.isMultiSelectMode.observe(viewLifecycleOwner) { isMulti ->
            binding.btnSelect.text = if (isMulti) getString(R.string.done) else getString(R.string.select)
            binding.selectBanner.visibleIf(isMulti)
            binding.bottomBar.visibleIf(isMulti)
            (activity as? com.liteswipe.app.ui.main.MainActivity)?.setBottomNavVisible(!isMulti)
            refreshList()
        }

        viewModel.selectedIds.observe(viewLifecycleOwner) { selected ->
            binding.tvSelectedCount.text = getString(R.string.selected_count, selected.size)
            refreshList()
        }
    }

    // 进入/退出多选、全选、恢复与永久删除的确认流程。
    private fun setupClickListeners() {
        binding.btnSelect.setOnClickListener { viewModel.toggleMultiSelect() }
        binding.btnSelectAll.setOnClickListener { viewModel.selectAll() }
        binding.btnCancelSelect.setOnClickListener { viewModel.toggleMultiSelect() }

        binding.btnRestore.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_restore_title)
                .setMessage(R.string.confirm_restore_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore) { _, _ ->
                    viewModel.restoreSelected()
                    requireContext().toast(getString(R.string.restored))
                }
                .show()
        }

        binding.btnPermanentDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.permanent_delete) { _, _ ->
                    performPermanentDelete()
                }
                .show()
        }

    }

    // Android 11+ 尽量弹出系统删除请求；失败或旧版本则仅更新本地回收站记录。
    private fun performPermanentDelete() {
        val items = viewModel.getSelectedItemsForDelete()
        if (items.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = viewModel.getDeleteUris(items)
            if (uris.isNotEmpty()) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        requireContext().contentResolver, uris
                    )
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                    viewModel.permanentlyDeleteSelected(items)
                } catch (_: Exception) {
                    viewModel.permanentlyDeleteSelected(items)
                }
            } else {
                viewModel.permanentlyDeleteSelected(items)
            }
        } else {
            viewModel.permanentlyDeleteSelected(items)
        }
        requireContext().toast(getString(R.string.permanently_deleted))
    }

    // 多选模式或选中集合变化时重算列表项上的勾选展示。
    private fun refreshList() {
        val items = viewModel.trashItems.value ?: return
        val isMulti = viewModel.isMultiSelectMode.value ?: false
        val selected = viewModel.selectedIds.value ?: emptySet()
        trashAdapter.submitList(items.map {
            TrashListItem(it, it.id in selected, isMulti)
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
