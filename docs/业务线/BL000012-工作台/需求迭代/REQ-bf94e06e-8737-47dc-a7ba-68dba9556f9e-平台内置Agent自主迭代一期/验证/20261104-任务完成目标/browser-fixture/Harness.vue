<script setup lang="ts">
import {ref} from 'vue'
import AgentTaskDialog from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/views/agent/AgentTaskDialog.vue'
import AgentRunAcceptancePanel from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/views/agent/AgentRunAcceptancePanel.vue'
import {useAuthStore} from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/stores/auth.ts'
const auth=useAuthStore();auth.userName='fixture';auth.permissions=['agent:task:view']
const opened=ref(false),saved=ref<any>(null)
const agent={id:'fixture',enabled:true,configuration:{name:'本地任务样本',instructions:'样本',providerConfigId:1,model:'fixture',scopes:[{businessLineCode:'BL000012',environmentCode:'TEST'},{businessLineCode:'BL000012',environmentCode:'PROD'}],toolCodes:[],maxSteps:10,maxRunSeconds:60,maxConcurrentRuns:1},ownerName:'fixture',version:1,createdAt:'2026-11-04T08:00:00Z',updatedAt:'2026-11-04T08:00:00Z'}
const options={providers:[],tools:[],scopes:[{businessLineCode:'BL000012',businessLineName:'工作台',environments:['TEST','PROD']}]}
</script>
<template><main><h2>本地任务完成目标样本</h2><p>本机模拟保存；不调用实际 Agent 或发布流程。</p><button @click="opened=true">分派样本任务</button><AgentTaskDialog v-model="opened" :agent="agent" :options="options" @saved="saved=$event"/><template v-if="saved"><h3>已保存的任务规格</h3><pre>{{JSON.stringify(saved.specification,null,2)}}</pre><AgentRunAcceptancePanel :key="saved.id" :run="{id:saved.id,taskId:saved.id,version:0} as any"/></template></main></template>
<style>body{font-family:Arial,'PingFang SC',sans-serif;background:#f4f6f8;color:#1f2937}main{max-width:950px;margin:30px auto}pre{white-space:pre-wrap;background:white;padding:20px}button{padding:8px}</style>
