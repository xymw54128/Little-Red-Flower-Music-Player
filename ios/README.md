# 小红花 · iPhone 1.0.1

作者：浪雨林兴云魔王。原生 SwiftUI / UIKit / AVFoundation 工程，最低 iOS 17，iPhone 竖屏布局。版本 1.0.1，构建号 8，Bundle ID `com.xiaohonghua.music.ios`。

原创源码采用 [MIT 许可证](LICENSE)，内置字体保留各自的 [原始 OFL 授权](THIRD_PARTY_NOTICES.md)。

**交付的是完整源代码工程，目前没有可安装的 IPA。** 当前开发电脑为 Windows，用户没有 Mac；Swift 语法和资源检查已运行，但没有运行 Xcode 编译、XCTest、模拟器、签名或 iPhone 测试。不能据此认定已达到正式发布质量。

## 已实现的代码

- 本地文件、多选歌曲和递归文件夹导入；持久化授权书签，失效时重新授权；应用自身 `Documents/Music` 文件共享目录。
- FLAC / MP3 标签与封面、系统其他音频元数据、内嵌歌词和同名 `.lrc`，也可单独导入歌词。原生播放 FLAC、MP3、WAV、M4A、AAC、AIFF、CAF 等常用格式；解码失败显示原因。
- 歌曲、专辑、歌手与搜索；列表序号、顶部/底部数量；标题 A–Z、歌手 A–Z、随机和可拖动保存的自定义顺序。
- 创建、重命名、删除歌单、选择目标歌单添加、移除歌曲；八款不同封面和相册照片封面。
- 迷你/完整播放页的上一首、播放暂停、下一首；四种播放模式；可拖动/移除的播放队列；细进度条；`歌手 · 专辑` 副标题。
- 横向分页进入居中歌词；LRC offset、多时间戳、同时间翻译合并；普通无时间戳歌词也可浏览。独立 CADisplayLink 根据相邻时间戳与显示刷新间隔提前完成位移，实际高亮按歌词时间切换。手动查看后四秒回到当前行。
- 列表滚动时显示当前歌曲定位按钮，停止滚动两秒后渐出；仅定位当前列表中的歌曲。
- 六种歌词字体、字号和行距、动态滚动开关；五种主题色；相册/专辑背景、不透明度、大小及水平/垂直位置。
- 后台音频、Now Playing、锁屏远程指令、来电中断与耳机拔出暂停；队列与位置保存，重开保持暂停。
- 文件夹手动刷新与前台静默监测；五秒检查文件增删改，六十秒重读标签。自动检查不显示顶部进度条。封面缓存按图片内容更新。
- 黄色花心、五瓣依次长出、旋转飞到音乐库标题旁及文楷文案渐入渐出的冷启动动画；尊重减少动态效果。动画使用独立绘制层，固定画布上的旋转、缩放、位移。
- JSON 备份/恢复；关于页和作者署名；三款未经修改的中文字体、原始 OFL 授权与来源信息。

## iOS 与 Android 的差异

通过系统“文件”选择器授予访问权限，应用不能扫描 iPhone 全盘。选中文件/文件夹不会再复制完整音乐库；同名歌词只在授权目录内可读，单独选择一首歌后如不能读旁边的 LRC，请再导入歌词或选择整个文件夹。

来自 iCloud 或其他文件提供商的音乐必须先下载到本机，才能满足离线播放。系统授权可能被提供商撤销，文件移走或删除后需重新授权。应用内 `Music` 目录最稳定。前台定时监测不等于应用关闭后仍扫描；iOS 在后台限制普通任务，持续音频播放由系统音频能力提供。

锁屏开关控制应用发布播放信息和接收系统指令；卡片显示、移除时机由 iOS 决定。当前无 OGG/Opus/DSD 等额外解码库，不将 Android 的全部格式支持直接照搬为 iOS 声明。

备份不包含原音乐、相册封面、背景图片和系统授权书签。换机时先导入原音乐，再恢复备份，以唯一的文件名和大小匹配。当前备份格式为 `xiaohonghua-ios` 版本 1，暂不直接读取 Android 私有路径备份。

## 在 Mac 上验证

需要完整 Xcode 16 或更新版本、安装 iOS 17+ 的 iPhone 模拟器。资源已包含，不需要 CocoaPods、XcodeGen 或第三方播放器库。

```sh
cd "/解压位置/小红花-iOS-1.0.1"
open Xiaohonghua.xcodeproj
bash tools/verify-mac.sh
```

脚本先运行纯核心 Swift Package 测试，再运行模拟器核心/界面测试，最后构建未签名的设备 Release。测试包含真实合成 FLAC（16 bit、24 bit / 96 kHz）和 WAV 的原生解码准备；测试音频只在测试 target 中打包。若机器有多个模拟器，可设 `IOS_DESTINATION='platform=iOS Simulator,name=具体模拟器名'`。首次需要在 Xcode 的 Settings > Locations 选择 Command Line Tools。

共提供 **20 项核心测试、3 项界面测试**；“提供”不代表已经执行。测试结果写入 `verification/mac/*.xcresult`。测试用数据与启动跳过入口只编译进 Debug，Release 不包含这些入口。界面测试应使用专用的干净模拟器。

## 在 iPhone 上安装

拿到 Mac 构建环境后，在 Xcode 的 Xiaohonghua target > Signing & Capabilities 选择实际 Apple 开发团队，将 Bundle ID 改成该团队可用的唯一值，连接 iPhone 并选中设备运行。准备正式分发时使用 Product > Archive，再按实际 Apple 账号及分发方式签名导出。工程没有伪造证书或预填团队。

如果暂时没有 Mac，先保留本源码包。借用/远程使用真实 Mac 构建环境也可以继续这一阶段；Windows 上不能通过改 APK 后缀得到 iOS 安装包。工程随完整项目开源，源码压缩包见 [GitHub Release](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.0.1)。目前未申请 Apple 开发账号、购买云构建服务或发布 App Store。

## 当前检查与待验收

`verification/SWIFT-SYNTAX.json`：独立 tree-sitter Swift 语法解析，不能检查 Apple SDK 类型或链接。

`verification/PROJECT-CHECK.json`：独立 OpenStep 解析器核对 Xcode 工程格式/引用、scheme、版本、资源、字体哈希/字体名/中文字形、隐私清单 XML、图标透明度和测试发现数。App 图标为不透明 1024 像素，页面花朵为透明图像。

仍需运行 Mac 脚本修复编译/测试反馈，并在 iPhone 上检验文件授权重启、FLAC 解码/后台切歌/锁屏、播放中断、密集歌词、拖动与定位、照片导入、启动动画帧率、SE 至 Pro Max 的安全区域及 100 GB 音乐库的耗时/内存。这些目前均未完成实机验证；不能承诺已经无卡顿。

已开启 [ProMotion 的帧率请求配置](https://developer.apple.com/documentation/quartzcore/optimizing-iphone-and-ipad-apps-to-support-promotion-displays)。实际刷新率仍由系统温度、电量与调度决定，不能承诺恒定 120 fps；比一个显示帧还密集的歌词时间戳也无法逐个可见。

维护工程的资源生成脚本依赖同工作区内的 Android 原始矢量和字体，只用于本工作区重新生成资源。解压包中的 Xcode 工程已经含有最终资源，构建不需要这些生成脚本。Windows 的 `host-python.cjs` 是当前工作区工具入口；其他主机可自行装 `tools/requirements-host.txt`，直接运行 Python 检查脚本。

Apple SDK 参考：[文件导入授权](https://developer.apple.com/documentation/swiftui/view/fileimporter(ispresented:allowedcontenttypes:allowsmultipleselection:oncompletion:))、[本地音频播放器](https://developer.apple.com/documentation/avfaudio/avaudioplayer)、[FLAC 格式](https://developer.apple.com/documentation/coreaudiotypes/kaudioformatflac)、[后台音频](https://developer.apple.com/documentation/avfoundation/configuring-your-app-for-media-playback)、[所用文件元数据 API 的隐私声明](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype)。
