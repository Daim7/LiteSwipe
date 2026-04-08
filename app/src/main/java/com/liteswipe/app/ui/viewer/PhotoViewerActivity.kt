package com.liteswipe.app.ui.viewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.liteswipe.app.R
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.databinding.ActivityPhotoViewerBinding
import com.liteswipe.app.util.SharedMediaData
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * 全屏媒体查看：左右翻页、上滑将当前项移入回收站、下滑退出；支持实况长按播放。
 */
@AndroidEntryPoint
class PhotoViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewerBinding
    private val viewModel: PhotoViewerViewModel by viewModels()
    private lateinit var pagerAdapter: ViewerPagerAdapter

    private var downX = 0f
    private var downY = 0f
    private var verticalDragging = false
    private var gestureDecided = false
    private val TRIGGER_THRESHOLD_DP = 120f
    private var isAnimating = false

    private var longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressTriggered = false
    private val LONG_PRESS_TIMEOUT = 300L
    private var motionVideoPlaying = false
    private val longPressRunnable = Runnable {
        if (!gestureDecided && !verticalDragging) {
            longPressTriggered = true
            triggerMotionPhotoPlay()
        }
    }

    /**
     * 从共享列表启动查看器、绑定返回与分页，并展示起始项元信息。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(R.anim.viewer_enter, R.anim.no_anim)
        binding = ActivityPhotoViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val items = SharedMediaData.getViewerItems()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)

        viewModel.setItems(ArrayList(items))
        setupViewPager(items, startIndex)

        binding.btnBack.setOnClickListener { finishWithAnim() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishWithAnim()
            }
        })
    }

    // 配置 ViewPager；翻页时更新信息并在必要时停止实况播放。
    private fun setupViewPager(items: List<MediaItem>, startIndex: Int) {
        pagerAdapter = ViewerPagerAdapter(items)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.setCurrentItem(startIndex, false)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (motionVideoPlaying) {
                    stopMotionVideoIfPlaying()
                    motionVideoPlaying = false
                }
                val currentItems = viewModel.items.value ?: items
                if (position < currentItems.size) updateInfo(currentItems[position])
            }
        })

        if (items.isNotEmpty()) {
            updateInfo(items[startIndex.coerceIn(0, items.lastIndex)])
        }
    }

    /**
     * 区分垂直滑动与左右翻页；上/下滑达阈值执行删除或退出，并与长按实况互不干扰。
     */
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
                longPressTriggered = false
                longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
            }
            MotionEvent.ACTION_MOVE -> {
                if (ev.pointerCount > 1) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    return super.dispatchTouchEvent(ev)
                }

                val dy = ev.rawY - downY
                val dx = ev.rawX - downX
                val absDy = abs(dy)
                val absDx = abs(dx)

                if (longPressTriggered) {
                    return true
                }

                if (!gestureDecided && (absDy > 30 || absDx > 30)) {
                    longPressHandler.removeCallbacks(longPressRunnable)
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
                    }
                    binding.tvSwipeDownHint.alpha = 0f
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                if (longPressTriggered || motionVideoPlaying) {
                    longPressTriggered = false
                    stopMotionVideoIfPlaying()
                    motionVideoPlaying = false
                    return true
                }

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

    // 上滑动画结束后将当前项移入回收站；若无剩余项则退出，否则刷新列表并支持撤销。
    private fun completeSwipeUp() {
        isAnimating = true
        binding.viewPager.animate()
            .translationY(-binding.viewPager.height * 0.5f)
            .scaleX(0.3f)
            .scaleY(0.3f)
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                val position = binding.viewPager.currentItem
                val items = viewModel.items.value ?: return@withEndAction
                if (position >= items.size) return@withEndAction

                viewModel.moveToTrash(items[position])
                resetViewPager()
                isAnimating = false

                val remaining = viewModel.items.value ?: emptyList()
                if (remaining.isEmpty()) {
                    finishWithAnim()
                } else {
                    pagerAdapter.updateItems(remaining)
                    if (position >= remaining.size) {
                        binding.viewPager.setCurrentItem(remaining.lastIndex, false)
                    }
                    Snackbar.make(binding.rootLayout, getString(R.string.moved_to_trash), Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.undo)) { viewModel.undoTrash() }
                        .show()
                }
            }
            .start()
    }

    // 下滑动画结束后关闭查看器（视为用户放弃浏览）。
    private fun completeSwipeDown() {
        isAnimating = true
        binding.viewPager.animate()
            .translationY(binding.viewPager.height * 0.5f)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                isAnimating = false
                finishWithAnim()
            }
            .start()
    }

    // 未达阈值时恢复 ViewPager 的位移与缩放，取消滑动提示。
    private fun snapBack() {
        binding.viewPager.animate()
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    // 在删除动画后立刻复位变换属性，避免下一页继承错位视觉。
    private fun resetViewPager() {
        binding.viewPager.translationY = 0f
        binding.viewPager.scaleX = 1f
        binding.viewPager.scaleY = 1f
        binding.viewPager.alpha = 1f
    }

    // 长按且判定为实况时，委托适配器播放当前页内嵌视频。
    private fun triggerMotionPhotoPlay() {
        val rv = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        if (rv == null) { android.util.Log.d("MotionPlay", "rv is null"); return }
        val pos = binding.viewPager.currentItem
        val holder = rv.findViewHolderForAdapterPosition(pos) as? ViewerPagerAdapter.ViewHolder
        if (holder == null) { android.util.Log.d("MotionPlay", "holder is null at pos=$pos"); return }
        android.util.Log.d("MotionPlay", "holder found: isMotion=${holder.isMotionPhoto}, isPlaying=${holder.isPlayingVideo}")
        if (holder.isMotionPhoto && !holder.isPlayingVideo) {
            val items = viewModel.items.value ?: return
            if (pos < items.size) {
                pagerAdapter.playMotionVideoPublic(holder, items[pos])
                motionVideoPlaying = true
            }
        }
    }

    // 切页或手势结束时停止当前页视频并释放播放器资源。
    private fun stopMotionVideoIfPlaying() {
        val rv = binding.viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView ?: return
        val pos = binding.viewPager.currentItem
        val holder = rv.findViewHolderForAdapterPosition(pos) as? ViewerPagerAdapter.ViewHolder
        pagerAdapter.stopVideoForCurrentPage(holder)
    }

    // 使用与进入一致的过渡动画结束 Activity。
    private fun finishWithAnim() {
        finish()
        overridePendingTransition(R.anim.no_anim, R.anim.viewer_exit)
    }

    // 更新底部文件名、日期、分辨率与体积等摘要信息。
    private fun updateInfo(item: MediaItem) {
        binding.tvFileName.text = item.displayName
        val dateFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
        val date = dateFormat.format(Date(item.dateAdded * 1000))
        val dimensions = if (item.width > 0) "${item.width}×${item.height}" else ""
        val parts = listOfNotNull(
            date,
            dimensions.ifEmpty { null },
            item.formattedSize
        )
        binding.tvFileMeta.text = parts.joinToString(" · ")
    }

    /** 释放适配器协程/播放器，并清空跨 Activity 传递的查看列表。 */
    override fun onDestroy() {
        super.onDestroy()
        pagerAdapter.release()
        SharedMediaData.clear()
    }

    companion object {
        private const val EXTRA_START_INDEX = "start_index"

        /**
         * 将列表写入 [SharedMediaData] 后启动查看器，[startIndex] 为初始页下标。
         */
        fun start(context: Context, items: List<MediaItem>, startIndex: Int = 0) {
            SharedMediaData.setViewerItems(items)
            context.startActivity(Intent(context, PhotoViewerActivity::class.java).apply {
                putExtra(EXTRA_START_INDEX, startIndex)
            })
        }
    }
}
