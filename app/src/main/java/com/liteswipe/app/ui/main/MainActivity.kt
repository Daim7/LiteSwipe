package com.liteswipe.app.ui.main

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.liteswipe.app.R
import com.liteswipe.app.receiver.StorageBroadcastReceiver
import com.liteswipe.app.ui.external.ExternalGalleryFragment
import com.liteswipe.app.ui.gallery.GalleryFragment
import com.liteswipe.app.ui.trash.TrashFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用主界面：底部导航在相册、导入与回收站之间切换，并响应外置存储挂载变化。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var storageReceiver: StorageBroadcastReceiver? = null

    private lateinit var bottomNav: BottomNavigationView

    private var galleryFragment: GalleryFragment? = null
    private var importFragment: ExternalGalleryFragment? = null
    private var trashFragment: TrashFragment? = null
    private var activeFragment: Fragment? = null

    companion object {
        private const val TAG_GALLERY = "gallery"
        private const val TAG_IMPORT = "import"
        private const val TAG_TRASH = "trash"
    }

    /** 初始化三 Tab 片段、边到边窗口内边距、底栏切换与存储广播。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNavigation)

        val container = findViewById<View>(R.id.fragmentContainer)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            galleryFragment = GalleryFragment()
            importFragment = ExternalGalleryFragment()
            trashFragment = TrashFragment()

            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, trashFragment!!, TAG_TRASH).hide(trashFragment!!)
                .add(R.id.fragmentContainer, importFragment!!, TAG_IMPORT).hide(importFragment!!)
                .add(R.id.fragmentContainer, galleryFragment!!, TAG_GALLERY)
                .commit()
            activeFragment = galleryFragment
        } else {
            galleryFragment = supportFragmentManager.findFragmentByTag(TAG_GALLERY) as? GalleryFragment
            importFragment = supportFragmentManager.findFragmentByTag(TAG_IMPORT) as? ExternalGalleryFragment
            trashFragment = supportFragmentManager.findFragmentByTag(TAG_TRASH) as? TrashFragment
            activeFragment = galleryFragment
        }

        bottomNav.setOnItemSelectedListener { item ->
            val target: Fragment? = when (item.itemId) {
                R.id.nav_gallery -> galleryFragment
                R.id.nav_import -> importFragment
                R.id.nav_trash -> trashFragment
                else -> null
            }
            if (target != null && target != activeFragment) {
                switchFragment(target)
            }
            true
        }

        viewModel.checkExternalStorage()
        registerStorageReceiver()
    }

    // 在已添加的 Fragment 间隐藏当前页、显示目标页，避免重复 add。
    private fun switchFragment(target: Fragment) {
        supportFragmentManager.beginTransaction().apply {
            activeFragment?.let { hide(it) }
            show(target)
        }.commit()
        activeFragment = target
    }

    /** 控制底部导航显隐，多选等全屏操作时由子页面调用以腾出布局空间。 */
    fun setBottomNavVisible(visible: Boolean) {
        bottomNav.visibility = if (visible) View.VISIBLE else View.GONE
    }

    // 监听存储挂载/卸载广播，通知 ViewModel 刷新外置存储状态。
    private fun registerStorageReceiver() {
        storageReceiver = StorageBroadcastReceiver { _, _ ->
            viewModel.onStorageChanged()
        }
        val filter = StorageBroadcastReceiver.getIntentFilter()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(storageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(storageReceiver, filter)
        }
    }

    /** 回到前台时再次校验外置存储可用性。 */
    override fun onResume() {
        super.onResume()
        viewModel.checkExternalStorage()
    }

    /** 销毁时注销存储广播接收器，避免泄漏与重复回调。 */
    override fun onDestroy() {
        super.onDestroy()
        try {
            storageReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
    }
}
