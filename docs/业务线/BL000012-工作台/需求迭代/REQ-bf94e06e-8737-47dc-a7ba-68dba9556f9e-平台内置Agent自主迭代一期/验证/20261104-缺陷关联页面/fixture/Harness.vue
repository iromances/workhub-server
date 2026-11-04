<script setup lang="ts">
import { ref, computed } from 'vue'
import AgentCasesPanel from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/views/agent/AgentCasesPanel.vue'
import { useAuthStore } from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/stores/auth.ts'
const auth=useAuthStore();auth.permissions=['agent:view','autonomy:case:view','work-item:view']
const second=ref(false), mode=ref('normal'), selected=ref('normal'), ready=ref(true), visible=ref(true)
const agent=computed(()=>({id:second.value?'agent-two':'agent-one',configuration:{name:'本地样本Agent',instructions:'仅用于UI验证',providerConfigId:1,model:'fixture',scopes:[{businessLineCode:'BL000012',environmentCode:'TEST'}],toolCodes:[],maxSteps:20,maxRunSeconds:600,maxConcurrentRuns:1},enabled:true,ownerName:'fixture',version:0,createdAt:'2026-11-04T03:00:00',updatedAt:'2026-11-04T03:00:00'}))
const options={providers:[],tools:[],scopes:[{businessLineCode:'BL000012',businessLineName:'本地样本工作台',environmentCodes:['TEST']}]}
async function setMode(){ready.value=false;await fetch('/__fixture/mode',{method:'POST',body:JSON.stringify({mode:selected.value})});mode.value=selected.value;ready.value=true}
function togglePermission(){auth.permissions=auth.permissions.includes('work-item:view')?['agent:view','autonomy:case:view']:['agent:view','autonomy:case:view','work-item:view']}
</script>
<template><main><aside><strong>本地样本验证 · 不连接业务系统</strong><label>样本响应 <select v-model="selected" aria-label="样本响应" @change="setMode"><option value="normal">正常</option><option value="error">读取失败</option><option value="delay">延迟12秒</option></select></label><button @click="second=!second">切换 Agent</button><button @click="togglePermission">切换缺陷查看权限</button><button @click="visible=!visible">切换页面激活</button><span>{{ready?'已应用':'应用中'}}：{{mode}} · {{agent.id}}</span></aside><KeepAlive><AgentCasesPanel v-if="visible" :agent="agent" :options="options" /></KeepAlive></main></template>
<style>body{font-family:Arial,'PingFang SC',sans-serif;margin:0;background:#f5f7fa;color:#1f2937}main{padding:24px;max-width:1350px;margin:auto}aside{display:flex;gap:14px;align-items:center;flex-wrap:wrap;margin-bottom:18px;background:#eef4ff;padding:14px;font-size:13px}aside button,aside select{padding:6px}.page-card{background:white;border:1px solid #e5e7eb;border-radius:8px}.el-drawer__body{overflow:auto}aside{position:fixed;left:12px;top:12px;z-index:10000;width:220px;box-sizing:border-box;flex-direction:column;align-items:flex-start}main{padding-top:250px}</style>
