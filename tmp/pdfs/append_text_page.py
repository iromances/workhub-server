from io import BytesIO
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.pdfgen import canvas


SOURCE = Path("/Users/aslight/Desktop/保费分期自营测试数据/交强险投保单.pdf")
OUTPUT = Path("/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/output/pdf/交强险投保单_追加编号.pdf")
TEXT = "LRDV6PECXST071777"


reader = PdfReader(SOURCE)
last_page = reader.pages[-1]
width = float(last_page.mediabox.width)
height = float(last_page.mediabox.height)

page_buffer = BytesIO()
pdf_canvas = canvas.Canvas(page_buffer, pagesize=(width, height))
pdf_canvas.setTitle("交强险投保单 - 追加编号")
pdf_canvas.setFont("Helvetica-Bold", 24)
pdf_canvas.drawCentredString(width / 2, height / 2, TEXT)
pdf_canvas.showPage()
pdf_canvas.save()
page_buffer.seek(0)

writer = PdfWriter()
writer.clone_document_from_reader(reader)
writer.add_page(PdfReader(page_buffer).pages[0])
writer.add_metadata({
    "/Title": "交强险投保单",
    "/Subject": "追加编号页",
})

OUTPUT.parent.mkdir(parents=True, exist_ok=True)
with OUTPUT.open("wb") as stream:
    writer.write(stream)

