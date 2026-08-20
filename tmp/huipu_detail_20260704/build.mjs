import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir =
  "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/019fb16e-5fdc-7f10-99f9-d29f532082b9";
const previewDir =
  "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/tmp/huipu_detail_20260704";

const rows = [
  ["汇浦", "5032026070410001000296560", "陈**", "2026-07-04T10:00:11", 1243.28, 2.74, 1041.73, 1041.73, 201.55, 201.55],
  ["汇浦", "5032026070410002400296564", "粘**", "2026-07-04T10:00:24", 1808.40, 3.98, 1515.24, 1515.24, 293.16, 293.16],
  ["汇浦直连", "5032026070410002400296565", "胡**", "2026-07-04T10:00:25", 1770.73, 3.90, 1473.46, 1473.46, 297.27, 297.27],
  ["汇浦直连", "5032026070410002500296568", "黄**", "2026-07-04T10:00:26", 5178.74, 11.39, 4138.23, 4138.23, 1040.51, 1040.51],
  ["汇浦直连", "5032026070410002600296569", "黄**", "2026-07-04T10:00:27", 1883.75, 4.14, 1567.51, 1567.51, 316.24, 316.24],
  ["汇浦直连", "5032026070410002900296573", "张**", "2026-07-04T10:00:30", 4765.28, 10.48, 4075.53, 4075.53, 689.75, 689.75],
  ["汇浦直连", "5032026070410003000296574", "黄**", "2026-07-04T10:00:31", 1319.62, 2.90, 1128.61, 1128.61, 191.01, 191.01],
  ["汇浦直连", "5032026070410003100296576", "刘**", "2026-07-04T10:00:32", 1569.31, 3.45, 1254.01, 1254.01, 315.30, 315.30],
  ["汇浦直连", "5032026070410003200296577", "严**", "2026-07-04T10:00:33", 1429.58, 3.15, 1222.66, 1222.66, 206.92, 206.92],
  ["汇浦直连", "5032026070410003300296578", "田**", "2026-07-04T10:00:34", 1172.99, 2.58, 1003.21, 1003.21, 169.78, 169.78],
  ["汇浦直连", "5032026070410003400296579", "刘**", "2026-07-04T10:00:34", 3277.73, 7.21, 2727.47, 2727.47, 550.26, 550.26],
  ["汇浦直连", "5032026070410003500296580", "王**", "2026-07-04T10:00:35", 2785.86, 6.13, 2382.62, 2382.62, 403.24, 403.24],
  ["汇浦直连", "5032026070410003700296583", "邱**", "2026-07-04T10:00:37", 3452.03, 7.59, 2758.82, 2758.82, 693.21, 693.21],
  ["汇浦", "5032026070410003800296585", "蒋**", "2026-07-04T10:00:38", 1318.63, 2.90, 1104.86, 1104.86, 213.77, 213.77],
  ["汇浦直连", "5032026070410003900296587", "胡**", "2026-07-04T10:00:40", 1569.10, 3.45, 1254.01, 1254.01, 315.09, 315.09],
  ["汇浦直连", "5032026070410004000296589", "李**", "2026-07-04T10:00:41", 1176.83, 2.59, 940.51, 940.51, 236.32, 236.32],
  ["汇浦直连", "5032026070410004100296590", "朱**", "2026-07-04T10:00:42", 1490.65, 3.28, 1191.31, 1191.31, 299.34, 299.34],
  ["汇浦直连", "5032026070410004400296592", "邱**", "2026-07-04T10:00:44", 3883.53, 8.54, 3103.67, 3103.67, 779.86, 779.86],
  ["汇浦直连", "5032026070410004500296595", "李**", "2026-07-04T10:00:46", 1571.86, 3.46, 1348.06, 1348.06, 223.80, 223.80],
  ["汇浦直连", "5032026070410004600296596", "罗**", "2026-07-04T10:00:46", 1864.30, 4.10, 1598.86, 1598.86, 265.44, 265.44],
  ["汇浦直连", "5032026070410004700296597", "程**", "2026-07-04T10:00:47", 2079.06, 4.57, 1661.56, 1661.56, 417.50, 417.50],
  ["汇浦直连", "5032026070410004800296598", "裴**", "2026-07-04T10:00:48", 3618.94, 7.96, 3103.67, 3103.67, 515.27, 515.27],
  ["汇浦", "5032026070410005000296602", "张**", "2026-07-04T10:00:50", 1770.73, 3.90, 1483.67, 1483.67, 287.06, 287.06],
  ["汇浦直连", "5032026070410005000296603", "刘**", "2026-07-04T10:00:51", 2314.43, 5.09, 1849.66, 1849.66, 464.77, 464.77],
  ["汇浦直连", "5032026070410005100296605", "吴**", "2026-07-04T10:00:51", 2637.25, 5.80, 2194.51, 2194.51, 442.74, 442.74],
  ["汇浦", "5032026070410010700296614", "李**", "2026-07-04T10:01:07", 1569.31, 3.45, 1262.70, 1262.70, 306.61, 306.61],
  ["汇浦", "5032026070410010800296615", "唐**", "2026-07-04T10:01:08", 1921.43, 4.23, 1609.94, 1609.94, 311.49, 311.49],
  ["汇浦直连", "5032026070410015800296661", "欧**", "2026-07-04T10:01:58", 5274.51, 11.60, 4389.03, 4389.03, 885.48, 885.48],
  ["汇浦直连", "5032026070411255800296748", "周**", "2026-07-04T11:25:59", 1580.08, 3.48, 1261.37, 1261.37, 318.71, 318.71],
  ["汇浦直连", "5032026070414001900296757", "郭**", "2026-07-04T14:00:20", 2485.74, 5.47, 2131.81, 2485.74, 353.93, 0.00],
  ["汇浦直连", "5032026070414002000296758", "周**", "2026-07-04T14:00:21", 1961.38, 4.32, 1567.51, 1961.38, 393.87, 0.00],
  ["汇浦直连", "5032026070414014400296816", "宋**", "2026-07-04T14:01:45", 2109.80, 4.64, 1755.61, 2109.80, 354.19, 0.00],
  ["汇浦", "5032026070414015500296831", "徐**", "2026-07-04T14:01:55", 10360.64, 22.79, 8681.07, 10360.64, 1679.57, 0.00],
  ["汇浦", "5032026070416231500296917", "赵**", "2026-07-04T16:23:15", 5688.76, 12.52, 4577.29, 5688.76, 1111.47, 0.00],
  ["汇浦", "5032026070417003600296935", "汤**", "2026-07-04T17:00:36", 2438.71, 5.37, 1988.75, 2438.71, 449.96, 0.00],
  ["汇浦", "5032026070417013200296959", "车**", "2026-07-04T17:01:32", 2762.78, 6.08, 2220.77, 2762.78, 542.01, 0.00],
  ["汇浦直连", "5032026070417013400296962", "庞**", "2026-07-04T17:01:34", 1941.62, 4.27, 1549.05, 1941.62, 392.57, 0.00],
  ["汇浦直连", "5032026070417015500296999", "谢**", "2026-07-04T17:01:56", 2884.04, 6.34, 2302.00, 2884.04, 582.04, 0.00],
  ["汇浦直连", "5032026070418000900297066", "马**", "2026-07-04T18:00:09", 1961.38, 4.32, 1567.51, 1961.38, 393.87, 0.00],
];

function parseLocalDateTime(value) {
  const [datePart, timePart] = value.split("T");
  const [year, month, day] = datePart.split("-").map(Number);
  const [hour, minute, second] = timePart.split(":").map(Number);
  return new Date(Date.UTC(year, month - 1, day, hour, minute, second));
}

const workbook = Workbook.create();
const detail = workbook.worksheets.add("交易明细");
const notes = workbook.worksheets.add("口径与核对");

detail.showGridLines = false;
detail.mergeCells("A1:L1");
detail.getRange("A1").values = [["嘉泰设备租赁｜2026-07-04 汇浦支付清分明细"]];
detail.getRange("A1:L1").format = {
  fill: "#17365D",
  font: { bold: true, color: "#FFFFFF", size: 16 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
};
detail.getRange("A1:L1").format.rowHeight = 30;

detail.mergeCells("A2:L2");
detail.getRange("A2").values = [[
  "范围：生产环境；支付成功消费交易；汇浦与汇浦直连。客户名已由 WorkHub 只读出口脱敏。"
]];
detail.getRange("A2:L2").format = {
  fill: "#D9EAF7",
  font: { color: "#334155", italic: true },
  wrapText: true,
  verticalAlignment: "center",
};
detail.getRange("A2:L2").format.rowHeight = 27;

const headers = [
  "项目",
  "支付订单号",
  "客户名",
  "交易时间",
  "交易金额",
  "手续费",
  "资方应分账金额",
  "资方实际分账金额",
  "合作方应分账金额",
  "合作方实际分账金额",
  "资方承担手续费",
  "合作方承担手续费",
];
detail.getRange("A4:L4").values = [headers];
detail.getRange("A4:L4").format = {
  fill: "#2F75B5",
  font: { bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
  verticalAlignment: "center",
  wrapText: true,
  borders: { preset: "outside", style: "thin", color: "#1F4E78" },
};
detail.getRange("A4:L4").format.rowHeight = 34;

const dataStart = 5;
const dataEnd = dataStart + rows.length - 1;
const dataMatrix = rows.map((r) => [
  r[0],
  `\u200B${r[1]}`,
  r[2],
  parseLocalDateTime(r[3]),
  r[4],
  r[5],
  r[6],
  r[7],
  r[8],
  r[9],
  null,
  null,
]);
detail.getRange(`A${dataStart}:L${dataEnd}`).values = dataMatrix;

detail.getRange(`K${dataStart}`).formulas = [[`=F${dataStart}`]];
detail.getRange(`K${dataStart}:K${dataEnd}`).fillDown();
detail.getRange(`L${dataStart}`).formulas = [["=0"]];
detail.getRange(`L${dataStart}:L${dataEnd}`).fillDown();

detail.getRange(`B${dataStart}:B${dataEnd}`).format.numberFormat = "@";
detail.getRange(`D${dataStart}:D${dataEnd}`).format.numberFormat = "yyyy-mm-dd hh:mm:ss";
detail.getRange(`E${dataStart}:L${dataEnd}`).format.numberFormat = "#,##0.00";
detail.getRange(`A${dataStart}:D${dataEnd}`).format.verticalAlignment = "center";
detail.getRange(`E${dataStart}:L${dataEnd}`).format.horizontalAlignment = "right";
detail.getRange(`A${dataStart}:L${dataEnd}`).format.borders = {
  insideHorizontal: { style: "thin", color: "#E2E8F0" },
};
for (let row = dataStart; row <= dataEnd; row += 2) {
  detail.getRange(`A${row}:L${row}`).format.fill = "#DDEBF7";
}

detail.getRange(`H${dataStart}:H${dataEnd}`).conditionalFormats.addCustom(
  `=H${dataStart}<>G${dataStart}`,
  { fill: "#FFF2CC", font: { color: "#9C5700", bold: true } },
);
detail.getRange(`J${dataStart}:J${dataEnd}`).conditionalFormats.addCustom(
  `=J${dataStart}<>I${dataStart}`,
  { fill: "#FCE4D6", font: { color: "#C00000", bold: true } },
);

const totalRow = dataEnd + 1;
detail.mergeCells(`A${totalRow}:D${totalRow}`);
detail.getRange(`A${totalRow}`).formulas = [[`="合计（"&COUNTA(B${dataStart}:B${dataEnd})&"笔）"`]];
detail.getRange(`E${totalRow}:L${totalRow}`).formulas = [[
  `=SUM(E${dataStart}:E${dataEnd})`,
  `=SUM(F${dataStart}:F${dataEnd})`,
  `=SUM(G${dataStart}:G${dataEnd})`,
  `=SUM(H${dataStart}:H${dataEnd})`,
  `=SUM(I${dataStart}:I${dataEnd})`,
  `=SUM(J${dataStart}:J${dataEnd})`,
  `=SUM(K${dataStart}:K${dataEnd})`,
  `=SUM(L${dataStart}:L${dataEnd})`,
]];
detail.getRange(`A${totalRow}:L${totalRow}`).format = {
  fill: "#D9EAD3",
  font: { bold: true, color: "#274E13" },
  borders: {
    top: { style: "double", color: "#548235" },
    bottom: { style: "thin", color: "#548235" },
  },
  verticalAlignment: "center",
};
detail.getRange(`E${totalRow}:L${totalRow}`).format.numberFormat = "#,##0.00";
detail.getRange(`A${totalRow}:D${totalRow}`).format.horizontalAlignment = "left";
detail.getRange(`E${totalRow}:L${totalRow}`).format.horizontalAlignment = "right";
detail.getRange(`A${totalRow}:L${totalRow}`).format.rowHeight = 24;

detail.freezePanes.freezeRows(4);
detail.freezePanes.freezeColumns(2);
detail.getRange(`A1:L${totalRow}`).format.font.name = "Aptos";
detail.getRange(`A${dataStart}:A${dataEnd}`).format.columnWidth = 11;
detail.getRange(`B${dataStart}:B${dataEnd}`).format.columnWidth = 26;
detail.getRange(`C${dataStart}:C${dataEnd}`).format.columnWidth = 10;
detail.getRange(`D${dataStart}:D${dataEnd}`).format.columnWidth = 20;
detail.getRange(`E${dataStart}:F${dataEnd}`).format.columnWidth = 13;
detail.getRange(`G${dataStart}:L${dataEnd}`).format.columnWidth = 17;

notes.showGridLines = false;
notes.mergeCells("A1:D1");
notes.getRange("A1").values = [["统计口径与金额核对"]];
notes.getRange("A1:D1").format = {
  fill: "#17365D",
  font: { bold: true, color: "#FFFFFF", size: 16 },
  horizontalAlignment: "center",
  verticalAlignment: "center",
};
notes.getRange("A1:D1").format.rowHeight = 30;

notes.getRange("A3:B11").values = [
  ["指标", "结果"],
  ["交易笔数", null],
  ["交易总金额", null],
  ["手续费合计", null],
  ["资方应分账合计", null],
  ["资方实际分账合计", null],
  ["合作方应分账合计", null],
  ["合作方实际分账合计", null],
  ["分账总额勾稽差", null],
];
notes.getRange("B4:B11").formulas = [
  [`=COUNTA('交易明细'!B${dataStart}:B${dataEnd})`],
  [`=SUM('交易明细'!E${dataStart}:E${dataEnd})`],
  [`=SUM('交易明细'!F${dataStart}:F${dataEnd})`],
  [`=SUM('交易明细'!G${dataStart}:G${dataEnd})`],
  [`=SUM('交易明细'!H${dataStart}:H${dataEnd})`],
  [`=SUM('交易明细'!I${dataStart}:I${dataEnd})`],
  [`=SUM('交易明细'!J${dataStart}:J${dataEnd})`],
  [`=(B8+B10)-(B7+B9)`],
];
notes.getRange("A3:B3").format = {
  fill: "#2F75B5",
  font: { bold: true, color: "#FFFFFF" },
  horizontalAlignment: "center",
};
notes.getRange("A4:B11").format.borders = {
  insideHorizontal: { style: "thin", color: "#E2E8F0" },
  outside: { style: "thin", color: "#94A3B8" },
};
notes.getRange("B4").format.numberFormat = "#,##0";
notes.getRange("B5:B11").format.numberFormat = "#,##0.00";
notes.getRange("A4:A11").format.font.bold = true;
notes.getRange("B4:B11").format.horizontalAlignment = "right";

notes.getRange("A13:D13").merge();
notes.getRange("A13").values = [["口径说明"]];
notes.getRange("A13:D13").format = {
  fill: "#D9EAF7",
  font: { bold: true, color: "#17365D" },
};
notes.getRange("A14:D19").values = [
  ["1", "交易范围", "生产环境；2026-07-04；汇浦/汇浦直连；消费交易；支付成功。", null],
  ["2", "应分账金额", "有清分转换记录时取转换前 source_user 金额；无转换记录时取实际分账明细还原。", null],
  ["3", "实际分账金额", "取支付库 order_business_asinfo.arrive_amount，按 C0001（资方）和 P0001（合作方）汇总。", null],
  ["4", "手续费承担", "两个商户均为外扣模式，主账户归属资方 C；因此交易手续费列入资方承担，合作方承担为 0。", null],
  ["5", "客户名", "由 WorkHub 生产只读查询出口自动脱敏。", null],
  ["6", "数据来源", "WorkHub 受控只读 MCP：jiatai-hp-db-prod / equip_lease_payment。", null],
];
notes.mergeCells("C14:D14");
notes.mergeCells("C15:D15");
notes.mergeCells("C16:D16");
notes.mergeCells("C17:D17");
notes.mergeCells("C18:D18");
notes.mergeCells("C19:D19");
notes.getRange("A14:D19").format = {
  wrapText: true,
  verticalAlignment: "top",
  borders: { preset: "inside", style: "thin", color: "#E2E8F0" },
};
notes.getRange("A14:A19").format.horizontalAlignment = "center";
notes.getRange("B14:B19").format.font.bold = true;
notes.getRange("A14:D19").format.rowHeight = 36;

notes.getRange("A1:D19").format.font.name = "Aptos";
notes.getRange("A1:A19").format.columnWidth = 22;
notes.getRange("B1:B19").format.columnWidth = 20;
notes.getRange("C1:D19").format.columnWidth = 28;
notes.freezePanes.freezeRows(3);

await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(previewDir, { recursive: true });

const detailCheck = await workbook.inspect({
  kind: "table",
  range: `交易明细!A1:L${totalRow}`,
  include: "values,formulas",
  tableMaxRows: 8,
  tableMaxCols: 12,
});
const notesCheck = await workbook.inspect({
  kind: "table",
  range: "口径与核对!A1:D19",
  include: "values,formulas",
  tableMaxRows: 19,
  tableMaxCols: 4,
});
const formulaErrors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
});

const detailPreview = await workbook.render({
  sheetName: "交易明细",
  range: "A1:L18",
  scale: 1.2,
  format: "png",
});
await fs.writeFile(
  `${previewDir}/detail-preview.png`,
  new Uint8Array(await detailPreview.arrayBuffer()),
);
const notesPreview = await workbook.render({
  sheetName: "口径与核对",
  range: "A1:D19",
  scale: 1.4,
  format: "png",
});
await fs.writeFile(
  `${previewDir}/notes-preview.png`,
  new Uint8Array(await notesPreview.arrayBuffer()),
);

const outputPath = `${outputDir}/嘉泰设备租赁_2026-07-04_汇浦支付清分明细.xlsx`;
const xlsx = await SpreadsheetFile.exportXlsx(workbook);
await xlsx.save(outputPath);

console.log(JSON.stringify({
  outputPath,
  rowCount: rows.length,
  detailCheck: detailCheck.ndjson,
  notesCheck: notesCheck.ndjson,
  formulaErrors: formulaErrors.ndjson,
}));
