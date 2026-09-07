<template>
  <div class="table-page">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="水表编号">
          <el-input v-model="searchForm.meterNo" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="异常类型">
          <el-select v-model="searchForm.anomalyType" placeholder="全部" clearable>
            <el-option label="漏水" value="leak" />
            <el-option label="偷水" value="theft" />
            <el-option label="用量突增" value="sudden_increase" />
            <el-option label="零用量" value="zero_usage" />
            <el-option label="表故障" value="meter_fault" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="searchForm.severity" placeholder="全部" clearable>
            <el-option label="紧急" value="critical" />
            <el-option label="高" value="high" />
            <el-option label="中" value="medium" />
            <el-option label="低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="待处理" :value="0" />
            <el-option label="处理中" :value="1" />
            <el-option label="已处理" :value="2" />
          </el-select>
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
    
    <!-- 表格 -->
    <div class="table-container">
      <el-table :data="tableData" stripe>
        <el-table-column prop="anomalyNo" label="异常编号" width="160" />
        <el-table-column prop="meterNo" label="水表编号" width="140" />
        <el-table-column prop="userName" label="用户" width="100" />
        <el-table-column prop="anomalyType" label="异常类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getAnomalyTypeTag(row.anomalyType)">
              {{ getAnomalyTypeLabel(row.anomalyType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="严重程度" width="100">
          <template #default="{ row }">
            <el-tag :type="getSeverityTag(row.severity)" effect="dark">
              {{ getSeverityLabel(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="aiScore" label="AI评分" width="100">
          <template #default="{ row }">
            <el-progress 
              :percentage="row.aiScore * 100" 
              :color="row.aiScore > 0.8 ? '#F56C6C' : '#E6A23C'"
              :stroke-width="12"
            />
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detectedTime" label="检测时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
            <el-button v-if="row.status === 0" type="warning" link @click="handleCreateOrder(row)">
              派单
            </el-button>
            <el-button v-if="row.workOrderNo" type="info" link @click="handleViewOrder(row)">
              工单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const searchForm = reactive({
  meterNo: '',
  anomalyType: '',
  severity: '',
  status: null
})

const tableData = ref([
  { id: 1, anomalyNo: 'ANOM-20240115-001', meterNo: 'WM-A001-0003', userName: '王五', anomalyType: 'leak', severity: 'critical', aiScore: 0.92, description: '检测到疑似漏水，用水量突增300%', status: 0, detectedTime: '2024-01-15 12:30:45', workOrderNo: null },
  { id: 2, anomalyNo: 'ANOM-20240114-002', meterNo: 'WM-A001-0004', userName: '赵六', anomalyType: 'zero_usage', severity: 'medium', aiScore: 0.75, description: '连续35天零用量', status: 1, detectedTime: '2024-01-14 09:15:22', workOrderNo: 'WO-20240114-001' },
  { id: 3, anomalyNo: 'ANOM-20240113-003', meterNo: 'WM-A002-0001', userName: '孙八', anomalyType: 'theft', severity: 'critical', aiScore: 0.88, description: 'AI模型检测到疑似偷水特征', status: 0, detectedTime: '2024-01-13 22:45:18', workOrderNo: null },
  { id: 4, anomalyNo: 'ANOM-20240112-004', meterNo: 'WM-A002-0002', userName: '周九', anomalyType: 'sudden_increase', severity: 'high', aiScore: 0.82, description: '用水量突增250%', status: 2, detectedTime: '2024-01-12 14:20:33', workOrderNo: 'WO-20240112-002' }
])

const handleSearch = () => {}
const handleReset = () => {}

const handleDetail = (row) => {
  ElMessage.info('查看详情')
}

const handleCreateOrder = (row) => {
  ElMessageBox.confirm('确定要为该异常创建工单吗？', '提示', {
    type: 'warning'
  }).then(() => {
    row.workOrderNo = 'WO-' + Date.now()
    row.status = 1
    ElMessage.success('工单创建成功')
  })
}

const handleViewOrder = (row) => {
  ElMessage.info('查看工单详情')
}

const getAnomalyTypeLabel = (type) => {
  const map = { leak: '漏水', theft: '偷水', sudden_increase: '用量突增', zero_usage: '零用量', meter_fault: '表故障' }
  return map[type] || type
}

const getAnomalyTypeTag = (type) => {
  const map = { leak: 'danger', theft: 'danger', sudden_increase: 'warning', zero_usage: 'info', meter_fault: 'warning' }
  return map[type] || 'info'
}

const getSeverityLabel = (severity) => {
  const map = { critical: '紧急', high: '高', medium: '中', low: '低' }
  return map[severity]
}

const getSeverityTag = (severity) => {
  const map = { critical: 'danger', high: 'warning', medium: 'info', low: '' }
  return map[severity]
}

const getStatusLabel = (status) => {
  const map = { 0: '待处理', 1: '处理中', 2: '已处理' }
  return map[status]
}

const getStatusTag = (status) => {
  const map = { 0: 'danger', 1: 'warning', 2: 'success' }
  return map[status]
}
</script>