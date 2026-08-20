import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";
const path = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/019fcad8-85be-7220-9b83-0f3380d4e58a/支付渠道商户号统一清单-已补京东即科.xlsx";
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(path));
const values = workbook.worksheets.getItem("渠道商户清单").getRange("A1:F82").values;
const rows = values.slice(1).map((row, index) => ({ excelRow: index + 2, row })).filter(({ row }) => row[1] === "代收");
console.log(JSON.stringify(rows, null, 2));
