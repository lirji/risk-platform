<script setup lang="ts">
defineProps<{ loading?: boolean; error?: string; empty?: boolean; emptyText?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="state-panel" aria-live="polite" aria-busy="true">
    <el-skeleton :rows="6" animated />
  </div>
  <div v-else-if="error" class="state-panel state-error" role="alert">
    <span class="state-mark">!</span><h3>数据暂时不可用</h3><p>{{ error }}</p>
    <el-button type="primary" plain @click="$emit('retry')">重新加载</el-button>
  </div>
  <div v-else-if="empty" class="state-panel">
    <span class="state-mark state-empty">○</span><h3>{{ emptyText || '暂无数据' }}</h3>
    <p>调整筛选条件，或等待新的数据进入。</p>
  </div>
  <slot v-else />
</template>
