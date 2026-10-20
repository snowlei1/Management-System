<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { publishedResourceApi } from '../api/resources.js'
import { portalApi } from '../api/portal.js'
import { timeText } from '../api/resourceState.js'
import ResourceCard from '../components/ResourceCard.vue'
import AppIcon from '../components/AppIcon.vue'
import LoadingState from '../components/LoadingState.vue'
import EmptyState from '../components/EmptyState.vue'
const router = useRouter(), keyword = ref(''), loading = ref(true), error = ref(''), latest = ref([]), favorites = ref([]), courses = ref([]), topics = ref([]), history = ref([]), changing = ref(0)
const courseHighlights = computed(() => [...courses.value].sort((a,b) => b.resourceCount-a.resourceCount || a.id-b.id).slice(0,4))
function search() { router.push({ path: '/resources', query: { keyword: keyword.value.trim() } }) }
async function favorite(r) { if (changing.value) return; changing.value = r.id; try { const value = await publishedResourceApi.favorite(r.id, !r.favorite); r.favorite = value.favorite; favorites.value = (await publishedResourceApi.favorites({ size: 3 })).items } catch(e) { error.value = e.message } finally { changing.value = 0 } }
onMounted(async () => { try { const [l,f,c,t,h] = await Promise.all([publishedResourceApi.list({ size: 6 }),publishedResourceApi.favorites({ size: 3 }),portalApi.courses(),portalApi.topics(),portalApi.history('browse',{ size: 5 })]); latest.value=l.items;favorites.value=f.items;courses.value=c;topics.value=t;history.value=h.items } catch(e) { error.value=e.message } finally { loading.value=false } })
</script>
<template>
  <section class="learning-hero" aria-label="课程思政教学资源中心">
    <div class="learning-hero-copy">
      <p class="learning-overline"><span class="learning-dot"/>课程 · 资源 · 思政元素</p>
      <h1>让每一次探索，<br/>都<span>多一点意义。</span></h1>
      <p class="learning-intro">在课程里发现知识，在案例中看见价值。<br/>从一份教学资源，打开新的学习视角。</p>
      <form class="learning-search" @submit.prevent="search"><AppIcon name="search"/><input v-model="keyword" aria-label="搜索教学资源" placeholder="搜索资源标题或简介" maxlength="200"/><button class="primary">检索资源<AppIcon name="arrow" :size="16"/></button></form>
      <div class="learning-hero-links"><span>从这里开始</span><RouterLink to="/course-resources">课程资源<AppIcon name="arrow" :size="13"/></RouterLink><RouterLink to="/ideological-topics">思政专题<AppIcon name="arrow" :size="13"/></RouterLink></div>
    </div>
    <div class="learning-hero-art" aria-hidden="true">
      <span class="learning-art-word">LEARN<br/>BEYOND.</span>
      <div class="learning-sheet learning-sheet-back"/>
      <div class="learning-sheet learning-sheet-front"><div class="learning-sheet-bar"><span>● ● ●</span><span>COURSE / RESOURCE</span></div><AppIcon name="file" :size="28"/><strong>知识之外<br/>看见价值</strong><div class="learning-sheet-rule"/><small>课程案例 · 价值思考 · 实践应用</small><div class="learning-sheet-footer"><span>教学资源</span><AppIcon name="arrow" :size="16"/></div></div>
      <span class="learning-floating-tag"><span/>从好奇心开始</span><small class="learning-art-caption">专业知识 × 价值引领</small>
    </div>
  </section>
  <p v-if="error" class="message error" role="alert">{{ error }}</p><LoadingState v-if="loading"/>
  <template v-else>
    <section class="learning-courses"><div class="learning-course-heading"><h2>从熟悉的课程开始</h2><RouterLink to="/course-resources" class="text-button">全部课程<AppIcon name="arrow" :size="15"/></RouterLink></div>
      <div class="learning-course-links"><RouterLink v-for="c in courseHighlights" :key="c.id" :to="`/course-resources/${c.id}`"><AppIcon name="course" :size="20"/><span><strong>{{c.name}}</strong><small>{{c.resourceCount}} 份已发布资源</small></span><AppIcon name="arrow" :size="15"/></RouterLink></div>
      <p v-if="courses.length" class="field-hint">按已发布资源数量排列，不代表课程质量或推荐。</p><EmptyState v-else title="暂无可用课程"/>
    </section>
    <section class="learning-library"><div class="section-heading"><div><p class="learning-overline">EXPLORE THE LIBRARY</p><h2>好内容，随时打开。</h2><p class="muted">最新审核发布的教学资源</p></div><RouterLink to="/resources" class="text-button">全部资源<AppIcon name="arrow" :size="15"/></RouterLink></div>
      <div class="resource-card-grid"><ResourceCard v-for="r in latest" :key="r.id" :resource="r" visual-cover :changing="!!changing" @favorite="favorite"/></div><EmptyState v-if="!latest.length" title="暂无已发布资源" description="教师资源通过审核后将展示在此处。"/>
    </section>
    <section class="learning-topics"><div class="section-heading"><div><p class="learning-overline">THINK A LITTLE FURTHER</p><h2>换个视角，看见更多。</h2></div><RouterLink to="/ideological-topics" class="text-button">全部思政专题<AppIcon name="arrow" :size="15"/></RouterLink></div>
      <div class="learning-topic-links"><RouterLink v-for="t in topics.slice(0,6)" :key="t.id" :to="`/ideological-topics/${t.id}`"><span class="learning-topic-icon"><AppIcon name="tags" :size="24"/></span><span><strong>{{t.name}}</strong><small>{{t.resourceCount}} 份已发布资源</small></span><AppIcon name="arrow" :size="16"/></RouterLink></div><EmptyState v-if="!topics.length" title="暂无可用专题"/>
    </section>
    <div class="portal-memory">
      <section><div class="section-heading"><h2>最近浏览</h2><RouterLink to="/history/browse" class="text-button">浏览记录</RouterLink></div>
        <RouterLink v-for="h in history" :key="h.id" :to="`/resources/${h.resourceId}`" class="navigation-row"><AppIcon name="history"/><div><strong>{{ h.title }}</strong><small>{{ h.courseName }} · {{ timeText(h.accessedAt) }}</small></div><AppIcon name="chevron" :size="16"/></RouterLink><EmptyState v-if="!history.length" title="暂无浏览记录" description="打开资源详情后会记录一次访问事件。" icon="history"/>
      </section>
      <section><div class="section-heading"><h2>我的收藏</h2><RouterLink to="/favorites" class="text-button">全部收藏</RouterLink></div>
        <RouterLink v-for="r in favorites" :key="r.id" :to="`/resources/${r.id}`" class="inline-resource"><span class="file-icon"><AppIcon name="file"/></span><div><strong>{{ r.title }}</strong><small>{{ r.courseName }} · {{ r.teacherName }}</small></div><AppIcon name="arrow" :size="15"/></RouterLink><EmptyState v-if="!favorites.length" title="暂无有效收藏" description="仅展示当前仍已发布的有效收藏。" icon="favorite"/>
      </section>
    </div>
  </template>
</template>
