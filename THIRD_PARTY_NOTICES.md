# 第三方资源与授权

项目原创源码和花朵 / SVG 设计采用根目录 [MIT 许可证](LICENSE)。下列资源保留各自的授权，根目录许可证不替代它们的原始条款。

## Android 歌词字体

Android 文楷使用未经修改的上游文件。其余三套歌词字体是 SIL Open Font License 1.1 授权下的组合衍生字体，统一为一个原生字体文件，保证 Android 8 及以上的 Compose 与 NativePaint 使用同一份中英日韩字形。修改包括合并不重叠的字符覆盖、统一 em 尺寸、将宋体立方轮廓转换为二次轮廓、移除不用于横排歌词的竖排表，并使用新的字体名称；不沿用上游保留名称。应用保留每个来源的完整版权和 OFL 文本。

| 字体 | 版本 / 来源 | 原始授权 |
| --- | --- | --- |
| 霞鹜文楷 | [LXGW WenKai 1.522](https://github.com/lxgw/LxgwWenKai/releases/tag/v1.522) | [OFL](android/app/src/main/assets/font-licenses/lxgw_wenkai.ttf.OFL.txt) |
| Little Flower Serif / 宋体 | [思源宋体 2.003](https://github.com/adobe-fonts/source-han-serif/releases/tag/2.003R) 中文、拉丁与日文；[Nanum Myeongjo](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/nanummyeongjo) 补足韩文 | [完整 OFL 与修改说明](android/app/src/main/assets/font-licenses/flower_serif.ttf.OFL.txt) |
| Little Flower Playful / 花间萌趣 | [ZCOOL KuaiLe](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/zcoolkuaile) 中文与拉丁；[Hachi Maru Pop](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/hachimarupop) 日文；[Single Day](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/singleday) 韩文；文楷补足两个罕用小假名 | [完整 OFL 与修改说明](android/app/src/main/assets/font-licenses/flower_cute.ttf.OFL.txt) |
| Little Flower Rounded / 圆润手绘 | [ZCOOL QingKe HuangYou](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/zcoolqingkehuangyou) 中文与拉丁；[Yomogi](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/yomogi) 日文；[Gowun Dodum](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/gowundodum) 韩文；文楷补足两个罕用小假名 | [完整 OFL 与修改说明](android/app/src/main/assets/font-licenses/flower_handdrawn.ttf.OFL.txt) |

来源与输出 SHA256：[Android 记录](android/app/src/main/assets/font-licenses/sources.json)。官方源文件、许可证 SHA256 与固定提交记录位于 [构建输入清单](tools/lyric-font-inputs.json)。使用 `node tools/check-fonts.cjs --install` 安装固定版本 fonttools 4.66.1 后，运行 `node tools/font-sources.cjs` 下载并重建；`GH_BIN` 与 `PYTHON_BIN` 可指定本机 GitHub CLI 和 Python 路径。字体工具只用于开发机，不作为应用依赖。`node tools/check-fonts.cjs` 检查实际 cmap、覆盖与多语种样式差异，并生成 [字体预览](dist/font-preview.png) 和 [检查记录](dist/FONT-CHECK.json)。

每个捆绑字体均覆盖基本日文假名 176 个、现代韩文音节 11,172 个、英文大小写与数字。文楷和宋体覆盖 CJK 基本区 20,992 个字；花间萌趣和圆润手绘分别覆盖其中 10,069 和 10,837 个字。字体不声称覆盖全部 Unicode，缺少的罕用字由安卓系统字体回退。

## iOS 暂存字体

iOS 源码保留先前的原始文楷、思源宋体 CN 与朱雀仿宋字体，本轮 Android 重置未移植到 iOS。其 [来源与哈希记录](ios/Xiaohonghua/Resources/font-sources.json) 和许可证副本仍位于 `ios/Xiaohonghua/Resources/`，均采用 SIL Open Font License 1.1。

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
