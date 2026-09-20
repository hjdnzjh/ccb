<template>
  <div class="assistant panel">
    <header class="panel-head">
      <div><h3>水务智能助手</h3><p class="sub">查询巡检优先级、漏水风险、登记收入与欠费，逐项展示业务依据</p></div>
      <el-button :disabled="thinking || messages.length === 0" @click="clear">清空对话</el-button>
    </header>
    <div class="capability">业务数据 · 规则分析<span>每条问题请写明对象和主题，当前不支持连续追问或自动执行操作。</span></div>
    <div class="chat-shell">
      <div ref="listRef" class="messages" aria-live="polite" :aria-busy="thinking">
        <div v-if="!messages.length" class="welcome">
          <h4>从一个具体问题开始</h4>
          <p>例如询问哪些水表需要优先巡检，我会列出表号、异常依据和下一步建议。</p>
          <p>数据不足时会说明缺少什么，不把风险提示当作已确认的故障。</p>
        </div>
        <article v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <template v-if="m.answer">
              <div class="answer-meta"><span>{{ m.answer.status === 'clarification' ? '需要明确查询条件' : '基于业务数据' }}</span><time>{{ m.answer.asOf }} · 北京时间</time></div>
              <h4>{{ m.answer.summary }}</h4>
              <p class="scope">查询范围：{{ m.answer.scope }}</p>
              <div v-if="m.answer.metrics?.length" class="metrics">
                <div v-for="metric in m.answer.metrics" :key="metric.label"><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong></div>
              </div>
              <div v-if="m.answer.rows?.length" class="answer-table" tabindex="0" aria-label="优先巡检水表及依据">
                <table>
                  <thead><tr><th>水表 / 区域</th><th>优先级</th><th>核查依据</th><th>下一步</th></tr></thead>
                  <tbody><tr v-for="row in m.answer.rows" :key="row.meterNo">
                    <td><strong>{{ row.meterNo }}</strong><small>{{ row.area }}</small><small>最后抄表：{{ row.lastReadingAt }}</small></td>
                    <td><span class="priority" :class="{ urgent: row.priority === '优先处理' }">{{ row.priority }}</span></td>
                    <td><ul><li v-for="reason in row.reasons" :key="reason">{{ reason }}</li></ul></td><td>{{ row.nextStep }}</td>
                  </tr></tbody>
                </table>
              </div>
              <details v-if="m.answer.evidence?.length" :open="m.answer.intent !== 'inspection'">
                <summary>{{ m.answer.status === 'clarification' ? '支持的问题示例' : '数据依据与统计口径' }}</summary>
                <ul><li v-for="item in m.answer.evidence" :key="item">{{ item }}</li></ul>
              </details>
              <div v-if="m.answer.caveats?.length" class="notes"><p v-for="note in m.answer.caveats" :key="note">{{ note }}</p></div>
              <nav v-if="m.answer.links?.length" class="answer-links" aria-label="相关业务页面">
                <router-link v-for="link in m.answer.links" :key="link.path" :to="link.path">{{ link.label }} →</router-link>
              </nav>
            </template>
            <template v-else><p>{{ m.text }}</p><el-button v-if="m.failedQuestion" size="small" :disabled="thinking" @click="ask(m.failedQuestion)">重试此问题</el-button></template>
          </div>
        </article>
        <div v-if="thinking" class="thinking" role="status">正在查询业务数据…</div>
      </div>
      <div class="quick"><button v-for="q in quickAsks" :key="q" type="button" :disabled="thinking" @click="ask(q)">{{ q }}</button></div>
      <form class="composer" @submit.prevent="ask()">
        <el-input v-model="input" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="写明对象和主题，例如：A区哪些水表需要优先巡检？" @keydown="onKeydown" @compositionstart="composing = true" @compositionend="composing = false" />
        <el-button native-type="submit" type="primary" :loading="thinking" :disabled="!input.trim()">发送</el-button>
      </form>
      <p class="input-hint">Enter 发送 · Shift + Enter 换行 · 收入统计采用已登记收款流水</p>
    </div>
  </div>
</template>

<script setup>
import { nextTick, ref } from 'vue'
import { opsApi } from '@/api'
const input = ref(''), thinking = ref(false), composing = ref(false), listRef = ref()
const quickAsks = ['哪些水表需要优先巡检？', 'A区是不是漏水？', '为什么本月水费收入下降？', '当前有多少欠费账单？']
const messages = ref([])
const scrollBottom = async (showAnswer = false) => {
  await nextTick()
  const list = listRef.value
  if (!list) return
  const answers = list.querySelectorAll('.msg.assistant')
  const latest = answers[answers.length - 1]
  list.scrollTop = showAnswer && latest
    ? list.scrollTop + latest.getBoundingClientRect().top - list.getBoundingClientRect().top
    : list.scrollHeight
}
const clear = () => { if (!thinking.value) messages.value = [] }
const onKeydown = (event) => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && !composing.value && event.keyCode !== 229) {
    event.preventDefault(); ask()
  }
}
const ask = async (preset) => {
  const text = (preset || input.value || '').trim()
  if (!text || thinking.value || text.length > 500) return
  messages.value.push({ role: 'user', text })
  input.value = ''; thinking.value = true
  await scrollBottom()
  try {
    const res = await opsApi.assistant(text)
    if (!res.data?.summary) throw new Error('未收到有效回答，请稍后重试')
    messages.value.push({ role: 'assistant', answer: res.data })
  } catch (e) {
    messages.value.push({ role: 'assistant', text: '本次查询未完成：' + (e.message || '请稍后重试'), failedQuestion: text })
  } finally {
    thinking.value = false
    await scrollBottom(true)
  }
}
</script>

<style scoped lang="scss">
.assistant { padding:20px; height:calc(100dvh - 115px); min-height:560px; display:flex; flex-direction:column; }
.panel-head { display:flex; justify-content:space-between; align-items:center; gap:16px; padding-bottom:12px; }
.sub,.scope,.input-hint { color:var(--color-muted); font-size:13px; line-height:1.6; }
.sub { margin-top:5px; }
.capability { font-size:12px; color:var(--teal-700); padding:10px 12px; background:#f0f8fa; border-radius:8px; margin-bottom:12px; span { color:var(--color-muted); margin-left:14px; } }
.chat-shell { flex:1; min-height:0; display:flex; flex-direction:column; gap:12px; }
.messages { flex:1; min-height:0; overflow:auto; padding:4px 6px 14px; display:flex; flex-direction:column; gap:18px; }
.welcome { margin:auto; padding:32px; text-align:center; color:var(--color-muted); line-height:1.8; h4{color:var(--color-text);font-size:20px;margin-bottom:12px;} }
.msg { display:flex; flex-shrink:0;
  .bubble { padding:18px; border-radius:14px; line-height:1.65; font-size:14px; min-width:0; overflow-wrap:anywhere; }
  &.user { justify-content:flex-end; .bubble { max-width:80%; background:var(--teal-700); color:#fff; } }
  &.assistant .bubble { width:100%; background:#f7fbfc; border:1px solid var(--color-border); }
  h4 { font-size:16px; margin:10px 0 6px; } ul{padding-left:18px;} }
.answer-meta { display:flex; justify-content:space-between; gap:12px; flex-wrap:wrap; color:var(--color-muted); font-size:12px; }
.metrics { display:flex; flex-wrap:wrap; gap:12px; margin:14px 0; div{padding:10px 16px;background:white;border:1px solid var(--color-border);border-radius:8px;min-width:145px;} span{display:block;color:var(--color-muted);font-size:12px;}strong{font-size:20px;color:var(--teal-700);} }
.answer-table { overflow-x:auto; margin:16px 0; border:1px solid var(--color-border); border-radius:8px;
  table{width:100%;border-collapse:collapse;text-align:left;background:white;min-width:700px;}
  th,td{padding:12px;vertical-align:top;border-bottom:1px solid var(--color-border);}
  th{background:#eaf4f6;font-size:12px;white-space:nowrap;}small{display:block;color:var(--color-muted);font-size:12px;}
  td:first-child{min-width:210px;}td:last-child{min-width:150px;}ul{margin:0;} }
.priority { color:#6e6533;white-space:nowrap;font-size:12px;&.urgent{color:#b45239;font-weight:700;} }
 details { margin:12px 0; summary{cursor:pointer;color:var(--teal-700);font-weight:600;}ul{margin-top:8px;} }
.notes { border-left:3px solid #d7bc77;padding:4px 12px;color:#776740;font-size:12px;p+p{margin-top:6px;} }
.answer-links { display:flex;flex-wrap:wrap;gap:16px;margin-top:14px;a{color:var(--teal-700);font-weight:600;} }
.quick { display:flex;flex-wrap:wrap;gap:8px;button{font:inherit;font-size:12px;color:var(--teal-700);background:white;border:1px solid var(--color-border);border-radius:18px;padding:7px 12px;cursor:pointer;&:disabled{opacity:.5;cursor:wait;}} }
.composer { display:grid;grid-template-columns:1fr auto;gap:10px;align-items:end; }
.thinking { color:var(--color-muted);font-size:13px;padding:10px; }.input-hint{font-size:11px;}
@media(max-width:700px){.assistant{padding:12px;height:calc(100dvh - 95px);}.capability span{display:block;margin:5px 0 0;}.msg .bubble{padding:12px;}.metrics div{min-width:115px;}.panel-head{align-items:flex-start;}}
</style>
