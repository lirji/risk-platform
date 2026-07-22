<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <main class="login-card-wrap" style="min-height: 100vh">
    <article class="panel login-card">
      <span class="state-mark">!</span>
      <h2>没有访问权限</h2>
      <p>当前角色不包含此功能所需权限。若业务职责已变化，请联系平台管理员。</p>
      <el-button type="primary" @click="router.push('/dashboard')">返回总览</el-button>
    </article>
  </main>
</template>
