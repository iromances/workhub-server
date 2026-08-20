import csv
import json
import re
import zipfile
from pathlib import Path
from xml.etree import ElementTree as ET

SOURCE = Path(
    "/Users/aslight/Library/Containers/com.tencent.xinWeChat/Data/Documents/"
    "xwechat_files/wxid_vefr0jgcu3ft22_73f3/temp/RWTemp/2026-07/"
    "92ac6f1d29f046067bf7836975d69516/6月提单实际还款数据.xlsx"
)
WORK_DIR = Path(
    "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/"
    "outputs/019fb19e-dfc9-7ac3-9600-e8a93321b309/work"
)
NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def load_shared_strings(archive):
    values = []
    with archive.open("xl/sharedStrings.xml") as stream:
        for event, element in ET.iterparse(stream, events=("end",)):
            if element.tag == f"{NS}si":
                values.append("".join(element.itertext()))
                element.clear()
    return values


def column_index(cell_ref):
    letters = re.match(r"[A-Z]+", cell_ref).group(0)
    result = 0
    for letter in letters:
        result = result * 26 + ord(letter) - 64
    return result - 1


def cell_value(cell, shared_strings):
    cell_type = cell.attrib.get("t")
    value_node = cell.find(f"{NS}v")
    if cell_type == "inlineStr":
        inline = cell.find(f"{NS}is")
        return "" if inline is None else "".join(inline.itertext())
    raw = "" if value_node is None or value_node.text is None else value_node.text
    if cell_type == "s" and raw:
        return shared_strings[int(raw)]
    if cell_type == "b":
        return "TRUE" if raw == "1" else "FALSE"
    return raw


def extract_sheet(archive, sheet_path, output_csv, shared_strings, max_columns=15):
    samples = []
    row_count = 0
    with archive.open(sheet_path) as stream, output_csv.open(
        "w", newline="", encoding="utf-8-sig"
    ) as output:
        writer = csv.writer(output)
        for event, element in ET.iterparse(stream, events=("end",)):
            if element.tag != f"{NS}row":
                continue
            row = [""] * max_columns
            for cell in element.findall(f"{NS}c"):
                ref = cell.attrib.get("r", "")
                if not ref:
                    continue
                index = column_index(ref)
                if index < max_columns:
                    row[index] = cell_value(cell, shared_strings)
            writer.writerow(row)
            row_count += 1
            if len(samples) < 25:
                samples.append(row)
            element.clear()
    return {"row_count": row_count, "samples": samples}


with zipfile.ZipFile(SOURCE) as archive:
    strings = load_shared_strings(archive)
    result = {
        "source": SOURCE.name,
        "shared_string_count": len(strings),
        "sheets": {},
    }
    for sheet_name, sheet_path, csv_name, max_columns in [
        ("总透视", "xl/worksheets/sheet1.xml", "source_总透视.csv", 30),
        ("还款台账", "xl/worksheets/sheet2.xml", "source_还款台账.csv", 15),
        ("滞纳金", "xl/worksheets/sheet3.xml", "source_滞纳金.csv", 20),
    ]:
        result["sheets"][sheet_name] = extract_sheet(
            archive,
            sheet_path,
            WORK_DIR / csv_name,
            strings,
            max_columns=max_columns,
        )

(WORK_DIR / "source_extract.json").write_text(
    json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
)
print(json.dumps(result, ensure_ascii=False))
