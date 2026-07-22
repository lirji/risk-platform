<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageState from '../components/PageState.vue'
import { api } from '../services/api'
import { useResource } from '../composables/useResource'
import { useAuthStore } from '../stores/auth'

type Release = Record<string, any>
type Binding = { sourceId: string; activeReleaseId: string; previousReleaseId?: string; ruleSets: string[]; rolloutPercentage: number; shadowReleaseId?: string }
const auth = useAuthStore()
const resource = useResource(async () => ({
  releases: await api<Release[]>('/api/v1/rules/releases'),
  bindings: await api<Binding[]>('/api/v1/rules/bindings'),
}))
const createOpen = ref(false)
const publishOpen = ref(false)
const selectedRelease = ref<Release | null>(null)
const deployment = reactive({ sourceId: 'MOBILE_TRANSFER', ruleSets: 'blacklist,threshold,model', rolloutPercentage: 100, shadowReleaseId: '' })
const form = reactive({ ruleCode: '', ruleName: '', drl: `package rules.fraud;

rule "new-rule"
  agenda-group "threshold"
when
then
end` })
async function create() {
  await api('/api/v1/rules/releases', { method: 'POST', body: JSON.stringify(form) })
  createOpen.value = false; ElMessage.success('草稿已创建'); resource.load()
}
async function action(id: string, name: string) {
  await api(`/api/v1/rules/releases/${id}/${name}`, { method: 'POST' })
  ElMessage.success('状态已更新'); resource.load()
}
function openPublish(row: Release) { selectedRelease.value = row; publishOpen.value = true }
async function publish() {
  if (!selectedRelease.value) return
  const ruleSets = deployment.ruleSets.split(',').map((item) => item.trim()).filter(Boolean)
  if (!ruleSets.length) { ElMessage.error('至少配置一个规则集'); return }
  await ElMessageBox.confirm(`将 ${selectedRelease.value.ruleCode}-${selectedRelease.value.version} 发布到 ${deployment.sourceId}？`, '发布确认', { type: 'warning' })
  await api(`/api/v1/rules/releases/${selectedRelease.value.releaseId}/publish`, { method: 'POST', body: JSON.stringify({ sourceId: deployment.sourceId, ruleSets, rolloutPercentage: deployment.rolloutPercentage, shadowReleaseId: deployment.shadowReleaseId || null }) })
  publishOpen.value = false
  ElMessage.success('运行时编译通过并已原子切换'); resource.load()
}
async function rollback(row: Release) {
  await ElMessageBox.confirm(`将 MOBILE_TRANSFER 回滚到 ${row.ruleCode} 的上一已发布版本？`, '回滚确认', { type: 'warning' })
  await api(`/api/v1/rules/releases/${row.releaseId}/rollback`, { method: 'POST', body: JSON.stringify({ sourceId: 'MOBILE_TRANSFER' }) })
  ElMessage.success('规则运行时已回滚'); resource.load()
}
onMounted(resource.load)
</script>
<template>
  <div><header class="page-title"><div><h2>规则治理</h2><p>草稿、复核、灰度发布与版本回滚</p></div><el-button v-if="auth.can('rule.write')" type="primary" @click="createOpen=true">新建规则版本</el-button></header>
    <PageState :loading="resource.loading.value" :error="resource.error.value" @retry="resource.load"><template v-if="resource.data.value"><article class="panel"><div class="panel-header"><h3>来源与决策流绑定</h3><span>{{ resource.data.value.bindings.length }} 个来源</span></div><el-table :data="resource.data.value.bindings" empty-text="尚无已发布绑定"><el-table-column prop="sourceId" label="来源"/><el-table-column prop="activeReleaseId" label="当前版本" min-width="180"/><el-table-column label="规则集 / 决策流" min-width="220"><template #default="scope">{{ scope.row.ruleSets.join(' → ') }}</template></el-table-column><el-table-column prop="rolloutPercentage" label="灰度比例"><template #default="scope">{{ scope.row.rolloutPercentage }}%</template></el-table-column><el-table-column prop="shadowReleaseId" label="Shadow 版本" min-width="160"/></el-table></article><article class="panel section-gap"><div class="panel-header"><h3>规则版本</h3><span>{{ resource.data.value.releases.length }} 个版本</span></div><el-table :data="resource.data.value.releases" empty-text="尚无规则版本"><el-table-column prop="ruleCode" label="规则编码"/><el-table-column prop="ruleName" label="名称"/><el-table-column prop="version" label="版本" width="80"/><el-table-column prop="status" label="状态"/><el-table-column prop="authorId" label="作者"/><el-table-column label="操作" min-width="260"><template #default="scope"><el-button v-if="scope.row.status==='DRAFT'&&auth.can('rule.write')" link @click="action(scope.row.releaseId,'submit')">提审</el-button><el-button v-if="scope.row.status==='IN_REVIEW'&&auth.can('rule.approve')" link type="warning" @click="action(scope.row.releaseId,'approve')">批准</el-button><el-button v-if="scope.row.status==='APPROVED'&&auth.can('rule.publish')" link type="danger" @click="openPublish(scope.row)">发布</el-button><el-button v-if="scope.row.status==='PUBLISHED'&&auth.can('rule.publish')" link type="warning" @click="rollback(scope.row)">回滚</el-button></template></el-table-column></el-table></article></template></PageState>
    <el-dialog v-model="createOpen" title="新建规则版本" width="min(720px,94vw)"><el-form label-position="top"><el-form-item label="规则编码"><el-input v-model="form.ruleCode"/></el-form-item><el-form-item label="名称"><el-input v-model="form.ruleName"/></el-form-item><el-form-item label="DRL"><el-input v-model="form.drl" type="textarea" :rows="14" class="mono"/></el-form-item></el-form><template #footer><el-button @click="createOpen=false">取消</el-button><el-button type="primary" @click="create">保存草稿</el-button></template></el-dialog>
    <el-dialog v-model="publishOpen" title="配置发布流量" width="min(560px,94vw)"><el-form label-position="top"><el-form-item label="来源 ID"><el-input v-model="deployment.sourceId"/></el-form-item><el-form-item label="规则集 / 决策流（逗号分隔，按顺序执行）"><el-input v-model="deployment.ruleSets"/></el-form-item><el-form-item label="新版本灰度比例"><el-slider v-model="deployment.rolloutPercentage" :min="0" :max="100" show-input/></el-form-item><el-form-item label="Shadow 版本 ID（可选，仅双跑比对）"><el-input v-model="deployment.shadowReleaseId" clearable/></el-form-item></el-form><template #footer><el-button @click="publishOpen=false">取消</el-button><el-button type="danger" @click="publish">编译并发布</el-button></template></el-dialog>
  </div>
</template>
