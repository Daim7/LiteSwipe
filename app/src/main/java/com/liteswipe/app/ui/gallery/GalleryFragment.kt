package com.liteswipe.app.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.liteswipe.app.data.model.DateGroup
import com.liteswipe.app.databinding.FragmentGalleryBinding
import com.liteswipe.app.ui.main.MainActivity
import com.liteswipe.app.ui.viewer.PhotoViewerActivity
import com.liteswipe.app.util.GridSpacingDecoration
import com.liteswipe.app.util.gone
import com.liteswipe.app.util.visible
import com.liteswipe.app.util.visibleIf
import dagger.hilt.android.AndroidEntryPoint

/**
 * 本地相册：按日期分组的网格、多选与双指捏合调整列数。
 */
@AndroidEntryPoint
class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by activityViewModels()
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var lastColumnCount = -1

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.loadMedia()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 绑定列表与手势，订阅 ViewModel 并按需请求读媒体权限。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupScaleGesture()
        setupObservers()
        setupClickListeners()
        requestPermissionsIfNeeded()
    }

    // 网格列数、分组头占满一行、间距与 Adapter 回调（点击/多选/按日全选）。
    private fun setupRecyclerView() {
        mediaAdapter = MediaAdapter(
            onItemClick = { item, index ->
                val items = mediaAdapter.allMediaItems()
                PhotoViewerActivity.start(requireContext(), items, index)
            },
            onItemLongClick = {
                if (viewModel.isMultiSelectMode.value != true) {
                    viewModel.toggleMultiSelect()
                    viewModel.toggleSelection(it.id)
                }
            },
            onCheckClick = { viewModel.toggleSelection(it.id) },
            onGroupSelectAll = { viewModel.selectGroup(it) }
        )

        val spanCount = viewModel.columnCount.value ?: 3
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (mediaAdapter.getItemViewType(position) == MediaAdapter.VIEW_TYPE_HEADER) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = mediaAdapter
        binding.recyclerView.setHasFixedSize(true)
        val spacingPx = (2 * resources.displayMetrics.density).toInt()
        binding.recyclerView.addItemDecoration(GridSpacingDecoration(spacingPx))
    }

    // 双指缩放超过阈值时增减列数，并把手势交给 ScaleGestureDetector 消费。
    private fun setupScaleGesture() {
        var accumulatedScale = 1f
        var hasTriggered = false

        scaleGestureDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    accumulatedScale = 1f
                    hasTriggered = false
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    accumulatedScale *= detector.scaleFactor
                    if (!hasTriggered) {
                        if (accumulatedScale > 1.15f) {
                            viewModel.adjustZoom(zoomIn = true)
                            hasTriggered = true
                        } else if (accumulatedScale < 0.85f) {
                            viewModel.adjustZoom(zoomIn = false)
                            hasTriggered = true
                        }
                    }
                    return true
                }
            }
        )

        binding.recyclerView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            false
        }
    }

    // 订阅分组数据、多选、列数与加载状态，驱动空态、底栏与缩放提示。
    private fun setupObservers() {
        viewModel.dateGroups.observe(viewLifecycleOwner) { groups ->
            updateList(groups)
            binding.emptyView.visibleIf(groups.isEmpty())
            binding.recyclerView.visibleIf(groups.isNotEmpty())
        }

        viewModel.isMultiSelectMode.observe(viewLifecycleOwner) { isMultiSelect ->
            binding.selectBanner.visibleIf(isMultiSelect)
            binding.bottomBar.visibleIf(isMultiSelect)
            (activity as? MainActivity)?.setBottomNavVisible(!isMultiSelect)
            refreshList()
        }

        viewModel.selectedItems.observe(viewLifecycleOwner) { selected ->
            binding.tvSelectedCount.text = "已选择 ${selected.size} 项"
            binding.btnDeleteSelected.text = "移入回收站 (${selected.size})"
            refreshList()
        }

        viewModel.columnCount.observe(viewLifecycleOwner) { count ->
            val layoutManager = binding.recyclerView.layoutManager as? GridLayoutManager
            layoutManager?.spanCount = count
            if (lastColumnCount != -1 && lastColumnCount != count) {
                showZoomHint(count)
            }
            lastColumnCount = count
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibleIf(loading)
        }
    }

    // 多选条：全选、退出多选、将选中项移入回收站。
    private fun setupClickListeners() {
        binding.btnSelectAll.setOnClickListener { viewModel.selectAll() }
        binding.btnCancelSelect.setOnClickListener { viewModel.toggleMultiSelect() }
        binding.btnDeleteSelected.setOnClickListener { viewModel.deleteSelected() }
    }

    // 将 DateGroup 展平为「日期头 + 媒体行」并带上当前多选状态提交给列表。
    private fun updateList(groups: List<DateGroup>) {
        val isMultiSelect = viewModel.isMultiSelectMode.value ?: false
        val selected = viewModel.selectedItems.value ?: emptySet()
        val list = mutableListOf<GalleryListItem>()
        for (group in groups) {
            list.add(GalleryListItem.Header(group.dateKey, group.displayLabel))
            for (item in group.items) {
                list.add(
                    GalleryListItem.Media(
                        item = item,
                        isSelected = item.id in selected,
                        isMultiSelectMode = isMultiSelect
                    )
                )
            }
        }
        mediaAdapter.submitList(list)
    }

    // 在仅多选状态变化时复用已有分组数据重建列表，避免重复扫描媒体库。
    private fun refreshList() {
        val groups = viewModel.dateGroups.value ?: return
        updateList(groups)
    }

    // 列数变更后短暂显示当前列数，便于用户感知网格密度变化。
    private fun showZoomHint(count: Int) {
        binding.tvZoomHint.text = "${count}列"
        binding.tvZoomHint.visible()
        binding.tvZoomHint.animate()
            .alpha(1f)
            .setDuration(200)
            .withEndAction {
                binding.tvZoomHint.animate()
                    .alpha(0f)
                    .setStartDelay(800)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    // 按系统版本请求读图/视频权限；已授权则直接触发媒体加载。
    private fun requestPermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (needRequest) {
            permissionLauncher.launch(permissions)
        } else {
            viewModel.loadMedia()
        }
    }

    /** 每次对用户可见时重新加载媒体，与其他应用变更保持同步。 */
    override fun onResume() {
        super.onResume()
        viewModel.loadMedia()
    }

    /** 解除 ViewBinding 引用，防止在 Fragment 销毁后仍持有旧视图。 */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
