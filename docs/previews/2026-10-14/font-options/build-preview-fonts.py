"""Create offline-only specimens from official fonts; never touches Android resources.
Requires existing fontTools 4.66.1. WOFF uses stdlib zlib, no added dependency.
Original full fonts stay in ignored .toolchain cache. Preview names avoid reserved names.
"""
import sys,json,hashlib,pathlib,base64,re
ROOT=pathlib.Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'.toolchain/python-packages'))
from fontTools.ttLib import TTFont
from fontTools import subset
OUT=pathlib.Path(__file__).resolve().parent
CACHE=ROOT/'.toolchain/font-preview-110-google'
SAMPLES={'zh':'风经过花瓣，音乐刚好。\n春风桃李花开日，秋雨梧桐叶落时。','en':'Music blooms where you are.\nLittle Red Flower · 0123456789','ja':'花びらが風にゆれる。\n君の声が、春を連れてくる。','ko':'꽃잎 사이로 음악이 흘러요.\n너의 목소리에 봄이 찾아와.','digits':'0123456789 · 01:23 / 04:56'}
OPTIONS=[
 ('xiaowei','A','站酷小薇','文艺宋意','字面轻盈，笔锋有变化。适合叙事感强的慢歌。','SIL OFL 1.1'),
 ('zenmaru','B','Zen Maru Gothic','克制圆体','圆角清楚、整齐而不僵硬。日文和英文温柔易读。','SIL OFL 1.1'),
 ('kiwi','C','Kiwi Maru','软糯书卷','圆润衬线，像带一点气泡感的书写。可爱但不花哨。','SIL OFL 1.1'),
 ('kurenaido','D','Zen Kurenaido','轻松手写','细笔触、不刻意对齐，有日记一样的自然感。','SIL OFL 1.1'),
 ('mashan','E','马善政毛笔','利落毛笔','笔画有粗细和收锋。个性鲜明，适合中文歌词。','SIL OFL 1.1'),
 ('longcang','F','龙藏手书','随性行书','纤细、松弛，带一点挥笔而成的自由感。','SIL OFL 1.1'),
 ('wenkai','G','文楷 · 现有对照','现有字体','保留熟悉的文楷，直接比较手写感与可读性。','SIL OFL 1.1'),
 ('song','H','宋体 · 现有对照','现有字体','现有宋体对照；来自 1.0.9 离线组合字体。','SIL OFL 1.1'),
]
inputs=json.loads((CACHE/'inputs.json').read_text('utf8'))+json.loads((ROOT/'.toolchain/font-preview-110-companions/inputs.json').read_text('utf8'))
for x in inputs:
 if x['id'] in ('gowun','jua','nanumpen','klee','yuji'):
  x['original']=str(ROOT/'.toolchain/font-preview-110-companions'/x['file'])
  x['licensePath']=str(ROOT/'.toolchain/font-preview-110-companions'/(x['id']+'-LICENSE.txt'))
by_id={x['id']:x for x in inputs}
by_id['wenkai']={'id':'wenkai','url':'https://github.com/lxgw/LxgwWenKai/releases/tag/v1.522','repo':'lxgw/LxgwWenKai','tag':'v1.522','original':str(ROOT/'android/app/src/main/res/font/lxgw_wenkai.ttf'),'licensePath':str(ROOT/'android/app/src/main/assets/font-licenses/lxgw_wenkai.ttf.OFL.txt'),'licenseUrl':'https://github.com/lxgw/LxgwWenKai/blob/v1.522/OFL.txt'}
by_id['song']={'id':'song','url':'https://github.com/adobe-fonts/source-han-serif','repo':'adobe-fonts/source-han-serif + google/fonts/ofl/nanummyeongjo','tag':'1.0.9 existing derivative','original':str(ROOT/'android/app/src/main/res/font/flower_serif.ttf'),'licensePath':str(ROOT/'android/app/src/main/assets/font-licenses/flower_serif.ttf.OFL.txt'),'licenseUrl':'https://github.com/adobe-fonts/source-han-serif/blob/7889f11bf31170b5d092a083b357c8c8130f89e0/LICENSE.txt'}
selected=set(''.join(SAMPLES.values())+' '+''.join(chr(x) for x in range(32,127)))
results=[]
COMPANIONS=[
 ('gowun','I','Gowun Batang','韩文配套','温润的韩文衬线，配合文艺宋体方向。','SIL OFL 1.1'),
 ('jua','J','Jua','韩文配套','圆润饱满的韩文，配合柔和圆体方向。','SIL OFL 1.1'),
 ('nanumpen','K','Nanum Pen Script','韩文配套','自然松弛的韩文手写，配合手写和毛笔方向。','SIL OFL 1.1'),
 ('klee','L','Klee One','日文配套','清楚柔软的日文手写，用于中文文艺字体的日文配套。','SIL OFL 1.1'),
 ('yuji','M','Yuji Syuku','日文配套','自然毛笔感，用于中文行书和毛笔的日文配套。','SIL OFL 1.1'),
]
for id,code,name,kind,description,license_name in OPTIONS+COMPANIONS:
 src=by_id[id];original=pathlib.Path(src.get('original',CACHE/src.get('file',id+'.ttf')))
 data=original.read_bytes();font=TTFont(original);cmap=font.getBestCmap()
 orig_names={str(k):font['name'].getDebugName(k) for k in (1,2,4,5,6)}
 coverage={}
 for lang,sample in SAMPLES.items():
  chars=sorted(set(sample)-set(' \n'))
  missing=[c for c in chars if ord(c) not in cmap]
  def belongs(c):
   n=ord(c)
   if lang=='zh': return 0x4e00<=n<=0x9fff
   if lang=='en': return 0x41<=n<=0x5a or 0x61<=n<=0x7a
   if lang=='ja': return 0x3040<=n<=0x30ff or 0x4e00<=n<=0x9fff
   if lang=='ko': return 0xac00<=n<=0xd7a3
   return 0x30<=n<=0x39
  letters=[c for c in chars if belongs(c)]
  present=sum(ord(c) in cmap for c in letters)
  coverage[lang]={'tested':len(letters),'native':present,'missing':missing,'percent':round(100*present/len(letters),1),'scope':'language letters / digits in shown samples; punctuation excluded from percentage','allSampleChars':len(chars),'allSampleNative':len(chars)-len(missing)}
 ranges={'basicCjk':(0x4e00,0x9fff),'hiragana':(0x3041,0x3096),'katakana':(0x30a1,0x30fa),'hangulSyllables':(0xac00,0xd7a3),'latinDigits':(0x30,0x7a)}
 counts={k:{'present':sum(i in cmap for i in range(a,b+1)),'total':b-a+1} for k,(a,b) in ranges.items()}
 opts=subset.Options();opts.layout_features=['*'];opts.name_IDs=['*'];opts.name_languages=['*'];opts.notdef_glyph=True;opts.notdef_outline=True;opts.recommended_glyphs=True
 sub=subset.Subsetter(options=opts);sub.populate(unicodes=[ord(c) for c in selected if ord(c) in cmap]);sub.subset(font)
 family='P110'+code
 for record in font['name'].names:
  if record.nameID in (1,3,4,6,16,17,18,21,22,25):
   value='Regular' if record.nameID in (17,22) else family
   record.string=value.encode(record.getEncoding(),errors='replace')
 font.flavor='woff';asset='assets/preview-'+code.lower()+'.woff';font.save(OUT/asset)
 derived=TTFont(OUT/asset);dcmap=derived.getBestCmap()
 assert all(ord(c) in dcmap for c in selected if ord(c) in cmap)
 assert all(ord(c) not in dcmap for c in selected if ord(c) not in cmap)
 license_path=pathlib.Path(src.get('licensePath',CACHE/(id+'-LICENSE.txt')))
 license_rel='licenses/preview-'+code.lower()+'.txt';(OUT/license_rel).write_bytes(license_path.read_bytes())
 results.append({'id':id,'code':code,'name':name,'family':family,'kind':kind,'description':description,'reference':id in ('wenkai','song'),'asset':asset,'format':'woff','source':src.get('url'),'repository':'https://github.com/'+src['repo'].split(' + ')[0],'revision':src['tag'],'license':license_name,'licenseFile':license_rel,'licenseUrl':src['licenseUrl'],'sourceScope':'full bundled face' if id in ('wenkai','song') else 'Google official text subset only','unicodeRangeScope':'full bundled face' if id in ('wenkai','song') else 'preview sample subset, not total family coverage','deliveryUrl':src.get('deliveryUrl'),'cssUrl':src.get('cssUrl'),'sourceSha256':hashlib.sha256(data).hexdigest(),'sourceBytes':len(data),'previewSha256':hashlib.sha256((OUT/asset).read_bytes()).hexdigest(),'previewBytes':(OUT/asset).stat().st_size,'sampleCoverage':coverage,'unicodeRanges':counts,'originalNames':orig_names})
primary=results[:8];companions=results[8:]
role_map={'xiaowei':{'ja':'klee','ko':'gowun'},'zenmaru':{'ko':'jua'},'kiwi':{'ko':'jua'},'kurenaido':{'ko':'nanumpen'},'mashan':{'ja':'yuji','ko':'nanumpen'},'longcang':{'ja':'yuji','ko':'nanumpen'}}
all_faces={x['id']:x for x in results}
for f in primary:
 f['languageCompanions']=role_map.get(f['id'],{})
 f['combinationCoverage']={lang:all_faces[f['languageCompanions'].get(lang,f['id'])]['sampleCoverage'][lang] for lang in SAMPLES}
 f['languageFonts']={lang:all_faces[f['languageCompanions'].get(lang,f['id'])]['name'] for lang in SAMPLES}
report={'date':'2026-10-14','scope':'Preview only; Android fonts unchanged','samples':SAMPLES,'fonts':primary,'companionFonts':companions,'fallback':'Browser/OS sans-serif, explicitly marked per character; does not claim Android-identical fallback','subset':'Only demonstration characters and printable ASCII. Names changed to P110A-P110M, outlines unchanged. Original distributions linked.','fontTools':'4.66.1','combinationRule':'A-F are cross-language preview combinations. Japanese/Korean whole sample uses named companion. Primary native coverage is preserved separately. Missing Chinese uses system fallback.'}
json_text=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
(OUT/'fonts.json').write_text(json_text,encoding='utf8')
html_path=OUT/'index.html'
if html_path.exists():
 html=html_path.read_text('utf8')
 html=re.sub(r'(<script id="font-data" type="application/json">)[\s\S]*?(</script>)',lambda m:m.group(1)+json_text.replace('<',r'\u003c')+m.group(2),html)
 css='\n'.join('@font-face{font-family:"'+f['family']+'";src:url("data:font/woff;base64,'+base64.b64encode((OUT/f['asset']).read_bytes()).decode('ascii')+'") format("woff");font-weight:400;font-style:normal;font-display:swap}' for f in results)
 html=re.sub(r'(<style id="embedded-fonts">)[\s\S]*?(</style>)',lambda m:m.group(1)+css+m.group(2),html)
 html_path.write_text(html,encoding='utf8')
for x in primary: print(x['code'],x['name'],x['previewBytes'],{k:v['percent'] for k,v in x['combinationCoverage'].items()})
print('Total preview WOFF bytes',sum(x['previewBytes'] for x in results))
