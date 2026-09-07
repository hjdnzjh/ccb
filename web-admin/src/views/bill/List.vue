<template>
  <div class="table-page">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="账单编号">
          <el-input v-model="searchForm.billNo" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="用户">
          <el-input v-model="searchForm.userName" placeholder="用户名/手机号" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="未支付" :value="0" />
            <el-option label="已支付" :value="1" />
            <el-option label="已逾期" :value="2" />
            <el-option label="部分支付" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="账期">
          <el-date-picker
            v-model="searchForm.billPeriod"
            type="month"
            placeholder="选择月份"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <!-- 统计卡片 -->
    <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 16px;">
      <div class="stat-card">
        <div class="stat-label">本期应收</div>
        <div class="stat-value">¥{{ formatNumber(stats.totalAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已收金额</div>
        <div class="stat-value success">¥{{ formatNumber(stats.paidAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">欠费金额</div>
        <div class="stat-value danger">¥{{ formatNumber(stats.overdueAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">收费率</div>
        <div class="stat-value warning">{{ stats.collectionRate }}%</div>
      </div>
    </div>
    
    <!-- 表格 -->
    <div class="table-container">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="billNo" label="账单编号" width="160" />
        <el-table-column prop="userName" label="用户" width="100" />
        <el-table-column prop="billPeriod" label="账期" width="100" />
        <el-table-column prop="usageAmount" label="用水量(吨)" width="110" />
        <el-table-column prop="waterFee" label="水费" width="100">
          <template #default="{ row }">¥{{ money(row.waterFee) }}</template>
        </el-table-column>
        <el-table-column prop="sewageFee" label="污水费" width="100">
          <template #default="{ row }">¥{{ money(row.sewageFee) }}</template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="应缴金额" width="110">
          <template #default="{ row }">
            <span style="font-weight: bold;">¥{{ money(row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="paidAmount" label="已缴" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.paidAmount > 0 ? '#67C23A' : '#909399' }">
              ¥{{ money(row.paidAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应缴日期" width="120">
          <template #default="{ row }">{{ formatDate(row.dueDate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status !== 1" type="success" link @click="handlePay(row)">
              缴费
            </el-button>
            <el-button v-if="row.status === 2" type="warning" link @click="handleCollection(row)">
              催缴
            </el-button>
            <el-button type="info" link @click="handlePrint(row)">打印</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div style="margin-top: 20px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          layout="total, sizes, prev, pager, next"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          @current-change="load"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
    
    <!-- 缴费对话框 -->
    <el-dialog v-model="payDialogVisible" title="账单缴费" width="450px">
      <el-form label-width="80px">
        <el-form-item label="账单编号">
          <el-input :value="currentBill.billNo" disabled />
        </el-form-item>
        <el-form-item label="用户">
          <el-input :value="currentBill.userName" disabled />
        </el-form-item>
        <el-form-item label="应缴金额">
          <span style="font-size: 24px; color: #F56C6C; font-weight: bold;">
            ¥{{ currentBill.totalAmount?.toFixed(2) }}
          </span>
        </el-form-item>
        <el-form-item label="支付方式">
          <el-radio-group v-model="payMethod">
            <el-radio-button label="wechat">微信</el-radio-button>
            <el-radio-button label="alipay">支付宝</el-radio-button>
            <el-radio-button label="bank">银行</el-radio-button>
            <el-radio-button label="cash">现金</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="payDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPay">确认缴费</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { billApi } from '@/api/index.js'

const payDialogVisible = ref(false)
const currentBill = ref({})
const payMethod = ref('wechat')
const loading = ref(false)
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const searchForm = reactive({
  billNo: '',
  userName: '',
  status: null,
  billPeriod: ''
})

const stats = reactive({
  totalAmount: 0,
  paidAmount: 0,
  overdueAmount: 0,
  collectionRate: 0
})

const tableData = ref([])

const load = async () => {
  loading.value = true
  try {
    const res = await billApi.list({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      billNo: searchForm.billNo || undefined,
      userName: searchForm.userName || undefined,
      status: searchForm.status ?? undefined,
      billPeriod: searchForm.billPeriod ? dayjs(searchForm.billPeriod).format('YYYY-MM') : undefined
    })
    const data = res.data || {}
    tableData.value = data.records || []
    pagination.total = Number(data.total || 0)
    Object.assign(stats, {
      totalAmount: Number(data.stats?.totalAmount || 0),
      paidAmount: Number(data.stats?.paidAmount || 0),
      overdueAmount: Number(data.stats?.overdueAmount || 0),
      collectionRate: Number(data.stats?.collectionRate || 0)
    })
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  load()
}
const handleSizeChange = () => {
  pagination.pageNum = 1
  load()
}
const handleReset = () => {
  Object.assign(searchForm, { billNo: '', userName: '', status: null, billPeriod: '' })
  handleSearch()
}

const handleDetail = (row) => {
  ElMessage.info('查看详情')
}

const handlePay = (row) => {
  currentBill.value = row
  payDialogVisible.value = true
}

const confirmPay = async () => {
  await billApi.pay(currentBill.value.id, {
    amount: Number(currentBill.value.totalAmount || 0),
    payMethod: payMethod.value
  })
  payDialogVisible.value = false
  ElMessage.success('缴费成功')
  await load()
}

const handleCollection = (row) => {
  ElMessage.success('已发送催缴通知')
}

const handlePrint = (row) => {
  ElMessage.info('打印账单')
}

const formatNumber = (num) => {
  return Number(num || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
const money = (num) => Number(num || 0).toFixed(2)
const formatDate = (value) => value ? dayjs(value).format('YYYY-MM-DD') : '—'

const getStatusLabel = (status) => {
  const map = { 0: '未支付', 1: '已支付', 2: '已逾期', 3: '部分支付' }
  return map[status] || '未知'
}

const getStatusTag = (status) => {
  const map = { 0: 'info', 1: 'success', 2: 'danger', 3: 'warning' }
  return map[status] || 'info'
}

onMounted(load)
</script>
