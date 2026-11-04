<script setup lang="ts">
import { computed, ref } from 'vue'
import AgentReleasesPanel from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/views/agent/AgentReleasesPanel.vue'
import { useAuthStore } from '/@fs/Users/aslight/IDEAWorkspace/workhub-agent-autonomy/workhub-web/src/stores/auth.ts'
const auth=useAuthStore(), full=['agent:task:view','agent:code:use','ops:system-alert:view','ops:accounting:view']
auth.userName='fixture-reviewer';auth.permissions=full
const mode=ref('observing'), revision=ref(1), second=ref(false), visible=ref(true)
const changeId=computed(()=>`change-${second.value?'two':'one'}-${revision.value}`)
async function setMode(){await fetch('/__fixture/mode',{method:'POST',body:JSON.stringify({mode:mode.value})});revision.value++}
function toggleCode(){auth.permissions=auth.permissions.includes('agent:code:use')?full.filter(x=>x!=='agent:code:use'):full}
function toggleReview(){auth.permissions=auth.permissions.includes('ops:system-alert:view')?full.filter(x=>x!=='ops:system-alert:view'):full}
</script>
<template><main><aside><strong>本地隔离样本 · 不连接业务系统</strong><label>场景 <select aria-label="场景" v-model="mode" @change="setMode"><option value="observing">observing</option><option value="passed">passed</option><option value="stale">stale</option><option value="failed">failed</option><option value="empty">empty</option><option value="error">error</option><option value="sample-error">sample-error</option><option value="evidence-error">evidence-error</option><option value="delay-summary">delay-summary</option><option value="delay-samples">delay-samples</option><option value="delay-evidence">delay-evidence</option><option value="identity-mismatch">identity-mismatch</option></select></label><button @click="second=!second">切换候选</button><button @click="toggleCode">切换查看权限</button><button @click="toggleReview">切换来源权限</button><button @click="visible=!visible">切换页面激活</button><span>{{changeId}} · {{mode}}</span></aside><h2>Agent 业务复查结果与证据</h2><KeepAlive><AgentReleasesPanel v-if="visible" :change-set-id="changeId" /></KeepAlive></main></template>
<style>body{font-family:Arial,'PingFang SC',sans-serif;background:#f4f6f8;margin:0;color:#1f2937}main{padding:140px 28px 28px;max-width:1000px;margin:auto}aside{position:fixed;top:0;left:0;right:0;z-index:10000;background:#e7efff;padding:18px 28px;display:flex;gap:12px;align-items:center;flex-wrap:wrap;font-size:13px}aside button,aside select{padding:7px}h2{font-size:20px}</style>
