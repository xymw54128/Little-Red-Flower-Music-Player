# 小红花 · Little Red Flower Music Player

简约的本地音乐播放器。音乐保存在自己的设备上，支持离线播放、歌单管理与动态歌词。作者：**浪雨林兴云魔王**。

本仓库包含 Android 原生应用、iPhone 原生源码、网页 UI 原型和设计文档。原创源码采用 [MIT 许可证](LICENSE)；字体及第三方资源的授权见 [第三方说明](THIRD_PARTY_NOTICES.md)。

## 下载与安装

**[下载 Android 1.0.1 APK](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.0.1/Xiaohonghua-1.0.1.apk)** · [发布页面](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.0.1) · [安装与换机说明](dist/安装说明.md)

- Android 8.0 及以上，针对 Android 16 开发；支持 ARM64、ARMv7、x86、x86_64。
- 约 44 MB / 41.70 MiB，包含离线中文字体。下载后在手机文件管理器中打开 APK，按系统提示允许该文件管理器安装应用。
- 官方 1.0.1 APK 沿用既有签名，可覆盖安装旧版小红花；建议更新前导出备份。
- 安装后在音乐库右上角 `+` 导入音乐文件或授权音乐文件夹。
- 应用运行不需要网络权限；首次下载源码构建所需的工具和依赖需要联网。

APK SHA-256（[校验文件](dist/小红花-1.0.1.apk.sha256)）：

```text
fd2c001a683fa80b3ba80404c6f182f3bb83c096921325db8844cc392c22cacf
```

**[下载 iOS 1.0.1 源码工程](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.0.1/Xiaohonghua-iOS-1.0.1-source.zip)** · [iOS 构建说明](ios/README.md)

iOS 暂按 iPhone / iOS 17+ 制作，目前提供源码，**没有可安装的 IPA**。已进行 Windows 端语法、工程引用和资源检查；尚未运行 Xcode 编译、XCTest、签名或 iPhone 真机验证。后续需要可用的 macOS + Xcode 环境完成这些步骤。

## 主要功能

- 导入本地音乐文件与文件夹；播放 FLAC、MP3、WAV 等常见格式，读取标题、歌手、专辑、封面和歌词。
- 音乐库支持歌曲、专辑、歌手分类及搜索；歌曲名 / 歌手 A–Z、随机、自定义拖动排序。
- 创建、重命名和删除歌单，添加歌曲时选择目标歌单；多种封面及本地相册照片。
- 迷你与完整播放器支持上一首、播放暂停、下一首、进度拖动、播放模式与队列调整。
- 播放页横向切换封面与歌词；按 LRC 时间戳滚动和高亮，手动浏览后恢复当前歌词位置。
- 歌词字体、字号和行间距，主题色，播放背景照片的透明度、缩放和位置。
- 后台播放与系统媒体控制，文件夹刷新及前台变化监测，JSON 备份与迁移。
- 五瓣小红花启动动画；歌曲列表滚动时提供当前歌曲定位按钮。

各平台的格式、权限与系统行为存在差异，具体范围见 [Android 说明](android/README.md) 和 [iOS 说明](ios/README.md)。Android 当前没有独立隐藏系统锁屏控件的应用开关；JSON 备份不包含原音乐与照片。

## 工程与构建

| 目录 | 内容 |
| --- | --- |
| [`android/`](android/README.md) | Kotlin / Jetpack Compose / Media3 原生 Android 工程 |
| [`ios/`](ios/README.md) | SwiftUI / UIKit / AVFoundation 原生 iPhone 工程及测试 |
| [`ui/`](ui/README.md) | 可点击的网页 UI 原型，使用模拟播放时钟 |
| [`docs/design/`](docs/design/) | 版本设计与实现范围 |
| [`dist/`](dist/) | 发布说明、SHA-256、检查报告与资源预览；安装包在 GitHub Releases |
| [`tools/`](tools/) | 原工作区的构建、打包与资源检查助手 |

### Android

在 Android Studio 打开 `android/`，配置 JDK 17 与 Android SDK 36。也可进入该目录运行：

```sh
# macOS / Linux
bash gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

```powershell
# Windows
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug APK 位于 `android/app/build/outputs/apk/debug/`。公开源码不包含正式版签名密钥；缺少本地密钥时，Debug 使用 Android 标准开发签名，Release 生成未签名包，需配置自己的签名后分发。自行构建的不同签名安装包不能覆盖官方 APK。

`tools/` 中的 Windows 构建助手依赖原工作区的 `.toolchain/` 配置；新克隆项目可直接使用 Android Studio / Gradle Wrapper。

### iPhone

在 Mac 的 Xcode 16 或更新版本中打开 `ios/Xiaohonghua.xcodeproj`，安装 iPhone 模拟器后进入 `ios/` 运行：

```sh
bash tools/verify-mac.sh
```

脚本运行核心与界面测试，并构建设备 Release；安装到 iPhone 还需设置自己的 Apple 开发团队并签名。详细步骤见 [iOS README](ios/README.md)。

### 网页原型

直接打开 `ui/gallery.html` 查看页面总览，或 `ui/index.html` 操作原型。原型不承担正式音频解码；文件夹授权等能力建议通过本地 HTTP 服务预览：

```sh
python -m http.server 49173 --bind 127.0.0.1 --directory ui
```

## 验证状态与反馈

开源准备期间，已在不含正式签名密钥的源码副本上成功构建 Debug 和未签名 Release，16 项 Debug 单元测试通过；核对了官方 APK 签名与原文件 SHA-256。此次只调整开源构建和文档，未修改播放器运行功能。[开源构建检查记录](dist/OPEN-SOURCE-CHECK.json)。

Android 1.0.1 已有 Release 构建、16 项 JVM 测试、lint、签名及资源检查记录；原生 UI 测试已编译，尚未由代理在设备执行。1.0.0 已由用户验收，1.0.1 新动画与交互仍需手机验证。历史记录见 [Android 检查报告](dist/小红花-1.0.1-CHECK.json) 与 [发布说明](dist/小红花-1.0.1-发布说明.md)。

iOS 提供 20 项核心测试和 3 项界面测试，尚未执行；源码检查不能替代 Xcode 编译或实机测试。资源预览不是手机性能测量，也尚未完成 100 GB 实际音乐库的性能验证。

欢迎通过 [GitHub Issues](https://github.com/xymw54128/Little-Red-Flower-Music-Player/issues) 提交问题，请附手机型号、系统版本、应用版本和复现步骤；请勿附带私人音乐、密钥或备份中的个人信息。
