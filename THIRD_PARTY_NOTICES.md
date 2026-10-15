# 第三方资源与授权

项目原创源码和花朵 / SVG 设计采用根目录 [MIT 许可证](LICENSE)。下列资源保留各自的授权，根目录许可证不替代它们的原始条款。

## Android 歌词字体

Android 提供系统字体和 7 份捆绑字库，共 8 个选项。文楷与原宋体逐字节保留；小薇宋意、禅丸圆体、奇异果圆体、红道手写、龙藏手书是分别独立的离线多语组合，替换旧花间萌趣、圆润手绘。5 个主字体来自固定 Google Fonts 提交的完整原文件；配套源来自固定提交或带语言参数的官方版本化完整交付，不使用预览 `text=` 子集。

每个新组合合并不重叠的字符覆盖，补全常用简体汉字、假名与现代韩文；统一 em 尺寸，将需要的 CFF 轮廓转换为 TrueType，移除不用于横排歌词的竖排表，按 Unicode 重排 glyph 以避免 BMP cmap 索引超过 65KB，并使用新的字体名称，不沿用上游保留名称。每项在 Android 8 及以上均为同一个原生 Typeface，Compose 与 NativePaint 使用相同字形。应用保留所有实际用到来源的完整版权及 SIL Open Font License 1.1 文本。

| 字体 | 实际字形来源 | 授权与修改记录 |
| --- | --- | --- |
| 霞鹜文楷 | [LXGW WenKai 1.522](https://github.com/lxgw/LxgwWenKai/releases/tag/v1.522)，保留未经修改的原文件 | [完整 OFL](android/app/src/main/assets/font-licenses/lxgw_wenkai.ttf.OFL.txt) |
| Little Flower Serif / 宋体 | [思源宋体 2.003](https://github.com/adobe-fonts/source-han-serif/releases/tag/2.003R) 与 [Nanum Myeongjo](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/nanummyeongjo)，保留 1.0.9 字库原文件 | [完整 OFL](android/app/src/main/assets/font-licenses/flower_serif.ttf.OFL.txt) |
| Little Flower Light Serif / 小薇宋意 | [ZCOOL XiaoWei](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/zcoolxiaowei) 中文与拉丁；[Klee One](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/kleeone) 日文；[Gowun Batang](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/gowunbatang) 韩文 | [完整 OFL](android/app/src/main/assets/font-licenses/flower_xiaowei.ttf.OFL.txt) |
| Little Flower Gentle Round / 禅丸圆体 | [Zen Maru Gothic](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/zenmarugothic) 主字形；[Noto Sans SC](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/notosanssc) 补全简体汉字；[Jua](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/jua) 常用韩文、[Gowun Dodum](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/gowundodum) 补全其他韩文音节；文楷补两个小假名 | [完整 OFL](android/app/src/main/assets/font-licenses/flower_zenmaru.ttf.OFL.txt) |
| Little Flower Round Serif / 奇异果圆体 | [Kiwi Maru](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/kiwimaru) 主字形；思源宋体补全简体汉字；Jua 常用韩文、Gowun Dodum 补全其他韩文音节 | [完整 OFL](android/app/src/main/assets/font-licenses/flower_kiwi.ttf.OFL.txt) |
| Little Flower Diary Hand / 红道手写 | [Zen Kurenaido](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/zenkurenaido) 主字形；文楷补全简体汉字及两个小假名；[Nanum Pen Script](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/nanumpenscript) 韩文 | [完整 OFL](android/app/src/main/assets/font-licenses/flower_kurenaido.ttf.OFL.txt) |
| Little Flower Free Hand / 龙藏手书 | [Long Cang](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/longcang) 中文与拉丁；[Yuji Syuku](https://github.com/google/fonts/tree/bd8f81ddb5c74d5c8897b36ad88b440266245103/ofl/yujisyuku) 日文；Nanum Pen Script 韩文 | [完整 OFL](android/app/src/main/assets/font-licenses/flower_longcang.ttf.OFL.txt) |

文楷还补足各组合未覆盖的必要标点等字符；龙藏手书中 Gowun Dodum 仅补一个兼容韩文字母。精确到来源的新增字符数、GB2312／假名／韩文贡献见 [Android 输出记录](android/app/src/main/assets/font-licenses/sources.json)，所有完整源文件和许可的 SHA256、官方 URL 与版本在 [构建输入清单](tools/lyric-font-inputs.json)。GStatic 配套记录明确区分实际字体交付版本与仅用于取得许可证的参考 Git 提交，不将两者冒称为相同原件。

7 份捆绑字库均实际覆盖全部 GB2312 汉字 6,763 个、基本日文假名 176 个、现代韩文音节 11,172 个和英文大小写及数字。主字体原生未覆盖的常用简体汉字由同类真实开源黑体／宋体／手写字形补全，不回退到系统默认字形；共享中日汉字保留优先来源的形状，不声称同时提供不同地区的同码位变体。Jua 原生只有 2,367 个现代韩文音节，其余 8,805 个由 Gowun Dodum 提供；B/C 与 D/F 共享部分配套字形是有意的搭配，不冒称每个语种的每个字都独一无二。捆绑范围之外的罕用 Unicode 字符才交由安卓系统回退。

使用 `node tools/check-fonts.cjs --install` 安装固定版本 fonttools 4.66.1，`node tools/font-sources.cjs` 获取已固定哈希的完整源并重建 5 个新字库；原文楷及宋体文件会先验 SHA，保持不变。`GH_BIN` 与 `PYTHON_BIN` 可指定 GitHub CLI 与 Python 路径。字体工具仅用于开发机，不作为应用依赖。`node tools/check-fonts.cjs` 检查实际 cmap、完整常用覆盖与多语种样式差异，输出 [实际字库预览](dist/font-preview.png) 与 [检查记录](dist/FONT-CHECK.json)。

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
