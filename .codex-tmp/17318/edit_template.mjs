import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/aslight/Documents/workspace/IDEAWorkspace/scf-saps/saps-controller/src/main/resources/excel/channel_business_detail.xlsx";
const outputDir = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/17318";

await fs.mkdir(outputDir, { recursive: true });
const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const summary = await workbook.inspect({
  kind: "workbook,sheet,region",
  maxChars: 10000,
  tableMaxRows: 20,
  tableMaxCols: 20,
  tableMaxCellChars: 120,
});
console.log(summary.ndjson);

const sheetInfo = await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 2000 });
console.log(sheetInfo.ndjson);
const sheet = workbook.worksheets.getItemAt(0);
sheet.getRange("I1:J2").copyFrom(sheet.getRange("J1:K2"), "all");
sheet.getRange("K1:K2").clear({ applyTo: "all" });

const check = await workbook.inspect({
  kind: "region",
  sheetId: sheet.name,
  range: "A1:J2",
  maxChars: 6000,
});
console.log(check.ndjson);
const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
});
console.log(errors.ndjson);
const preview = await workbook.render({
  sheetName: sheet.name,
  autoCrop: "all",
  scale: 2,
  format: "png",
});
await fs.writeFile(`${outputDir}/after.png`, new Uint8Array(await preview.arrayBuffer()));
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(`${outputDir}/channel_business_detail.xlsx`);
