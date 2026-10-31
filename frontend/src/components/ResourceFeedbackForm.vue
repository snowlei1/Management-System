<script setup>
import {ref,watch} from 'vue'
import {governanceApi} from '../api/enhancements.js'
const props=defineProps({resourceId:{type:Number,required:true}})
const open=ref(false),kind=ref('FILE'),description=ref(''),busy=ref(false),error=ref(''),success=ref(false)
watch(()=>props.resourceId,()=>{open.value=false;description.value='';error.value='';success.value=false})
async function submit(){
  if(busy.value)return
  const id=props.resourceId
  busy.value=true;error.value=''
  try{await governanceApi.report(id,{kind:kind.value,description:description.value});if(id===props.resourceId){success.value=true;open.value=false;description.value=''}}
  catch(e){if(id===props.resourceId)error.value=e.message}
  finally{busy.value=false}
}
</script>
<template>
  <section class="feedback-block">
    <div class="section-heading"><div><h2>资源问题反馈</h2><p class="field-hint">文件、内容、分类或使用授权问题交由管理员核查，不公开展示个人反馈。</p></div><button class="secondary" :disabled="busy" @click="open=!open">{{open?'收起':'反馈问题'}}</button></div>
    <p v-if="success" class="message success" role="status">反馈已提交，可在“我的反馈”查看处理结果。</p>
    <p v-if="error" class="message error" role="alert">{{error}}</p>
    <form v-if="open" class="feedback-form" @submit.prevent="submit">
      <label for="feedback-kind">问题类型</label><select id="feedback-kind" v-model="kind" :disabled="busy"><option value="FILE">附件 / 预览问题</option><option value="CONTENT">内容问题</option><option value="CLASSIFICATION">分类 / 标注问题</option><option value="RIGHTS">来源 / 授权问题</option><option value="OTHER">其他问题</option></select>
      <label for="feedback-description">问题说明</label><textarea id="feedback-description" v-model="description" required maxlength="1000" rows="4" :disabled="busy" placeholder="请说明具体问题，勿填写个人敏感信息。"/>
      <button class="primary" :disabled="busy||!description.trim()">{{busy?'提交中…':'提交反馈'}}</button>
    </form>
    <RouterLink class="link-button" to="/my-feedback">我的反馈与处理结果 →</RouterLink>
  </section>
</template>
<style scoped>
.feedback-block{padding:24px 0;border-top:1px solid var(--border,#e5e2dc);margin-top:24px}.feedback-form{display:grid;gap:10px;max-width:700px;margin:16px 0}.feedback-form select{max-width:300px}.feedback-form button{justify-self:start}.feedback-block>.link-button{display:inline-block;margin-top:12px}
</style>
