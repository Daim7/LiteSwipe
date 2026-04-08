package com.liteswipe.app.ui.external

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.liteswipe.app.R
import com.liteswipe.app.data.model.MediaItem
import com.liteswipe.app.databinding.BottomSheetImportQueueBinding
import com.liteswipe.app.util.ImportQueueManager

/** 以底部表展示待导入缩略图，支持单项移除、清空与触发全部导入。 */
class ImportQueueBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetImportQueueBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: QueueAdapter

    /** 用户点击「导入全部」时回调，由宿主执行实际导入并可在回调内 dismiss。 */
    var onImportAll: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetImportQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    /** 绑定网格适配器、观察队列变化并连接清空/全部导入按钮。 */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = QueueAdapter { item ->
            ImportQueueManager.remove(item)
        }
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.recyclerView.adapter = adapter

        ImportQueueManager.queue.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items.toList())
            binding.tvTitle.text = "待导入 (${items.size})"
            binding.btnImportAll.text = "导入全部 (${items.size})"
            val hasItems = items.isNotEmpty()
            binding.recyclerView.visibility = if (hasItems) View.VISIBLE else View.GONE
            binding.tvHint.visibility = if (hasItems) View.VISIBLE else View.GONE
            binding.btnImportAll.visibility = if (hasItems) View.VISIBLE else View.GONE
            binding.tvEmpty.visibility = if (hasItems) View.GONE else View.VISIBLE
        }

        binding.btnClearAll.setOnClickListener {
            ImportQueueManager.clear()
        }

        binding.btnImportAll.setOnClickListener {
            onImportAll?.invoke()
            dismiss()
        }
    }

    /** 提高 peek 高度并设为展开，便于一次看到更多待导入项。 */
    override fun onStart() {
        super.onStart()
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.6).toInt()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ImportQueueBottomSheet"
    }
}

/** 导入队列缩略图网格：点击单项即从队列移除。 */
private class QueueAdapter(
    private val onRemove: (MediaItem) -> Unit
) : ListAdapter<MediaItem, QueueAdapter.VH>(DiffCb) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
    }

    /** 加载队列项单元格布局。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue_thumbnail, parent, false)
        return VH(view)
    }

    /** 展示缩略图，点击触发移除回调。 */
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        Glide.with(holder.thumbnail)
            .load(item.uri)
            .centerCrop()
            .placeholder(R.drawable.placeholder_image)
            .into(holder.thumbnail)

        holder.itemView.setOnClickListener { onRemove(item) }
    }

    companion object DiffCb : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(a: MediaItem, b: MediaItem) = a.id == b.id
        override fun areContentsTheSame(a: MediaItem, b: MediaItem) = a == b
    }
}
