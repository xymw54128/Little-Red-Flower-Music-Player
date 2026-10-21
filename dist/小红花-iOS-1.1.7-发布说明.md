# 小红花 iPhone 1.1.7 源码

2026-10-21。版本1.1.7 / build28，最低iOS17。提供原生Xcode源码工程，与Android1.1.7/code28统一发布。

- 同步花瓣迷你栏、圆封面进度圈、右侧五叶枝条、播放页彩色野花与七色花；去掉封面呼吸。
- 歌词标题配左二右三花枝和边界渐隐；收藏有无底纹动效，我喜欢取消收藏后再移出。菜单与sheet保留iOS原生风格并跟随主题背景。
- 播放队列按最新音乐库或歌单顺序建立，搜索子集不扩充；跨歌单共享歌曲也切换来源，专辑保留盘号/曲号；随机队列显示实际遍历顺序，手动调整队列退出随机。来源排序只调整队列，不重启当前音频。
- 保留手动刷新全标签、拼音搜索、歌单描述/封面/批量管理、八项歌词字体、主题和夜间模式。

## 下载与构建

[下载iOS源码ZIP](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.1.7/Xiaohonghua-iOS-1.1.7-source.zip) · [双端发布页](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.1.7) · [构建说明](../ios/README.md) · [源码打包报告](小红花-iOS-1.1.7-CHECK.json)

解压后在Mac用Xcode打开 Xiaohonghua.xcodeproj，执行 `bash tools/verify-mac.sh`。构建与安装还需选择有效Apple开发团队；导出IPA需Archive及签名。本包不含证书、描述文件和IPA。

Windows主机仅执行Swift语法、工程/target引用、scheme、版本、资源、字体许可/哈希及ZIP完整性检查。未执行Apple SDK类型编译、XCTest、模拟器、实机、签名或音频输出测量；测试发现数量不等于通过数量。

保持AVFoundation解码、音频session、DSP、音量和速度配置；iOS文件授权、系统锁屏及sheet行为遵循系统，与Android可能不同。本次源码未经过iPhone排版、千首文件/100GB导入、峰值内存与帧率验收。

最终主机检查：25个Swift文件语法无错误、18个应用Swift引用完整、7份字体和资源检查通过。提供52项CoreTests（47项纯核心、5项仅iOS模型）与9项UI用例，均未执行；5项模型用例不包含真实音源，不作为解码或音频输出证据。
