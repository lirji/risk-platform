<script setup lang="ts">
defineProps<{ label: string; value: string | number; hint: string; tone?: 'teal' | 'amber' | 'red' }>()
</script>
<template>
  <article class="metric-card" :class="`metric-${tone || 'teal'}`">
    <div class="metric-label">{{ label }}</div><strong>{{ value }}</strong><p>{{ hint }}</p>
  </article>
</template>
