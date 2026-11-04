import { defineConfig } from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/vite/dist/node/index.js'
import vue from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/@vitejs/plugin-vue/dist/index.mjs'
const root='/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web'
let mode='cli';const saved=new Map();let writes=0
function reply(res,value,status=200){res.statusCode=status;res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify(value))}
function view(id,mode){
 const cli={kind:'CLI',key:'cli:fixture',toolCode:null,readOnly:null,host:'fixture-worker',pid:789,startInstant:'2026-11-04T06:00:00Z',phase:'RUNNING',processState:mode==='alive'?'RUNNING':'REMOTE_HOST',receiptSource:null,receiptKey:null,needsConfirmation:true,requestPayloadJson:JSON.stringify({task:'本地样本 <img src=x onerror=alert(1)>',scope:'TEST'})}
 const tool={kind:'TOOL',key:'cli:fixture:tool',toolCode:'change.publish',readOnly:false,host:null,pid:null,startInstant:null,phase:null,processState:null,receiptSource:null,receiptKey:null,needsConfirmation:true,requestPayloadJson:JSON.stringify({tool:'change.publish',arguments:{repository:'local-fixture'}})}
 const api={...tool,kind:'API',key:'step:4',toolCode:null,readOnly:null,requestPayloadJson:JSON.stringify({provider:'本地样本 AI'})}
 const calls=mode==='api'?[api]:[cli,tool]
 if(mode==='known')calls.forEach(c=>{c.needsConfirmation=false;c.receiptKey=c.key+':result';c.receiptSource=c.kind==='CLI'?'CLI_RESULT':'TOOL_RESULT'})
 return {runId:id,version:11,snapshotHash:'b'.repeat(64),leaseUntil:'2026-11-04T06:10:00',canReconcile:!['alive','known'].includes(mode),cancelRequested:mode==='cancel',blockedReasons:mode==='alive'?['本机 CLI 进程仍存活，需先核实并停止原进程树，不能解除占用']:mode==='known'?['已取得全部实际回执，等待执行器自动恢复，无需人工替代回执']:[],resumeBlockedReasons:mode==='cancel'?['运行已请求取消']:[],calls}
}
export default defineConfig({root:'/private/tmp/workhub-agent-reconciliation-harness',plugins:[vue(),{name:'local-fixture-only',configureServer(server){server.middlewares.use(async(req,res,next)=>{
 const url=new URL(req.url,'http://127.0.0.1')
 if(url.pathname==='/__fixture/mode'&&req.method==='POST'){let body='';for await(const c of req)body+=c;mode=JSON.parse(body).mode;return reply(res,{mode})}
 if(!url.pathname.startsWith('/api/'))return next()
 const match=url.pathname.match(/^\/api\/agent-tasks\/task-fixture\/runs\/([^/]+)\/reconciliation$/)
 if(!match)return reply(res,{code:404,message:'无此本地接口'},404)
 const current=mode,id=match[1]
 if(req.method==='GET'){
  console.log(JSON.stringify({method:'GET',runId:id,mode:current}))
  if(current==='delay')await new Promise(r=>setTimeout(r,12000))
  if(current==='error')return reply(res,{code:503,message:'本地模拟：读取暂不可用'},503)
  return reply(res,{code:0,message:'ok',data:view(id,current)})
 }
 let body='';for await(const c of req)body+=c
 const request=JSON.parse(body),key=id+':'+request.requestKey
 console.log(JSON.stringify({method:'POST',runId:id,mode:current,key:request.requestKey,body}))
 if(saved.has(key)){const prior=saved.get(key);if(prior.body!==body)return reply(res,{code:409,message:'同键不同内容'},409);return reply(res,{code:0,message:'ok',data:prior.result})}
 const result={runId:id,status:request.nextAction==='CANCEL'?'CANCELLED':request.nextAction==='RESUME'?'QUEUED':'WAITING_INPUT',version:12,eventKey:'reconcile:'+request.requestKey}
 saved.set(key,{body,result});writes++;console.log(JSON.stringify({kind:'saved',writes,key}))
 if(current==='post-delay')await new Promise(r=>setTimeout(r,12000))
 if(current==='lost')return reply(res,{code:503,message:'本地模拟：提交后响应丢失，请重试原请求'},503)
 return reply(res,{code:0,message:'ok',data:result})
})}}],resolve:{alias:{'@':root+'/src'}},define:{'import.meta.env.VITE_API_BASE_URL':JSON.stringify('/api')},server:{host:'127.0.0.1',port:19539,strictPort:true,fs:{allow:[root,'/private/tmp/workhub-agent-reconciliation-harness','/Users/aslight/IDEAWorkspace/workhub-web/node_modules']}}})
