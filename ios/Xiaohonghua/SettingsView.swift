import SwiftUI
import UniformTypeIdentifiers
import PhotosUI

struct SettingsView: View {
    @EnvironmentObject private var library: MusicLibrary
    var body: some View {
        VStack(spacing: 0) {
            FlowerHeader(title: "设置", icon: "FlowerSettings")
            Form {
                Section("音乐") {
                    NavigationLink { SourcesView() } label: { Label("歌曲文件与文件夹", systemImage: "folder") }
                    NavigationLink { LyricSettingsView() } label: { Label("歌词字体与动态效果", systemImage: "textformat") }
                    NavigationLink { BackgroundSettingsView() } label: { Label("播放背景", systemImage: "photo") }
                    Toggle("系统锁屏音乐控件", isOn: Binding(get: { library.state.settings.lockScreen }, set: { value in library.update { $0.settings.lockScreen = value } }))
                }
                Section("主题颜色") {
                    HStack(spacing: 14) {
                        ForEach(Array(Brand.themes.enumerated()), id: \.offset) { index, theme in
                            Button { library.update { $0.settings.theme = index } } label: {
                                Circle().fill(Color(hex: theme.1)).frame(width: 34, height: 34)
                                    .overlay { if library.state.settings.theme == index { Image(systemName: "checkmark").foregroundStyle(.white).font(.system(size: 14, weight: .semibold)) } }
                                    .frame(maxWidth: .infinity)
                            }.buttonStyle(.plain).accessibilityLabel(theme.0).accessibilityAddTraits(library.state.settings.theme == index ? .isSelected : [])
                        }
                    }.padding(.vertical, 6)
                }
                Section {
                    NavigationLink { BackupView() } label: { Label("备份与恢复", systemImage: "arrow.triangle.2.circlepath") }
                    NavigationLink { AboutView() } label: { Label("关于小红花", systemImage: "info.circle") }
                }
                Section { Text("小红花 1.0.1\n作者：浪雨林兴云魔王").font(.footnote).foregroundStyle(Brand.muted).frame(maxWidth: .infinity).multilineTextAlignment(.center).listRowBackground(Color.clear) }
            }.scrollContentBackground(.hidden)
        }.background(Brand.paper).toolbar(.hidden, for: .navigationBar)
    }
}

struct SourcesView: View {
    @EnvironmentObject private var library: MusicLibrary
    @State private var importing = false
    @State private var folder = true
    @State private var replacing: String?
    @State private var remove: MusicSource?
    var body: some View {
        Form {
            Section {
                Button { Task { await library.refresh(visible: true, force: true) } } label: { HStack { Label("刷新音乐库与标签", systemImage: "arrow.clockwise"); Spacer(); if library.visibleScan { ProgressView() } } }.disabled(library.scanning)
                Toggle("前台自动监测文件变化", isOn: Binding(get: { library.state.settings.monitor }, set: { value in library.update { $0.settings.monitor = value } }))
                Button("添加音乐文件夹", systemImage: "folder.badge.plus") { folder = true; replacing = nil; importing = true }
                Button("添加音乐文件", systemImage: "music.note") { folder = false; replacing = nil; importing = true }
            } footer: { Text("自动刷新保持安静。前台每 5 秒检查增删改，每 60 秒重读标签；后台继续播放，返回应用时重新检查。文件夹不会复制到另一份库中。") }
            Section("音乐来源") {
                ForEach(library.state.sources) { source in
                    VStack(alignment: .leading, spacing: 8) {
                        Label(source.name, systemImage: source.isFolder ? "folder" : "doc")
                        Text("\(library.state.songs.filter { $0.sourceID == source.id }.count) 首歌曲").font(.caption).foregroundStyle(Brand.muted)
                        if let error = source.error { Text(error).font(.caption).foregroundStyle(Brand.red) }
                        if !source.isInternal {
                            HStack {
                                Button("重新授权") { replacing = source.id; folder = source.isFolder; importing = true }
                                Spacer(); Button("移除来源", role: .destructive) { remove = source }
                            }.font(.subheadline).disabled(library.scanning)
                        }
                    }.padding(.vertical, 6)
                }
            }
            Section { Text("可在“文件”→“我的 iPhone”→“小红花”→“Music”中放入音乐，或由电脑通过 Finder 文件共享传入。外部文件或文件夹需要持续的系统授权。放在 iCloud/其他提供商的文件，请先下载到本机。") }
        }.navigationTitle("歌曲文件夹").navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .fileImporter(isPresented: $importing, allowedContentTypes: folder ? [.folder] : [.audio], allowsMultipleSelection: !folder && replacing == nil) { result in
                switch result {
                case .success(let urls): Task { await library.importURLs(urls, folder: folder, replacingID: replacing) }
                case .failure(let error): library.message = error.localizedDescription
                }
            }
            .confirmationDialog("移除来源仅从索引移除歌曲，原文件保留。", isPresented: Binding(get: { remove != nil }, set: { if !$0 { remove = nil } }), titleVisibility: .visible) {
                Button("移除", role: .destructive) { if let remove { library.removeSource(remove.id) }; remove = nil }
            }
    }
}

struct LyricSettingsView: View {
    @EnvironmentObject private var library: MusicLibrary
    var body: some View {
        Form {
            Section("预览") {
                Text("风经过窗边\n让音乐在这里开花")
                    .font(Font(Brand.font(library.state.settings.lyricFont, size: library.state.settings.lyricSize)))
                    .lineSpacing(library.state.settings.lyricGap).foregroundStyle(Brand.accent(library.state.settings))
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity).padding(.vertical, 24)
            }
            Section("字体") {
                Picker("歌词字体", selection: Binding(get: { library.state.settings.lyricFont }, set: { value in library.update { $0.settings.lyricFont = value } })) {
                    ForEach(Brand.fonts, id: \.0) { item in Text(item.1).tag(item.0) }
                }
                Text("字号 \(Int(library.state.settings.lyricSize))")
                Slider(value: Binding(get: { library.state.settings.lyricSize }, set: { value in library.update { $0.settings.lyricSize = value } }), in: 18...34, step: 1)
                Text("行间距 \(Int(library.state.settings.lyricGap))")
                Slider(value: Binding(get: { library.state.settings.lyricGap }, set: { value in library.update { $0.settings.lyricGap = value } }), in: 0...48, step: 1)
            }
            Section {
                Toggle("动态滚动歌词", isOn: Binding(get: { library.state.settings.dynamicLyrics }, set: { value in library.update { $0.settings.dynamicLyrics = value } }))
            } footer: { Text("按 LRC 时间戳跟随播放，手动浏览后 4 秒回到当前行。系统“减少动态效果”开启时直接定位。") }
        }.navigationTitle("歌词设置").navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

struct BackgroundSettingsView: View {
    @EnvironmentObject private var library: MusicLibrary
    @State private var photo: PhotosPickerItem?
    @State private var saving = false
    @State private var photoError: String?
    var body: some View {
        Form {
            Section("背景") {
                Picker("来源", selection: binding(\.background)) { Text("纯色").tag("plain"); Text("专辑封面").tag("artwork"); Text("相册照片").tag("photo") }.pickerStyle(.segmented)
                PhotosPicker(selection: $photo, matching: .images) { Label(saving ? "正在保存…" : "从本地相册导入", systemImage: "photo") }.disabled(saving)
                PlayerBackground(artwork: library.state.songs.first?.artwork ?? "", settings: library.state.settings)
                    .frame(height: 240).overlay { Text("小红花\n音乐在这里开花").font(.custom(Brand.fontNames["kai"]!, size: 26)).multilineTextAlignment(.center).foregroundStyle(Brand.red) }
                    .clipShape(RoundedRectangle(cornerRadius: 18))
            }
            Section("调节") {
                adjustment("不透明度", value: binding(\.backgroundOpacity), range: 0...1)
                adjustment("大小", value: binding(\.backgroundScale), range: 1...2.5)
                adjustment("水平位置", value: binding(\.backgroundX), range: 0...1)
                adjustment("垂直位置", value: binding(\.backgroundY), range: 0...1)
                Button("恢复默认位置") { library.update { $0.settings.backgroundScale = 1; $0.settings.backgroundX = 0.5; $0.settings.backgroundY = 0.5; $0.settings.backgroundOpacity = 0.35 } }
            }
        }.navigationTitle("播放背景").navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .onChange(of: photo) { _, item in
                guard let item else { return }; saving = true
                Task {
                    do {
                        guard let data = try await item.loadTransferable(type: Data.self) else { throw LibraryError.unsupported }
                        let name = try await library.files.savePhoto(data)
                        library.update { $0.settings.backgroundPhoto = name; $0.settings.background = "photo" }
                    } catch { photoError = error.localizedDescription }
                    saving = false
                }
            }
            .alert("照片导入失败", isPresented: Binding(get: { photoError != nil }, set: { if !$0 { photoError = nil } })) { Button("知道了", role: .cancel) { photoError = nil } } message: { Text(photoError ?? "") }
    }
    private func binding<T>(_ key: WritableKeyPath<MusicSettings, T>) -> Binding<T> { Binding(get: { library.state.settings[keyPath: key] }, set: { value in library.update { $0.settings[keyPath: key] = value } }) }
    private func adjustment(_ title: String, value: Binding<Double>, range: ClosedRange<Double>) -> some View {
        VStack(alignment: .leading, spacing: 10) { HStack { Text(title); Spacer(); Text(String(format: "%.2f", value.wrappedValue)).font(.caption.monospacedDigit()).foregroundStyle(Brand.muted) }; Slider(value: value, in: range) }
    }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(state: MusicState) throws { data = try JSONEncoder().encode(MusicBackup(state: state)) }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct BackupView: View {
    @EnvironmentObject private var library: MusicLibrary
    @State private var document: BackupDocument?
    @State private var exporting = false
    @State private var importing = false
    var body: some View {
        Form {
            Section {
                Button("导出备份", systemImage: "square.and.arrow.up") { do { document = try BackupDocument(state: library.state); exporting = true } catch { library.message = error.localizedDescription } }
                Button("从备份恢复", systemImage: "square.and.arrow.down") { importing = true }
            } footer: { Text("备份包含歌单、顺序、歌词与设置，不包含音乐原文件、封面照片和文件访问授权。换手机后先导入原音乐，再恢复备份；同名同大小且唯一匹配的歌曲会自动关联。iOS 备份不直接导入 Android 的私有文件路径。") }
        }.navigationTitle("备份与恢复").navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
            .fileExporter(isPresented: $exporting, document: document, contentType: .json, defaultFilename: "小红花-备份") { result in if case .failure(let error) = result { library.message = error.localizedDescription } }
            .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
                if case .success(let url) = result { Task { await library.restoreBackup(url) } }
                else if case .failure(let error) = result { library.message = error.localizedDescription }
            }
    }
}

struct AboutView: View {
    var body: some View {
        Form {
            Section {
                VStack(spacing: 16) { Image("Flower").resizable().scaledToFit().frame(width: 76, height: 76); Text("小红花").font(.custom(Brand.fontNames["kai"]!, size: 30)).foregroundStyle(Brand.red); Text("音乐在这里开花").foregroundStyle(Brand.muted); Text("版本 1.0.1（8）"); Text("作者：浪雨林兴云魔王") }.frame(maxWidth: .infinity).padding(.vertical, 24)
            }
            Section { Text("个人本地音乐播放器。无需账号，应用不连接音乐云端服务。支持 FLAC、MP3、WAV、M4A、AAC、AIFF、CAF 等原生格式。OGG / Opus 等额外解码器暂未包含。") }
            Section("开源字体授权") {
                ForEach(["lxgw_wenkai.ttf.OFL", "source_han_serif_cn.otf.OFL", "zhuque_fangsong.ttf.OFL"], id: \.self) { license in
                    NavigationLink(license.replacingOccurrences(of: ".OFL", with: "")) { LicenseView(name: license) }
                }
            }
        }.navigationTitle("关于小红花").navigationBarTitleDisplayMode(.inline).toolbar(.visible, for: .navigationBar)
    }
}

private struct LicenseView: View {
    var name: String
    var body: some View {
        ScrollView { Text(Bundle.main.url(forResource: name, withExtension: "txt").flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? "授权文件未找到").font(.system(size: 12, design: .monospaced)).textSelection(.enabled).padding(20) }.navigationTitle("字体授权").navigationBarTitleDisplayMode(.inline)
    }
}
