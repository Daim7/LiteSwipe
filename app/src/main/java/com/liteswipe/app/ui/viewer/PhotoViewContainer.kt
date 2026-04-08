package com.liteswipe.app.ui.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import com.github.chrisbanes.photoview.PhotoView
import com.liteswipe.app.R

/** 包裹大图 [PhotoView]，在缩放或多指操作时避免外层 ViewPager 等抢滑动手势。 */
class PhotoViewContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 不拦截事件，交由子 View（如 PhotoView）优先处理。 */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    /** 放大或多指时请求父布局不要拦截；抬起或取消后恢复，以便外层继续滑动翻页。 */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val photoView = findViewById<PhotoView>(R.id.photoView)
        if (ev.pointerCount > 1 || (photoView != null && photoView.scale > 1.05f)) {
            parent?.requestDisallowInterceptTouchEvent(true)
        } else if (ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(ev)
    }
}
