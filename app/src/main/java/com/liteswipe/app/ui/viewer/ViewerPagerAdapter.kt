package com.liteswipe.app.ui.viewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import com.liteswipe.app.R
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.util.MotionPhotoHelper
import kotlinx.coroutines.*

/**
 * 查看器每一页：图片用 Glide、视频用 ExoPlayer；实况在后台检测并缓存，支持长按提取播放。
 */
class ViewerPagerAdapter(
    private var items: List<MediaItem>
) : RecyclerView.Adapter<ViewerPagerAdapter.ViewHolder>() {

    private val motionPhotoCache = mutableMapOf<Long, Boolean>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 单页视图引用与播放器、实况检测相关的可变状态。 */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photoView: PhotoView = view.findViewById(R.id.photoView)
        val playerView: PlayerView = view.findViewById(R.id.playerView)
        val liveBadge: TextView = view.findViewById(R.id.tvLiveBadge)
        var player: ExoPlayer? = null
        var isMotionPhoto = false
        var isPlayingVideo = false
        var detectionJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_viewer_page, parent, false)
        return ViewHolder(view)
    }

    /**
     * 按项类型展示图片或视频；实况在元数据未知时异步检测并更新角标，已缓存则直接复用。
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        stopVideo(holder)
        holder.liveBadge.visibility = View.GONE
        holder.isMotionPhoto = false

        if (item.isVideo) {
            holder.photoView.visibility = View.GONE
            holder.playerView.visibility = View.VISIBLE
            setupVideoPlayer(holder, item)
            return
        }

        holder.photoView.visibility = View.VISIBLE
        holder.playerView.visibility = View.GONE
        Glide.with(holder.photoView)
            .load(item.uri)
            .into(holder.photoView)

        if (item.isImage) {
            if (item.isMotionPhoto) {
                holder.isMotionPhoto = true
                holder.liveBadge.visibility = View.VISIBLE
            } else {
                val cached = motionPhotoCache[item.id]
                if (cached != null) {
                    holder.isMotionPhoto = cached
                    holder.liveBadge.visibility = if (cached) View.VISIBLE else View.GONE
                } else {
                    holder.detectionJob?.cancel()
                    holder.detectionJob = scope.launch {
                        val isMotion = withContext(Dispatchers.IO) {
                            MotionPhotoHelper.isMotionPhoto(
                                holder.itemView.context.contentResolver, item.uri
                            )
                        }
                        motionPhotoCache[item.id] = isMotion
                        holder.isMotionPhoto = isMotion
                        if (isMotion) {
                            holder.liveBadge.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    // 为普通视频页创建 ExoPlayer，默认不自动播放以便用户用控制器操作。
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun setupVideoPlayer(holder: ViewHolder, item: MediaItem) {
        val context = holder.itemView.context
        val player = ExoPlayer.Builder(context).build()
        holder.player = player
        holder.playerView.player = player
        holder.playerView.useController = true
        holder.playerView.controllerAutoShow = true
        holder.playerView.controllerShowTimeoutMs = 3000
        holder.playerView.setShowNextButton(false)
        holder.playerView.setShowPreviousButton(false)
        holder.isPlayingVideo = true

        val mediaItem = ExoMediaItem.fromUri(item.uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = false
    }

    /** 由 Activity 长按等场景调用，对指定 [holder] 播放 [item] 的实况内嵌视频。 */
    fun playMotionVideoPublic(holder: ViewHolder, item: MediaItem) {
        playMotionVideo(holder, item)
    }

    // 从实况文件中解出视频临时文件，切换为 PlayerView 全屏循环播放。
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun playMotionVideo(holder: ViewHolder, item: MediaItem) {
        android.util.Log.d("MotionPlay", "playMotionVideo called for ${item.displayName}")
        scope.launch {
            val videoFile = MotionPhotoHelper.extractVideo(
                holder.itemView.context, item.uri
            )
            android.util.Log.d("MotionPlay", "extractVideo result: ${videoFile?.absolutePath ?: "NULL"}")
            if (videoFile == null) return@launch

            holder.isPlayingVideo = true
            val context = holder.itemView.context
            val player = ExoPlayer.Builder(context).build()
            holder.player = player
            holder.playerView.player = player

            holder.playerView.useController = false
            holder.playerView.visibility = View.VISIBLE
            holder.photoView.visibility = View.INVISIBLE

            val mediaItem = ExoMediaItem.fromUri(
                android.net.Uri.fromFile(videoFile)
            )
            player.setMediaItem(mediaItem)
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.prepare()
            player.play()
        }
    }

    /** 在切页或界面需要时停止并释放 [holder] 上的播放器（可空以防尚未绑定）。 */
    fun stopVideoForCurrentPage(holder: ViewHolder?) {
        holder?.let { stopVideo(it) }
    }

    // 停止并释放 ExoPlayer，恢复 PhotoView 可见、隐藏控制器。
    private fun stopVideo(holder: ViewHolder) {
        holder.player?.let { player ->
            player.stop()
            player.release()
        }
        holder.player = null
        holder.playerView.visibility = View.GONE
        holder.photoView.visibility = View.VISIBLE
        holder.isPlayingVideo = false
    }

    /** 页被回收时取消未完成的实况检测并释放播放器，避免泄漏。 */
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.detectionJob?.cancel()
        stopVideo(holder)
    }

    override fun getItemCount() = items.size

    /** 替换数据（如查看器内删除当前项后）并全量刷新列表。 */
    fun updateItems(newItems: List<MediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** 取消协程作用域；应在宿主（如 Activity）销毁时调用。 */
    fun release() {
        scope.cancel()
    }
}
