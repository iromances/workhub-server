"""独立回读已保存成品，与 DataGrip 原始导出逐单元格核对。"""
from pathlib import Path
from decimal import Decimal
from collections import Counter, defaultdict
from itertools import zip_longest
import datetime as dt
import hashlib
import json
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

from openpyxl import load_workbook
from openpyxl.utils.datetime import from_excel

ROOT = Path(__file__).resolve().parent
BASE = ROOT.parent
sys.path.insert(0, str(BASE / "导出SQL"))
from build_refresh import records, template_metadata

OUTPUT = ROOT / "需求202610270002_中恒项目全量数据_20261029_132751.xlsx"
OLD = BASE / "需求202610270002_中恒项目全量数据_20261028.xlsx"
CUTOFF = dt.date(2026, 10, 29)
NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def output_rows(archive, sheet, width):
    with archive.open(f"xl/worksheets/sheet{sheet}.xml") as stream:
        for _, element in ET.iterparse(stream, events=("end",)):
            if element.tag != NS + "row":
                continue
            values = [(None, None)] * width
            for cell in element:
                assert cell.find(NS + "f") is None, "成品存在意外公式"
                col = 0
                for letter in re.match(r"[A-Z]+", cell.get("r"))[0]:
                    col = col * 26 + ord(letter) - 64
                assert 1 <= col <= width, "成品出现辅助列"
                typ = cell.get("t", "n")
                assert typ != "e", "成品存在错误单元格"
                if typ == "inlineStr":
                    value = "".join(cell.find(NS + "is").itertext())
                else:
                    assert typ == "n", f"未预期类型{typ}"
                    node = cell.find(NS + "v")
                    value = None if node is None else Decimal(node.text)
                values[col - 1] = (value, typ)
            yield values
            element.clear()


def parsed_date(value):
    if isinstance(value, dt.datetime):
        return value
    if isinstance(value, dt.date):
        return dt.datetime.combine(value, dt.time())
    if value is None:
        return None
    for pattern in (None, "%Y/%m/%d", "%Y%m%d", "%Y.%m.%d"):
        try:
            return dt.datetime.fromisoformat(value) if pattern is None else dt.datetime.strptime(value, pattern)
        except (ValueError, TypeError):
            pass
    return value


report = {"file": str(OUTPUT), "cutoff_date": str(CUTOFF), "sheets": {}}
metadata = template_metadata(OLD)
with zipfile.ZipFile(OUTPUT) as archive:
    assert archive.testzip() is None, "XLSX ZIP 完整性失败"
    names = [e.get("name") for e in ET.fromstring(archive.read("xl/workbook.xml")).find(NS + "sheets")]
    assert names == ["资产清单", "银行卡列表", "客户账单"]
    for number, ((name, headers, _, columns, _), stem) in enumerate(zip(metadata, ("assets", "cards", "bills")), 1):
        styles = {}
        for col in columns:
            for index in range(int(col["min"]) - 1, int(col["max"])):
                styles[index] = int(col.get("style", 3))
        saved = output_rows(archive, number, len(headers))
        assert [v for v, _ in next(saved)] == headers
        source = records(ROOT / "原始数据" / f"{stem}.xlsx")
        count = text_count = long_text = dates = amounts = 0
        blank_finance = Counter()
        rates = Counter()
        for rownum, (raw, actual) in enumerate(zip_longest(source, saved), 2):
            assert raw is not None and actual is not None, f"{name}行数不符"
            count += 1
            if number == 1:
                raw["转让日期"] = CUTOFF
                for field in ("户籍行政区划代码", "现居住地行政区划代码", "单位行政区划代码", "车辆型号(行驶证)", "登记证编号", "抵押权人(选填)", "抵押登记日期(选填)"):
                    assert raw[field] is None, f"约定空字段有值: {field}"
                finance = [raw[x] for x in ("融资单号", "成本利率", "保理合同编号", "合同金额", "放款日期")]
                if finance[0] is None:
                    assert all(x is None for x in finance), "无成功融资字段未全部留空"
                    blank_finance["rows"] += 1
                else:
                    assert raw["成本利率"] in (Decimal("7"), Decimal("7.5")), "成本利率异常"
                    rates[str(raw["成本利率"])] += 1
            elif number == 3:
                grace = parsed_date(raw["__last_grace_date"])
                paid = parsed_date(raw["客户实还时间"])
                raw["逾期天数"] = None if grace is None else max(((paid.date() if paid else CUTOFF) - grace.date()).days, 0)
            for col, header in enumerate(headers):
                expected = raw[header]
                value, typ = actual[col]
                where = f"{name} row {rownum} column {header}"
                if expected is None:
                    assert value is None, f"空值不一致: {where}"
                elif styles[col] in (6, 7):
                    expected = parsed_date(expected)
                    if isinstance(expected, dt.datetime):
                        assert typ == "n", f"日期不是数值: {where}"
                        assert abs((from_excel(float(value)) - expected).total_seconds()) <= 0.001, f"日期不一致: {where}"
                        dates += 1
                    else:
                        assert value == expected and typ == "inlineStr", f"日期原文不一致: {where}"
                elif styles[col] == 3:
                    assert value == expected and typ == "inlineStr", f"文本原值不一致: {where}"
                    text_count += 1
                    long_text += expected.isdigit() and len(expected) > 15
                else:
                    assert typ == "n" and abs(value - Decimal(str(expected))) <= Decimal("0.0000000001"), f"数值不一致: {where}"
                    amounts += 1
        report["sheets"][name] = {"rows": count, "columns": len(headers), "text_cells_verified": text_count, "long_numeric_text_cells_verified": long_text, "date_cells_verified": dates, "numeric_cells_verified": amounts, "all_cells_equal_to_source": True}
        if number == 1:
            report["sheets"][name]["without_success_financing"] = blank_finance["rows"]
            report["sheets"][name]["cost_rates"] = dict(rates)
        print(json.dumps({name: report["sheets"][name]}, ensure_ascii=False), flush=True)

# 稳定字段比较银行卡变化，不推测业务原因。
old_book = load_workbook(OLD, read_only=True, data_only=True)
old_sheet = old_book["银行卡列表"]
card_headers = next(old_sheet.iter_rows(min_row=1, max_row=1, values_only=True))
def norm(value):
    if value == "":
        return None
    if isinstance(value, dt.datetime):
        return value.strftime("%Y-%m-%d %H:%M:%S")
    return value
old_rows = [tuple(norm(v) for v in row) for row in old_sheet.iter_rows(min_row=2, values_only=True)]
old_book.close()
new_rows = [tuple(norm(row[h]) for h in card_headers) for row in records(ROOT / "原始数据/cards.xlsx")]
def card_key(row):
    return row[0], row[4], row[6]
old_map, new_map = defaultdict(list), defaultdict(list)
for row in old_rows:
    old_map[card_key(row)].append(row)
for row in new_rows:
    new_map[card_key(row)].append(row)
changed = Counter()
changed_rows = 0
ambiguous_groups = 0
for key in old_map.keys() & new_map.keys():
    old_residual = list((Counter(old_map[key]) - Counter(new_map[key])).elements())
    new_residual = list((Counter(new_map[key]) - Counter(old_map[key])).elements())
    if not old_residual and not new_residual:
        continue
    if len(old_residual) == len(new_residual) == 1:
        diffs = [card_headers[i] for i, (a, b) in enumerate(zip(old_residual[0], new_residual[0])) if a != b]
        changed_rows += 1
        changed.update(diffs)
    else:
        ambiguous_groups += 1
added = new_map.keys() - old_map.keys()
removed = old_map.keys() - new_map.keys()
report["bank_card_delta"] = {"previous_rows": len(old_rows), "current_rows": len(new_rows), "net_change": len(new_rows) - len(old_rows), "comparison_key": ["资产编号", "银行卡卡号", "绑定日期"], "previous_key_duplicate_rows": len(old_rows) - len(old_map), "current_key_duplicate_rows": len(new_rows) - len(new_map), "same_key_changed_rows": changed_rows, "changed_fields": dict(changed), "ambiguous_changed_key_groups": ambiguous_groups, "added_key_rows": sum(len(new_map[k]) for k in added), "removed_key_rows": sum(len(old_map[k]) for k in removed), "added_binding_times": sorted(x[2] for x in added), "removed_binding_times": sorted(x[2] for x in removed), "assets_with_both_added_and_removed_keys": len({x[0] for x in added} & {x[0] for x in removed})}
report["output_sha256"] = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
report["completed_at"] = dt.datetime.now().astimezone().isoformat()
(ROOT / "成品独立核验.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(report["bank_card_delta"], ensure_ascii=False), flush=True)

# 临时缩小副本仅用于渲染，保留正式文件每页前8行、格式及宽度。
preview_dir = Path("/private/tmp/zh_refresh_20261029_preview")
preview_dir.mkdir(exist_ok=True)
with zipfile.ZipFile(OUTPUT) as source, zipfile.ZipFile(preview_dir / "sample.xlsx", "w", zipfile.ZIP_DEFLATED) as sample:
    for entry in source.infolist():
        if re.match(r"xl/worksheets/sheet\d+\.xml$", entry.filename):
            with source.open(entry.filename) as stream:
                prefix = b""
                while prefix.count(b"</row>") < 8:
                    part = stream.read(32768)
                    assert part
                    prefix += part
            prefix = b"</row>".join(prefix.split(b"</row>")[:8]) + b"</row>"
            prefix = re.sub(rb'(<dimension ref="A1:[A-Z]+)\d+("/>)', rb'\g<1>8\2', prefix)
            data = prefix + b"</sheetData></worksheet>"
        else:
            data = source.read(entry.filename)
        sample.writestr(entry, data)
print("PASS: all saved cells match source; preview sample ready", flush=True)
