import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

process.on("uncaughtException", (error) => {
  console.log(`ERROR: ${error.message}`);
  process.exit(1);
});

const outDir = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/019fcad8-85be-7220-9b83-0f3380d4e58a";
const inputPath = `${outDir}/支付渠道商户号统一清单.xlsx`;
const outputPath = `${outDir}/支付渠道商户号统一清单-已补京东即科.xlsx`;

const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(inputPath));
const main = workbook.worksheets.getItem("渠道商户清单");
const source = workbook.worksheets.getItem("生产环境来源");
const table = main.tables.items[0];

table.appendRows([
  ["京东支付", "代收", "消费分期", "360080004090349187\u200c", "通汇诚泰商业保理（天津）有限公司", "生产配置：即科、即科-再保理（DEF）"],
  ["京东支付", "代收", "消费分期", "360080004099096375\u200c", "大和融资担保（黑龙江）有限公司", "生产配置：即科-大和（DEF）"],
]);

const sortedRows = table.getDataRows().sort((left, right) => {
  const leftKey = [left[0], left[1], left[2], left[3]].map((value) => String(value ?? "").replaceAll("\u200c", "")).join("|");
  const rightKey = [right[0], right[1], right[2], right[3]].map((value) => String(value ?? "").replaceAll("\u200c", "")).join("|");
  return leftKey.localeCompare(rightKey, "zh-CN");
});
main.getRange("A2:F82").values = sortedRows;

source.getRange("H2").formulas = [["=COUNTIF('渠道商户清单'!$C$2:$C$82,A2)"]];
source.getRange("H2:H11").fillDown();
source.getRange("I5").values = [["涉及再保理的嘉泰侧配置在“嘉泰消费分期”单独列示；京东支付即科协议代收及签约已按 amp_payment 生产配置复核"]];

const mainCheck = await workbook.inspect({
  kind: "table",
  range: "渠道商户清单!A35:F43",
  tableMaxRows: 9,
  tableMaxCols: 6,
  maxChars: 6000,
});
console.log(mainCheck.ndjson);

const sourceCheck = await workbook.inspect({
  kind: "table,formula",
  range: "生产环境来源!A1:I11",
  tableMaxRows: 11,
  tableMaxCols: 9,
  maxChars: 10000,
});
console.log(sourceCheck.ndjson);

const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
});
console.log(errors.ndjson);

const mainPreview = await workbook.render({
  sheetName: "渠道商户清单",
  range: "A1:F43",
  scale: 1.5,
  format: "png",
});
await fs.writeFile(`${outDir}/updated-main-preview.png`, new Uint8Array(await mainPreview.arrayBuffer()));

const mainTailPreview = await workbook.render({
  sheetName: "渠道商户清单",
  range: "A35:F43",
  scale: 1.5,
  format: "png",
});
await fs.writeFile(`${outDir}/updated-jdpay-preview.png`, new Uint8Array(await mainTailPreview.arrayBuffer()));

const sourcePreview = await workbook.render({
  sheetName: "生产环境来源",
  range: "A1:I11",
  scale: 1.25,
  format: "png",
});
await fs.writeFile(`${outDir}/updated-source-preview.png`, new Uint8Array(await sourcePreview.arrayBuffer()));

await fs.mkdir(outDir, { recursive: true });
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(outputPath);
