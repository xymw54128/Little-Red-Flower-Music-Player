from pathlib import Path
import hashlib, json, zipfile

root = Path(__file__).resolve().parents[1]
dest = root.parent / 'dist'
syntax = json.loads((root / 'verification/SWIFT-SYNTAX.json').read_text(encoding='utf-8'))
project = json.loads((root / 'verification/PROJECT-CHECK.json').read_text(encoding='utf-8'))
assert not syntax['errors'] and project['result'] == 'pass'
ignored_parts = {'.build', 'DerivedData', 'xcuserdata', '__pycache__', '.host-tools'}
files = sorted(path for path in root.rglob('*') if path.is_file() and not ignored_parts.intersection(path.relative_to(root).parts) and not path.relative_to(root).as_posix().startswith('verification/mac/'))
assert all(path.suffix.lower() not in {'.p12', '.pfx', '.mobileprovision', '.pem'} for path in files)
dest.mkdir(exist_ok=True)
archive = dest / '小红花-iOS-1.0.1-源码.zip'
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as out:
    for file in files:
        out.write(file, file.relative_to(root).as_posix())
with zipfile.ZipFile(archive) as check:
    assert check.testzip() is None
    assert 'Xiaohonghua.xcodeproj/project.pbxproj' in check.namelist()
    assert check.read('Xiaohonghua/Info.plist') == (root / 'Xiaohonghua/Info.plist').read_bytes()
    assert len(check.namelist()) == len(files)
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
(dest / (archive.name + '.sha256')).write_text(digest + '  ' + archive.name + '\n', encoding='utf-8')
android = dest / '小红花-1.0.1.apk'
android_hash = hashlib.sha256(android.read_bytes()).hexdigest()
assert android_hash == 'fd2c001a683fa80b3ba80404c6f182f3bb83c096921325db8844cc392c22cacf'
report = {'artifact': archive.name, 'bytes': archive.stat().st_size, 'sha256': digest, 'files': len(files),
    'artifactType': 'Xcode source project, NOT an installable IPA', 'version': '1.0.1', 'build': 8, 'minimumIOS': '17.0',
    'windowsChecks': {'swiftGrammarErrors': len(syntax['errors']), 'swiftFiles': len(syntax['files']), 'projectResourcesAndFonts': project['result'], 'zipIntegrity': 'pass', 'android101Unchanged': android_hash},
    'coreTestsProvided': project['coreTestsDiscovered'], 'uiTestsProvided': project['uiTestsDiscovered'],
    'notRun': ['Xcode SDK type checking and compilation', 'XCTest execution', 'iPhone simulator', 'iPhone device and performance', 'Apple signing and IPA export']}
(dest / '小红花-iOS-1.0.1-CHECK.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
