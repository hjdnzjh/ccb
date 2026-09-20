<template>
  <dl v-if="entries.length" class="facts">
    <template v-for="[key, value] in entries" :key="key"><dt>{{ labels[key] || key }}</dt><dd>{{ factValue(key,value) }}</dd></template>
  </dl>
  <p v-else class="empty">{{ empty }}</p>
</template>
<script setup>
import { computed } from 'vue'
import { displayValue, safeJson } from '@/utils/diagnosis.js'
const props = defineProps({ value: { default: null }, empty: { default: '暂无有效数据，不能据此判断正常或异常。' } })
const entries = computed(() => {
  const data = safeJson(props.value)
  return data && typeof data === 'object' ? Object.entries(data).filter(([, value]) => typeof value !== 'object' || value === null) : []
})
const labels = {
  profileVersion:'全天分组快照版本',
  count:'同类历史样本数',validDays:'有效历史天数',validIntervals:'有效历史区间',instantaneousFlow:'瞬时流量 (m³/h)',intervalDeviation:'区间流量偏离',instantDeviation:'瞬时流量偏离',changeRate:'流量变化比例',deviceAlarm:'设备报警标记',valveClosed:'关阀标记',featureSchema:'特征版本',samples:'有效观测格数',expectedSamples:'预期观测格数',maxGapSeconds:'最大空档（秒）',comparable:'供水条件可比较',hasBaseline:'可用基线',windowStart:'观察起点',windowEnd:'观察终点',
  version:'基线版本',ready:'基线已就绪',trainingEnd:'历史截止时间',upper:'个人范围上限 (m³/h)',intervalFlow:'区间平均流量 (m³/h)',instantFlow:'瞬时流量 (m³/h)',flowZ:'流量标准化偏离',deltaRatio:'流量变化比例',consecutiveHigh:'连续偏高窗口',validSamples:'有效窗口数',expectedSamples:'预期窗口数',decision:'检测结论',fallbackReason:'降级说明',maxGapMinutes:'最大空档（分钟）',nightSeen:'包含夜间观测',observations:'有效观测数',normalRatio:'正常窗口比例',normalWindows:'正常窗口数',expectedWindows:'预期窗口数',
  flow: '瞬时流量 (m³/h)', interval_flow: '区间平均流量 (m³/h)', temperature: '水温 (℃)', total: '累计量 (m³)',
  coverage: '观测覆盖率', valid_count: '有效样本数', sample_count: '样本数', missing_count: '缺失样本',
  model_version: '模型版本', baseline_version: '基线版本', rule_version: '规则版本', score: '异常评分',
  median: '历史中位数', mad: '历史绝对偏差中位数', p95: '历史95%分位', reason: '判定说明',
  source: '数据来源', mode: '检测模式', cold_start: '冷启动', max_gap_minutes: '最大空档（分钟）',
  precision: '精确率', recall: '召回率', f1: 'F1', false_positives: '误报事件数', true_positives: '检出事件数',
  false_negatives: '漏报事件数', event_count: '事件数', seed: '固定种子', meters: '水表数', days: '回放天数'
}
const descriptions={needs_review:'需要人工核查',normal:'未发现当前规则异常',insufficient_data:'证据不足',model_not_configured:'当前未启用学习模型',insufficient_features:'有效特征不足，使用基础规则',model_unavailable:'模型不可用，使用基础规则',engine_unavailable:'智能引擎不可用，使用基础规则',model_version_mismatch:'模型版本不匹配，使用基础规则'}
function factValue(key,value){if(typeof value==='string'&&descriptions[value])return descriptions[value];if(typeof value==='number'){if(key==='coverage')return `${(value*100).toFixed(1)}%`;return Number.isInteger(value)?String(value):String(Number(value.toFixed(6)))}return displayValue(value)}
</script>
<style scoped>
.facts{display:grid;grid-template-columns:minmax(110px,1fr) minmax(0,2fr);gap:10px 16px;font-size:13px}.facts dt,.empty{color:#718096}.facts dd{margin:0;overflow-wrap:anywhere}.empty{font-size:13px}
</style>
