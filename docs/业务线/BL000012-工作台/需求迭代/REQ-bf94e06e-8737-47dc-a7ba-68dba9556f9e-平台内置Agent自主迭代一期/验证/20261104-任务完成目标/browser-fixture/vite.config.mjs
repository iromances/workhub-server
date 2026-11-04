import {defineConfig} from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/vite/dist/node/index.js'
import vue from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/@vitejs/plugin-vue/dist/index.mjs'
const web='/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web',root='/private/tmp/workhub-completion-target-harness';let sequence=0;const saved=new Map()
function reply(res,value){res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify({code:0,data:value}))}
export default defineConfig({root,plugins:[vue(),{name:'local-only-fixture',configureServer(server){server.middlewares.use(async(req,res,next)=>{
const path=new URL(req.url,'http://localhost').pathname
if(path==='/api/agents/fixture/tasks'&&req.method==='POST'){let body='';for await(const part of req)body+=part;const value=JSON.parse(body);const id='task-'+(++sequence);saved.set(id,value.specification);console.log(JSON.stringify({id,...value}));return reply(res,{id,agentId:'fixture',...value})}
const match=path.match(/^\/api\/agent-tasks\/(task-\d+)\/runs\/[^/]+\/acceptance$/)
if(match){const spec=saved.get(match[1]);return reply(res,{runId:match[1],checkedAt:'2026-11-04T12:30:00Z',satisfied:false,items:spec.expectedArtifacts.map(kind=>({kind,status:'UNAVAILABLE',message:'本地样本：尚无实际凭据',evidence:[],targets:['DEPLOYMENT','VERIFICATION'].includes(kind)?spec.completionTargets.map(scope=>({scope,status:'UNAVAILABLE',message:'该目标的实际凭据待核验',evidence:[]})):[]}))})}
if(path.startsWith('/api/')){res.statusCode=404;return reply(res,null)}next()
})}}],resolve:{alias:{'@':web+'/src'}},define:{'import.meta.env.VITE_API_BASE_URL':JSON.stringify('/api')},server:{host:'127.0.0.1',port:19544,strictPort:true,fs:{allow:[root,web,'/Users/aslight/IDEAWorkspace/workhub-web/node_modules']}}})
