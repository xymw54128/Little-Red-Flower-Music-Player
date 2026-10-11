import {
  _decorator, AudioSource, Color, Component, EventTouch, Font, Label,
  Node, ScrollView, UITransform, UIOpacity, Vec2,
} from 'cc';
import { LyricAnimator, parseLrc } from './LyricsCore';
import type { LyricGeometry, LyricLine } from './LyricsCore';

const { ccclass, property } = _decorator;

interface LyricRow {
  node: Node;
  label: Label;
  transform: UITransform;
  opacity: UIOpacity;
  color: Color;
  lastEmphasis: number;
}

/**
 * Cocos Creator 3.8 歌词组件。ScrollView 负责用户拖动，LyricAnimator 负责自动动画。
 * 两者不会同时写滚动位置；不用 tween/scrollToOffset 的内置动画，避免两个时钟叠加。
 */
@ccclass('LyricsController')
export class LyricsController extends Component {
  @property({ type: ScrollView, tooltip: '竖向 ScrollView，content 必须是专用空节点' })
  scrollView: ScrollView | null = null;

  @property({ type: AudioSource, tooltip: '可选；不绑定时由 updateLyrics(播放器秒数) 提供时间' })
  audioSource: AudioSource | null = null;

  @property({ multiline: true, tooltip: '标准 LRC 字符串，也可以运行时调用 setLyrics' })
  lrcText = '';

  @property({ min: 0, tooltip: '音频总时长（秒），0 表示未知' })
  durationSeconds = 0;

  @property({ min: 0, tooltip: '颜色、透明度、缩放过渡时长（秒）' })
  transitionDuration = 0.25;

  @property({ min: 0, tooltip: '自动居中位移过渡时长（秒）' })
  scrollDuration = 0.3;

  @property({ min: 0, tooltip: '手动浏览停止后恢复居中的等待时间（秒）' })
  idleResumeDelay = 1.5;

  @property({ min: 1, tooltip: '当前歌词缩放；只改视觉变换，不改变行高' })
  activeScale = 1.05;

  @property({ min: 0, max: 1 })
  inactiveOpacity = 0.6;

  @property({ type: Color })
  activeColor = new Color(255, 255, 255, 255);

  @property({ type: Color })
  inactiveColor = new Color(210, 213, 218, 255);

  @property({ type: Font, tooltip: '可选的本地字体资源' })
  font: Font | null = null;

  @property({ min: 1 })
  fontSize = 30;

  @property({ min: 1, tooltip: '同一条歌词换行后的行高' })
  lineHeight = 42;

  @property({ min: 0, tooltip: '相邻歌词节点之间的间距' })
  rowGap = 28;

  @property({ min: 0 })
  horizontalPadding = 24;

  private readonly animator = new LyricAnimator();
  private rows: LyricRow[] = [];
  private lines: LyricLine[] = [];
  private content: Node | null = null;
  private contentTransform: UITransform | null = null;
  private contentOpacity: UIOpacity | null = null;
  private viewport: UITransform | null = null;
  private geometry: LyricGeometry = { rowCenters: [], viewportHeight: 0, contentHeight: 0 };
  private now = 0;
  private externalPlaybackTime = 0;
  private pendingSeekTime: number | null = null;
  private seekIssuedAt = 0;
  private writingOffset = false;
  private layoutInProgress = false;
  private ready = false;
  private loadedSource: string | null = null;
  private loadedDuration = NaN;
  private measuredWidth = 0;
  private readonly offset = new Vec2();

  onLoad(): void {
    this.content = this.scrollView?.content ?? null;
    this.viewport = this.scrollView?.view ?? null;
    this.contentTransform = this.content?.getComponent(UITransform) ?? null;
    if (!this.scrollView || !this.content || !this.viewport || !this.contentTransform) {
      console.error('[LyricsController] 请绑定具有 content / view / UITransform 的 ScrollView。');
      this.enabled = false;
      return;
    }
    this.contentOpacity = this.content.getComponent(UIOpacity) ?? this.content.addComponent(UIOpacity);
    this.scrollView.horizontal = false;
    this.scrollView.vertical = true;
    this.scrollView.elastic = false; // 边界不回弹，首尾行也能稳定地位于正中。
    this.contentTransform.setAnchorPoint(0.5, 1);
    this.ready = true;
  }

  onEnable(): void {
    if (!this.ready || !this.scrollView || !this.viewport) return;
    const node = this.scrollView.node;
    // 捕获阶段接收内部 Label 的触摸；模拟 cancel 不等于用户真的松手。
    node.on(Node.EventType.TOUCH_START, this.onTouchStart, this, true);
    node.on(Node.EventType.TOUCH_END, this.onTouchEnd, this, true);
    node.on(Node.EventType.TOUCH_CANCEL, this.onTouchEnd, this, true);
    node.on(Node.EventType.MOUSE_WHEEL, this.onMouseWheel, this, true);
    node.on(ScrollView.EventType.SCROLLING, this.onScrolling, this);
    this.viewport.node.on(Node.EventType.SIZE_CHANGED, this.refreshLayout, this);
    // 重进页面复用已有 Label，不把一次页面切换变成全量节点重建。
    const duration = this.resolvedDuration(this.durationSeconds);
    if (this.loadedSource !== this.lrcText || this.loadedDuration !== duration) {
      this.setLyrics(this.lrcText, this.durationSeconds);
    } else {
      this.contentOpacity!.opacity = 0;
      this.scrollView.stopAutoScroll();
      if (this.viewport.width !== this.measuredWidth || this.viewport.height !== this.geometry.viewportHeight) this.measureRows();
      this.syncOptions();
      this.animator.load(this.lines, this.geometry, this.playbackTime(), this.now);
      this.renderFrame();
      this.contentOpacity!.opacity = 255;
    }
  }

  onDisable(): void {
    if (!this.scrollView || !this.viewport) return;
    const node = this.scrollView.node;
    node.off(Node.EventType.TOUCH_START, this.onTouchStart, this, true);
    node.off(Node.EventType.TOUCH_END, this.onTouchEnd, this, true);
    node.off(Node.EventType.TOUCH_CANCEL, this.onTouchEnd, this, true);
    node.off(Node.EventType.MOUSE_WHEEL, this.onMouseWheel, this, true);
    node.off(ScrollView.EventType.SCROLLING, this.onScrolling, this);
    this.viewport.node.off(Node.EventType.SIZE_CHANGED, this.refreshLayout, this);
    this.scrollView.stopAutoScroll();
    this.pendingSeekTime = null;
  }

  /** 解析入口；只在换歌/更换歌词时重建节点，禁止逐帧调用。 */
  setLyrics(source: string, durationSeconds = 0): void {
    this.lrcText = source;
    this.durationSeconds = durationSeconds;
    const duration = this.resolvedDuration(durationSeconds);
    this.lines = parseLrc(source, duration);
    this.pendingSeekTime = null;
    if (!this.ready || !this.content || !this.contentOpacity) return;
    this.contentOpacity.opacity = 0;
    this.scrollView!.stopAutoScroll();
    // 只移除本组件自己创建的歌词，content 不应混放其他业务节点。
    for (const row of this.rows) {
      row.node.removeFromParent();
      row.node.destroy();
    }
    this.rows = this.lines.map((line, index) => this.createRow(line, index));
    this.loadedSource = source;
    this.loadedDuration = duration;
    this.measureRows();
    this.syncOptions();
    this.animator.load(this.lines, this.geometry, this.playbackTime(), this.now);
    this.renderFrame();
    this.contentOpacity.opacity = 255;
  }

  /**
   * 歌词更新函数。自定义播放器每帧传实际秒数，暂停时保留该值，seek 后传新值。
   * 如果绑定 AudioSource，update() 会直接读取它；不要再同时提供另一套媒体时钟。
   */
  updateLyrics(playbackTimeSeconds: number): void {
    if (!Number.isFinite(playbackTimeSeconds)) return;
    this.externalPlaybackTime = Math.max(0, playbackTimeSeconds);
    if (!this.ready || !this.enabledInHierarchy) return;
    this.syncOptions();
    this.animator.updateLyrics(this.externalPlaybackTime, this.now);
    this.renderFrame();
  }

  /**
   * 进度条 seek 专用入口：立即退出手动浏览；同一歌词行也平滑恢复居中。
   * 先由调用者修改音乐位置，再调用本方法。它不会代替播放器执行音频 seek。
   */
  seekLyrics(playbackTimeSeconds: number): void {
    if (!Number.isFinite(playbackTimeSeconds)) return;
    this.externalPlaybackTime = Math.max(0, playbackTimeSeconds);
    if (this.audioSource) {
      // AudioSource seek 是异步的；短暂过滤尚未更新的旧位置，防止新旧行来回闪动。
      this.pendingSeekTime = this.externalPlaybackTime;
      this.seekIssuedAt = this.now;
    }
    if (!this.ready || !this.enabledInHierarchy) return;
    this.syncOptions();
    this.animator.observeUserScroll(this.now, this.actualOffset());
    this.scrollView!.stopAutoScroll();
    this.animator.updateLyrics(this.externalPlaybackTime, this.now, true);
    this.renderFrame();
  }

  update(dt: number): void {
    if (!this.ready) return;
    // UI 帧时钟独立于音乐时间：暂停后正在执行的过渡仍可平稳结束。
    this.now += Math.max(0, Number.isFinite(dt) ? dt : 0);
    this.syncOptions();
    this.animator.observeUserScroll(this.now, this.actualOffset(), this.scrollView!.isAutoScrolling());
    this.animator.updateLyrics(this.playbackTime(), this.now);
    this.renderFrame();
  }

  /** 改字体/字号/间距或旋转屏幕后重测真实行高；正常切行不重新布局。 */
  refreshLayout(): void {
    if (!this.ready || this.layoutInProgress) return;
    const previousOffset = this.actualOffset();
    this.measureRows();
    // SIZE_CHANGED 可能发生在手动浏览期间，布局重建不能把用户位置重置到顶部。
    this.applyOffset(Math.max(0, Math.min(previousOffset, this.geometry.contentHeight - this.geometry.viewportHeight)));
    this.syncOptions();
    this.animator.setGeometry(this.geometry, this.now);
    this.renderFrame();
  }

  private createRow(line: LyricLine, index: number): LyricRow {
    const node = new Node(`Lyric-${index}`);
    node.layer = this.content!.layer;
    this.content!.addChild(node);
    const transform = node.addComponent(UITransform);
    transform.setAnchorPoint(0.5, 0.5);
    const label = node.addComponent(Label);
    label.string = line.text || '·';
    label.horizontalAlign = Label.HorizontalAlign.CENTER;
    label.verticalAlign = Label.VerticalAlign.CENTER;
    label.overflow = Label.Overflow.RESIZE_HEIGHT;
    label.enableWrapText = true;
    const opacity = node.addComponent(UIOpacity);
    return { node, label, transform, opacity, color: new Color(), lastEmphasis: NaN };
  }

  private measureRows(): void {
    if (!this.viewport || !this.contentTransform || !this.content) return;
    this.layoutInProgress = true;
    try {
      const width = Math.max(1, this.viewport.width);
      const height = Math.max(1, this.viewport.height);
      // 预留放大后的宽度，最长歌词换行后也不被 Mask 裁掉。
      const rowWidth = Math.max(1, (width - 2 * Math.max(0, this.horizontalPadding)) / Math.max(1, this.activeScale));
      let top = height / 2;
      const centers: number[] = [];
      for (let i = 0; i < this.rows.length; i++) {
        const row = this.rows[i];
        row.transform.setContentSize(rowWidth, Math.max(1, this.lineHeight));
        row.label.font = this.font;
        row.label.fontSize = Math.max(1, this.fontSize);
        row.label.lineHeight = Math.max(1, this.lineHeight);
        // 强制测量一次，拿到长句/翻译的实际高度，不用固定行高猜中心。
        row.label.updateRenderData(true);
        const rowHeight = Math.max(1, row.transform.height);
        const center = top + rowHeight / 2;
        centers.push(center);
        row.node.setPosition(0, -center, 0);
        top += rowHeight + (i + 1 < this.rows.length ? Math.max(0, this.rowGap) : 0);
        row.lastEmphasis = NaN;
      }
      const contentHeight = Math.max(height, top + height / 2);
      this.contentTransform.setContentSize(width, contentHeight);
      // content 的顶部中点与 viewport 顶部中点对齐；scrollToOffset 会继续移动它。
      this.content.setPosition(width * (0.5 - this.viewport.anchorX), height * (1 - this.viewport.anchorY), 0);
      this.geometry = { rowCenters: centers, viewportHeight: height, contentHeight };
      this.measuredWidth = width;
    } finally {
      this.layoutInProgress = false;
    }
  }

  private syncOptions(): void {
    Object.assign(this.animator.options, {
      transitionDuration: Math.max(0, this.transitionDuration),
      scrollDuration: Math.max(0, this.scrollDuration),
      idleResumeDelay: Math.max(0, this.idleResumeDelay),
      activeScale: Math.max(1, this.activeScale),
      inactiveOpacity: Math.max(0, Math.min(1, this.inactiveOpacity)),
    });
  }

  private playbackTime(): number {
    if (!this.audioSource) return this.externalPlaybackTime;
    const actual = this.audioSource.currentTime;
    if (this.pendingSeekTime !== null) {
      const elapsed = this.now - this.seekIssuedAt;
      const upper = this.pendingSeekTime + (this.audioSource.playing ? elapsed : 0) + 0.05;
      if ((actual >= this.pendingSeekTime - 0.01 && actual <= upper) || elapsed >= 1) {
        this.pendingSeekTime = null; // 确认或超时后，实际音频位置始终是最终依据。
      } else return this.pendingSeekTime;
    }
    return actual;
  }

  private resolvedDuration(duration: number): number {
    const value = duration > 0 ? duration : this.audioSource?.duration;
    return value && Number.isFinite(value) && value > 0 ? value : Infinity;
  }

  private actualOffset(): number {
    return this.scrollView?.getScrollOffset().y ?? 0;
  }

  /** 切换动画由核心 switchLine() 建立；此处同一帧应用颜色、缩放、透明度和位移。 */
  private renderFrame(): void {
    if (!this.scrollView) return;
    const frame = this.animator.frame(this.now);
    for (let i = 0; i < this.rows.length; i++) {
      const row = this.rows[i];
      const emphasis = frame.emphasis[i] ?? 0;
      if (emphasis === row.lastEmphasis) continue;
      row.lastEmphasis = emphasis;
      const style = this.animator.lineStyle(i);
      row.node.setScale(style.scale, style.scale, 1);
      row.opacity.opacity = Math.round(style.opacity * 255);
      Color.lerp(row.color, this.inactiveColor, this.activeColor, emphasis);
      row.label.color = row.color;
    }
    if (frame.manualBrowsing || Math.abs(this.actualOffset() - frame.scrollOffset) <= 0.001) return;
    this.applyOffset(frame.scrollOffset);
  }

  private applyOffset(value: number): void {
    if (!this.scrollView) return;
    this.writingOffset = true;
    try {
      this.scrollView.stopAutoScroll();
      this.offset.set(0, value);
      // 参数 0 表示应用“这一帧”的位置；平滑曲线由核心 0.3s 帧动画计算，非硬跳目标。
      this.scrollView.scrollToOffset(this.offset, 0, false);
    } finally {
      this.writingOffset = false;
    }
  }

  private onTouchStart(): void {
    if (this.writingOffset) return;
    this.animator.beginUserScroll(this.now, this.actualOffset());
  }

  private onTouchEnd(event: EventTouch): void {
    if (event.simulate || this.writingOffset) return;
    this.animator.endUserScroll(this.now, this.actualOffset());
  }

  private onMouseWheel(): void {
    if (this.writingOffset) return;
    this.animator.beginUserScroll(this.now, this.actualOffset());
    this.animator.endUserScroll(this.now, this.actualOffset());
  }

  private onScrolling(): void {
    if (!this.writingOffset) this.animator.observeUserScroll(this.now, this.actualOffset());
  }
}
