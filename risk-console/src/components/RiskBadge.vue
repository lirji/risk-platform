<script setup lang="ts">
const props = defineProps<{ level: string }>()
const kind = ['REJECT', 'CRITICAL', 'HIGH'].includes(props.level) ? 'danger' : props.level === 'MEDIUM' ? 'warning' : 'success'
</script>
<template><el-tag :type="kind" effect="dark" round>{{ level }}</el-tag></template>
