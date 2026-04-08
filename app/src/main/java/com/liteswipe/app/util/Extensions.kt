package com.liteswipe.app.util

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/** 以短时 Toast 提示 [message]。 */
fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * 在当前视图上显示 Snackbar；可附带操作文案与点击回调。
 */
fun View.showSnackbar(
    message: String,
    actionText: String? = null,
    action: (() -> Unit)? = null,
    duration: Int = Snackbar.LENGTH_LONG
) {
    val snackbar = Snackbar.make(this, message, duration)
    if (actionText != null && action != null) {
        snackbar.setAction(actionText) { action() }
    }
    snackbar.show()
}

/** 将视图设为可见。 */
fun View.visible() { visibility = View.VISIBLE }
/** 将视图设为 GONE，不占布局空间。 */
fun View.gone() { visibility = View.GONE }
/** 将视图设为不可见但仍占位。 */
fun View.invisible() { visibility = View.INVISIBLE }

/** 根据条件在 VISIBLE 与 GONE 之间切换，便于绑定 UI 状态。 */
fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}
