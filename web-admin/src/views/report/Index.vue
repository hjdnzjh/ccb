<template>
  <div class="table-page">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="报表类型">
          <el-select v-model="reportType" placeholder="请选择">
            <el-option label="月度报表" value="monthly" />
            <el-option label="季度报表" value="quarterly" />
            <el-option label="年度报表" value="yearly" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker v-model="dateRange" type="daterange" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleGenerate">
            <el-icon><Document /></el-icon> 生成报表
          </el-button>
          <el-button type="success" @click="handleExport">
            <el-icon><Download /></el-icon> 导出
          </el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <!-- 报表卡片 -->
    <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; margin-bottom: 20px;">
      <div class="stat-card">
        <div class="stat-label">总用水量</div>
        <div class="stat-value">1,256,780 吨</div>
        <div class="stat-trend up">
          <el-icon><Top /></el-icon> 同比 +12.5%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总营收</div>
        <div class="stat-value success">¥ 2,456,789</div>
        <div class="stat-trend up">
          <el-icon><Top /></el-icon> 同比 +8.3%
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-label">收费率</div>
        <div class="stat-value warning">96.8%</div>
        <div class="stat-trend up">
          <el-icon><Top /></el-icon> 同比 +2.1%
        </div>
      </div>
    </div>
    
    <!-- 图表 -->
    <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 20px;">
      <div class="chart-card">
        <div class="chart-title">用水量趋势</div>
        <div ref="usageChartRef" style="height: 300px;"></div>
      </div>
      <div class="chart-card">
        <div class="chart-title">收入趋势</div>
        <div ref="revenueChartRef" style="height: 300px;"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

const reportType = ref('monthly')
const dateRange = ref([])
const usageChartRef = ref()
const revenueChartRef = ref()

onMounted(() => {
  initCharts()
})

const initCharts = () => {
  // 用水量趋势图
  const usageChart = echarts.init(usageChartRef.value)
  usageChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['1月', '2月', '3月', '4月', '5月', '6月'] },
    yAxis: { type: 'value', name: '吨' },
    series: [{ data: [820, 932, 901, 934, 1290, 1330], type: 'bar', itemStyle: { color: '#409EFF' } }]
  })
  
  // 收入趋势图
  const revenueChart = echarts.init(revenueChartRef.value)
  revenueChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['1月', '2月', '3月', '4月', '5月', '6月'] },
    yAxis: { type: 'value', name: '元' },
    series: [{ data: [320000, 332000, 301000, 334000, 390000, 410000], type: 'line', smooth: true, areaStyle: { opacity: 0.3 } }]
  })
}

const handleGenerate = () => ElMessage.success('报表生成中...')
const handleExport = () => ElMessage.success('报表导出中...')
</script>

<style scoped>
.chart-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}
.chart-title {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 16px;
}
</style>