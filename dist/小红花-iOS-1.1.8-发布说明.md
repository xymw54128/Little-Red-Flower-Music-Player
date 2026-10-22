# 小红花 iPhone 1.1.8 源码

2026-10-22。版本 1.1.8 / build29，最低 iOS17，iPhone 竖屏。作者：浪雨林兴云魔王。

**本产物为 Xcode 源码工程，不是 IPA。** 安装到 iPhone 仍需 Mac、Xcode 和有效 Apple 签名。Android 1.1.8 APK 与本源码包在同一版本发布。

## 更新

- 歌曲信息页突出封面和明确的“标题”，艺术家、作曲家、专辑与音频/文件信息分组；使用纸色主题和少量静态花枝。长文本和大字号可完整查看，封面保留点击放大。
- 作曲家读取真实文件标签，兼容旧索引和备份；升级后手动刷新一次曲库补读已有文件，缺失标签显示“未设置”。移除信息页中专辑歌手、碟号和曲目展示，保留底层专辑排序字段。
- 检查并修正歌词颜色状态与封面单行歌词时钟，保留顶部/底部渐隐、LRC offset 与详情滚动规则；隐藏界面停止不必要的逐帧工作。
- 保留文件导入、歌单管理、收藏动效、七色花迷你栏、主题与夜间模式。播放顺序、音频解码、session、DSP、音量和速度配置不因本轮修改而改变。

## 下载和构建

[iOS 源码 ZIP](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/download/v1.1.8/Xiaohonghua-iOS-1.1.8-source.zip) · [源码包校验](小红花-iOS-1.1.8-CHECK.json) · [构建说明](../ios/README.md) · [双端发布页](https://github.com/xymw54128/Little-Red-Flower-Music-Player/releases/tag/v1.1.8)

解压后打开 `Xiaohonghua.xcodeproj`。Mac 上可在工程根目录运行 `bash tools/verify-mac.sh`，执行核心与模拟器测试、未签名设备构建；在 Xcode 选择自己的 Apple 开发团队后签名安装。

## 验证边界

27 个 Swift 文件语法解析无错误，18 个应用源码的工程引用、版本 1.1.8/build29、scheme、图片与 7 份字体许可/哈希检查通过。提供 61 项 CoreTests（54 项纯 Core、7 项仅 iOS 模型/渲染）与 9 项 UI 用例，均未执行；Windows 另核对源码快照和 ZIP 完整性。Xcode SDK 类型编译、XCTest、模拟器、iPhone、Apple 签名和 IPA 导出均未执行；这些检查不能代替手机视觉、帧率或音频验收。

保留已发布 iOS 1.1.7 源码包，不覆盖历史产物。
