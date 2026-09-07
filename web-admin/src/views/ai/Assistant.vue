<template>
  <div class="assistant panel">
    <div class="panel-head">
      <div>
        <h3>水务智能助手</h3>
        <div class="sub">问题经 water-service 聚合库内数据，并尝试调用 agent-engine</div>
      </div>
      <span class="pill pill-info">{{ sourceLabel }}</span>
    </div>

    <div class="chat-shell">
      <div class="messages" ref="listRef">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <template v-if="m.role === 'assistant' && m.structured">
              <p class="title">分析</p>
              <ul><li v-for="(a, idx) in m.structured.analysis" :key="idx">{{ a }}</li></ul>
              <p class="title">建议</p>
              <ol><li v-for="(a, idx) in m.structured.actions" :key="'act'+idx">{{ a }}</li></ol>
            </template>
            <template v-else>{{ m.text }}</template>
          </div>
        </div>
      </div>

      <div class="quick">
        <el-tag v-for="q in quickAsks" :key="q" class="q" effect="plain" @click="ask(q)">{{ q }}</el-tag>
      </div>

      <div class="composer">
        <el-input v-model="input" type="textarea" :rows="2" placeholder="例如：为什么本月水费收入下降？" @keydown.enter.exact.prevent="ask()" />
        <el-button type="primary" :loading="thinking" @click="ask()">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { opsApi } from '@/api'

const input = ref('')
const thinking = ref(false)
const listRef = ref()
const sourceLabel = ref('等待提问')
const quickAsks = ['为什么本月水费收入下降？', 'A区是不是漏水？', '哪些水表需要优先巡检？']
const messages = ref([
  { role: 'assistant', text: '你好，我会基于数据库实时统计回答运营问题（并可联动 agent-engine）。' }
])

const scrollBottom = async () => {
  await nextTick()
  if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
}

const ask = async (preset) => {
  const text = (preset || input.value || '').trim()
  if (!text || thinking.value) return
  messages.value.push({ role: 'user', text })
  input.value = ''
  thinking.value = true
  await scrollBottom()
  try {
    const res = await opsApi.assistant(text)
    const data = res.data || {}
    sourceLabel.value = data.source === 'agent-engine+db' ? 'agent-engine + DB' : '数据库推理'
    messages.value.push({
      role: 'assistant',
      structured: { analysis: data.analysis || [], actions: data.actions || [] }
    })
  } catch (e) {
    messages.value.push({ role: 'assistant', text: '调用失败：' + (e.message || '未知错误') })
  } finally {
    thinking.value = false
    await scrollBottom()
  }
}
</script>

<style scoped lang="scss">
.assistant { padding: 18px; min-height: calc(100vh - 120px); display: flex; flex-direction: column; }
.chat-shell { flex: 1; display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.messages { flex: 1; overflow: auto; padding: 8px 4px 16px; display: flex; flex-direction: column; gap: 12px; max-height: calc(100vh - 280px); }
.msg {
  display: flex;
  &.user { justify-content: flex-end; }
  .bubble { max-width: min(720px, 92%); padding: 14px 16px; border-radius: 16px; line-height: 1.6; font-size: 14px; }
  &.user .bubble { background: linear-gradient(120deg,#0a4d68,#088395); color:#fff; border-bottom-right-radius:4px; }
  &.assistant .bubble { background:#f3fbfc; border:1px solid var(--color-border); border-bottom-left-radius:4px; }
  .title { font-weight:700; margin:8px 0 4px; color:var(--teal-700); &:first-child{margin-top:0;} }
  ul,ol { padding-left:18px; margin:0 0 6px; }
}
.quick { display:flex; flex-wrap:wrap; gap:8px; .q{cursor:pointer;} }
.composer { display:grid; grid-template-columns:1fr auto; gap:10px; align-items:end; }
</style>
