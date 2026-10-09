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
        WindowGroup { RootView().environmentObject(library).environmentObject(playback).preferredColorScheme(.light) }
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
            }.tint(Brand.accent(library.state.settings))
            if !launchFinished, !reduceMotion { LaunchView(target: flowerFrame, active: phase == .active) { launchFinished = true }.zIndex(10) }
        }.background(Brand.paper).foregroundStyle(Brand.ink)
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
                guard phase == .active else {
                    if library.ready { playback.savePosition(); library.persist(immediate: true) }
                    return
                }
                while !Task.isCancelled {
                    if library.state.settings.monitor { await library.refresh(visible: false) }
                    do { try await Task.sleep(for: .seconds(5)) } catch { break }
                }
            }
            .onChange(of: library.state.songs) { _, _ in playback.updateCurrentMetadata() }
            .onChange(of: library.state.settings.lockScreen) { _, _ in playback.refreshLockScreen() }
            .onChange(of: reduceMotion) { _, value in if value { launchFinished = true } }
    }
    @ViewBuilder private var miniPlayer: some View {
        if playback.current != nil { MiniPlayer { showPlayer = true } }
    }
}

struct MiniPlayer: View {
    @EnvironmentObject private var playback: Playback
    var open: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Button(action: open) {
                HStack(spacing: 12) {
                    Artwork(name: playback.current?.artwork ?? "", radius: 13).frame(width: 44, height: 44)
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
        }.padding(.horizontal, 16).padding(.vertical, 10).background(.regularMaterial)
            .overlay(alignment: .top) { Rectangle().fill(Brand.ink.opacity(0.05)).frame(height: 0.5) }
            .buttonStyle(.plain)
    }
}
