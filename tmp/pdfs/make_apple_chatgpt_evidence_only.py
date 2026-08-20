from pathlib import Path

from PIL import Image, ImageOps
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[2]
TMP = ROOT / "tmp" / "pdfs" / "evidence_only"
OUT = ROOT / "output" / "pdf" / "Apple_ChatGPT_subscription_receipts_A4.pdf"
TMP.mkdir(parents=True, exist_ok=True)
OUT.parent.mkdir(parents=True, exist_ok=True)

APPLE = [
    "/var/folders/br/ndyhl3gs0rvb6z12m_x8rqf00000gn/T/codex-clipboard-feab101c-b8f2-4858-846d-b7348f9870a1.png",
    "/var/folders/br/ndyhl3gs0rvb6z12m_x8rqf00000gn/T/codex-clipboard-152a6d75-18a5-4ed8-8d9f-fc0232aaa002.png",
    "/var/folders/br/ndyhl3gs0rvb6z12m_x8rqf00000gn/T/codex-clipboard-8f5a6db0-67db-4256-b6ec-ee731ca8f4e1.png",
    "/var/folders/br/ndyhl3gs0rvb6z12m_x8rqf00000gn/T/codex-clipboard-429ba5f3-800d-4520-850a-2ef66dcd8ea2.png",
    "/var/folders/br/ndyhl3gs0rvb6z12m_x8rqf00000gn/T/codex-clipboard-8a7c887c-b9c9-4da9-802e-a09002a1b17b.png",
    "/var/folders/br/ndyhl3gs0rvb6z12m_x8rqf00000gn/T/codex-clipboard-9734c5e6-37df-41f8-9ccd-015be393ccdc.png",
]

BANK = [
    "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/D/D10C5FE7-4A36-4D75-836E-16623702F564_1_105_c.jpeg",
    "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/E/EB8E137A-5E78-4B5F-9370-44FA81B4311A_1_105_c.jpeg",
    "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/2/25B13FDB-C19D-4332-9ADF-14C81BAB686B_1_105_c.jpeg",
    "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/7/7C6E0CE3-F67A-4FCA-946E-04E0442F4B64_1_105_c.jpeg",
    "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/7/74E37D6F-DFBE-442B-9299-ABD7CB0A9E1E_1_105_c.jpeg",
]

CELL_W, CELL_H = 1584, 392
GAP = 16


def paste_fit(dst: Image.Image, src: Image.Image, box, contain=True):
    x, y, w, h = box
    src = src.convert("RGB")
    if contain:
        src.thumbnail((w, h), Image.Resampling.LANCZOS)
    else:
        src = ImageOps.fit(src, (w, h), Image.Resampling.LANCZOS)
    px = x + (w - src.width) // 2
    py = y + (h - src.height) // 2
    dst.paste(src, (px, py))


def crop_relative(im: Image.Image, left, top, right, bottom):
    w, h = im.size
    return im.crop((int(w * left), int(h * top), int(w * right), int(h * bottom)))


def apple_compact(path: str) -> Image.Image:
    """两张 Plus 凭据共享一格时使用：产品与订单字段上下排列。"""
    with Image.open(path) as src:
        src = src.convert("RGB")
        top = crop_relative(src, 0.045, 0.145, 0.925, 0.385)
        details = crop_relative(src, 0.045, 0.500, 0.925, 0.800)
    out = Image.new("RGB", (CELL_W // 2 - GAP // 2, CELL_H), "white")
    paste_fit(out, top, (0, 0, out.width, CELL_H // 2 - 3))
    paste_fit(out, details, (0, CELL_H // 2 + 3, out.width, CELL_H // 2 - 3))
    return out


def apple_wide(path: str) -> Image.Image:
    """单张 Apple 凭据：产品卡与订单字段左右排列，放大关键文字。"""
    with Image.open(path) as src:
        src = src.convert("RGB")
        top = crop_relative(src, 0.040, 0.140, 0.925, 0.470)
        details = crop_relative(src, 0.040, 0.495, 0.925, 0.810)
    out = Image.new("RGB", (CELL_W, CELL_H), "white")
    half = (CELL_W - GAP) // 2
    paste_fit(out, top, (0, 0, half, CELL_H))
    paste_fit(out, details, (half + GAP, 0, half, CELL_H))
    return out


def bank_wide(path: str) -> Image.Image:
    """银行凭据：金额区与交易字段左右排列。"""
    with Image.open(path) as src:
        src = src.convert("RGB")
        amount = crop_relative(src, 0.035, 0.175, 0.965, 0.335)
        details = crop_relative(src, 0.035, 0.325, 0.965, 0.575)
    out = Image.new("RGB", (CELL_W, CELL_H), "white")
    left_w = 610
    paste_fit(out, amount, (0, 0, left_w, CELL_H))
    paste_fit(out, details, (left_w + GAP, 0, CELL_W - left_w - GAP, CELL_H))
    return out


left_cells = []

# ¥279.22 对应两笔 Apple Plus 订阅，合并放在第一行左侧。
first = Image.new("RGB", (CELL_W, CELL_H), "white")
a0 = apple_compact(APPLE[0])
a1 = apple_compact(APPLE[1])
first.paste(a0, (0, 0))
first.paste(a1, (a0.width + GAP, 0))
left_cells.append(first)

# 其余四张 Apple 凭据按用户提供顺序对应四张银行凭据。
for path in APPLE[2:]:
    left_cells.append(apple_wide(path))

right_cells = [bank_wide(path) for path in BANK]

left_files = []
right_files = []
for idx, im in enumerate(left_cells, start=1):
    p = TMP / f"left_{idx}.jpg"
    im.save(p, quality=96, subsampling=0)
    left_files.append(p)
for idx, im in enumerate(right_cells, start=1):
    p = TMP / f"right_{idx}.jpg"
    im.save(p, quality=96, subsampling=0)
    right_files.append(p)

page_w, page_h = landscape(A4)
c = canvas.Canvas(str(OUT), pagesize=(page_w, page_h))
c.setTitle("Apple ChatGPT evidence only")

margin_x = 16
margin_y = 16
col_gap = 8
row_gap = 6
col_w = (page_w - 2 * margin_x - col_gap) / 2
row_h = (page_h - 2 * margin_y - 4 * row_gap) / 5

c.setFillColorRGB(1, 1, 1)
c.rect(0, 0, page_w, page_h, stroke=0, fill=1)

for idx in range(5):
    y = page_h - margin_y - (idx + 1) * row_h - idx * row_gap
    c.setStrokeColorRGB(0.88, 0.88, 0.88)
    c.setLineWidth(0.35)
    c.roundRect(margin_x, y, col_w, row_h, 3, stroke=1, fill=0)
    c.roundRect(margin_x + col_w + col_gap, y, col_w, row_h, 3, stroke=1, fill=0)
    c.drawImage(ImageReader(str(left_files[idx])), margin_x + 2, y + 2,
                width=col_w - 4, height=row_h - 4, preserveAspectRatio=True, anchor="c")
    c.drawImage(ImageReader(str(right_files[idx])), margin_x + col_w + col_gap + 2, y + 2,
                width=col_w - 4, height=row_h - 4, preserveAspectRatio=True, anchor="c")

c.showPage()
c.save()
print(OUT)
