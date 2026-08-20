from io import BytesIO
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.pdfgen import canvas


SOURCE = Path("/Users/aslight/Desktop/保费分期自营测试数据/交强险投保单_追加编号.pdf")
OUTPUT = Path("output/pdf/交强险投保单_追加编号_OPERATOR.pdf")


reader = PdfReader(SOURCE)
writer = PdfWriter()

for index, page in enumerate(reader.pages):
    if index == len(reader.pages) - 1:
        width = float(page.mediabox.width)
        height = float(page.mediabox.height)
        overlay_buffer = BytesIO()
        overlay = canvas.Canvas(overlay_buffer, pagesize=(width, height))
        overlay.setFont("Helvetica", 18)
        text = "OPERATOR"
        text_width = overlay.stringWidth(text, "Helvetica", 18)
        overlay.drawString((width - text_width) / 2, height / 2 - 52, text)
        overlay.save()
        overlay_buffer.seek(0)
        page.merge_page(PdfReader(overlay_buffer).pages[0])
    writer.add_page(page)

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
with OUTPUT.open("wb") as stream:
    writer.write(stream)

print(OUTPUT.resolve())
