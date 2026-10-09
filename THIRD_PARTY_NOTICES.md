# 第三方资源与授权

项目原创源码和花朵 / SVG 设计采用根目录 [MIT 许可证](LICENSE)。下列资源保留各自的授权，根目录许可证不替代它们的原始条款。

## 中文字体

字体使用未经修改的上游文件，Android 与 iOS 均包含原始 SIL Open Font License 1.1 和来源 / 哈希记录。

| 字体 | 版本 / 来源 | 原始授权 |
| --- | --- | --- |
| 霞鹜文楷 | [LXGW WenKai 1.522](https://github.com/lxgw/LxgwWenKai/releases/tag/v1.522) | [OFL](android/app/src/main/assets/font-licenses/lxgw_wenkai.ttf.OFL.txt) |
| 思源宋体 | [Source Han Serif 2.003](https://github.com/adobe-fonts/source-han-serif/releases/tag/2.003R) | [OFL](android/app/src/main/assets/font-licenses/source_han_serif_cn.otf.OFL.txt) |
| 朱雀仿宋 | [Zhuque Fangsong 0.212，预览版](https://github.com/TrionesType/zhuque/releases/tag/v0.212) | [OFL](android/app/src/main/assets/font-licenses/zhuque_fangsong.ttf.OFL.txt) |

来源记录：[Android](android/app/src/main/assets/font-licenses/sources.json)、[iOS](ios/Xiaohonghua/Resources/font-sources.json)。iOS 的许可证副本位于 `ios/Xiaohonghua/Resources/`。

## 网页原型开关

`ui/styles.css` 中的开关结构参考 Uiverse / Galaxy 的 **alexruix / lazy-dragon-53**，采用 MIT 授权，原 attribution 注释保留。上游原始许可证见 [`licenses/Uiverse-MIT.txt`](licenses/Uiverse-MIT.txt)。

```text
MIT License

Copyright (c) 2023 Uiverse.io

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Gradle Wrapper 与 Android 依赖

仓库中的 Gradle Wrapper 保留 Gradle 的 Apache License 2.0 版权头；完整许可证见 [`licenses/Apache-2.0.txt`](licenses/Apache-2.0.txt)。Android 构建时下载的 Kotlin、AndroidX / Compose / Media3 等依赖遵循各自上游许可证。工具链、SDK 与 Gradle 缓存未纳入仓库。

## 测试与音乐文件

FLAC / WAV 测试素材为项目合成的测试音；网页歌曲名称和封面用于 UI 演示。仓库不包含用户导入的真实音乐库。
