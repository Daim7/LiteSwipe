# 轻滑相册 LiteSwipe

一款仿 iOS 相册风格的 Android 本地相册应用，支持本地媒体浏览、外部存储导入、动态照片播放、视频播放和回收站管理。

## 功能特性

### 📸 本地图库
- 按日期分组的网格展示（支持图片和视频）
- 双指缩放手势切换网格列数（2/3/5/7 列）
- 长按进入多选模式，支持全选、日期分组全选
- 批量移入回收站

### 📥 外部存储导入
- 自动检测 USB/SD 卡等外部存储设备
- 独立的"导入"Tab 页，始终显示选择圆圈
- 点击图片打开全屏查看，点击圆圈加入待导入合集
- 全局待导入合集管理（跨页面持久化）
- 支持批量导入和批量删除

### 🖼 全屏查看器
- **本地查看器**: 左右滑动切换、上滑移入回收站（跟手动画）、下滑返回
- **外部查看器 (微信风格)**: 顶部计数器和选择按钮、底部待导入缩略图横滑列表、一键导入
- 动态照片长按播放、松手停止
- 视频播放器集成（ExoPlayer），沉浸式控制条

### 🗑 回收站
- 30 天自动清理机制
- 显示剩余天数覆盖层
- 多选恢复或永久删除

### 🎬 动态照片 (Motion Photo)
- 自动识别 Android Live Photo（XMP 元数据解析）
- 网格和查看器中显示 LIVE 标识
- 长按播放嵌入视频

### 🎥 视频播放
- 集成 ExoPlayer (Media3)
- 自定义沉浸式控制条（底部渐变，半透明播放按钮）
- 打开后暂停，点击播放

### 🌙 深色模式
- Material Design 3 DayNight 主题
- 跟随系统深色模式自动切换

### 📱 沉浸式体验
- Edge-to-edge 全屏显示
- 透明状态栏和导航栏
- 底部三栏导航（图库 / 导入 / 最近删除）

## 技术架构

| 层级 | 技术 |
|------|------|
| 架构模式 | MVVM (ViewModel + LiveData + Coroutines) |
| 语言 | Kotlin + XML |
| UI 框架 | Material Design 3 |
| 依赖注入 | Dagger Hilt |
| 图片加载 | Glide |
| 图片缩放 | PhotoView |
| 视频播放 | ExoPlayer (Media3) |
| 本地数据库 | Room |
| 后台任务 | WorkManager |
| 页面切换 | ViewPager2 |

## 项目结构

```
app/src/main/java/com/liteswipe/app/
├── data/
│   ├── db/           # Room 数据库 (AppDatabase, TrashDao)
│   ├── local/        # 数据源 (MediaStore, ExternalStorage)
│   ├── model/        # 数据模型 (MediaItem, TrashItem, DateGroup)
│   └── repository/   # 仓库层 (MediaRepository, ExternalMediaRepository)
├── di/               # Hilt 依赖注入模块
├── receiver/         # 广播接收器 (StorageBroadcastReceiver)
├── service/          # 后台服务 (TrashCleanupWorker)
├── ui/
│   ├── external/     # 导入页 (ExternalGalleryFragment, ImportQueueBottomSheet)
│   ├── gallery/      # 图库页 (GalleryFragment, MediaAdapter)
│   ├── main/         # 主界面 (MainActivity, MainViewModel)
│   ├── trash/        # 回收站 (TrashFragment, TrashAdapter)
│   └── viewer/       # 查看器 (PhotoViewerActivity, ExternalPhotoViewerActivity)
└── util/             # 工具类 (ImportQueueManager, MotionPhotoHelper, SharedMediaData)
```

## 构建要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- Gradle 8.x
- Android SDK 34 (compileSdk)
- 最低支持 Android 8.0 (API 26)

## 快速开始

1. 克隆仓库
```bash
git clone https://github.com/Daim7/LiteSwipe.git
```

2. 用 Android Studio 打开项目

3. 同步 Gradle 依赖

4. 连接 Android 设备或启动模拟器

5. 运行项目
```bash
./gradlew installDebug
```

## UI 原型

项目包含一个交互式 HTML 原型文件，可直接在浏览器中预览 UI 设计：

```
docs/prototype.html
```

## 文档

- [产品需求文档 (PRD)](docs/PRD.md)
- [软件设计文档 (SDD)](docs/SDD_v1.1.md)
- [开发调试过程总结](docs/开发调试过程总结.md)

## 许可证

MIT License
