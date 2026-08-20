import base64
import sys
from pathlib import Path

output_path = Path(__file__).resolve().parent / "reconciliation_db_results.json"
output_path.write_bytes(base64.b64decode(sys.argv[1]))
print(output_path)
