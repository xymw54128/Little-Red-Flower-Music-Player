import SwiftUI

@main
@MainActor
struct XiaohonghuaApp: App {
    @StateObject private var library = MusicLibrary()
    @StateObject private var playback = Playback()
    init() {
        // Prewarm the caption face before starting the animation clock.
        _ = Brand.font("kai", size: 24)
    }
    var body: some Scene {
        WindowGroup { RootView().environmentObject(library).environmentObject(playback).preferredColorScheme(library.state.settings.nightMode ? .dark : .light) }
    }
}

struct RootView: View {
    @EnvironmentObject private var library: MusicLibrary
    @EnvironmentObject private var playback: Playback
    @Environment(\.scenePhase) private var phase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var tab = 0
    @State private var showPlayer = false
    @State private var launchFinished = false
    @State private var flowerFrame = CGRect.zero
    @State private var rootPages: [Int: Bool] = [:]

    var body: some View {
        ZStack {
            TabView(selection: $tab) {
                NavigationStack {
                    LibraryView(hiddenFlower: !launchFinished)
                        .safeAreaInset(edge: .bottom, spacing: 0) { miniPlayer }
                }.tabItem { Label("音乐库", systemImage: "music.note.list") }.tag(0)
                NavigationStack {
                    PlaylistsView().safeAreaInset(edge: .bottom, spacing: 0) { miniPlayer }
                }.tabItem { Label("歌单", systemImage: "text.line.first.and.arrowtriangle.forward") }.tag(1)
                NavigationStack {
                    SettingsView().safeAreaInset(edge: .bottom, spacing: 0) { miniPlayer }
                }.tabItem { Label("设置", systemImage: "gearshape") }.tag(2)
            }.tint(Brand.accent(library.state.settings)).allowsHitTesting(library.ready && launchFinished)
                .simultaneousGesture(DragGesture(minimumDistance: 30).onEnded { value in
                    guard rootPages[tab] == true, value.startLocation.x > 44,
                          abs(value.translation.width) > 70, abs(value.translation.width) > abs(value.translation.height) * 1.5 else { return }
                    let target = min(max(tab + (value.translation.width < 0 ? 1 : -1), 0), 2)
                    withAnimation(reduceMotion ? nil : .easeOut(duration: 0.28)) { tab = target }
                })
            if !launchFinished, !reduceMotion { LaunchView(target: flowerFrame, active: phase == .active) { launchFinished = true }.zIndex(10) }
        }.background(Brand.paper).foregroundStyle(Brand.ink)
            .onPreferenceChange(RootPagePreferenceKey.self) { rootPages = $0 }
            .onPreferenceChange(FlowerFrameKey.self) { if $0 != .zero { flowerFrame = $0 } }
            .fullScreenCover(isPresented: $showPlayer) { PlayerView() }
            .alert("小红花", isPresented: Binding(get: { library.message != nil && !showPlayer }, set: { if !$0 { library.message = nil } })) {
                Button("知道了", role: .cancel) { library.message = nil }
            } message: { Text(library.message ?? "") }
            .task {
                if reduceMotion { launchFinished = true }
                #if DEBUG
                if ProcessInfo.processInfo.environment["XIAOHONGHUA_UI_TESTING"] == "1" { launchFinished = true }
                #endif
                await library.start()
                playback.connect(library)
            }
            .task(id: phase) {
                playback.setForeground(phase == .active)
                if phase != .active && library.ready { playback.savePosition(); library.persist(immediate: true) }
            }
            .onChange(of: reduceMotion) { _, value in if value { launchFinished = true } }
    }
    @ViewBuilder private var miniPlayer: some View {
        if playback.current != nil { MiniPlayer(active: !showPlayer) { showPlayer = true } }
    }
}

struct MiniPlayer: View {
    @EnvironmentObject private var playback: Playback
    @EnvironmentObject private var library: MusicLibrary
    var active = true
    var open: () -> Void
    var body: some View {
        HStack(spacing: 4) {
            Button(action: open) {
                HStack(spacing: 8) {
                    ZStack {
                        RainbowFlower(playing: playback.playing, orbit: true, active: active).frame(width: 64, height: 64)
                        Artwork(name: playback.current?.artwork ?? "", radius: 22).frame(width: 44, height: 44)
                        MiniProgressRing(progress: playback.progress, color: Brand.accent(library.state.settings)).frame(width: 46, height: 46)
                    }.frame(width: 64, height: 64)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(playback.current?.title ?? "").font(.system(size: 16, weight: .medium)).lineLimit(1).accessibilityIdentifier("mini.title")
                        Text(playback.current?.artist ?? "").font(.system(size: 12)).foregroundStyle(Brand.muted).lineLimit(1)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }.contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityLabel("打开正在播放").accessibilityIdentifier("mini.open").accessibilityValue(playback.current?.title ?? "")
            Button { playback.previous() } label: { Image(systemName: "backward.end.fill").frame(width: 44, height: 44) }.accessibilityLabel("上一首")
            Button { playback.toggle() } label: {
                Group { if playback.loading { ProgressView() } else { Image(systemName: playback.playing ? "pause.fill" : "play.fill") } }.frame(width: 44, height: 44)
            }.accessibilityLabel(playback.playing ? "暂停" : "播放")
            Button { playback.next() } label: { Image(systemName: "forward.end.fill").frame(width: 44, height: 44) }.accessibilityLabel("下一首")
        }.padding(.horizontal, 8).padding(.vertical, 2)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 32, style: .continuous))
            .overlay(alignment: .bottomTrailing) { FiveLeafBranch().frame(width: 148, height: 17).padding(.trailing, 9).padding(.bottom, 1) }
            .buttonStyle(.plain).foregroundStyle(Brand.ink)
    }
}
