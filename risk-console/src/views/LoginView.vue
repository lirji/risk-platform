<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { AUTH_CONFIG, AUTH_MODE } from '../auth/config'
import { validateTenantSelection } from '../auth/tenantSelection'

const auth = useAuthStore()
const route = useRoute()
const returnTo = computed(() => typeof route.query.returnTo === 'string' ? route.query.returnTo : '/dashboard')
const tenant = ref(AUTH_CONFIG.organization)
const tenantError = ref('')

const FEATURES = [
  { icon: '◈', title: '实时风险识别', description: '融合规则、模型与客户画像，快速识别可疑交易' },
  { icon: '◎', title: '风险案件处置', description: '串联告警、认领、研判与审计，形成完整处置闭环' },
  { icon: '△', title: '统一身份与权限', description: '通过 Casdoor 单点登录，按职责呈现最小权限视图' },
]

async function startLogin() {
  tenantError.value = ''
  if (AUTH_MODE === 'oidc') {
    const selection = validateTenantSelection(tenant.value, AUTH_CONFIG.organization, AUTH_CONFIG.clientId)
    if (!selection.ok) {
      tenantError.value = selection.message
      return
    }
  }
  await auth.login(returnTo.value)
}
</script>

<template>
  <main class="login-page">
    <section class="login-story" aria-label="风控平台能力">
      <span class="env-pill login-story__eyebrow">ZERO TRUST CONTROL PLANE</span>
      <div class="login-story__brand"><span class="brand-mark">R</span><strong>RISK COMMAND</strong></div>
      <h1>把风险<br /><span>看清楚。</span></h1>
      <p>融合实时决策、客户画像、策略模型与案件运营，让每一次风险判断都有依据，每一次处置都有记录。</p>
      <div class="login-features">
        <div v-for="feature in FEATURES" :key="feature.title" class="login-feature">
          <span class="login-feature__icon" aria-hidden="true">{{ feature.icon }}</span>
          <div><strong>{{ feature.title }}</strong><small>{{ feature.description }}</small></div>
        </div>
      </div>
      <small class="login-story__foot">实时决策 · 全程可溯 · 最小权限</small>
    </section>

    <section class="login-card-wrap">
      <article class="panel login-card">
        <div class="login-mobile-brand">
          <span class="brand-mark">R</span>
          <div><strong>RISK COMMAND</strong><small>智能风控决策与运营平台</small></div>
        </div>
        <span class="login-card__eyebrow">SECURE ACCESS</span>
        <h2>{{ AUTH_MODE === 'oidc' ? '统一身份登录' : '本地开发模式' }}</h2>
        <p>{{ AUTH_MODE === 'oidc' ? '确认所属租户后，使用统一企业身份安全登录。' : '当前为本地开发环境，将使用预置开发身份进入控制台。' }}</p>

        <form class="login-form" @submit.prevent="startLogin">
          <label for="login-tenant">所属租户</label>
          <el-input
            id="login-tenant"
            v-model="tenant"
            size="large"
            :disabled="auth.redirecting || AUTH_MODE !== 'oidc'"
            autocomplete="organization"
            spellcheck="false"
            placeholder="请输入租户名称"
            aria-describedby="login-tenant-help"
            @input="tenantError = ''"
          >
            <template #prefix><span aria-hidden="true">◇</span></template>
          </el-input>
          <p id="login-tenant-help" class="tenant-help" :class="{ 'tenant-help--error': tenantError }" :role="tenantError ? 'alert' : undefined">
            {{ tenantError || `当前可用租户：${AUTH_CONFIG.organization}` }}
          </p>

          <el-alert v-if="auth.error" :title="auth.error" type="error" show-icon :closable="false" />
          <el-button native-type="submit" type="primary" color="#20d3b0" :loading="auth.redirecting" :disabled="auth.redirecting">
            {{ auth.redirecting ? '正在跳转 Casdoor…' : AUTH_MODE === 'oidc' ? '使用统一身份登录' : '进入本地开发模式' }}
          </el-button>
        </form>

        <div class="security-note">
          <strong>统一身份认证 · PKCE 安全登录</strong>
          <span>登录由统一身份平台完成，本系统不会存储您的密码；会话仅在当前标签页有效。</span>
        </div>
      </article>
    </section>
  </main>
</template>
