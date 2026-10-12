"""Convert a static CFF face to TrueType outlines for the lyric-font build.

Run with the same fontTools environment as build-lyric-fonts.py. This is an
offline build helper; Android never runs Python or loads conversion libraries.
"""
from pathlib import Path
import argparse

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

FIXED_TIMESTAMP = 2082844800  # OpenType epoch for 1970-01-01.


def convert_cff_font(font):
    """Replace CFF outlines in-place, retaining glyph IDs, metrics and layout.

    The cubic-to-quadratic tolerance is 0.25 design units. Integer rounding adds
    at most sqrt(0.5**2 + 0.5**2), keeping the total error below one design unit.
    CFF contours are reversed for TrueType's winding convention. CID names are
    retained in post format 2 so later fontTools merging keeps stable names.
    """
    if "CFF " not in font or "fvar" in font:
        raise ValueError("Expected one static CFF font")

    glyph_order = font.getGlyphOrder()
    glyph_set = font.getGlyphSet()
    glyphs = {}
    for name in glyph_order:
        pen = TTGlyphPen(glyph_set)
        glyph_set[name].draw(Cu2QuPen(pen, max_err=0.25, reverse_direction=True))
        glyphs[name] = pen.glyph()

    # Keep the original cmap, hmtx/vmtx, OS/2, name, BASE and shaping tables.
    for tag in ("CFF ", "VORG", "DSIG"):
        if tag in font:
            del font[tag]
    font.sfntVersion = "\x00\x01\x00\x00"
    font.setGlyphOrder(glyph_order)
    font["head"].glyphDataFormat = 0
    builder = FontBuilder(font=font)
    builder.isTTF = True
    builder.setupGlyf(glyphs)
    builder.setupMaxp()
    post = font["post"]
    post.formatType = 2.0
    post.extraNames = []
    post.mapping = {}
    font["head"].created = font["head"].modified = FIXED_TIMESTAMP
    font.recalcTimestamp = False
    return font


def convert_file(source, output):
    """Save and reopen once, checking that conversion preserves text and layout."""
    with TTFont(source, recalcTimestamp=False) as font:
        cmap = font.getBestCmap().copy()
        order = font.getGlyphOrder().copy()
        horizontal = font["hmtx"].metrics.copy()
        vertical = font["vmtx"].metrics.copy() if "vmtx" in font else None
        names = [(n.nameID, n.platformID, n.platEncID, n.langID, n.string)
                 for n in font["name"].names]
        layout = {tag: font[tag].compile(font) for tag in ("BASE", "GSUB", "GPOS", "GDEF")
                  if tag in font}
        convert_cff_font(font).save(output)
    with TTFont(output, recalcTimestamp=False) as result:
        assert "glyf" in result and "CFF " not in result
        assert result.getBestCmap() == cmap
        assert result.getGlyphOrder() == order
        assert result["hmtx"].metrics == horizontal
        assert vertical is None or result["vmtx"].metrics == vertical
        assert [(n.nameID, n.platformID, n.platEncID, n.langID, n.string)
                for n in result["name"].names] == names
        assert all(result[tag].compile(result) == data for tag, data in layout.items())
    return Path(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = convert_file(args.source, args.output)
    print(f"Converted and verified: {result} ({result.stat().st_size} bytes)")
