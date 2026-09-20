<template><section id="diagnosis" class="panel-section"><header><h3>我的用水异常进度</h3><el-button :loading="loading" @click="load">刷新诊断</el-button></header><el-alert v-if="error" :title="error" type="error" :closable="false"/><el-table :data="rows" empty-text="暂无需要您关注的诊断事件"><el-table-column prop="meterNo" label="水表" min-width="160"/><el-table-column label="进度" width="140"><template #default="{row}">{{stateLabel(row.state)}}</template></el-table-column><el-table-column prop="summary" label="说明" min-width="250"/><el-table-column label="处理效果" width="130"><template #default="{row}">{{row.verificationState?stateLabel(row.verificationState):'暂无核验结果'}}</template></el-table-column></el-table><el-pagination v-if="total>20" v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next" @current-change="load"/><p>异常提醒属于核查线索，不能单独证明漏水。您可以通过下方反馈入口补充情况。</p></section></template>
<script setup>
import {ref,onMounted} from 'vue'
import {diagnosisApi} from '@/api/diagnosis.js'
import {stateLabel} from '@/utils/diagnosis.js'
const rows=ref([]),page=ref(1),total=ref(0),loading=ref(false),error=ref('')
async function load(){loading.value=true;error.value='';try{const r=await diagnosisApi.myCases({page:page.value,size:20});rows.value=r.data.records;total.value=r.data.total}catch{error.value='诊断进度暂时无法加载，请重试'}finally{loading.value=false}}
onMounted(load)
</script>
<style scoped>section{background:white;border:1px solid #e1ebee;border-radius:12px;padding:22px;min-width:0}header{display:flex;justify-content:space-between;align-items:center}p{font-size:13px;color:#6e828b}</style>
