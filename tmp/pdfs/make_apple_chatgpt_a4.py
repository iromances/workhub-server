from pathlib import Path

from PIL import Image
from reportlab.lib.colors import HexColor, white
from reportlab.lib.pagesizes import A4, landscape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader


ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "output" / "pdf"
TMP_DIR = ROOT / "tmp" / "pdfs" / "apple_chatgpt_crops"
OUT_DIR.mkdir(parents=True, exist_ok=True)
TMP_DIR.mkdir(parents=True, exist_ok=True)

OUT_PDF = OUT_DIR / "Apple_ChatGPT_subscription_receipts_A4.pdf"

FONT_LIGHT = "/System/Library/Fonts/STHeiti Light.ttc"
FONT_MEDIUM = "/System/Library/Fonts/STHeiti Medium.ttc"
pdfmetrics.registerFont(TTFont("STHeiti", FONT_LIGHT))
pdfmetrics.registerFont(TTFont("STHeiti-Medium", FONT_MEDIUM))


records = [
    {
        "label": "01",
        "receipt_date": "2026-01-31",
        "product": "ChatGPT Plus（月度）",
        "amount": "$19.99",
        "renewal": "2026-02-28",
        "apple_order": "MX5X30D363",
        "document": "728085180834",
        "bank_time": "2026-02-01 09:51:06",
        "rmb": "¥279.22",
        "usd": "$39.92",
        "rate": "6.994499",
        "card": "民生银行信用卡（4300）",
        "bank_order": "2026020122001452981431925378",
        "image": "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/D/D10C5FE7-4A36-4D75-836E-16623702F564_1_105_c.jpeg",
    },
    {
        "label": "02",
        "receipt_date": "2026-03-25",
        "product": "ChatGPT Pro 20x（月度）",
        "amount": "$200.00",
        "renewal": "2026-04-25",
        "apple_order": "MX5X8NXKXM",
        "document": "744111016907",
        "bank_time": "2026-03-26 11:17:14",
        "rmb": "¥1,386.13",
        "usd": "$199.60",
        "rate": "6.94454",
        "card": "民生银行信用卡（4300）",
        "bank_order": "2026032622001452981409027049",
        "image": "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/E/EB8E137A-5E78-4B5F-9370-44FA81B4311A_1_105_c.jpeg",
    },
    {
        "label": "03",
        "receipt_date": "2026-04-28",
        "product": "ChatGPT Pro 20x（月度）",
        "amount": "$200.00",
        "renewal": "2026-05-28",
        "apple_order": "MX5XF7FYJ5",
        "document": "856126102633",
        "bank_time": "2026-04-29 12:25:16",
        "rmb": "¥1,372.63",
        "usd": "$199.60",
        "rate": "6.87689",
        "card": "民生银行信用卡（4300）",
        "bank_order": "2026042922001452981436889752",
        "image": "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/2/25B13FDB-C19D-4332-9ADF-14C81BAB686B_1_105_c.jpeg",
    },
    {
        "label": "04",
        "receipt_date": "2026-06-01",
        "product": "ChatGPT Pro 20x（月度）",
        "amount": "$200.00",
        "renewal": "2026-07-01",
        "apple_order": "MX5XJT42T3",
        "document": "836141231245",
        "bank_time": "2026-06-01 14:58:07",
        "rmb": "¥1,358.04",
        "usd": "$199.60",
        "rate": "6.80381",
        "card": "民生银行信用卡（4300）",
        "bank_order": "2026060122001452981455466409",
        "image": "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/7/7C6E0CE3-F67A-4FCA-946E-04E0442F4B64_1_105_c.jpeg",
    },
    {
        "label": "05",
        "receipt_date": "2026-07-01",
        "product": "ChatGPT Pro 20x（月度）",
        "amount": "$200.00",
        "renewal": "2026-08-01",
        "apple_order": "MX5XMW1WK9",
        "document": "734155097046",
        "bank_time": "2026-07-01 07:50:43",
        "rmb": "¥1,364.07",
        "usd": "$199.60",
        "rate": "6.83403",
        "card": "招商银行信用卡（1151）",
        "bank_order": "2026070122001452981448592221",
        "image": "/Users/aslight/Pictures/Photos Library.photoslibrary/resources/derivatives/7/74E37D6F-DFBE-442B-9299-ABD7CB0A9E1E_1_105_c.jpeg",
    },
]


def crop_transaction_image(source: str, target: Path) -> Path:
    with Image.open(source) as im:
        im = im.convert("RGB")
        w, h = im.size
        # 保留金额、订单金额、汇率、时间、银行卡和商品说明，去掉状态栏、广告和条码。
        box = (int(w * 0.035), int(h * 0.205), int(w * 0.965), int(h * 0.555))
        crop = im.crop(box)
        crop.save(target, quality=95, subsampling=0)
    return target


def fit_image(c, image_path: Path, x, y, w, h):
    with Image.open(image_path) as im:
        iw, ih = im.size
    scale = min(w / iw, h / ih)
    dw, dh = iw * scale, ih * scale
    c.drawImage(ImageReader(str(image_path)), x + (w - dw) / 2, y + (h - dh) / 2,
                width=dw, height=dh, preserveAspectRatio=True, mask="auto")


PAGE_W, PAGE_H = landscape(A4)
c = canvas.Canvas(str(OUT_PDF), pagesize=(PAGE_W, PAGE_H))
c.setTitle("Apple ChatGPT subscription receipts and payment evidence")

bg = HexColor("#F5F7FA")
ink = HexColor("#17212B")
muted = HexColor("#667085")
line = HexColor("#D7DCE3")
apple = HexColor("#111111")
bank = HexColor("#C4471C")
soft_apple = HexColor("#ECEFF3")
soft_bank = HexColor("#FFF0E9")

c.setFillColor(bg)
c.rect(0, 0, PAGE_W, PAGE_H, stroke=0, fill=1)

margin = 22
usable = PAGE_W - 2 * margin
receipt_w = 305
bank_w = 342
evidence_w = usable - receipt_w - bank_w
table_top = PAGE_H - 77
header_h = 22
row_h = 89

c.setFillColor(ink)
c.setFont("STHeiti-Medium", 17)
c.drawString(margin, PAGE_H - 29, "Apple / ChatGPT 订阅及同期支付凭证合并页")
c.setFillColor(muted)
c.setFont("STHeiti", 7.6)
c.drawString(margin, PAGE_H - 45,
             "邮件来源：Apple 已验证发件人 no_reply@email.apple.com  |  Apple 账户：ymcdhl@gmail.com  |  5 组唯一收据（4 月重复邮件已去重）")
c.drawString(margin, PAGE_H - 58,
             "说明：Apple 收据显示为账户余额扣款；右侧为同期 App Store & iTunes US 礼品卡购买记录，两者金额不必完全相等。")

# Table header
c.setFillColor(apple)
c.roundRect(margin, table_top - header_h, receipt_w, header_h, 4, stroke=0, fill=1)
c.setFillColor(bank)
c.rect(margin + receipt_w, table_top - header_h, bank_w, header_h, stroke=0, fill=1)
c.setFillColor(HexColor("#475467"))
c.roundRect(margin + receipt_w + bank_w, table_top - header_h, evidence_w, header_h, 4, stroke=0, fill=1)
c.setFillColor(white)
c.setFont("STHeiti-Medium", 8)
c.drawString(margin + 9, table_top - 15, "APPLE 邮件收据")
c.drawString(margin + receipt_w + 9, table_top - 15, "同期礼品卡付款记录")
c.drawCentredString(margin + receipt_w + bank_w + evidence_w / 2, table_top - 15, "原图裁切")

y = table_top - header_h
for i, rec in enumerate(records):
    row_y = y - row_h
    fill = white if i % 2 == 0 else HexColor("#FAFBFC")
    c.setFillColor(fill)
    c.roundRect(margin, row_y, usable, row_h - 2, 4, stroke=0, fill=1)

    c.setStrokeColor(line)
    c.setLineWidth(0.55)
    c.line(margin + receipt_w, row_y + 5, margin + receipt_w, row_y + row_h - 7)
    c.line(margin + receipt_w + bank_w, row_y + 5, margin + receipt_w + bank_w, row_y + row_h - 7)

    # Apple receipt block
    c.setFillColor(soft_apple)
    c.roundRect(margin + 8, row_y + 58, 25, 20, 5, stroke=0, fill=1)
    c.setFillColor(ink)
    c.setFont("Helvetica-Bold", 9)
    c.drawCentredString(margin + 20.5, row_y + 65, rec["label"])
    c.setFont("STHeiti-Medium", 9.2)
    c.drawString(margin + 41, row_y + 69, f'{rec["receipt_date"]}  {rec["product"]}')
    c.setFillColor(apple)
    c.setFont("Helvetica-Bold", 12)
    c.drawRightString(margin + receipt_w - 12, row_y + 65, rec["amount"])
    c.setFillColor(muted)
    c.setFont("STHeiti", 7.3)
    c.drawString(margin + 12, row_y + 47,
                 f'订单号 {rec["apple_order"]}   文稿 {rec["document"]}')
    c.drawString(margin + 12, row_y + 31, f'下次续期：{rec["renewal"]}')
    c.drawString(margin + 12, row_y + 15, "付款方式：Apple 账户余额")

    # Bank transaction block
    bx = margin + receipt_w
    c.setFillColor(soft_bank)
    c.roundRect(bx + 9, row_y + 58, 116, 20, 5, stroke=0, fill=1)
    c.setFillColor(bank)
    c.setFont("STHeiti-Medium", 8.5)
    c.drawString(bx + 16, row_y + 65, rec["bank_time"])
    c.setFont("Helvetica-Bold", 12)
    c.drawRightString(bx + bank_w - 12, row_y + 65, rec["rmb"])
    c.setFillColor(ink)
    c.setFont("STHeiti", 7.3)
    c.drawString(bx + 12, row_y + 47,
                 f'订单金额 {rec["usd"]}   汇率 1 USD = {rec["rate"]} CNY   {rec["card"]}')
    c.setFillColor(muted)
    c.drawString(bx + 12, row_y + 31, "商品：App Store & iTunes US - Pockyt GiftCard")
    c.setFont("STHeiti", 7.2)
    c.drawString(bx + 12, row_y + 15, f'交易订单号 {rec["bank_order"]}')

    # Evidence crop
    ex = margin + receipt_w + bank_w
    crop_path = TMP_DIR / f'{rec["label"]}.jpg'
    crop_transaction_image(rec["image"], crop_path)
    c.setFillColor(HexColor("#FFFFFF"))
    c.roundRect(ex + 6, row_y + 5, evidence_w - 12, row_h - 12, 4, stroke=0, fill=1)
    fit_image(c, crop_path, ex + 8, row_y + 7, evidence_w - 16, row_h - 16)

    y = row_y

c.setFillColor(muted)
c.setFont("STHeiti", 6.6)
c.drawString(margin, 18, "打印建议：A4 横向、实际大小（100%）、关闭浏览器页眉页脚。原始截图已裁切至关键信息，完整原图未修改。")
c.drawRightString(PAGE_W - margin, 18, "生成日期：2026-07-13")

c.showPage()
c.save()
print(OUT_PDF)
