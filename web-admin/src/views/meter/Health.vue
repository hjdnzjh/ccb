<template>
  <div class="health">
    <header class="panel intro">
      <div>
        <h3>水表健康指数</h3>
        <div class="sub">由电量、信号、故障状态、上传间隔、读数跳变等真实字段计算</div>
      </div>
      <el-button @click="load" :loading="loading">刷新</el-button>
    </header>
    <el-alert v-if="error" type="error" :title="error" show-icon />
    <div class="grid">
      <article v-for="m in list" :key="m.meterNo" class="panel card">
        <div class="top">
          <div>
            <h4>{{ m.meterNo }}</h4>
            <span class="pill pill-info">{{ m.area || '未分区' }}</span>
          </div>
          <div class="score" :class="scoreTone(m.health)">{{ m.health }}</div>
        </div>
        <div class="bar-label">健康度</div>
        <div class="bar"><i :style="{ width: m.health + '%' }" :class="scoreTone(m.health)" /></div>
        <p class="pred">预测：未来30天故障概率 <strong>{{ m.fault30d }}%</strong></p>
        <div class="reasons">
          <em>原因解释</em>
          <ul><li v-for="(r, i) in m.reasons" :key="i">{{ r }}</li></ul>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { opsApi } from '@/api'

const list = ref([])
const loading = ref(false)
const error = ref('')
const scoreTone = (h) => (h >= 80 ? 'ok' : h >= 60 ? 'warn' : 'bad')

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await opsApi.meterHealth()
    list.value = res.data || []
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped lang="scss">
.health { display:grid; gap:14px; }
.intro { padding:16px 18px; display:flex; justify-content:space-between; align-items:center; }
.grid { display:grid; grid-template-columns:repeat(auto-fill,minmax(280px,1fr)); gap:14px; }
.card { padding:16px; display:grid; gap:10px; }
.top { display:flex; justify-content:space-between; h4{font-family:var(--font-display); margin-bottom:6px;} }
.score { width:48px; height:48px; border-radius:14px; display:grid; place-items:center; color:#fff; font-family:var(--font-display); font-weight:700;
  &.ok{background:#2a9d8f;} &.warn{background:#c4a35a;} &.bad{background:#e07a5f;} }
.bar { height:10px; background:#e8f1f3; border-radius:999px; overflow:hidden;
  i{display:block;height:100%; &.ok{background:#2a9d8f;} &.warn{background:#c4a35a;} &.bad{background:#e07a5f;} } }
.reasons em{font-style:normal;font-size:12px;color:var(--color-muted);} ul{margin:6px 0 0;padding-left:18px;font-size:13px;}
</style>
