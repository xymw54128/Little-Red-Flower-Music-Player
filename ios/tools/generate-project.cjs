// Deterministic, plain Xcode project. No XcodeGen, CocoaPods or SwiftPM runtime dependencies.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '..');
const project = path.join(root, 'Xiaohonghua.xcodeproj');
const objects = [];
const uid = key => crypto.createHash('sha256').update(key).digest('hex').slice(0, 24).toUpperCase();
const quote = value => JSON.stringify(value);
const ref = key => uid(key);
function object(key, contents) { objects.push(`\t\t${ref(key)} = { ${contents} };`); return ref(key); }
function walk(folder) { return fs.readdirSync(path.join(root, folder), {withFileTypes: true}).flatMap(entry => entry.isDirectory() ? walk(folder + '/' + entry.name) : [folder + '/' + entry.name]); }
const swift = walk('Xiaohonghua').filter(file => file.endsWith('.swift')).sort();
const unit = walk('Tests/CoreTests').filter(file => file.endsWith('.swift')).sort();
const ui = walk('Tests/UITests').filter(file => file.endsWith('.swift')).sort();
const fixtures = walk('Tests/CoreTests/Fixtures').sort();
const resources = ['Xiaohonghua/Resources/Assets.xcassets', ...fs.readdirSync(path.join(root, 'Xiaohonghua/Resources')).filter(name => !fs.statSync(path.join(root, 'Xiaohonghua/Resources', name)).isDirectory()).map(name => 'Xiaohonghua/Resources/' + name)];
const type = file => file.endsWith('.swift') ? 'sourcecode.swift' : file.endsWith('.xcassets') ? 'folder.assetcatalog' : file.endsWith('.storyboard') ? 'file.storyboard' : file.endsWith('.plist') ? 'text.plist.xml' : file.endsWith('.xcprivacy') ? 'text.xml' : file.endsWith('.json') ? 'text.json' : 'file';
for (const file of [...swift, ...unit, ...ui, ...fixtures, ...resources, 'Xiaohonghua/Info.plist']) {
  object('file:' + file, `isa = PBXFileReference; lastKnownFileType = ${type(file)}; path = ${quote(file)}; sourceTree = SOURCE_ROOT;`);
}
for (const [name, files] of [['app', swift], ['unit', unit], ['ui', ui], ['resources', resources]]) {
  for (const file of files) object('build:' + name + ':' + file, `isa = PBXBuildFile; fileRef = ${ref('file:' + file)};`);
  object('phase:' + name, `isa = ${name === 'resources' ? 'PBXResourcesBuildPhase' : 'PBXSourcesBuildPhase'}; buildActionMask = 2147483647; files = (${files.map(file => ref('build:' + name + ':' + file)).join(',')}); runOnlyForDeploymentPostprocessing = 0;`);
}
for (const file of fixtures) object('build:fixture:' + file, `isa = PBXBuildFile; fileRef = ${ref('file:' + file)};`);
for (const name of ['app', 'unit', 'ui']) {
  object('frameworks:' + name, 'isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;');
  if (name !== 'app') object('resources:' + name, `isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = (${name === 'unit' ? fixtures.map(file => ref('build:fixture:' + file)).join(',') : ''}); runOnlyForDeploymentPostprocessing = 0;`);
}
const targets = [['app', 'Xiaohonghua', 'com.apple.product-type.application', 'wrapper.application', 'app'], ['unit', 'CoreTests', 'com.apple.product-type.bundle.unit-test', 'wrapper.cfbundle', 'xctest'], ['ui', 'PlayerUITests', 'com.apple.product-type.bundle.ui-testing', 'wrapper.cfbundle', 'xctest']];
for (const [key, name, productType, fileType, extension] of targets) {
  object('product:' + key, `isa = PBXFileReference; explicitFileType = ${fileType}; includeInIndex = 0; path = ${quote(name + '.' + extension)}; sourceTree = BUILT_PRODUCTS_DIR;`);
  if (key !== 'app') {
    object('proxy:' + key, `isa = PBXContainerItemProxy; containerPortal = ${ref('project')}; proxyType = 1; remoteGlobalIDString = ${ref('target:app')}; remoteInfo = Xiaohonghua;`);
    object('dependency:' + key, `isa = PBXTargetDependency; target = ${ref('target:app')}; targetProxy = ${ref('proxy:' + key)};`);
  }
  object('target:' + key, `isa = PBXNativeTarget; buildConfigurationList = ${ref('configs:' + key)}; buildPhases = (${ref('phase:' + key)}, ${ref('frameworks:' + key)}, ${ref(key === 'app' ? 'phase:resources' : 'resources:' + key)}); buildRules = (); dependencies = (${key !== 'app' ? ref('dependency:' + key) : ''}); name = ${name}; productName = ${name}; productReference = ${ref('product:' + key)}; productType = ${quote(productType)};`);
}
object('group:source', `isa = PBXGroup; name = Xiaohonghua; children = (${[...swift, 'Xiaohonghua/Info.plist'].map(file => ref('file:' + file)).join(',')}); sourceTree = "<group>";`);
object('group:resources', `isa = PBXGroup; name = Resources; children = (${resources.map(file => ref('file:' + file)).join(',')}); sourceTree = "<group>";`);
object('group:tests', `isa = PBXGroup; name = Tests; children = (${[...unit, ...ui, ...fixtures].map(file => ref('file:' + file)).join(',')}); sourceTree = "<group>";`);
object('group:products', `isa = PBXGroup; name = Products; children = (${targets.map(([key]) => ref('product:' + key)).join(',')}); sourceTree = "<group>";`);
object('group:root', `isa = PBXGroup; children = (${['source', 'resources', 'tests', 'products'].map(name => ref('group:' + name)).join(',')}); sourceTree = "<group>";`);
function settings(value) { return Object.entries(value).map(([key, val]) => `${key} = ${Array.isArray(val) ? '(' + val.map(quote).join(',') + ')' : quote(String(val))};`).join(' '); }
for (const scope of ['project', 'app', 'unit', 'ui']) {
  for (const config of ['Debug', 'Release']) {
    let values = {};
    if (scope === 'project') values = {SDKROOT: 'iphoneos', IPHONEOS_DEPLOYMENT_TARGET: '17.0', CLANG_ENABLE_MODULES: 'YES', CLANG_ENABLE_OBJC_ARC: 'YES', SWIFT_VERSION: '5.0', SWIFT_STRICT_CONCURRENCY: 'minimal', DEBUG_INFORMATION_FORMAT: config === 'Debug' ? 'dwarf' : 'dwarf-with-dsym', SWIFT_OPTIMIZATION_LEVEL: config === 'Debug' ? '-Onone' : '-O', SWIFT_ACTIVE_COMPILATION_CONDITIONS: config === 'Debug' ? 'DEBUG $(inherited)' : '$(inherited)', ENABLE_TESTABILITY: config === 'Debug' ? 'YES' : 'NO'};
    else values = {PRODUCT_NAME: '$(TARGET_NAME)', CODE_SIGN_STYLE: 'Automatic', TARGETED_DEVICE_FAMILY: '1', SUPPORTED_PLATFORMS: 'iphoneos iphonesimulator', SUPPORTS_MACCATALYST: 'NO', SWIFT_EMIT_LOC_STRINGS: 'YES', GENERATE_INFOPLIST_FILE: scope === 'app' ? 'NO' : 'YES', MARKETING_VERSION: '1.0.1', CURRENT_PROJECT_VERSION: '8', LD_RUNPATH_SEARCH_PATHS: ['$(inherited)', '@executable_path/Frameworks', '@loader_path/Frameworks'], PRODUCT_BUNDLE_IDENTIFIER: 'com.xiaohonghua.music.ios' + (scope === 'app' ? '' : '.' + scope + 'tests')};
    if (scope === 'app') Object.assign(values, {INFOPLIST_FILE: 'Xiaohonghua/Info.plist', ASSETCATALOG_COMPILER_APPICON_NAME: 'AppIcon', ENABLE_PREVIEWS: 'YES'});
    if (scope === 'unit') Object.assign(values, {TEST_HOST: '$(BUILT_PRODUCTS_DIR)/Xiaohonghua.app/Xiaohonghua', BUNDLE_LOADER: '$(TEST_HOST)'});
    if (scope === 'ui') Object.assign(values, {TEST_TARGET_NAME: 'Xiaohonghua'});
    object('config:' + scope + ':' + config, `isa = XCBuildConfiguration; buildSettings = { ${settings(values)} }; name = ${config};`);
  }
  object('configs:' + scope, `isa = XCConfigurationList; buildConfigurations = (${ref('config:' + scope + ':Debug')}, ${ref('config:' + scope + ':Release')}); defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;`);
}
object('project', `isa = PBXProject; attributes = { BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 1600; TargetAttributes = { ${targets.map(([key]) => `${ref('target:' + key)} = { CreatedOnToolsVersion = 16.0; ${key !== 'app' ? `TestTargetID = ${ref('target:app')};` : ''} };`).join(' ')} }; }; buildConfigurationList = ${ref('configs:project')}; compatibilityVersion = "Xcode 14.0"; developmentRegion = "zh-Hans"; hasScannedForEncodings = 0; knownRegions = ("zh-Hans", en, Base); mainGroup = ${ref('group:root')}; productRefGroup = ${ref('group:products')}; projectDirPath = ""; projectRoot = ""; targets = (${targets.map(([key]) => ref('target:' + key)).join(',')});`);
fs.mkdirSync(project, {recursive: true});
fs.writeFileSync(path.join(project, 'project.pbxproj'), `// !$*UTF8*$!\n{\n\tarchiveVersion = 1;\n\tclasses = {};\n\tobjectVersion = 56;\n\tobjects = {\n${objects.join('\n')}\n\t};\n\trootObject = ${ref('project')};\n}\n`);
const buildRef = (key, name) => `<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="${ref('target:' + key)}" BuildableName="${name}.${key === 'app' ? 'app' : 'xctest'}" BlueprintName="${name}" ReferencedContainer="container:Xiaohonghua.xcodeproj"/>`;
const scheme = `<?xml version="1.0" encoding="UTF-8"?>\n<Scheme LastUpgradeVersion="1600" version="1.7"><BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">${buildRef('app', 'Xiaohonghua')}</BuildActionEntry></BuildActionEntries></BuildAction><TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables><TestableReference skipped="NO">${buildRef('unit', 'CoreTests')}</TestableReference><TestableReference skipped="NO">${buildRef('ui', 'PlayerUITests')}</TestableReference></Testables></TestAction><LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="NO"><BuildableProductRunnable runnableDebuggingMode="0">${buildRef('app', 'Xiaohonghua')}</BuildableProductRunnable></LaunchAction><ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">${buildRef('app', 'Xiaohonghua')}</BuildableProductRunnable></ProfileAction><AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/></Scheme>\n`;
fs.mkdirSync(path.join(project, 'xcshareddata/xcschemes'), {recursive: true});
fs.writeFileSync(path.join(project, 'xcshareddata/xcschemes/Xiaohonghua.xcscheme'), scheme);
fs.mkdirSync(path.join(project, 'project.xcworkspace'), {recursive: true});
fs.writeFileSync(path.join(project, 'project.xcworkspace/contents.xcworkspacedata'), '<?xml version="1.0" encoding="UTF-8"?>\n<Workspace version="1.0"><FileRef location="self:"/></Workspace>\n');
fs.mkdirSync(path.join(root, 'verification'), {recursive: true});
fs.writeFileSync(path.join(root, 'verification/PROJECT-MANIFEST.json'), JSON.stringify({appSources: swift, unitTests: unit, uiTests: ui, testFixtures: fixtures, resources, bundleID: 'com.xiaohonghua.music.ios', version: '1.0.1', build: 8, deploymentTarget: '17.0'}, null, 2) + '\n');
console.log(JSON.stringify({appSources: swift.length, unitTestFiles: unit.length, uiTestFiles: ui.length, resourceReferences: resources.length, project: 'ios/Xiaohonghua.xcodeproj', targets: targets.map(x => x[1])}));
