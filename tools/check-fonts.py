"""Audit real cmap coverage, OFL provenance and four-script visual differences."""
from pathlib import Path
import json
import hashlib
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parent.parent
folder = root / "android/app/src/main/res/font"
samples = ["小红花 春风桃李花开日", "Little Flower ABC xyz 0123456789",
           "あいうえお アイウエオ 音楽 歌詞 ゕゖ", "꽃피는노래 가나다라마바사 123"]
faces = [("文楷", "lxgw_wenkai.ttf"), ("宋体", "flower_serif.ttf"),
         ("小薇宋意", "flower_xiaowei.ttf"), ("禅丸圆体", "flower_zenmaru.ttf"),
         ("奇异果圆体", "flower_kiwi.ttf"), ("红道手写", "flower_kurenaido.ttf"),
         ("龙藏手书", "flower_longcang.ttf")]
sources = {source["file"]: source for source in json.loads((root / "android/app/src/main/assets/font-licenses/sources.json").read_text("utf-8"))}
required = {
    "LatinLettersAndDigits": set(range(65, 91)) | set(range(97, 123)) | set(range(48, 58)),
    "JapaneseBasicKana": set(range(0x3041, 0x3097)) | set(range(0x30A1, 0x30FB)),
    "ModernHangulSyllables": set(range(0xAC00, 0xD7A4)),
}
# Check all GB2312 Chinese ideographs, rather than relying on a few demo words.
gb2312_han = set()
for lead in range(0xB0, 0xF8):
    for trail in range(0xA1, 0xFF):
        try:
            gb2312_han.add(ord(bytes([lead, trail]).decode("gb2312")))
        except UnicodeDecodeError:
            pass
assert len(gb2312_han) == 6763
required["Gb2312ChineseIdeographs"] = gb2312_han
results = []
signatures = {sample: [] for sample in samples}
canvas = Image.new("RGB", (1220, len(faces) * 245), "#f8f8f6")
draw = ImageDraw.Draw(canvas)
for index, (label, file) in enumerate(faces):
    font = TTFont(folder / file)
    cmap = font.getBestCmap()
    missing = sorted({c for sample in samples for c in sample if ord(c) not in cmap})
    assert not missing, (file, missing)
    coverage = {}
    for group, codepoints in required.items():
        absent = codepoints - set(cmap)
        assert not absent, (file, group, sorted(absent))
        coverage[group] = {"required": len(codepoints), "covered": len(codepoints)}
    source = sources[file]
    assert hashlib.sha256((folder / file).read_bytes()).hexdigest() == source["sha256"], file
    license_path = root / "android/app/src/main/assets/font-licenses" / (file + ".OFL.txt")
    license_text = license_path.read_text("utf-8")
    assert "SIL OPEN FONT LICENSE" in license_text, file
    family = font["name"].getDebugName(1)
    if source.get("modified"):
        assert family.startswith("Little Flower "), (file, family)
        for upstream in source["sources"]:
            assert len(upstream["sha256"]) == 64 and len(upstream["licenseSha256"]) == 64
        assert "Modifications:" in license_text
    results.append({"file": file, "family": family, "bytes": (folder / file).stat().st_size,
                    "glyphs": len(cmap), "CjkBasicCodepoints": sum(cp in cmap for cp in range(0x4E00, 0xA000)),
                    "coverage": coverage, "sampleMissingGlyphs": missing,
                    "rareCharacterFallback": "Android system fallback for codepoints absent from the bundled cmap"})
    sample_font = ImageFont.truetype(str(folder / file), 34)
    label_font = ImageFont.truetype(str(folder / file), 20)
    draw.text((36, index * 245 + 14), label, font=label_font, fill="#b93449")
    for row, sample in enumerate(samples):
        draw.text((36, index * 245 + 51 + row * 46), sample, font=sample_font, fill="#272b29")
        image = Image.new("L", (1220, 80))
        ImageDraw.Draw(image).text((0, 0), sample, font=sample_font, fill=255)
        signatures[sample].append(hashlib.sha256(image.tobytes()).hexdigest())
    font.close()
for sample, hashes in signatures.items():
    if sample.startswith("꽃"):
        # B/C intentionally share Jua; D/F share Nanum Pen Script. Each chosen
        # style still changes Korean from the existing WenKai/system appearance.
        assert len(set(hashes)) >= 5, ("Missing distinct Korean companion styles", sample)
        assert all(value != hashes[0] for value in hashes[2:]), ("New Korean style reused WenKai", sample)
    else:
        assert len(set(hashes)) == len(faces), ("Font style did not change this script", sample)
dest = root / "dist"
dest.mkdir(exist_ok=True)
canvas.save(dest / "font-preview.png")
(dest / "FONT-CHECK.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
print(json.dumps(results, ensure_ascii=False, indent=2))
