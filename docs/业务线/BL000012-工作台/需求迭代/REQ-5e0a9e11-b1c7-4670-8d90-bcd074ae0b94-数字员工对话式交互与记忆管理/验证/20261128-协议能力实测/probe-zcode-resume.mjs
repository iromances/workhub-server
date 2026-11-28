#!/usr/bin/env node
// T-00 探针（REQ-5e0a9e11）：ZCode app-server 跨进程会话续接实测。
// 阶段1：新进程 session/create + session/send（记住一个哨兵句）→ 进程退出；
// 阶段2：新进程 session/resume + session/send（追问哨兵句）→ 验证上下文跨进程存活。
// 隔离：Provider 配置只读取并复制到临时目录；存储/会话库/日志/数据根全部指向临时目录，不写真实 ~/.zcode。
// 用法：node probe-zcode-resume.mjs <cliPath> <builtinSrc> <personalSrc> <outDir>
// 产出：<outDir>/phase{1,2}/protocol.ndjson（全部协议消息）、<outDir>/result.json（结论摘要）。
import { spawn } from 'node:child_process';
import { mkdirSync, copyFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { createInterface } from 'node:readline';
import { createWriteStream } from 'node:fs';

const [cliPath, builtinSrc, personalSrc, outDir, modelId, reasoningLevel] = process.argv.slice(2);
if (!cliPath || !builtinSrc || !personalSrc || !outDir) {
  console.error('用法: node probe-zcode-resume.mjs <cliPath> <builtinSrc> <personalSrc> <outDir> [modelId] [reasoningLevel]');
  process.exit(2);
}
const SECRET = 'T00_SECRET_7f3a';
// CLI 对失败请求按约 50 秒退避重试最多 11 次，看门狗须覆盖完整重试周期。
const PHASE_MS = 600_000;
// 可选显式模型选择；省略时用 CLI 默认。形态对齐 ZCodeTaskConversation 的 selection（providerId/modelId/options.reasoningLevel）。
const SELECTION = modelId ? { providerId: 'new-provider', modelId, ...(reasoningLevel ? { options: { reasoningLevel } } : {}) } : null;

// 两阶段共用：工作目录与会话库（验证跨进程续接需要同一个库）。
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

async function runPhase(name, { resume, prompt }) {
  const phaseDir = join(outDir, name);
  const log = createWriteStream(join(phaseDir, 'protocol.ndjson'));
  const child = spawn(process.execPath, [cliPath, 'app-server', '--stdio', '--cwd', work], {
    cwd: work, env: baseEnv(),
  });
  const pending = new Map();
  const state = { sessionId: null, resumedEvent: false, turnText: null, turnFailed: null, eventTypes: [], resumeParamsTried: [] };
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
      // 反向请求：只回答运行时偏好（memoryEnabled=true 以便后续记忆探针复用本脚本骨架）。
      if (msg.method === 'session/requestRuntimePreferences') {
        send({ id: msg.id, result: { nativeSearchEnhancementsEnabled: false, memoryEnabled: true, askUserQuestionAutoResolutionEnabled: false, toolSearchMode: 'off', additionalDirectories: [] } });
      } else {
        send({ id: msg.id, error: { code: -32601, message: 'probe: unsupported reverse request' } });
      }
      return;
    }
    if (msg.id != null) {
      const p = pending.get(String(msg.id));
      if (p) { pending.delete(String(msg.id)); msg.error ? p.reject(Object.assign(new Error(msg.error.message || 'error'), { code: msg.error.code })) : p.resolve(msg.result); }
      return;
    }
    if (msg.method === 'session/event') {
      const { type, payload } = msg.params || {};
      state.eventTypes.push(type);
      if (type === 'session.resumed') state.resumedEvent = true;
      if (type === 'turn.completed') { state.turnText = payload?.response ?? null; state.resolveTurn(); }
      if (type === 'turn.failed') { state.turnFailed = payload?.error?.code || 'unknown'; state.resolveTurn(); }
    }
  });
  child.stderr.on('data', () => {}); // 丢弃 stderr，避免管道阻塞；异常以协议结果为准。

  const watchdog = setTimeout(() => { state.turnFailed = state.turnFailed || 'phase-watchdog'; state.resolveTurn(); }, PHASE_MS);

  const summary = { name, ok: false, error: null };
  try {
    // 1) 建会话或续接。resume 依次尝试两种参数形态，错误信息原样记录（这就是实测数据）。
    if (!resume) {
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
    } else {
      let lastErr = null;
      for (const params of [{ sessionId: resume }, { sessionId: resume, workspace: { workspacePath: work, workspaceKey: work } }]) {
        state.resumeParamsTried.push(params);
        try {
          const result = await request('p-resume', 'session/resume', params);
          state.sessionId = result?.session?.sessionId || resume;
          lastErr = null;
          break;
        } catch (err) { lastErr = err; }
      }
      if (lastErr) throw lastErr;
    }
    // 2) 订阅事件。
    await request('p-subscribe', 'session/subscribe', { sessionId: state.sessionId, deliveryKind: 'desktop-continuous', includeSnapshot: false });
    // 3) 发送探针消息（不带 modelSelection，用 CLI 默认模型）。
    const sendResult = await request('p-send', 'session/send', { sessionId: state.sessionId, inputId: randomUUID(), ...(SELECTION ? { modelSelection: SELECTION } : {}), content: prompt });
    if (sendResult && sendResult.accepted === false) throw new Error('session/send 未被接受');
    // 4) 等 turn 终态。
    await turnDone;
    if (state.turnFailed) throw new Error(`turn 失败: ${state.turnFailed}`);
    if (state.turnText == null) throw new Error('turn.completed 缺少 response');
    summary.ok = true;
  } catch (err) {
    summary.error = String(err.message || err);
  } finally {
    clearTimeout(watchdog);
    // 结束进程树（Windows 下 kill 后再兜底 taskkill /T /F）。
    stdinClosed = true;
    try { child.stdin.end(); } catch {}
    try { child.kill(); } catch {}
    setTimeout(() => { try { spawn('taskkill', ['/PID', String(child.pid), '/T', '/F']); } catch {} }, 1500);
    await new Promise((r) => setTimeout(r, 2500));
  }
  summary.sessionId = state.sessionId;
  summary.resumedEvent = state.resumedEvent;
  summary.resumeParamsTried = state.resumeParamsTried.map((p) => (p.workspace ? '{sessionId, workspace}' : '{sessionId}'));
  summary.reply = state.turnText;
  summary.eventTypes = state.eventTypes;
  summary.errorEvent = state.turnFailed;
  log.end();
  return summary;
}

const phase1 = await runPhase('phase1', { resume: null, prompt: `这是一次协议连通性测试。请把下面这句话原样记住：${SECRET}。除此之外只回复：已记住。` });
let phase2 = null;
if (phase1.ok) {
  phase2 = await runPhase('phase2', { resume: phase1.sessionId, prompt: '我上一条消息让你记住的句子是什么？只回复那个句子本身，不要其他内容。' });
}
const result = {
  probedAt: new Date().toISOString(),
  cliPath,
  crossProcessContext: phase2 ? { recalled: Boolean(phase2.reply && phase2.reply.includes(SECRET)), phase2Ok: phase2.ok } : null,
  phase1,
  phase2,
};
writeFileSync(join(outDir, 'result.json'), JSON.stringify(result, null, 2));
console.log(JSON.stringify({ crossProcessContext: result.crossProcessContext, p1: { ok: phase1.ok, error: phase1.error }, p2: phase2 && { ok: phase2.ok, error: phase2.error, resumedEvent: phase2.resumedEvent, resumeParamsTried: phase2.resumeParamsTried, reply: (phase2.reply || '').slice(0, 200) } }, null, 2));
