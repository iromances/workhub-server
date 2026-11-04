<script setup lang="ts">
import {computed,ref} from 'vue'
import AgentCasesPanel from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/views/agent/AgentCasesPanel.vue'
import AgentReleasesPanel from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/views/agent/AgentReleasesPanel.vue'
import {useAuthStore} from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/stores/auth.ts'
const auth=useAuthStore(),full=['agent:view','autonomy:case:view','agent:task:view','agent:code:use','ops:system-alert:view','ops:accounting:view']
auth.userName='fixture-reviewer';auth.permissions=full
const mode=ref('resolved'),revision=ref(1),visible=ref(true),release=ref(false)
const agent=computed(()=>({id:'agent-'+revision.value,configuration:{name:'本地样本',scopes:[{businessLineCode:'BL000012',environmentCode:'TEST'}]}}))
const options={scopes:[{businessLineCode:'BL000012',businessLineName:'工作台',environments:['TEST']}]}
async function setMode(){await fetch('/__fixture/mode',{method:'POST',body:JSON.stringify({mode:mode.value})});revision.value++}
function permission(){auth.permissions=auth.permissions.includes('ops:accounting:view')?full.filter(x=>x!=='ops:accounting:view'):full}
</script>
<template><main><aside><strong>本地隔离样本 · 无业务系统调用</strong><label>场景 <select aria-label="场景" v-model="mode" @change="setMode"><option>resolved</option><option>reopened</option><option>empty</option><option>error</option><option>delay</option><option>wrong-case</option></select></label><button @click="permission">切换来源权限</button><button @click="visible=!visible">切换页面激活</button><button @click="release=!release">切换结果页面</button><span>{{mode}} / {{revision}}</span></aside><h2>问题关闭与复发本地核验</h2><KeepAlive><AgentCasesPanel v-if="visible&&!release" :agent="agent" :options="options" /></KeepAlive><AgentReleasesPanel v-if="visible&&release" :change-set-id="'change-'+revision" /></main></template>
<style>body{font-family:Arial,'PingFang SC',sans-serif;background:#f4f6f8;margin:0;color:#1f2937}main{padding:155px 24px 24px;max-width:1000px;margin:auto}aside{position:fixed;top:0;left:0;right:0;z-index:10000;background:#e7efff;padding:16px 24px;display:flex;gap:12px;align-items:center;flex-wrap:wrap;font-size:13px}aside button,aside select{padding:7px}h2{font-size:20px}</style>
