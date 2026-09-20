<template>
 <div class="anomaly-page">
  <header class="panel heading"><div><h3>异常记录与处置</h3><p>查询采集和检测生成的真实告警，记录核查结论或转入工单。</p></div><el-button @click="$router.push('/anomaly/workorder')">工单管理</el-button></header>
  <section class="panel content"><el-form inline @submit.prevent="search"><el-form-item label="表号"><el-input v-model="filters.meterNo" placeholder="搜索表号" clearable/></el-form-item><el-form-item label="状态"><el-select v-model="filters.status" clearable style="width:140px"><el-option v-for="(name,idx) in states" :key="idx" :value="idx" :label="name"/></el-select></el-form-item><el-form-item label="严重程度"><el-select v-model="filters.severity" clearable style="width:140px"><el-option v-for="(name,key) in severities" :key="key" :label="name" :value="key"/></el-select></el-form-item><el-button native-type="submit" :loading="loading">查询</el-button></el-form>
   <el-table v-loading="loading" :data="rows" stripe><el-table-column prop="anomaly_no" label="异常编号" min-width="200"/><el-table-column prop="meter_no" label="表号" min-width="160"/><el-table-column label="类型" width="130"><template #default="{row}">{{ types[row.anomaly_type] || row.anomaly_type }}</template></el-table-column><el-table-column label="级别" width="90"><template #default="{row}">{{ severities[row.severity] || row.severity }}</template></el-table-column><el-table-column label="状态" width="90"><template #default="{row}">{{ states[row.status] }}</template></el-table-column><el-table-column prop="description" label="依据" min-width="200" show-overflow-tooltip/><el-table-column prop="detected_time" label="检测时间" min-width="175"/><el-table-column label="操作" fixed="right" width="120"><template #default="{row}"><el-button link type="primary" @click="open(row)">查看 / 处置</el-button></template></el-table-column></el-table>
   <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="total,prev,pager,next" @current-change="load"/>
  </section>
  <el-dialog v-model="visible" title="异常核查与处置" width="min(720px,95vw)"><template v-if="current"><el-descriptions :column="1" border><el-descriptions-item label="异常编号">{{ current.anomaly_no }}</el-descriptions-item><el-descriptions-item label="水表">{{ current.meter_no }}</el-descriptions-item><el-descriptions-item label="状态">{{ states[current.status] }}</el-descriptions-item><el-descriptions-item label="检测依据">{{ current.description }}</el-descriptions-item><el-descriptions-item label="详情">{{ current.detection_detail || '暂无补充数据' }}</el-descriptions-item><el-descriptions-item label="关联工单">{{ current.order_no || '未关联工单' }}</el-descriptions-item><el-descriptions-item label="处置结论">{{ current.handle_result || '尚未结案' }}</el-descriptions-item><el-descriptions-item label="处置时间">{{ current.handled_time || '尚未结案' }}</el-descriptions-item></el-descriptions>
   <template v-if="current.status<2"><el-alert class="note" title="告警是核查线索，结案须填写事实依据。未完成的关联工单需先在工单管理中处理。" type="info" :closable="false"/><el-input v-model="note" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="现场核查情况、处理措施与结论"/><div class="actions"><el-button v-if="!current.work_order_id" :loading="saving" @click="createOrder">生成处置工单</el-button><el-button v-else @click="$router.push('/anomaly/workorder')">查看工单管理</el-button><el-button type="primary" :disabled="!note.trim()" :loading="saving" @click="resolve(2)">确认已处理</el-button><el-button :disabled="!note.trim()" :loading="saving" @click="resolve(3)">关闭告警</el-button></div></template>
  </template></el-dialog>
 </div>
</template>
<script setup>
import {ref,reactive,onMounted} from 'vue'
import {ElMessage} from 'element-plus'
import {request} from '@/api'
import {useNotificationTarget} from '@/utils/notificationTarget.js'
const rows=ref([]),total=ref(0),page=ref(1),loading=ref(false),visible=ref(false),saving=ref(false),current=ref(null),note=ref('')
const filters=reactive({meterNo:'',status:null,severity:''}),states=['待处理','处理中','已处理','已关闭'],severities={critical:'紧急',high:'高',medium:'中',low:'低'}
const types={meter_fault:'设备故障',collection_failure:'补抄失败',night_usage:'夜间用水',high_flow:'高流量',sudden_increase:'用量突增',valve_fault:'阀门异常',leak:'疑似漏水',leakage:'疑似漏水',temperature:'水温异常',device_alarm:'设备报警'}
async function load(){loading.value=true;try{const res=await request.get('/anomaly/list',{params:{...filters,pageNum:page.value,pageSize:10}});rows.value=res.data.records;total.value=res.data.total}finally{loading.value=false}}
function search(){page.value=1;load()}
async function open(row){const res=await request.get(`/anomaly/${row.id}`);current.value=res.data;note.value='';visible.value=true}
async function createOrder(){if(saving.value)return;saving.value=true;try{await request.post(`/anomaly/${current.value.id}/work-order`);await open(current.value);await load();ElMessage.success('已关联真实工单，可前往工单管理派单')}finally{saving.value=false}}
async function resolve(status){if(saving.value)return;saving.value=true;try{await request.post(`/anomaly/${current.value.id}/resolve`,{status,note:note.value});visible.value=false;await load();ElMessage.success('处置结论已保存')}finally{saving.value=false}}
onMounted(()=>load().catch(()=>{}))
useNotificationTarget(open)
</script>
<style scoped>
.anomaly-page{display:grid;gap:16px;min-width:0}.anomaly-page>section,.anomaly-page>header{min-width:0}.heading{display:flex;justify-content:space-between;align-items:center;padding:18px}.heading p{color:var(--color-muted);font-size:13px;margin-top:8px}.content{padding:18px}.el-pagination{margin-top:16px}.note{margin:16px 0}.actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:16px}
</style>
