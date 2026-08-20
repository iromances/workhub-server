import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";
const path = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/019fcad8-85be-7220-9b83-0f3380d4e58a/支付渠道商户号统一清单-已补京东即科.xlsx";
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(path));
console.log((await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 2000 })).ndjson);
console.log((await workbook.inspect({
  kind: "table",
  range: "渠道商户清单!A37:F40",
  tableMaxRows: 4,
  tableMaxCols: 6,
  maxChars: 4000,
})).ndjson);
console.log((await workbook.inspect({
  kind: "table,formula",
  range: "生产环境来源!H2:I11",
  tableMaxRows: 10,
  tableMaxCols: 2,
  maxChars: 5000,
})).ndjson);
console.log((await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "post-export formula error scan",
})).ndjson);
