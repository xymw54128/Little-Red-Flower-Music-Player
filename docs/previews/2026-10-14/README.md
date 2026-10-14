# 小红花 · 2026-10-14 设计预览

[预览入口](index.html) · [播放页单页](player-options/index.html) · [四方案对比](player-options/compare.html) · [字体样本](font-options/index.html) · [新字体对比](font-options/compare.html)

双击 `index.html` 可以离线打开。字体预览将真实字形子集嵌入页面，不依赖网络。

也可在项目根目录运行：

```text
node tools/serve-design-preview.cjs
```

再用浏览器打开 `http://127.0.0.1:8897/`。服务只绑定本机回环地址，只展示本轮预览目录；关闭终端可结束。

点击四个方案切换布局，点播放看动效；支持暂停、切歌、进度拖动、收藏、队列、歌单选择和主题。点击封面/歌词或向左滑进入完整歌词，向右滑返回。原型使用示例时钟，不播放音频，不访问音乐文件或 App 数据。

截图是浏览器实渲染；动效用交互页面查看。原型与 App 是两个独立实现，手机帧率尚未测试。候选字体只包含样本所需字形，不能直接作为完整 App 字体；授权与语种配套见字体页面。

本轮已在 Android 1.0.10 中取消手工歌词时间校准；新的字体和播放页还未替换，等待选择。

本机截图检查助手：`tools/check-design-preview.cjs` 与 `tools/check-font-preview.cjs`，使用当前 Codex 捆绑的 Playwright 和本机 Chrome；普通查看预览无需安装这些依赖。
