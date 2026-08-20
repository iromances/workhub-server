import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";
const path = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/019fcad8-85be-7220-9b83-0f3380d4e58a/支付渠道商户号统一清单.xlsx";
const wb = await SpreadsheetFile.importXlsx(await FileBlob.load(path));
console.log((await wb.inspect({
  kind: "table",
  range: "生产环境来源!A1:I15",
  tableMaxRows: 15,
  tableMaxCols: 9,
  maxChars: 16000,
})).ndjson);
console.log((await wb.inspect({
  kind: "formula",
  range: "生产环境来源!H2:H11",
  maxChars: 4000,
})).ndjson);
