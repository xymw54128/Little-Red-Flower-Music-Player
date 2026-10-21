# 小红花迷你播放栏候选

双击 index.html 即可离线预览；所有资源在本目录，无需安装依赖。

- 四款对比：index.html
- 单款完整首页：index.html?focus=A / B / C / D，可直接点击页面中的“放大体验”。
- 点播放 / 暂停观察动效；上一首 / 下一首切换演示曲目；页面顶部可调进度。仅模拟状态，不播放实际音频，不读取 App 音乐库。
- screenshots/overview.png 是四款对比图，其余 a/b/c/d-home.png 为完整首页，*-bar.png 为播放栏近景。

原四款候选：A 花瓣唱片、B 花枝书签、C 花心播放键、D 七色花岛。用户已选择 A，并结合 D 七色花瓣形成单独定稿。

CHECK.json：16 组屏宽 / 方案组合、48 个点击目标、长文本、切歌、进度、暂停减速、连续切换、模拟页面隐藏及减少动态效果检查通过，未发现脚本错误。网页验证不代表 Android 真机帧率。

## A 方案细化预览

- 地址：`index.html?focus=A&refined=1`（原四款和 `?focus=A` 仍保留）。
- 圆润播放栏左右贴满手机页面；七片花瓣使用 D 方案七色配色，围绕静止封面旋转、轻跳，保留 A 的环形进度与原有三个播放按键。
- 播放时 48°/秒匀速旋转，起转 240ms；暂停时 900ms 平滑减速并减小跳动幅度，停在当前角度，快速反复切换沿当前速度继续。
- 轻跳为同一旋转时钟驱动的逐瓣接力，每瓣约 536ms，无实际音频，不是音频节拍检测。减少动态效果时关闭花瓣运动；页面隐藏时暂停动画计算。
- 网页为独立演示；选定布局已接入本地 Android 包。定稿截图及 4 种屏宽的按键、进度、旋转与缓停检查见 REFINED-CHECK.json。

本地 Android 已实现用户选定的 A 七色花版本；定稿网页为交互动效示意，不播放实际音频。精修原生包与检查记录见项目 dist/ANDROID-LOCAL-MINI-PLAYER-POLISH-CHECK.json；原生手机帧率尚未实测。

精修：进度圈直接作为封面边框，瓣根贴圈、七片依次轻跳；边框右上和左下各两片绿叶。最新截图为 screenshots/a-polished-home.png 与 a-polished-bar.png，浏览器检查见 POLISH-CHECK.json。可用 ?focus=A&refined=1&v=2 刷新查看。

最新紧密花瓣版：宽圆花瓣相邻略微交叠，逐片跳动时保持衔接；进度圈、绿叶与按键布局保持不变。截图为 screenshots/a-packed-home.png 与 a-packed-bar.png，检查见 PACKED-CHECK.json；刷新参数 ?focus=A&refined=1&v=3。Android 包检查见 dist/ANDROID-LOCAL-MINI-PLAYER-PACKED-CHECK.json。
