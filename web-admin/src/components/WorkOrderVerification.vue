<template><section class="verification"><h3>处理效果核验</h3><p v-if="error">{{error}} <el-button link @click="load">重试</el-button></p><p v-if="!rows.length&&!loading&&!error">此工单未关联诊断观察任务。</p><article v-for="row in rows" :key="row.id"><el-tag :type="stateTone(row.state)">{{stateLabel(row.state)}}</el-tag><p>观察开始：{{row.started_at}}<br/>最晚期限：{{row.deadline_at}}</p><p>数据覆盖：{{(Number(row.coverage)*100).toFixed(1)}}%</p><p>{{safeJson(row.result_json).reason || '等待足够的后续观测；工单完成不等同效果恢复。'}}</p><p v-if="row.followup_order_id">已关联复检工单 #{{row.followup_order_id}}</p><el-button v-else-if="['persistent','inconclusive'].includes(row.state)" :loading="saving" @click="followup">确认安排复检</el-button></article></section></template>
<script setup>
import {ref,watch} from 'vue'
import {ElMessageBox,ElMessage} from 'element-plus'
import {diagnosisApi} from '@/api/diagnosis.js'
import {safeJson,stateLabel,stateTone,newRequestKey} from '@/utils/diagnosis.js'
const props=defineProps({orderId:[Number,String]});const rows=ref([]),loading=ref(false),saving=ref(false),error=ref('');let version=0
async function load(){const v=++version;loading.value=true;error.value='';try{const r=await diagnosisApi.verifications(props.orderId);if(v===version)rows.value=r.data||[]}catch(e){if(v===version)error.value='核验记录加载失败'}finally{if(v===version)loading.value=false}}
async function followup(){try{const {value}=await ElMessageBox.prompt('填写复检依据，原工单与原处置结论将保留。','确认安排复检',{inputType:'textarea',inputValidator:v=>!!v?.trim()&&v.length<=1000||'请输入1至1000字'});saving.value=true;await diagnosisApi.followup(props.orderId,{action:'followup',note:value,requestKey:newRequestKey()});ElMessage.success('已创建关联复检工单');await load()}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message||'复检未完成'}finally{saving.value=false}}
watch(()=>props.orderId,id=>{rows.value=[];if(id)load()},{immediate:true})
</script>
<style scoped>.verification{margin-top:22px;line-height:1.8}.verification article{padding:16px;border:1px solid #d7eaee;border-radius:12px;margin:12px 0}.verification p{font-size:13px;color:#5f7882}</style>
