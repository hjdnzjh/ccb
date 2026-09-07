<template>
  <div class="table-page">
    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="水表编号">
          <el-input v-model="searchForm.meterNo" placeholder="请输入水表编号" clearable />
        </el-form-item>
        <el-form-item label="用户类型">
          <el-select v-model="searchForm.userType" placeholder="全部" clearable>
            <el-option label="居民" value="residential" />
            <el-option label="商业" value="commercial" />
            <el-option label="工业" value="industrial" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable>
            <el-option label="正常" :value="0" />
            <el-option label="故障" :value="1" />
            <el-option label="停用" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="通讯方式">
          <el-select v-model="searchForm.commType" placeholder="全部" clearable>
            <el-option label="NB-IoT" value="NB-IoT" />
            <el-option label="LoRa" value="LoRa" />
            <el-option label="4G" value="4G" />
            <el-option label="有线" value="wired" />
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
      <div style="margin-bottom: 16px;">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增
        </el-button>
        <el-button type="success" @click="handleBatchReading">
          <el-icon><Camera /></el-icon> 批量抄表
        </el-button>
        <el-button type="warning" @click="handleExport">
          <el-icon><Download /></el-icon> 导出
        </el-button>
      </div>
      
      <el-table 
        :data="tableData" 
        stripe 
        style="width: 100%"
        v-loading="loading"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="meterNo" label="水表编号" width="140" fixed />
        <el-table-column prop="meterType" label="表类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ getMeterTypeLabel(row.meterType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="commType" label="通讯方式" width="100" />
        <el-table-column prop="userName" label="用户" width="120" />
        <el-table-column prop="userType" label="用户类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getUserTypeTag(row.userType)">{{ getUserTypeLabel(row.userType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentReading" label="当前读数" width="120" />
        <el-table-column prop="lastReadingTime" label="上次抄表" width="160" />
        <el-table-column prop="signalStrength" label="信号" width="80">
          <template #default="{ row }">
            <el-progress 
              :percentage="row.signalStrength" 
              :color="row.signalStrength > 80 ? '#67C23A' : row.signalStrength > 50 ? '#E6A23C' : '#F56C6C'"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="installAddress" label="安装地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleReading(row)">
              <el-icon><Camera /></el-icon> 抄表
            </el-button>
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div style="margin-top: 20px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </div>
    
    <!-- AI抄表对话框 -->
    <el-dialog v-model="readingDialogVisible" title="AI智能抄表" width="600px">
      <div class="ai-reading-dialog">
        <el-steps :active="readingStep" finish-status="success" simple>
          <el-step title="上传图片" />
          <el-step title="AI识别" />
          <el-step title="确认结果" />
        </el-steps>
        
        <div style="margin-top: 20px;">
          <!-- 步骤1: 上传图片 -->
          <div v-if="readingStep === 0" class="upload-area" @click="triggerUpload">
            <input ref="fileInput" type="file" accept="image/*" style="display: none;" @change="handleFileChange">
            <el-icon><Upload /></el-icon>
            <div class="upload-text">点击或拖拽上传水表图片</div>
            <div class="upload-hint">支持 JPG、PNG 格式，建议清晰拍摄表盘</div>
          </div>
          
          <!-- 步骤2: 预览和识别 -->
          <div v-if="readingStep >= 1">
            <div class="preview-area">
              <img :src="previewImage" class="preview-image">
            </div>
            
            <div v-if="readingStep === 1" style="text-align: center; margin-top: 20px;">
              <el-button type="primary" :loading="recognizing" @click="handleAIRecognize">
                <el-icon><Camera /></el-icon> 开始AI识别
              </el-button>
            </div>
            
            <!-- 步骤3: 识别结果 -->
            <div v-if="readingStep === 2" class="result-area">
              <div class="result-item">
                <span class="label">识别读数</span>
                <span class="value" style="font-size: 24px; color: #409EFF;">{{ aiResult.reading }}</span>
              </div>
              <div class="result-item">
                <span class="label">置信度</span>
                <span class="value">
                  <el-progress 
                    :percentage="aiResult.confidence * 100" 
                    :color="aiResult.confidence >= 0.95 ? '#67C23A' : '#E6A23C'"
                    style="width: 200px; display: inline-block;"
                  />
                </span>
              </div>
              <div class="result-item">
                <span class="label">表类型</span>
                <span class="value">{{ aiResult.meterType }}</span>
              </div>
              <div class="result-item">
                <span class="label">处理时间</span>
                <span class="value">{{ aiResult.processTime }}ms</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="readingDialogVisible = false">取消</el-button>
        <el-button v-if="readingStep > 0" @click="readingStep--">上一步</el-button>
        <el-button v-if="readingStep === 2" type="primary" @click="handleConfirmReading">
          确认录入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const readingDialogVisible = ref(false)
const readingStep = ref(0)
const recognizing = ref(false)
const previewImage = ref('')
const currentMeter = ref(null)

const searchForm = reactive({
  meterNo: '',
  userType: '',
  status: null,
  commType: ''
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 100
})

const aiResult = ref({
  reading: 0,
  confidence: 0,
  meterType: '',
  processTime: 0
})

// 模拟表格数据
const tableData = ref([
  { id: 1, meterNo: 'WM-A001-0001', meterType: 'digital', commType: 'NB-IoT', userName: '张三', userType: 'residential', currentReading: 1256.5, lastReadingTime: '2024-01-15 14:32', signalStrength: 95, status: 0, installAddress: '示范区A街道1号楼101室' },
  { id: 2, meterNo: 'WM-A001-0002', meterType: 'pointer', commType: 'LoRa', userName: '李四', userType: 'commercial', currentReading: 5680.0, lastReadingTime: '2024-01-15 13:45', signalStrength: 78, status: 0, installAddress: '示范区A街道2号商铺' },
  { id: 3, meterNo: 'WM-A001-0003', meterType: 'wheel', commType: '4G', userName: '王五', userType: 'industrial', currentReading: 12500.0, lastReadingTime: '2024-01-15 12:30', signalStrength: 88, status: 0, installAddress: '示范区A工业园区3号厂房' },
  { id: 4, meterNo: 'WM-A001-0004', meterType: 'digital', commType: 'NB-IoT', userName: '赵六', userType: 'residential', currentReading: 890.3, lastReadingTime: '2024-01-14 16:20', signalStrength: 45, status: 1, installAddress: '示范区A街道4号楼202室' },
  { id: 5, meterNo: 'WM-A001-0005', meterType: 'digital', commType: 'wired', userName: '钱七', userType: 'residential', currentReading: 1567.8, lastReadingTime: '2024-01-14 15:10', signalStrength: 100, status: 0, installAddress: '示范区A街道5号楼303室' }
])

const handleSearch = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
  }, 500)
}

const handleReset = () => {
  Object.keys(searchForm).forEach(key => {
    searchForm[key] = key === 'status' ? null : ''
  })
  handleSearch()
}

const handleAdd = () => {
  ElMessage.info('新增水表功能开发中')
}

const handleBatchReading = () => {
  ElMessage.info('批量抄表功能开发中')
}

const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

const handleReading = (row) => {
  currentMeter.value = row
  readingStep.value = 0
  previewImage.value = ''
  readingDialogVisible.value = true
}

const handleEdit = (row) => {
  ElMessage.info('编辑功能开发中')
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该水表吗？', '提示', {
    type: 'warning'
  }).then(() => {
    ElMessage.success('删除成功')
  })
}

const triggerUpload = () => {
  document.querySelector('input[type="file"]').click()
}

const handleFileChange = (e) => {
  const file = e.target.files[0]
  if (file) {
    previewImage.value = URL.createObjectURL(file)
    readingStep.value = 1
  }
}

const handleAIRecognize = async () => {
  recognizing.value = true
  
  // 模拟AI识别
  setTimeout(() => {
    aiResult.value = {
      reading: (Math.random() * 10000).toFixed(1),
      confidence: 0.85 + Math.random() * 0.14,
      meterType: '数字式水表',
      processTime: Math.floor(Math.random() * 1000 + 500)
    }
    readingStep.value = 2
    recognizing.value = false
  }, 2000)
}

const handleConfirmReading = () => {
  ElMessage.success(`读数 ${aiResult.value.reading} 已成功录入`)
  readingDialogVisible.value = false
}

const getMeterTypeLabel = (type) => {
  const map = { digital: '电子式', pointer: '指针式', wheel: '字轮式' }
  return map[type] || type
}

const getUserTypeLabel = (type) => {
  const map = { residential: '居民', commercial: '商业', industrial: '工业' }
  return map[type] || type
}

const getUserTypeTag = (type) => {
  const map = { residential: 'success', commercial: 'warning', industrial: 'danger' }
  return map[type] || 'info'
}

const getStatusLabel = (status) => {
  const map = { 0: '正常', 1: '故障', 2: '停用' }
  return map[status] || '未知'
}

const getStatusTag = (status) => {
  const map = { 0: 'success', 1: 'danger', 2: 'info' }
  return map[status] || 'info'
}
</script>