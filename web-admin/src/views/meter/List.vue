<template>
  <div class="asset-page">
    <header class="panel heading"><div><h3>水表资产</h3><p>维护真实水表档案；累计读数由已审核抄表更新，停用保留历史。</p></div><el-button type="primary" @click="open()">新增水表</el-button></header>
    <section class="panel content">
      <el-form inline @submit.prevent="search"><el-form-item label="表号"><el-input v-model="filters.meterNo" clearable placeholder="搜索表号" /></el-form-item><el-form-item label="状态"><el-select v-model="filters.status" clearable style="width:140px"><el-option v-for="(name,idx) in statuses" :key="idx" :label="name" :value="idx" /></el-select></el-form-item><el-form-item label="通信"><el-select v-model="filters.commType" clearable style="width:140px"><el-option v-for="type in comms" :key="type" :value="type" /></el-select></el-form-item><el-button native-type="submit" :loading="loading">查询</el-button><el-button @click="$router.push('/automation')">采集与补抄</el-button></el-form>
      <el-table v-loading="loading" :data="rows" stripe><el-table-column prop="meterNo" label="表号" min-width="175"/><el-table-column label="用户" min-width="120"><template #default="{row}">{{ userName(row.userId) }}</template></el-table-column><el-table-column label="区域" min-width="120"><template #default="{row}">{{ areaName(row.areaId) }}</template></el-table-column><el-table-column prop="commType" label="通信" width="90"/><el-table-column prop="currentReading" label="累计读数/m³" width="130"/><el-table-column label="状态" width="90"><template #default="{row}">{{ statuses[row.status] || '未知' }}</template></el-table-column><el-table-column prop="lastReadingTime" label="最后抄表" min-width="175"/><el-table-column label="操作" fixed="right" width="160"><template #default="{row}"><el-button link type="primary" @click="detail(row)">详情</el-button><el-button link @click="open(row)">编辑档案</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="total,prev,pager,next" @current-change="load"/>
    </section>
    <el-dialog v-model="visible" :title="form.id ? '编辑水表档案' : '新增水表'" width="min(600px,95vw)">
      <el-alert title="读数不在档案表单修改。已有业务记录的水表不可直接改表号或转户；停用后历史仍可查询。" type="info" :closable="false"/>
      <el-form label-width="95px" class="editor"><el-form-item label="表号"><el-input v-model="form.meterNo" :disabled="!!form.id" maxlength="50"/></el-form-item><el-form-item label="用户"><el-select v-model="form.userId" filterable :disabled="!!form.id" style="width:100%"><el-option v-for="u in options.users" :key="u.id" :value="u.id" :label="`${u.real_name || u.username} (${u.username})`"/></el-select></el-form-item><el-form-item label="区域"><el-select v-model="form.areaId" filterable style="width:100%"><el-option v-for="a in options.areas" :key="a.id" :value="a.id" :label="a.area_name"/></el-select></el-form-item><el-form-item label="类型"><el-select v-model="form.meterType"><el-option label="电子表" value="digital"/><el-option label="指针表" value="pointer"/><el-option label="字轮表" value="wheel"/></el-select></el-form-item><el-form-item label="通信方式"><el-select v-model="form.commType"><el-option v-for="t in comms" :key="t" :value="t"/></el-select></el-form-item><el-form-item label="状态"><el-select v-model="form.status"><el-option v-for="(name,idx) in statuses" :key="idx" :label="name" :value="idx"/></el-select></el-form-item><el-form-item label="安装地址"><el-input v-model="form.installAddress" maxlength="255"/></el-form-item><el-form-item label="生产厂家"><el-input v-model="form.manufacturer" maxlength="100"/></el-form-item></el-form>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存档案</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="水表详情" width="min(680px,95vw)"><el-descriptions v-if="selected" :column="1" border><el-descriptions-item v-for="(label,key) in detailLabels" :key="key" :label="label">{{ selected[key] ?? '暂无记录' }}</el-descriptions-item></el-descriptions><p class="muted">累计读数通过抄表流程更新。需要采集或录入时，请使用自动采集与七字段报文页面。</p></el-dialog>
  </div>
</template>
<script setup>
import {ref,reactive,onMounted} from 'vue'
import {ElMessage} from 'element-plus'
import {request} from '@/api'
const rows=ref([]),total=ref(0),page=ref(1),loading=ref(false),saving=ref(false),visible=ref(false),detailVisible=ref(false),selected=ref(null)
const filters=reactive({meterNo:'',status:null,commType:''}),options=reactive({users:[],areas:[]}),form=reactive({})
const statuses=['正常','故障','停用','更换'],comms=['NB-IoT','LoRa','4G','wired']
const detailLabels={meterNo:'表号',meterType:'类型',commType:'通信方式',userId:'用户ID',areaId:'区域ID',installAddress:'安装地址',manufacturer:'生产厂家',currentReading:'当前累计读数',lastReading:'上次累计读数',lastReadingTime:'最后抄表',batteryLevel:'电量',signalStrength:'信号强度'}
const userName=id=>{const u=options.users.find(x=>x.id===id);return u?.real_name || u?.username || `用户#${id ?? '未绑定'}`}
const areaName=id=>options.areas.find(x=>x.id===id)?.area_name || `区域#${id ?? '未分区'}`
async function load(){loading.value=true;try{const res=await request.get('/meter/list',{params:{...filters,pageNum:page.value,pageSize:10}});rows.value=res.data.records;total.value=res.data.total}finally{loading.value=false}}
function search(){page.value=1;load()}
function open(row){Object.keys(form).forEach(k=>delete form[k]);Object.assign(form,{meterNo:'',meterType:'digital',commType:'NB-IoT',userId:null,areaId:null,installAddress:'',manufacturer:'',status:0},row || {});visible.value=true}
async function detail(row){const res=await request.get(`/meter/${row.id}`);selected.value=res.data;detailVisible.value=true}
async function save(){if(saving.value)return;saving.value=true;try{const body=Object.fromEntries(['meterNo','meterType','commType','userId','areaId','installAddress','manufacturer','status'].map(k=>[k,form[k]]));if(form.id)await request.put(`/meter/${form.id}`,body);else await request.post('/meter',body);ElMessage.success('档案已保存');visible.value=false;await load()}finally{saving.value=false}}
onMounted(async()=>{try{const res=await request.get('/meter/options');Object.assign(options,res.data);await load()}catch{}})
</script>
<style scoped>
.asset-page{display:grid;gap:16px;min-width:0}.asset-page>section,.asset-page>header{min-width:0}.heading{display:flex;justify-content:space-between;align-items:center;padding:18px}.heading p,.muted{font-size:13px;color:var(--color-muted);margin-top:8px}.content{padding:18px}.el-pagination{margin-top:16px}.editor{margin-top:18px}
</style>
