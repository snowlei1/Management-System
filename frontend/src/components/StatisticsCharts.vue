<script setup>
import { onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([PieChart, BarChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])
const props = defineProps({ data: { type: Object, required: true } })
const roots = ref([]), instances = []; let observer
const labels = ['资源状态概览', '课程资源分布', '资源分类分布', '思政元素关联分布']
function render() {
  const state = props.data.resources
  const states = [{ name: '草稿', value: state.draft }, { name: '待审核', value: state.pending }, { name: '已驳回', value: state.rejected }, { name: '已发布', value: state.approved }]
  instances[0]?.setOption({ color: ['#8fa3b4', '#e8a03e', '#d96363', '#27a38a'], tooltip: { trigger: 'item' }, legend: { bottom: 0 }, series: [{ type: 'pie', radius: ['48%', '70%'], center: ['50%', '43%'], label: { show: false }, data: states }] }, true)
  for (let i = 1; i < 4; i++) {
    const rows = props.data[['courseDistribution', 'categoryDistribution', 'elementDistribution'][i - 1]].filter(r => r.resourceCount > 0).slice(0, 6).reverse()
    instances[i]?.setOption({ color: [i === 3 ? '#8a78bf' : '#258f95'], tooltip: { trigger: 'axis' }, grid: { left: 12, right: 25, top: 15, bottom: 20, containLabel: true }, xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf1f4' } } }, yAxis: { type: 'category', data: rows.map(r => r.name), axisLabel: { width: 115, overflow: 'truncate' }, axisTick: { show: false } }, series: [{ type: 'bar', barMaxWidth: 20, data: rows.map(r => r.resourceCount), label: { show: true, position: 'right' }, itemStyle: { borderRadius: [0, 4, 4, 0] } }] }, true)
  }
}
onMounted(async () => { await nextTick(); roots.value.forEach(el => instances.push(echarts.init(el))); render(); observer = new ResizeObserver(() => instances.forEach(c => c.resize())); roots.value.forEach(el => observer.observe(el)) })
watch(() => props.data, render)
onBeforeUnmount(() => { observer?.disconnect(); instances.forEach(c => c.dispose()) })
</script>
<template><div class="chart-grid"><section v-for="(label, index) in labels" :key="label" class="card chart-card"><h2>{{ label }}</h2><p class="field-hint">{{ index ? '展示前6个非零项，完整数据见统计表；不是推荐或综合评分。' : '当前未软删除资源；已发布须具有发布时间。' }}</p><div :ref="el => roots[index] = el" class="chart-canvas" role="img" :aria-label="label"/></section></div></template>
