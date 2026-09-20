<template>
  <el-dialog :model-value="modelValue" title="账单详情与收款流水" width="min(820px, 94vw)" @update:model-value="emit('update:modelValue',$event)">
    <div v-loading="loading" style="min-height:120px">
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-button v-if="error" @click="load" style="margin-top:12px">重新加载</el-button>
      <template v-if="bill">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="账单号">{{ bill.billNo }}</el-descriptions-item>
          <el-descriptions-item label="账期">{{ bill.billPeriod }}</el-descriptions-item>
          <el-descriptions-item label="起止读数">{{ bill.startReading ?? '—' }} → {{ bill.endReading ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="用水量">{{ bill.usageAmount ?? '—' }} 吨</el-descriptions-item>
          <el-descriptions-item label="水费">¥{{ formatMoney(bill.waterFee) }}</el-descriptions-item>
          <el-descriptions-item label="污水费">¥{{ formatMoney(bill.sewageFee) }}</el-descriptions-item>
          <el-descriptions-item label="违约金">¥{{ formatMoney(bill.penalty) }}</el-descriptions-item>
          <el-descriptions-item label="优惠">¥{{ formatMoney(bill.discount) }}</el-descriptions-item>
          <el-descriptions-item label="应付总额">¥{{ formatMoney(bill.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="累计已付">¥{{ formatMoney(bill.paidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="剩余应付">¥{{ remainingAmount(bill) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(bill.status) }}</el-descriptions-item>
          <el-descriptions-item label="应付日期">{{ date(bill.dueDate) }}</el-descriptions-item>
          <el-descriptions-item label="最近登记">{{ date(bill.paidTime) }}</el-descriptions-item>
        </el-descriptions>
        <template v-if="annual">
          <h4 style="margin:20px 0 12px">年度阶梯计价依据</h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="结算户">{{annual.accountName}}</el-descriptions-item><el-descriptions-item label="计费年度">{{annual.year}}</el-descriptions-item>
            <el-descriptions-item label="地区方案" :span="2">{{annual.profileName}}</el-descriptions-item><el-descriptions-item label="此前年度累计">{{annual.beforeUsage}} m³</el-descriptions-item><el-descriptions-item label="本次后年度累计">{{annual.afterUsage}} m³</el-descriptions-item>
            <el-descriptions-item label="污水单位费额">{{annual.sewageUnit}} 元/m³</el-descriptions-item><el-descriptions-item label="污水费">¥{{formatMoney(annual.sewageAmount)}}</el-descriptions-item>
          </el-descriptions>
          <el-table :data="annual.tiers" stripe><el-table-column prop="tier" label="阶梯"/><el-table-column prop="quantity" label="本次分档用量/m³"/><el-table-column prop="unitPrice" label="供水单价/元"/><el-table-column label="供水费/元"><template #default="{row}">{{formatMoney(row.amount)}}</template></el-table-column></el-table>
          <p class="history-note">{{annual.rounding}}。这是出账时保存的计价明细，不随当前配置变更。</p>
        </template>
        <p v-else class="history-note">此账单没有居民年度方案快照，保留原历史或演示计费结果。</p>
        <h4 style="margin:20px 0 12px">收款登记流水</h4>
        <p class="history-note">{{ payments.length ? '流水仅记录本系统登记，不代表支付机构回执。历史账单的已付金额可能没有对应流水。' : '暂无收款流水。历史已付汇总保留，不补造历史交易。' }}</p>
        <el-table :data="payments" stripe empty-text="暂无收款登记">
          <el-table-column prop="tradeNo" label="请求 / 流水号" min-width="240" show-overflow-tooltip />
          <el-table-column label="金额" width="110"><template #default="{row}">¥{{ formatMoney(row.amount) }}</template></el-table-column>
          <el-table-column label="渠道" width="100"><template #default="{row}">{{ channelLabel(row.payMethod) }}</template></el-table-column>
          <el-table-column label="登记时间" width="170"><template #default="{row}">{{ date(row.paidTime) }}</template></el-table-column>
        </el-table>
      </template>
    </div>
    <template #footer><el-button @click="emit('update:modelValue',false)">关闭</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, watch, computed } from 'vue'
import dayjs from 'dayjs'
import { billApi } from '@/api/index.js'
import { formatMoney, remainingAmount, statusLabel, channelLabel } from '@/utils/billing.js'
const props=defineProps({modelValue:Boolean,billId:[Number,String],api:{type:Object,default:()=>billApi}})
const emit=defineEmits(['update:modelValue'])
const bill=ref(null),payments=ref([]),loading=ref(false),error=ref('')
const annual=computed(()=>{try{const value=JSON.parse(bill.value?.ladderDetail || 'null');return value?.schema==='residential-annual-v1'?value:null}catch{return null}})
const date=value=>value?dayjs(value).format('YYYY-MM-DD HH:mm:ss'):'—'
let version=0
async function load(){
  const current=++version
  loading.value=true; error.value=''; bill.value=null; payments.value=[]
  try {
    const [detail,history]=await Promise.all([props.api.getDetail(props.billId),props.api.getPayments(props.billId)])
    if(current!==version)return
    if(detail?.id==null)throw new Error('账单不存在')
    remainingAmount(detail)
    bill.value=detail; payments.value=history.data||[]
  }catch(e){if(current===version)error.value=e.message||'详情加载失败'}
  finally{if(current===version)loading.value=false}
}
watch(()=>[props.modelValue,props.billId],([open])=>{if(open&&props.billId!=null)load();else ++version})
</script>
<style scoped>.history-note{font-size:13px;color:var(--el-text-color-secondary);margin-bottom:12px}</style>
