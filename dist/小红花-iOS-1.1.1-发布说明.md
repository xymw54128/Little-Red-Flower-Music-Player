# 小红花 iOS 1.1.1 源码版

2026-10-15。iPhone / iOS 17+，版本 1.1.1 / build 21，Bundle ID `com.xiaohonghua.music.ios`。与 Android 1.1.1 一起发布到 GitHub。

提供完整 Xcode 工程和资源，**没有可安装 IPA**。当前 Windows 环境仅完成语法、工程、资源、字体与压缩包检查，未运行 Apple SDK 编译、XCTest、模拟器、签名或 iPhone 验收。

同步原版 B 固定大封面、完整单行歌词预览与总时长、默认收藏、歌单描述/编辑/批量管理、收起搜索、拼音/首字母、专辑歌手和盘号/曲号、八项字体、主题/夜间模式、花朵互动、手动全标签刷新，以及千首曲库后台处理。原生歌词同步颜色/缩放/居中，正常播放提前过渡、手动浏览 1.5 秒回归。

主页面横滑松手切换，子页保留系统返回；并非 Android 式跟随手指的交互式分页。格式解码、文件授权、后台调度与锁屏行为遵循 iOS，具体范围见 [iOS 说明](../ios/README.md)。

源码包：`小红花-iOS-1.1.1-源码.zip`，49621473 字节（47.32 MiB），96 个文件。

SHA-256：

```text
cd317a1a5fc1d31a66b960edbd2d99e7df5bc0a1b510f434c49e9de78e0b3522
```

[源码检查报告](小红花-iOS-1.1.1-CHECK.json) · [本地摘要](小红花-iOS-1.1.1-源码.zip.sha256) · [设计与验收边界](../docs/design/ios-1.1.1.md)

39 项核心测试、7 项 UI 测试已提供，未执行。Windows 的语法检查不验证 Apple SDK 类型，工程引用通过不代表 Xcode 构建通过。后续在 Mac 运行 `ios/tools/verify-mac.sh`，再设置真实 Apple 开发团队并签名安装。真实千首 FLAC / 100 GB 曲库、帧率、内存、后台与锁屏、不同 iPhone 排版尚未实测。

[GitHub 1.1.1 下载](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.1.1)。原创源码 MIT，7 份字体的 OFL 与来源包含在包内。
