from pathlib import Path
import json, importlib.util
from fontTools.ttLib import TTFont

root = Path(__file__).resolve().parents[2]
fonts = root / 'android/app/src/main/res/font'
result = {}
for name in ['lxgw_wenkai.ttf', 'source_han_serif_cn.otf', 'zhuque_fangsong.ttf']:
    font = TTFont(fonts / name)
    result[name] = {'postScriptName': font['name'].getDebugName(6), 'family': font['name'].getDebugName(1)}
    font.close()
result['treeSitter'] = importlib.util.find_spec('tree_sitter') is not None
result['swiftGrammar'] = importlib.util.find_spec('tree_sitter_swift') is not None
print(json.dumps(result, ensure_ascii=False, indent=2))
