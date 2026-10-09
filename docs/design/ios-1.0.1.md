# 小红花 iPhone 1.0.1

现有 Android 1.0.1 的功能与视觉作为明确设计基线，单独新增 `ios/` 原生工程。当前目录不是 Git 仓库，继续按之前的选择本地保存。用户没有 Mac，未提供目标机型，最低系统先设 iOS 17，适配 iPhone 安全区域、刘海、灵动岛和底部手势区域。

使用 SwiftUI、UIKit、AVFoundation、MediaPlayer 和系统文件/照片选择器，不增加第三方运行时依赖。作者为“浪雨林兴云魔王”。版本 1.0.1，内部构建号 8。

## 平台适配

- 通过“文件”导入歌曲、LRC 或文件夹。文件夹保存系统授权书签，直接读取，不重复复制整库音乐。也自动扫描应用 Documents/Music，用户可通过 Finder 或“文件”放入歌曲。
- 文件夹监测只在前台定时执行，后台依靠系统音频能力维持播放；重新进入前台会扫描。手动刷新显示进度，自动扫描静默更新。文件提供商撤销授权、文件移动或未下载时显示错误并允许重新授权。
- 原生解码常用 FLAC、MP3、WAV、M4A、AAC、AIFF、CAF。不能把 Android 的 OGG/Opus 支持直接等同于 iOS；当前不加入额外解码库。解码失败明确反馈。
- iOS 的系统锁屏控件由 Now Playing 与系统管理，设置开关控制向系统发布播放信息及接收远程指令，不能承诺系统每次都立即移除已有卡片。
- 不访问云端服务，不要求登录。相册照片由 PhotosPicker 单次选择，转换为应用内部图片。

## 界面和行为

音乐库、歌单、设置沿用红色 #CE3C4C、金色 #F6D88C、浅底 #F8F8F6。顶部三种五瓣花保持原样。底部迷你播放器有上一首、播放暂停、下一首，播放页保留相同控制。

音乐库支持歌曲/专辑/歌手、搜索、序号、数量、标题/歌手/随机/自定义排序。自定义用系统拖动手柄调整后保存。歌单详情共享歌曲列表，添加歌曲先选歌单，歌单封面可选八种样式或本地照片。滚动列表时出现定位按钮，停止两秒后渐出，点击居中当前歌曲。

播放页左滑歌词页，使用系统分页。歌词按 LRC 时间戳二分定位，合并同时间的翻译，支持 offset 和多时间戳；滚动动画依据相邻实际时间间隔，在下一行时间戳前结束，强调行仍按真实时间切换。UIKit 歌词视图用 CADisplayLink 直接更新偏移，不逐帧重建 SwiftUI 主界面。手动查看四秒后回到当前歌词；暂停时仍允许浏览。进度只更新播放器局部。

歌词六种字体、字号、行距、动态滚动开关；五种主题；背景纯色/专辑/照片，透明度、缩放、位置可调。复用 Android 已有的三款原始开源中文字体及 OFL 授权。

启动画面先静态黄色花心，随后应用内 2.64 秒五瓣依次长出、小转动、沿弧线快速旋转飞到标题旁、落位转动停止。“小红花 · 音乐播放器”使用文楷手写字体，渐入渐出。遵循系统减少动态效果，后台暂停动画，每次进程冷启动播放一次。苹果静态 LaunchScreen 本身不播放动画。

## 验收边界

提供完整 Xcode 工程、共享 scheme、核心 XCTest、Mac 验证脚本和本地源代码包。Windows 检查 Swift 语法树、工程引用、资源、字体及元数据；这些不替代 Xcode 编译、模拟器和 iPhone 验证。目前无 Mac，不生成或声称已验证 IPA，不预填 Apple 团队或签名证书。

重点实机验收：大 FLAC 文件、文件夹授权重启持久化、锁屏后台下一首、耳机拔出/来电、密集 LRC/拖动进度、动态字体、不同 iPhone 尺寸、100 GB 索引与启动动画帧表现。

SDK 参考：Apple 的 [文件选择器](https://developer.apple.com/documentation/swiftui/view/fileimporter(ispresented:allowedcontenttypes:allowsmultipleselection:oncompletion:))、[后台音频配置](https://developer.apple.com/documentation/avfoundation/configuring-your-app-for-media-playback)、[AVAudioPlayer](https://developer.apple.com/documentation/avfaudio/avaudioplayer)、[FLAC 格式](https://developer.apple.com/documentation/coreaudiotypes/kaudioformatflac)。
