import csv
import json
from collections import defaultdict
from decimal import Decimal
from pathlib import Path

WORK_DIR = Path(__file__).resolve().parent
SOURCE_CSV = WORK_DIR / "source_还款台账.csv"
DB_RESULTS = WORK_DIR / "reconciliation_db_results.json"

db_results = json.loads(DB_RESULTS.read_text(encoding="utf-8"))
diff_by_pid = {
    item["funder_bill_no"]: item for item in db_results["differences"]
}

daily = defaultdict(lambda: {"count": 0, "amount": Decimal("0")})
settlement = defaultdict(lambda: {"count": 0, "amount": Decimal("0")})
payment = defaultdict(lambda: {"count": 0, "amount": Decimal("0")})
date_settlement = defaultdict(lambda: {"count": 0, "amount": Decimal("0")})
diff_rows = []
zero_count = 0
positive_count = 0

with SOURCE_CSV.open(encoding="utf-8-sig", newline="") as stream:
    reader = csv.DictReader(stream)
    for row in reader:
        if row["还款类型"] != "资方服务费" or row["交易状态"] != "交易成功":
            continue
        amount = Decimal(row["实还金额"] or "0")
        funder_pid = f'{row["放款申请单号"]}_{row["期数"]}'
        daily[row["实还时间"]]["count"] += 1
        daily[row["实还时间"]]["amount"] += amount
        settlement[row["结算方式"]]["count"] += 1
        settlement[row["结算方式"]]["amount"] += amount
        payment[row["还款方式"]]["count"] += 1
        payment[row["还款方式"]]["amount"] += amount
        date_settlement[(row["实还时间"], row["结算方式"])]["count"] += 1
        date_settlement[(row["实还时间"], row["结算方式"])]["amount"] += amount
        if amount == 0:
            zero_count += 1
        else:
            positive_count += 1
        if funder_pid in diff_by_pid:
            db = diff_by_pid[funder_pid]
            source_fee = Decimal(str(db["source_fee"]))
            consumer_fee = Decimal(str(db["consumer_fee"]))
            difference = Decimal(str(db["difference"]))
            ratio = None if source_fee == 0 else consumer_fee / source_fee
            diff_rows.append(
                {
                    "funder_bill_no": funder_pid,
                    "contract_no": row["合同编号"],
                    "project": row["项目"],
                    "customer_name": row["客户姓名"],
                    "interest_mode": row["付息方式"],
                    "period": int(row["期数"]),
                    "paid_date": row["实还时间"],
                    "source_fee": float(source_fee),
                    "payment_method": row["还款方式"],
                    "settlement_type": row["结算方式"],
                    "jiatai_application_no": row["进件申请单号"],
                    "jiatai_loan_no": row["放款申请单号"],
                    "jiatai_bill_no": row["账单编号"],
                    "consumer_application_no": db["consumer_application_no"],
                    "consumer_bill_no": db["consumer_bill_no"],
                    "consumer_fee": float(consumer_fee),
                    "difference": float(difference),
                    "consumer_to_source_ratio": None if ratio is None else float(ratio),
                    "diagnosis": (
                        "消费分期P0003账单金额高于嘉泰实收"
                        if difference > 0
                        else "消费分期P0003账单金额低于嘉泰实收"
                    ),
                }
            )

diff_rows.sort(key=lambda row: (-abs(row["difference"]), row["funder_bill_no"]))

source_pid_totals = json.loads(
    (WORK_DIR / "source_funder_pid_totals.json").read_text(encoding="utf-8")
)
source_pids = list(source_pid_totals)
group_rows = []
for item in db_results["groups"]:
    size = item["source_count"]
    index = item["chunk_index"]
    if size == 1000:
        start = index * 1000
    else:
        start = index * 5000
    end = start + size
    pids = source_pids[start:end]
    group_rows.append(
        {
            "group": f"{index}-{size}",
            "first_funder_bill_no": pids[0] if pids else "",
            "last_funder_bill_no": pids[-1] if pids else "",
            "source_count": item["source_count"],
            "consumer_fee_subject_count": item["matched_bill_count"],
            "zero_without_fee_subject_count": (
                item["source_count"] - item["matched_bill_count"]
            ),
            "source_fee": float(Decimal(str(item["source_total"]))),
            "consumer_fee": float(Decimal(str(item["funder_fee_total"]))),
            "difference": float(
                Decimal(str(item["funder_fee_total"]))
                - Decimal(str(item["source_total"]))
            ),
            "consumer_paid_field": float(Decimal(str(item["funder_fee_paid"]))),
            "paid_field_gap": float(
                Decimal(str(item["source_total"]))
                - Decimal(str(item["funder_fee_paid"]))
            ),
        }
    )

payload = {
    "summary": db_results["summary"],
    "daily": [
        {
            "date": key,
            "count": value["count"],
            "source_fee": float(value["amount"]),
        }
        for key, value in sorted(daily.items())
    ],
    "settlement": [
        {
            "settlement_type": key,
            "count": value["count"],
            "source_fee": float(value["amount"]),
        }
        for key, value in sorted(settlement.items())
    ],
    "payment": [
        {
            "payment_method": key,
            "count": value["count"],
            "source_fee": float(value["amount"]),
        }
        for key, value in sorted(payment.items())
    ],
    "date_settlement": [
        {
            "date": key[0],
            "settlement_type": key[1],
            "count": value["count"],
            "source_fee": float(value["amount"]),
        }
        for key, value in sorted(date_settlement.items())
    ],
    "differences": diff_rows,
    "groups": group_rows,
    "controls": {
        "source_fee_rows": zero_count + positive_count,
        "source_zero_fee_rows": zero_count,
        "source_positive_fee_rows": positive_count,
        "difference_rows": len(diff_rows),
        "difference_sum": float(
            sum((Decimal(str(row["difference"])) for row in diff_rows), Decimal("0"))
        ),
        "difference_source_sum": float(
            sum((Decimal(str(row["source_fee"])) for row in diff_rows), Decimal("0"))
        ),
        "difference_consumer_sum": float(
            sum((Decimal(str(row["consumer_fee"])) for row in diff_rows), Decimal("0"))
        ),
    },
}

(WORK_DIR / "reconciliation_data.json").write_text(
    json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
)

with (WORK_DIR / "reconciliation_differences.csv").open(
    "w", encoding="utf-8-sig", newline=""
) as stream:
    writer = csv.DictWriter(stream, fieldnames=list(diff_rows[0]))
    writer.writeheader()
    writer.writerows(diff_rows)

print(json.dumps(payload["controls"], ensure_ascii=False))
