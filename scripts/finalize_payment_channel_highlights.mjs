import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "/Users/aslight/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs";

const candidatePath = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/outputs/payment-channel-restore-highlights-20260805/支付渠道_恢复手工底色.xlsx";
const desktopPath = "/Users/aslight/Desktop/支付渠道.xlsx";

const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(candidatePath));
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(desktopPath);
await fs.rm(`${desktopPath}.inspect.ndjson`, { force: true });

console.log(JSON.stringify({ desktopPath, candidatePath }, null, 2));
