# 小红花 · iPhone 1.1.1

作者：浪雨林兴云魔王。SwiftUI / UIKit / AVFoundation 原生 iPhone 工程，最低 iOS 17，竖屏；版本 1.1.1、构建号 21、Bundle ID `com.xiaohonghua.music.ios`。

**本版提供 Xcode 源码工程，没有可安装的 IPA。** 当前开发环境是 Windows，没有 Apple SDK。Swift 语法、工程引用和资源检查已执行；Xcode 编译、XCTest、模拟器、签名与 iPhone 运行均未执行，不能将其视为 App Store 或实机验收通过。

[下载 iOS 1.1.1 源码包](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.1.1/Xiaohonghua-iOS-1.1.1-source.zip) · [双端发布页](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.1.1) · [源代码许可](LICENSE) · [字体许可](THIRD_PARTY_NOTICES.md)

## 本版功能

- 原版 B「封面画廊」固定播放页：放大封面，优先留出一行歌词空间，长歌词滚动完整展示；点击封面/预览或左滑进入歌词，右滑返回。保留轻微呼吸、收藏、歌手与专辑、音质、更多操作和队列。进度左侧已播放时间、右侧完整时长。
- 默认“我喜欢”与列表爱心；歌单名称、描述、花朵/相册封面、批量添加和移出歌曲；点击歌单进入详情，点击歌曲播放；详情信息随列表上划收起。十一款封面沿用 Android 花朵资源。
- 音乐库、歌单收起式搜索，原文、拼音和首字母索引；专辑按专辑歌手区分并按盘号/曲号排列；分类选中填充主题色。自定义排序由原生 List 拖动，保存后同步同来源的队列，保留当前歌曲和进度。
- 主页保留系统底部导航，支持横向滑动松手切换；子页不触发该手势。它不是随手指连续拖动的自定义翻页器，具体过渡仍需 iPhone 验收。
- LRC 使用同一个显示时钟同步颜色、缩放与居中，在下一行时间戳前完成过渡，短间隔压缩；手动浏览停止 1.5 秒后回位。歌词页只保留歌曲标题与播放控制，导入和样式在设置中调整。
- 系统、文楷、宋体与五款已选多语言字体，共八项；五种主题色、夜间主题、字号和行距。三个页头花朵有可重触发的两秒互动，没有圆形底纹。
- 本地歌曲/递归文件夹导入，FLAC / MP3 标签与封面、内嵌和同名 LRC。WAV、M4A、AAC、AIFF、CAF 等常见格式由系统解码，失败有提示。
- 只在用户导入或手动刷新时扫描；刷新强制检查所有标签、封面、歌词与文件变化。取消自动文件夹监测、自定义播放背景、睡眠计时和最近播放入口。
- 曲库和文件处理置于后台，按 ID 合并去重；后台排序和搜索索引，封面有尺寸采样、24 MiB 缓存、双并发及离屏请求取消。播放进度独立保存，避免定时重写整份千首歌词曲库。
- 后台音频、锁屏指令、中断与耳机拔出暂停，重开保持暂停；原生媒体封面优先，缺图使用小红花。iOS 决定锁屏卡片及应用图标的实际布局，不能任意替换系统组件。
- JSON 备份/恢复，旧 1.0.1 数据默认值兼容；关于页保留作者及“春风桃李花开日，秋雨梧桐叶落时。”

## 导入、离线与换机

通过系统“文件”授权歌曲或文件夹，或向应用 Documents/Music 文件共享目录放入音乐。应用不能任意扫描 iPhone 全盘。iCloud/第三方文件提供商里的文件须先下载到本机才能离线播放；权限失效后需重新授权。单独导入歌曲时，系统可能不允许读取旁边的 LRC，可再导入歌词或授权整个文件夹。

备份包含索引、歌单、排序、手动歌词和设置，不包含原音乐、照片或系统授权书签。换机先导入音乐再恢复备份，按唯一文件名和大小匹配；备份格式为 xiaohonghua-ios / 1，不直接读取 Android 私有路径备份。当前不提供额外 OGG/Opus/DSD 解码器。

## 在 Mac 上验证和安装

需要 Xcode 16 或更新版本及 iOS 17+ 模拟器。资源已在工程中，不需要 CocoaPods 或 XcodeGen：

```sh
cd "/解压位置/小红花-iOS-1.1.1"
open Xiaohonghua.xcodeproj
bash tools/verify-mac.sh
```

脚本先执行 Swift Package 核心测试，再运行 iPhone 模拟器 XCTest，最后构建未签名设备 Release。可设 IOS_DESTINATION 指定模拟器。测试用 WAV / FLAC 仅打包进测试 target，调试夹具不编译进 Release；界面测试使用干净的专用模拟器。

当前共发现 39 项核心测试和 7 项界面测试，均未在此 Windows 环境执行。含千首去重/顺序/收藏/搜索/备份兼容、FLAC 标签、歌词提前过渡、队列编辑、固定布局与总时长场景。发现数量不是执行通过数量。

在 Xcode 选择实际 Apple 开发团队和可用 Bundle ID，连接 iPhone 运行；导出 IPA 需 Archive 与有效签名，源码不包含证书、描述文件或预填团队。

## 检查与验收范围

[语法记录](verification/SWIFT-SYNTAX.json) 只检查 Swift grammar，不检查 Apple SDK 类型。[工程检查](verification/PROJECT-CHECK.json) 检查 OpenStep 工程、目标引用、scheme、版本、隐私 XML、图像、字体名称/哈希/许可及样本文字和明确的标点回退。

没有测试 iPhone 上真实千首 FLAC、100 GB 文件夹、原生帧率、峰值内存、后台播放与锁屏、权限重启、减少动态、SE 至 Pro Max 的排版和手势。不能把后台任务、语法通过或源码打包当成“绝不卡顿”的证明。详见 [本版设计与验收](https://github.com/xymw54128/Little-Red-Flower-Music-Player/blob/main/docs/design/ios-1.1.1.md)。

资源生成工具依赖同工作区 Android 矢量与字体；源码包已带生成结果，Mac 构建无需运行它。Windows host-python 工具依赖原工作区，其他主机可安装 tools/requirements-host.txt 后直接运行 Python 检查。
