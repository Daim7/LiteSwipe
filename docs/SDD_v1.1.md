# 轻滑相册 (LiteSwipe) v1.1 软件开发文档 (SDD)

> 更新日期：2026-04-08 | 基于 v1.0 文档扩展

## 1. 项目概述
**轻滑相册 (LiteSwipe)** 是一款仿最新 iOS 相册交互体验的 Android 本地/外部媒体相册应用。
**核心特色**：极简 UI（Material Design 3 + iOS 风格）、手势驱动交互（跟手拖拽返回、上滑删除）、按日期智能分组、动态双指缩放调整网格列数、外部存储无缝导入、动态照片(Motion Photo)播放、视频集成播放，以及 Edge-to-edge 沉浸式体验。

### v1.1 新增特性
- 底部三栏导航（图库 / 导入 / 最近删除）替代原有 Fragment 内 TabBar
- 导入页全面重设计：分离选择与查看行为，全局待导入合集
- 外部查看器微信风格重设计：顶部计数器+选择按钮，底部缩略图横滑列表
- 待导入合集 BottomSheet 管理页
- 动态照片 (Motion Photo) 识别与播放
- 视频文件集成播放（ExoPlayer/Media3 自定义控制条）
- Edge-to-edge 沉浸式 UI（透明状态栏/导航栏）

---

## 2. 技术栈与架构
*   **开发语言**: Kotlin 1.9+
*   **系统支持**: minSdk 26 (Android 8.0) ~ targetSdk 34 (Android 14)
*   **架构模式**: MVVM (Model-View-ViewModel) 单向数据流
*   **依赖注入**: Dagger Hilt
*   **异步与并发**: Kotlin Coroutines + Flow
*   **核心第三方库**:
    *   图片加载：`Glide`
    *   大图缩放：`PhotoView` (通过自定义容器解决手势冲突)
    *   视频播放：`ExoPlayer` (Media3) - 普通视频和动态照片嵌入视频
    *   本地数据库：`Room` (用于回收站记录)
    *   后台任务：`WorkManager` (用于清理过期回收站图片)
    *   UI 组件：`ViewPager2` (左右滑动), `RecyclerView` (网格展示), `BottomNavigationView` (底部导航)

---

## 3. 项目目录结构解析

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
│   │   ├── ExternalStorageDataSource.kt // 外部存储(U盘/SD卡)媒体扫描
│   │   └── MediaStoreDataSource.kt      // 手机本地媒体扫描 (含动态照片检测)
│   ├── model/                    // 数据实体类
│   │   ├── DateGroup.kt          // 媒体按日期分组实体
│   │   ├── MediaItem.kt          // 单个图片/视频实体 (含 isMotionPhoto 字段)
│   │   └── TrashItem.kt          // 回收站记录实体 (Room 表)
│   └── repository/               // 仓库层 (提供给 ViewModel 使用)
│       ├── ExternalMediaRepository.kt // 处理外部存储加载/导入/删除逻辑
│       └── MediaRepository.kt         // 处理本地媒体、删除、恢复逻辑
│
├── di/                           // 依赖注入模块
│   └── AppModule.kt              // Hilt 全局单例提供者 (DB, Repo 等)
│
├── receiver/                     // 广播接收器
│   └── StorageBroadcastReceiver.kt // 监听 U盘/SD卡 插入/拔出事件 (动态注册)
│
├── service/                      // 后台服务
│   └── TrashCleanupWorker.kt     // 每天自动清理超 30 天回收站照片的后台任务
│
├── ui/                           // UI 与表现层 (View & ViewModel)
│   ├── main/
│   │   ├── MainActivity.kt       // 唯一主 Activity，管理底部导航和 Fragment 切换
│   │   └── MainViewModel.kt      // 主 ViewModel，管理外部存储检测状态
│   ├── gallery/                  // 本地图库模块
│   │   ├── GalleryFragment.kt    // 首页网格视图
│   │   ├── GalleryViewModel.kt   // 首页逻辑 (加载、多选、缩放)
│   │   └── MediaAdapter.kt       // 网格 RecyclerView 适配器 (支持多选/分离模式)
│   ├── external/                 // 外部存储导入模块
│   │   ├── ExternalGalleryFragment.kt   // 导入 Tab 页 (始终显示选择圆圈)
│   │   ├── ExternalGalleryViewModel.kt  // 导入页逻辑
│   │   └── ImportQueueBottomSheet.kt    // 待导入合集 BottomSheet 管理页
│   ├── trash/                    // 回收站模块
│   │   ├── TrashFragment.kt      
│   │   ├── TrashViewModel.kt
│   │   └── TrashAdapter.kt       // 回收站带有"剩余天数"的适配器
│   └── viewer/                   // 大图预览模块 (核心手势逻辑)
│       ├── PhotoViewerActivity.kt         // 本地相册预览 (含上滑删除、下滑返回)
│       ├── ExternalPhotoViewerActivity.kt // 外部相册预览 (微信风格，含选择/导入)
│       ├── PhotoViewerViewModel.kt
│       ├── ExternalPhotoViewerViewModel.kt
│       ├── ViewerPagerAdapter.kt          // ViewPager2 适配器 (支持图片/视频/动态照片)
│       └── PhotoViewContainer.kt          // 自定义容器 (解决双指缩放与滑动冲突)
│
└── util/                         // 工具类
    ├── Extensions.kt             // Kotlin 扩展函数 (View可见性、Toast等)
    ├── GestureHelper.kt          // 通用手势检测辅助类
    ├── GridSpacingDecoration.kt  // RecyclerView 网格间距装饰器
    ├── ImportQueueManager.kt     // 全局待导入合集单例管理器
    ├── MotionPhotoHelper.kt      // 动态照片检测与视频提取工具
    └── SharedMediaData.kt        // 解决 Activity 间传大量数据导致崩溃的内存单例
```

---

## 4. 核心功能实现原理与修改指南

### 4.1 底部导航架构 (v1.1 新增)
**所在文件**: `MainActivity.kt`, `activity_main.xml`, `bottom_nav_menu.xml`

*   **原理**: 使用 `BottomNavigationView` 实现 iOS 风格底部三栏导航。Fragment 管理采用 `show/hide` 模式而非 `replace`，在 `onCreate` 中通过 `add().hide()` 初始化所有 Fragment，切换时通过 `switchFragment()` 方法 `show` 目标 Fragment 并 `hide` 当前活跃 Fragment。
*   **优势**: 保留 Fragment 视图状态，切换瞬间无重建开销。
*   **注意**: 使用 `onHiddenChanged()` 代替 `onResume()` 感知可见性变化。
*   **多选联动**: 当任意 Fragment 进入多选模式时，通过 `(activity as? MainActivity)?.setBottomNavVisible(false)` 隐藏底部导航栏。

### 4.2 手势交互：上滑删除 / 下滑返回
**所在文件**: `PhotoViewerActivity.kt` / `ExternalPhotoViewerActivity.kt` 的 `dispatchTouchEvent` 方法。
*   **原理**: 重写 Activity 的事件分发机制。检测手指按下的 Y 轴和 X 轴位移，如果上下移动远大于左右移动，则接管事件，实时修改 `binding.viewPager` 的 `translationY` 和 `scale`。
*   **修改指南**:
    *   **想改触发删除的滑动距离**：修改 `TRIGGER_THRESHOLD_DP = 120f`。
    *   **想改跟手的缩放比例**：修改 `val scale = 1f - progress * 0.4f`。

### 4.3 双指缩放：相册网格列数切换
**所在文件**: `GalleryFragment.kt` 和 `ExternalGalleryFragment.kt` 的 `setupScaleGesture()` 方法。
*   **原理**: 使用 `ScaleGestureDetector` 监听双指缩放。定义了一个累积缩放因子 `accumulatedScale`，当放大超过 1.15 倍或缩小低于 0.85 倍时，触发 ViewModel 更新列数（2, 3, 5, 7 之间切换）。
*   **修改指南**:
    *   **想改支持的列数**：进入 `GalleryViewModel.kt` 的 `adjustZoom` 方法，修改 `val cols = listOf(2, 3, 5, 7)`。
    *   **想改灵敏度**：修改 Fragment 中的 `1.15f` 和 `0.85f` 的阈值。

### 4.4 大图双指放大：解决与 ViewPager 的冲突
**所在文件**: `PhotoViewContainer.kt` 和 `item_viewer_page.xml`
*   **原理**: `PhotoViewContainer` 包裹 `PhotoView`，重写 `dispatchTouchEvent`。当检测到双指 (`pointerCount > 1`) 或图片处于放大状态 (`photoView.scale > 1.05f`) 时，调用 `requestDisallowInterceptTouchEvent(true)` 强行禁止外部的 ViewPager2 拦截事件。

### 4.5 导入页交互设计 (v1.1 重设计)
**所在文件**: `ExternalGalleryFragment.kt`, `MediaAdapter.kt`, `ImportQueueManager.kt`

*   **分离选择与查看**: `MediaAdapter` 新增 `separateCheckAndView` 参数。当为 `true` 时（导入页使用）：
    *   点击 **图片区域**：始终打开 `ExternalPhotoViewerActivity` 全屏查看
    *   点击 **选择圆圈**：将该项加入/移出 `ImportQueueManager` 全局待导入合集
    *   选择圆圈始终可见 (`isMultiSelectMode = true`)
*   **全局待导入合集**: `ImportQueueManager` 是一个 Kotlin `object` 单例，使用 `MutableLiveData<List<MediaItem>>` 存储。多个页面（导入页、外部查看器、BottomSheet）同时观察并保持同步。
*   **状态同步**: 导入网格中的项目通过 `isInQueue` 字段显示"待导入"徽章。`ExternalGalleryFragment.refreshList()` 根据 `ImportQueueManager` 实时更新。

### 4.6 外部查看器 - 微信风格 (v1.1 重设计)
**所在文件**: `ExternalPhotoViewerActivity.kt`, `activity_external_viewer.xml`, `item_selected_thumbnail.xml`

*   **顶部栏**: 返回按钮 (左)、当前位置/总数计数器 (中)、选择圆圈 (右)。选择圆圈点击切换当前图片在 `ImportQueueManager` 中的状态，绿色背景表示已选中。
*   **底部面板**: 水平可滑动的缩略图列表，显示 `ImportQueueManager` 中所有已选项。点击缩略图跳转到对应图片。右下角"导入"按钮。
*   **浮动叠层**: `ViewPager2` 在 XML 中最先声明（最底层渲染），顶部栏和底部面板声明在后面，自然浮于图片之上。
*   **`SelectedThumbAdapter`**: 内部 RecyclerView Adapter，显示缩略图并标记当前查看的项。

### 4.7 待导入合集管理页 (v1.1 新增)
**所在文件**: `ImportQueueBottomSheet.kt`, `bottom_sheet_import_queue.xml`, `item_queue_thumbnail.xml`

*   **实现**: `BottomSheetDialogFragment`，peek 高度为屏幕 60%。
*   **功能**: 网格展示所有待导入项，点击移除单项。顶部"清空"按钮和"导入全部"按钮。
*   **入口**: 导入页的"待导入"状态栏 和 外部查看器内。

### 4.8 动态照片 (Motion Photo) 支持 (v1.1 新增)
**所在文件**: `MotionPhotoHelper.kt`, `MediaStoreDataSource.kt`, `ViewerPagerAdapter.kt`

*   **检测**: 两级检测机制
    *   **首页快速检测** (`MediaStoreDataSource.queryImages`): 文件名启发式（"MVIMG_" 前缀、".MP." 包含等）
    *   **查看器精确检测** (`MotionPhotoHelper.isMotionPhoto`): 解析 XMP 元数据，支持 `Camera:MotionPhoto`、`GCamera:MicroVideo` 等多种标签
*   **视频提取** (`MotionPhotoHelper.extractVideo`): 
    *   优先使用 XMP 中的 `MicroVideoOffset` 字段定位视频数据偏移量
    *   回退方案：扫描文件尾部寻找 MP4 `ftyp` 签名
    *   提取的视频保存到应用缓存目录的临时文件
*   **播放交互** (`ViewerPagerAdapter`): 
    *   网格中显示 "LIVE" 橙色徽章
    *   查看器中长按开始播放嵌入视频（ExoPlayer），松手停止
    *   使用 Activity 级 `dispatchTouchEvent` 检测长按（避免与 PhotoView 手势冲突）

### 4.9 视频播放集成 (v1.1 新增)
**所在文件**: `ViewerPagerAdapter.kt`, `custom_player_controls.xml`, `item_viewer_page.xml`

*   **播放器**: ExoPlayer (Media3)，通过 `PlayerView` 展示
*   **控制条**: 自定义布局 `custom_player_controls.xml`：
    *   底部渐变背景 (`bg_player_controls_gradient.xml`)
    *   播放/暂停按钮在左侧，半透明无圆形背景
    *   进度条与播放按钮同一水平面
    *   所有控件同步出现/消失 (`controllerShowTimeoutMs = 3000`)
*   **行为**: 视频打开后暂停（`playWhenReady = false`），需点击播放按钮启动
*   **资源管理**: `ViewPager2.onPageChangeCallback` 中切页时暂停当前视频

### 4.10 页面传值 (TransactionTooLargeException)
**所在文件**: `SharedMediaData.kt`
*   **原理**: 创建全局单例 `SharedMediaData`。打开查看器前存入数据，进入后取出，`onDestroy` 时清除。

### 4.11 删除与回收站机制
**所在文件**: `MediaRepository.kt`, `TrashFragment.kt`, `TrashViewModel.kt`
*   **移入回收站**: 复制到 `filesDir/trash/`，Room 记录元数据，主页查询时过滤
*   **彻底删除**: Android 11+ 使用 `MediaStore.createDeleteRequest(uris)` 系统确认
*   **自动清理**: `TrashCleanupWorker` (WorkManager) 每天检测，清理超 30 天的记录
*   **修改保留天数**: `TrashItem.kt` 中 `const val RETENTION_DAYS = 30`

### 4.12 Edge-to-edge 沉浸式 UI (v1.1 新增)
**所在文件**: `MainActivity.kt`, `themes.xml`

*   **实现**: 
    *   `enableEdgeToEdge()` 在 `MainActivity.onCreate()` 中调用
    *   `themes.xml` 中 `android:statusBarColor` 和 `android:navigationBarColor` 设为 `@android:color/transparent`
    *   `android:windowTranslucentNavigation="false"` 和 `android:enforceNavigationBarContrast="false"`
*   **内容适配**:
    *   `fragmentContainer`: 通过 `ViewCompat.setOnApplyWindowInsetsListener` 添加顶部 padding（状态栏高度）
    *   `BottomNavigationView`: 通过 `ViewCompat.setOnApplyWindowInsetsListener` 添加底部 padding（导航栏高度）
*   **注意**: 各 Fragment 的 RecyclerView 需设置足够的 `paddingBottom` 以避免内容被底部栏遮挡

---

## 5. XML 布局关键文件

| 文件 | 说明 |
|------|------|
| `activity_main.xml` | 主布局：LinearLayout 包含 FrameLayout(Fragment容器) + BottomNavigationView |
| `fragment_gallery.xml` | 图库页：标题栏 + 选择横幅 + RecyclerView |
| `fragment_external_gallery.xml` | 导入页：标题栏 + 选择横幅 + 待导入状态栏 + RecyclerView + 底部操作栏 |
| `fragment_trash.xml` | 回收站：标题栏 + 选择横幅 + RecyclerView |
| `activity_photo_viewer.xml` | 本地查看器：ViewPager2(底层) + 返回按钮(浮动) + 信息栏(浮动) |
| `activity_external_viewer.xml` | 外部查看器：ViewPager2(底层) + 顶部栏(浮动) + 底部面板(浮动) |
| `item_media_grid.xml` | 网格单项：图片 + 视频时长 + LIVE徽章 + 待导入徽章 + 选择圆圈 |
| `item_viewer_page.xml` | 查看器单页：PhotoViewContainer + PlayerView + LIVE徽章 |
| `custom_player_controls.xml` | ExoPlayer 自定义控制条 |
| `bottom_sheet_import_queue.xml` | 待导入合集 BottomSheet |
| `bottom_nav_menu.xml` | 底部导航菜单定义 |

---

## 6. UI/主题说明
*   **深色模式支持**: `DayNight` 主题，`res/values/colors.xml` 和 `res/values-night/colors.xml` 分别定义日间和夜间颜色。
*   **iOS 风格**: 大圆角 (`12dp`~`16dp`)，背景色调 `#F2F2F7` (日) / `#000000` (夜)，蓝色主色 `#007AFF`。
*   **Edge-to-edge**: 透明状态栏和导航栏，内容延伸到系统栏区域，通过 WindowInsets 添加适当间距。

---

## 7. MediaAdapter 多模式说明 (v1.1 更新)

`MediaAdapter` 支持三种点击行为模式：

| 模式 | 触发条件 | 点击图片区域 | 点击选择圆圈 |
|------|----------|-------------|-------------|
| **标准模式** | `isMultiSelectMode = false` | 打开查看器 | N/A (圆圈隐藏) |
| **多选模式** | `isMultiSelectMode = true, separateCheckAndView = false` | 单击=切换选择，双击(300ms内)=打开查看器 | 切换选择 |
| **分离模式** | `separateCheckAndView = true` (导入页) | 始终打开查看器 | 加入/移出待导入合集 |

`GalleryListItem.Media` 数据类新增：
- `isInQueue: Boolean` - 是否在待导入合集中（控制"待导入"徽章显示）

---

## 8. 全局状态管理

| 单例 | 所在文件 | 用途 |
|------|----------|------|
| `ImportQueueManager` | `util/ImportQueueManager.kt` | 全局待导入合集，`MutableLiveData` 驱动多页面同步 |
| `SharedMediaData` | `util/SharedMediaData.kt` | Activity 间传递大量 MediaItem 列表（绕过 Binder 限制） |

---

## 9. 后续待完善建议

1.  **性能优化**: 图片列表超过万张时考虑 Paging 3 分页加载
2.  **国产 ROM 适配**: WorkManager 后台任务在小米/华为等设备上可能被省电策略杀掉，建议在 App 启动时额外做过期检测
3.  **SAF 权限**: 外部存储删除操作可能在某些设备上需要 SAF 授权
4.  **动态照片兼容性**: 不同厂商 Motion Photo 格式持续变化，需关注新设备的 XMP 标签
5.  **视频编辑**: 可考虑加入视频裁剪、旋转等功能
6.  **云同步**: 可接入 WebDAV 或其他云存储实现照片同步
7.  **相册分类**: 按照片类型（截图、全景、慢动作等）自动分类
