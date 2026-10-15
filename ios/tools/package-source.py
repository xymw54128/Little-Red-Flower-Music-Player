from pathlib import Path
import hashlib, json, zipfile

root = Path(__file__).resolve().parents[1]
dest = root.parent / 'dist'
syntax = json.loads((root / 'verification/SWIFT-SYNTAX.json').read_text(encoding='utf-8'))
project = json.loads((root / 'verification/PROJECT-CHECK.json').read_text(encoding='utf-8'))
assert not syntax['errors'] and project['result'] == 'pass'
assert project.get('sourceSha256'), 'Run current project checks before packaging'
for name, digest in project['sourceSha256'].items():
    assert hashlib.sha256((root / name).read_bytes()).hexdigest() == digest, 'Project report is stale: ' + name
ignored_parts = {'.build', 'DerivedData', 'xcuserdata', '__pycache__', '.host-tools'}
active_fonts = {part for entry in json.loads((root / 'Xiaohonghua/Resources/font-sources.json').read_text(encoding='utf-8')) for part in (entry['file'], entry['file'] + '.OFL.txt')}
files = sorted(path for path in root.rglob('*') if path.is_file() and not ignored_parts.intersection(path.relative_to(root).parts) and not path.relative_to(root).as_posix().startswith('verification/mac/') and (not path.name.endswith(('.ttf', '.otf', '.ttf.OFL.txt', '.otf.OFL.txt')) or path.name in active_fonts))
assert all(path.suffix.lower() not in {'.p12', '.pfx', '.mobileprovision', '.pem'} for path in files)
dest.mkdir(exist_ok=True)
archive = dest / '小红花-iOS-1.1.1-源码.zip'
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as out:
    for file in files:
        out.write(file, file.relative_to(root).as_posix())
with zipfile.ZipFile(archive) as check:
    assert check.testzip() is None
    assert 'Xiaohonghua.xcodeproj/project.pbxproj' in check.namelist()
    assert check.read('Xiaohonghua/Info.plist') == (root / 'Xiaohonghua/Info.plist').read_bytes()
    assert len(check.namelist()) == len(files)
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
(dest / (archive.name + '.sha256')).write_bytes((digest + '  ' + archive.name + '\n').encode('utf-8'))
android = dest / '小红花-1.1.1.apk'
android_hash = hashlib.sha256(android.read_bytes()).hexdigest()
android_report = json.loads((dest / '小红花-1.1.1-CHECK.json').read_text(encoding='utf-8'))
assert android_hash == android_report['sha256']
assert project['version'] == '1.1.1' and project['build'] == 21
report = {'artifact': archive.name, 'bytes': archive.stat().st_size, 'sha256': digest, 'files': len(files),
    'artifactType': 'Xcode source project, NOT an installable IPA', 'version': '1.1.1', 'build': 21, 'minimumIOS': '17.0',
    'windowsChecks': {'swiftGrammarErrors': len(syntax['errors']), 'swiftFiles': len(syntax['files']), 'projectResourcesAndFonts': project['result'], 'zipIntegrity': 'pass', 'android111Unchanged': android_hash},
    'coreTestsProvided': project['coreTestsDiscovered'], 'uiTestsProvided': project['uiTestsDiscovered'],
    'notRun': ['Xcode SDK type checking and compilation', 'XCTest execution', 'iPhone simulator', 'iPhone device and performance', 'Apple signing and IPA export']}
(dest / '小红花-iOS-1.1.1-CHECK.json').write_bytes((json.dumps(report, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))
print(json.dumps(report, ensure_ascii=False, indent=2))
