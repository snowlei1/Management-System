<script setup>
import { ref,computed,watch } from 'vue'
import { portalApi } from '../api/portal.js'
import { timeText } from '../api/resourceState.js'
import AppIcon from '../components/AppIcon.vue'
import EmptyState from '../components/EmptyState.vue'
import LoadingState from '../components/LoadingState.vue'
const props=defineProps({kind:{type:String,required:true}}),items=ref([]),total=ref(0),page=ref(1),loading=ref(false),error=ref(''),size=10
const downloads=computed(()=>props.kind==='downloads'),pages=computed(()=>Math.max(1,Math.ceil(total.value/size)))
let sequence=0
async function load(){const current=++sequence;loading.value=true;error.value='';try{const r=await portalApi.history(props.kind,{page:page.value,size});if(current===sequence){items.value=r.items;total.value=r.total}}catch(e){if(current===sequence)error.value=e.message}finally{if(current===sequence)loading.value=false}}
watch(()=>props.kind,()=>{page.value=1;load()},{immediate:true})
</script>
<template><section class="page-heading heading-row"><div><p class="eyebrow">我的资源使用</p><h1>{{downloads?'下载记录':'最近浏览'}}</h1><p class="muted">{{downloads?'记录已通过后端校验并准备返回文件的下载请求，不代表客户端一定完整保存。':'每次成功打开或刷新详情对应一次事件，重复访问可显示多条；不是访问人数。'}}仅展示当前仍已发布的资源。</p></div><span class="count-pill">{{loading ? '—' : total}} 条记录</span></section><p v-if="error" class="message error" role="alert">{{error}}</p><LoadingState v-if="loading"/>
<section v-else class="card"><div v-for="h in items" :key="h.id" class="history-row"><span class="file-icon"><AppIcon :name="downloads?'download':'history'" :size="23"/></span><div><RouterLink class="history-title" :to="`/resources/${h.resourceId}`">{{h.title}}</RouterLink><p>{{h.courseName}} · {{h.fileOriginalName}} · {{h.fileMimeType}}</p></div><time>{{timeText(h.accessedAt)}}</time><RouterLink class="secondary" :to="`/resources/${h.resourceId}`">查看资源</RouterLink></div><EmptyState v-if="!items.length" :icon="downloads?'download':'history'" :title="downloads?'暂无下载记录':'暂无浏览记录'" description="使用已发布资源后，相关事件会显示在这里。"/>
<div class="pagination"><span>共 {{loading ? '—' : total}} 条 · 第 {{page}} / {{pages}} 页</span><div><button class="secondary" :disabled="page<=1||loading" @click="page--;load()">上一页</button><button class="secondary" :disabled="page>=pages||loading" @click="page++;load()">下一页</button></div></div></section></template>
