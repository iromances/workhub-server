import fs from "node:fs/promises";
import { SpreadsheetFile, Workbook } from "@oai/artifact-tool";

const outputDir = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/20260816_production_trial_model";
const previewDir = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/.tmp/trial_model_runtime/previews";
await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(previewDir, { recursive: true });

const workbook = Workbook.create();
const cover = workbook.worksheets.add("说明与摘要");
const params = workbook.worksheets.add("参数输入");
const vehicles = workbook.worksheets.add("车辆输入");
const detail = workbook.worksheets.add("逐车试算");
const plan = workbook.worksheets.add("还款计划");
const sample = workbook.worksheets.add("生产样本核对");
const config = workbook.worksheets.add("产品配置");
const audit = workbook.worksheets.add("依据与口径");

const navy = "#17365D";
const blue = "#1F4E78";
const teal = "#0F766E";
const lightBlue = "#D9EAF7";
const paleBlue = "#EAF3F8";
const paleGreen = "#E2F0D9";
const paleYellow = "#FFF2CC";
const paleRed = "#FCE4D6";
const gray = "#F2F2F2";
const border = "#B8C4CE";
const black = "#000000";
const inputBlue = "#0000FF";
const linkGreen = "#008000";
const moneyFmt = "#,##0.00;[Red](#,##0.00);-";
const pctFmt = "0.00%;[Red](0.00%);-";
const dateFmt = "yyyy-mm-dd";

function title(sheet, range, text) {
  const r = sheet.getRange(range);
  r.merge();
  r.values = [[text]];
  r.format = {
    fill: navy,
    font: { bold: true, color: "#FFFFFF", size: 16 },
    horizontalAlignment: "left",
    verticalAlignment: "center",
  };
  r.format.rowHeight = 30;
}

function section(sheet, range, text) {
  const r = sheet.getRange(range);
  r.merge();
  r.values = [[text]];
  r.format = {
    fill: blue,
    font: { bold: true, color: "#FFFFFF" },
    horizontalAlignment: "left",
    verticalAlignment: "center",
  };
  r.format.rowHeight = 22;
}

function header(range) {
  range.format = {
    fill: lightBlue,
    font: { bold: true, color: navy },
    horizontalAlignment: "center",
    verticalAlignment: "center",
    wrapText: true,
    borders: { preset: "all", style: "thin", color: border },
  };
}

function bodyGrid(range) {
  range.format.borders = { preset: "all", style: "thin", color: "#D9E1E8" };
  range.format.verticalAlignment = "center";
}

function setWidths(sheet, widths) {
  for (const [col, width] of Object.entries(widths)) {
    sheet.getRange(`${col}:${col}`).format.columnWidth = width;
  }
}

for (const sheet of [cover, params, vehicles, detail, plan, sample, config, audit]) {
  sheet.showGridLines = false;
}

// 产品配置：来自生产只读配置，长表结构便于 INDEX/MATCH 精确查找。
title(config, "A1:M1", "保费分期生产产品配置快照");
config.getRange("A2:M2").merge();
config.getRange("A2").values = [["数据日期：2026-08-16；来源：db-prod / readonly / scf_self_order.amp_product + product_detail；不含客户标识。"]];
config.getRange("A2:M2").format = { fill: paleYellow, font: { color: navy }, wrapText: true };
config.getRange("A4:M4").values = [[
  "匹配键", "产品代码", "产品名称", "险种代码", "期数", "渠道服务费率", "运营服务费率",
  "首付比例", "技术服务费率", "还款策略", "还款日规则", "近90天试算订单", "最近试算时间"
]];
header(config.getRange("A4:M4"));
const productRows = [
  ["PRD-JBR-A001|1|3", "PRD-JBR-A001", "聚保融保费分期产品", 1, 3, 0, 0.02, 0.10, 0.005, "ZY_Customer", "1-10→当月24；11-20→次月04；21-31→次月14", 260, "2026-08-16 17:55:04"],
  ["PRD-JBR-A001|1|6", "PRD-JBR-A001", "聚保融保费分期产品", 1, 6, 0, 0.03, 0.10, 0.005, "ZY_Customer", "1-10→当月24；11-20→次月04；21-31→次月14", 260, "2026-08-16 17:55:04"],
  ["PRD-JBR-A001|1|9", "PRD-JBR-A001", "聚保融保费分期产品", 1, 9, 0, 0.03, 0.10, 0.005, "ZY_Customer", "1-10→当月24；11-20→次月04；21-31→次月14", 260, "2026-08-16 17:55:04"],
  ["PRD-JBR-A001|2|3", "PRD-JBR-A001", "聚保融保费分期产品", 2, 3, 0, 0.00, 0.10, 0.005, "ZY_Customer", "1-10→当月24；11-20→次月04；21-31→次月14", 260, "2026-08-16 17:55:04"],
  ["PRD-ZY-A001|1|3", "PRD-ZY-A001", "山西鸿瑞坤", 1, 3, 0, 0.02, 0.10, 0.005, "ZY_Customer", "1-10→当月24；11-20→次月04；21-31→次月14", 118, "2026-08-15 16:42:03"],
  ["PRD-ZY-A001|1|6", "PRD-ZY-A001", "山西鸿瑞坤", 1, 6, 0, 0.03, 0.10, 0.005, "ZY_Customer", "1-10→当月24；11-20→次月04；21-31→次月14", 118, "2026-08-15 16:42:03"],
  ["PRD-ZY-A001|1|9", "PRD-ZY-A001", "山西鸿瑞坤", 1, 9, 0, 0.036, 0.10, 0.005, "ZY_Customer", "1-10→当月24；11-20→次月04；21-31→次月14", 118, "2026-08-15 16:42:03"],
  ["PRD-ZY-A001|2|3", "PRD-ZY-A001", "山西鸿瑞坤", 2, 3, 0, 0.03, 0.10, 0.005, "ZY_Customer", "1-10→当月24；11-20→次月04；21-31→次月14", 118, "2026-08-15 16:42:03"],
];
config.getRange("A5:M12").values = productRows;
bodyGrid(config.getRange("A5:M12"));
config.getRange("F5:I12").format.numberFormat = pctFmt;
config.getRange("L5:L12").format.numberFormat = "#,##0";
config.getRange("M5:M12").format.numberFormat = "yyyy-mm-dd hh:mm:ss";
config.getRange("A5:M12").format.font = { color: black };
config.freezePanes.freezeRows(4);
setWidths(config, { A: 24, B: 18, C: 22, D: 11, E: 9, F: 15, G: 15, H: 11, I: 14, J: 17, K: 42, L: 15, M: 21 });

// 参数输入。
title(params, "A1:F1", "保费分期试算参数");
params.getRange("A2:F2").merge();
params.getRange("A2").values = [["蓝字为可编辑输入；绿字为跨表链接；黑字为公式。默认值使用一组自营渠道生产脱敏样本。"]];
params.getRange("A2:F2").format = { fill: paleYellow, font: { color: navy }, wrapText: true };
section(params, "A4:F4", "场景选择");
params.getRange("A5:C10").values = [
  ["参数", "值", "说明"],
  ["产品代码", "PRD-ZY-A001", "生产启用产品"],
  ["融资险种代码", 1, "1=仅商业险；2=全部险种"],
  ["商业险期数", 9, "生产当前配置支持3/6/9期"],
  ["非商业险融资期数", 0, "险种代码=2时通常填3"],
  ["试算日期", new Date(2026, 7, 15), "用于首期还款日规则"],
];
header(params.getRange("A5:C5"));
bodyGrid(params.getRange("A6:C10"));
params.getRange("B6:B10").format = { font: { color: inputBlue }, fill: paleYellow };
params.getRange("B10").format.numberFormat = dateFmt;
params.getRange("B6").dataValidation = { rule: { type: "list", values: ["PRD-ZY-A001", "PRD-JBR-A001"] } };
params.getRange("B7").dataValidation = { rule: { type: "list", values: [1, 2] } };
params.getRange("B8").dataValidation = { rule: { type: "list", values: [3, 6, 9] } };
params.getRange("B9").dataValidation = { rule: { type: "list", values: [0, 3] } };

section(params, "A12:F12", "由生产配置自动带入");
params.getRange("A13:C20").values = [
  ["配置项", "值", "口径"],
  ["还款策略", null, "产品 repaymentPlanCalculation"],
  ["首付比例", null, "产品 customerDownPaymentRatio"],
  ["商业险渠道服务费率", null, "商业险+商业险期数"],
  ["商业险运营服务费率", null, "商业险+商业险期数"],
  ["非商业险渠道服务费率", null, "非商业险+非商业险期数"],
  ["非商业险运营服务费率", null, "非商业险+非商业险期数"],
  ["技术服务费率", null, "产品 plateformFeeRate"],
];
header(params.getRange("A13:C13"));
bodyGrid(params.getRange("A14:C20"));
params.getRange("B14").formulas = [["=IF(COUNTIFS('产品配置'!$B$5:$B$12,$B$6,'产品配置'!$D$5:$D$12,1,'产品配置'!$E$5:$E$12,$B$8)>0,\"ZY_Customer\",\"未配置\")"]];
params.getRange("B15").formulas = [["=SUMIFS('产品配置'!$H$5:$H$12,'产品配置'!$B$5:$B$12,$B$6,'产品配置'!$D$5:$D$12,1,'产品配置'!$E$5:$E$12,$B$8)"]];
params.getRange("B16").formulas = [["=SUMIFS('产品配置'!$F$5:$F$12,'产品配置'!$B$5:$B$12,$B$6,'产品配置'!$D$5:$D$12,1,'产品配置'!$E$5:$E$12,$B$8)"]];
params.getRange("B17").formulas = [["=SUMIFS('产品配置'!$G$5:$G$12,'产品配置'!$B$5:$B$12,$B$6,'产品配置'!$D$5:$D$12,1,'产品配置'!$E$5:$E$12,$B$8)"]];
params.getRange("B18").formulas = [["=IF($B$7=2,SUMIFS('产品配置'!$F$5:$F$12,'产品配置'!$B$5:$B$12,$B$6,'产品配置'!$D$5:$D$12,2,'产品配置'!$E$5:$E$12,$B$9),0)"]];
params.getRange("B19").formulas = [["=IF($B$7=2,SUMIFS('产品配置'!$G$5:$G$12,'产品配置'!$B$5:$B$12,$B$6,'产品配置'!$D$5:$D$12,2,'产品配置'!$E$5:$E$12,$B$9),0)"]];
params.getRange("B20").formulas = [["=SUMIFS('产品配置'!$I$5:$I$12,'产品配置'!$B$5:$B$12,$B$6,'产品配置'!$D$5:$D$12,1,'产品配置'!$E$5:$E$12,$B$8)"]];
params.getRange("B14:B20").format.font = { color: linkGreen };
params.getRange("B15:B20").format.numberFormat = pctFmt;

section(params, "A22:F22", "还款日派生");
params.getRange("A23:C25").values = [
  ["派生项", "值", "规则"],
  ["还款日锚点", null, "后续每期在锚点上加期数月份"],
  ["首期还款日", null, "EDATE(锚点,1)"],
];
header(params.getRange("A23:C23"));
bodyGrid(params.getRange("A24:C25"));
params.getRange("B24").formulas = [["=IF(DAY($B$10)<=10,DATE(YEAR($B$10),MONTH($B$10)-1,24),IF(DAY($B$10)<=20,DATE(YEAR($B$10),MONTH($B$10),4),DATE(YEAR($B$10),MONTH($B$10),14)))"]];
params.getRange("B25").formulas = [["=EDATE($B$24,1)"]];
params.getRange("B24:B25").format.numberFormat = dateFmt;
params.freezePanes.freezeRows(4);
setWidths(params, { A: 25, B: 20, C: 48, D: 3, E: 3, F: 3 });

// 车辆维度输入和金额计算。
title(vehicles, "A1:O1", "车辆输入与订单金额拆分");
vehicles.getRange("A2:O2").merge();
vehicles.getRange("A2").values = [["最多录入10辆车。B:E为金额输入；其余列按 SelfOperatedOrderService.calculateAmount 逐车计算并汇总。"]];
vehicles.getRange("A2:O2").format = { fill: paleYellow, font: { color: navy }, wrapText: true };
vehicles.getRange("A4:O4").values = [[
  "车辆序号", "商业险", "交强险", "车船税", "附加险", "非商业险合计", "商业险首付", "非商业险首付",
  "商业险渠道费", "非商业险渠道费", "商业险运营费", "非商业险运营费", "商业险垫资", "非商业险垫资", "首笔其他费用"
]];
header(vehicles.getRange("A4:O4"));
const vehicleValues = [];
for (let i = 1; i <= 10; i++) {
  vehicleValues.push([`V${String(i).padStart(2, "0")}`, i === 1 ? 17921.45 : 0, 0, 0, 0]);
}
vehicles.getRange("A5:E14").values = vehicleValues;
vehicles.getRange("A5:E14").format = { font: { color: inputBlue }, fill: paleYellow };
bodyGrid(vehicles.getRange("A5:O14"));
for (let row = 5; row <= 14; row++) {
  vehicles.getRange(`F${row}:O${row}`).formulas = [[
    `=ROUND(SUM(C${row}:E${row}),2)`,
    `=ROUND(B${row}*'参数输入'!$B$15,2)`,
    `=IF('参数输入'!$B$7=2,ROUND(F${row}*'参数输入'!$B$15,2),0)`,
    `=ROUND(B${row}*'参数输入'!$B$16,2)`,
    `=IF('参数输入'!$B$7=2,ROUND(F${row}*'参数输入'!$B$18,2),0)`,
    `=ROUND(B${row}*'参数输入'!$B$17,2)`,
    `=IF('参数输入'!$B$7=2,ROUND(F${row}*'参数输入'!$B$19,2),0)`,
    `=ROUND(B${row}-G${row},2)`,
    `=IF('参数输入'!$B$7=2,ROUND(F${row}-H${row},2),0)`,
    `=IF('参数输入'!$B$7=1,ROUND(F${row},2),0)`,
  ]];
}
vehicles.getRange("F5:O14").format.font = { color: black };
vehicles.getRange("B5:O14").format.numberFormat = moneyFmt;
vehicles.getRange("A16:O16").values = [["合计", null, null, null, null, null, null, null, null, null, null, null, null, null, null]];
for (let col = 2; col <= 15; col++) {
  const letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  const c = letters[col - 1];
  vehicles.getRange(`${c}16`).formulas = [[`=ROUND(SUM(${c}5:${c}14),2)`]];
}
vehicles.getRange("A16:O16").format = { fill: paleBlue, font: { bold: true }, borders: { preset: "doubleBottom", style: "medium", color: navy } };
vehicles.getRange("B16:O16").format.numberFormat = moneyFmt;
vehicles.freezePanes.freezeRows(4);
vehicles.freezePanes.freezeColumns(1);
setWidths(vehicles, { A: 12, B: 13, C: 13, D: 12, E: 12, F: 15, G: 14, H: 15, I: 15, J: 16, K: 15, L: 16, M: 14, N: 15, O: 15 });

// 逐车逐期计算，保留代码中的“按期初占用本金权重分摊费用”逻辑。
title(detail, "A1:R1", "逐车逐期试算明细");
detail.getRange("A2:R2").values = [[
  "车辆", "期次", "启用", "期初商业险本金", "期初非商业险本金", "期初占用本金", "商业险本金", "非商业险本金",
  "本期本金", "分摊权重", "运营服务费总额", "技术服务费总额", "运营服务费分摊", "技术服务费分摊",
  "其他分期费用", "客户本期应还", "开始日期", "还款/结束日期"
]];
header(detail.getRange("A2:R2"));
const detailRows = [];
for (let v = 1; v <= 10; v++) {
  for (let p = 1; p <= 12; p++) detailRows.push([`V${String(v).padStart(2, "0")}`, p]);
}
detail.getRange("A3:B122").values = detailRows;
for (let row = 3; row <= 122; row++) {
  const v = Math.floor((row - 3) / 12);
  const inputRow = 5 + v;
  detail.getRange(`C${row}:N${row}`).formulas = [[
    `=IF(AND(SUM('车辆输入'!$B$${inputRow}:$F$${inputRow})>0,B${row}<='参数输入'!$B$8),1,0)`,
    `=IF(C${row}=1,ROUND(MAX('车辆输入'!$M$${inputRow}-ROUND('车辆输入'!$M$${inputRow}/'参数输入'!$B$8,2)*(B${row}-1),0),2),0)`,
    `=IF(AND(C${row}=1,B${row}<='参数输入'!$B$9),ROUND(MAX('车辆输入'!$N$${inputRow}-ROUND('车辆输入'!$N$${inputRow}/MAX('参数输入'!$B$9,1),2)*(B${row}-1),0),2),0)`,
    `=ROUND(D${row}+E${row},2)`,
    `=IF(C${row}=0,0,ROUND(IF(B${row}='参数输入'!$B$8,D${row},MIN(D${row},ROUND('车辆输入'!$M$${inputRow}/'参数输入'!$B$8,2))),2))`,
    `=IF(OR(C${row}=0,'参数输入'!$B$9=0,B${row}>'参数输入'!$B$9),0,ROUND(IF(B${row}='参数输入'!$B$9,E${row},MIN(E${row},ROUND('车辆输入'!$N$${inputRow}/'参数输入'!$B$9,2))),2))`,
    `=ROUND(G${row}+H${row},2)`,
    `=IF(C${row}=1,F${row}/SUMIFS($F$3:$F$122,$A$3:$A$122,A${row},$C$3:$C$122,1),0)`,
    `=IF(C${row}=1,ROUND('车辆输入'!$K$${inputRow}+'车辆输入'!$L$${inputRow},2),0)`,
    `=IF(C${row}=1,ROUND(('车辆输入'!$M$${inputRow}+'车辆输入'!$N$${inputRow})*'参数输入'!$B$20,2),0)`,
    `=IF(C${row}=0,0,ROUND(IF(B${row}='参数输入'!$B$8,K${row}-SUMIFS($M$3:$M$122,$A$3:$A$122,A${row},$B$3:$B$122,\"<\"&B${row}),K${row}*J${row}),2))`,
    `=IF(C${row}=0,0,ROUND(IF(B${row}='参数输入'!$B$8,L${row}-SUMIFS($N$3:$N$122,$A$3:$A$122,A${row},$B$3:$B$122,\"<\"&B${row}),L${row}*J${row}),2))`,
  ]];
  detail.getRange(`O${row}`).values = [[0]];
  detail.getRange(`P${row}:R${row}`).formulas = [[
    `=ROUND(I${row}+O${row},2)`,
    `=IF(C${row}=0,\"\",IF(B${row}=1,'参数输入'!$B$10,EDATE('参数输入'!$B$24,B${row}-1)+1))`,
    `=IF(C${row}=0,\"\",EDATE('参数输入'!$B$24,B${row}))`,
  ]];
}
bodyGrid(detail.getRange("A3:R122"));
detail.getRange("O3:O122").format = { fill: paleYellow, font: { color: inputBlue } };
detail.getRange("D3:I122").format.numberFormat = moneyFmt;
detail.getRange("J3:J122").format.numberFormat = "0.0000%";
detail.getRange("K3:P122").format.numberFormat = moneyFmt;
detail.getRange("Q3:R122").format.numberFormat = dateFmt;
detail.getRange("C3:C122").conditionalFormats.add("cellIs", { operator: "equal", formula: 0, format: { font: { color: "#A6A6A6" } } });
detail.freezePanes.freezeRows(2);
detail.freezePanes.freezeColumns(2);
setWidths(detail, { A: 10, B: 8, C: 8, D: 16, E: 17, F: 15, G: 14, H: 15, I: 13, J: 12, K: 16, L: 16, M: 16, N: 16, O: 15, P: 15, Q: 13, R: 16 });

// 汇总还款计划。
title(plan, "A1:L1", "订单还款计划（主账单口径）");
plan.getRange("A2:L2").merge();
plan.getRange("A2").values = [["客户本期应还 = 商业险本金 + 非商业险本金 + 其他分期费用。当前代码不把运营/技术服务费分摊字段重复加入 repay_total_amount。"]];
plan.getRange("A2:L2").format = { fill: paleYellow, font: { color: navy }, wrapText: true };
plan.getRange("A4:L4").values = [[
  "期次", "开始日期", "还款/结束日期", "期初占用本金", "商业险本金", "非商业险本金", "本期本金",
  "运营服务费分摊", "技术服务费分摊", "其他分期费用", "客户本期应还", "期末占用本金"
]];
header(plan.getRange("A4:L4"));
for (let i = 1; i <= 12; i++) {
  const row = 4 + i;
  plan.getRange(`A${row}`).values = [[i]];
  plan.getRange(`B${row}:L${row}`).formulas = [[
    `=IF(A${row}<='参数输入'!$B$8,IF(A${row}=1,'参数输入'!$B$10,EDATE('参数输入'!$B$24,A${row}-1)+1),\"\")`,
    `=IF(A${row}<='参数输入'!$B$8,EDATE('参数输入'!$B$24,A${row}),\"\")`,
    `=ROUND(SUMIFS('逐车试算'!$F$3:$F$122,'逐车试算'!$B$3:$B$122,A${row},'逐车试算'!$C$3:$C$122,1),2)`,
    `=ROUND(SUMIFS('逐车试算'!$G$3:$G$122,'逐车试算'!$B$3:$B$122,A${row},'逐车试算'!$C$3:$C$122,1),2)`,
    `=ROUND(SUMIFS('逐车试算'!$H$3:$H$122,'逐车试算'!$B$3:$B$122,A${row},'逐车试算'!$C$3:$C$122,1),2)`,
    `=ROUND(E${row}+F${row},2)`,
    `=ROUND(SUMIFS('逐车试算'!$M$3:$M$122,'逐车试算'!$B$3:$B$122,A${row},'逐车试算'!$C$3:$C$122,1),2)`,
    `=ROUND(SUMIFS('逐车试算'!$N$3:$N$122,'逐车试算'!$B$3:$B$122,A${row},'逐车试算'!$C$3:$C$122,1),2)`,
    `=ROUND(SUMIFS('逐车试算'!$O$3:$O$122,'逐车试算'!$B$3:$B$122,A${row},'逐车试算'!$C$3:$C$122,1),2)`,
    `=ROUND(G${row}+J${row},2)`,
    `=ROUND(D${row}-G${row},2)`,
  ]];
}
bodyGrid(plan.getRange("A5:L16"));
plan.getRange("B5:C16").format.numberFormat = dateFmt;
plan.getRange("D5:L16").format.numberFormat = moneyFmt;
plan.getRange("A18:L18").values = [["合计", null, null, null, null, null, null, null, null, null, null, null]];
for (const col of ["E", "F", "G", "H", "I", "J", "K"]) plan.getRange(`${col}18`).formulas = [[`=ROUND(SUM(${col}5:${col}16),2)`]];
plan.getRange("A18:L18").format = { fill: paleBlue, font: { bold: true }, borders: { preset: "doubleBottom", style: "medium", color: navy } };
plan.getRange("D18:L18").format.numberFormat = moneyFmt;
plan.freezePanes.freezeRows(4);
plan.freezePanes.freezeColumns(1);
setWidths(plan, { A: 8, B: 13, C: 16, D: 16, E: 14, F: 15, G: 13, H: 16, I: 16, J: 15, K: 15, L: 16 });

// 生产样本回代核对。
title(sample, "A1:P1", "生产脱敏样本回代核对");
sample.getRange("A2:C2").merge();
sample.getRange("A2").values = [["样本：PRD-ZY-A001 / 自营渠道 / 商业险 / 9期 / 2026-08-15；不含申请号、客户、车辆标识。"]];
sample.getRange("D2:E2").merge();
sample.getRange("D2").values = [["模型状态"]];
sample.getRange("F2:H2").merge();
sample.getRange("F2").formulas = [["=IF(COUNTIF(E6:E13,\"差异\")+COUNTIF(P18:P29,\"差异\")=0,\"OK\",\"需复核\")"]];
sample.getRange("D2:E2").format = { fill: blue, font: { bold: true, color: "#FFFFFF" }, horizontalAlignment: "center" };
sample.getRange("F2:H2").format = { fill: paleGreen, font: { bold: true, color: "#006100", size: 14 }, horizontalAlignment: "center" };
section(sample, "A4:G4", "订单汇总核对");
sample.getRange("A5:G5").values = [["指标", "生产值", "模型值", "差异", "状态", "容差", "来源/说明"]];
header(sample.getRange("A5:G5"));
const summaryActuals = [
  ["保费合计", 17921.45, "='车辆输入'!$B$16+'车辆输入'!$F$16", "application_loan.premium_amount"],
  ["首付款", 1792.15, "='车辆输入'!$G$16+'车辆输入'!$H$16", "application_loan.initial_payment"],
  ["运营服务费", 645.17, "='车辆输入'!$K$16+'车辆输入'!$L$16", "application_loan.operation_service_fee"],
  ["商业险垫资", 16129.30, "='车辆输入'!$M$16", "application_loan.ci_advance_total_amount"],
  ["首笔款", 2437.32, "='车辆输入'!$G$16+'车辆输入'!$H$16+'车辆输入'!$I$16+'车辆输入'!$J$16+'车辆输入'!$K$16+'车辆输入'!$L$16+'车辆输入'!$O$16", "application_loan.first_payment"],
  ["各期本金合计", 16129.30, "='还款计划'!$G$18", "loan_trial_repayschedules"],
  ["运营服务费分摊合计", 645.17, "='还款计划'!$H$18", "loan_trial_repayschedules"],
  ["技术服务费分摊合计", 80.65, "='还款计划'!$I$18", "垫资额×0.5%"],
];
for (let i = 0; i < summaryActuals.length; i++) {
  const row = 6 + i;
  const [label, actual, formula, source] = summaryActuals[i];
  sample.getRange(`A${row}:B${row}`).values = [[label, actual]];
  sample.getRange(`C${row}`).formulas = [[formula]];
  sample.getRange(`D${row}`).formulas = [[`=C${row}-B${row}`]];
  sample.getRange(`E${row}`).formulas = [[`=IF(ABS(D${row})<=F${row},\"OK\",\"差异\")`]];
  sample.getRange(`F${row}:G${row}`).values = [[0.01, source]];
}
bodyGrid(sample.getRange("A6:G13"));
sample.getRange("B6:D13").format.numberFormat = moneyFmt;
sample.getRange("F6:F13").format.numberFormat = "0.00";
sample.getRange("C6:C13").format.font = { color: linkGreen };
sample.getRange("E6:E13").conditionalFormats.add("containsText", { text: "OK", format: { fill: paleGreen, font: { color: "#006100", bold: true } } });
sample.getRange("E6:E13").conditionalFormats.add("containsText", { text: "差异", format: { fill: paleRed, font: { color: "#9C0006", bold: true } } });

section(sample, "A16:P16", "逐期核对");
sample.getRange("A17:P17").values = [[
  "期次", "生产本金", "模型本金", "差异", "生产运营费", "模型运营费", "差异", "生产技术费", "模型技术费", "差异",
  "生产应还", "模型应还", "差异", "生产还款日", "模型还款日", "状态"
]];
header(sample.getRange("A17:P17"));
const actualSchedule = [
  [1,1792.14,129.03,16.13,1792.14,46269],
  [2,1792.14,114.70,14.34,1792.14,46299],
  [3,1792.14,100.36,12.55,1792.14,46330],
  [4,1792.14,86.02,10.75,1792.14,46360],
  [5,1792.14,71.69,8.96,1792.14,46391],
  [6,1792.14,57.35,7.17,1792.14,46422],
  [7,1792.14,43.01,5.38,1792.14,46450],
  [8,1792.14,28.67,3.58,1792.14,46481],
  [9,1792.18,14.34,1.79,1792.18,46511],
];
for (let i = 0; i < 12; i++) {
  const row = 18 + i;
  const planRow = 5 + i;
  if (i < actualSchedule.length) {
    const [period, principal, opFee, techFee, repay, due] = actualSchedule[i];
    sample.getRange(`A${row}:B${row}`).values = [[period, principal]];
    sample.getRange(`E${row}`).values = [[opFee]];
    sample.getRange(`H${row}`).values = [[techFee]];
    sample.getRange(`K${row}`).values = [[repay]];
    sample.getRange(`N${row}`).values = [[due]];
  } else {
    sample.getRange(`A${row}`).values = [[i + 1]];
    sample.getRange(`B${row}`).values = [[0]];
    sample.getRange(`E${row}`).values = [[0]];
    sample.getRange(`H${row}`).values = [[0]];
    sample.getRange(`K${row}`).values = [[0]];
    sample.getRange(`N${row}`).values = [[null]];
  }
  sample.getRange(`C${row}:D${row}`).formulas = [[`='还款计划'!$G$${planRow}`, `=C${row}-B${row}`]];
  sample.getRange(`F${row}:G${row}`).formulas = [[`='还款计划'!$H$${planRow}`, `=F${row}-E${row}`]];
  sample.getRange(`I${row}:J${row}`).formulas = [[`='还款计划'!$I$${planRow}`, `=I${row}-H${row}`]];
  sample.getRange(`L${row}:M${row}`).formulas = [[`='还款计划'!$K$${planRow}`, `=L${row}-K${row}`]];
  sample.getRange(`O${row}`).formulas = [[`=IF(A${row}<='参数输入'!$B$8,'还款计划'!$C$${planRow},\"\")`]];
  sample.getRange(`P${row}`).formulas = [[`=IF(AND(ABS(D${row})<=0.01,ABS(G${row})<=0.01,ABS(J${row})<=0.01,ABS(M${row})<=0.01,OR(AND(N${row}=\"\",O${row}=\"\"),N${row}=O${row})),\"OK\",\"差异\")`]];
}
bodyGrid(sample.getRange("A18:P29"));
sample.getRange("B18:M29").format.numberFormat = moneyFmt;
sample.getRange("N18:O29").format.numberFormat = dateFmt;
sample.getRange("C18:C29").format.font = { color: linkGreen };
sample.getRange("F18:F29").format.font = { color: linkGreen };
sample.getRange("I18:I29").format.font = { color: linkGreen };
sample.getRange("L18:L29").format.font = { color: linkGreen };
sample.getRange("O18:O29").format.font = { color: linkGreen };
sample.getRange("P18:P29").conditionalFormats.add("containsText", { text: "OK", format: { fill: paleGreen, font: { color: "#006100", bold: true } } });
sample.getRange("P18:P29").conditionalFormats.add("containsText", { text: "差异", format: { fill: paleRed, font: { color: "#9C0006", bold: true } } });
sample.freezePanes.freezeRows(17);
setWidths(sample, { A: 17, B: 14, C: 14, D: 12, E: 14, F: 14, G: 12, H: 14, I: 14, J: 12, K: 14, L: 14, M: 12, N: 14, O: 14, P: 11 });

// 摘要页。
title(cover, "A1:H1", "保费分期生产试算表（代码与数据反推）");
cover.getRange("A2:H2").merge();
cover.getRange("A2").values = [["适用范围：BL000001 保费分期 / scf / 生产 / master。默认回代自营渠道生产脱敏样本；用户只需调整参数输入与车辆金额。"]];
cover.getRange("A2:H2").format = { fill: paleYellow, font: { color: navy }, wrapText: true };
section(cover, "A4:H4", "模型状态与使用说明");
cover.getRange("A5:B8").values = [
  ["模型状态", null],
  ["基准产品", null],
  ["融资口径", null],
  ["使用步骤", "1. 参数输入选择产品/险种/期数；2. 车辆输入录入保费；3. 查看还款计划；4. 生产样本核对必须为OK。"],
];
cover.getRange("B5").formulas = [["='生产样本核对'!$F$2"]];
cover.getRange("B6").formulas = [["='参数输入'!$B$6"]];
cover.getRange("B7").formulas = [["=IF('参数输入'!$B$7=1,\"仅商业险\",\"全部险种\")"]];
cover.getRange("A5:A8").format = { fill: lightBlue, font: { bold: true, color: navy } };
cover.getRange("B5:B8").format = { borders: { preset: "outside", style: "thin", color: border }, wrapText: true };
cover.getRange("B5:B7").format.font = { color: linkGreen, bold: true };
cover.getRange("B5").format = { fill: paleGreen, font: { bold: true, color: "#006100", size: 14 }, horizontalAlignment: "center" };

section(cover, "A10:H10", "核心金额");
cover.getRange("A11:D17").values = [
  ["指标", "金额", "口径", "检查"],
  ["总保费", null, "商业险+非商业险", null],
  ["首付款", null, "商业险首付+非商业险首付", null],
  ["渠道服务费", null, "按险种金额×渠道费率", null],
  ["运营服务费", null, "按险种金额×运营费率", null],
  ["首笔款", null, "首付款+渠道费+运营费+不融资险种", null],
  ["运营商垫资", null, "商业险垫资+非商业险垫资", null],
];
header(cover.getRange("A11:D11"));
const coverFormulas = [
  "='车辆输入'!$B$16+'车辆输入'!$F$16",
  "='车辆输入'!$G$16+'车辆输入'!$H$16",
  "='车辆输入'!$I$16+'车辆输入'!$J$16",
  "='车辆输入'!$K$16+'车辆输入'!$L$16",
  "='车辆输入'!$G$16+'车辆输入'!$H$16+'车辆输入'!$I$16+'车辆输入'!$J$16+'车辆输入'!$K$16+'车辆输入'!$L$16+'车辆输入'!$O$16",
  "='车辆输入'!$M$16+'车辆输入'!$N$16",
];
for (let i = 0; i < coverFormulas.length; i++) {
  const row = 12 + i;
  cover.getRange(`B${row}`).formulas = [[coverFormulas[i]]];
  cover.getRange(`D${row}`).formulas = [[i === 5 ? `=IF(ABS(B${row}-'还款计划'!$G$18)<=0.01,\"OK\",\"差异\")` : "=\"\""]];
}
bodyGrid(cover.getRange("A12:D17"));
cover.getRange("B12:B17").format = { numberFormat: moneyFmt, font: { color: linkGreen, bold: true } };
cover.getRange("D12:D17").conditionalFormats.add("containsText", { text: "OK", format: { fill: paleGreen, font: { color: "#006100", bold: true } } });

section(cover, "A19:H19", "还款与现金口径");
cover.getRange("A20:D24").values = [
  ["指标", "金额", "说明", "检查"],
  ["各期客户应还合计", null, "本金+其他分期费用；不重复加服务费分摊", null],
  ["运营服务费分摊合计", null, "账务分摊字段，应与订单运营服务费一致", null],
  ["技术服务费分摊合计", null, "垫资额×技术服务费率", null],
  ["客户总现金支出", null, "首笔款+各期客户应还", null],
];
header(cover.getRange("A20:D20"));
cover.getRange("B21").formulas = [["='还款计划'!$K$18"]];
cover.getRange("B22").formulas = [["='还款计划'!$H$18"]];
cover.getRange("B23").formulas = [["='还款计划'!$I$18"]];
cover.getRange("B24").formulas = [["=B16+B21"]];
cover.getRange("D22").formulas = [["=IF(ABS(B22-B15)<=0.01,\"OK\",\"差异\")"]];
cover.getRange("D23").formulas = [["=IF(ABS(B23-B17*'参数输入'!$B$20)<=0.01,\"OK\",\"差异\")"]];
bodyGrid(cover.getRange("A21:D24"));
cover.getRange("B21:B24").format = { numberFormat: moneyFmt, font: { color: linkGreen, bold: true } };
cover.getRange("D21:D24").conditionalFormats.add("containsText", { text: "OK", format: { fill: paleGreen, font: { color: "#006100", bold: true } } });
cover.getRange("D21:D24").conditionalFormats.add("containsText", { text: "差异", format: { fill: paleRed, font: { color: "#9C0006", bold: true } } });

cover.getRange("F5:H11").values = [
  ["颜色图例", null, null],
  ["蓝字/黄底", "可编辑输入", null],
  ["绿字", "跨工作表链接", null],
  ["黑字", "公式或固定来源值", null],
  ["绿色状态", "核对通过", null],
  ["红色状态", "需复核", null],
  ["重要限制", "多车时系统逐车四舍五入；本表按同一口径逐车计算。资方保理拆分不在本次客户/运营商试算范围。", null],
];
cover.getRange("F5:H5").merge();
cover.getRange("F5").format = { fill: blue, font: { bold: true, color: "#FFFFFF" } };
cover.getRange("F6").format = { fill: paleYellow, font: { color: inputBlue } };
cover.getRange("F7").format.font = { color: linkGreen };
cover.getRange("F9").format.fill = paleGreen;
cover.getRange("F10").format.fill = paleRed;
cover.getRange("F11:H11").merge();
cover.getRange("F11:H11").format = { fill: gray, wrapText: true, font: { color: navy } };
cover.freezePanes.freezeRows(4);
setWidths(cover, { A: 22, B: 18, C: 48, D: 12, E: 3, F: 18, G: 24, H: 18 });

// 依据与口径。
title(audit, "A1:F1", "依据、公式映射与限制");
audit.getRange("A3:F3").values = [["类别", "事项", "代码/数据来源", "反推公式或结论", "核验状态", "备注"]];
header(audit.getRange("A3:F3"));
const auditRows = [
  ["环境", "业务上下文", "WorkHub: BL000001 / scf / prod / master", "生产按 master；数据库只读", "已确认", "2026-08-16"],
  ["代码", "金额拆分", "scf-order/.../SelfOperatedOrderService.java:250-380", "首付、渠道费、运营费、垫资、首笔款逐车计算", "已映射", "ROUND_HALF_UP，2位"],
  ["代码", "还款策略", "scf-order/.../ZYCustomerCalculationStrategy.java:92-490", "等额本金；末期吃差；费用按期初占用本金权重分摊", "已映射", "当前生产产品均配置 ZY_Customer"],
  ["代码", "试算入口", "scf-order/.../ApplicationProprietaryController.java:500-552", "POST /api/application/v2/installment/plan/calculate", "已确认", "自营渠道"],
  ["代码", "正式出账", "scf-saps/.../CustomerBillProviderController.java:44-57", "POST /api/generate/customer/bill/V2", "已确认", "运营商+客户账单"],
  ["数据", "产品配置", "db-prod.readonly / scf_self_order.amp_product + product_detail", "费率、首付比例、策略、还款日规则", "已读取", "未包含客户数据"],
  ["数据", "生产样本", "db-prod.readonly / application_loan + application_vehicle + loan_trial_repayschedules", "PRD-ZY-A001，自营，商业险，9期，脱敏", "回代OK", "不保留申请号/车架号"],
  ["口径", "客户本期应还", "ZYCustomerCalculationStrategy.java:386,443", "商业险本金+非商业险本金+其他分期费用", "已映射", "不重复加运营/技术服务费分摊"],
  ["口径", "首笔款", "SelfOperatedOrderService.java:357-363", "首付款+渠道费+运营费+不融资险种费用", "已映射", "全部险种时不融资险种费用为0"],
  ["限制", "其他分期费用", "vinOtherFeeMap", "逐车试算O列按车辆/期次手工录入", "显式输入", "自营默认0"],
  ["限制", "资方保理", "factoring_principal / factoring_fee", "未纳入本表", "不在范围", "需要时可另建资方试算页"],
  ["限制", "部署提交", "生产数据库有2026-08-16新增记录", "运行口径与 master 样本回代一致", "数据佐证", "未读取服务器部署commit"],
];
audit.getRange(`A4:F${3 + auditRows.length}`).values = auditRows;
bodyGrid(audit.getRange(`A4:F${3 + auditRows.length}`));
audit.getRange(`C4:D${3 + auditRows.length}`).format.wrapText = true;
audit.getRange(`E4:E${3 + auditRows.length}`).conditionalFormats.add("containsText", { text: "确认", format: { fill: paleGreen, font: { color: "#006100" } } });
audit.getRange(`E4:E${3 + auditRows.length}`).conditionalFormats.add("containsText", { text: "OK", format: { fill: paleGreen, font: { color: "#006100" } } });
audit.freezePanes.freezeRows(3);
setWidths(audit, { A: 12, B: 20, C: 58, D: 58, E: 14, F: 32 });

// 注释：将关键来源贴到输入和样本核对单元格。
workbook.comments.setSelf({ displayName: "Codex" });
workbook.comments.addThread({ cell: params.getRange("B6") }, "来源：生产只读 scf_self_order.amp_product；当前启用并有近90天试算数据的产品为 PRD-ZY-A001 与 PRD-JBR-A001。采集日期：2026-08-16。");
workbook.comments.addThread({ cell: params.getRange("B15") }, "公式来源：SelfOperatedOrderService.preAuditPass/calculateAmount；产品首付比例固化到 ApplicationLoan，再逐车计算，金额按 HALF_UP 保留2位。");
workbook.comments.addThread({ cell: sample.getRange("B6") }, "来源：db-prod readonly，scf_self_order.application_loan + application_vehicle；已去除申请号、客户、车架号等标识。");
workbook.comments.addThread({ cell: sample.getRange("B18") }, "来源：db-prod readonly，scf_self_order.loan_trial_repayschedules 主计划(category=1)；生产值仅用于回代核对。");

// 统一字体和行高微调。
for (const sheet of [cover, params, vehicles, detail, plan, sample, config, audit]) {
  const used = sheet.getUsedRange();
  used.format.font.name = "Aptos";
  used.format.font.size = 10;
  used.format.verticalAlignment = "center";
}
cover.getRange("A1:H1").format.font.size = 16;
params.getRange("A1:F1").format.font.size = 16;
vehicles.getRange("A1:O1").format.font.size = 16;
detail.getRange("A1:R1").format.font.size = 16;
plan.getRange("A1:L1").format.font.size = 16;
sample.getRange("A1:P1").format.font.size = 16;
config.getRange("A1:M1").format.font.size = 16;
audit.getRange("A1:F1").format.font.size = 16;

// 关键区检查和公式错误扫描。
const keyCheck = await workbook.inspect({
  kind: "table",
  range: "生产样本核对!A1:P29",
  include: "values,formulas",
  tableMaxRows: 30,
  tableMaxCols: 16,
  maxChars: 9000,
});
console.log("KEY_CHECK\n" + keyCheck.ndjson);
const formulaErrors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!",
  options: { useRegex: true, maxResults: 300 },
  summary: "final formula error scan",
  maxChars: 5000,
});
console.log("FORMULA_ERRORS\n" + formulaErrors.ndjson);

for (const sheetName of ["说明与摘要", "参数输入", "车辆输入", "逐车试算", "还款计划", "生产样本核对", "产品配置", "依据与口径"]) {
  const preview = await workbook.render({ sheetName, autoCrop: "all", scale: 1, format: "png" });
  await fs.writeFile(`${previewDir}/${sheetName}.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
const outputPath = `${outputDir}/保费分期生产试算表_代码与数据反推.xlsx`;
await output.save(outputPath);
console.log(`OUTPUT=${outputPath}`);
