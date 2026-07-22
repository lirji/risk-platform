<script setup lang="ts">
import { computed, onMounted } from 'vue'
import MetricCard from '../components/MetricCard.vue'; import RiskChart from '../components/RiskChart.vue'; import PageState from '../components/PageState.vue'
import { api } from '../services/api'; import { useResource } from '../composables/useResource'; import type { Decision, PageResponse } from '../types'
const resource = useResource(async () => {
  const [decisions, cases, dead] = await Promise.all([
    api<PageResponse<Decision>>('/api/v1/decisions?size=100'), api<Record<string, unknown>[]>('/api/v1/cases'), api<Record<string, unknown>[]>('/api/v1/operations/dead-events')])
  return { decisions, cases, dead }
})
const high = computed(() => resource.data.value?.decisions.content.filter((d) => ['HIGH','REJECT'].includes(d.riskLevel)).length ?? 0)
const openCases = computed(() => resource.data.value?.cases.filter((item) => item.status !== 'RESOLVED').length ?? 0)
const buckets = computed(() => ['LOW','MEDIUM','HIGH','REJECT'].map((level) => resource.data.value?.decisions.content.filter((d) => d.riskLevel === level).length ?? 0))
onMounted(resource.load)
</script>
<template><div><header class="page-title"><div><h2>实时风险态势</h2><p>决策、案件与数据链路的统一运行视图</p></div><el-button plain @click="resource.load">刷新数据</el-button></header><PageState :loading="resource.loading.value" :error="resource.error.value" @retry="resource.load"><template v-if="resource.data.value"><section class="metric-grid"><MetricCard label="近期待审决策" :value="high" hint="高风险与拒绝" tone="red"/><MetricCard label="未结案件" :value="openCases" hint="等待分析师处置" tone="amber"/><MetricCard label="决策样本" :value="resource.data.value.decisions.total" hint="当前检索窗口"/><MetricCard label="死信事件" :value="resource.data.value.dead.length" hint="需要授权重放" :tone="resource.data.value.dead.length ? 'red' : 'teal'"/></section><section class="dashboard-grid"><article class="panel"><div class="panel-header"><h3>风险等级分布</h3><span>最近 100 条决策</span></div><RiskChart :labels="['低风险','中风险','高风险','拒绝']" :values="buckets" summary="最近决策按风险等级分布" /></article><article class="panel"><div class="panel-header"><h3>链路健康</h3><span>运行状态</span></div><div class="detail-stack"><div class="key-values"><div><label>决策持久化</label><b><span class="status-dot"/>正常</b></div><div><label>Outbox</label><b>{{ resource.data.value.dead.length ? '存在死信' : '无积压告警' }}</b></div><div><label>案件闭环</label><b>{{ openCases }} 待处理</b></div><div><label>数据新鲜度</label><b>实时</b></div></div><div v-if="resource.data.value.dead.length" class="panel degraded">存在发布失败事件，请进入运维中心核查后重放。</div></div></article></section></template></PageState></div></template>
