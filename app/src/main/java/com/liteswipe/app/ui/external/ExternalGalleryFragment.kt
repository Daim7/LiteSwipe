package com.liteswipe.app.ui.external

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.liteswipe.app.R
import com.liteswipe.app.databinding.FragmentExternalGalleryBinding
import com.liteswipe.app.ui.gallery.GalleryListItem
import com.liteswipe.app.ui.gallery.MediaAdapter
import com.liteswipe.app.ui.main.MainActivity
import com.liteswipe.app.ui.main.MainViewModel
import com.liteswipe.app.ui.viewer.ExternalPhotoViewerActivity
import com.liteswipe.app.util.GridSpacingDecoration
import com.liteswipe.app.util.ImportQueueManager
import com.liteswipe.app.util.toast
import com.liteswipe.app.util.visibleIf
import dagger.hilt.android.AndroidEntryPoint

/** 外置存储相册页：按日期浏览、多选加入导入队列，并支持捏合调整网格列数。 */
@AndroidEntryPoint
class ExternalGalleryFragment : Fragment() {

    private var _binding: FragmentExternalGalleryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExternalGalleryViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: MediaAdapter
    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExternalGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 初始化列表、缩放手势、数据观察与按钮逻辑，并触发外置存储检查。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupScaleGesture()
        setupObservers()
        setupClickListeners()
        viewModel.checkExternalStorage()
    }

    // 配置按日期分组的网格、勾选与分组全选回调（勾选与点击查看分离）。
    private fun setupRecyclerView() {
        adapter = MediaAdapter(
            onItemClick = { _, index ->
                val items = adapter.allMediaItems()
                ExternalPhotoViewerActivity.start(requireContext(), items, index)
            },
            onItemLongClick = {},
            onCheckClick = { item ->
                val queueItems = ImportQueueManager.getItems()
                if (queueItems.any { it.id == item.id }) {
                    ImportQueueManager.remove(item)
                } else {
                    ImportQueueManager.add(item)
                }
            },
            onGroupSelectAll = { dateKey ->
                val group = viewModel.dateGroups.value?.find { it.dateKey == dateKey } ?: return@MediaAdapter
                val queueIds = ImportQueueManager.getItems().map { it.id }.toSet()
                val allInQueue = group.items.all { it.id in queueIds }
                if (allInQueue) {
                    group.items.forEach { ImportQueueManager.remove(it) }
                } else {
                    ImportQueueManager.addAll(group.items)
                }
            },
            separateCheckAndView = true
        )

        val lm = androidx.recyclerview.widget.GridLayoutManager(
            requireContext(), viewModel.columnCount.value ?: 3
        )
        lm.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) =
                if (adapter.getItemViewType(position) == MediaAdapter.VIEW_TYPE_HEADER) lm.spanCount else 1
        }
        binding.recyclerView.layoutManager = lm
        binding.recyclerView.adapter = adapter
        val spacingPx = (2 * resources.displayMetrics.density).toInt()
        binding.recyclerView.addItemDecoration(GridSpacingDecoration(spacingPx))
    }

    // 双指捏合在预设列数档位间切换，避免与列表滚动冲突（触摸仍交给 RecyclerView）。
    private fun setupScaleGesture() {
        var accumulatedScale = 1f
        var hasTriggered = false

        scaleDetector = ScaleGestureDetector(requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    accumulatedScale = 1f
                    hasTriggered = false
                    return true
                }

                override fun onScale(d: ScaleGestureDetector): Boolean {
                    accumulatedScale *= d.scaleFactor
                    if (!hasTriggered) {
                        if (accumulatedScale > 1.15f) {
                            viewModel.adjustZoom(true)
                            hasTriggered = true
                        } else if (accumulatedScale < 0.85f) {
                            viewModel.adjustZoom(false)
                            hasTriggered = true
                        }
                    }
                    return true
                }
            })
        binding.recyclerView.setOnTouchListener { _, e -> scaleDetector.onTouchEvent(e); false }
    }

    // 根据外置存储可用性、分组数据、列数与导入队列刷新列表与底部栏。
    private fun setupObservers() {
        mainViewModel.hasExternalStorage.observe(viewLifecycleOwner) { hasExternal ->
            if (hasExternal) {
                binding.emptyView.visibility = View.GONE
                binding.recyclerView.visibility = View.VISIBLE
                viewModel.loadExternalMedia()
            } else {
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
                binding.selectBanner.visibility = View.GONE
                binding.bottomBar.visibility = View.GONE
            }
        }

        viewModel.dateGroups.observe(viewLifecycleOwner) { groups ->
            refreshList()
            if (groups.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.GONE
            }
        }

        viewModel.columnCount.observe(viewLifecycleOwner) { count ->
            (binding.recyclerView.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)?.spanCount = count
        }

        ImportQueueManager.queue.observe(viewLifecycleOwner) { queue ->
            val count = queue.size
            binding.importQueueBar.visibleIf(count > 0)
            binding.tvQueueCount.text = "待导入 ($count)"
            binding.selectBanner.visibleIf(count > 0)
            binding.tvSelectedCount.text = "已选择 $count 项"
            binding.bottomBar.visibleIf(count > 0)
            binding.btnImport.text = "导入 ($count)"
            binding.btnDelete.text = "删除 ($count)"
            refreshList()
        }
    }

    // 全选/反选、导入、删除外置文件及队列条入口。
    private fun setupClickListeners() {
        binding.btnSelect.setOnClickListener {
            val allItems = viewModel.allItems
            val queueIds = ImportQueueManager.getItems().map { it.id }.toSet()
            if (allItems.all { it.id in queueIds }) {
                ImportQueueManager.clear()
            } else {
                ImportQueueManager.addAll(allItems)
            }
        }

        binding.btnSelectAll.setOnClickListener {
            ImportQueueManager.addAll(viewModel.allItems)
        }

        binding.btnCancelSelect.setOnClickListener {
            ImportQueueManager.clear()
        }

        binding.btnImport.setOnClickListener {
            val items = ImportQueueManager.getItems()
            if (items.isEmpty()) return@setOnClickListener
            viewModel.importSelected(
                items,
                onProgress = { _, _ -> },
                onComplete = { success, _ ->
                    ImportQueueManager.clear()
                    requireContext().toast("成功导入 $success 张")
                }
            )
        }

        binding.btnDelete.setOnClickListener {
            val items = ImportQueueManager.getItems()
            if (items.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.delete_external_confirm, items.size))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_external) { _, _ ->
                    viewModel.deleteItems(items) { success, _ ->
                        ImportQueueManager.clear()
                        requireContext().toast("已删除 $success 个文件")
                    }
                }
                .show()
        }

        binding.btnImportAll.setOnClickListener {
            performImportQueue()
        }

        binding.btnClearQueue.setOnClickListener {
            ImportQueueManager.clear()
        }

        binding.importQueueBar.setOnClickListener {
            showImportQueueSheet()
        }
    }

    // 打开待导入队列底部表，并将「全部导入」委托给当前页逻辑。
    private fun showImportQueueSheet() {
        val sheet = ImportQueueBottomSheet()
        sheet.onImportAll = { performImportQueue() }
        sheet.show(childFragmentManager, ImportQueueBottomSheet.TAG)
    }

    // 将队列中的项批量导入本地，完成后清空队列并提示结果。
    private fun performImportQueue() {
        val items = ImportQueueManager.getItems()
        if (items.isEmpty()) return
        viewModel.importSelected(
            items,
            onProgress = { _, _ -> },
            onComplete = { success, _ ->
                ImportQueueManager.clear()
                requireContext().toast("成功导入 $success 张")
            }
        )
    }

    // 合并日期分组与队列选中状态，生成带头部的列表并提交适配器。
    private fun refreshList() {
        val groups = viewModel.dateGroups.value ?: return
        val queueIds = ImportQueueManager.getItems().map { it.id }.toSet()
        val list = mutableListOf<GalleryListItem>()
        for (group in groups) {
            list.add(GalleryListItem.Header(group.dateKey, group.displayLabel))
            for (item in group.items) {
                list.add(GalleryListItem.Media(
                    item = item,
                    isSelected = item.id in queueIds,
                    isMultiSelectMode = true
                ))
            }
        }
        adapter.submitList(list)
    }

    /** 页签重新可见时同步存储状态并刷新外置列表，避免挂载/权限变化后数据陈旧。 */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            mainViewModel.checkExternalStorage()
            viewModel.checkExternalStorage()
            if (mainViewModel.hasExternalStorage.value == true) {
                viewModel.reloadExternalMedia()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
