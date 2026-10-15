"""Build complete offline single-Typeface lyric choices from pinned official fonts.

Inputs are full upstream fonts, never the sample-only preview WOFFs. Primary fonts
retain their native repertoire; named companions supply Japanese/Korean, and
matching Chinese sources complete all 6,763 GB2312 ideographs. One face works with
both Compose and NativePaint on Android 8+. Rare unsupported Unicode may fall back.
"""
from pathlib import Path
import hashlib
import json
import sys

import fontTools
from fontTools import subset
from fontTools.merge import Merger
from fontTools.ttLib import TTFont
from fontTools.ttLib.scaleUpem import scale_upem
from fontTools.varLib.instancer import instantiateVariableFont

if fontTools.__version__ != "4.66.1":
    raise RuntimeError("Deterministic builds require fonttools==4.66.1; run node tools/check-fonts.cjs --install")
ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".toolchain/font-downloads"
FONTS = ROOT / "android/app/src/main/res/font"
LICENSES = ROOT / "android/app/src/main/assets/font-licenses"
INPUTS = {s["file"]: s for s in json.loads((ROOT / "tools/lyric-font-inputs.json").read_text("utf-8"))}
FIXED_TIMESTAMP = 2082844800
KANA = set(range(0x3041, 0x3097)) | set(range(0x30A1, 0x30FB))
HAN = set(range(0x4E00, 0xA000))
HANGUL = set(range(0xAC00, 0xD7A4))
JAMO = set(range(0x1100, 0x1200)) | set(range(0x3131, 0x318F))
LATIN = set(range(32, 127))
PUNCTUATION = set(range(0x3000, 0x3040))
GB2312 = set()
for lead in range(0xB0, 0xF8):
    for trail in range(0xA1, 0xFF):
        try:
            GB2312.add(ord(bytes([lead, trail]).decode("gb2312")))
        except UnicodeDecodeError:
            pass
assert len(GB2312) == 6763
REQUIRED = GB2312 | KANA | HANGUL | LATIN
# First face owns shared Unicode points. Later roles add only the stated script.
# B's rounded Japanese blackface uses real Noto Sans SC for missing simplified
# Chinese; C uses Source Han Serif; D uses WenKai's handwritten Chinese.
PRESETS = [
    ("flower_xiaowei.ttf", "Little Flower Light Serif", [
        ("xiaowei.ttf", "primary"), ("klee-full.ttf", "japanese"),
        ("gowunserif.ttf", "korean"), ("lxgw_wenkai.ttf", "required-completion")]),
    ("flower_zenmaru.ttf", "Little Flower Gentle Round", [
        ("zenmaru.ttf", "primary"), ("noto-sans-sc-gstatic.ttf", "chinese-completion"),
        ("jua-full.ttf", "korean"), ("gowun.ttf", "korean-completion"),
        ("lxgw_wenkai.ttf", "required-completion")]),
    ("flower_kiwi.ttf", "Little Flower Round Serif", [
        ("kiwi.ttf", "primary"), ("source_han_serif_original.otf", "chinese-completion"),
        ("jua-full.ttf", "korean"), ("gowun.ttf", "korean-completion"),
        ("lxgw_wenkai.ttf", "required-completion")]),
    ("flower_kurenaido.ttf", "Little Flower Diary Hand", [
        ("kurenaido.ttf", "primary"), ("nanumpen-full.ttf", "korean"), ("lxgw_wenkai.ttf", "required-completion")]),
    ("flower_longcang.ttf", "Little Flower Free Hand", [
        ("longcang.ttf", "primary"), ("yuji-full.ttf", "japanese"),
        ("nanumpen-full.ttf", "korean"), ("gowun.ttf", "korean-completion"),
        ("lxgw_wenkai.ttf", "required-completion")]),
]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def rename(font, family, license_text):
    name_ids = {1, 2, 3, 4, 6, 16, 17, 18, 21, 22, 25}
    font["name"].names = [x for x in font["name"].names if x.nameID not in name_ids]
    values = {1: family, 2: "Regular", 3: family + "-1.1", 4: family + " Regular",
              6: family.replace(" ", "") + "-Regular", 16: family, 17: "Regular"}
    for name_id, value in values.items():
        font["name"].setName(value, name_id, 3, 1, 0x409)
    font["name"].setName(license_text, 13, 3, 1, 0x409)
    font["name"].setName("https://openfontlicense.org", 14, 3, 1, 0x409)
    if "DSIG" in font:
        del font["DSIG"]
    font["head"].created = font["head"].modified = FIXED_TIMESTAMP
    font.recalcTimestamp = False


def allowed(role, cmap):
    if role == "primary":
        return set(cmap)
    if role == "japanese":
        # Keep native Japanese Han absent from the Chinese primary too (e.g.
        # 音楽's 楽); adding kana alone would silently system-fallback common JP.
        return KANA | PUNCTUATION | HAN
    if role in ("korean", "korean-completion"):
        return HANGUL | JAMO
    if role == "chinese-completion":
        return GB2312
    if role == "required-completion":
        return REQUIRED | PUNCTUATION
    raise ValueError("Unknown role " + role)


def build(file, family, specs):
    used, intermediates, contributions, included = set(), [], [], []
    for index, (input_file, role) in enumerate(specs):
        source = INPUTS[input_file]
        assert digest((CACHE / input_file).read_bytes()) == source["sha256"], input_file
        license_path = CACHE / (input_file + ".OFL.txt")
        assert digest(license_path.read_bytes()) == source["licenseSha256"], input_file + " license"
        face = TTFont(CACHE / input_file, recalcTimestamp=False)
        variable_axes = None
        if "fvar" in face:
            variable_axes = {axis.axisTag: (400 if axis.axisTag == "wght" else axis.defaultValue)
                             for axis in face["fvar"].axes}
            face = instantiateVariableFont(face, variable_axes, inplace=True)
        cmap = face.getBestCmap()
        added = (set(cmap) & allowed(role, cmap)) - used
        contributions.append({"source": input_file, "role": role, "addedCodepoints": len(added),
                              "addedGb2312": len(added & GB2312), "addedKana": len(added & KANA),
                              "addedHangul": len(added & HANGUL), "staticAxes": variable_axes})
        if not added:
            face.close()
            continue
        used.update(added)
        options = subset.Options()
        options.name_IDs = ["*"]
        options.name_languages = ["*"]
        options.recalc_timestamp = False
        sub = subset.Subsetter(options=options)
        sub.populate(unicodes=added)
        sub.subset(face)
        if "CFF " in face:
            from font_cff_to_ttf import convert_cff_font
            face = convert_cff_font(face)
        if face["head"].unitsPerEm != 1000:
            scale_upem(face, 1000)
        for tag in ("vhea", "vmtx", "VORG", "BASE", "DSIG"):
            if tag in face:
                del face[tag]
        face["head"].created = face["head"].modified = FIXED_TIMESTAMP
        intermediate = CACHE / f"{file}.{index}.ttf"
        face.save(intermediate)
        face.close()
        intermediates.append(str(intermediate))
        included.append(input_file)
    assert not (REQUIRED - used), (file, "missing production glyphs", sorted(REQUIRED - used))
    merged = Merger().merge(intermediates)
    # Put encoded glyphs in Unicode order before compiling BMP cmap format 4.
    # Interleaved Japanese/Chinese/Korean source IDs otherwise fragment that
    # legacy table past its 65,535-byte limit. FontTools updates layout indices.
    cmap = merged.getBestCmap()
    old_order = merged.getGlyphOrder()
    new_order = [old_order[0]] + [cmap[cp] for cp in sorted(cmap)] + old_order
    merged.reorderGlyphs(list(dict.fromkeys(new_order)))
    upstream_licenses = "\n\n".join((CACHE / (name + ".OFL.txt")).read_text("utf-8") for name in included)
    notices = (f"{family} 1.1: an OFL 1.1 derivative for Little Red Flower.\n"
               "Modifications: retain primary repertoire and add disjoint script coverage from full upstream fonts; "
               "complete GB2312 Chinese, kana, Latin and Hangul; normalize em size; convert CFF outlines where needed; "
               "freeze variable weight at 400; remove unused vertical tables; rename the face.\n"
               "Shared Han codepoints retain the primary face's shape; language-specific Han alternates are not claimed.\n"
               "Original copyright notices and license texts follow in full.\n\n" + upstream_licenses)
    rename(merged, family, notices)
    assert not (REQUIRED - set(merged.getBestCmap())), file
    output = FONTS / file
    merged.save(output)
    merged.close()
    (LICENSES / (file + ".OFL.txt")).write_text(notices, encoding="utf-8", newline="\n")
    result = {"file": file, "family": family, "version": "1.1 multilingual OFL derivative", "modified": True,
              "buildScript": "tools/build-lyric-fonts.py", "fontToolsVersion": fontTools.__version__,
              "sources": [INPUTS[name] for name in included], "coveragePriority": contributions,
              "productionCoverage": {"Gb2312ChineseIdeographs": 6763, "JapaneseBasicKana": 176,
                                     "ModernHangulSyllables": 11172, "PrintableAscii": 95},
              "sha256": digest(output.read_bytes()), "bytes": output.stat().st_size}
    print(json.dumps({"file": file, "bytes": result["bytes"], "sha256": result["sha256"]}), flush=True)
    return result


if __name__ == "__main__":
    requested = set(sys.argv[1:])
    old_sources = json.loads((LICENSES / "sources.json").read_text("utf-8"))
    retained = [next(x for x in old_sources if x["file"] == name)
                for name in ("lxgw_wenkai.ttf", "flower_serif.ttf")]
    for source in retained:
        assert digest((FONTS / source["file"]).read_bytes()) == source["sha256"], source["file"]
    results = [build(*p) for p in PRESETS if not requested or p[0] in requested]
    if requested:
        (CACHE / "derivatives-partial.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8", newline="\n")
    else:
        (LICENSES / "sources.json").write_text(json.dumps(retained + results, indent=2) + "\n", encoding="utf-8", newline="\n")
