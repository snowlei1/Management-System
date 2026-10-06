<script setup>
import { onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart, BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([PieChart, BarChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])
const props = defineProps({ data: { type: Object, required: true }, indexes: { type: Array, default: () => [0,1,2,3] } })
const roots = ref([]), instances = []; let observer
const labels = ['资源状态概览', '课程资源分布', '资源分类分布', '思政元素关联分布']
const tooltip = { backgroundColor:'#fffdfa', borderColor:'#d9c8b4', textStyle:{color:'#544638',fontSize:12}, extraCssText:'box-shadow:0 4px 14px #654a2514;border-radius:6px;' }
const axisLabel = { color:'#95836f', fontSize:10 }
function render() {
  const state = props.data.resources
  const states = [{ name: '草稿', value: state.draft }, { name: '待审核', value: state.pending }, { name: '已驳回', value: state.rejected }, { name: '已发布', value: state.approved }]
  instances[0]?.setOption({ color: ['#92908b', '#cc943e', '#a84031', '#47785a'], tooltip: { ...tooltip, trigger: 'item' }, legend: { bottom:0, icon:'circle', itemWidth:8, itemHeight:8, textStyle:axisLabel }, series: [{ type: 'pie', radius: ['48%', '70%'], center: ['50%', '43%'], label: { show: false }, itemStyle:{borderColor:'#fff',borderWidth:2}, data: states }] }, true)
  for (let i = 1; i < 4; i++) {
    const rows = props.data[['courseDistribution', 'categoryDistribution', 'elementDistribution'][i - 1]].filter(r => r.resourceCount > 0).slice(0, 6).reverse()
    instances[i]?.setOption({ color: [i===1?'#9b5545':i===2?'#b3914b':'#47785a'], tooltip: { ...tooltip, trigger:'axis', axisPointer:{type:'shadow',shadowStyle:{color:'#f3ece2'}} }, grid: { left:8, right:25, top:15, bottom:20, containLabel:true }, xAxis: { type:'value', minInterval:1, axisLabel, splitLine:{lineStyle:{color:'#eee6dc'}}, axisLine:{show:false}, axisTick:{show:false} }, yAxis: { type:'category', data:rows.map(r=>r.name), axisLabel:{...axisLabel,width:105,overflow:'truncate'}, axisTick:{show:false}, axisLine:{show:false} }, series: [{ type:'bar', barMaxWidth:16, data:rows.map(r=>r.resourceCount), label:{show:true,position:'right',color:'#826b50',fontSize:11}, itemStyle:{borderRadius:[0,2,2,0]} }] }, true)
  }
}
onMounted(async () => { await nextTick(); roots.value.forEach((el,index) => { if(el) instances[index]=echarts.init(el) }); render(); observer = new ResizeObserver(() => instances.forEach(c => c?.resize())); roots.value.forEach(el => { if(el) observer.observe(el) }) })
watch(() => props.data, render)
onBeforeUnmount(() => { observer?.disconnect(); instances.forEach(c => c?.dispose()) })
</script>
<template><div class="chart-grid" :class="{ 'single-chart':indexes.length===1,'three-charts':indexes.length===3 }"><section v-for="index in indexes" :key="index" class="card chart-card"><h2>{{labels[index]}}</h2><p class="field-hint">{{index ? '前6个非零项，完整数据见统计表。' : '当前未软删除资源；已发布须具有发布时间。'}}</p><div :ref="el=>roots[index]=el" class="chart-canvas" role="img" :aria-label="labels[index]"/></section></div></template>
