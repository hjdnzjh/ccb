<template>
  <div class="tariff-page">
    <header class="panel heading"><div><h2>居民年度计费</h2><p>按结算户累计年度用量，供水费与污水处理费分别计算。</p></div><el-button type="primary" @click="open">新建结算户</el-button></header>
    <el-alert type="info" :closable="false" show-icon title="预置方案来自已核对的杭州资料，适用地区由管理员核实后选择。未绑定水表沿用原演示计费；已生成的历史账单保持原金额。"/>
    <div class="profiles"><article v-for="p in profiles" :key="p.key" class="panel profile"><h3>{{p.name}}</h3><p>年度阶梯：216 / 300 m³</p><p>供水单价：1.90 / 2.85 / 5.70 元/m³</p><strong>污水费 {{p.sewageUnit}} 元/m³</strong><p><a :href="p.source" target="_blank" rel="noopener noreferrer">查看政策资料</a></p></article></div>
    <section class="panel accounts"><div class="tools"><h3>结算户与年度台账</h3><el-button :loading="loading" @click="load">刷新</el-button></div><el-table v-loading="loading" :data="accounts" stripe empty-text="尚未配置结算户，现有水表仍使用原演示方案"><el-table-column prop="name" label="结算户" min-width="170"/><el-table-column prop="username" label="所属用户" width="130"/><el-table-column label="地区方案" min-width="210"><template #default="{row}">{{profileName(row.profile_key)}}</template></el-table-column><el-table-column prop="effective_from" label="生效日期" width="120"/><el-table-column prop="opening_usage" label="首年期初/m³" width="135"/><el-table-column prop="meters" label="绑定水表" min-width="200" show-overflow-tooltip/><el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" @click="inspect(row)">年度台账</el-button></template></el-table-column></el-table><p class="muted">显示最近200个结算户。结算户独立于登录账号，同一户的多表共享额度；不同用水地址应分别建户。当前支持居民标准阶梯，未包含多人口增额、合表价及非居民超定额。</p></section>
    <el-dialog v-model="visible" title="新建居民结算户" width="min(760px,95vw)">
      <el-alert type="warning" :closable="false" title="请核对期初用量依据。生效账期已有账单的水表不能直接切换；绑定后不支持直接改价或改归属。生效前的未出账读数会要求核查，请先完成历史出账。"/>
      <el-form label-position="top" class="editor">
        <el-form-item label="结算户名称"><el-input v-model="form.name" maxlength="100" placeholder="例如：某小区1幢101室"/></el-form-item>
        <el-form-item label="地区收费方案"><el-select v-model="form.profileKey" style="width:100%"><el-option v-for="p in profiles" :key="p.key" :value="p.key" :label="p.name"/></el-select></el-form-item>
        <el-form-item label="绑定水表（须属于同一居民用户）"><el-select v-model="form.meterIds" multiple filterable remote :remote-method="searchMeters" :loading="searching" style="width:100%" placeholder="输入表号搜索未绑定水表"><el-option v-for="m in meters" :key="m.id" :value="m.id" :label="`${m.meter_no} · ${m.real_name || m.username} (${m.username})`"/></el-select></el-form-item>
        <div class="two-fields"><el-form-item label="生效日期"><el-date-picker v-model="form.effectiveFrom" value-format="YYYY-MM-DD" type="date" style="width:100%"/></el-form-item><el-form-item label="生效日前的本年度累计用量/m³"><el-input-number v-model="form.openingUsage" :min="0" :max="999999999" :precision="3" style="width:100%"/></el-form-item></div>
        <el-form-item label="期初用量核对依据"><el-input v-model="form.openingNote" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="填写对应年度、核对资料及用量来源；新开户请注明期初为0的依据"/></el-form-item>
      </el-form><p class="muted">跨年区间需要上一年末23:59:59分界读数；迟到读数不能倒序占用已结算额度。居民预置方案不自动启用违约金。</p>
      <template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存结算户</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="年度用量台账" width="min(760px,95vw)"><template v-if="detail"><el-descriptions :column="1" border><el-descriptions-item label="结算户">{{detail.account.name}}</el-descriptions-item><el-descriptions-item label="方案">{{profileName(detail.account.profile_key)}}</el-descriptions-item><el-descriptions-item label="生效日期">{{detail.account.effective_from}}</el-descriptions-item><el-descriptions-item label="首年期初量">{{detail.account.opening_usage}} m³</el-descriptions-item><el-descriptions-item label="期初依据">{{detail.account.opening_note}}</el-descriptions-item></el-descriptions><el-table :data="detail.years" stripe><el-table-column prop="billing_year" label="年度"/><el-table-column prop="used_amount" label="累计已占用/m³"/><el-table-column prop="last_read_at" label="最近计费读数时间" min-width="190"/></el-table><p class="muted">累计量含核实录入的首年期初与成功出账用量，不等于实际收到的款项；完整价格分档在各账单详情中查看。</p></template></el-dialog>
  </div>
</template>
<script setup>
import {ref,reactive,onMounted} from 'vue'
import dayjs from 'dayjs'
import {ElMessage} from 'element-plus'
import {request} from '@/api'
const profiles=ref([]),accounts=ref([]),meters=ref([]),loading=ref(false),saving=ref(false),searching=ref(false),visible=ref(false),detailVisible=ref(false),detail=ref(null)
const form=reactive({name:'',profileKey:'',meterIds:[],effectiveFrom:dayjs().format('YYYY-MM-DD'),openingUsage:0,openingNote:''})
const profileName=k=>profiles.value.find(p=>p.key===k)?.name || k
async function load(){loading.value=true;try{accounts.value=(await request.get('/tariff/accounts')).data}catch{}finally{loading.value=false}}
let searchVersion=0
async function searchMeters(search=''){const version=++searchVersion;searching.value=true;try{const rows=(await request.get('/tariff/meters',{params:{search}})).data;if(version===searchVersion){const selected=meters.value.filter(m=>form.meterIds.includes(m.id));meters.value=[...new Map([...selected,...rows].map(m=>[m.id,m])).values()]}}catch{}finally{if(version===searchVersion)searching.value=false}}
function open(){Object.assign(form,{name:'',profileKey:'',meterIds:[],effectiveFrom:dayjs().format('YYYY-MM-DD'),openingUsage:0,openingNote:''});visible.value=true;searchMeters()}
async function save(){if(saving.value)return;if(!form.name.trim()||!form.profileKey||!form.meterIds.length||!form.effectiveFrom||!form.openingNote.trim()){ElMessage.warning('请完整填写结算户信息及期初依据');return}saving.value=true;try{await request.post('/tariff/accounts',form);visible.value=false;ElMessage.success('结算户已保存，新账单将按年度累计计价');await load()}catch{}finally{saving.value=false}}
async function inspect(row){try{detail.value=(await request.get(`/tariff/accounts/${row.id}`)).data;detailVisible.value=true}catch{}}
onMounted(async()=>{try{profiles.value=(await request.get('/tariff/profiles')).data;await load()}catch{}})
</script>
<style scoped>
.tariff-page{display:grid;gap:18px;min-width:0}.tariff-page>section{min-width:0}.heading,.tools{display:flex;justify-content:space-between;align-items:center;gap:12px}.heading,.accounts,.profile{padding:20px}.heading p,.muted{font-size:13px;color:var(--color-muted);line-height:1.7;margin-top:8px}.profiles{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}.profile h3{font-size:15px}.profile p{margin:12px 0;font-size:13px}.profile a{color:var(--color-primary)}.tools{margin-bottom:16px}.editor{margin-top:18px}.two-fields{display:grid;grid-template-columns:1fr 1fr;gap:16px}@media(max-width:900px){.profiles,.two-fields{grid-template-columns:1fr}}
</style>
