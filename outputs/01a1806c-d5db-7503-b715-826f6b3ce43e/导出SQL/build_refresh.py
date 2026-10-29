"""将本次 DataGrip JSON 导出写入已确认的三页模板，不连接数据库。"""
import argparse
import collections
import datetime as dt
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

import xlsxwriter

NS = {"s": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}


def json_records(path):
    decoder = json.JSONDecoder(parse_float=Decimal)
    with path.open(encoding="utf-8-sig") as stream:
        buf = ""
        eof = False
        started = False
        while True:
            if not eof and len(buf) < 65536:
                chunk = stream.read(262144)
                eof = not chunk
                buf += chunk
            buf = buf.lstrip()
            if not started:
                if not buf:
                    raise ValueError(f"空 JSON 文件: {path.name}")
                if buf[0] != "[":
                    raise ValueError(f"需要 JSON 数组: {path.name}")
                buf = buf[1:]
                started = True
                continue
            if buf.startswith(","):
                buf = buf[1:]
                continue
            if buf.startswith("]"):
                tail = buf[1:] + stream.read()
                if tail.strip():
                    raise ValueError("JSON 结束后仍有内容")
                return
            try:
                row, end = decoder.raw_decode(buf)
            except json.JSONDecodeError:
                if eof:
                    raise
                chunk = stream.read(262144)
                eof = not chunk
                buf += chunk
                continue
            if not isinstance(row, dict):
                raise ValueError("JSON 记录必须是对象")
            yield row
            buf = buf[end:]


def xlsx_records(path):
    """读取 DataGrip 原始 XLSX，按 XML 十进制原值读取数值，不经浮点转换。"""
    ns = "{" + NS["s"] + "}"
    with zipfile.ZipFile(path) as archive:
        shared = []
        if "xl/sharedStrings.xml" in archive.namelist():
            with archive.open("xl/sharedStrings.xml") as stream:
                for _, element in ET.iterparse(stream, events=("end",)):
                    if element.tag == ns + "si":
                        shared.append("".join(element.itertext()))
                        element.clear()
        headers = None
        with archive.open("xl/worksheets/sheet1.xml") as stream:
            for _, element in ET.iterparse(stream, events=("end",)):
                if element.tag != ns + "row":
                    continue
                values = []
                for cell in element:
                    if cell.find(ns + "f") is not None:
                        raise ValueError("原始导出不应包含公式")
                    typ = cell.get("t", "n")
                    node = cell.find(ns + "v")
                    if typ == "s":
                        value = shared[int(node.text)]
                    elif typ == "inlineStr":
                        value = "".join(cell.find(ns + "is").itertext())
                    elif node is None or node.text is None:
                        value = None
                    elif typ == "n":
                        value = Decimal(node.text)
                    else:
                        raise ValueError(f"未支持的原始单元格类型: {typ}")
                    values.append(None if value == "" else value)
                if headers is None:
                    headers = values
                else:
                    if len(values) != len(headers):
                        raise ValueError("原始导出行列数不一致")
                    yield dict(zip(headers, values))
                element.clear()


def records(path):
    yield from (xlsx_records(path) if path.suffix == ".xlsx" else json_records(path))


def template_metadata(path):
    with zipfile.ZipFile(path) as archive:
        book = ET.fromstring(archive.read("xl/workbook.xml"))
        names = [s.attrib["name"] for s in book.find("s:sheets", NS)]
        result = []
        for number, name in enumerate(names, 1):
            with archive.open(f"xl/worksheets/sheet{number}.xml") as stream:
                prefix = b""
                while b"</row>" not in prefix:
                    prefix += stream.read(16384)
            root = ET.fromstring(prefix.split(b"</row>", 1)[0] + b"</row></sheetData></worksheet>")
            cells = list(root.find("s:sheetData/s:row", NS))
            headers = ["".join(c.itertext()) for c in cells]
            columns = [c.attrib for c in root.find("s:cols", NS)]
            comments = {}
            comment_path = f"xl/comments{number}.xml"
            if comment_path in archive.namelist():
                for c in ET.fromstring(archive.read(comment_path)).find("s:commentList", NS):
                    comments[c.attrib["ref"]] = "".join(c.find("s:text", NS).itertext())
            result.append((name, headers, [int(c.get("s", "1")) for c in cells], columns, comments))
        return result


def typed_date(value):
    if value is None or isinstance(value, (dt.datetime, dt.date)):
        return value
    for pattern in (None, "%Y/%m/%d", "%Y%m%d", "%Y.%m.%d"):
        try:
            return dt.datetime.fromisoformat(value) if pattern is None else dt.datetime.strptime(value, pattern)
        except (ValueError, TypeError):
            pass
    return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--raw-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--snapshot", required=True)
    parser.add_argument("--cutoff", required=True, type=dt.date.fromisoformat)
    parser.add_argument("--connection-id", required=True, type=int)
    args = parser.parse_args()
    metadata = template_metadata(args.template)
    paths = [next((args.raw_dir / (name + ext) for ext in (".xlsx", ".json") if (args.raw_dir / (name + ext)).is_file()), args.raw_dir / (name + ".xlsx")) for name in ("assets", "cards", "bills")]
    if args.output.exists():
        raise FileExistsError(args.output)
    for path in paths:
        if not path.is_file():
            raise FileNotFoundError(path)
    audit_rows = list(records(args.raw_dir / "audit.xlsx"))
    if len(audit_rows) != 1:
        raise ValueError("快照审计必须只有一条记录")
    audit = audit_rows[0]
    if int(audit["connection_id"]) != args.connection_id or int(audit["trx_is_read_only"]) != 1 or int(audit["trx_rows_modified"]) != 0 or audit["trx_isolation_level"] != "REPEATABLE READ":
        raise ValueError("快照只读或连接核验失败")
    wb = xlsxwriter.Workbook(args.output, {"constant_memory": True, "strings_to_formulas": False, "strings_to_urls": False})
    base = {"font_name": "宋体", "font_size": 10, "valign": "vcenter"}
    formats = {i: wb.add_format(dict(base, num_format=f)) for i, f in {3: "@", 4: "0.0########", 5: "#,##0.00", 6: "yyyy-mm-dd hh:mm:ss", 7: "yyyy-mm-dd", 8: "0.####"}.items()}
    heads = {i: wb.add_format(dict(base, bold=True, font_color="white", bg_color=color, border=1, border_color="#CCCCCC", text_wrap=True)) for i, color in {1: "#2D8CF0", 2: "#00B050"}.items()}
    wb.set_properties({"title": "需求202610270002 中恒项目全量数据", "comments": f"未脱敏、未加密；实际快照时间：{args.snapshot}（北京时间）"})
    report = {"snapshot_time": args.snapshot, "cutoff_date": str(args.cutoff), "connection_id": args.connection_id, "timezone": "Asia/Shanghai", "sheets": {}, "sources": {}, "snapshot_audit": {k: str(v) if isinstance(v, Decimal) else v for k, v in audit.items()}}
    assets = {}
    bill_ids = set()
    balances = collections.defaultdict(lambda: [Decimal(0), Decimal(0), 0])
    deleted_bills = 0
    for index, ((name, headers, header_styles, columns, comments), path) in enumerate(zip(metadata, paths)):
        ws = wb.add_worksheet(name)
        ws.freeze_panes(1, 0)
        ws.set_default_row(20)
        ws.set_row(0, 38)
        styles = {}
        for col in columns:
            for c in range(int(col["min"]) - 1, int(col["max"])):
                styles[c] = int(col.get("style", 3))
            ws.set_column(int(col["min"]) - 1, int(col["max"]) - 1, float(col["width"]) - 0.7109375, formats[int(col.get("style", 3))])
        for c, header in enumerate(headers):
            ws.write_string(0, c, header, heads[header_styles[c]])
        for cell, comment in comments.items():
            if cell != "A1":
                comment = comment.replace("2026-10-28 11:57:54", args.snapshot)
                comment = comment.replace("转让截止日2026-10-28", f"转让截止日{args.cutoff}")
                comment = comment.replace("115条无放款成功记录", "无放款成功记录")
                comment = comment.replace("已删除36条账单", "已删除账单")
                comment = comment.replace("105条失效资产无客户账单", "没有客户账单的资产")
                ws.write_comment(cell, comment, {"author": "User"})
        ws.write_comment("A1", f"需求202610270002；嘉泰租赁资产保理生产；中恒 PRJ-ZH-001。\n实际只读快照时间：{args.snapshot}（北京时间）。\n未脱敏、未加密；转让截止日{args.cutoff}。资产、银行卡含历史记录；已删除账单留明细、不计余额；孤立试单不纳入。", {"author": "User"})
        count = 0
        query_times = set()
        for row in records(path):
            if list(row)[:len(headers)] != headers:
                raise ValueError(f"{name} 导出列与模板不一致")
            count += 1
            if int(row["__connection_id"]) != args.connection_id:
                raise ValueError(f"{name} 导出连接与快照连接不一致")
            query_times.add(row["__extracted_at"])
            if index == 0:
                row["转让日期"] = args.cutoff
            elif index == 2 and "__last_grace_date" in row:
                grace = typed_date(row["__last_grace_date"])
                settled = typed_date(row["客户实还时间"])
                if grace is None:
                    row["逾期天数"] = None
                else:
                    if not isinstance(grace, (dt.date, dt.datetime)) or (settled is not None and not isinstance(settled, (dt.date, dt.datetime))):
                        raise ValueError("源账单日期无法解析")
                    grace = grace.date() if isinstance(grace, dt.datetime) else grace
                    settled = settled.date() if isinstance(settled, dt.datetime) else settled
                    row["逾期天数"] = max(((settled or args.cutoff) - grace).days, 0)
            asset_id = row[headers[0]]
            if not isinstance(asset_id, str):
                raise ValueError("资产编号不是文本")
            if index == 0:
                if asset_id in assets:
                    raise ValueError("重复资产")
                assets[asset_id] = (row["转让时剩余本金"], row["转让时剩余租金"])
            elif asset_id not in assets:
                raise ValueError("发现无主资产的关联记录")
            if index == 2:
                bill_id = row["客户账单编号"]
                if bill_id in bill_ids:
                    raise ValueError("重复客户账单")
                bill_ids.add(bill_id)
                if "__is_delete" not in row:
                    raise ValueError("账单缺少余额核对所需的辅助字段")
                if int(row["__is_delete"]) == 0:
                    principal, interest = row["__unpaid_principal"], row["__unpaid_interest"]
                    if principal is None or interest is None:
                        raise ValueError("有效账单本金或利息科目缺失")
                    balances[asset_id][0] += Decimal(str(principal))
                    balances[asset_id][1] += Decimal(str(principal)) + Decimal(str(interest))
                    balances[asset_id][2] += 1
                else:
                    deleted_bills += 1
            for c, header in enumerate(headers):
                value = row[header]
                style = styles[c]
                fmt = formats[style]
                if value is None:
                    continue
                if style in (6, 7):
                    value = typed_date(value)
                    if isinstance(value, (dt.datetime, dt.date)):
                        ws.write_datetime(count, c, value, fmt)
                    else:
                        ws.write_string(count, c, str(value), fmt)
                elif style == 3:
                    if not isinstance(value, str):
                        raise ValueError(f"{name} {header} 应为文本，实际为 {type(value).__name__}")
                    ws.write_string(count, c, value, fmt)
                else:
                    ws.write_number(count, c, Decimal(str(value)), fmt)
        ws.autofilter(0, 0, count, len(headers) - 1)
        if len(query_times) != 1:
            raise ValueError(f"{name} 查询时间不唯一或结果为空")
        if count != int(audit[("assets_count", "cards_count", "bills_count")[index]]):
            raise ValueError(f"{name} 导出行数与同一快照的总量不一致")
        report["sheets"][name] = {"rows": count, "columns": len(headers), "query_started_at": next(iter(query_times))}
        report["sources"][path.name] = {"bytes": path.stat().st_size, "sha256": hashlib.file_digest(path.open("rb"), "sha256").hexdigest()}
        print(json.dumps({"sheet": name, "rows": count}, ensure_ascii=False), flush=True)
    mismatches = 0
    for asset_id, expected in assets.items():
        actual = balances.get(asset_id)
        if actual is None:
            mismatches += expected != (None, None)
        else:
            mismatches += tuple(Decimal(str(x)) if x is not None else None for x in expected) != tuple(actual[:2])
    report["deleted_bill_rows"] = deleted_bills
    report["balance_mismatches"] = mismatches
    report["assets_without_effective_bills"] = len(assets) - len(balances)
    report["remaining_principal"] = str(sum((v[0] for v in balances.values()), Decimal(0)))
    report["remaining_rent"] = str(sum((v[1] for v in balances.values()), Decimal(0)))
    wb.close()
    if mismatches:
        raise ValueError(f"资产余额与客户账单不一致: {mismatches}")
    if deleted_bills != int(audit["deleted_bills_count"]) or Decimal(report["remaining_principal"]) != audit["remaining_principal"] or Decimal(report["remaining_rent"]) != audit["remaining_rent"]:
        raise ValueError("账单历史记录数量或余额总计与快照审计不一致")
    report["output"] = str(args.output)
    report["output_sha256"] = hashlib.file_digest(args.output.open("rb"), "sha256").hexdigest()
    report["completed_at"] = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat()
    args.output.with_suffix(".核对记录.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
