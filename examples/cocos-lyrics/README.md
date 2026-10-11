# 小红花歌词组件（Cocos Creator 3.8）

这是用户要求的独立 TypeScript 示例。现有 Android App 使用 Kotlin / Compose；这个示例不参与 APK 构建。选择 Cocos Creator 是因为需求中的 ScrollView、Label 节点和属性面板与它对应。

## 文件与职责

- `LyricsCore.ts`：`parseLrc()` 解析标准 LRC，`findLyricIndex()` 二分查找当前行，`LyricAnimator.updateLyrics()` 更新播放行，`switchLine()` 建立可中断动画。
- `LyricsController.ts`：可直接挂载的 Creator 组件，生成单独的 Label 节点，测量真实高度，逐帧应用样式与 ScrollView 偏移。
- `core.test.cjs`：不依赖引擎的时间与动画回归测试。
- `check.cjs`：使用官方 `@cocos/creator-types@3.8.6` 声明进行 TypeScript 检查。

全部时间参数使用**秒**。输入 `[01:23.45]歌词` 的 `startTime` 为 `83.45`，`endTime` 为下一条的 `startTime`；末条使用音频总时长，未知时为 `Infinity`。解析支持多个时间戳、毫秒 `offset`、同时间翻译合并以及空白间奏行。

## 节点挂载

将 `LyricsCore.ts` 和 `LyricsController.ts` 一起复制到 Creator 3.8 项目的 `assets/scripts/lyrics/`。在编辑器创建以下结构：

```text
Canvas
└─ LyricsPage                深色纯色背景、iPhone/Android 安全区域内布局
   ├─ Title                 可选，外部设置为“歌名 - 歌手”
   └─ LyricsScroll          ScrollView + LyricsController
      └─ View               UITransform + Mask，决定可见歌词范围
         └─ Content         UITransform + UIOpacity，初始为空
            ├─ Lyric-0      脚本创建：Label + UITransform + UIOpacity
            ├─ Lyric-1
            └─ …
```

1. `ScrollView.content` 绑定 `Content`，`LyricsController.scrollView` 绑定该 ScrollView。
2. `View` 必须是 `Content` 的直接父节点；设置它的宽高并加 Mask。Content 和歌词节点不要挂 Layout / Widget，避免编辑器布局组件与逐帧位置争抢。使用 Widget 调整 LyricsScroll 或 View 的大小即可。
3. Content 为本组件专用，不放其他业务节点。脚本只销毁自己创建的歌词节点。
4. `lrcText` 输入标准 LRC；可绑定已导入的 `Font` 资源。歌词容器推荐背景 `#171A20`，当前行白色，非当前行浅灰。
5. 音乐可使用绑定的 `AudioSource`，也可由自定义播放器提供时间，两者选一。

首尾有半个容器高度的留白，保证第一行和最后一行也能垂直居中。长句自动换行；以 Label 实测行高计算中心，缩放不改变布局。不存在“固定行高乘索引”的近似。

## 属性面板

| 属性 | 默认值 | 含义 |
| --- | ---: | --- |
| transitionDuration | 0.25 | 颜色、透明度、缩放过渡时长 |
| scrollDuration | 0.30 | 自动滚动居中时长 |
| idleResumeDelay | 1.50 | 手指松开且惯性停止后，等待归位的时间 |
| activeScale | 1.05 | 当前行的视觉缩放 |
| inactiveOpacity | 0.60 | 非当前行透明度 |
| activeColor | 白色 | 当前行颜色 |
| inactiveColor | 浅灰 | 非当前行颜色 |
| fontSize / lineHeight | 30 / 42 | 字号和单条歌词换行后的行高 |
| rowGap | 28 | 相邻歌词节点间距 |
| horizontalPadding | 24 | 容器两侧留白 |
| durationSeconds | 0 | 音频总时长；0 为未知 |

时长在运行时修改会用于动画计算；修改字体、字号、颜色、缩放或间距后调用 `refreshLayout()`，让行高和绘制缓存更新。

## 音乐时间接入

绑定 AudioSource 时，组件在每一帧读取 `audioSource.currentTime`。音乐暂停后时间不再增加，但 UI 动画继续到终点。继续播放则继续匹配 LRC 时间。原始 FLAC 的解码由播放器负责，这个组件只处理歌词。

使用自定义播放器时，不绑定 AudioSource，在播放器更新回调或帧更新中传入实际播放时间：

```ts
// lyrics 为绑定好的 LyricsController，player 为你的播放器。
lyrics.setLyrics(lrcString, player.durationSeconds);

// 每帧 / 真实播放位置回调执行。暂停时仍可以传入同一个位置。
lyrics.updateLyrics(player.positionSeconds);

// 进度条：先修改媒体位置，收到新的实际位置后再同步歌词。
// 播放器的异步 seek 未确认前，不要把旧位置继续覆盖到新位置上。
await player.seekToSeconds(targetSeconds);
lyrics.seekLyrics(player.positionSeconds);

// 改字体或容器尺寸后：
lyrics.refreshLayout();
```

`player` 是接入位置的示意变量，不是本组件附带的音频库。若在 Creator 中使用 AudioSource，进度条设置位置后调用 seek 专用入口：

```ts
audioSource.currentTime = targetSeconds;
lyrics.seekLyrics(targetSeconds);
```

组件会短暂过滤尚未完成的异步 seek 的旧位置，确认后继续以实际音频时间为准。暂停状态下拖动进度条也会触发相同的动画。`seekLyrics()` 会立即退出手动浏览；即使 seek 后仍位于同一歌词行，也从用户当前位置平滑居中，无需等 1.5 秒。

页面进入/退出使用 `LyricsPage.active = true / false`。重新启用时，内容先隐藏，在同一帧完成测量和当前行定位后显示，用户不会看到旧位置向上/下跳。如果项目只用透明度隐藏页面而不禁用节点，在再次显示前调用 `setLyrics(当前LRC, 音频总时长)`，再执行页面淡入。

## 同步、打断和用户浏览

LRC 时间决定当前目标行；单一 UI 帧时钟决定动画进度。换行时，旧行向浅灰、0.6 透明度、1 倍缩放过渡，新行向白色、1 透明度、1.05 倍缩放过渡；颜色、透明度、缩放共享 **250 ms easeOut**，位移从同一时刻开始，使用 **300 ms easeOut**。两段时长按需求不同，结束时间相差 50 ms，但不存在提前滚动、时间戳到了才改色的第二套时序。

`styleRunning` / `scrollRunning` 是唯一动画状态锁，`generation` 标记新一轮状态。快速 seek 或密集时间戳会先采样旧动画当前值，再以该状态为新起点；没有挂起的 tween 完成回调，也不会逐次堆叠动画。重复的同一歌词索引不会重启动画。暂停只停媒体时间，不会让 UI 动画停在半途。

手动触摸停止自动居中，高亮仍按音乐更新。手指持续按住不会被强制归位；惯性位移刷新最后操作时间，停止后 **1.5 秒**才重新居中。程序写入 ScrollView 时用 `writingOffset` 排除自己触发的滚动事件。模拟 TOUCH_CANCEL 与真正松手区分，防止拖动过程中提前恢复。

## 验证

在此目录使用 Node.js 24 或以上：

```sh
npm ci --ignore-scripts
npm test
npm run check
```

交付时已通过 **16 项核心测试**，包括不规则时间戳、打断连续性、暂停 seek、手动浏览后同索引 seek、1.5 秒归位、惯性/按住、重新进页和变化行高。完整组件通过 TypeScript 5.8.3 与官方 Creator 3.8.6 声明检查。

当前环境没有运行 Creator 编辑器，也没有运行这个示例的手机构建；上述检查不等同于真机帧率验证。接入后应在目标手机检查首次进入、连续 seek、长句字体加载、上下浏览和横竖屏布局。

API 依据：[ScrollView 组件文档](https://docs.cocos.com/creator/3.8/manual/zh/ui-system/components/editor/scrollview.html)、[官方 ScrollView 实现](https://github.com/cocos/cocos-engine/blob/v3.8.6/cocos/ui/scroll-view.ts)、[Label 实现](https://github.com/cocos/cocos-engine/blob/v3.8.6/cocos/2d/components/label.ts)、[AudioSource 实现](https://github.com/cocos/cocos-engine/blob/v3.8.6/cocos/audio/audio-source.ts)。
