<template>
  <div class="insight">
    <header class="panel intro">
      <div>
        <h3>智能收费策略</h3>
        <div class="sub">对比本账期用量与历史均值（bill 表），异常高账单优先提醒而非硬催</div>
      </div>
      <el-button @click="load" :loading="loading">刷新</el-button>
    </header>
    <el-alert v-if="error" type="error" :title="error" show-icon />
    <el-empty v-if="!loading && !items.length" description="本账期暂无账单数据" />
    <div class="list">
      <article v-for="b in items" :key="b.billId" class="panel row">
        <div class="who">
          <h4>{{ b.user }} · {{ b.meterNo }}</h4>
          <p>
            历史均值 <strong>{{ b.avg12 }}</strong> 吨，本月 <strong :class="{ spike: b.growth > 50 }">{{ b.current }}</strong> 吨
            （{{ b.growth > 0 ? '+' : '' }}{{ b.growth }}%）
          </p>
        </div>
        <div class="hypo">
          <em>可能原因</em>
          <div class="tags"><span v-for="h in b.hypotheses" :key="h" class="pill pill-warn">{{ h }}</span></div>
        </div>
        <div class="suggest">
          <em>策略建议</em>
          <p>{{ b.suggest }}</p>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { opsApi } from '@/api'

const items = ref([])
const loading = ref(false)
const error = ref('')

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await opsApi.billingInsights()
    items.value = res.data || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped lang="scss">
.insight { display:grid; gap:14px; }
.intro { padding:16px 18px; display:flex; justify-content:space-between; align-items:center; }
.list { display:grid; gap:12px; }
.row { padding:16px 18px; display:grid; grid-template-columns:1.2fr .9fr 1.2fr; gap:16px; }
.who h4 { font-family:var(--font-display); margin-bottom:8px; }
.who p { color:var(--color-muted); line-height:1.55; font-size:14px; }
.spike { color:var(--coral-500); }
.hypo em, .suggest em { font-style:normal; font-size:12px; color:var(--color-muted); }
.tags { display:flex; flex-wrap:wrap; gap:6px; margin-top:8px; }
.suggest p { margin:8px 0 0; line-height:1.55; font-size:14px; }
@media (max-width:960px){ .row{grid-template-columns:1fr;} }
</style>
