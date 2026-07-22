<script setup lang="ts">
import { ref } from 'vue'
import PageState from '../components/PageState.vue'
import { api } from '../services/api'
import { useResource } from '../composables/useResource'

const account = ref('')
const searched = ref(false)
const resource = useResource(() => api<Record<string, any>>(`/api/v1/profiles/${encodeURIComponent(account.value)}`))
async function search() {
  if (!account.value.trim()) return
  searched.value = true
  await resource.load()
}
</script>
<template>
  <div>
    <header class="page-title"><div><h2>客户画像</h2><p>融合实时特征、离线标签、关系摘要与口径版本</p></div></header>
    <article class="panel">
      <form class="toolbar" @submit.prevent="search"><el-input v-model="account" clearable placeholder="输入客户账号" style="max-width:420px" aria-label="客户账号"/><el-button type="primary" native-type="submit">查询画像</el-button></form>
      <div v-if="!searched" class="state-panel"><span class="state-mark state-empty">⌕</span><h3>查询一个客户</h3><p>账号仅用于在线查询，页面和审计默认显示掩码。</p></div>
      <PageState v-else :loading="resource.loading.value" :error="resource.error.value" @retry="resource.load">
        <template v-if="resource.data.value">
          <el-alert v-if="!resource.data.value.available" title="实时特征存储不可用，以下结果为降级视图" type="warning" show-icon :closable="false"/>
          <div class="split" style="margin-top:16px"><article class="panel"><div class="panel-header"><h3>实时特征</h3><span>{{ resource.data.value.accountNo }}</span></div><div class="key-values"><div v-for="(value,key) in resource.data.value.onlineFeatures" :key="key"><label>{{ key }}</label><b>{{ value }}</b></div></div></article><article class="panel"><div class="panel-header"><h3>标签口径</h3><span>{{ resource.data.value.definitions.length }} 项</span></div><div class="detail-stack"><div v-for="tag in resource.data.value.definitions" :key="tag.tag_code" class="panel" style="padding:12px"><b>{{ tag.tag_name }}</b><p style="color:var(--muted);font-size:12px">{{ tag.definition_text }} · v{{ tag.version_no }}</p></div></div></article></div>
        </template>
      </PageState>
    </article>
  </div>
</template>
