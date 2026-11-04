import { defineConfig } from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/vite/dist/node/index.js'
import vue from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/@vitejs/plugin-vue/dist/index.mjs'
const web='/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web'
let mode='mixed'
function reply(res,value,status=200){res.statusCode=status;res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify(value))}
function item(kind,status,message,id){return {kind,status,message,evidence:id?[{kind:kind==='CHANGE_SET'?'CHANGE_SET':'ARTIFACT',id,contentHash:'b'.repeat(64)}]:[]}}
function view(id,mode){const code=item('CHANGE_SET',mode==='blocked'?'BLOCKED':'SATISFIED',mode==='blocked'?'代码或关联来源的当前权限不足，无法验收':'当前清单的全部仓库已交付并核验；实测、发布及问题解决需另行验证',mode==='blocked'?null:'manifest-'+id)
 const items=mode==='code'?[code]:[item('REPORT','SATISFIED','已保存 AI 分析报告','report-'+id),code,item('TEST_REPORT','UNAVAILABLE','尚未取得绑定当前代码的实际构建测试证据，Jenkins 独立测试接入待完成'),item('DEPLOYMENT','UNAVAILABLE','尚未完成任务目标环境与发布结果的验收绑定'),item('VERIFICATION','UNAVAILABLE','尚未取得原问题回归及观察窗口的业务效果证据 <img src=x onerror=alert(1)>')]
 return {runId:id,checkedAt:'2026-11-04T07:30:00',satisfied:items.every(i=>i.status==='SATISFIED'),items}}
export default defineConfig({root:'/private/tmp/workhub-agent-acceptance-harness',plugins:[vue(),{name:'fixture-only',configureServer(server){server.middlewares.use(async(req,res,next)=>{
 const url=new URL(req.url,'http://127.0.0.1')
 if(url.pathname==='/__fixture/mode'&&req.method==='POST'){let body='';for await(const c of req)body+=c;mode=JSON.parse(body).mode;return reply(res,{mode})}
 if(!url.pathname.startsWith('/api/'))return next()
 const match=url.pathname.match(/^\/api\/agent-tasks\/task-fixture\/runs\/([^/]+)\/acceptance$/)
 if(!match||req.method!=='GET')return reply(res,{code:404,message:'本地样本接口不存在'},404)
 const current=mode,id=match[1];console.log(JSON.stringify({method:req.method,id,mode:current}))
 if(current==='delay')await new Promise(r=>setTimeout(r,6000))
 if(current==='error')return reply(res,{code:503,message:'本地模拟：暂时无法核验'},503)
 return reply(res,{code:0,message:'ok',data:view(id,current)})
})}}],resolve:{alias:{'@':web+'/src'}},define:{'import.meta.env.VITE_API_BASE_URL':JSON.stringify('/api')},server:{host:'127.0.0.1',port:19540,strictPort:true,fs:{allow:[web,'/private/tmp/workhub-agent-acceptance-harness','/Users/aslight/IDEAWorkspace/workhub-web/node_modules']}}})
