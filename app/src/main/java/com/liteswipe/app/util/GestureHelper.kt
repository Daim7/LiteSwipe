package com.liteswipe.app.util

import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2

/**
 * 识别纵向滑动手势：区分垂直拖拽过程与达到阈值后的上滑/下滑完成。
 */
class GestureHelper(
    private val view: View,
    private val listener: GestureListener
) {
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false

    /**
     * 分发触摸事件：记录起点、在移动中按角度触发纵向拖拽，在抬起时判定上/下滑或拖拽结束。
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                isDragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                val angle = Math.toDegrees(
                    atan2(abs(dy).toDouble(), abs(dx).toDouble())
                )
                if (angle > VERTICAL_THRESHOLD_ANGLE) {
                    listener.onVerticalDrag(dy, dx)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                val distance = abs(dy)
                val densityDp = distance / view.resources.displayMetrics.density
                val angle = Math.toDegrees(
                    atan2(abs(dy).toDouble(), abs(dx).toDouble())
                )

                if (angle > VERTICAL_THRESHOLD_ANGLE && densityDp > SWIPE_THRESHOLD_DP) {
                    if (dy < 0) {
                        listener.onSwipeUp()
                    } else {
                        listener.onSwipeDown()
                    }
                } else {
                    listener.onDragEnd()
                }
                return true
            }
        }
        return false
    }

    interface GestureListener {
        /** 手指在判定为纵向方向上的拖动过程中持续回调。 */
        fun onVerticalDrag(dy: Float, dx: Float)
        /** 向上滑动距离与角度满足阈值时触发。 */
        fun onSwipeUp()
        /** 向下滑动距离与角度满足阈值时触发。 */
        fun onSwipeDown()
        /** 未构成有效上/下滑时，手势结束回调。 */
        fun onDragEnd()
    }

    companion object {
        const val VERTICAL_THRESHOLD_ANGLE = 60.0
        const val SWIPE_THRESHOLD_DP = 100f
    }
}
