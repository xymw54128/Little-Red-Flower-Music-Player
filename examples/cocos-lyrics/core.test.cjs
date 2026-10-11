const assert = require('node:assert/strict');
const { test } = require('node:test');
const { parseLrc, findLyricIndex, LyricAnimator, easeOut } = require('./LyricsCore.ts');

const lyrics = () => parseLrc('[00:01.00]一\n[00:01.10]二\n[00:04.25]三', 10);
const geometry = { rowCenters: [100, 220, 400], viewportHeight: 200, contentHeight: 600 };
const create = (time = 1) => {
  const animation = new LyricAnimator();
  animation.load(lyrics(), geometry, time, 0);
  return animation;
};
const near = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-9, `${actual} != ${expected}`);

test('LRC fractions, metadata, repeated tags, offset and actual end times', () => {
  const lines = parseLrc('[ar:浪雨林兴云魔王]\n[00:02.25][00:05.100]重复\n[00:01.5]开始\n[offset:-250]', 9);
  assert.deepEqual(lines, [
    { startTime: 1.25, endTime: 2, text: '开始' },
    { startTime: 2, endTime: 4.85, text: '重复' },
    { startTime: 4.85, endTime: 9, text: '重复' },
  ]);
});

test('same timestamp translations share one node and do not produce zero length intervals', () => {
  assert.deepEqual(parseLrc('[00:02.00]你好\n[00:02.00]Hello\n[00:02.00]你好\n[00:04.00]下一句'), [
    { startTime: 2, endTime: 4, text: '你好\nHello' },
    { startTime: 4, endTime: Infinity, text: '下一句' },
  ]);
});

test('malformed times ignored; timed instrumental gaps remain; offset clamped safely', () => {
  assert.deepEqual(parseLrc('[00:61.00]无效\n[00:02.1234]无效\n[00:00.10]前奏\n[00:03.00]\n[offset:-200]'), [
    { startTime: 0, endTime: 2.8, text: '前奏' },
    { startTime: 2.8, endTime: Infinity, text: '' },
  ]);
});

test('binary time match is exact at irregular timestamps, before first line and reverse seek', () => {
  const lines = lyrics();
  assert.equal(findLyricIndex(lines, 0.999), -1);
  assert.equal(findLyricIndex(lines, 1.099999), 0);
  assert.equal(findLyricIndex(lines, 1.1), 1);
  assert.equal(findLyricIndex(lines, 4.24999), 1);
  assert.equal(findLyricIndex(lines, 4.25), 2);
  assert.equal(findLyricIndex(lines, 1), 0);
  assert.equal(findLyricIndex(lines, NaN), -1);
});

test('old and new line fade, opacity and scale share the same easing fraction', () => {
  const animation = create();
  animation.updateLyrics(1.1, 0.1);
  const frame = animation.frame(0.225);
  near(frame.emphasis[0], 1 - easeOut(0.5));
  near(frame.emphasis[1], easeOut(0.5));
  near(frame.scrollOffset, 120 * easeOut(0.125 / 0.3));
  const newStyle = animation.lineStyle(1);
  near(newStyle.scale, 1 + 0.05 * easeOut(0.5));
  near(newStyle.opacity, 0.6 + 0.4 * easeOut(0.5));
});

test('250ms emphasis and 300ms centering start together and settle once', () => {
  const animation = create();
  animation.updateLyrics(1.1, 0.1);
  const frame = animation.frame(0.35);
  near(frame.emphasis[1], 1);
  assert.equal(frame.animating, true);
  const completed = animation.frame(0.4);
  near(completed.scrollOffset, 120);
  assert.equal(completed.animating, false);
});

test('interruption rebases every still fading row and scroll from its current visual state', () => {
  const animation = create();
  animation.updateLyrics(1.1, 0.1);
  const old = animation.frame(0.2);
  const oldWeights = [...old.emphasis];
  const oldOffset = old.scrollOffset;
  const generation = old.generation;
  animation.updateLyrics(4.25, 0.2);
  const rebased = animation.frame(0.2);
  assert.deepEqual(rebased.emphasis, oldWeights);
  near(rebased.scrollOffset, oldOffset);
  assert.equal(rebased.generation, generation + 1);
  const settled = animation.frame(0.5);
  assert.deepEqual(settled.emphasis, [0, 0, 1]);
  near(settled.scrollOffset, 300);
});

test('identical playback updates cannot restart an animation or increment its generation', () => {
  const animation = create();
  animation.updateLyrics(1.1, 0.1);
  const generation = animation.frame(0.1).generation;
  for (let i = 1; i <= 30; i++) animation.updateLyrics(1.1 + i / 1000, 0.1 + i / 1000);
  assert.equal(animation.frame(0.4).generation, generation);
  near(animation.frame(0.4).emphasis[1], 1);
});

test('paused playback retains its line while UI animation and a paused seek can complete', () => {
  const animation = create();
  animation.updateLyrics(4.25, 0.1);
  animation.updateLyrics(4.25, 0.2);
  assert.equal(animation.frame(0.4).currentIndex, 2);
  assert.equal(animation.frame(0.4).animating, false);
  animation.updateLyrics(1, 0.5);
  assert.equal(animation.frame(0.5).currentIndex, 0);
  assert.equal(animation.frame(0.5).animating, true);
  near(animation.frame(0.8).scrollOffset, 0);
});

test('manual browsing pauses only centering and resumes 1.5s after the last interaction', () => {
  const animation = create();
  animation.beginUserScroll(0.1, 250);
  animation.updateLyrics(1.1, 0.2);
  animation.endUserScroll(0.3, 250);
  animation.updateLyrics(1.2, 1.799);
  const before = animation.frame(1.799);
  assert.equal(before.manualBrowsing, true);
  near(before.scrollOffset, 250);
  near(before.emphasis[1], 1);
  animation.updateLyrics(1.2, 1.8);
  const starts = animation.frame(1.8);
  assert.equal(starts.manualBrowsing, false);
  near(starts.scrollOffset, 250);
  near(animation.frame(2.1).scrollOffset, 120);
});

test('finger held down or inertial manual movement prevents premature reattachment', () => {
  const animation = create();
  animation.beginUserScroll(0, 200);
  animation.updateLyrics(4.3, 10);
  assert.equal(animation.frame(10).manualBrowsing, true);
  animation.endUserScroll(10, 200);
  animation.observeUserScroll(11, 230);
  animation.updateLyrics(4.3, 12.49);
  assert.equal(animation.frame(12.49).manualBrowsing, true);
  animation.updateLyrics(4.3, 12.5);
  near(animation.frame(12.8).scrollOffset, 300);
});

test('reentering a hidden page initializes to exact current row without playing a stale transition', () => {
  const animation = create();
  animation.beginUserScroll(1, 200);
  animation.load(lyrics(), geometry, 4.3, 10);
  const frame = animation.frame(10);
  assert.deepEqual(frame.emphasis, [0, 0, 1]);
  near(frame.scrollOffset, 300);
  assert.equal(frame.manualBrowsing, false);
  assert.equal(frame.animating, false);
});

test('pending inertial scrolling postpones the idle deadline even at a nearly stationary frame', () => {
  const animation = create();
  animation.beginUserScroll(0, 250);
  animation.endUserScroll(0.1, 250);
  animation.observeUserScroll(2, 250, true);
  animation.updateLyrics(4.3, 2);
  assert.equal(animation.frame(2).manualBrowsing, true);
  animation.observeUserScroll(2.1, 250, false);
  animation.updateLyrics(4.3, 3.499);
  assert.equal(animation.frame(3.499).manualBrowsing, true);
  animation.updateLyrics(4.3, 3.5);
  assert.equal(animation.frame(3.5).manualBrowsing, false);
});

test('paused same-index seek immediately restores centering from the manually browsed offset', () => {
  const animation = create();
  animation.beginUserScroll(0.1, 250);
  const before = animation.frame(0.1);
  const weights = [...before.emphasis];
  animation.updateLyrics(1.05, 0.2, true);
  const starts = animation.frame(0.2);
  assert.equal(starts.manualBrowsing, false);
  assert.equal(starts.currentIndex, 0);
  assert.deepEqual(starts.emphasis, weights);
  near(starts.scrollOffset, 250);
  assert.equal(starts.animating, true);
  near(animation.frame(0.35).scrollOffset, 250 * (1 - easeOut(0.5)));
  near(animation.frame(0.5).scrollOffset, 0);
});

test('centering uses measured variable-height row centers, clamped to real scroll bounds', () => {
  const animation = create();
  animation.setGeometry({ rowCenters: [100, 280, 590], viewportHeight: 200, contentHeight: 650 }, 0);
  animation.updateLyrics(4.3, 0.1);
  near(animation.frame(0.4).scrollOffset, 450);
});

test('empty lyrics, zero durations, and backward wall-clock samples are safe', () => {
  const animation = new LyricAnimator({ transitionDuration: 0, scrollDuration: 0 });
  animation.load([], { rowCenters: [], viewportHeight: 100, contentHeight: 100 }, 10, 0);
  assert.equal(animation.frame(0).currentIndex, -1);
  animation.load(lyrics(), geometry, 1, 1);
  animation.updateLyrics(4.3, 1.1);
  assert.deepEqual(animation.frame(1.1).emphasis, [0, 0, 1]);
  near(animation.frame(0.5).scrollOffset, 300);
  assert.equal(animation.frame(1.2).animating, false);
});
