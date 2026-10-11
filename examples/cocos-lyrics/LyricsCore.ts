/** 秒为统一时间单位；endTime 是下一条的 startTime，末条使用音频时长或 Infinity。 */
export interface LyricLine {
  startTime: number;
  endTime: number;
  text: string;
}

/**
 * 标准 LRC 解析函数，与 Cocos 无关。
 * 支持 [mm:ss.xx]、1/2/3 位小数、多个时间标签、全局 offset 和同时间翻译。
 * 同一时间的不同文本合为一个多行 Label，防止零长度歌词与高亮争抢。
 */
export function parseLrc(source: string, duration = Infinity): LyricLine[] {
  const entries: { startTime: number; text: string; order: number }[] = [];
  let offset = 0;
  for (const match of source.matchAll(/\[offset:([+-]?\d+)\]/gi)) offset = Number(match[1]) / 1000;
  for (const raw of source.replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const tags = [...raw.matchAll(/\[([^\]]+)\]/g)];
    const text = raw.replace(/\[[^\]]+\]/g, '').trim();
    for (const tag of tags) {
      const time = /^(\d+):([0-5]\d)(?:[.:](\d{1,3}))?$/.exec(tag[1]);
      if (!time) continue;
      const seconds = Number(time[1]) * 60 + Number(time[2]) + Number(`0.${time[3] ?? '0'}`) + offset;
      if (Number.isFinite(seconds)) entries.push({ startTime: Math.max(0, seconds), text, order: entries.length });
    }
  }
  entries.sort((a, b) => a.startTime - b.startTime || a.order - b.order);
  const lines: LyricLine[] = [];
  for (const entry of entries) {
    const previous = lines[lines.length - 1];
    if (previous && Math.abs(previous.startTime - entry.startTime) < 1e-9) {
      if (entry.text && !previous.text.split('\n').includes(entry.text)) {
        previous.text = previous.text ? `${previous.text}\n${entry.text}` : entry.text;
      }
    } else {
      lines.push({ startTime: entry.startTime, endTime: Infinity, text: entry.text });
    }
  }
  for (let i = 0; i < lines.length; i++) {
    const end = i + 1 < lines.length ? lines[i + 1].startTime : duration;
    lines[i].endTime = Number.isFinite(end) ? Math.max(lines[i].startTime, end) : Infinity;
  }
  return lines;
}

/** 精确二分匹配；前奏返回 -1，播放结束时保留最后一行，避免尾帧闪灭。 */
export function findLyricIndex(lines: readonly LyricLine[], timeSeconds: number): number {
  if (!Number.isFinite(timeSeconds)) return -1;
  let low = 0;
  let high = lines.length - 1;
  let result = -1;
  while (low <= high) {
    const middle = (low + high) >>> 1;
    if (lines[middle].startTime <= timeSeconds) {
      result = middle;
      low = middle + 1;
    } else high = middle - 1;
  }
  return result;
}

export function easeOut(progress: number): number {
  const value = Math.max(0, Math.min(1, progress));
  return 1 - Math.pow(1 - value, 3);
}

export interface LyricGeometry {
  /** 从 content 顶部量起的每行几何中心，已经包括换行后的真实行高。 */
  rowCenters: readonly number[];
  viewportHeight: number;
  contentHeight: number;
}

export interface AnimationOptions {
  transitionDuration: number;
  scrollDuration: number;
  idleResumeDelay: number;
  activeScale: number;
  inactiveOpacity: number;
}

export interface LyricFrame {
  currentIndex: number;
  /** 0=非当前样式，1=当前样式；这是复用数组，需要保存快照时请自行复制。 */
  emphasis: readonly number[];
  scrollOffset: number;
  animating: boolean;
  manualBrowsing: boolean;
  generation: number;
}

/**
 * 歌词动画状态机：最多只有一组字体过渡和一组居中过渡。
 * 不创建逐行 tween、定时器或完成回调；所有视觉属性共享 frame(now) 的单帧时钟。
 * 打断时先求旧动画的当前值，再以它为起点，generation 立即失效旧一轮状态。
 */
export class LyricAnimator {
  readonly options: AnimationOptions;
  private lines: readonly LyricLine[] = [];
  private geometry: LyricGeometry = { rowCenters: [], viewportHeight: 0, contentHeight: 0 };
  private weights: number[] = [];
  private styleFrom: number[] = [];
  private currentIndex = -1;
  private styleStarted = 0;
  private styleRunning = false;
  private scrollStarted = 0;
  private scrollFrom = 0;
  private scrollTo = 0;
  private scrollOffset = 0;
  private scrollRunning = false;
  private manualBrowsing = false;
  private pointerDown = false;
  private lastInteraction = 0;
  private lastNow = 0;
  private generation = 0;

  constructor(options: Partial<AnimationOptions> = {}) {
    this.options = {
      transitionDuration: 0.25,
      scrollDuration: 0.3,
      idleResumeDelay: 1.5,
      activeScale: 1.05,
      inactiveOpacity: 0.6,
      ...options,
    };
  }

  /** 页面显示前调用。一次性按当前媒体时间布局，隐藏期间完成定位，不播放旧位置的动画。 */
  load(lines: readonly LyricLine[], geometry: LyricGeometry, playbackTime: number, now: number): void {
    this.lines = lines;
    this.geometry = geometry;
    this.lastNow = Math.max(0, Number.isFinite(now) ? now : 0);
    this.currentIndex = findLyricIndex(lines, playbackTime);
    this.weights = lines.map((_, i) => i === this.currentIndex ? 1 : 0);
    this.styleFrom = [...this.weights];
    this.manualBrowsing = this.pointerDown = false;
    this.styleRunning = this.scrollRunning = false;
    this.scrollOffset = this.centerOffset(this.currentIndex);
    this.generation++;
  }

  /** 仅在字体、宽度或容器大小变化时调用，正常逐帧播放不能重新布局。 */
  setGeometry(geometry: LyricGeometry, now: number): void {
    now = this.normalizeNow(now);
    this.sample(now);
    this.geometry = geometry;
    if (!this.manualBrowsing) this.beginCentering(now);
  }

  /** 歌词更新函数：音乐时间决定目标行，帧时间决定视觉过渡，暂停不冻结 UI 动画。 */
  updateLyrics(playbackTime: number, now: number, forceFollow = false): void {
    now = this.normalizeNow(now);
    const target = findLyricIndex(this.lines, playbackTime);
    if (forceFollow) {
      this.sample(now);
      this.manualBrowsing = this.pointerDown = false;
      // seek 即使落在同一歌词区间，也应该从用户当前浏览位置恢复居中。
      if (target === this.currentIndex) {
        this.generation++;
        this.beginCentering(now);
      }
    }
    if (target !== this.currentIndex) this.switchLine(target, now);
    if (this.manualBrowsing && !this.pointerDown && now - this.lastInteraction >= this.options.idleResumeDelay - 1e-9) {
      this.sample(now);
      this.manualBrowsing = false;
      this.generation++;
      this.beginCentering(now);
    }
  }

  /** 切换动画函数：新旧行颜色、透明度、缩放在同一时间起步，滚动也从此帧起步。 */
  switchLine(target: number, now: number): void {
    if (target === this.currentIndex) return;
    now = this.normalizeNow(now);
    this.sample(now); // 必须先采样再替换目标，不能先重置旧值。
    this.styleFrom = [...this.weights];
    this.currentIndex = target;
    this.styleStarted = now;
    this.styleRunning = true;
    this.generation++;
    if (!this.manualBrowsing) this.beginCentering(now);
    this.sample(now);
  }

  beginUserScroll(now: number, actualOffset: number): void {
    now = this.normalizeNow(now);
    this.sample(now);
    this.manualBrowsing = this.pointerDown = true;
    this.scrollRunning = false;
    this.scrollOffset = actualOffset;
    this.lastInteraction = now;
    this.generation++;
  }

  /** 用户拖动/惯性期间把真实 ScrollView 偏移送回；程序居中不能调用本方法。 */
  observeUserScroll(now: number, actualOffset: number, stillMoving = false): void {
    if (!this.manualBrowsing) return;
    now = this.normalizeNow(now);
    if (stillMoving || Math.abs(actualOffset - this.scrollOffset) > 0.01) this.lastInteraction = now;
    this.scrollOffset = actualOffset;
  }

  endUserScroll(now: number, actualOffset: number): void {
    if (!this.manualBrowsing) return;
    this.pointerDown = false;
    this.lastInteraction = this.normalizeNow(now);
    this.scrollOffset = actualOffset;
  }

  /** 单帧渲染入口；animation lock 由两段唯一活动状态共同决定，不阻塞新的 seek。 */
  frame(now: number): LyricFrame {
    this.sample(this.normalizeNow(now));
    return {
      currentIndex: this.currentIndex,
      emphasis: this.weights,
      scrollOffset: this.scrollOffset,
      animating: this.styleRunning || this.scrollRunning,
      manualBrowsing: this.manualBrowsing,
      generation: this.generation,
    };
  }

  lineStyle(index: number): { scale: number; opacity: number; emphasis: number } {
    const emphasis = this.weights[index] ?? 0;
    return {
      scale: 1 + (this.options.activeScale - 1) * emphasis,
      opacity: this.options.inactiveOpacity + (1 - this.options.inactiveOpacity) * emphasis,
      emphasis,
    };
  }

  private normalizeNow(now: number): number {
    if (Number.isFinite(now)) this.lastNow = Math.max(this.lastNow, now);
    return this.lastNow;
  }

  private centerOffset(index: number): number {
    if (index < 0 || index >= this.geometry.rowCenters.length) return 0;
    const maximum = Math.max(0, this.geometry.contentHeight - this.geometry.viewportHeight);
    return Math.max(0, Math.min(maximum, this.geometry.rowCenters[index] - this.geometry.viewportHeight / 2));
  }

  private beginCentering(now: number): void {
    this.scrollFrom = this.scrollOffset;
    this.scrollTo = this.centerOffset(this.currentIndex);
    this.scrollStarted = now;
    this.scrollRunning = Math.abs(this.scrollTo - this.scrollFrom) > 1e-7;
  }

  private sample(now: number): void {
    if (this.styleRunning) {
      const duration = Math.max(0, this.options.transitionDuration);
      const elapsed = Math.max(0, now - this.styleStarted);
      const progress = duration === 0 || elapsed >= duration - 1e-9 ? 1 : elapsed / duration;
      const eased = easeOut(progress);
      for (let i = 0; i < this.weights.length; i++) {
        const target = i === this.currentIndex ? 1 : 0;
        this.weights[i] = this.styleFrom[i] + (target - this.styleFrom[i]) * eased;
      }
      this.styleRunning = progress < 1;
    }
    if (this.scrollRunning && !this.manualBrowsing) {
      const duration = Math.max(0, this.options.scrollDuration);
      const elapsed = Math.max(0, now - this.scrollStarted);
      const progress = duration === 0 || elapsed >= duration - 1e-9 ? 1 : elapsed / duration;
      this.scrollOffset = this.scrollFrom + (this.scrollTo - this.scrollFrom) * easeOut(progress);
      this.scrollRunning = progress < 1;
    }
  }
}
