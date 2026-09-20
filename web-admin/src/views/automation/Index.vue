<template>
  <div class="automation" v-loading="busy">
    <div class="heading"><div><h2>自动采集与违约金</h2><p>持久化计划 · 漏抄补采 · 每日可追溯计费</p></div><el-button @click="refresh">刷新</el-button></div>
    <el-alert title="当前采集来源为七字段模拟设备。自动确认读数会生成真实系统账单；模拟故障和补抄记录会保存在数据库中。" type="warning" :closable="false" show-icon />
    <el-card class="packet">
      <template #header><strong>自主制定采集计划</strong></template>
      <p class="muted">启用后自动为未被启用计划覆盖的在役/故障水表建计划，新建水表也会自动纳入。已有手工计划优先；每轮最多新增100个计划。使用模拟设备，每次成功采集会出账。停用策略只阻止新一轮采集，已下发任务继续完成。</p>
      <el-form inline><el-form-item label="启用自主覆盖"><el-switch v-model="coverage.enabled"/></el-form-item><el-form-item label="基础间隔/分钟"><el-input-number v-model="coverage.intervalMinutes" :min="1" :max="43200"/></el-form-item><el-form-item label="每次模拟增量/m³"><el-input-number v-model="coverage.incrementAmount" :min="0.01" :max="100000" :precision="2"/></el-form-item><el-button type="primary" :loading="busy" @click="saveCoverage">保存自主覆盖策略</el-button></el-form>
    </el-card>
    <el-tabs v-model="tab">
      <el-tab-pane label="采集计划" name="plans">
        <el-card><template #header><strong>创建模拟采集计划</strong></template>
          <el-form label-position="top" class="form-grid">
            <el-form-item label="计划名称"><el-input v-model="form.name" maxlength="100" /></el-form-item>
            <el-form-item label="水表"><el-select v-model="form.meterIds" multiple filterable placeholder="请选择水表"><el-option v-for="m in meters" :key="m.id" :value="m.id" :label="m.meter_no" /></el-select></el-form-item>
            <el-form-item label="正常采集间隔（分钟）"><el-input-number v-model="form.intervalMinutes" :min="1" :max="43200" /></el-form-item>
            <el-form-item label="设备状态自适应"><el-switch v-model="form.adaptive" active-text="故障/低电/弱信号时缩短一半" /></el-form-item>
            <el-form-item label="最大尝试次数（含首次）"><el-input-number v-model="form.maxAttempts" :min="1" :max="5" /></el-form-item>
            <el-form-item label="补抄等待（秒）"><el-input-number v-model="form.retrySeconds" :min="5" :max="3600" /></el-form-item>
            <el-form-item label="模拟场景"><el-select v-model="form.scenario"><el-option label="正常采集" value="normal" /><el-option label="首次超时，补抄成功" value="first_failure" /><el-option label="持续超时，达到上限" value="always_failure" /><el-option label="报警码与高流量" value="fault" /></el-select></el-form-item>
            <el-form-item label="每次模拟增加用量（m³）"><el-input-number v-model="form.incrementAmount" :min="0" :max="100000" :precision="2" /></el-form-item>
          </el-form><el-button type="primary" @click="createPlan">创建并启用</el-button>
        </el-card>
        <el-table :data="plans" stripe><el-table-column prop="name" label="计划" /><el-table-column prop="meter_count" label="水表数" width="90" /><el-table-column prop="interval_minutes" label="间隔/分钟" width="110" /><el-table-column prop="next_run_at" label="下次执行" width="190" /><el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="row.enabled ? 'success':'info'">{{ row.enabled ? '已启用':'已暂停' }}</el-tag></template></el-table-column><el-table-column label="操作" width="200"><template #default="{row}"><span v-if="row.auto_managed" class="muted">策略管理</span><el-button v-else link type="primary" @click="toggle(row)">{{ row.enabled?'暂停':'启用' }}</el-button><el-button link type="primary" @click="trigger(row)">触发一次</el-button></template></el-table-column></el-table>
        <p class="muted">后台每10秒检查到期任务；暂停阻止新增执行，已生成的补抄任务仍会完成。每次触发请求保留请求号，重复提交不会重复建立执行。</p>
      </el-tab-pane>
      <el-tab-pane label="执行与补抄" name="runs">
        <el-button type="primary" @click="process">立即检查到期任务</el-button>
        <el-table :data="runs" stripe @row-click="showTasks"><el-table-column prop="id" label="执行号" width="85" /><el-table-column prop="name" label="计划" /><el-table-column prop="created_at" label="创建时间" /><el-table-column prop="total" label="总表数" /><el-table-column prop="succeeded" label="成功" /><el-table-column prop="pending" label="等待/补抄" /><el-table-column prop="failed" label="失败" /><el-table-column label="详情"><template #default="{row}"><el-button link @click.stop="showTasks(row)">查看每表记录</el-button></template></el-table-column></el-table>
        <el-alert v-if="!runs.length" title="尚无执行。创建计划后等待调度，或手动触发。" type="info" :closable="false" />
        <el-card class="packet"><template #header>七字段模拟报文接入</template><p class="muted">表号 | ISO本地时间 | 瞬时流量(m³/h) | 累计流量(m³) | 水温(℃) | OPEN/CLOSED | 报警码（0正常）</p><el-input v-model="packet" type="textarea" :rows="2" placeholder="WM-A001-0001|2026-09-18T12:00:00|0.1|1231.50|25|OPEN|0" /><el-button @click="submitPacket" class="submit">校验、保存并出账</el-button></el-card>
      </el-tab-pane>
      <el-tab-pane label="违约金规则与流水" name="penalty">
        <el-alert :title="policyBasis" type="info" :closable="false" />
        <p>升级起算下限：{{ installationDate }}。仅计算昨日及以前已结束的自然日，按账单与日期唯一登记；规则保留历史版本，新配置不追溯修改已登记日期。</p>
        <el-form label-position="top" class="form-grid">
          <el-form-item label="启用"><el-switch v-model="policy.enabled" /></el-form-item><el-form-item label="宽限天数"><el-input-number v-model="policy.graceDays" :min="0" :max="365" /></el-form-item><el-form-item label="日费率（0.001 = 0.1%）"><el-input-number v-model="policy.dailyRate" :min="0" :max="0.05" :step="0.001" :precision="6" /></el-form-item><el-form-item label="累计封顶占本金比例"><el-input-number v-model="policy.capRatio" :min="0" :max="1" :step="0.01" :precision="6" /></el-form-item><el-form-item label="新版本生效日期"><el-date-picker v-model="policy.effectiveFrom" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        </el-form><el-button type="primary" @click="savePolicy">保存新版本</el-button><el-button @click="accrue">运行每日计费</el-button>
        <el-table :data="ledger" stripe><el-table-column prop="bill_no" label="账单" min-width="210" /><el-table-column prop="accrual_date" label="计费日期" /><el-table-column prop="principal_outstanding" label="日末未付本金/元" /><el-table-column prop="amount" label="当日违约金/元" /><el-table-column prop="policy_id" label="规则版本" /></el-table>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="taskDialog" title="每表采集与补抄记录" width="90%"><el-table :data="tasks"><el-table-column prop="meter_id" label="水表ID" width="85" /><el-table-column label="状态" width="100"><template #default="{row}">{{ statuses[row.status] || row.status }}</template></el-table-column><el-table-column prop="attempts" label="已尝试" width="80" /><el-table-column prop="max_attempts" label="上限" width="70" /><el-table-column prop="next_attempt_at" label="下次尝试" width="180" /><el-table-column prop="reading_id" label="读数ID" width="90" /><el-table-column prop="last_error" label="失败原因" min-width="160" /><el-table-column prop="packet" label="模拟原报文" min-width="260" /></el-table></el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { request } from '@/api'
const tab=ref('plans'), busy=ref(false), plans=ref([]), meters=ref([]), runs=ref([]), tasks=ref([]), ledger=ref([]), packet=ref(''), taskDialog=ref(false)
const today=()=>new Date().toLocaleDateString('sv-SE')
const form=reactive({name:'模拟日常采集',meterIds:[],intervalMinutes:1440,adaptive:true,maxAttempts:3,retrySeconds:30,scenario:'normal',incrementAmount:1})
const policy=reactive({enabled:false,graceDays:3,dailyRate:0.001,capRatio:0.1,effectiveFrom:today()})
const coverage=reactive({enabled:false,intervalMinutes:1440,incrementAmount:1})
async function saveCoverage(){await action(()=>request.put('/automation/coverage-policy',coverage),'自主覆盖策略已保存')}
const policyBasis=ref(''),installationDate=ref(''),statuses={pending:'等待采集',retry:'等待补抄',success:'成功',failed:'已失败'}
const base='/automation'; let timer; let selectedRun
async function loadRows() { const result=await Promise.all([request.get(`${base}/plans`),request.get(`${base}/runs`),request.get(`${base}/penalty-ledger`)]); [plans.value,runs.value,ledger.value]=result.map(r=>r.data); if(taskDialog.value && selectedRun) tasks.value=(await request.get(`${base}/runs/${selectedRun}/tasks`)).data }
async function refresh() { busy.value=true; try { await loadRows(); const [m,p]=await Promise.all([request.get(`${base}/meters`),request.get(`${base}/penalty-policy`)]); meters.value=m.data; const d=p.data; Object.assign(policy,{enabled:!!d.enabled,graceDays:d.grace_days,dailyRate:Number(d.daily_rate),capRatio:Number(d.cap_ratio),effectiveFrom:d.effective_from<today()?today():d.effective_from});policyBasis.value=d.basis;installationDate.value=d.installationDate } finally {busy.value=false} }
async function action(fn,message) { if(busy.value)return;busy.value=true;try{await fn();ElMessage.success(message);await loadRows()}finally{busy.value=false} }
async function createPlan(){await action(()=>request.post(`${base}/plans`,form),'计划已创建')}
async function toggle(row){await action(()=>request.put(`${base}/plans/${row.id}/enabled`,{enabled:!row.enabled}),'计划状态已更新')}
const triggerKeys=new Map()
async function trigger(row){const key=triggerKeys.get(row.id)||crypto.randomUUID();triggerKeys.set(row.id,key);await action(async()=>{await request.post(`${base}/plans/${row.id}/trigger`,{requestKey:key});triggerKeys.delete(row.id)},'执行已创建，等待后台采集')}
async function process(){await action(()=>request.post(`${base}/process`),'已检查到期任务')}
async function showTasks(row){selectedRun=row.id;tasks.value=(await request.get(`${base}/runs/${row.id}/tasks`)).data;taskDialog.value=true}
async function submitPacket(){await action(()=>request.post(`${base}/simulated-report`,{packet:packet.value}),'报文已接受并出账')}
async function savePolicy(){await action(()=>request.put(`${base}/penalty-policy`,policy),'规则新版本已保存')}
async function accrue(){await action(()=>request.post(`${base}/penalties/run`),'每日计费完成；重复运行不会重复计费')}
onMounted(async()=>{const c=(await request.get('/automation/coverage-policy')).data;Object.assign(coverage,{enabled:!!c.enabled,intervalMinutes:c.interval_minutes,incrementAmount:Number(c.increment_amount)});await refresh();timer=setInterval(()=>{if(!busy.value)loadRows().catch(()=>{})},10000)})
onUnmounted(()=>clearInterval(timer))
</script>

<style scoped>
.automation{padding:24px;max-width:1600px;margin:auto}.heading{display:flex;align-items:center;justify-content:space-between}.heading h2{margin:0}.heading p,.muted{color:#768397;font-size:13px;line-height:1.7}.el-tabs{margin-top:22px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:0 24px}.el-select{width:100%}.el-table{margin-top:20px}.packet{margin-top:26px}.submit{margin-top:12px}.el-alert{margin:12px 0}@media(max-width:760px){.automation{padding:12px}.form-grid{grid-template-columns:1fr}}
</style>
