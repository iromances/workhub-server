from pathlib import Path

from PIL import Image
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "output" / "pdf" / "Apple_ChatGPT_subscription_receipts_A4.pdf"
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


def draw_contain(c, path, x, y, w, h):
    with Image.open(path) as im:
        iw, ih = im.size
    scale = min(w / iw, h / ih)
    dw, dh = iw * scale, ih * scale
    c.drawImage(
        ImageReader(path),
        x + (w - dw) / 2,
        y + (h - dh) / 2,
        width=dw,
        height=dh,
        preserveAspectRatio=True,
        mask="auto",
    )


page_w, page_h = landscape(A4)
c = canvas.Canvas(str(OUT), pagesize=(page_w, page_h))
c.setTitle("Apple and bank evidence - original screenshots")

margin = 16
gap = 14
col_w = (page_w - 2 * margin - gap) / 2
usable_h = page_h - 2 * margin

for page_idx in range(5):
    c.setFillColorRGB(1, 1, 1)
    c.rect(0, 0, page_w, page_h, stroke=0, fill=1)

    left_x = margin
    right_x = margin + col_w + gap

    if page_idx == 0:
        # ¥279.22 对应两笔 Apple Plus，完整截图上下排列。
        inner_gap = 8
        each_h = (usable_h - inner_gap) / 2
        draw_contain(c, APPLE[0], left_x, margin + each_h + inner_gap, col_w, each_h)
        draw_contain(c, APPLE[1], left_x, margin, col_w, each_h)
    else:
        draw_contain(c, APPLE[page_idx + 1], left_x, margin, col_w, usable_h)

    # 右侧保留银行完整原图，不裁切、不重组。
    draw_contain(c, BANK[page_idx], right_x, margin, col_w, usable_h)

    c.setStrokeColorRGB(0.86, 0.86, 0.86)
    c.setLineWidth(0.45)
    c.line(margin + col_w + gap / 2, margin, margin + col_w + gap / 2, page_h - margin)

    c.showPage()

c.save()
print(OUT)
