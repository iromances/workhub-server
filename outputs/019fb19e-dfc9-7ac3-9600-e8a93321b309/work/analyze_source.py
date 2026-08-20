import csv
import json
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path

WORK_DIR = Path(
    "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/"
    "outputs/019fb19e-dfc9-7ac3-9600-e8a93321b309/work"
)
INPUT = WORK_DIR / "source_还款台账.csv"
OUTPUT = WORK_DIR / "source_service_fee.csv"

dimensions = {
    "还款类型": Counter(),
    "交易状态": Counter(),
    "还款方式": Counter(),
    "结算方式": Counter(),
    "实还时间": Counter(),
    "项目": Counter(),
}
amounts = {key: defaultdict(Decimal) for key in dimensions}
fee_rows = []
fee_by_bill = defaultdict(Decimal)
fee_by_application = defaultdict(Decimal)
fee_by_date = defaultdict(Decimal)
fee_by_settlement = defaultdict(Decimal)
fee_by_payment = defaultdict(Decimal)
fee_total = Decimal("0")
all_fee_rows = []
all_fee_by_bill = defaultdict(Decimal)
all_fee_by_funder_pid = defaultdict(Decimal)
all_fee_total = Decimal("0")
all_zero_fee_pids = []

with INPUT.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream)
    for row in reader:
        amount = Decimal(row["实还金额"] or "0")
        for key in dimensions:
            dimensions[key][row[key]] += 1
            amounts[key][row[key]] += amount
        if row["还款类型"] == "资方服务费" and row["交易状态"] == "交易成功":
            all_fee_rows.append(row)
            all_fee_total += amount
            all_fee_by_bill[row["账单编号"]] += amount
            all_fee_by_funder_pid[f'{row["放款申请单号"]}_{row["期数"]}'] += amount
            if amount == 0:
                all_zero_fee_pids.append(f'{row["放款申请单号"]}_{row["期数"]}')
        if (
            row["还款类型"] == "资方服务费"
            and row["交易状态"] == "交易成功"
            and "2026-06-01" <= row["实还时间"] <= "2026-06-30"
        ):
            fee_rows.append(row)
            fee_total += amount
            fee_by_bill[row["账单编号"]] += amount
            fee_by_application[row["进件申请单号"]] += amount
            fee_by_date[row["实还时间"]] += amount
            fee_by_settlement[row["结算方式"]] += amount
            fee_by_payment[row["还款方式"]] += amount

with OUTPUT.open("w", encoding="utf-8-sig", newline="") as stream:
    writer = csv.DictWriter(stream, fieldnames=reader.fieldnames)
    writer.writeheader()
    writer.writerows(fee_rows)

result = {
    "file_scope": {
        "date_min": min(row["实还时间"] for row in all_fee_rows),
        "date_max": max(row["实还时间"] for row in all_fee_rows),
        "fee_row_count": len(all_fee_rows),
        "fee_unique_bill_count": len(all_fee_by_bill),
        "fee_unique_funder_pid_count": len(all_fee_by_funder_pid),
        "fee_total": str(all_fee_total),
    },
    "period": "2026-06-01 to 2026-06-30",
    "filter": {
        "还款类型": "资方服务费",
        "交易状态": "交易成功",
    },
    "fee_row_count": len(fee_rows),
    "fee_unique_bill_count": len(fee_by_bill),
    "fee_unique_application_count": len(fee_by_application),
    "fee_total": str(fee_total),
    "fee_by_date": {key: str(value) for key, value in sorted(fee_by_date.items())},
    "fee_by_settlement": {
        key: str(value) for key, value in sorted(fee_by_settlement.items())
    },
    "fee_by_payment": {key: str(value) for key, value in sorted(fee_by_payment.items())},
    "duplicate_bill_count": sum(1 for count in Counter(row["账单编号"] for row in fee_rows).values() if count > 1),
    "top_duplicate_bills": Counter(row["账单编号"] for row in fee_rows).most_common(20),
    "all_dimension_counts": {
        dimension: dict(counter) for dimension, counter in dimensions.items()
    },
}
(WORK_DIR / "source_analysis.json").write_text(
    json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8"
)
(WORK_DIR / "source_bill_totals.json").write_text(
    json.dumps(
        {key: str(value) for key, value in sorted(all_fee_by_bill.items())},
        ensure_ascii=False,
    ),
    encoding="utf-8",
)
(WORK_DIR / "source_funder_pid_totals.json").write_text(
    json.dumps(
        {key: str(value) for key, value in sorted(all_fee_by_funder_pid.items())},
        ensure_ascii=False,
    ),
    encoding="utf-8",
)
(WORK_DIR / "source_zero_fee_pids.json").write_text(
    json.dumps(sorted(all_zero_fee_pids), ensure_ascii=False),
    encoding="utf-8",
)
print(json.dumps(result, ensure_ascii=False, indent=2))
