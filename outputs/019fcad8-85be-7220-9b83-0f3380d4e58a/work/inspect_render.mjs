import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const outDir = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/019fcad8-85be-7220-9b83-0f3380d4e58a";
const currentPath = `${outDir}/支付渠道商户号统一清单.xlsx`;
const sourcePath = "/Users/aslight/Desktop/支付渠道商户号.xlsx";

const current = await SpreadsheetFile.importXlsx(await FileBlob.load(currentPath));
console.log((await current.inspect({
  kind: "workbook,sheet,table,computedStyle",
  range: "渠道商户清单!A1:F85",
  maxChars: 16000,
  tableMaxRows: 85,
  tableMaxCols: 6,
})).ndjson);

const preview = await current.render({
  sheetName: "渠道商户清单",
  range: "A1:F45",
  scale: 1.5,
  format: "png",
});
await fs.writeFile(`${outDir}/current-preview.png`, new Uint8Array(await preview.arrayBuffer()));
console.log((await current.inspect({
  kind: "table",
  range: "生产环境来源!A1:I15",
  tableMaxRows: 15,
  tableMaxCols: 9,
  maxChars: 12000,
})).ndjson);

const source = await SpreadsheetFile.importXlsx(await FileBlob.load(sourcePath));
console.log((await source.inspect({
  kind: "match",
  searchTerm: "360080004099096375|360080004090349187|即科|京东",
  options: { useRegex: true, maxResults: 100 },
  maxChars: 12000,
  summary: "source JD/JK matches",
})).ndjson);
console.log((await source.inspect({
  kind: "table",
  range: "Sheet1!A1:V15",
  tableMaxRows: 15,
  tableMaxCols: 22,
  maxChars: 20000,
})).ndjson);
