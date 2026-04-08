package com.liteswipe.app.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.liteswipe.app.R
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.util.gone
import com.liteswipe.app.util.visible
import com.liteswipe.app.util.visibleIf

/** 相册列表中的扁平项：日期分组标题或单条媒体（含多选/队列状态）。 */
sealed class GalleryListItem {
    data class Header(
        val dateKey: String,
        val label: String
    ) : GalleryListItem()

    data class Media(
        val item: MediaItem,
        val isSelected: Boolean = false,
        val isMultiSelectMode: Boolean = false,
        val isInQueue: Boolean = false
    ) : GalleryListItem()
}

/**
 * 分组相册的 RecyclerView 适配器：日期头与网格缩略图，支持多选、队列角标与可选双击。
 */
class MediaAdapter(
    private val onItemClick: (MediaItem, Int) -> Unit,
    private val onItemLongClick: (MediaItem) -> Unit,
    private val onCheckClick: (MediaItem) -> Unit,
    private val onGroupSelectAll: (String) -> Unit,
    private val onItemDoubleClick: ((MediaItem, Int) -> Unit)? = null,
    private val separateCheckAndView: Boolean = false
) : ListAdapter<GalleryListItem, RecyclerView.ViewHolder>(DiffCallback) {

    /** 区分日期头与媒体格两种视图类型。 */
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GalleryListItem.Header -> VIEW_TYPE_HEADER
            is GalleryListItem.Media -> VIEW_TYPE_MEDIA
        }
    }

    /** 按类型加载日期头或媒体格的布局并包装为 ViewHolder。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_date_header, parent, false)
            )
            else -> MediaViewHolder(
                inflater.inflate(R.layout.item_media_grid, parent, false)
            )
        }
    }

    /** 将对应位置的 Header 或 Media 数据绑定到 ViewHolder。 */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GalleryListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is GalleryListItem.Media -> (holder as MediaViewHolder).bind(item)
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.tvDateTitle)
        private val selectAllBtn: TextView = view.findViewById(R.id.btnSelectAll)

        /** 显示日期文案；多选模式下展示「全选该日」并回调日期键。 */
        fun bind(header: GalleryListItem.Header) {
            titleText.text = header.label
            val anyMultiSelect = currentList.any {
                it is GalleryListItem.Media && it.isMultiSelectMode
            }
            selectAllBtn.visibleIf(anyMultiSelect)
            selectAllBtn.setOnClickListener {
                onGroupSelectAll(header.dateKey)
            }
        }
    }

    inner class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        private val durationText: TextView = view.findViewById(R.id.tvDuration)
        private val liveBadge: TextView = view.findViewById(R.id.tvLiveBadge)
        private val checkIcon: View = view.findViewById(R.id.viewCheck)
        private val checkBg: View = view.findViewById(R.id.viewCheckBg)
        private val queueBadge: TextView = view.findViewById(R.id.tvQueueBadge)
        private var lastClickTime = 0L

        /** 加载缩略图与角标，并按模式处理单击、长按、勾选与双击查看。 */
        fun bind(media: GalleryListItem.Media) {
            val item = media.item

            Glide.with(thumbnail)
                .load(item.uri)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .placeholder(R.drawable.placeholder_image)
                .into(thumbnail)

            if (item.isVideo && item.formattedDuration.isNotEmpty()) {
                durationText.visible()
                durationText.text = item.formattedDuration
            } else {
                durationText.gone()
            }

            liveBadge.visibleIf(item.isMotionPhoto)

            checkIcon.visibleIf(media.isMultiSelectMode)
            checkBg.visibleIf(media.isMultiSelectMode)
            checkIcon.isSelected = media.isSelected
            checkBg.isSelected = media.isSelected
            queueBadge.visibleIf(media.isInQueue && !media.isMultiSelectMode)

            itemView.setOnClickListener {
                if (separateCheckAndView) {
                    onItemClick(item, allMediaItems().indexOf(item))
                } else if (media.isMultiSelectMode) {
                    val now = System.currentTimeMillis()
                    if (onItemDoubleClick != null && now - lastClickTime < 300) {
                        onItemDoubleClick.invoke(item, allMediaItems().indexOf(item))
                        lastClickTime = 0L
                    } else {
                        onCheckClick(item)
                        lastClickTime = now
                    }
                } else {
                    onItemClick(item, allMediaItems().indexOf(item))
                }
            }

            itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }

            checkIcon.setOnClickListener {
                onCheckClick(item)
            }
        }
    }

    /** 从当前列表提取全部媒体项，用于大图查看器按顺序浏览。 */
    fun allMediaItems(): List<MediaItem> {
        return currentList.filterIsInstance<GalleryListItem.Media>().map { it.item }
    }

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_MEDIA = 1

        val DiffCallback = object : DiffUtil.ItemCallback<GalleryListItem>() {
            /** 以日期键或媒体 id 判断是否为同一逻辑条目。 */
            override fun areItemsTheSame(old: GalleryListItem, new: GalleryListItem): Boolean {
                return when {
                    old is GalleryListItem.Header && new is GalleryListItem.Header ->
                        old.dateKey == new.dateKey
                    old is GalleryListItem.Media && new is GalleryListItem.Media ->
                        old.item.id == new.item.id
                    else -> false
                }
            }

            /** 比较条目全部字段，决定是否需要更新已绑定视图。 */
            override fun areContentsTheSame(old: GalleryListItem, new: GalleryListItem): Boolean {
                return old == new
            }
        }
    }
}
