package com.liteswipe.app.ui.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.liteswipe.app.R
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.databinding.ActivityExternalViewerBinding
import com.liteswipe.app.util.ImportQueueManager
import com.liteswipe.app.util.SharedMediaData
import com.liteswipe.app.util.toast
import com.liteswipe.app.util.visibleIf
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.abs

/** 外置相册大图查看：下滑加入导入队列、上滑隐藏当前项，并展示已选缩略图条。 */
@AndroidEntryPoint
class ExternalPhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExternalViewerBinding
    private val viewModel: ExternalPhotoViewerViewModel by viewModels()
    private lateinit var pagerAdapter: ViewerPagerAdapter
    private lateinit var thumbAdapter: SelectedThumbAdapter

    private var downX = 0f
    private var downY = 0f
    private var verticalDragging = false
    private var gestureDecided = false
    private val TRIGGER_THRESHOLD_DP = 120f
    private var isAnimating = false

    /** 装配 ViewPager、缩略图、导入按钮与返回处理，并从共享缓存恢复列表与起始页。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.viewer_enter, R.anim.no_anim)
        binding = ActivityExternalViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val items = SharedMediaData.getViewerItems()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        viewModel.setItems(ArrayList(items))
        setupViewPager(items, startIndex)
        setupThumbList()
        setupObservers()

        binding.btnBack.setOnClickListener { finishWithAnim() }
        binding.btnToggleSelect.setOnClickListener { toggleCurrentInQueue() }
        binding.btnImport.setOnClickListener { performImport() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finishWithAnim() }
        })

        updateCounter(startIndex)
        updateSelectIcon(startIndex)
    }

    // 绑定分页适配器与页码变化时的计数、选中态刷新。
    private fun setupViewPager(items: List<MediaItem>, startIndex: Int) {
        pagerAdapter = ViewerPagerAdapter(items)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.setCurrentItem(startIndex, false)

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateCounter(position)
                updateSelectIcon(position)
            }
        })
    }

    // 横向缩略图列表：点击跳转到对应全屏页。
    private fun setupThumbList() {
        thumbAdapter = SelectedThumbAdapter { item ->
            val items = viewModel.items.value ?: return@SelectedThumbAdapter
            val idx = items.indexOfFirst { it.id == item.id }
            if (idx >= 0) {
                binding.viewPager.setCurrentItem(idx, true)
            }
        }
        binding.rvSelectedThumbnails.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvSelectedThumbnails.adapter = thumbAdapter
    }

    // 更新顶部「当前页/总张数」文案。
    private fun updateCounter(position: Int) {
        val total = viewModel.items.value?.size ?: 0
        binding.tvCounter.text = "${position + 1}/$total"
    }

    // 根据导入队列是否包含当前项，切换「选中」按钮视觉状态。
    private fun updateSelectIcon(position: Int) {
        val items = viewModel.items.value ?: return
        if (position >= items.size) return
        val item = items[position]
        val inQueue = ImportQueueManager.getItems().any { it.id == item.id }
        binding.btnToggleSelect.isSelected = inQueue
    }

    // 将当前页媒体加入或移出导入队列。
    private fun toggleCurrentInQueue() {
        val pos = binding.viewPager.currentItem
        val items = viewModel.items.value ?: return
        if (pos >= items.size) return
        val item = items[pos]

        if (ImportQueueManager.getItems().any { it.id == item.id }) {
            ImportQueueManager.remove(item)
        } else {
            ImportQueueManager.add(item)
        }
    }

    // 订阅导入队列：控制导入按钮、缩略图条与当前页选中图标。
    private fun setupObservers() {
        ImportQueueManager.queue.observe(this) { queue ->
            val count = queue.size
            binding.btnImport.visibleIf(count > 0)
            binding.btnImport.text = "导入 ($count)"
            binding.tvQueueCount.text = if (count > 0) "已选择 $count 项" else ""
            binding.rvSelectedThumbnails.visibleIf(count > 0)
            thumbAdapter.submitList(queue.toList())
            updateSelectIcon(binding.viewPager.currentItem)
        }
    }

    /** 识别垂直拖动手势：达阈值则上滑隐藏或下滑入队，否则回弹；多指时交由系统处理。 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isAnimating) return true

        if (ev.pointerCount > 1) {
            if (verticalDragging) {
                snapBack()
                verticalDragging = false
                gestureDecided = false
            }
            return super.dispatchTouchEvent(ev)
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                verticalDragging = false
                gestureDecided = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1) return super.dispatchTouchEvent(ev)

                val dy = ev.rawY - downY
                val dx = ev.rawX - downX
                val absDy = abs(dy)
                val absDx = abs(dx)

                if (!gestureDecided && (absDy > 30 || absDx > 30)) {
                    verticalDragging = absDy > absDx * 1.2f
                    gestureDecided = true
                }

                if (verticalDragging) {
                    val progress = (absDy / (resources.displayMetrics.density * 300f)).coerceIn(0f, 1f)
                    val scale = 1f - progress * 0.4f
                    binding.viewPager.translationY = dy * 0.5f
                    binding.viewPager.scaleX = scale
                    binding.viewPager.scaleY = scale

                    if (dy < 0) {
                        binding.tvSwipeUpHint.alpha = progress
                        binding.tvSwipeDownHint.alpha = 0f
                    } else {
                        binding.tvSwipeDownHint.alpha = progress
                        binding.tvSwipeUpHint.alpha = 0f
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (verticalDragging) {
                    val dy = ev.rawY - downY
                    val distDp = abs(dy) / resources.displayMetrics.density

                    binding.tvSwipeUpHint.animate().alpha(0f).setDuration(150).start()
                    binding.tvSwipeDownHint.animate().alpha(0f).setDuration(150).start()

                    if (distDp > TRIGGER_THRESHOLD_DP) {
                        if (dy < 0) completeSwipeUp() else completeSwipeDown()
                    } else {
                        snapBack()
                    }
                    verticalDragging = false
                    gestureDecided = false
                    return true
                }
                verticalDragging = false
                gestureDecided = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // 上滑动画结束后从列表移除当前项并提示隐藏；若无剩余则关闭页面。
    private fun completeSwipeUp() {
        isAnimating = true
        binding.viewPager.animate()
            .translationY(-binding.viewPager.height * 0.5f)
            .scaleX(0.3f).scaleY(0.3f).alpha(0f)
            .setDuration(250)
            .withEndAction {
                val pos = binding.viewPager.currentItem
                val items = viewModel.items.value ?: return@withEndAction
                if (pos >= items.size) return@withEndAction
                viewModel.hideItem(pos)
                toast(getString(R.string.hidden))
                resetViewPager()
                isAnimating = false
                val remaining = viewModel.items.value ?: emptyList()
                if (remaining.isEmpty()) finishWithAnim()
                else pagerAdapter.updateItems(remaining)
            }.start()
    }

    // 下滑时将当前项加入导入队列并播放收起动画，便于连续挑选。
    private fun completeSwipeDown() {
        isAnimating = true
        val pos = binding.viewPager.currentItem
        val items = viewModel.items.value
        if (items != null && pos < items.size) {
            ImportQueueManager.add(items[pos])
            toast(getString(R.string.added_to_import))
        }

        binding.viewPager.animate()
            .translationY(binding.viewPager.height * 0.3f)
            .scaleX(0.7f).scaleY(0.7f)
            .setDuration(200)
            .withEndAction {
                resetViewPager()
                isAnimating = false
            }.start()
    }

    // 未达阈值时恢复 ViewPager 位姿与透明度。
    private fun snapBack() {
        binding.viewPager.animate()
            .translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(200).start()
    }

    // 取消动画后的变换，避免残留位移或缩放。
    private fun resetViewPager() {
        binding.viewPager.translationY = 0f
        binding.viewPager.scaleX = 1f
        binding.viewPager.scaleY = 1f
        binding.viewPager.alpha = 1f
    }

    // 使用与进入一致的退出过渡关闭 Activity。
    private fun finishWithAnim() {
        finish()
        overridePendingTransition(R.anim.no_anim, R.anim.viewer_exit)
    }

    // 将队列中媒体导入本地，完成后清空队列并提示成功数量。
    private fun performImport() {
        val items = ImportQueueManager.getItems()
        if (items.isEmpty()) return
        viewModel.importItems(items,
            onProgress = { _, _ -> },
            onComplete = { success, _ ->
                ImportQueueManager.clear()
                toast(getString(R.string.import_success, success))
            }
        )
    }

    /** 避免静态共享列表泄漏，离开查看器后释放 [SharedMediaData]。 */
    override fun onDestroy() {
        super.onDestroy()
        SharedMediaData.clear()
    }

    companion object {
        private const val EXTRA_START_INDEX = "start_index"

        /** 写入共享媒体列表后启动查看器，并指定初始下标。 */
        fun start(context: Context, items: List<MediaItem>, startIndex: Int = 0) {
            SharedMediaData.setViewerItems(items)
            context.startActivity(Intent(context, ExternalPhotoViewerActivity::class.java).apply {
                putExtra(EXTRA_START_INDEX, startIndex)
            })
        }
    }
}

/** 导入队列横向缩略图：展示已选项并支持点击定位到大图。 */
private class SelectedThumbAdapter(
    private val onClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, SelectedThumbAdapter.VH>(DiffCb) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.ivThumb)
        val highlight: View = view.findViewById(R.id.viewHighlight)
    }

    /** 实例化单项缩略图布局。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selected_thumbnail, parent, false))
    }

    /** 加载图片并绑定点击以跳转对应页。 */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        Glide.with(holder.thumb).load(item.uri).centerCrop().into(holder.thumb)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    companion object DiffCb : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(a: MediaItem, b: MediaItem) = a.id == b.id
        override fun areContentsTheSame(a: MediaItem, b: MediaItem) = a == b
    }
}
