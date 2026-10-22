# 小红花 · iPhone 1.1.8

作者：浪雨林兴云魔王。SwiftUI / UIKit / AVFoundation 原生 iPhone 工程，最低 iOS17、竖屏；版本 1.1.8、构建号29，Bundle ID `com.xiaohonghua.music.ios`。

**提供 Xcode 源码工程，没有可安装 IPA。** 当前开发环境为 Windows；语法、工程引用、资源和源码包检查不能替代 Apple SDK 类型检查、Xcode 编译、XCTest 或 iPhone 验收。

[下载 iOS 1.1.8 源码包](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.1.8/Xiaohonghua-iOS-1.1.8-source.zip) · [双端发布页](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.1.8) · [源代码许可](LICENSE) · [字体许可](THIRD_PARTY_NOTICES.md)

## 本版变化

- 歌曲信息采用纸色分组布局，突出封面与明确的“标题”，展示艺术家、作曲家和专辑，音频与文件信息独立排列，加入少量静态花枝。长标题、路径和大字号可完整查看；保留点击封面放大。
- 读取真实作曲家标签并兼容旧索引和备份。升级后请手动刷新曲库补读作曲家；缺失标签显示“未设置”。仅移除专辑歌手/碟号/曲目的信息展示，保留专辑排序字段。
- 歌词的字色与缩放由同一帧状态驱动，保留上下渐隐；封面单行歌词读取实际媒体时钟，按 LRC 时间戳与 offset 切换。

## 保留功能

- 原版 B 固定封面播放页，取消封面呼吸；封面周围采用彩色野花，小红心下方七色花按播放状态旋转、暂停逐渐停下。保留上一首/播放暂停/下一首、队列与完整歌曲时长，长歌词单行滚动完整预览。
- 迷你栏采用圆润全宽布局，圆形封面与进度圈、紧密七色花瓣逐瓣跳动、右侧五片绿叶；按钮无灰色底纹。花朵使用原生图层，减少动态和退到后台时停帧。
- 歌词标题配左右花枝，左二右三，歌词上下边界渐隐；歌词时间戳、居中和高亮保持同一时钟，手动浏览结束1.5秒后回位。
- 收藏爱心采用轻微弹动和花瓣反馈，在“我喜欢”取消收藏时先完成爱心与歌曲行退场，再移出歌单。歌单卡片和详情仍支持名称、描述、十一款花朵封面与相册照片、批量管理。
- 点歌按最新音乐库或歌单排序生成当前选择子集；跨歌单点同一首歌也切换来源，单纯浏览不换队列。随机模式的队列显示实际播放顺序；手动队列排序与来源排序保持当前音频和进度，专辑保留盘号/曲号顺序。
- 收起式搜索、拼音/首字母搜索，歌曲/专辑/歌手分类、手动刷新全部标签与文件变化；系统/文楷/宋体和五款多语言字体、主题颜色与夜间主题。
- 弹层跟随纸色和主题色，菜单、sheet与返回手势保留iOS原生行为。后台音频、锁屏指令、中断与耳机拔出暂停、JSON备份沿用现有实现。

## 导入、离线与换机

在系统“文件”授权音乐或文件夹，或向应用 Documents/Music 文件共享目录放入音乐。应用不能任意扫描iPhone全盘；iCloud/第三方提供商内文件须先下载到本机才能离线播放。权限失效后重新授权，单独导入歌曲时可另行导入同名LRC。支持FLAC/MP3标签、封面与歌词，WAV、M4A、AAC、AIFF、CAF等由系统解码；不额外提供OGG/Opus/DSD解码器。

只在导入或手动刷新时扫描，刷新完整重读标签/封面/LRC。备份包含索引、歌单、排序、手动歌词与设置，不包含原音乐、照片或系统授权书签。换机先导入音乐，再恢复备份，按唯一文件名和大小匹配；iOS备份格式保持 `xiaohonghua-ios / 1`，不直接读取Android私有路径。

## 在 Mac 上构建

需要 Xcode16或更新版本与iOS17+模拟器。源码包已包含资源，不需要CocoaPods或XcodeGen：

```sh
cd "/解压位置/小红花-iOS-1.1.8"
open Xiaohonghua.xcodeproj
bash tools/verify-mac.sh
```

脚本运行Swift Package核心测试、iPhone模拟器测试及未签名设备Release构建。可用 `IOS_DESTINATION` 指定模拟器；测试用WAV/FLAC只打包进测试target，UI测试使用专用干净模拟器。

在Xcode选择自己的Apple开发团队和可用Bundle ID，连接iPhone运行。导出IPA需Archive与有效签名；源码不包含证书、描述文件或预填团队。系统决定锁屏卡片布局，菜单与sheet过渡也可能与Android不同。

## 验证范围

[语法报告](verification/SWIFT-SYNTAX.json)检查Swift grammar；[工程报告](verification/PROJECT-CHECK.json)检查OpenStep工程、target/source引用、版本、scheme、隐私XML、资源、字体许可/哈希、样本文字与标点回退，并记录发现的测试数量。发现数量不表示测试执行通过。

本轮 27 个 Swift 文件语法解析无错误，18 个应用源码的工程引用、版本 1.1.8/build29、scheme、图片与 7 份字体许可/哈希检查通过。提供 61 项 CoreTests（54 项纯 Core、7 项仅 iOS 模型/渲染）和 9 项 UI 用例，均未在 Windows 执行；发现数量不表示测试通过。

本轮未执行Xcode SDK编译、XCTest、模拟器、实机、Apple签名和IPA导出，也未测真实千首FLAC、100GB文件夹、手机帧率、峰值内存、权限重启、锁屏音频或SE至Pro Max排版。解码器、DSP、音量和速度配置保持原实现，不宣称系统输出bit-perfect。

[本版源码打包报告](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.1.8/Xiaohonghua-iOS-1.1.8-CHECK.json) · [设计与验收范围](https://github.com/xymw54128/Little-Red-Flower-Music-Player/blob/main/docs/design/ios-1.1.8.md)。历史 [iOS1.1.1源码发布](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.1.1) 保留。

源码包解压后本目录即工程根目录，示例命令的外层文件夹名可自定。Windows `tools/host-python.cjs` 仅供原工作区检查；其他主机可安装 `tools/requirements-host.txt` 后直接运行Python检查。Mac构建无需运行资源生成器。
