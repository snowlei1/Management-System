<script setup>
import { timeText } from '../api/resourceState.js'
import { computed } from 'vue'
const props = defineProps({ records:{type:Array,default:()=>[]}, pendingSubmissionNo:{type:Number,default:0}, pendingSubmittedAt:{type:String,default:null} })
const rounds = computed(()=>[...props.records].sort((a,b)=>a.submissionNo-b.submissionNo||a.id-b.id))
const showPending = computed(()=>props.pendingSubmissionNo>0&&!rounds.value.some(a=>a.submissionNo===props.pendingSubmissionNo))
</script>
<template>
  <section class="audit-history"><h2>审核流程</h2><p v-if="!rounds.length&&!showPending" class="muted">尚无提交审核记录。</p>
    <p class="field-hint">历史提交轮次以真实审核记录为依据；未保存的历史提交时间不展示，不补造教师修改节点。</p>
    <ol v-if="rounds.length||showPending" class="audit-flow">
      <li v-for="a in rounds" :key="a.id" class="audit-flow-round" :class="a.decision==='REJECT'?'flow-rejected':'flow-approved'">
        <div class="audit-submit-node"><strong>第 {{a.submissionNo}} 次提交</strong><span>第 {{a.submissionNo}} 轮</span></div>
        <section class="audit-decision-node"><h3>{{a.decision==='APPROVE'?'审核通过并发布':'审核驳回'}}</h3><p class="audit-reviewer">审核人：{{a.reviewerName}}</p><time class="audit-event-time">审核时间：{{timeText(a.auditedAt)}}</time><div v-if="a.reason" class="formal-audit-opinion"><span>审核意见（驳回原因）</span><p class="preserve-lines">{{a.reason}}</p></div></section>
      </li>
      <li v-if="showPending" class="audit-flow-round flow-pending"><div class="audit-submit-node"><strong>第 {{pendingSubmissionNo}} 次提交</strong><span>第 {{pendingSubmissionNo}} 轮</span></div><time v-if="pendingSubmittedAt" class="audit-pending-time">提交时间：{{timeText(pendingSubmittedAt)}}</time><p class="audit-waiting">等待管理员审核</p></li>
    </ol>
  </section>
</template>
