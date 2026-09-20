<template>
 <div class="lab-page"><header><div><span>可重复实验 · 独立模拟数据</span><h2>创新演练工作台</h2><p>固定阈值、个体基线与候选模型对比。报告保留漏检和失败结果。</p></div><el-button :loading="loading" @click="load">刷新</el-button></header>
 <el-alert title="此处使用独立模拟轨迹，不写业务账单。短期演练用于验证流程；模型训练与完整时间切分需要84天场景。" type="info" :closable="false" show-icon/>
 <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
 <section class="panel"><el-form inline @submit.prevent="create"><el-form-item label="随机种子"><el-input-number v-model="config.seed" :min="0" :max="2147483647"/></el-form-item><el-form-item label="水表数量"><el-input-number v-model="config.meters" :min="1" :max="60"/></el-form-item><el-form-item label="模拟天数"><el-input-number v-model="config.days" :min="1" :max="84"/></el-form-item><el-button type="primary" :loading="saving" native-type="submit">创建独立演练</el-button></el-form>
 <el-table :data="runs" stripe empty-text="暂无演练记录"><el-table-column label="场景"><template #default="{row}">{{row.config?.meters}} 块表 · {{row.config?.days}} 天</template></el-table-column><el-table-column label="创建时间" min-width="170"><template #default="{row}">{{dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss')}}</template></el-table-column><el-table-column label="状态"><template #default="{row}">{{status(row.status)}}</template></el-table-column><el-table-column label="操作"><template #default="{row}"><el-button link type="primary" @click="inspect(row.id)">查看实验</el-button></template></el-table-column></el-table></section>
 <section v-if="selected" class="panel"><h3>实验进度 · {{status(selected.status)}}</h3><el-progress :percentage="Math.round((selected.progress||0)*100)"/><p>{{selected.message || selected.error || '等待任务更新'}}</p><div class="actions"><el-button v-if="selected.status==='running'" @click="control('pause')">暂停</el-button><el-button v-if="selected.status==='paused'" @click="control('resume')">继续</el-button><el-button v-if="selected.status==='paused'" @click="control('step')">推进一个处理检查点</el-button><el-button :disabled="selected.status!=='completed'" type="primary" @click="download">下载完整JSON报告</el-button></div>
 <el-table v-if="selected.summary" :data="summaryRows" stripe><el-table-column prop="group" label="方案"/><el-table-column prop="recall" label="事件召回率"/><el-table-column prop="precision" label="精确率"/><el-table-column prop="falsePositiveMeterDayRate" label="正常表日误报率"/><el-table-column prop="fn" label="漏检事件"/><el-table-column prop="medianDelayMinutes" label="发现中位延迟/分钟"/></el-table>
 <p class="note">A：现有10 m³/h设备规则；B：个体基线；C：候选Isolation Forest。未训练的模型显示“不可用”。当前实验尚未满足效果门槛，不作为启用自动处置的依据；检测指标不等同真实主动复测成本。</p>
 <el-table :data="selected.events||[]" stripe max-height="360"><el-table-column prop="meterId" label="模拟表ID"/><el-table-column label="场景类型"><template #default="{row}">{{scenarioNames[row.kind]||row.kind}}</template></el-table-column><el-table-column prop="start" label="事件起点/分钟"/><el-table-column prop="end" label="事件终点/分钟"/><el-table-column label="维修后结果标签"><template #default="{row}">{{row.kind==='repair_outcomes'?outcomeNames[row.outcome]:'—'}}</template></el-table-column></el-table>
 </section></div>
</template>
<script setup>
import {ref,reactive,computed,onMounted,onBeforeUnmount} from 'vue'
import dayjs from 'dayjs'
import {replayApi} from '@/api/diagnosis.js'
const runs=ref([]),selected=ref(null),error=ref(''),loading=ref(false),saving=ref(false),config=reactive({seed:20260920,meters:6,days:21})
const status=s=>({running:'运行中',paused:'已暂停',completed:'已完成',failed:'失败',interrupted:'服务中断'})[s]||s
const scenarioNames={normal_peaks:'正常用水高峰',night_business:'夜间经营',small_flow:'持续小流量',high_flow:'高流量',communication:'通信缺失',quality:'报文质量异常',temporary_increase:'临时用水增加',repair_outcomes:'维修后观察'}
const outcomeNames={restored:'观测恢复',persistent:'异常持续',missing:'观测缺失'}
const summaryRows=computed(()=>Object.entries(selected.value?.summary||{}).map(([group,value])=>{const row={group};for(const key of ['recall','precision','falsePositiveMeterDayRate','fn','medianDelayMinutes'])row[key]=value?.[key]==null?'不可用':typeof value[key]==='number'&&key!=='fn'&&key!=='medianDelayMinutes'?`${(value[key]*100).toFixed(1)}%`:value[key];return row}))
let timer,disposed=false
async function load(){loading.value=true;try{const r=await replayApi.list();if(!disposed){runs.value=r.data.runs||[];error.value=''}}catch(e){error.value=e.response?.data?.message||e.message}finally{loading.value=false}}
async function inspect(id){try{const r=await replayApi.detail(id);if(!disposed)selected.value=r.data}catch(e){error.value=e.response?.data?.message||e.message}}
async function create(){if(saving.value)return;saving.value=true;try{const r=await replayApi.create({...config});await inspect(r.data.id);await load()}catch(e){error.value=e.response?.data?.message||e.message}finally{saving.value=false}}
async function control(action){try{await replayApi.control(selected.value.id,action);await inspect(selected.value.id)}catch(e){error.value=e.response?.data?.message||e.message}}
async function download(){try{const r=await replayApi.report(selected.value.id);const url=URL.createObjectURL(new Blob([JSON.stringify(r.data,null,2)],{type:'application/json;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=`innovation-${selected.value.id}.json`;a.click();URL.revokeObjectURL(url)}catch(e){error.value=e.message}}
onMounted(()=>{load();timer=setInterval(()=>{if(document.visibilityState==='visible'&&selected.value&&['running','paused'].includes(selected.value.status))inspect(selected.value.id)},3000)})
onBeforeUnmount(()=>{disposed=true;clearInterval(timer)})
</script>
<style scoped>.lab-page{display:grid;gap:18px}.lab-page header{display:flex;justify-content:space-between;align-items:center}.lab-page header span{color:#087f90}.lab-page h2{margin:8px 0}.lab-page header p,.note{color:#6e828b;font-size:13px;line-height:1.8}.panel{min-width:0;background:white;border:1px solid #dfebef;border-radius:16px;padding:22px}.actions{display:flex;gap:12px;margin:18px 0;flex-wrap:wrap}.actions .el-button{margin-left:0}@media(max-width:600px){.panel{padding:14px}}</style>
