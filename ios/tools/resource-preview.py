from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[1]
resources = root / 'Xiaohonghua/Resources'
font = ImageFont.truetype(str(resources / 'lxgw_wenkai.ttf'), 25)
small = ImageFont.truetype(str(resources / 'lxgw_wenkai.ttf'), 18)
canvas = Image.new('RGB', (920, 330), '#f8f8f6')
draw = ImageDraw.Draw(canvas)
draw.text((32, 23), '小红花 · iPhone 图标资源检查', font=font, fill='#272b29')
names = [('AppIcon.appiconset/AppIcon.png', '应用图标'), ('Flower.imageset/Flower.png', '音乐库'), ('FlowerPlaylists.imageset/FlowerPlaylists.png', '歌单'), ('FlowerSettings.imageset/FlowerSettings.png', '设置')]
for index, (path, label) in enumerate(names):
    image = Image.open(resources / 'Assets.xcassets' / path).convert('RGBA')
    image.thumbnail((130, 130), Image.Resampling.LANCZOS)
    x = 66 + index * 220
    canvas.paste(image, (x, 85), image)
    draw.text((x + 25, 233), label, font=font, fill='#272b29')
draw.text((32, 288), '仅为实际图片资源的预览；不是 iPhone / 模拟器截图。', font=small, fill='#696f6b')
canvas.save(root / 'verification/icon-resources.png')
print('ios/verification/icon-resources.png')
