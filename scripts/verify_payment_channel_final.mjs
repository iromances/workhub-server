import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "/Users/aslight/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const inputPath = "/Users/aslight/Desktop/支付渠道.xlsx";
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(inputPath));
const main = workbook.worksheets.getItem("渠道商户清单");
const failure = workbook.worksheets.getItem("失败原因明细");
const mainTable = main.tables.getItem("PaymentMerchantList");
const failureTable = failure.tables.getItem("FailureReasonList");
const rows = mainTable.getDataRows();
const failureRows = failureTable.getDataRows();

if (rows.length !== 108) throw new Error(`主表行数异常：${rows.length}`);
if (failureRows.length !== 274) throw new Error(`失败明细行数异常：${failureRows.length}`);

const expectedLogistics = new Map([
  ["360080004100058885", ["正常", "2026-08-05 13:10:26"]],
  ["552290060129UL7", ["停用", "2026-06-26 16:15:23"]],
  ["660451048165ZZ5", ["正常", "2026-08-05 13:30:41"]],
]);
const toExcelSerial = (value) => {
  return new Date(`${value.replace(" ", "T")}+08:00`).getTime() / 86400000 + 25569;
};
const logistics = rows.filter((row) => String(row[2] ?? "") === "物流平台");
if (logistics.length !== 3) throw new Error(`物流平台账号数量异常：${logistics.length}`);
for (const row of logistics) {
  const merchantNo = String(row[3] ?? "");
  const expected = expectedLogistics.get(merchantNo);
  if (!expected) throw new Error(`物流平台出现未知账号：${merchantNo}`);
  const actualLastSuccess = Number(row[9]);
  const expectedLastSuccess = toExcelSerial(expected[1]);
  if (String(row[7] ?? "") !== expected[0] || Math.abs(actualLastSuccess - expectedLastSuccess) > 1 / 86400) {
    throw new Error(`物流平台账号数据异常：${merchantNo}`);
  }
}

const failureCounts = {};
for (const merchantNo of expectedLogistics.keys()) {
  const details = failureRows.filter((row) => String(row[0] ?? "") === "物流平台" && String(row[2] ?? "") === merchantNo);
  failureCounts[merchantNo] = details.length;
  if (merchantNo !== "360080004100058885" && details.length !== 30) {
    throw new Error(`${merchantNo} 的失败原因不是 30 条：${details.length}`);
  }
  if (details.some((row, index) => index > 0 && Number(details[index - 1][5]) < Number(row[5]))) {
    throw new Error(`${merchantNo} 的失败次数未按降序排列`);
  }
}

const protectedConsumer = rows.filter((row) =>
  ["360080004090349187", "360080004099096375"].includes(String(row[3] ?? "")),
);
if (protectedConsumer.length !== 2 || protectedConsumer.some((row) => String(row[7] ?? "") !== "停用")) {
  throw new Error("即科两个京东商户号的停用状态被改动");
}

const styleResult = await workbook.inspect({
  kind: "computedStyle",
  sheetId: "渠道商户清单",
  range: "A2:L109",
  maxChars: 500000,
});
let greenCells = 0;
const greenRows = new Set();
for (const line of styleResult.ndjson.split("\n")) {
  let item;
  try { item = JSON.parse(line); } catch { continue; }
  if (String(item?.style?.fill?.color?.value ?? "").toUpperCase() !== "92D050") continue;
  greenCells += 1;
  const match = String(item.for ?? "").match(/^[A-Z]+(\d+)$/);
  if (match) greenRows.add(Number(match[1]));
}
if (greenCells !== 711 || greenRows.size !== 93) {
  throw new Error(`手工绿色底色数量异常：${greenRows.size} 行 / ${greenCells} 个单元格`);
}

const formulaErrors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "formula error scan",
});
if (formulaErrors.ndjson.includes('"matchCount":') && !formulaErrors.ndjson.includes('"matchCount":0')) {
  throw new Error(`发现公式错误：${formulaErrors.ndjson}`);
}

await fs.rm(`${inputPath}.inspect.ndjson`, { force: true });
console.log(JSON.stringify({
  ok: true,
  inputPath,
  mainRows: rows.length,
  failureRows: failureRows.length,
  logistics: logistics.map((row) => ({ merchantNo: String(row[3]), status: row[7], lastSuccess: row[9] })),
  failureCounts,
  protectedConsumer: protectedConsumer.map((row) => ({ merchantNo: String(row[3]), status: row[7] })),
  restoredManualHighlights: { rows: greenRows.size, cells: greenCells, color: "#92D050" },
  formulaErrorScan: formulaErrors.ndjson,
}, null, 2));
