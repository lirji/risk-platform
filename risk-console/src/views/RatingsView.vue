<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageState from '../components/PageState.vue'
import { api } from '../services/api'
import { useResource } from '../composables/useResource'
import { useAuthStore } from '../stores/auth'

type Job = Record<string, any>
const auth = useAuthStore()
const resource = useResource(() => api<Job[]>('/api/v1/ratings/jobs'))
const open = ref(false)
const form = reactive({ modelCode: 'RISK_M1', sourceIndex: 'cust-tags', targetIndex: 'es-risk-store' })
async function create() { await api('/api/v1/ratings/jobs', { method: 'POST', body: JSON.stringify(form) }); open.value = false; ElMessage.success('评级任务已进入队列'); resource.load() }
async function retry(id: string) { await api(`/api/v1/ratings/jobs/${id}/retry`, { method: 'POST' }); ElMessage.success('任务已重新排队'); resource.load() }
onMounted(resource.load)
</script>
<template>
  <div><header class="page-title"><div><h2>评级任务</h2><p>原子领取、租约恢复、批量写入与单条失败核查</p></div><el-button v-if="auth.can('rating.write')" type="primary" @click="open=true">创建评级任务</el-button></header>
    <article class="panel"><PageState :loading="resource.loading.value" :error="resource.error.value" :empty="resource.data.value?.length===0" @retry="resource.load"><el-table v-if="resource.data.value" :data="resource.data.value"><el-table-column prop="job_id" label="任务编号" min-width="180"><template #default="scope"><span class="mono">{{ scope.row.job_id }}</span></template></el-table-column><el-table-column prop="model_code" label="模型"/><el-table-column prop="status" label="状态"/><el-table-column prop="attempts" label="尝试"/><el-table-column prop="lease_owner" label="执行器"/><el-table-column prop="last_error" label="失败原因"/><el-table-column label="操作"><template #default="scope"><el-button v-if="scope.row.status==='FAILED'&&auth.can('rating.write')" link @click="retry(scope.row.job_id)">重试</el-button></template></el-table-column></el-table></PageState></article>
    <el-dialog v-model="open" title="创建评级任务"><el-form label-position="top"><el-form-item label="模型"><el-input v-model="form.modelCode"/></el-form-item><el-form-item label="来源索引"><el-input v-model="form.sourceIndex"/></el-form-item><el-form-item label="目标索引"><el-input v-model="form.targetIndex"/></el-form-item></el-form><template #footer><el-button @click="open=false">取消</el-button><el-button type="primary" @click="create">创建</el-button></template></el-dialog>
  </div>
</template>
