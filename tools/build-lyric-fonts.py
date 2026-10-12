"""Build offline, single-face multilingual lyric fonts from pinned OFL inputs.

No font tool runs on Android. Each output is one native Typeface, including on API 26.
Font priority is explicit: the first face owns shared code points, later faces add
only missing characters. Uncovered rare characters use Android's system fallback.
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

if fontTools.__version__ != "4.66.1":
    raise RuntimeError("Deterministic builds require fonttools==4.66.1; run node tools/check-fonts.cjs --install")

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / ".toolchain/font-downloads"
FONTS = ROOT / "android/app/src/main/res/font"
LICENSES = ROOT / "android/app/src/main/assets/font-licenses"
INPUTS = {source["file"]: source for source in json.loads((ROOT / "tools/lyric-font-inputs.json").read_text("utf-8"))}
FIXED_TIMESTAMP = 2082844800  # OpenType epoch, corresponding to 1970-01-01.
PRESETS = [
    ("flower_serif.ttf", "Little Flower Serif", ["source_han_serif_original.otf", "nanum.ttf"]),
    ("flower_cute.ttf", "Little Flower Playful", ["happy.ttf", "hachi.ttf", "singleday.ttf", "lxgw_wenkai.ttf"]),
    ("flower_handdrawn.ttf", "Little Flower Rounded", ["butter.ttf", "yomogi.ttf", "gowun.ttf", "lxgw_wenkai.ttf"]),
]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify_inputs(files):
    for file in files:
        source = INPUTS[file]
        assert digest((CACHE / file).read_bytes()) == source["sha256"], f"Font source changed: {file}"
        assert digest((CACHE / (file + ".OFL.txt")).read_bytes()) == source["licenseSha256"], f"License changed: {file}"


def rename(font, family, license_text):
    # Remove primary/typographic/PostScript names in *every* locale, including RFNs.
    name_ids = {1, 2, 3, 4, 6, 16, 17, 18, 21, 22, 25}
    font["name"].names = [entry for entry in font["name"].names if entry.nameID not in name_ids]
    values = {1: family, 2: "Regular", 3: family + "-1.0", 4: family + " Regular",
              6: family.replace(" ", "") + "-Regular", 16: family, 17: "Regular"}
    for name_id, value in values.items():
        font["name"].setName(value, name_id, 3, 1, 0x409)
    font["name"].setName(license_text, 13, 3, 1, 0x409)
    font["name"].setName("https://openfontlicense.org", 14, 3, 1, 0x409)
    if "DSIG" in font:
        del font["DSIG"]
    font["head"].created = font["head"].modified = FIXED_TIMESTAMP
    font.recalcTimestamp = False


def build(file, family, input_files):
    verify_inputs(input_files)
    used = set()
    intermediates = []
    coverage = []
    for index, input_file in enumerate(input_files):
        face = TTFont(CACHE / input_file, recalcTimestamp=False)
        cmap = face.getBestCmap()
        added = set(cmap) - used
        if input_file == "lxgw_wenkai.ttf":
            # Two rare small hiragana are absent upstream. Complete the entire
            # basic kana ranges, without importing unrelated WenKai glyphs.
            added &= set(range(0x3041, 0x3097)) | set(range(0x30A1, 0x30FB))
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
        # Lyrics are horizontal; mixed upstreams do not all provide vertical
        # metrics, and those partial tables cannot form a valid merged face.
        for tag in ("vhea", "vmtx", "VORG", "BASE", "DSIG"):
            if tag in face:
                del face[tag]
        intermediate = CACHE / f"{file}.{index}.ttf"
        face["head"].created = face["head"].modified = FIXED_TIMESTAMP
        face.save(intermediate)
        face.close()
        intermediates.append(str(intermediate))
        coverage.append({"source": input_file, "addedCodepoints": len(added)})
    merged = Merger().merge(intermediates)
    license_text = "\n\n".join((CACHE / (input_file + ".OFL.txt")).read_text("utf-8") for input_file in input_files)
    # Original notices remain intact; this new name never uses reserved names.
    notices = (f"{family} 1.0: an OFL 1.1 derivative for Little Red Flower.\n"
               "Modifications: combine disjoint Unicode coverage into one face; normalize em size; "
               "convert cubic outlines to quadratic outlines for the Serif preset; rename the face.\n"
               "Original copyright notices and license texts follow in full.\n\n" + license_text)
    rename(merged, family, notices)
    output = FONTS / file
    merged.save(output)
    merged.close()
    (LICENSES / (file + ".OFL.txt")).write_text(notices, encoding="utf-8", newline="\n")
    data = output.read_bytes()
    result = {"file": file, "family": family, "version": "1.0 multilingual OFL derivative",
              "modified": True, "buildScript": "tools/build-lyric-fonts.py", "fontToolsVersion": fontTools.__version__,
              "sources": [INPUTS[name] for name in input_files], "coveragePriority": coverage,
              "sha256": digest(data), "bytes": len(data)}
    print(json.dumps({"file": file, "bytes": len(data), "sha256": result["sha256"]}))
    return result


if __name__ == "__main__":
    # Individual presets are useful while the independent outline conversion runs.
    requested = set(sys.argv[1:])
    results = [build(*preset) for preset in PRESETS if not requested or preset[0] in requested]
    if requested:
        (CACHE / "derivatives-partial.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8", newline="\n")
    else:
        old_sources = json.loads((LICENSES / "sources.json").read_text("utf-8"))
        wenkai = next(source for source in old_sources if source["file"] == "lxgw_wenkai.ttf")
        assert digest((FONTS / wenkai["file"]).read_bytes()) == wenkai["sha256"]
        (LICENSES / "sources.json").write_text(json.dumps([wenkai] + results, indent=2) + "\n", encoding="utf-8", newline="\n")
