# 轻滑相册 (LiteSwipe) v1.0 软件开发文档 (SDD)

## 1. 项目概述
**轻滑相册 (LiteSwipe)** 是一款仿最新 iOS 相册交互体验的 Android 本地/外部媒体相册应用。
**核心特色**：极简 UI（Material Design 3 + iOS 风格）、手势驱动交互（跟手拖拽返回、上滑删除）、按日期智能分组、动态双指缩放调整网格列数，以及对外部存储（U盘/SD卡）的无缝支持。

## 2. 技术栈与架构
*   **开发语言**: Kotlin 1.9+
*   **系统支持**: minSdk 26 (Android 8.0) ~ targetSdk 34 (Android 14)
*   **架构模式**: MVVM (Model-View-ViewModel) 单向数据流
*   **依赖注入**: Dagger Hilt
*   **异步与并发**: Kotlin Coroutines + Flow
*   **核心第三方库**:
    *   图片加载：`Glide`
    *   大图缩放：`PhotoView` (通过自定义容器解决手势冲突)
    *   本地数据库：`Room` (用于回收站记录)
    *   后台任务：`WorkManager` (用于清理过期回收站图片)
    *   UI 组件：`ViewPager2` (左右滑动), `RecyclerView` (网格展示)

---

## 3. 项目目录结构解析
帮助你快速定位需要修改的代码文件。项目主要代码位于 `app/src/main/java/com/liteswipe/app/`：

```text
com.liteswipe.app
│
├── LiteSwipeApplication.kt       // 全局 Application，初始化 Hilt 和 WorkManager
│
├── data/                         // 数据层 (Model)
│   ├── db/                       // Room 数据库配置
│   │   ├── AppDatabase.kt        // 数据库声明
│   │   └── TrashDao.kt           // 回收站增删改查 SQL
│   ├── local/                    // 本地数据源
│   │   ├── ExternalStorageDataSource.kt // 外部存储(U盘)媒体扫描
│   │   └── MediaStoreDataSource.kt      // 手机本地媒体扫描
│   ├── model/                    // 数据实体类
│   │   ├── DateGroup.kt          // 媒体按日期分组实体
│   │   ├── MediaItem.kt          // 单个图片/视频实体
│   │   └── TrashItem.kt          // 回收站记录实体 (Room 表)
│   └── repository/               // 仓库层 (提供给 ViewModel 使用)
│       ├── ExternalMediaRepository.kt // 处理外部存储业务逻辑
│       └── MediaRepository.kt         // 处理本地媒体、删除、恢复逻辑
│
├── di/                           // 依赖注入模块
│   └── AppModule.kt              // Hilt 全局单例提供者 (DB, Repo 等)
│
├── receiver/                     // 广播接收器
│   └── StorageBroadcastReceiver.kt // 监听 U盘 插入/拔出事件
│
├── service/                      // 后台服务
│   └── TrashCleanupWorker.kt     // 每天自动清理超 30 天回收站照片的后台任务
│
├── ui/                           // UI 与 表现层 (View & ViewModel)
│   ├── main/
│   │   └── MainActivity.kt       // 唯一主 Activity，承载各个 Fragment
│   ├── gallery/                  // 本地相册模块
│   │   ├── GalleryFragment.kt    // 首页网格视图
│   │   ├── GalleryViewModel.kt   // 首页逻辑 (加载、多选、缩放)
│   │   └── MediaAdapter.kt       // 网格 RecyclerView 适配器
│   ├── external/                 // 外部存储相册模块
│   │   ├── ExternalGalleryFragment.kt
│   │   └── ExternalGalleryViewModel.kt
│   ├── trash/                    // 回收站模块
│   │   ├── TrashFragment.kt      
│   │   ├── TrashViewModel.kt
│   │   └── TrashAdapter.kt       // 回收站带有“剩余天数”的适配器
│   └── viewer/                   // 大图预览模块 (核心手势逻辑)
│       ├── PhotoViewerActivity.kt         // 本地相册预览 (含上滑删除、下滑返回)
│       ├── ExternalPhotoViewerActivity.kt // 外部相册预览 (含加入导入队列)
│       ├── PhotoViewerViewModel.kt
│       ├── ViewerPagerAdapter.kt          // ViewPager2 适配器
│       └── PhotoViewContainer.kt          // 自定义容器 (解决双指缩放与滑动冲突)
│
└── util/                         // 工具类
    ├── Extensions.kt             // Kotlin 扩展函数 (View可见性、Toast等)
    └── SharedMediaData.kt        // 解决 Activity 间传大量数据导致崩溃的内存单例
```

---

## 4. 核心功能实现原理与修改指南

### 4.1 手势交互：上滑删除 / 下滑返回
**所在文件**: `PhotoViewerActivity.kt` / `ExternalPhotoViewerActivity.kt` 的 `dispatchTouchEvent` 方法。
*   **原理**: 不使用普通的 `OnTouchListener`（会被内部的 ViewPager2 吞掉事件），而是重写 Activity 的事件分发机制。检测手指按下的 Y 轴和 X 轴位移，如果上下移动远大于左右移动，则接管事件，实时修改 `binding.viewPager` 的 `translationY` 和 `scale`。
*   **修改指南**:
    *   **想改触发删除的滑动距离**：修改 `TRIGGER_THRESHOLD_DP = 120f`。
    *   **想改跟手的缩放比例**：修改 `val scale = 1f - progress * 0.4f`。

### 4.2 双指缩放：相册网格列数切换
**所在文件**: `GalleryFragment.kt` 的 `setupScaleGesture()` 方法。
*   **原理**: 使用 `ScaleGestureDetector` 监听双指缩放。定义了一个累积缩放因子 `accumulatedScale`，当放大超过 1.15 倍或缩小低于 0.85 倍时，触发 ViewModel 更新列数（2, 3, 5, 7 之间切换）。
*   **修改指南**:
    *   **想改支持的列数**：进入 `GalleryViewModel.kt` 的 `adjustZoom` 方法，修改 `val cols = listOf(2, 3, 5, 7)`。
    *   **想改灵敏度**：修改 Fragment 中的 `1.15f` 和 `0.85f` 的阈值。

### 4.3 大图双指放大：解决与 ViewPager 的冲突
**所在文件**: `PhotoViewContainer.kt` 和 `item_viewer_page.xml`
*   **原理**: ViewPager2 会拦截水平滑动，导致放大图片后无法查看图片左右边缘的细节。我们创建了 `PhotoViewContainer` 包裹 `PhotoView`，并重写 `dispatchTouchEvent`。当检测到双指 (`pointerCount > 1`) 或图片处于放大状态 (`photoView.scale > 1.05f`) 时，调用 `requestDisallowInterceptTouchEvent(true)` 强行禁止外部的 ViewPager2 拦截事件。
*   **修改指南**: 如果发现某些极端尺寸图片平移有问题，可以微调 `1.05f` 的缩放容差。

### 4.4 页面传值导致崩溃 (TransactionTooLargeException)
**所在文件**: `SharedMediaData.kt`
*   **原理**: Android 规定 Activity 之间通过 Intent 传递的数据总量不能超过约 1MB (Binder 限制)。相册图片动辄成百上千张，传对象列表必然崩溃。
*   **解决方案**: 创建一个全局单例 `SharedMediaData`。打开查看器前，将图片列表存入单例：`SharedMediaData.setViewerItems(items)`。进入后取出数据，并在 Activity 销毁 (`onDestroy`) 时调用 `clear()` 释放内存。

### 4.5 删除与回收站机制 (适配 Android 10+ 存储机制)
**所在文件**: `MediaRepository.kt` 和 `TrashFragment.kt`
*   **原理**:
    *   **移入回收站 (上滑/删除)**: 不直接删除系统里的照片（因为 Android 10+ 的 Scoped Storage 限制，直接删除会抛异常或需要弹窗）。我们先将照片**复制**到应用的内部私有目录 `filesDir/trash/`，然后用 Room 数据库 (`TrashItem`) 记录这条“虚拟删除”信息。主页相册查询时，会**过滤掉**所有记录在 Room 里的图片。
    *   **彻底删除**: 用户在回收站点击“永久删除”时，针对 Android 11+，构建 `MediaStore.createDeleteRequest(uris)` 发起系统底层删除，这会弹出系统 UI 让用户确认。用户同意后，再清空数据库记录和私有目录的副本。
*   **修改指南**:
    *   **修改保留天数**：在 `TrashItem.kt` 中修改 `const val RETENTION_DAYS = 30`。

---

## 5. UI/主题说明
*   **深色模式支持**: 应用完全使用了 `DayNight` 主题，在 `res/values/colors.xml` 和 `res/values-night/colors.xml` 中分别定义了日间和夜间的颜色。
*   **圆角与间距**: 大量使用了类似 iOS 的大圆角(`12dp`~`16dp`)，背景色调采用了 `#F2F2F7` (日) 和 `#000000` (夜)。

## 6. 后续待完善建议 (TODOs)
1.  **视频播放**: 目前 `MediaItem` 实体已经支持 `duration` 和 `isVideo` 的判断，列表也会显示时长标签。但在大图中仅做了封面展示，后续可在此处嵌入 `ExoPlayer` 实现视频播放。
2.  **大图查看性能**: 传递超过一万张图片到 Viewer 时，ViewPager2 的初始化可能会有极短的卡顿。可考虑在 ViewModel 中实现分页加载 (Paging 3)。
3.  **回收站清理服务可靠性**: 目前使用 `WorkManager` (在 `LiteSwipeApplication` 中注册)。部分国产手机（如小米、华为）由于省电策略，可能会杀掉后台任务，导致 `TrashCleanupWorker` 无法准时每天运行。可以在 App 启动时额外在主线程做一次过期检测。