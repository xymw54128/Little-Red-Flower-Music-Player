"""Check the unmodified bundled faces and render a host-side glyph preview."""
from pathlib import Path
import json
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parent.parent
folder = root / "android/app/src/main/res/font"
text = "风经过窗边，让音乐在这里开花。春夏秋冬 ABC 123"
faces = [("楷体 · 霞鹜文楷", "lxgw_wenkai.ttf"), ("宋体 · 思源宋体", "source_han_serif_cn.otf"), ("仿宋 · 朱雀仿宋", "zhuque_fangsong.ttf")]
results = []
canvas = Image.new("RGB", (1120, 390), "#f8f8f6")
draw = ImageDraw.Draw(canvas)
for index, (label, file) in enumerate(faces):
    font = TTFont(folder / file)
    cmap = font.getBestCmap()
    missing = [c for c in text if ord(c) not in cmap]
    assert not missing, (file, missing)
    family = font["name"].getDebugName(1)
    results.append({"file": file, "family": family, "glyphs": len(cmap), "sampleMissingGlyphs": missing})
    sample_font = ImageFont.truetype(str(folder / file), 36)
    label_font = ImageFont.truetype(str(folder / file), 20)
    draw.text((36, index * 126 + 20), label, font=label_font, fill="#696f6b")
    draw.text((36, index * 126 + 57), text, font=sample_font, fill="#272b29")
    font.close()
dest = root / "dist"
dest.mkdir(exist_ok=True)
canvas.save(dest / "font-preview.png")
(dest / "FONT-CHECK.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(results, ensure_ascii=False, indent=2))
