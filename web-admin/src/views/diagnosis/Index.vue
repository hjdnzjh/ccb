<template>
  <main class="diagnosis-page">
    <header class="heading"><div><span class="eyebrow">主动发现 · 人工核查 · 效果观察</span><h2>诊断中心</h2><p>沿着每次观测查看依据，让复测与现场处置形成可追溯的记录。</p></div><el-button :loading="loading" @click="refresh">刷新</el-button></header>
    <el-alert title="诊断观测独立保存，不生成账单。当前复测使用模拟采集器；异常评分不代表漏水概率。" type="info" :closable="false" show-icon />
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-tabs v-model="tab">
      <el-tab-pane label="诊断事件" name="cases">
        <section class="panel">
          <el-form inline class="filters" @submit.prevent="search">
            <el-form-item label="表号"><el-input v-model="filters.meterNo" clearable placeholder="按水表编号查询" /></el-form-item>
            <el-form-item label="状态"><el-select v-model="filters.state" clearable placeholder="全部状态" style="width:180px"><el-option v-for="state in caseStates" :key="state" :value="state" :label="stateLabel(state)" /></el-select></el-form-item>
            <el-button type="primary" native-type="submit">查询</el-button>
          </el-form>
          <el-table :data="rows" v-loading="loading" stripe empty-text="当前筛选下暂无诊断事件">
            <el-table-column prop="meter_no" label="水表编号" min-width="160" />
            <el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="stateTone(row.state)">{{ stateLabel(row.state) }}</el-tag></template></el-table-column>
            <el-table-column prop="summary" label="诊断摘要" min-width="260" show-overflow-tooltip />
            <el-table-column label="级别" width="90"><template #default="{row}">{{row.severity==='high'?'高':'中'}}</template></el-table-column>
            <el-table-column prop="last_evidence_at" label="最近证据" min-width="185" />
            <el-table-column label="操作" width="100" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openDetail(row)">查看证据</el-button></template></el-table-column>
          </el-table>
          <div class="pager"><el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="total, prev, pager, next" @current-change="loadCases" /></div>
        </section>
      </el-tab-pane>
      <el-tab-pane label="策略与范围" name="policy">
        <section class="panel policy-panel" v-loading="policyLoading">
          <h3>诊断运行策略 <el-tag>版本 {{ policy.version ?? '未读取' }}</el-tag></h3>
          <p class="muted">关闭：停止新增诊断动作；影子观察：只记录建议；辅助诊断：允许限额复测，工单由管理员确认创建。</p>
          <el-form label-position="top">
            <el-form-item label="运行模式"><el-radio-group v-model="policy.mode"><el-radio-button value="off" label="off">关闭</el-radio-button><el-radio-button value="shadow" label="shadow">影子观察</el-radio-button><el-radio-button value="assisted" label="assisted">辅助诊断</el-radio-button></el-radio-group></el-form-item>
            <el-form-item label="生效水表（仅作用于所选范围）"><el-select v-model="policy.meterIds" multiple filterable style="width:100%" placeholder="请选择诊断范围"><el-option v-for="meter in meters" :key="meter.id" :value="meter.id" :label="meter.meter_no || meter.meterNo" /></el-select></el-form-item>
          </el-form>
          <el-button type="primary" :loading="acting" :disabled="!policyReady || (policy.mode !== 'off' && !policy.meterIds.length)" @click="savePolicy">保存策略</el-button>
          <el-button :disabled="acting" @click="loadPolicy">重新读取</el-button>
        </section>
      </el-tab-pane>
      <el-tab-pane label="模拟诊断输入" name="input">
        <section class="panel policy-panel"><h3>提交一次诊断观测</h3><p class="muted">七字段：表号 | ISO 时间 | 瞬时流量(m³/h) | 累计量(m³) | 水温(℃) | OPEN/CLOSED | 报警码。此入口不更新正式抄表读数，不计费。</p>
          <el-input v-model="packet" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="填入真实格式的模拟设备报文" />
          <div class="actions"><el-button type="primary" :loading="acting" :disabled="!packet.trim()" @click="submitObservation">提交诊断观测</el-button><el-button :loading="acting" @click="process">推进到期诊断任务</el-button></div>
          <el-alert v-if="observationResult" :title="observationResult" type="success" :closable="false" />
          <p class="muted">推进操作只执行当前已到期任务；未到复测时间的任务不会提前执行。</p>
        </section>
      </el-tab-pane>
    </el-tabs>
    <el-drawer v-model="detailVisible" title="诊断证据与处置" size="min(780px, 100%)">
      <div v-loading="detailLoading">
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" /><el-button v-if="detailError" @click="openDetail({id:selectedId})">重试</el-button>
        <template v-if="detail">
          <div class="detail-heading"><h3>{{ detail.meter_no }}</h3><el-tag :type="stateTone(detail.state)">{{ stateLabel(detail.state) }}</el-tag></div><p>{{ detail.summary }}</p>
          <p class="muted">事件 #{{ detail.id }} · {{ ({device:'设备异常',usage:'用水偏离'})[detail.family] || '未分类' }} · 建立于 {{ detail.opened_at || '未提供' }}</p>
          <div class="actions"><el-button :loading="acting" :disabled="policy.mode !== 'assisted' || terminal" @click="probe">安排复测</el-button><el-button :loading="acting" :disabled="!hasPendingProbes" @click="cancelProbes">取消未执行复测</el-button><el-button v-if="!detail.work_order_id" type="primary" :loading="acting" :disabled="policy.mode !== 'assisted' || terminal" @click="createOrder">确认创建巡检工单</el-button><el-button v-else type="primary" @click="goOrder(detail.work_order_id)">查看工单 #{{ detail.work_order_id }}</el-button></div>
          <p v-if="policy.mode !== 'assisted'" class="muted">安排复测与创建工单需要辅助诊断模式及后端范围校验。</p>
          <el-tabs v-model="detailTab">
            <el-tab-pane label="证据链" name="evidence">
              <el-empty v-if="!detail.evidence?.length" description="暂无可用证据" :image-size="60" />
              <article v-for="evidence in detail.evidence || []" :key="evidence.id" class="evidence-card">
                <div class="detail-heading"><strong>观测窗口 {{ evidence.window_end || evidence.created_at }}</strong><el-tag>评分 {{ displayValue(evidence.score) }}</el-tag></div>
                <p>{{ reasons(evidence.reason_codes) }}</p><p class="muted">模型版本：{{ evidence.model_version || '规则模式 / 未提供' }} · 基线版本：{{ safeJson(evidence.baseline_json)?.version || '未提供' }}</p>
                <h4>实际观测与完整度</h4><DiagnosisFacts :value="evidence.features_json" />
                <h4>同类历史基线</h4><DiagnosisFacts :value="evidence.baseline_json" empty="未提供个体基线；按基础规则或数据不足解释。" />
                <details><summary>展开原始证据字段</summary><pre>{{ prettyJson(evidence) }}</pre></details>
              </article>
            </el-tab-pane>
            <el-tab-pane label="复测进度" name="probes"><p class="muted">最多三次复测；网络重试单独计数，失败与重复观测不能作为正常证据。</p><el-table :data="detail.probes || []" empty-text="尚无复测任务"><el-table-column prop="sequence_no" label="序号" width="65" /><el-table-column label="状态" width="110"><template #default="{row}">{{ stateLabel(row.state) }}</template></el-table-column><el-table-column prop="due_at" label="计划时间" min-width="180" /><el-table-column prop="attempts" label="请求次数" width="95" /><el-table-column prop="error" label="说明" min-width="170" /><el-table-column prop="observation_id" label="观测编号" width="95" /></el-table></el-tab-pane>
            <el-tab-pane label="人工核查" name="review"><el-form label-position="top"><el-form-item label="核查标签"><el-select v-model="review.label" style="width:100%"><el-option v-for="label in reviewLabels" :key="label" :value="label" :label="stateLabel(label)" /></el-select></el-form-item><el-form-item label="现场情况与判断依据"><el-input v-model="review.note" type="textarea" :rows="3" maxlength="1000" show-word-limit /></el-form-item></el-form><el-button type="primary" :disabled="!review.note.trim()" :loading="acting" @click="saveReview">保存人工核查记录</el-button><p class="muted">标签追加留痕，不直接用于在线模型训练。</p><article v-for="(item,index) in detail.reviews || []" :key="index" class="evidence-card"><strong>{{ stateLabel(item.label) }}</strong><p>{{ item.note }}</p><small>{{ item.created_at }}</small></article></el-tab-pane>
            <el-tab-pane label="效果核验" name="verification"><p class="muted">工单完成后的独立观察结论；没有足够证据时显示“无法判断”。</p><el-empty v-if="!detail.verifications?.length" description="暂无效果观察记录" :image-size="60" /><article v-for="item in detail.verifications || []" :key="item.id" class="evidence-card"><el-tag :type="stateTone(item.state)">{{ stateLabel(item.state) }}</el-tag><p>观察期：{{ item.started_at || '未提供' }} — {{ item.deadline_at || '未提供' }}</p><p>覆盖率：{{ displayValue(item.coverage) }}</p><DiagnosisFacts :value="item.result_json" /><el-button v-if="item.work_order_id" link type="primary" @click="goOrder(item.work_order_id)">查看关联工单</el-button><details><summary>核验明细</summary><pre>{{ prettyJson(item.result_json) }}</pre></details></article></el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-drawer>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { diagnosisApi } from '@/api/diagnosis.js'
import DiagnosisFacts from '@/components/DiagnosisFacts.vue'
import { displayValue, newRequestKey, prettyJson, safeJson, stateLabel, stateTone } from '@/utils/diagnosis.js'
import { useNotificationTarget } from '@/utils/notificationTarget.js'
const router = useRouter()
const tab = ref('cases'), detailTab = ref('evidence'), loading = ref(false), acting = ref(false), error = ref('')
const rows = ref([]), total = ref(0), meters = ref([]), policyReady = ref(false), policyLoading = ref(false)
const filters = reactive({ page: 1, size: 15, meterNo: '', state: '' })
const policy = reactive({ version: null, mode: 'off', meterIds: [] })
const caseStates = ['candidate','probing','needs_review','work_order_linked','awaiting_verification','closed','not_persistent']
const reviewLabels = ['confirmed_anomaly','normal_business','device_fault','insufficient_data']
const review = reactive({ label: 'insufficient_data', note: '' })
const detail = ref(null), detailVisible = ref(false), detailLoading = ref(false), detailError = ref(''), selectedId = ref(null)
const packet = ref(''), observationResult = ref('')
const keys = new Map()
const keyFor = action => { if (!keys.has(action)) keys.set(action, newRequestKey()); return keys.get(action) }
const terminal = computed(() => ['closed','not_persistent'].includes(detail.value?.state))
const hasPendingProbes = computed(() => detail.value?.probes?.some(p => ['pending','retry'].includes(p.state)))
const reasonLabels={device_alarm:'设备报警',high_flow:'流量超过基础阈值',flow_while_valve_closed:'关阀后仍有流量',temperature_abnormal:'水温异常',persistent_flow_above_personal_baseline:'连续用水超过个人历史范围',cold_start:'历史不足，使用基础规则',insufficient_windows:'有效窗口不足',within_personal_baseline:'处于个人历史范围',missing_intervals:'观测间隔不完整',missing_window:'连续观测窗口不足',insufficient_group_history:'同类时段历史不足',no_observations:'尚无有效观测'}
const reasons = value => { const parsed = safeJson(value, value); return Array.isArray(parsed) ? parsed.map(x=>reasonLabels[x]||x).join(' · ') : displayValue(parsed) }
async function loadCases() { loading.value = true; error.value = ''; try { const result = (await diagnosisApi.cases(filters)).data; rows.value = result.records || []; total.value = result.total || 0 } catch (e) { error.value = e.message || '诊断事件读取失败，请重试' } finally { loading.value = false } }
async function loadPolicy() { policyLoading.value = true; policyReady.value = false; try { const [p,m] = await Promise.all([diagnosisApi.policy(), diagnosisApi.meters()]); Object.assign(policy,p.data); policy.meterIds = p.data.meterIds || []; meters.value = m.data || []; policyReady.value = true } catch (e) { error.value = '策略读取失败，请重新读取后操作。' } finally { policyLoading.value = false } }
async function refresh() { await Promise.all([loadCases(), loadPolicy()]) }
function search() { filters.page = 1; loadCases() }
async function openDetail(row) { selectedId.value = row.id; detail.value = null; detailVisible.value = true; detailLoading.value = true; detailError.value = ''; review.note = ''; detailTab.value = 'evidence'; try { const result = await diagnosisApi.detail(row.id); if (selectedId.value === row.id) detail.value = result.data } catch (e) { detailError.value = e.message || '证据读取失败' } finally { detailLoading.value = false } }
async function reloadDetail() { if (selectedId.value) detail.value = (await diagnosisApi.detail(selectedId.value)).data }
async function action(task, message) { if (acting.value) return; acting.value = true; try { await task(); ElMessage.success(message) } catch (e) { error.value = e.message || '操作未完成，请重试' } finally { acting.value = false } }
async function savePolicy() { await action(async () => { await diagnosisApi.savePolicy({...policy}); await loadPolicy() }, '诊断策略已保存') }
async function probe() { const id = detail.value.id; const key = `probe:${id}`; await action(async () => { await diagnosisApi.probe(id,{requestKey:keyFor(key)}); keys.delete(key); await reloadDetail(); await loadCases() }, '复测请求已提交') }
async function cancelProbes() { try { const result = await ElMessageBox.prompt('请填写取消原因，历史证据会保留。','取消未执行复测',{inputValidator:v => !!v?.trim() || '请填写原因'}); await action(async () => { await diagnosisApi.cancelProbes(detail.value.id,{reason:result.value,requestKey:newRequestKey()}); await reloadDetail() }, '取消请求已提交') } catch {} }
async function saveReview() { const id = detail.value.id; const key = `review:${id}:${review.label}:${review.note}`; await action(async () => { await diagnosisApi.review(id,{...review,requestKey:keyFor(key)}); keys.delete(key); review.note = ''; await reloadDetail() }, '人工核查记录已保存') }
async function createOrder() { try { await ElMessageBox.confirm('将为当前诊断事件建立巡检工单，由管理员继续派单。','确认创建工单',{type:'info'}); const id = detail.value.id; const key = `order:${id}`; await action(async () => { await diagnosisApi.workOrder(id,{requestKey:keyFor(key)}); keys.delete(key); await reloadDetail(); await loadCases() }, '关联工单已创建') } catch {} }
const goOrder = id => router.push({path:'/anomaly/workorder',query:{focus:String(id)}})
async function submitObservation() { const key = `packet:${packet.value}`; await action(async () => { const result = (await diagnosisApi.observation({packet:packet.value,requestKey:keyFor(key)})).data; keys.delete(key); observationResult.value = `观测 ${result.observationId ?? '未生成'} · ${result.status || '已接收'}（不计费）`; await loadCases() }, '模拟诊断观测已提交') }
async function process() { await action(async () => { await diagnosisApi.process(); await loadCases(); await reloadDetail() }, '到期任务推进完成') }
useNotificationTarget(openDetail)
onMounted(refresh)
</script>

<style scoped>
.diagnosis-page{padding:24px;max-width:1600px;margin:auto}.heading,.detail-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.heading h2{margin:8px 0;font-size:26px}.heading p,.muted{color:#718096;font-size:13px;line-height:1.8}.eyebrow{font-size:12px;color:#168a8a;letter-spacing:1px}.el-alert{margin:16px 0}.panel{background:#fff;border:1px solid #e5edf3;border-radius:12px;padding:20px}.policy-panel{max-width:800px}.pager{margin-top:20px;overflow:auto}.actions{display:flex;flex-wrap:wrap;gap:10px;margin:18px 0}.actions .el-button{margin-left:0}.evidence-card{border:1px solid #e0e8ef;background:#fafcfe;border-radius:10px;padding:18px;margin:16px 0}.evidence-card h4{margin-bottom:8px}details{margin-top:16px;color:#64748b;font-size:12px}summary{cursor:pointer}pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:350px;overflow:auto;background:#eef3f7;padding:12px}.filters{display:flex;flex-wrap:wrap;gap:0 12px}.filters .el-form-item{margin-right:0}@media(max-width:760px){.diagnosis-page{padding:12px}.heading{align-items:flex-start}.heading h2{font-size:22px}.panel{padding:12px}.detail-heading{align-items:flex-start;flex-wrap:wrap}.evidence-card{padding:12px}}
</style>
