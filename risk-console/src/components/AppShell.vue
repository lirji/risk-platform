<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter, RouterView } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute(); const router = useRouter(); const auth = useAuthStore()
const collapsed = ref(false); const mobileOpen = ref(false); const query = ref('')
const items = [
  ['◫', '态势总览', '/dashboard', undefined], ['⌁', '风险决策', '/decisions', 'decision.read'],
  ['◈', '案件中心', '/cases', 'case.read'], ['◎', '客户画像', '/profiles', 'profile.read'],
  ['⌘', '规则治理', '/rules', 'rule.read'], ['◇', '模型中心', '/models', 'model.read'],
  ['▤', '评级任务', '/ratings', 'rating.read'], ['◌', '运维中心', '/operations', 'ops.read'],
  ['≋', '审计日志', '/audit', 'audit.read'],
] as const
const visibleItems = computed(() => items.filter((item) => auth.can(item[3])))
const title = computed(() => visibleItems.value.find((item) => item[2] === route.path)?.[1] ?? '风控指挥台')
function search() { if (query.value.trim()) router.push({ path: '/decisions', query: { q: query.value.trim() } }) }
</script>

<template>
  <div class="app-shell" :class="{ collapsed }">
    <aside class="sidebar" :class="{ 'mobile-open': mobileOpen }" aria-label="主导航">
      <div class="brand"><span class="brand-mark">R</span><div><strong>RISK COMMAND</strong><small>风控指挥台</small></div></div>
      <nav>
        <RouterLink v-for="item in visibleItems" :key="item[2]" :to="item[2]" @click="mobileOpen = false">
          <span class="nav-icon">{{ item[0] }}</span><span class="nav-label">{{ item[1] }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-foot"><span class="status-dot" />全链路监控中</div>
    </aside>
    <div v-if="mobileOpen" class="scrim" @click="mobileOpen = false" />
    <main class="workspace">
      <header class="topbar">
        <button class="icon-button desktop-toggle" aria-label="折叠导航" @click="collapsed = !collapsed">☰</button>
        <button class="icon-button mobile-toggle" aria-label="打开导航" @click="mobileOpen = true">☰</button>
        <div class="page-heading"><span>CONTROL PLANE</span><h1>{{ title }}</h1></div>
        <form class="global-search" role="search" @submit.prevent="search"><span>⌕</span><input v-model="query" aria-label="搜索决策流水" placeholder="搜索交易流水…" /></form>
        <span class="env-pill">{{ auth.user?.mode === 'auth-platform' ? 'AUTH PLATFORM' : 'LOCAL' }}</span>
        <el-dropdown trigger="click">
          <button class="user-button"><span>{{ auth.user?.displayName?.slice(0, 1) || 'U' }}</span><b>{{ auth.user?.displayName || '用户' }}</b></button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item disabled>{{ auth.user?.roles.join(' · ') }}</el-dropdown-item><el-dropdown-item divided @click="auth.logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </header>
      <section class="page-content"><RouterView /></section>
    </main>
  </div>
</template>
