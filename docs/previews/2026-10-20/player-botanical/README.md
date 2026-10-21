# 播放页花枝预览

仅对当前原版 B 播放布局周围的封面装饰做对照；三个方向使用相同封面、歌曲信息、歌词与按键。歌词页右上角常驻现有五瓣小红花，点击可重播旋转动画。

- A 疏朗花枝：对角的灰绿叶枝、红花与奶白小花，留白最多。
- B 沿框藤蔓：细藤顺边框生长，三处大小不同的花朵。
- C 彩色野花：蓝紫金小花与叶枝，色彩更活泼。

打开 index.html 可离线比较；?focus=A/B/C 单独查看，&embed=1 以整个视口显示手机界面，&page=lyrics 可直接查看歌词页。演示素材复用此前本地原创封面，不播放音频，不读取或改变 App 音乐库。

点击封面或左滑进入歌词，右滑或底部圆点切回；播放/暂停、喜欢、上一首/下一首、进度、模式与队列均可试用。枝梢只在封面页播放时轻摆，封面本身不做呼吸或旋转。减少动态效果关闭装饰动画。

歌词右上角小红花已在 Android 源码补齐；枝叶装饰暂未接入 Android，等待选定。网页验证不代表 Android 真机流畅度。

用户已选择 C：原生落地于 Android 1.1.2。歌词标题两侧野花与上下羽化边界已同步示意，最新截图为 screenshots/c-cover.png 和 c-lyrics.png。原生状态和未实测边界见 dist/ANDROID-1.1.2-UI-CHECK.json。

歌词标题花枝最新修订：左2蓝紫、右3金粉奶白，共同轮廓180度中心对称，右侧多一朵点缀。预览检查TITLE-SPRIG-CHECK.json；截图screenshots/c-title-pair-header.png与c-title-pair-lyrics.png；本地1.1.2花枝修订APK与原正式包分别保留。

草图版：按用户手绘的横向弧线改画歌词标题花枝，左两朵在外侧上方、枝尾向内下弯，右侧中心旋转并带第三朵花。截图screenshots/c-sketch-title-header.png、c-sketch-title-lyrics.png；检查SKETCH-TITLE-SPRIG-CHECK.json。

1.1.3正式版采用紧凑花簇：标题两侧间距约1dp，枝条轻包裹文字两角，五种鲜艳花型（左矢车菊/蔷薇，右向日葵/郁金香/桔梗）。最新截图c-garland-title-header.png、c-garland-title-lyrics.png；检查GARLAND-TITLE-CHECK.json。原生与预览保持同一绘制参数，真机效果未实测。

当前修订采用圆润小红花主题：左2朵向上、右3朵向下，简洁轻弯枝叶，无越界卷枝；短标题居中，长标题在固定文字区域缓慢滚动。96种屏宽/字号/语言组合已检查，记录ROUNDED-TITLE-CHECK.json，截图screenshots/c-rounded-title-header.png、c-rounded-title-lyrics.png和c-rounded-title-large-header.png。原1.1.3及先前截图独立保留。
