<template>
  <div class="billing-workspace">
    <header class="billing-header">
      <div><h2>{{ paymentMode ? '缴费管理' : '账单管理' }}</h2><p>查询账单 · 登记部分或全额收款 · 查看收款流水</p></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </header>
    <el-alert v-if="paymentMode" title="线下 / 演示收款登记：选择渠道只记录收款方式，不发起真实扣款。" type="info" :closable="false" show-icon />
    <section class="billing-stats">
      <article v-for="item in statItems" :key="item.key"><span>{{ item.label }}</span><strong>¥{{ formatMoney(stats[item.key]) }}</strong></article>
    </section>
    <div class="table-page">
      <el-form :inline="true" @submit.prevent="search">
        <el-form-item label="账单号"><el-input v-model="filters.billNo" clearable placeholder="输入账单编号" @keyup.enter="search" /></el-form-item>
        <el-form-item label="用户"><el-input v-model="filters.userName" clearable placeholder="姓名、账号或手机号" @keyup.enter="search" /></el-form-item>
        <el-form-item label="账期"><el-date-picker v-model="filters.billPeriod" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width:150px" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部" style="width:140px">
          <el-option v-for="value in [0,1,2,3]" :key="value" :value="value" :label="statusLabel(value)" />
        </el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="error-bar" />
      <el-table v-loading="loading" :data="records" stripe empty-text="当前条件下暂无账单">
        <el-table-column prop="billNo" label="账单编号" min-width="215" show-overflow-tooltip />
        <el-table-column prop="userName" label="用户" min-width="110" />
        <el-table-column prop="billPeriod" label="账期" width="100" />
        <el-table-column label="用水量" width="100"><template #default="{row}">{{ row.usageAmount ?? '—' }} 吨</template></el-table-column>
        <el-table-column label="应付" width="115"><template #default="{row}">¥{{ formatMoney(row.totalAmount) }}</template></el-table-column>
        <el-table-column label="已付" width="115"><template #default="{row}">¥{{ formatMoney(row.paidAmount) }}</template></el-table-column>
        <el-table-column label="剩余应付" width="125"><template #default="{row}"><strong>¥{{ remainingAmount(row) }}</strong></template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{row}"><el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="应缴日期" width="120"><template #default="{row}">{{ row.dueDate ? dayjs(row.dueDate).format('YYYY-MM-DD') : '—' }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="185"><template #default="{row}">
          <el-button type="primary" link @click="detail(row)">详情 / 流水</el-button>
          <el-button v-if="canPay(row)" type="success" link @click="pay(row)">{{ canRetry(row) ? '核对登记' : '登记收款' }}</el-button>
        </template></el-table-column>
      </el-table>
      <div class="billing-pagination"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
        :page-sizes="[10,20,50]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </div>
    <BillPaymentDialog v-model="payOpen" :bill-id="selectedId" @registered="load" />
    <BillDetailDialog v-model="detailOpen" :bill-id="selectedId" />
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import dayjs from 'dayjs'
import { billApi } from '@/api/index.js'
import { formatMoney, remainingAmount, toCents, statusLabel, statusTag } from '@/utils/billing.js'
import BillPaymentDialog from './BillPaymentDialog.vue'
import BillDetailDialog from './BillDetailDialog.vue'
defineProps({paymentMode:Boolean})
const filters=reactive({billNo:'',userName:'',billPeriod:'',status:null})
const records=ref([]),stats=ref({}),page=ref(1),size=ref(10),total=ref(0),loading=ref(false),error=ref('')
const selectedId=ref(null),payOpen=ref(false),detailOpen=ref(false)
const statItems=[{key:'totalAmount',label:'筛选账单应付总额'},{key:'paidAmount',label:'累计已付'},{key:'remainingAmount',label:'剩余应付'},{key:'overdueAmount',label:'其中已到期欠款'}]
let version=0
async function load(){
  const current=++version;loading.value=true;error.value=''
  try{
    const result=await billApi.list({pageNum:page.value,pageSize:size.value,billNo:filters.billNo||undefined,
      userName:filters.userName||undefined,billPeriod:filters.billPeriod||undefined,status:Number.isInteger(filters.status)?filters.status:undefined})
    if(current!==version)return
    records.value=result.data.records||[];total.value=Number(result.data.total||0);stats.value=result.data.stats||{}
  }catch(e){if(current===version){error.value=e.message||'加载失败，请点击刷新重试';records.value=[];stats.value={};total.value=0}}
  finally{if(current===version)loading.value=false}
}
function search(){page.value=1;load()}
function reset(){Object.assign(filters,{billNo:'',userName:'',billPeriod:'',status:null});search()}
function detail(row){selectedId.value=row.id;detailOpen.value=true}
function pay(row){selectedId.value=row.id;payOpen.value=true}
function canRetry(row){return !!sessionStorage.getItem(`water_receipt_pending_${row.id}`)}
function canPay(row){return canRetry(row)||([0,2,3].includes(row.status)&&toCents(remainingAmount(row))>0n)}
onMounted(load)
</script>
<style scoped>
.billing-workspace{display:grid;grid-template-columns:minmax(0,1fr);min-width:0;gap:18px}.table-page{min-width:0}.billing-header{display:flex;justify-content:space-between;align-items:center;gap:16px}
.billing-header h2{margin:0 0 8px}.billing-header p{color:var(--el-text-color-secondary);margin:0}
.billing-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}
.billing-stats article{background:var(--el-bg-color,#fff);border:1px solid var(--el-border-color-lighter);border-radius:12px;padding:20px;display:grid;gap:12px}
.billing-stats span{color:var(--el-text-color-secondary);font-size:13px}.billing-stats strong{font-size:clamp(18px,2vw,26px);color:var(--el-color-primary);overflow-wrap:anywhere}
.billing-pagination{display:flex;justify-content:flex-end;margin-top:20px;overflow:auto}.error-bar{margin-bottom:14px}
@media(max-width:900px){.billing-stats{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
