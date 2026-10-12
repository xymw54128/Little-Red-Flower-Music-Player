# 小红花 · Little Red Flower Music Player

简约的本地音乐播放器。音乐保存在自己的设备上，支持离线播放、歌单管理与动态歌词。作者：**浪雨林兴云魔王**。

本仓库包含 Android 原生应用、iPhone 原生源码、网页 UI 原型和设计文档。原创源码采用 [MIT 许可证](LICENSE)；字体及第三方资源的授权见 [第三方说明](THIRD_PARTY_NOTICES.md)。

## 下载与安装

本地开发版 **Android 1.0.7 / code 14** 已完成本轮收藏管理、歌词与日常功能升级，暂不推送 GitHub。原工作区安装包为 `dist/小红花-1.0.7.apk`，见 [本地版本说明](dist/小红花-1.0.7-发布说明.md)。下方下载链接继续指向已公开的 1.0.5。

**[下载 Android 1.0.5 APK](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.0.5/Xiaohonghua-1.0.5.apk)** · [1.0.5 发布页面](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.0.5) · [发布说明](dist/小红花-1.0.5-发布说明.md) · [安装与换机说明](dist/安装说明.md)

源码与 Android 1.0.5 已公开发布，APK 和校验文件保存在 GitHub Releases；Git 克隆不包含安装包。历史版本见 [全部发布](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases)。

- Android 8.0 及以上，针对 Android 16 开发；支持 ARM64、ARMv7、x86、x86_64。
- 包含离线中文字体。下载后在手机文件管理器中打开 APK，按系统提示允许该文件管理器安装应用。
- Android 公开版本为 `1.0.5` / code `12`，本地开发版为 `1.0.7` / code `14`；继续沿用既有签名，可覆盖安装旧版小红花，建议更新前导出备份。
- 安装后在 **设置 → 歌曲文件夹** 导入音乐文件或授权音乐文件夹。
- 应用运行不需要网络权限；首次下载源码构建所需的工具和依赖需要联网。

APK SHA-256（[下载校验文件](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.0.5/Xiaohonghua-1.0.5.apk.sha256)）：

```text
6016a8c7b0a1ece85e37037b3f3859fea35da44ad9f6f4873ccd0c9b725bf086
```

**[下载 iOS 1.0.1 源码工程](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.0.1/Xiaohonghua-iOS-1.0.1-source.zip)** · [iOS 构建说明](ios/README.md)

iOS 暂按 iPhone / iOS 17+ 制作，目前提供源码，**没有可安装的 IPA**。已进行 Windows 端语法、工程引用和资源检查；尚未运行 Xcode 编译、XCTest、签名或 iPhone 真机验证。后续需要可用的 macOS + Xcode 环境完成这些步骤。

## 主要功能

### 1.0.7 使用入口

- 中文与英文直接搜索；Android 10 及以上也可输入完整拼音或首字母，如“dengziqi”“dzq”，Android 8/9 保留原词检索。覆盖音乐库、歌单、最近播放及批量选择；初次后台准备索引时有状态提示。多音字按系统 ICU 转写。
- 专辑按专辑歌手聚合，内部按盘号、曲号排序；没有专辑歌手标签时使用歌曲歌手。专辑播放队列与全库排序独立；原文件缺少标签时需自行补标签。
- 歌曲三个点菜单 → 下一首播放 / 追加队尾；已有歌曲移动，当前歌曲不重启，空队列加入保持暂停。队列显示实际随机顺序、来源、模式和当前位置；手动拖动排序后关闭随机模式。
- 设置 → 歌词样式 → 当前歌曲时间校准：±10 秒，每步 0.1 秒，正值延后、负值提前；预览即时变化，拖动松手保存，音频进度保持。
- 设置 → 睡眠计时，或播放封面页右上角月亮：15/30/45/60 分钟，到时暂停，可取消；页面退出继续，服务销毁后清除。
- 设置 → 主题颜色 → 夜间主题；保留主题色与花朵互动，系统栏、播放和弹层随深浅配色。
- 音乐库 → 最近播放，或设置 → 最近播放：实际开始播放才记录，最多 100 首，去重且最近在前，支持确认清空。记录、校准、夜间偏好都纳入 JSON 备份。
- 列表信息为“歌手 · 专辑名”，歌单卡片显示描述；歌曲信息中点击封面全屏查看，可捏合、双击缩放与拖动查看。
- 前台 15 秒、后台播放 60 秒增量扫描，停止播放并隐藏后停止监测；不周期强读全库。升级首次重新读取新专辑标签；手动刷新重读全部文件及外置 LRC，包含单独导入的文件。提供者不报告修改时间/大小时需手动刷新，未知 LRC 时间不使用缓存。


- 导入本地音乐文件与文件夹；播放 FLAC、MP3、WAV 等常见格式，读取标题、歌手、专辑、封面和歌词。
- 音乐库支持歌曲、专辑、歌手分类；右上角搜索图标向左展开搜索框，收起时让出列表空间；歌曲名 / 歌手 A–Z、随机、自定义拖动排序。
- 默认保留“我喜欢”歌单；点亮歌曲列表的白色描边爱心后变为红色并收藏，再点取消。普通歌单支持创建、编辑名称/封面/简介和删除，详情右上角菜单批量添加或移出音乐，添加歌曲时选择目标歌单；新增 10 款自然花型封面及“心之花”收藏封面，保留原 8 款图案和本地相册照片。
- 点击歌单封面立即播放该歌单，点击名称或卡片其他区域进入详情；详情与音乐库共用收起式搜索，封面/简介/数量在上划时收起、下划时展开。
- 自定义排序支持长列表边缘自动滚动、手柄菜单与无障碍移动、固定取消/保存；曲库更新保留排序草稿，保存后同步同一来源的播放队列，保留当前歌曲、进度、播放状态和循环模式，关闭该队列的随机播放。
- 音乐库、歌单、设置三个主页支持左右滑动切换，仍可点击底部导航。
- 迷你与完整播放器支持上一首、播放暂停、下一首、进度拖动、播放模式与队列调整。
- 点击播放页封面或当前歌词预览、向左滑动均进入歌词页，向右滑动返回封面；按 LRC 时间戳滚动和高亮，手动浏览结束 1.5 秒后平滑回位；主题色当前行、轻微放大与灰色非当前行，颜色与居中同步过渡，正常播放在下一行时间戳前完成；长短间隔及倍速自动调整提前窗口。
- 歌词字体、字号和行间距，主题色；封面播放页可调整背景照片的透明度、缩放和位置，歌词页跟随主页深浅背景，顶部显示“歌名 - 歌手”；导入歌词统一在设置中完成。
- 后台播放与系统媒体控制，文件夹刷新及前台变化监测，JSON 备份与迁移。
- 五瓣小红花启动动画；三个页头花朵各有可重复点击的 2 秒旋转、描线或花瓣归位互动，没有圆形涟漪底纹；歌曲列表滚动时提供当前歌曲定位按钮。
- 收藏、添加歌曲和新建歌单只保留控件与列表反馈，不弹成功提示。
- 统一轻按回弹、收藏弹动、排序拾起与归位、非阻塞完成提示；删除歌单或移除歌曲支持撤销。设置滑条本地预览、松手保存；新建歌单在选择面板内连续完成。

歌词实现与验收范围见 [1.0.5 歌词与互动说明](docs/design/1.0.5-flower-motion.md)。先前需求另附完整 [Cocos Creator 3.8 TypeScript 脚本及节点挂载说明](examples/cocos-lyrics/README.md)，Android 保持原生 Kotlin 实现；16 项 TypeScript 核心测试与官方引擎类型检查通过，Creator 编辑器及手机未运行。

各平台的格式、权限与系统行为存在差异，具体范围见 [Android 说明](android/README.md) 和 [iOS 说明](ios/README.md)。Android 当前没有独立隐藏系统锁屏控件的应用开关；JSON 备份不包含原音乐与照片。

## 工程与构建

| 目录 | 内容 |
| --- | --- |
| [`android/`](android/README.md) | Kotlin / Jetpack Compose / Media3 原生 Android 工程 |
| [`ios/`](ios/README.md) | SwiftUI / UIKit / AVFoundation 原生 iPhone 工程及测试 |
| [`ui/`](ui/README.md) | 可点击的网页 UI 原型，使用模拟播放时钟 |
| [`docs/design/`](docs/design/) | 版本设计与实现范围 |
| [`dist/`](dist/) | 发布说明、SHA-256、检查报告与资源预览；公开安装包在 GitHub Releases |
| [`examples/cocos-lyrics/`](examples/cocos-lyrics/README.md) | 独立 Cocos Creator 3.8 歌词脚本、节点说明与核心测试 |
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

Android 1.0.7（仅本地）：Release/Debug APK 与原生测试 APK 构建成功，95 项 JVM 单元测试通过，Android lint 为 0 错误、23 提醒；69 项原生测试编译成功。APK 的版本、包名、历史签名、资源、无调试/测试组件、无网络权限及 16 KB 对齐校验通过。当前 adb 设备列表为空，原生测试未执行，手机手势、后台计时、帧率及 100 GB 实际音乐库性能仍待验收。 [APK 报告](dist/小红花-1.0.7-CHECK.json) · [原生测试状态](dist/ANDROID-1.0.7-UI-CHECK.json)。

Android 1.0.6（仅本地）：Release/Debug APK 构建成功，71 项 JVM 单元测试通过，Android lint 为 0 错误、17 提醒；42 项原生测试编译成功。APK 的版本、包名、历史签名一致性、资源、无调试/测试组件、无网络权限及 16 KB 对齐校验通过。当前 adb 设备列表为空，原生测试未执行，未测量手机帧率；长列表拖动、收起/展开、快速操作与播放连续性仍需手机验收。 [APK 报告](dist/小红花-1.0.6-CHECK.json) · [UI 状态](dist/ANDROID-1.0.6-UI-CHECK.json)。

Android 1.0.5：Release/Debug APK 构建成功，59 项 JVM 单元测试通过，Android lint 为 0 错误、17 提醒；25 项原生界面测试编译成功。最终 APK 的包名、版本、签名、图标、离线字体、无调试/测试组件、无网络权限及 16 KB 对齐检查通过，签名与 1.0.4 及历史安装包一致。当前未连接 Android 设备，原生 UI 测试未执行，未测量手机帧率；歌词居中、手势和动画手感仍需小米 15 验收。 检查记录：[APK 报告](dist/小红花-1.0.5-CHECK.json) · [UI 执行状态](dist/ANDROID-1.0.5-UI-CHECK.json)。

历史开源准备期间，已在不含正式签名密钥的源码副本上成功构建 Debug 和未签名 Release，16 项 Debug 单元测试通过；核对了官方 1.0.1 APK 签名与原文件 SHA-256。该轮只调整开源构建和文档，未修改播放器运行功能。[开源构建检查记录](dist/OPEN-SOURCE-CHECK.json)。

Android 1.0.1 已有 Release 构建、16 项 JVM 测试、lint、签名及资源检查记录；原生 UI 测试已编译，尚未由代理在设备执行。1.0.0 已由用户验收，1.0.1 新动画与交互仍需手机验证。历史记录见 [Android 检查报告](dist/小红花-1.0.1-CHECK.json) 与 [发布说明](dist/小红花-1.0.1-发布说明.md)。

iOS 提供 20 项核心测试和 3 项界面测试，尚未执行；源码检查不能替代 Xcode 编译或实机测试。资源预览不是手机性能测量，也尚未完成 100 GB 实际音乐库的性能验证。

欢迎通过 [GitHub Issues](https://github.com/xymw54128/Little-Red-Flower-Music-Player/issues) 提交问题，请附手机型号、系统版本、应用版本和复现步骤；请勿附带私人音乐、密钥或备份中的个人信息。
