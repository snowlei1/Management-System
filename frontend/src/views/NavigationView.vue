<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { portalApi } from '../api/portal.js'
import AppIcon from '../components/AppIcon.vue'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'
import ResourceLibraryView from './ResourceLibraryView.vue'
const props=defineProps({topics:Boolean}),route=useRoute(),items=ref([]),detail=ref(null),loading=ref(false),error=ref(''),keyword=ref('')
const base=computed(()=>props.topics?'/ideological-topics':'/course-resources'),filtered=computed(()=>items.value.filter(x=>(x.name+(x.code||'')+(x.description||'')).toLowerCase().includes(keyword.value.trim().toLowerCase())))
let sequence=0
watch(()=>[props.topics,route.params.id],async()=>{const current=++sequence;loading.value=true;error.value='';detail.value=null;try{if(route.params.id){const value=await portalApi.navigation(props.topics,route.params.id);if(current===sequence)detail.value=value}else{const value=await(props.topics?portalApi.topics():portalApi.courses());if(current===sequence)items.value=value}}catch(e){if(current===sequence)error.value=e.message}finally{if(current===sequence)loading.value=false}},{immediate:true})
</script>
<template>
  <section class="page-heading heading-row" :class="{ 'topic-banner':topics }">
    <div><p class="eyebrow">{{topics?'课程思政 / 专题栏目':'教学资源 / 课程导航'}}</p><h1>{{detail ? detail.item.name : topics ? '课程思政专题资源' : '课程资源导航'}}</h1><p class="muted">{{detail?.item.description || (topics ? '围绕课程思政元素组织教学材料，跨课程查阅相关资源。' : '按课程查阅已发布教学材料及其关联的思政元素。')}}</p></div>
    <RouterLink v-if="route.params.id" class="secondary" :to="base">返回{{topics?'专题':'课程'}}列表</RouterLink><span v-else class="count-pill">{{loading ? '—' : items.length}} 个{{topics?'专题':'课程'}}</span>
  </section>
  <p v-if="error" class="message error" role="alert">{{error}}</p><LoadingState v-if="loading"/>
  <template v-else-if="detail">
    <section v-if="!topics" class="card navigation-detail"><span class="file-icon"><AppIcon name="course" :size="28"/></span><div><h2>课程信息</h2><p>{{detail.item.code}} · {{detail.item.resourceCount}} 份已发布资源</p><div class="tag-list"><span class="muted">相关思政元素</span><RouterLink v-for="n in detail.related" :key="n.id" class="tag" :to="`/ideological-topics/${n.id}`">{{n.name}} · {{n.resourceCount}}</RouterLink><span v-if="!detail.related.length" class="muted">暂无已发布关联</span></div></div></section>
    <section v-else class="topic-related"><div class="section-heading"><h2>关联课程</h2><span class="count-pill">{{detail.item.resourceCount}} 份已发布资源</span></div><div class="tag-list"><RouterLink v-for="n in detail.related" :key="n.id" class="tag" :to="`/course-resources/${n.id}`">{{n.name}} · {{n.resourceCount}} 份</RouterLink><span v-if="!detail.related.length" class="muted">暂无已发布关联课程</span></div></section>
    <div class="section-heading"><h2>{{topics?'专题教学资源':'课程教学资源'}}</h2><span class="muted">仅展示审核通过且已发布的资源</span></div>
    <ResourceLibraryView :key="route.fullPath" embedded :fixed-course="topics?undefined:route.params.id" :fixed-element="topics?route.params.id:undefined"/>
  </template>
  <template v-else-if="!error">
    <section class="card navigation-search"><AppIcon name="search"/><input v-model="keyword" :aria-label="topics?'检索专题':'检索课程'" :placeholder="topics?'输入元素名称或说明':'输入课程编号、名称或简介'"/></section>
    <div class="navigation-grid" :class="{ 'topic-grid':topics }">
      <RouterLink v-for="(n,index) in filtered" :key="n.id" :to="`${base}/${n.id}`" class="navigation-card" :class="topics?'topic-card':'course-card'">
        <template v-if="topics"><span class="topic-ordinal" aria-hidden="true">{{String(index+1).padStart(2,'0')}}</span><h2>{{n.name}}</h2><p class="clamp-2">{{n.description||'围绕该元素查阅课程教学材料。'}}</p><footer><span><strong>{{n.resourceCount}}</strong> 份已发布资源</span><span>进入专题 <AppIcon name="arrow" :size="17"/></span></footer></template>
        <template v-else><div><span class="course-code">{{n.code}}</span><span class="count-pill">{{n.resourceCount}} 份资源</span></div><h2>{{n.name}}</h2><p class="clamp-3">{{n.description||'暂无课程简介。'}}</p><p class="course-footnote">进入课程查看相关思政元素与教学材料</p><footer>查看课程资源<AppIcon name="arrow" :size="17"/></footer></template>
      </RouterLink>
    </div><EmptyState v-if="!filtered.length" title="暂无符合条件的导航内容" description="这里仅展示启用课程或启用思政元素。"/>
  </template>
</template>
