<script setup lang="ts">
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageState from '../components/PageState.vue'
import MetricCard from '../components/MetricCard.vue'
import { api } from '../services/api'
import { useResource } from '../composables/useResource'
import { useAuthStore } from '../stores/auth'

type DeadEvent = Record<string, any>
const auth = useAuthStore()
const resource = useResource(async () => ({ health: await api<Record<string, any>>('/actuator/health'), dead: await api<DeadEvent[]>('/api/v1/operations/dead-events') }))
async function replay(event: DeadEvent) {
  await ElMessageBox.confirm(`重放事件 ${event.event_id}？请先确认下游故障已经恢复。`, '死信重放', { type: 'warning' })
  await api(`/api/v1/operations/dead-events/${event.event_id}/replay`, { method: 'POST' })
  ElMessage.success(event.event_kind === 'KAFKA_DLT' ? '事件已重新发送到原 Topic' : '事件已重新进入 Outbox 队列'); resource.load()
}
onMounted(resource.load)
</script>
<template>
  <div><header class="page-title"><div><h2>运维中心</h2><p>服务健康、消息死信、批任务与恢复操作</p></div><el-button @click="resource.load">刷新状态</el-button></header>
    <PageState :loading="resource.loading.value" :error="resource.error.value" @retry="resource.load"><template v-if="resource.data.value"><section class="metric-grid"><MetricCard label="管理服务" :value="resource.data.value.health.status" hint="Spring health"/><MetricCard label="统一死信" :value="resource.data.value.dead.length" hint="Outbox 与 Kafka DLT" :tone="resource.data.value.dead.length?'red':'teal'"/><MetricCard label="消费者策略" value="3 + DLT" hint="有限退避重试"/><MetricCard label="状态恢复" value="启用" hint="Checkpoint / lease"/></section><article class="panel"><div class="panel-header"><h3>死信事件</h3><span>授权重放会创建审计记录</span></div><PageState :empty="resource.data.value.dead.length===0" empty-text="当前没有死信事件"><el-table :data="resource.data.value.dead"><el-table-column prop="event_id" label="事件" min-width="190"><template #default="scope"><span class="mono">{{ scope.row.event_id }}</span></template></el-table-column><el-table-column prop="event_kind" label="来源" width="120"/><el-table-column prop="topic" label="原 Topic"/><el-table-column prop="attempts" label="尝试"/><el-table-column prop="last_error" label="最后错误" min-width="260"/><el-table-column label="操作"><template #default="scope"><el-button v-if="auth.can('ops.replay')" link type="warning" @click="replay(scope.row)">授权重放</el-button></template></el-table-column></el-table></PageState></article></template></PageState>
  </div>
</template>
