import fs from "node:fs/promises";
import path from "node:path";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const baseDir = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/jiatai-ledger-export";
const outputDir = path.join(baseDir, "final");

const jobs = [
  {
    csv: path.join(baseDir, "loan-ledger.csv"),
    sheetName: "放款台账",
    out: path.join(outputDir, "嘉泰资产平台_全量放款台账_20260529.xlsx"),
  },
  {
    csv: path.join(baseDir, "prepayment-ledger.csv"),
    sheetName: "提前结清台账",
    out: path.join(outputDir, "嘉泰资产平台_提前结清台账_结算日期20260427-20260529.xlsx"),
  },
];

await fs.mkdir(outputDir, { recursive: true });

for (const job of jobs) {
  const csvText = await fs.readFile(job.csv, "utf8");
  const workbook = await Workbook.fromCSV(csvText, { sheetName: job.sheetName });
  const sheet = workbook.worksheets.get(job.sheetName);
  const used = sheet.getUsedRange();
  used.format.font.name = "Microsoft YaHei";
  used.format.font.size = 10;
  used.format.wrapText = false;
  const header = sheet.getRangeByIndexes(0, 0, 1, used.columnCount);
  header.format.font.bold = true;
  header.format.fill.color = "#D9EAF7";
  header.format.horizontalAlignment = "Center";
  sheet.freezePanes.freezeRows(1);
  sheet.autoFilter.apply(used);
  for (let c = 0; c < used.columnCount; c += 1) {
    sheet.getRangeByIndexes(0, c, used.rowCount, 1).format.columnWidth = 16;
  }
  const exported = await SpreadsheetFile.exportXlsx(workbook);
  await exported.save(job.out);
  console.log(`${path.basename(job.out)}\trows=${used.rowCount - 1}\tcols=${used.columnCount}`);
}
