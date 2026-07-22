<script setup lang="ts">
import { onMounted } from 'vue'
import PageState from '../components/PageState.vue'
import { api } from '../services/api'
import { useResource } from '../composables/useResource'

const resource = useResource(() => api<Record<string, any>[]>('/api/v1/audit'))
onMounted(resource.load)
</script>
<template>
  <div><header class="page-title"><div><h2>审计日志</h2><p>规则、模型、案件与恢复操作的不可省略证据</p></div><el-button @click="resource.load">刷新</el-button></header><article class="panel"><PageState :loading="resource.loading.value" :error="resource.error.value" :empty="resource.data.value?.length===0" @retry="resource.load"><el-table v-if="resource.data.value" :data="resource.data.value"><el-table-column prop="created_at" label="时间" min-width="170"/><el-table-column prop="actor_id" label="操作者"/><el-table-column prop="action" label="动作" min-width="190"/><el-table-column prop="resource_type" label="资源"/><el-table-column prop="resource_id" label="资源 ID" min-width="180"><template #default="scope"><span class="mono">{{ scope.row.resource_id }}</span></template></el-table-column><el-table-column prop="details_json" label="详情" min-width="240"/></el-table></PageState></article></div>
</template>
