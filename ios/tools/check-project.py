from pathlib import Path
import plistlib, json, hashlib, re, xml.etree.ElementTree as ET
from PIL import Image
from fontTools.ttLib import TTFont
from openstep_parser import OpenStepDecoder

root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'verification/PROJECT-MANIFEST.json').read_text(encoding='utf-8'))
with (root / 'Xiaohonghua.xcodeproj/project.pbxproj').open(encoding='utf-8') as handle:
    project = OpenStepDecoder.ParseFromFile(handle)
objects = project['objects']
assert project['rootObject'] in objects
ids = re.findall(r'\b[A-F0-9]{24}\b', (root / 'Xiaohonghua.xcodeproj/project.pbxproj').read_text(encoding='utf-8'))
assert all(value in objects for value in ids), 'Dangling PBX object reference'
for obj in objects.values():
    if obj.get('isa') == 'PBXFileReference' and obj.get('sourceTree') == 'SOURCE_ROOT':
        assert (root / obj['path']).exists(), obj['path']
app_target = next(obj for obj in objects.values() if obj.get('isa') == 'PBXNativeTarget' and obj['name'] == 'Xiaohonghua')
app_config = objects[app_target['buildConfigurationList']]
for key in app_config['buildConfigurations']:
    settings = objects[key]['buildSettings']
    assert settings['MARKETING_VERSION'] == '1.1.1'
    assert settings['CURRENT_PROJECT_VERSION'] == '21'
    assert settings['TARGETED_DEVICE_FAMILY'] == '1'
    assert not settings.get('DEVELOPMENT_TEAM'), 'Do not prefill a signing team'
for key, obj in objects.items():
    if obj.get('isa') == 'XCBuildConfiguration' and 'IPHONEOS_DEPLOYMENT_TARGET' in obj['buildSettings']:
        assert obj['buildSettings']['IPHONEOS_DEPLOYMENT_TARGET'] == '17.0'
for swift in manifest['appSources']:
    assert (root / swift).exists()
phase = next(objects[key] for key in app_target['buildPhases'] if objects[key]['isa'] == 'PBXSourcesBuildPhase')
in_phase = {objects[objects[key]['fileRef']]['path'] for key in phase['files']}
assert in_phase == set(manifest['appSources'])
resource_phase = next(objects[key] for key in app_target['buildPhases'] if objects[key]['isa'] == 'PBXResourcesBuildPhase')
assert {objects[objects[key]['fileRef']]['path'] for key in resource_phase['files']} == set(manifest['resources'])
assert not set(manifest['testFixtures']).intersection(manifest['resources'])
unit_target = next(obj for obj in objects.values() if obj.get('isa') == 'PBXNativeTarget' and obj['name'] == 'CoreTests')
fixture_phase = next(objects[key] for key in unit_target['buildPhases'] if objects[key]['isa'] == 'PBXResourcesBuildPhase')
assert {objects[objects[key]['fileRef']]['path'] for key in fixture_phase['files']} == set(manifest['testFixtures'])
info = plistlib.loads((root / 'Xiaohonghua/Info.plist').read_bytes())
assert info['UIBackgroundModes'] == ['audio']
assert info.get('UIUserInterfaceStyle') != 'Light', 'Night theme must not be forced to light'
assert len(info['UIAppFonts']) == 7
assert info['CADisableMinimumFrameDurationOnPhone']
assert info['LSSupportsOpeningDocumentsInPlace'] and info['UIFileSharingEnabled']
assert 'NSPhotoLibraryUsageDescription' not in info, 'PhotosPicker needs no broad photo permission'
resources = root / 'Xiaohonghua/Resources'
fonts = []
expected = json.loads((resources / 'font-sources.json').read_text(encoding='utf-8'))
sample = '小红花 · 音乐播放器浪雨林兴云魔王风经过窗边让音乐在这里开花 ABC123'
design = (root / 'Xiaohonghua/Design.swift').read_text(encoding='utf-8')
for entry in expected:
    file = resources / entry['file']
    assert entry['file'] in info['UIAppFonts']
    digest = hashlib.sha256(file.read_bytes()).hexdigest()
    assert digest == entry['sha256']
    assert (resources / (entry['file'] + '.OFL.txt')).stat().st_size > 1000
    font = TTFont(file)
    postscript = font['name'].getDebugName(6)
    assert postscript in design
    missing = sorted({c for c in sample if ord(c) not in font.getBestCmap()})
    assert set(missing).issubset({'·'}), (entry['file'], missing)
    if missing:
        fallback = TTFont(resources / 'lxgw_wenkai.ttf')
        assert all(ord(c) in fallback.getBestCmap() for c in missing)
        assert 'cascadeList' in design and 'LXGWWenKai-Regular' in design
        fallback.close()
    fonts.append({'file': entry['file'], 'postScriptName': postscript, 'sha256': digest, 'samplePunctuationFallback': missing, 'fallbackFont': 'LXGWWenKai-Regular' if missing else None})
    font.close()
assets = []
for folder in sorted((resources / 'Assets.xcassets').iterdir()):
    if not folder.is_dir():
        continue
    contents = json.loads((folder / 'Contents.json').read_text(encoding='utf-8'))
    for entry in contents.get('images', []):
        if 'filename' not in entry:
            continue
        file = folder / entry['filename']
        with Image.open(file) as image:
            if folder.name == 'AppIcon.appiconset':
                assert image.size == (1024, 1024)
                assert image.mode == 'RGB', 'App icon must be opaque'
            else:
                assert image.mode == 'RGBA'
                if not folder.name.startswith('FlowerCover'):
                    assert image.getpixel((0, 0))[3] == 0
            assets.append({'name': folder.name, 'size': image.size, 'mode': image.mode})
privacy = plistlib.loads((resources / 'PrivacyInfo.xcprivacy').read_bytes())
assert not privacy['NSPrivacyTracking'] and not privacy['NSPrivacyCollectedDataTypes']
ET.parse(resources / 'LaunchScreen.storyboard')
scheme = ET.parse(root / 'Xiaohonghua.xcodeproj/xcshareddata/xcschemes/Xiaohonghua.xcscheme')
for buildable in scheme.findall('.//BuildableReference'):
    assert buildable.attrib['BlueprintIdentifier'] in objects
unit_tests = sum(len(re.findall(r'func test\w+\(', (root / file).read_text(encoding='utf-8'))) for file in manifest['unitTests'])
ui_tests = sum(len(re.findall(r'func test\w+\(', (root / file).read_text(encoding='utf-8'))) for file in manifest['uiTests'])
report = {'result': 'pass', 'scope': 'Project format, paths, build settings, assets, font hashes/names/glyphs, XML and test discovery; NOT Xcode compilation or test execution',
          'version': manifest['version'], 'build': manifest['build'], 'minimumIOS': manifest['deploymentTarget'],
          'appSources': len(manifest['appSources']), 'fonts': fonts, 'assets': assets,
          'coreTestsDiscovered': unit_tests, 'uiTestsDiscovered': ui_tests, 'xcodeBuild': 'not_run_no_mac', 'iPhoneTest': 'not_run_no_device',
          'sourceSha256': {name: hashlib.sha256((root / name).read_bytes()).hexdigest() for name in manifest['appSources'] + manifest['unitTests'] + manifest['uiTests']}}
# Replace atomically; host file indexers can briefly hold the previous report open.
report_path = root / 'verification/PROJECT-CHECK.json'
report_temp = root / 'verification/PROJECT-CHECK.json.tmp'
report_temp.write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
report_temp.replace(report_path)
print(json.dumps(report, ensure_ascii=False, indent=2))
