import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const workDir =
  "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/019fb19e-dfc9-7ac3-9600-e8a93321b309/work";
const outputDir =
  "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/019fb19e-dfc9-7ac3-9600-e8a93321b309";
const outputPath = `${outputDir}/嘉泰消费分期-消费分期_资方服务费对账_2026-06.xlsx`;
const data = JSON.parse(
  await fs.readFile(`${workDir}/reconciliation_data.json`, "utf8"),
);

const workbook = Workbook.create();
const summarySheet = workbook.worksheets.add("对账结论");
const differenceSheet = workbook.worksheets.add("差异明细");
const dailySheet = workbook.worksheets.add("日期汇总");
const settlementSheet = workbook.worksheets.add("结算汇总");
const groupSheet = workbook.worksheets.add("分段校验");
const checksSheet = workbook.worksheets.add("检查");
const sourceSheet = workbook.worksheets.add("口径与来源");

workbook.comments.setSelf({ displayName: "User" });

const colors = {
  navy: "#16324F",
  blue: "#2F75B5",
  lightBlue: "#D9EAF7",
  paleBlue: "#EDF5FB",
  green: "#2E7D32",
  lightGreen: "#E2F0D9",
  red: "#C62828",
  lightRed: "#FCE4D6",
  amber: "#BF6A02",
  lightAmber: "#FFF2CC",
  gray: "#667085",
  lightGray: "#F2F4F7",
  border: "#D0D5DD",
  white: "#FFFFFF",
  black: "#101828",
};

const moneyFormat = '#,##0.00;[Red](#,##0.00);-';
const countFormat = '#,##0;[Red](#,##0);-';
const percentFormat = '0.00%;[Red](0.00%);-';

function styleTitle(sheet, range, text) {
  const target = sheet.getRange(range);
  target.merge();
  target.values = [[text]];
  target.format = {
    fill: colors.navy,
    font: { bold: true, color: colors.white, size: 16 },
    verticalAlignment: "center",
    horizontalAlignment: "left",
  };
  target.format.rowHeight = 32;
}

function styleHeader(range) {
  range.format = {
    fill: colors.blue,
    font: { bold: true, color: colors.white },
    horizontalAlignment: "center",
    verticalAlignment: "center",
    wrapText: true,
    borders: { preset: "all", style: "thin", color: colors.border },
  };
  range.format.rowHeight = 28;
}

function styleBody(range) {
  range.format = {
    font: { color: colors.black, size: 10 },
    verticalAlignment: "center",
    borders: {
      insideHorizontal: { style: "thin", color: "#E4E7EC" },
    },
  };
}

function parseDate(value) {
  return new Date(`${value}T00:00:00Z`);
}

function tableRange(lastColumn, rowCount) {
  return `A1:${lastColumn}${rowCount + 1}`;
}

// 对账结论
summarySheet.showGridLines = false;
styleTitle(summarySheet, "A1:H1", "嘉泰消费分期 × 消费分期｜资方服务费对账");
summarySheet.getRange("A2:H2").merge();
summarySheet.getRange("A2").values = [[
  "生产环境（prod）｜master｜源文件范围：2026-05-27 至 2026-06-30｜生成日期：2026-07-30",
]];
summarySheet.getRange("A2:H2").format = {
  fill: colors.lightGray,
  font: { color: colors.gray, italic: true, size: 10 },
  verticalAlignment: "center",
};
summarySheet.getRange("A2:H2").format.rowHeight = 24;

summarySheet.getRange("A4:H4").values = [[
  "嘉泰服务费明细数",
  "嘉泰服务费金额",
  "消费分期P0003金额",
  "账单金额差额",
  "差异账单数",
  "零金额回购记录",
  "消费分期paid_amount",
  "paid_amount缺口",
]];
styleHeader(summarySheet.getRange("A4:H4"));
summarySheet.getRange("A5").formulas = [["='检查'!B4"]];
summarySheet.getRange("B5").formulas = [["='日期汇总'!E2"]];
summarySheet.getRange("C5").formulas = [["='分段校验'!H39"]];
summarySheet.getRange("D5").formulas = [["=C5-B5"]];
summarySheet.getRange("E5").formulas = [["=COUNTA('差异明细'!A2:A149)"]];
summarySheet.getRange("F5").values = [[data.summary.source_zero_fee_rows]];
summarySheet.getRange("G5").formulas = [["='分段校验'!J39"]];
summarySheet.getRange("H5").formulas = [["=B5-G5"]];
summarySheet.getRange("A5:H5").format = {
  fill: colors.paleBlue,
  font: { bold: true, color: colors.black, size: 12 },
  horizontalAlignment: "right",
  verticalAlignment: "center",
  borders: { preset: "outside", style: "thin", color: colors.border },
};
summarySheet.getRange("A5:H5").format.rowHeight = 30;
summarySheet.getRange("A5").format.numberFormat = countFormat;
summarySheet.getRange("B5:D5").format.numberFormat = moneyFormat;
summarySheet.getRange("E5:F5").format.numberFormat = countFormat;
summarySheet.getRange("G5:H5").format.numberFormat = moneyFormat;
summarySheet.getRange("A5:C5").format.font.color = colors.green;
summarySheet.getRange("E5").format.font.color = colors.green;
summarySheet.getRange("G5,G5").format.font.color = colors.green;
summarySheet.getRange("D5").conditionalFormats.add("cellIs", {
  operator: "notEqual",
  formula: 0,
  format: { fill: colors.lightRed, font: { bold: true, color: colors.red } },
});

summarySheet.getRange("A7:H7").merge();
summarySheet.getRange("A7").values = [["核心结论"]];
summarySheet.getRange("A7:H7").format = {
  fill: colors.navy,
  font: { bold: true, color: colors.white },
};
summarySheet.getRange("A8:H11").values = [
  [
    "1",
    "文件中共有 111,479 笔资方服务费记录，金额 4,572,948.50 元；其中 7,033 笔为零金额回购记录。",
    "",
    "",
    "",
    "",
    "",
    "",
  ],
  [
    "2",
    "所有 104,446 笔正金额记录均可通过“嘉泰放款申请单号_期数”关联到消费分期资方账单。",
    "",
    "",
    "",
    "",
    "",
    "",
  ],
  [
    "3",
    "共 148 笔金额不一致：嘉泰实收 2,616.40 元，消费分期 P0003 为 5,232.86 元，消费分期高 2,616.46 元。",
    "",
    "",
    "",
    "",
    "",
    "",
  ],
  [
    "4",
    "消费分期 paid_amount 比嘉泰实收少 528,901.69 元；该字段受代偿、回购等非普通客户支付链路影响，不应直接作为本次账单金额差异。",
    "",
    "",
    "",
    "",
    "",
    "",
  ],
];
for (let row = 8; row <= 11; row += 1) {
  summarySheet.getRange(`B${row}:H${row}`).merge();
}
summarySheet.getRange("A8:A11").format = {
  fill: colors.lightBlue,
  font: { bold: true, color: colors.blue },
  horizontalAlignment: "center",
  verticalAlignment: "center",
};
summarySheet.getRange("B8:H11").format = {
  fill: colors.white,
  font: { color: colors.black, size: 10 },
  wrapText: true,
  verticalAlignment: "center",
  borders: {
    insideHorizontal: { style: "thin", color: colors.border },
  },
};
summarySheet.getRange("A8:H11").format.rowHeight = 34;

summarySheet.getRange("A13:D13").values = [[
  "差异口径",
  "笔数",
  "金额（元）",
  "判断",
]];
styleHeader(summarySheet.getRange("A13:D13"));
summarySheet.getRange("A14:D16").values = [
  [
    "消费分期P0003高于嘉泰实收",
    data.summary.difference_bill_count,
    data.summary.fee_difference,
    "需核查重复/调整原因",
  ],
  ["零金额回购且无P0003科目", 7031, 0, "不影响金额"],
  [
    "消费分期paid_amount低于嘉泰实收",
    data.summary.source_fee_rows,
    data.summary.paid_field_gap,
    "业务状态字段，不作为账单错账",
  ],
];
styleBody(summarySheet.getRange("A14:D16"));
summarySheet.getRange("B14:B16").format.numberFormat = countFormat;
summarySheet.getRange("C14:C16").format.numberFormat = moneyFormat;
summarySheet.getRange("C14").format = {
  fill: colors.lightRed,
  font: { bold: true, color: colors.red },
  numberFormat: moneyFormat,
};
summarySheet.getRange("C15").format = {
  fill: colors.lightGreen,
  font: { bold: true, color: colors.green },
  numberFormat: moneyFormat,
};
summarySheet.getRange("C16").format = {
  fill: colors.lightAmber,
  font: { bold: true, color: colors.amber },
  numberFormat: moneyFormat,
};
summarySheet.freezePanes.freezeRows(2);
summarySheet.getRange("A1:H16").format.font.name = "Aptos";
summarySheet.getRange("A1:H16").format.columnWidth = 16;
summarySheet.getRange("A1").format.columnWidth = 20;
summarySheet.getRange("B1").format.columnWidth = 22;
summarySheet.getRange("D1").format.columnWidth = 18;
summarySheet.getRange("G1").format.columnWidth = 21;
summarySheet.getRange("H1").format.columnWidth = 18;
summarySheet.getRange("A14:A16").format.wrapText = true;
summarySheet.getRange("A14:D16").format.rowHeight = 28;

workbook.comments.addThread(
  { cell: summarySheet.getRange("B5") },
  "Source: 嘉泰消费分期“6月提单实际还款数据.xlsx”中交易成功、还款类型=资方服务费的全部记录。",
);
workbook.comments.addThread(
  { cell: summarySheet.getRange("C5") },
  "Source: 消费分期生产只读库 amp_saps.bill_fee_detail，科目 P0003，按 repayment_plan.funder_bill_no 与嘉泰放款申请单号_期数匹配。",
);

// 差异明细
const diffHeaders = [
  "序号",
  "资方账单键",
  "嘉泰账单号",
  "嘉泰进件申请单号",
  "嘉泰放款申请单号",
  "合同编号",
  "客户姓名",
  "实还日期",
  "期数",
  "还款方式",
  "结算方式",
  "嘉泰实收服务费",
  "消费分期申请单号",
  "消费分期账单号",
  "消费分期P0003金额",
  "差额",
  "消费/嘉泰倍数",
  "差异说明",
];
differenceSheet.getRange("A1:R1").values = [diffHeaders];
styleHeader(differenceSheet.getRange("A1:R1"));
const diffValues = data.differences.map((row, index) => [
  index + 1,
  row.funder_bill_no,
  row.jiatai_bill_no,
  row.jiatai_application_no,
  row.jiatai_loan_no,
  row.contract_no,
  row.customer_name,
  parseDate(row.paid_date),
  row.period,
  row.payment_method,
  row.settlement_type,
  row.source_fee,
  row.consumer_application_no,
  row.consumer_bill_no,
  row.consumer_fee,
  null,
  null,
  row.diagnosis,
]);
differenceSheet.getRange(`A2:R${diffValues.length + 1}`).values = diffValues;
differenceSheet.getRange("P2").formulas = [["=O2-L2"]];
differenceSheet.getRange(`P2:P${diffValues.length + 1}`).fillDown();
differenceSheet.getRange("Q2").formulas = [["=IF(L2=0,\"\",O2/L2)"]];
differenceSheet.getRange(`Q2:Q${diffValues.length + 1}`).fillDown();
styleBody(differenceSheet.getRange(`A2:R${diffValues.length + 1}`));
differenceSheet.getRange(`H2:H${diffValues.length + 1}`).format.numberFormat =
  "yyyy-mm-dd";
differenceSheet.getRange(`L2:L${diffValues.length + 1}`).format.numberFormat =
  moneyFormat;
differenceSheet.getRange(`O2:P${diffValues.length + 1}`).format.numberFormat =
  moneyFormat;
differenceSheet.getRange(`Q2:Q${diffValues.length + 1}`).format.numberFormat =
  "0.00x";
differenceSheet.getRange(`P2:P${diffValues.length + 1}`).conditionalFormats.add(
  "cellIs",
  {
    operator: "greaterThan",
    formula: 0,
    format: { fill: colors.lightRed, font: { color: colors.red, bold: true } },
  },
);
differenceSheet.tables.add(
  tableRange("R", diffValues.length),
  true,
  "DifferenceTable",
);
differenceSheet.freezePanes.freezeRows(1);
differenceSheet.freezePanes.freezeColumns(2);
const diffWidths = [8, 27, 23, 23, 23, 24, 12, 12, 8, 10, 12, 15, 23, 23, 16, 14, 14, 28];
diffWidths.forEach((width, index) => {
  differenceSheet.getRangeByIndexes(0, index, 1, 1).format.columnWidth = width;
});
differenceSheet.getRange(`A1:R${diffValues.length + 1}`).format.font.name =
  "Aptos";

// 日期汇总
dailySheet.showGridLines = false;
styleTitle(dailySheet, "A1:C1", "资方服务费按实还日期汇总");
dailySheet.getRange("A3:C3").values = [["实还日期", "笔数", "嘉泰实收服务费"]];
styleHeader(dailySheet.getRange("A3:C3"));
const dailyValues = data.daily.map((row) => [
  parseDate(row.date),
  row.count,
  row.source_fee,
]);
dailySheet.getRange(`A4:C${dailyValues.length + 3}`).values = dailyValues;
styleBody(dailySheet.getRange(`A4:C${dailyValues.length + 3}`));
dailySheet.getRange(`A4:A${dailyValues.length + 3}`).format.numberFormat =
  "yyyy-mm-dd";
dailySheet.getRange(`B4:B${dailyValues.length + 3}`).format.numberFormat =
  countFormat;
dailySheet.getRange(`C4:C${dailyValues.length + 3}`).format.numberFormat =
  moneyFormat;
dailySheet.getRange("E1").values = [["文件范围总额"]];
dailySheet.getRange("E2").formulas = [[`=SUM(C4:C${dailyValues.length + 3})`]];
dailySheet.getRange("E1:E2").format = {
  fill: colors.paleBlue,
  font: { bold: true, color: colors.navy },
  horizontalAlignment: "right",
  borders: { preset: "outside", style: "thin", color: colors.border },
};
dailySheet.getRange("E2").format.numberFormat = moneyFormat;
dailySheet.getRange("A3:C3").format.font.color = colors.white;
dailySheet.tables.add(
  `A3:C${dailyValues.length + 3}`,
  true,
  "DailySummaryTable",
);
dailySheet.freezePanes.freezeRows(3);
dailySheet.getRange("A1:E40").format.font.name = "Aptos";
dailySheet.getRange("A1").format.columnWidth = 14;
dailySheet.getRange("B1").format.columnWidth = 12;
dailySheet.getRange("C1").format.columnWidth = 20;
dailySheet.getRange("E1").format.columnWidth = 20;

// 结算汇总
settlementSheet.showGridLines = false;
styleTitle(settlementSheet, "A1:H1", "资方服务费按结算方式与还款方式汇总");
settlementSheet.getRange("A3:D3").values = [[
  "结算方式",
  "笔数",
  "嘉泰实收服务费",
  "金额占比",
]];
styleHeader(settlementSheet.getRange("A3:D3"));
const settlementValues = data.settlement.map((row) => [
  row.settlement_type,
  row.count,
  row.source_fee,
  null,
]);
settlementSheet.getRange(`A4:D${settlementValues.length + 3}`).values =
  settlementValues;
settlementSheet.getRange("D4").formulas = [[
  `=C4/SUM($C$4:$C$${settlementValues.length + 3})`,
]];
settlementSheet
  .getRange(`D4:D${settlementValues.length + 3}`)
  .fillDown();
styleBody(settlementSheet.getRange(`A4:D${settlementValues.length + 3}`));
settlementSheet.getRange(`B4:B${settlementValues.length + 3}`).format.numberFormat =
  countFormat;
settlementSheet.getRange(`C4:C${settlementValues.length + 3}`).format.numberFormat =
  moneyFormat;
settlementSheet.getRange(`D4:D${settlementValues.length + 3}`).format.numberFormat =
  percentFormat;
settlementSheet.getRange("F3:H3").values = [[
  "还款方式",
  "笔数",
  "嘉泰实收服务费",
]];
styleHeader(settlementSheet.getRange("F3:H3"));
const paymentValues = data.payment.map((row) => [
  row.payment_method,
  row.count,
  row.source_fee,
]);
settlementSheet.getRange(`F4:H${paymentValues.length + 3}`).values =
  paymentValues;
styleBody(settlementSheet.getRange(`F4:H${paymentValues.length + 3}`));
settlementSheet.getRange(`G4:G${paymentValues.length + 3}`).format.numberFormat =
  countFormat;
settlementSheet.getRange(`H4:H${paymentValues.length + 3}`).format.numberFormat =
  moneyFormat;
settlementSheet.tables.add(
  `A3:D${settlementValues.length + 3}`,
  true,
  "SettlementSummaryTable",
);
settlementSheet.tables.add(
  `F3:H${paymentValues.length + 3}`,
  true,
  "PaymentSummaryTable",
);
settlementSheet.freezePanes.freezeRows(3);
settlementSheet.getRange("A1:H12").format.font.name = "Aptos";
["A", "F"].forEach((col) => {
  settlementSheet.getRange(`${col}1`).format.columnWidth = 16;
});
["B", "D", "G"].forEach((col) => {
  settlementSheet.getRange(`${col}1`).format.columnWidth = 13;
});
["C", "H"].forEach((col) => {
  settlementSheet.getRange(`${col}1`).format.columnWidth = 20;
});

// 分段校验
groupSheet.showGridLines = false;
styleTitle(groupSheet, "A1:K1", "消费分期生产只读数据分段校验");
const groupHeaders = [
  "分段",
  "首个资方账单键",
  "末个资方账单键",
  "嘉泰记录数",
  "消费分期P0003数",
  "零值无P0003数",
  "嘉泰服务费",
  "消费分期P0003金额",
  "账单差额",
  "消费分期paid_amount",
  "paid_amount缺口",
];
groupSheet.getRange("A3:K3").values = [groupHeaders];
styleHeader(groupSheet.getRange("A3:K3"));
const groupValues = data.groups.map((row) => [
  row.group,
  row.first_funder_bill_no,
  row.last_funder_bill_no,
  row.source_count,
  row.consumer_fee_subject_count,
  row.zero_without_fee_subject_count,
  row.source_fee,
  row.consumer_fee,
  null,
  row.consumer_paid_field,
  null,
]);
groupSheet.getRange(`A4:K${groupValues.length + 3}`).values = groupValues;
groupSheet.getRange("I4").formulas = [["=H4-G4"]];
groupSheet.getRange(`I4:I${groupValues.length + 3}`).fillDown();
groupSheet.getRange("K4").formulas = [["=G4-J4"]];
groupSheet.getRange(`K4:K${groupValues.length + 3}`).fillDown();
styleBody(groupSheet.getRange(`A4:K${groupValues.length + 3}`));
groupSheet.getRange(`D4:F${groupValues.length + 3}`).format.numberFormat =
  countFormat;
groupSheet.getRange(`G4:K${groupValues.length + 3}`).format.numberFormat =
  moneyFormat;
groupSheet.getRange(`I4:I${groupValues.length + 3}`).conditionalFormats.add(
  "cellIs",
  {
    operator: "notEqual",
    formula: 0,
    format: { fill: colors.lightRed, font: { color: colors.red, bold: true } },
  },
);
const totalRow = groupValues.length + 4;
groupSheet.getRange(`A${totalRow}:C${totalRow}`).merge();
groupSheet.getRange(`A${totalRow}`).values = [["合计"]];
for (const col of ["D", "E", "F", "G", "H", "I", "J", "K"]) {
  if (["D", "E", "F"].includes(col)) continue;
  groupSheet.getRange(`${col}${totalRow}`).formulas = [[
    `=SUM(${col}4:${col}${groupValues.length + 3})`,
  ]];
}
groupSheet.getRange(`D${totalRow}`).formulas = [[
  `=SUM(D4:D${groupValues.length + 3})`,
]];
groupSheet.getRange(`E${totalRow}`).formulas = [[
  `=SUM(E4:E${groupValues.length + 3})`,
]];
groupSheet.getRange(`F${totalRow}`).formulas = [[
  `=SUM(F4:F${groupValues.length + 3})`,
]];
groupSheet.getRange(`A${totalRow}:K${totalRow}`).format = {
  fill: colors.lightBlue,
  font: { bold: true, color: colors.navy },
  borders: { top: { style: "double", color: colors.navy } },
};
groupSheet.getRange(`D${totalRow}:F${totalRow}`).format.numberFormat =
  countFormat;
groupSheet.getRange(`G${totalRow}:K${totalRow}`).format.numberFormat =
  moneyFormat;
groupSheet.tables.add(
  `A3:K${groupValues.length + 3}`,
  true,
  "GroupCheckTable",
);
groupSheet.freezePanes.freezeRows(3);
groupSheet.freezePanes.freezeColumns(1);
const groupWidths = [12, 29, 29, 12, 15, 15, 17, 20, 15, 20, 18];
groupWidths.forEach((width, index) => {
  groupSheet.getRangeByIndexes(0, index, 1, 1).format.columnWidth = width;
});
groupSheet.getRange(`A1:K${totalRow}`).format.font.name = "Aptos";

// 检查
checksSheet.showGridLines = false;
styleTitle(checksSheet, "A1:G1", "对账检查与勾稽");
checksSheet.getRange("A3:G3").values = [[
  "检查项",
  "实际值",
  "期望值",
  "差异",
  "容差",
  "状态",
  "说明",
]];
styleHeader(checksSheet.getRange("A3:G3"));
checksSheet.getRange("A4:G9").values = [
  ["源服务费记录数", null, data.summary.source_fee_rows, null, 0, null, "正金额+零金额"],
  ["源金额与日期汇总", null, data.summary.source_fee_total, null, 0.01, null, "按实还日期汇总"],
  ["账单总差与差异明细", null, data.summary.fee_difference, null, 0.01, null, "148笔差异合计"],
  ["差异笔数", null, data.summary.difference_bill_count, null, 0, null, "差异明细行数"],
  ["正金额记录全部有P0003", null, data.summary.source_positive_fee_rows, null, 0, null, "P0003数扣除2笔零值科目"],
  ["零值回购记录数", data.summary.source_zero_fee_rows, 7033, null, 0, null, "不影响金额"],
];
checksSheet.getRange("B4").formulas = [["='分段校验'!D39"]];
checksSheet.getRange("B5").formulas = [["='日期汇总'!E2"]];
checksSheet.getRange("B6").formulas = [["=SUM('差异明细'!P2:P149)"]];
checksSheet.getRange("B7").formulas = [["=COUNTA('差异明细'!A2:A149)"]];
checksSheet.getRange("B8").formulas = [["='分段校验'!E39-2"]];
checksSheet.getRange("D4").formulas = [["=ROUND(B4-C4,2)"]];
checksSheet.getRange("D4:D9").fillDown();
checksSheet.getRange("F4").formulas = [["=IF(ABS(D4)<=E4,\"OK\",\"FAIL\")"]];
checksSheet.getRange("F4:F9").fillDown();
styleBody(checksSheet.getRange("A4:G9"));
checksSheet.getRange("B4:E9").format.numberFormat = moneyFormat;
checksSheet.getRange("B4:E4").format.numberFormat = countFormat;
checksSheet.getRange("B7:E9").format.numberFormat = countFormat;
checksSheet.getRange("F4:F9").conditionalFormats.add("containsText", {
  text: "OK",
  format: { fill: colors.lightGreen, font: { color: colors.green, bold: true } },
});
checksSheet.getRange("F4:F9").conditionalFormats.add("containsText", {
  text: "FAIL",
  format: { fill: colors.lightRed, font: { color: colors.red, bold: true } },
});
checksSheet.getRange("A11:B11").merge();
checksSheet.getRange("A11").values = [["MODEL STATUS"]];
checksSheet.getRange("C11:G11").merge();
checksSheet.getRange("C11").formulas = [[
  '=IF(COUNTIF(F4:F9,"FAIL")=0,"PASS","FAIL")',
]];
checksSheet.getRange("A11:B11").format = {
  fill: colors.navy,
  font: { bold: true, color: colors.white },
};
checksSheet.getRange("C11:G11").format = {
  fill: colors.lightGreen,
  font: { bold: true, color: colors.green, size: 14 },
  horizontalAlignment: "center",
};
checksSheet.getRange("A1:G11").format.font.name = "Aptos";
const checkWidths = [26, 18, 18, 16, 12, 12, 32];
checkWidths.forEach((width, index) => {
  checksSheet.getRangeByIndexes(0, index, 1, 1).format.columnWidth = width;
});

// 口径与来源
sourceSheet.showGridLines = false;
styleTitle(sourceSheet, "A1:F1", "对账口径、资源与证据");
sourceSheet.getRange("A3:F3").values = [[
  "项目",
  "值",
  "期间/环境",
  "来源类型",
  "来源",
  "说明",
]];
styleHeader(sourceSheet.getRange("A3:F3"));
const sourceRows = [
  [
    "源台账",
    "6月提单实际还款数据.xlsx",
    "2026-05-27 至 2026-06-30",
    "Excel",
    "嘉泰消费分期实际还款台账",
    "筛选：交易成功、还款类型=资方服务费；文件包含5月末数据。",
  ],
  [
    "自然月6月",
    "108,607笔 / 4,475,397.95元",
    "2026-06-01 至 2026-06-30",
    "Excel汇总",
    "源台账",
    "仅供自然月口径参考；本次对账按文件全部记录。",
  ],
  [
    "嘉泰业务线",
    "BL000003 / th-jiatai-amp",
    "prod / master",
    "WorkHub",
    "jiatai-amp-db-prod",
    "核心表：jiatai_amp_saps.repayment_plan_ledger、repayment_plan。",
  ],
  [
    "消费分期业务线",
    "BL000004 / asset-plateform",
    "prod / master",
    "WorkHub",
    "amp-db-prod",
    "核心表：amp_saps.repayment_plan、bill_fee_detail。",
  ],
  [
    "对账键",
    "嘉泰放款申请单号 + '_' + 期数",
    "文件范围",
    "代码+数据库",
    "repayment_plan.funder_bill_no",
    "嘉泰 repayment_plan.pid 同样采用“放款申请单号_期数”。",
  ],
  [
    "金额口径",
    "消费分期 P0003.total_amount",
    "文件范围",
    "生产只读数据库",
    "amp_saps.bill_fee_detail",
    "P0003=资方服务费；与嘉泰实还金额比较。",
  ],
  [
    "状态口径",
    "paid_amount单列",
    "文件范围",
    "生产只读数据库",
    "amp_saps.bill_fee_detail.paid_amount",
    "代偿、回购等链路可能不更新为普通客户支付口径，不直接判错账。",
  ],
  [
    "嘉泰代码证据",
    "8975bbe20a1c697e770e80dc9440b61673fa8611",
    "master",
    "GitLab受控缓存",
    "jiatai-amp-saps",
    "字段与台账生成逻辑。",
  ],
  [
    "消费分期代码证据",
    "83728b713991b7b7ff6b01bc6dca3ccde73c7fdb",
    "master",
    "GitLab受控缓存",
    "amp-saps",
    "P0003科目、资方账单同步与结算逻辑。",
  ],
  [
    "数据安全",
    "只读、已脱敏",
    "prod",
    "WorkHub MCP",
    "run_readonly_query / readonly",
    "未执行写库、修数、接口调用或服务变更。",
  ],
];
sourceSheet.getRange(`A4:F${sourceRows.length + 3}`).values = sourceRows;
styleBody(sourceSheet.getRange(`A4:F${sourceRows.length + 3}`));
sourceSheet.getRange(`A4:F${sourceRows.length + 3}`).format.wrapText = true;
sourceSheet.getRange(`A4:F${sourceRows.length + 3}`).format.rowHeight = 38;
sourceSheet.tables.add(
  `A3:F${sourceRows.length + 3}`,
  true,
  "SourceTable",
);
sourceSheet.freezePanes.freezeRows(3);
const sourceWidths = [22, 37, 23, 19, 28, 52];
sourceWidths.forEach((width, index) => {
  sourceSheet.getRangeByIndexes(0, index, 1, 1).format.columnWidth = width;
});
sourceSheet.getRange(`A1:F${sourceRows.length + 3}`).format.font.name = "Aptos";

// 公式与样式检查
const summaryInspection = await workbook.inspect({
  kind: "table",
  sheetId: "对账结论",
  range: "A1:H16",
  include: "values,formulas",
  maxChars: 12000,
  tableMaxRows: 20,
  tableMaxCols: 10,
});
const differenceInspection = await workbook.inspect({
  kind: "table",
  sheetId: "差异明细",
  range: "A1:R12",
  include: "values,formulas",
  maxChars: 12000,
  tableMaxRows: 12,
  tableMaxCols: 18,
});
const errorScan = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A",
  options: { useRegex: true, maxResults: 300 },
  summary: "final formula error scan",
});

await fs.writeFile(
  `${workDir}/final-inspection.txt`,
  [
    "SUMMARY",
    summaryInspection.ndjson,
    "DIFFERENCES",
    differenceInspection.ndjson,
    "ERRORS",
    errorScan.ndjson,
  ].join("\n"),
);

for (const sheet of workbook.worksheets.items) {
  const used = sheet.getUsedRange();
  const usedAddress = used.address.includes("!")
    ? used.address.split("!").pop()
    : used.address;
  const preview = await workbook.render({
    sheetName: sheet.name,
    range: usedAddress,
    scale: sheet.name === "差异明细" ? 0.8 : 1,
    format: "png",
  });
  const safeName = sheet.name.replaceAll(/[^\p{L}\p{N}_-]+/gu, "_");
  await fs.writeFile(
    `${workDir}/final-${safeName}.png`,
    new Uint8Array(await preview.arrayBuffer()),
  );
}

await fs.mkdir(outputDir, { recursive: true });
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(
  JSON.stringify({
    outputPath,
    differenceCount: data.differences.length,
    differenceAmount: data.summary.fee_difference,
  }),
);
