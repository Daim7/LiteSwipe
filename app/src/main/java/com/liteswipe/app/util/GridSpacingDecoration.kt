package com.liteswipe.app.util

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 为网格布局子项计算外边距，使列间距均匀；占满整行的项仅保留上下半间距。
 */
class GridSpacingDecoration(
    private val spacing: Int
) : RecyclerView.ItemDecoration() {

    /**
     * 按 span 位置设置 left/right/top/bottom，避免网格项挤在一起。
     */
    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
    ) {
        val lm = parent.layoutManager as? GridLayoutManager ?: return
        val params = view.layoutParams as GridLayoutManager.LayoutParams
        val spanSize = params.spanSize
        val spanCount = lm.spanCount

        if (spanSize == spanCount) {
            outRect.set(0, spacing / 2, 0, spacing / 2)
            return
        }

        val spanIndex = params.spanIndex
        outRect.left = spacing - spanIndex * spacing / spanCount
        outRect.right = (spanIndex + 1) * spacing / spanCount
        outRect.top = spacing / 2
        outRect.bottom = spacing / 2
    }
}
