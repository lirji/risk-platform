<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageState from '../components/PageState.vue'
import { useAuthStore } from '../stores/auth'
const router = useRouter(); const auth = useAuthStore(); const error = ref('')
onMounted(async () => {
  try { await router.replace(await auth.completeLogin()) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '登录回调处理失败' }
})
</script>
<template><main class="login-card-wrap" style="min-height:100vh"><PageState :loading="!error" :error="error" @retry="router.replace('/login')" /></main></template>
