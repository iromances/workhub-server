import { defineConfig } from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/vite/dist/node/index.js'
import vue from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/@vitejs/plugin-vue/dist/index.mjs'
const root = '/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web'
const empty = { total: 0, items: [] }
const scope = { businessLineCode: 'BL000012', environmentCode: 'TEST' }
let mode = 'normal'
const caseData = id => ({ id, fingerprint: id, ...scope, sourceType: 'SYSTEM_ALERT', subjectKey: 'demo', category: 'APPLICATION_ERROR',
  title: id === 'case-main' ? '本地样本：巡检发现业务错误' : id === 'case-empty' ? '本地样本：尚无缺陷关联' : '本地样本：第二个 Agent 的问题',
  status: 'ANALYZING', activeRunId: 'demo-run', latestProposalId: id === 'case-main' ? 'proposal-current' : null,
  occurrenceCount: 1, evidenceVersion: 1, version: 9, firstSeenAt: '2026-11-04T03:00:00', lastSeenAt: '2026-11-04T03:00:00',
  lastObservedAt: '2026-11-04T03:10:00', resolvedAt: null, createdAt: '2026-11-04T03:00:00', updatedAt: '2026-11-04T03:15:00' })
const links = [6,5,4,3,2,1].map((n,i) => {
  const workItemId = n === 6 ? 3 : n === 5 || n === 2 ? 2 : 1
  const snapshot = { workItem: { no: `BUG-DEMO-${workItemId}`, title: n === 1 ? '第六条历史关联，仅第二页可见' : '本地样本缺陷', projectName: '本地样本项目', status: '处理中', priority: 'P1',
    description: n === 6 ? '异常发生时请求中断。\n<img src=x onerror=alert(1)> 为原始描述文字。' : '关联时记录的复现步骤。', acceptanceCriteria: '核对修复后的实际业务结果。' },
    scope, assessment: 'AI_SUGGESTED_RELATION', environmentBasis: 'REFERENCED_RUNTIME_EVIDENCE', ...(n === 6 ? { scopeBasis: 'CURRENT_MONITOR_CONFIGURATION', historicalEnvironmentVerified: false } : {}) }
  return { link: { id: `link-${n}`, caseId: 'case-main', workItemId, workItemVersion: String(n).repeat(64), snapshotJson: JSON.stringify(snapshot), observationId: `observation-${n}`, evidencePointer: '/data/events/0', signalId: `signal-${n}`, reason: `本地样本：缺陷描述与运行证据相符，待人审核（关联 ${n}）。`, runId: `run-${n}`, caseVersion: n + 1, createdAt: '2026-11-04T03:10:00' }, latest: i < 3, sourceState: workItemId === 2 ? 'UNAVAILABLE' : workItemId === 1 ? 'CHANGED' : 'CURRENT' }
})
function reply(res, value, status=200) { res.statusCode=status;res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify(value)) }
export default defineConfig({
  root: '/private/tmp/workhub-agent-bug-ui-harness',
  plugins: [vue(), { name:'local-fixture-only', configureServer(server) {
    server.middlewares.use(async (req,res,next) => {
      const url = new URL(req.url, 'http://127.0.0.1')
      if (url.pathname === '/__fixture/mode' && req.method === 'POST') {
        let body='';for await (const chunk of req) body+=chunk;mode=JSON.parse(body).mode;reply(res,{mode});return
      }
      if (!url.pathname.startsWith('/api/')) return next()
      const path=url.pathname.slice(4)
      if (path === '/autonomy/cases') {
        const ids=url.searchParams.get('agentId')==='agent-two' ? ['case-two'] : ['case-main','case-empty']
        return reply(res,{code:0,message:'ok',data:{total:ids.length,items:ids.map(caseData)}})
      }
      const detail=path.match(/^\/autonomy\/cases\/([^/]+)$/)
      if (detail) {
        const id=detail[1], main=id==='case-main'
        const proposal={ facts:'已有运行证据与缺陷关联。', hypothesis:'原因待验证。', impact:'测试环境样本。', plan:'按核对后的缺陷版本形成修复。', acceptance:'独立实测。', rollback:'恢复基线。', unknowns:'当前尚未证明修复完成。', changeType:'CODE' }
        return reply(res,{code:0,message:'ok',data:{caseInfo:caseData(id),signals:empty,
          proposals:main ? {total:2,items:[{id:'proposal-current',caseId:id,runId:'demo-run',caseVersion:9,proposalJson:JSON.stringify(proposal),createdAt:'2026-11-04T03:15:00'}, {id:'proposal-old',caseId:id,runId:'old-run',caseVersion:4,proposalJson:JSON.stringify(proposal),createdAt:'2026-11-04T03:05:00'}]} : empty,
          relatedRuns:[],relatedRunTotal:0,bugContext:main ? {historyCount:6,bugCount:3,changedCount:1,unavailableCount:1} : {historyCount:0,bugCount:0,changedCount:0,unavailableCount:0}}})
      }
      const bug=path.match(/^\/autonomy\/cases\/([^/]+)\/bugs$/)
      if (bug) {
        const sentMode=mode
        console.log(JSON.stringify({kind:'fixture-request',caseId:bug[1],page:url.searchParams.get('page'),mode:sentMode}))
        if (sentMode==='delay') await new Promise(resolve=>setTimeout(resolve,12000))
        if (sentMode==='error') return reply(res,{code:503,message:'本地模拟：关联读取暂不可用'},503)
        const page=Number(url.searchParams.get('page')||1), rows=bug[1]==='case-main'?links:[]
        return reply(res,{code:0,message:'ok',data:{total:rows.length,items:rows.slice((page-1)*5,page*5)}})
      }
      reply(res,{code:404,message:'本地样本没有此接口'},404)
    })
  }}],
  resolve: {alias:{'@':root+'/src'}},
  define: {'import.meta.env.VITE_API_BASE_URL':JSON.stringify('/api')},
  server:{host:'127.0.0.1',port:19538,strictPort:true,fs:{allow:[root,'/private/tmp/workhub-agent-bug-ui-harness','/Users/aslight/IDEAWorkspace/workhub-web/node_modules']}}
})
