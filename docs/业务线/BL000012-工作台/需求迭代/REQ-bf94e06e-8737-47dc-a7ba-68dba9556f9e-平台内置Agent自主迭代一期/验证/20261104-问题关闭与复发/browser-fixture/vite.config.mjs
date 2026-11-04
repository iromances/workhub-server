import {defineConfig} from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/vite/dist/node/index.js'
import vue from '/Users/aslight/IDEAWorkspace/workhub-web/node_modules/@vitejs/plugin-vue/dist/index.mjs'
import {createHash} from 'node:crypto'
const web='/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web', root='/private/tmp/workhub-case-resolution-harness'
let mode='resolved'; let epoch=1;const plans=new Map()
function reply(res,value,status=200){res.statusCode=status;res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify(value))}
function createPlan(change,kind){
 const id='plan-'+change,scope={businessLineCode:'BL000012',environmentCode:'TEST'}
 const checks=[{id:'error',scope,toolCode:'workhub.logs',monitoringItem:'订单服务原错误：重复处理失败',passCondition:'原错误不再出现，且日志采集完整 <img src=x onerror=alert(1)>',observationSeconds:3600,sampleIntervalSeconds:60,maxDataAgeSeconds:300,minimumSamples:2,evidenceRequirements:'保留实际查询范围、覆盖状态与新鲜数据时间'},{id:'account',scope,toolCode:'monitor.accounting.detail',monitoringItem:'TX_INVALID_AMOUNT：账务原规则',passCondition:'同一规则通过；空数据、跳过或执行失败不能通过',observationSeconds:86400,sampleIntervalSeconds:3600,maxDataAgeSeconds:7200,minimumSamples:12,evidenceRequirements:'核对运行凭据、实际环境及规则定义摘要'}]
 const snapshot={schemaVersion:kind==='legacy'?1:2,change:{id:change,revision:3,manifest:{repositories:[{repositoryId:'server',repositoryName:'订单后端',resultSha:'b'.repeat(40)}],plan:{summary:'修复重复处理错误',impact:'订单与账务查询',acceptance:'验证原问题及相关规则',rollback:'由人判断并按已有流程回退'}}},evidence:[{id:'test-fixture',kind:'TEST_REPORT',payloadJson:'{"fixture":true,"result":"PASS"}',contentHash:'c'.repeat(64),createdAt:'2026-11-04T07:00:00'}],blockers:kind==='legacy'?['VERIFICATION_PLAN_MISSING']:[],steps:[{requestId:'request-'+change,approvalId:'approval-'+change,dependsOn:[],parameters:{COMMIT_SHA:'b'.repeat(40),ENVIRONMENT:'TEST'},binding:{hash:'d'.repeat(64),instance:{id:'local-fixture',name:'本地模拟 Jenkins',baseUrl:'http://127.0.0.1'},binding:{id:'fixture-job',name:'订单测试发布（样本）',scope,systemName:'orders',stage:'TEST_DEPLOY',jobSegments:['test','orders'],commitParameters:{server:'COMMIT_SHA'}}}}],verificationPlan:kind==='legacy'?null:{checks,historicalDataHandling:'历史重复数据另建处置待办，处理完成前保留部分修复状态',failureHandling:'保存失败证据并返回 Agent 修复，不自动回滚或修改数据'}}
 return {id,runId:'run-fixture',changeSetId:change,manifestHash:'a'.repeat(64),snapshotHash:createHash('sha256').update(JSON.stringify(snapshot)).digest('hex'),stage:'TEST_DEPLOY',status:'SUCCEEDED',version:0,createdBy:'Agent 样本',createdAt:'2026-11-04T07:00:00',updatedAt:'2026-11-04T07:00:00',businessVerified:false,current:true,readyForReview:kind!=='legacy',blockers:snapshot.blockers,reviews:[],deployments:[],snapshot}
}

const time='2026-11-04T08:30:00Z',scope={businessLineCode:'BL000012',environmentCode:'TEST'}
function verification(plan,kind){
 const passed=['passed','stale','resolved','reopened'].includes(kind),failed=kind==='failed'
 const checks=plan.snapshot.verificationPlan.checks.map((c,i)=>({checkId:c.id,assessment:failed?'FAIL':'PASS',reason:'本地样本：原错误和历史数据逐项核对。',reasonTruncated:false,historicalDataPending:kind==='observing'&&i===1,distinctSamples:passed?c.minimumSamples:1,observedSeconds:passed?c.observationSeconds:10,windowStart:'2026-11-04T07:00:00Z',windowEnd:time,constraints:kind==='stale'?['LATEST_DATA_STALE']:passed?[]:['INSUFFICIENT_DISTINCT_SAMPLES','OBSERVATION_WINDOW_INCOMPLETE'],latestSampleId:'sample-'+c.id+'-12',evidenceRoot:'a'.repeat(64)}))
 return {id:'verification-'+plan.id,planId:kind==='identity-mismatch'?'foreign-plan':plan.id,runId:plan.runId,changeSetId:plan.changeSetId,stage:plan.stage,manifestHash:plan.manifestHash,approvedSnapshotHash:plan.snapshotHash,status:passed?'PASSED':failed?'FAILED':'OBSERVING',version:100,constraintsCheckedAt:time,currentConstraintsSatisfied:['passed','resolved','reopened'].includes(kind),deployedAt:'2026-11-04T06:00:00Z',verificationPlan:plan.snapshot.verificationPlan,nextCheckAt:passed||failed?null:'2026-11-04T08:31:00Z',artifactId:passed?'artifact-fixture':null,checks,caseClosed:kind==='resolved',caseStatus:kind==='reopened'?'DISCOVERED':'RESOLVED',caseResolution:resolution('case-'+epoch,6),conclusion:{assessment:failed?'FAIL':'PASS',reason:('本地样本完整结论：查询范围和原始日志已核对，业务含义由Agent判断。').repeat(45)+' <img src=x onerror=alert(1)>',historicalDataPending:kind==='observing',conclusionSource:'AI_ASSESSMENT',platformConstraintsSatisfied:passed,checks,recordedAt:'2026-11-04T08:20:00Z'}}
}
function samples(plan,checkId){return Array.from({length:checkId==='error'?12:2},(_,i)=>({id:'sample-'+plan.id+'-'+checkId+'-'+(i+1),checkId,sampleOrder:i+1,assessment:i===3?'INCONCLUSIVE':'PASS',reason:'样本 '+(i+1)+' 的完整理由：'+('依据实际监测数据判断，并记录历史数据是否已处置。').repeat(10),historicalDataPending:i===3,createdAt:time,evidence:[{observationId:'observation-'+plan.id+'-'+checkId+'-'+(i+1),contentHash:'b'.repeat(64),pointer:'/data',sourceKey:'source-fixture',eventKey:'event-'+(i+1),observedAt:time,dataAt:'2026-11-04T08:29:00Z',problems:i===3?['COLLECTION_COVERAGE_UNPROVEN']:[],coverageFrom:'2026-11-04T08:20:00Z',coverageThrough:time}]}))}


function problem(id,current){return {id,fingerprint:'fixture',businessLineCode:'BL000012',environmentCode:'TEST',sourceType:'ACCOUNTING',subjectKey:'config:1:rule:BALANCE',category:'DATA_DISCREPANCY',title:'本地样本：账务差异待核对',status:current==='reopened'?'DISCOVERED':'RESOLVED',activeRunId:null,latestProposalId:current==='reopened'?null:'proposal',occurrenceCount:16,evidenceVersion:16,version:current==='reopened'?14:13,firstSeenAt:'2026-11-04T06:00:00Z',lastSeenAt:'2026-11-04T08:40:00Z',lastObservedAt:'2026-11-04T08:45:00Z',resolvedAt:current==='reopened'?null:'2026-11-04T08:30:00Z',createdAt:'2026-11-04T06:00:00Z',updatedAt:'2026-11-04T08:45:00Z'}}
function resolution(caseId,index){return {id:'resolution-'+caseId+'-'+index,caseId,caseVersion:index*2,resolvedVersion:index*2+1,evidenceVersion:index,runId:'run-'+index,verificationId:'verification-'+index,planId:'plan-'+index,changeSetId:'change-'+index,manifestHash:'a'.repeat(64),proposalId:'proposal-'+index,proposalHash:'b'.repeat(64),artifactId:'artifact-'+index,artifactHash:'c'.repeat(64),checkIdsJson:'["health"]',windowStart:'2026-11-04T07:00:00Z',windowEnd:'2026-11-04T08:00:00Z',reason:'第 '+index+' 轮：原问题及历史数据已按获批标准核对。<img src=x onerror=alert(1)>',createdAt:'2026-11-04T08:30:00Z'}}

export default defineConfig({root,cacheDir:"/private/tmp/workhub-case-resolution-vite-cache",plugins:[vue(),{name:'fixture-only',configureServer(server){server.middlewares.use(async(req,res,next)=>{
 const url=new URL(req.url,'http://127.0.0.1')
 if(url.pathname==='/__fixture/mode'&&req.method==='POST'){let body='';for await(const c of req)body+=c;mode=JSON.parse(body).mode;epoch++;return reply(res,{mode})}
 if(!url.pathname.startsWith('/api/'))return next()
 console.log(JSON.stringify({method:req.method,path:url.pathname,mode}))
 if(url.pathname.startsWith('/api/autonomy/cases')){
  const current=mode,caseId='case-'+epoch;
  if(req.method!=='GET')return reply(res,{code:405,message:'只读本地样本'},405)
  if(url.pathname==='/api/autonomy/cases')return reply(res,{code:0,data:{total:1,items:[problem(caseId,current)]}})
  const match=url.pathname.match(/^\/api\/autonomy\/cases\/([^/]+)(\/resolutions)?$/)
  if(!match)return reply(res,{code:404,message:'无本地接口'},404)
  if(!match[2])return reply(res,{code:0,data:{caseInfo:problem(match[1],current),signals:{total:0,items:[]},proposals:{total:0,items:[]},relatedRuns:[],relatedRunTotal:0,bugContext:{historyCount:0,bugCount:0,changedCount:0,unavailableCount:0}}})
  if(current==='delay')await new Promise(r=>setTimeout(r,6000))
  if(current==='error')return reply(res,{code:503,message:'本地样本：关闭历史读取失败'},503)
  const rows=current==='empty'?[]:Array.from({length:6},(_,i)=>resolution(current==='wrong-case'?'foreign-case':match[1],6-i));const page=Number(url.searchParams.get('page')||1)
  return reply(res,{code:0,data:{total:rows.length,items:rows.slice((page-1)*5,page*5)}})
 }
 if(url.pathname==='/api/agent-release-plans'&&req.method==='GET'){const change=url.searchParams.get('changeSetId');let plan=plans.get('plan-'+change);if(!plan){plan=createPlan(change,mode);plans.set(plan.id,plan)}return reply(res,{code:0,data:[plan]})}

 const verificationPath=url.pathname.match(/^\/api\/agent-release-plans\/([^/]+)\/verification(.*)$/)
 if(verificationPath){
  if(req.method!=='GET')return reply(res,{code:405,message:'本地只读样本不允许写入'},405)
  const plan=plans.get(verificationPath[1]),suffix=verificationPath[2],current=mode
  if(!plan)return reply(res,{code:404,message:'本地样本计划不存在'},404)
  if(suffix===''){
   if(current==='delay-summary')await new Promise(r=>setTimeout(r,6000))
   if(current==='error')return reply(res,{code:503,message:'本地模拟：复查结果暂时不可读'},503)
   return reply(res,{code:0,data:current==='empty'?null:verification(plan,current)})
  }
  if(suffix==='/samples'){
   if(current==='delay-samples')await new Promise(r=>setTimeout(r,6000))
   if(current==='sample-error')return reply(res,{code:503,message:'本地模拟：采样记录暂时不可读'},503)
   const checkId=url.searchParams.get('checkId'),after=Number(url.searchParams.get('afterOrder')),through=Number(url.searchParams.get('throughOrder')),size=Number(url.searchParams.get('pageSize'))
   const remaining=samples(plan,checkId).filter(s=>s.sampleOrder>after&&s.sampleOrder<=through),items=remaining.slice(0,size)
   return reply(res,{code:0,data:{planId:plan.id,verificationId:'verification-'+plan.id,checkId,afterOrder:after,throughOrder:through,pageSize:size,items,hasMore:remaining.length>size,nextAfterOrder:items.at(-1)?.sampleOrder||after}})
  }
  const found=suffix.match(/^\/samples\/([^/]+)\/evidence$/)
  if(found){
   if(current==='delay-evidence')await new Promise(r=>setTimeout(r,6000))
   if(current==='evidence-error')return reply(res,{code:503,message:'本地模拟：原始证据暂时不可读'},503)
   const row=[...samples(plan,'error'),...samples(plan,'account')].find(x=>x.id===found[1]);if(!row)return reply(res,{code:404,message:'样本不存在'},404)
   const fact=row.evidence[0]
   return reply(res,{code:0,data:{planId:plan.id,verificationId:'verification-'+plan.id,sampleId:row.id,checkId:row.checkId,observationId:fact.observationId,pointer:fact.pointer,contentHash:fact.contentHash,toolCode:row.checkId==='error'?'workhub.logs':'monitor.accounting.detail',sourceType:'LOCAL_FIXTURE',scope,observedAt:time,payload:{scope,observedAt:time,data:{message:'本地脱敏记录 <img src=x onerror=alert(1)>',authorization:'[REDACTED]',sample:row.sampleOrder,window:{from:'2026-11-04T08:20:00Z',to:time}}}}})
  }
  return reply(res,{code:404,message:'仅允许本地模拟读取'},404)
 }
 const match=url.pathname.match(/^\/api\/agent-release-plans\/([^/]+)(\/reviews)?$/),plan=match&&plans.get(match[1])
 if(!plan)return reply(res,{code:404,message:'本地样本计划不存在'},404)
 if(req.method==='GET')return reply(res,{code:0,data:plan})
 if(req.method==='POST'&&match[2]){let body='';for await(const c of req)body+=c;const decision=JSON.parse(body);console.log(JSON.stringify({review:decision,planId:plan.id}))
 if(decision.snapshotHash!==plan.snapshotHash||decision.expectedVersion!==plan.version||plan.status!=='WAITING_REVIEW')return reply(res,{code:409,message:'样本材料已变化'},409)
 if(decision.decision==='APPROVED'&&!plan.readyForReview)return reply(res,{code:400,message:'缺少复查标准'},400)
 plan.status=decision.decision;plan.version++;plan.readyForReview=false;plan.reviews.push({...decision,id:'review-'+plan.id,planId:plan.id,reviewedBy:'fixture-reviewer',createdAt:'2026-11-04T07:30:00'});return reply(res,{code:0,data:plan})}
 return reply(res,{code:404,message:'本地样本接口不存在'},404)
})}}],resolve:{alias:{'@':web+'/src'}},define:{'import.meta.env.VITE_API_BASE_URL':JSON.stringify('/api')},server:{host:'127.0.0.1',port:19543,strictPort:true,fs:{allow:[root,web,'/Users/aslight/IDEAWorkspace/workhub-web/node_modules']}}})
