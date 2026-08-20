import json
import sys
from decimal import Decimal
from pathlib import Path

WORK_DIR = Path(__file__).resolve().parent
start_chunk = int(sys.argv[1])
chunk_count = int(sys.argv[2])
chunk_size = int(sys.argv[3])

data = json.loads((WORK_DIR / "source_funder_pid_totals.json").read_text(encoding="utf-8"))
items = list(data.items())
chunks = []
for chunk_index in range(start_chunk, min(start_chunk + chunk_count, (len(items) + chunk_size - 1) // chunk_size)):
    start = chunk_index * chunk_size
    selected = items[start : start + chunk_size]
    chunks.append(
        {
            "chunk_index": chunk_index,
            "items": selected,
            "source_count": len(selected),
            "source_total": str(sum((Decimal(value) for _, value in selected), Decimal("0"))),
        }
    )
print(json.dumps(chunks, ensure_ascii=False))
