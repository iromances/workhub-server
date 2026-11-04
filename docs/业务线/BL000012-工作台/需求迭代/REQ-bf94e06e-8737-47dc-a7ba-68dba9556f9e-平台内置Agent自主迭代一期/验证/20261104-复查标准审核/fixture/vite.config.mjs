import {defineConfig} from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/vite/dist/node/index.js'
import vue from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/@vitejs/plugin-vue/dist/index.mjs'
import {createHash} from 'node:crypto'
const web='/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web', root='/private/tmp/workhub-agent-verification-harness'
let mode='normal';const plans=new Map()
function reply(res,value,status=200){res.statusCode=status;res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify(value))}
function createPlan(change,kind){
 const id='plan-'+change,scope={businessLineCode:'BL000012',environmentCode:'TEST'}
 const checks=[{id:'error',scope,toolCode:'workhub.logs',monitoringItem:'订单服务原错误：重复处理失败',passCondition:'原错误不再出现，且日志采集完整 <img src=x onerror=alert(1)>',observationSeconds:3600,sampleIntervalSeconds:60,maxDataAgeSeconds:300,minimumSamples:2,evidenceRequirements:'保留实际查询范围、覆盖状态与新鲜数据时间'},{id:'account',scope,toolCode:'monitor.accounting.detail',monitoringItem:'TX_INVALID_AMOUNT：账务原规则',passCondition:'同一规则通过；空数据、跳过或执行失败不能通过',observationSeconds:86400,sampleIntervalSeconds:3600,maxDataAgeSeconds:7200,minimumSamples:12,evidenceRequirements:'核对运行凭据、实际环境及规则定义摘要'}]
 const snapshot={schemaVersion:kind==='legacy'?1:2,change:{id:change,revision:3,manifest:{repositories:[{repositoryId:'server',repositoryName:'订单后端',resultSha:'b'.repeat(40)}],plan:{summary:'修复重复处理错误',impact:'订单与账务查询',acceptance:'验证原问题及相关规则',rollback:'由人判断并按已有流程回退'}}},evidence:[{id:'test-fixture',kind:'TEST_REPORT',payloadJson:'{"fixture":true,"result":"PASS"}',contentHash:'c'.repeat(64),createdAt:'2026-11-04T07:00:00'}],blockers:kind==='legacy'?['VERIFICATION_PLAN_MISSING']:[],steps:[{requestId:'request-'+change,approvalId:'approval-'+change,dependsOn:[],parameters:{COMMIT_SHA:'b'.repeat(40),ENVIRONMENT:'TEST'},binding:{hash:'d'.repeat(64),instance:{id:'local-fixture',name:'本地模拟 Jenkins',baseUrl:'http://127.0.0.1'},binding:{id:'fixture-job',name:'订单测试发布（样本）',scope,systemName:'orders',stage:'TEST_DEPLOY',jobSegments:['test','orders'],commitParameters:{server:'COMMIT_SHA'}}}}],verificationPlan:kind==='legacy'?null:{checks,historicalDataHandling:'历史重复数据另建处置待办，处理完成前保留部分修复状态',failureHandling:'保存失败证据并返回 Agent 修复，不自动回滚或修改数据'}}
 return {id,runId:'run-fixture',changeSetId:change,manifestHash:'a'.repeat(64),snapshotHash:createHash('sha256').update(JSON.stringify(snapshot)).digest('hex'),stage:'TEST_DEPLOY',status:'WAITING_REVIEW',version:0,createdBy:'Agent 样本',createdAt:'2026-11-04T07:00:00',updatedAt:'2026-11-04T07:00:00',businessVerified:false,current:true,readyForReview:kind!=='legacy',blockers:snapshot.blockers,reviews:[],deployments:[],snapshot}
}
export default defineConfig({root,plugins:[vue(),{name:'fixture-only',configureServer(server){server.middlewares.use(async(req,res,next)=>{
 const url=new URL(req.url,'http://127.0.0.1')
 if(url.pathname==='/__fixture/mode'&&req.method==='POST'){let body='';for await(const c of req)body+=c;mode=JSON.parse(body).mode;return reply(res,{mode})}
 if(!url.pathname.startsWith('/api/'))return next()
 console.log(JSON.stringify({method:req.method,path:url.pathname,mode}))
 if(url.pathname==='/api/agent-release-plans'&&req.method==='GET'){const change=url.searchParams.get('changeSetId');let plan=plans.get('plan-'+change);if(!plan){plan=createPlan(change,mode);plans.set(plan.id,plan)}return reply(res,{code:0,data:[plan]})}
 const match=url.pathname.match(/^\/api\/agent-release-plans\/([^/]+)(\/reviews)?$/),plan=match&&plans.get(match[1])
 if(!plan)return reply(res,{code:404,message:'本地样本计划不存在'},404)
 if(req.method==='GET'){const current=mode;if(current==='delay')await new Promise(r=>setTimeout(r,6000));if(current==='error')return reply(res,{code:503,message:'本地模拟：复查标准读取失败'},503);return reply(res,{code:0,data:plan})}
 if(req.method==='POST'&&match[2]){let body='';for await(const c of req)body+=c;const decision=JSON.parse(body);console.log(JSON.stringify({review:decision,planId:plan.id}))
 if(decision.snapshotHash!==plan.snapshotHash||decision.expectedVersion!==plan.version||plan.status!=='WAITING_REVIEW')return reply(res,{code:409,message:'样本材料已变化'},409)
 if(decision.decision==='APPROVED'&&!plan.readyForReview)return reply(res,{code:400,message:'缺少复查标准'},400)
 plan.status=decision.decision;plan.version++;plan.readyForReview=false;plan.reviews.push({...decision,id:'review-'+plan.id,planId:plan.id,reviewedBy:'fixture-reviewer',createdAt:'2026-11-04T07:30:00'});return reply(res,{code:0,data:plan})}
 return reply(res,{code:404,message:'本地样本接口不存在'},404)
})}}],resolve:{alias:{'@':web+'/src'}},define:{'import.meta.env.VITE_API_BASE_URL':JSON.stringify('/api')},server:{host:'127.0.0.1',port:19541,strictPort:true,fs:{allow:[root,web,'/Users/aslight/IDEAWorkspace/workhub-web/node_modules']}}})
