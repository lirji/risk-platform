<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])
const props = defineProps<{ labels: string[]; values: number[]; summary: string; type?: 'bar' | 'line' }>()
const root = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined
const render = () => chart?.setOption({
  backgroundColor: 'transparent', tooltip: { trigger: 'axis' },
  grid: { left: 36, right: 16, top: 20, bottom: 30 },
  xAxis: { type: 'category', data: props.labels, axisLabel: { color: '#8495aa' }, axisLine: { lineStyle: { color: '#263a50' } } },
  yAxis: { type: 'value', axisLabel: { color: '#8495aa' }, splitLine: { lineStyle: { color: '#17283a' } } },
  series: [{ type: props.type || 'bar', data: props.values, smooth: true,
    itemStyle: { color: '#20d3b0', borderRadius: [4, 4, 0, 0] }, lineStyle: { color: '#20d3b0', width: 3 }, areaStyle: props.type === 'line' ? { color: 'rgba(32,211,176,.12)' } : undefined }],
})
const resize = () => chart?.resize()
onMounted(() => { if (root.value) { chart = echarts.init(root.value); render(); window.addEventListener('resize', resize) } })
watch(() => [props.labels, props.values], render, { deep: true })
onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose() })
</script>
<template><div ref="root" class="chart" role="img" :aria-label="summary" /><p class="sr-only">{{ summary }}</p></template>
