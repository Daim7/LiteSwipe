package com.liteswipe.app.ui.trash

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.liteswipe.app.R
import com.liteswipe.app.data.model.TrashItem
import com.liteswipe.app.util.visibleIf
import java.io.File

/** 回收站列表展示用模型：条目数据、是否选中及是否处于多选模式。 */
data class TrashListItem(
    val item: TrashItem,
    val isSelected: Boolean = false,
    val isMultiSelectMode: Boolean = false
)

/** 回收站网格 [RecyclerView] 适配器：加载缩略图并区分单击与多选勾选。 */
class TrashAdapter(
    private val onItemClick: (TrashItem) -> Unit,
    private val onCheckClick: (TrashItem) -> Unit
) : ListAdapter<TrashListItem, TrashAdapter.ViewHolder>(DiffCallback) {

    /** 单项布局中的缩略图、遮罩、剩余天数与勾选控件引用。 */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val overlay: View = view.findViewById(R.id.trashOverlay)
        val daysText: TextView = view.findViewById(R.id.tvDaysRemaining)
        val checkIcon: View = view.findViewById(R.id.viewCheck)
    }

    /** 加载 [R.layout.item_trash_grid] 并创建 [ViewHolder]。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trash_grid, parent, false)
        return ViewHolder(view)
    }

    /** 绑定缩略图、剩余天数文案，并按多选模式处理点击与勾选显示。 */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listItem = getItem(position)
        val item = listItem.item

        val file = File(item.trashFilePath)
        Glide.with(holder.thumbnail)
            .load(file)
            .centerCrop()
            .placeholder(R.drawable.placeholder_image)
            .into(holder.thumbnail)

        holder.daysText.text = holder.itemView.context.getString(
            R.string.days_remaining, item.daysRemaining
        )

        holder.checkIcon.visibleIf(listItem.isMultiSelectMode)
        holder.checkIcon.isSelected = listItem.isSelected

        holder.itemView.setOnClickListener {
            if (listItem.isMultiSelectMode) {
                onCheckClick(item)
            } else {
                onItemClick(item)
            }
        }

        holder.checkIcon.setOnClickListener { onCheckClick(item) }
    }

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<TrashListItem>() {
            /** DiffUtil：是否同一数据库条目（按 id）。 */
            override fun areItemsTheSame(old: TrashListItem, new: TrashListItem) =
                old.item.id == new.item.id

            /** DiffUtil：条目数据与列表展示状态是否完全一致。 */
            override fun areContentsTheSame(old: TrashListItem, new: TrashListItem) =
                old == new
        }
    }
}
