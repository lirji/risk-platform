<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageState from '../components/PageState.vue'
import { api } from '../services/api'
import { useResource } from '../composables/useResource'
import { useAuthStore } from '../stores/auth'

type Model = Record<string, any>
const auth = useAuthStore()
const resource = useResource(() => api<Model[]>('/api/v1/models'))
const open = ref(false)
const activationOpen = ref(false)
const activationModel = ref<Model | null>(null)
const rolloutPercentage = ref(100)
const form = reactive({ modelCode: 'fraud-rf', version: 1, artifactUri: 'http://localhost:9000/models/fraud-rf/model.pmml', checksum: '', trainingDataVersion: 'facts-v1', metrics: { auc: .9, ks: .5, recallAtFixedFpr: .65, precision: .75, recall: .7 } })
async function register() {
  await api('/api/v1/models', { method: 'POST', body: JSON.stringify(form) })
  open.value = false; ElMessage.success('模型制品已登记'); resource.load()
}
async function action(id: string, name: string) {
  if (name === 'rollback') await ElMessageBox.confirm('确认把该历史模型重新切换为活动版本？', '模型回滚', { type: 'warning' })
  await api(`/api/v1/models/${id}/${name}`, { method: 'POST' })
  ElMessage.success(name === 'approve' ? '模型已批准' : '模型运行版本已更新'); resource.load()
}
function openActivation(row: Model) { activationModel.value = row; rolloutPercentage.value = 100; activationOpen.value = true }
async function activate() {
  if (!activationModel.value) return
  await ElMessageBox.confirm(`将模型流量灰度比例设置为 ${rolloutPercentage.value}%？`, '模型激活', { type: 'warning' })
  await api(`/api/v1/models/${activationModel.value.model_id}/activate`, { method: 'POST', body: JSON.stringify({ rolloutPercentage: rolloutPercentage.value }) })
  activationOpen.value = false; ElMessage.success('模型运行版本已更新'); resource.load()
}
onMounted(resource.load)
</script>
<template>
  <div><header class="page-title"><div><h2>模型中心</h2><p>评估、审批、制品校验、激活与漂移监控</p></div><el-button v-if="auth.can('model.write')" type="primary" @click="open=true">登记模型版本</el-button></header>
    <article class="panel"><PageState :loading="resource.loading.value" :error="resource.error.value" :empty="resource.data.value?.length===0" @retry="resource.load"><el-table v-if="resource.data.value" :data="resource.data.value"><el-table-column prop="model_code" label="模型"/><el-table-column prop="version_no" label="版本" width="80"/><el-table-column prop="status" label="状态"/><el-table-column prop="rollout_percentage" label="流量" width="80"><template #default="scope">{{ scope.row.rollout_percentage }}%</template></el-table-column><el-table-column prop="training_data_version" label="训练数据"/><el-table-column prop="metrics_json" label="评估指标" min-width="260"><template #default="scope"><span class="mono">{{ scope.row.metrics_json }}</span></template></el-table-column><el-table-column label="操作" width="180"><template #default="scope"><el-button v-if="scope.row.status==='REGISTERED'&&auth.can('model.approve')" link @click="action(scope.row.model_id,'approve')">批准</el-button><el-button v-if="['APPROVED','CANARY'].includes(scope.row.status)&&auth.can('model.activate')" link type="warning" @click="openActivation(scope.row)">{{ scope.row.status==='CANARY' ? '调整灰度' : '激活' }}</el-button><el-button v-if="['RETIRED','STABLE'].includes(scope.row.status)&&auth.can('model.activate')" link type="warning" @click="action(scope.row.model_id,'rollback')">回滚</el-button></template></el-table-column></el-table></PageState></article>
    <el-dialog v-model="open" title="登记模型制品" width="min(680px,94vw)"><el-form label-position="top"><div class="key-values"><el-form-item label="模型编码"><el-input v-model="form.modelCode"/></el-form-item><el-form-item label="版本"><el-input-number v-model="form.version" :min="1"/></el-form-item></div><el-form-item label="制品 URI"><el-input v-model="form.artifactUri"/></el-form-item><el-form-item label="SHA-256"><el-input v-model="form.checksum" class="mono"/></el-form-item><el-form-item label="训练数据版本"><el-input v-model="form.trainingDataVersion"/></el-form-item></el-form><template #footer><el-button @click="open=false">取消</el-button><el-button type="primary" @click="register">登记</el-button></template></el-dialog>
    <el-dialog v-model="activationOpen" title="模型灰度激活" width="min(520px,94vw)"><p>相同 sourceId + txnId 会稳定命中同一模型版本；未进入新版本的流量继续使用当前活动模型。</p><el-form label-position="top"><el-form-item label="新版本流量比例"><el-slider v-model="rolloutPercentage" :min="0" :max="100" show-input/></el-form-item></el-form><template #footer><el-button @click="activationOpen=false">取消</el-button><el-button type="warning" @click="activate">校验制品并激活</el-button></template></el-dialog>
  </div>
</template>
