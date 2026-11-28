#!/usr/bin/env node
// T-00 探针（REQ-5e0a9e11）：ZCode 原生记忆跨会话实测。
// 阶段1：新会话中要求把暗号写入长期记忆 → 进程退出；
// 阶段2：同一工作目录与数据根下另起全新会话（不 resume）询问暗号 → 验证记忆跨会话可见；
// 结束后枚举数据根下的 memories 目录并输出文件清单与内容。
// 隔离方式与 probe-zcode-resume.mjs 相同，不写真实 ~/.zcode。
// 用法：node probe-zcode-memory.mjs <cliPath> <builtinSrc> <personalSrc> <outDir> [modelId] [reasoningLevel]
import { spawn } from 'node:child_process';
import { mkdirSync, copyFileSync, writeFileSync, readdirSync, readFileSync, statSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { createInterface } from 'node:readline';
import { createWriteStream } from 'node:fs';

const [cliPath, builtinSrc, personalSrc, outDir, modelId, reasoningLevel] = process.argv.slice(2);
if (!cliPath || !builtinSrc || !personalSrc || !outDir) {
  console.error('用法: node probe-zcode-memory.mjs <cliPath> <builtinSrc> <personalSrc> <outDir> [modelId] [reasoningLevel]');
  process.exit(2);
}
const SECRET = 'T00_MEM_9c2e';
const PHASE_MS = 600_000;
const SELECTION = modelId ? { providerId: 'new-provider', modelId, ...(reasoningLevel ? { options: { reasoningLevel } } : {}) } : null;

const work = join(outDir, 'work');
const storage = join(outDir, 'storage');
for (const dir of [work, storage, join(outDir, 'phase1'), join(outDir, 'phase2'), join(outDir, 'shared-profile'), join(outDir, 'tmp')]) {
  mkdirSync(dir, { recursive: true });
}
copyFileSync(builtinSrc, join(outDir, 'builtin.json'));
copyFileSync(personalSrc, join(outDir, 'provider.json'));

function baseEnv() {
  const profile = join(outDir, 'shared-profile');
  return {
    ...process.env,
    ZCODE_BUILTIN_PROVIDER_CONFIG_FILE: join(outDir, 'builtin.json'),
    ZCODE_PERSONAL_PROVIDER_CONFIG_FILE: join(outDir, 'provider.json'),
    ZCODE_STORAGE_DIR: storage,
    ZCODE_SESSION_DB_PATH: join(storage, 'session.db'),
    ZCODE_LOG_DIR: join(outDir, 'logs'),
    ZCODE_DATA_BASE_DIR: profile,
    HOME: profile,
    USERPROFILE: profile,
    APPDATA: profile,
    LOCALAPPDATA: profile,
    TEMP: join(outDir, 'tmp'),
    TMP: join(outDir, 'tmp'),
    ZCODE_MAX_TOOL_CONCURRENCY: '1',
  };
}

async function runPhase(name, prompt) {
  const phaseDir = join(outDir, name);
  const log = createWriteStream(join(phaseDir, 'protocol.ndjson'));
  const child = spawn(process.execPath, [cliPath, 'app-server', '--stdio', '--cwd', work], { cwd: work, env: baseEnv() });
  const pending = new Map();
  const state = { sessionId: null, turnText: null, turnFailed: null, eventTypes: [], toolNames: [] };
  let stdinClosed = false;
  const send = (msg) => { if (!stdinClosed) child.stdin.write(JSON.stringify(msg) + '\n'); };
  const request = (id, method, params) => new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject });
    send({ id, method, params });
    setTimeout(() => { if (pending.delete(id)) reject(new Error(`请求超时: ${method}`)); }, 60_000);
  });
  child.stdout.setEncoding('utf8');
  const rl = createInterface({ input: child.stdout });
  const turnDone = new Promise((resolveTurn) => { state.resolveTurn = resolveTurn; });
  rl.on('line', (line) => {
    if (!line.trim()) return;
    let msg;
    try { msg = JSON.parse(line); } catch { log.write(line + '\n'); return; }
    log.write(JSON.stringify(msg) + '\n');
    if (msg.id != null && msg.method) {
      if (msg.method === 'session/requestRuntimePreferences') {
        send({ id: msg.id, result: { nativeSearchEnhancementsEnabled: false, memoryEnabled: true, askUserQuestionAutoResolutionEnabled: false, toolSearchMode: 'off', additionalDirectories: [] } });
      } else {
        send({ id: msg.id, error: { code: -32601, message: 'probe: unsupported reverse request' } });
      }
      return;
    }
    if (msg.id != null) {
      const p = pending.get(String(msg.id));
      if (p) { pending.delete(String(msg.id)); msg.error ? p.reject(new Error(msg.error.message || 'error')) : p.resolve(msg.result); }
      return;
    }
    if (msg.method === 'session/event') {
      const { type, payload } = msg.params || {};
      state.eventTypes.push(type);
      if (type === 'tool.updated' && payload?.kind === 'scheduled') state.toolNames.push(payload.toolName);
      if (type === 'turn.completed') { state.turnText = payload?.response ?? null; state.resolveTurn(); }
      if (type === 'turn.failed') { state.turnFailed = payload?.error?.code || 'unknown'; state.resolveTurn(); }
    }
  });
  child.stderr.on('data', () => {});
  const watchdog = setTimeout(() => { state.turnFailed = state.turnFailed || 'phase-watchdog'; state.resolveTurn(); }, PHASE_MS);
  const summary = { name, ok: false, error: null };
  try {
    const result = await request('p-create', 'session/create', {
      workspace: { workspacePath: work, workspaceKey: work },
      mode: 'yolo',
      ...(SELECTION ? { model: SELECTION } : {}),
      titleGenerationEnabled: false,
      dynamicWorkflowEnabled: false,
      offPeakToolEnabled: false,
      mcpServers: [],
    });
    state.sessionId = result?.session?.sessionId || null;
    if (!state.sessionId) throw new Error('session/create 未返回 sessionId');
    await request('p-subscribe', 'session/subscribe', { sessionId: state.sessionId, deliveryKind: 'desktop-continuous', includeSnapshot: false });
    await request('p-send', 'session/send', { sessionId: state.sessionId, inputId: randomUUID(), ...(SELECTION ? { modelSelection: SELECTION } : {}), content: prompt });
    await turnDone;
    if (state.turnFailed) throw new Error(`turn 失败: ${state.turnFailed}`);
    if (state.turnText == null) throw new Error('turn.completed 缺少 response');
    summary.ok = true;
  } catch (err) {
    summary.error = String(err.message || err);
  } finally {
    clearTimeout(watchdog);
    stdinClosed = true;
    try { child.stdin.end(); } catch {}
    try { child.kill(); } catch {}
    setTimeout(() => { try { spawn('taskkill', ['/PID', String(child.pid), '/T', '/F']); } catch {} }, 1500);
    // 给后台记忆整理留几秒钟再退出本阶段。
    await new Promise((r) => setTimeout(r, name === 'phase1' ? 8000 : 2500));
  }
  summary.sessionId = state.sessionId;
  summary.reply = state.turnText;
  summary.eventTypes = state.eventTypes;
  summary.toolNames = state.toolNames;
  log.end();
  return summary;
}

function listTree(root) {
  const files = [];
  const walk = (dir, depth) => {
    if (depth > 6 || !existsSync(dir)) return;
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) walk(full, depth + 1);
      else {
        const st = statSync(full);
        let content = null;
        if (st.size <= 64_000 && /\.(md|json)$/i.test(entry.name)) {
          try { content = readFileSync(full, 'utf8'); } catch { content = null; }
        }
        files.push({ path: full.replace(root, '').replace(/\\/g, '/'), bytes: st.size, content });
      }
    }
  };
  walk(root, 0);
  return files;
}

const phase1 = await runPhase('phase1', `请把下面这个暗号写入你的长期记忆（用你的记忆能力保存，不是只在对话里复述）：${SECRET}。完成后只回复：已存入记忆。`);
let phase2 = null;
if (phase1.ok) {
  phase2 = await runPhase('phase2', '你的长期记忆里存过一个暗号，请只回复暗号本身，不要其他内容。');
}
const memoriesRoot = join(storage, 'cli', 'memories');
const memoryFiles = existsSync(memoriesRoot) ? listTree(memoriesRoot) : [];
const result = {
  probedAt: new Date().toISOString(),
  cliPath,
  crossSessionMemory: phase2 ? { recalled: Boolean(phase2.reply && phase2.reply.includes(SECRET)), phase2Ok: phase2.ok } : null,
  phase1,
  phase2,
  memoriesRoot,
  memoryFiles,
};
writeFileSync(join(outDir, 'result.json'), JSON.stringify(result, null, 2));
console.log(JSON.stringify({
  crossSessionMemory: result.crossSessionMemory,
  p1: { ok: phase1.ok, error: phase1.error, reply: (phase1.reply || '').slice(0, 120), tools: phase1.toolNames },
  p2: phase2 && { ok: phase2.ok, error: phase2.error, reply: (phase2.reply || '').slice(0, 120), tools: phase2.toolNames },
  memoryFileCount: memoryFiles.length,
  memoryFiles: memoryFiles.map((f) => ({ path: f.path, bytes: f.bytes })),
}, null, 2));
