<script setup>
import { computed } from 'vue'
const props = defineProps({ text: String, keyword: String })
const parts = computed(() => {
  const value = props.text || '', query = (props.keyword || '').trim().slice(0,200)
  if (!query) return [{text:value,match:false}]
  const result=[], lower=value.toLowerCase(), needle=query.toLowerCase()
  let start=0, at
  while((at=lower.indexOf(needle,start))>=0){if(at>start)result.push({text:value.slice(start,at),match:false});result.push({text:value.slice(at,at+query.length),match:true});start=at+query.length}
  if(start<value.length)result.push({text:value.slice(start),match:false})
  return result
})
</script>
<template><template v-for="(part,index) in parts" :key="index"><mark v-if="part.match">{{part.text}}</mark><template v-else>{{part.text}}</template></template></template>
<style scoped>mark { color:inherit; background:#e7edab; border-radius:2px; padding:0 1px; }</style>
