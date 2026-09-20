<template>
  <el-badge :value="error ? '!' : inbox.unreadCount" :max="99" :hidden="!error && !inbox.unreadCount">
    <button class="notice-bell" type="button" :aria-label="error ? '消息中心，刷新失败' : `消息中心，${inbox.unreadCount} 条未读`" title="消息中心" @click="show">
      <el-icon :size="22"><Bell /></el-icon>
    </button>
  </el-badge>
  <el-drawer v-model="opened" title="消息中心" size="min(580px, 100vw)" append-to-body class="notification-drawer">
    <template #header>
      <div class="notice-heading"><span class="notice-heading-icon"><el-icon :size="24"><Bell /></el-icon></span><div><h2>消息中心</h2><p>业务动态，及时掌握</p></div></div>
    </template>
    <div class="notice-summary">
      <div><strong>{{ inbox.unreadCount }}</strong><span>当前未读</span></div>
      <div><strong>{{ inbox.items.length }}</strong><span>当前消息</span></div>
      <el-button :loading="loading" :disabled="saving" @click="load">刷新消息</el-button>
    </div>
    <p class="notice-hint">{{ isAdmin ? '异常、工单、到期账单、采集失败、用户反馈与诊断进度' : '您的到期账单、反馈回复与诊断进度' }}。已读不会改变业务状态；诊断结果可回看。</p>
    <el-alert v-if="error" :title="error" description="当前可能显示上次结果，请点击刷新重试。" type="error" show-icon :closable="false" class="notice-alert" />
    <el-alert v-if="inbox.truncated" title="部分分类消息较多，每类仅展示最近 100 条。完整记录请前往对应业务页面。" type="warning" show-icon :closable="false" class="notice-alert" />
    <nav class="notice-categories" aria-label="消息分类">
      <button v-for="tab in categories" :key="tab.value" type="button" :class="{active:category===tab.value}" :aria-pressed="category===tab.value" @click="category=tab.value">
        {{ tab.label }} <span>{{ count(tab.value) }}</span>
      </button>
    </nav>
    <el-input v-model="search" placeholder="搜索消息标题、编号或内容" clearable aria-label="搜索消息" />
    <div class="notice-toolbar">
      <el-radio-group v-model="readFilter" size="small" aria-label="阅读状态"><el-radio-button label="all">全部</el-radio-button><el-radio-button label="unread">未读</el-radio-button></el-radio-group>
      <el-button link type="primary" :disabled="!unreadFiltered.length || loading || saving || !!error" :loading="saving" @click="mark(unreadFiltered,true)">当前筛选全部已读</el-button>
    </div>
    <div v-loading="loading" class="notice-list" aria-live="polite">
      <el-empty v-if="!filtered.length && !error" :description="readFilter==='unread' ? '当前没有未读消息' : '暂无符合条件的消息'" :image-size="90" />
      <article v-for="item in paged" :key="item.key" class="notice-card" :class="{unread:!item.read}">
        <div class="notice-meta"><el-tag :type="item.priority" size="small" effect="plain">{{ categoryLabel(item.category) }}</el-tag><time>{{ date(item.occurredAt) }}</time><span v-if="!item.read" class="unread-dot" aria-label="未读"></span></div>
        <button type="button" class="notice-title" :disabled="saving || loading" @click="view(item)">{{ item.title }}</button>
        <p class="notice-preview">{{ item.content }}</p>
        <div class="notice-actions">
          <el-button link type="primary" :disabled="saving || loading" @click="view(item)">查看详情</el-button>
          <el-button link :disabled="saving || loading || !!error" @click="mark([item],!item.read)">{{ item.read ? '标为未读' : '标为已读' }}</el-button>
          <el-button link type="primary" :disabled="saving || loading" @click="go(item)">{{ destination(item) }} →</el-button>
        </div>
      </article>
    </div>
    <el-pagination v-if="filtered.length>10" v-model:current-page="page" :total="filtered.length" :page-size="10" layout="prev, pager, next" small class="notice-pagination" />
    <template #footer><div class="notice-footer"><span>每类最多 {{ inbox.perCategoryLimit }} 条 · {{ updatedLabel }}</span><el-button @click="opened=false">关闭</el-button></div></template>
  </el-drawer>
  <el-dialog v-model="detailOpen" title="消息详情" width="min(620px,94vw)" append-to-body>
    <template v-if="selected"><el-tag :type="selected.priority">{{ categoryLabel(selected.category) }}</el-tag><h3 class="notice-detail-title">{{ selected.title }}</h3><p class="notice-hint">{{ date(selected.occurredAt) }} · 来源记录 #{{ selected.sourceId }}</p><p class="notice-detail-content">{{ selected.content }}</p><p class="notice-hint">此内容为打开时的消息快照，最新处理状态以业务页面为准。</p></template>
    <template #footer><el-button @click="detailOpen=false">关闭</el-button><el-button v-if="selected" type="primary" @click="go(selected)">{{ destination(selected) }}</el-button></template>
  </el-dialog>
  <BillDetailDialog v-model="billOpen" :bill-id="billId" :api="isAdmin ? billApi : meApi" append-to-body />
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { notificationApi, billApi, meApi } from '@/api/index.js'
import BillDetailDialog from '@/components/BillDetailDialog.vue'
const props=defineProps({isAdmin:Boolean})
const route=useRoute(),router=useRouter()
const opened=ref(false),loading=ref(false),saving=ref(false),error=ref('')
const inbox=ref({items:[],unreadCount:0,perCategoryLimit:100,truncated:false,refreshedAt:null})
const category=ref('all'),readFilter=ref('all'),search=ref(''),page=ref(1)
const detailOpen=ref(false),selected=ref(null),billOpen=ref(false),billId=ref(null)
const labels={all:'全部',anomaly:'异常预警',workorder:'工单待办',bill:'账单提醒',automation:'采集失败',feedback:'用户反馈',diagnosis:'诊断进度'}
const categoryLabel=value=>value==='feedback'&&!props.isAdmin?'反馈回复':labels[value]
const categories=computed(()=>Object.keys(labels).filter(key=>props.isAdmin||['all','bill','feedback','diagnosis'].includes(key)).map(value=>({value,label:categoryLabel(value)})))
const count=value=>inbox.value.items.filter(item=>(value==='all'||item.category===value)&&!item.read).length
const filtered=computed(()=>inbox.value.items.filter(item=>(category.value==='all'||item.category===category.value)&&(readFilter.value==='all'||!item.read)&&`${item.title} ${item.content}`.toLowerCase().includes(search.value.trim().toLowerCase())))
const paged=computed(()=>filtered.value.slice((page.value-1)*10,page.value*10))
const unreadFiltered=computed(()=>filtered.value.filter(item=>!item.read))
const date=value=>value?dayjs(value).format('MM-DD HH:mm'):'时间未记录'
const updatedLabel=computed(()=>inbox.value.refreshedAt?`${dayjs(inbox.value.refreshedAt).format('HH:mm:ss')} 更新`:'尚未获取')
const destination=item=>item.category==='bill'?'查看账单':({anomaly:'进入异常记录',workorder:'进入工单管理',automation:'进入采集管理',feedback:props.isAdmin?'处理用户反馈':'查看反馈进度',diagnosis:props.isAdmin?'查看诊断证据':'查看用水异常进度'})[item.category]
watch([category,readFilter,search],()=>{page.value=1})
watch(()=>filtered.value.length,n=>{page.value=Math.min(page.value,Math.max(1,Math.ceil(n/10)))})
let disposed=false,timer
async function load(){
  if(loading.value||saving.value||disposed)return
  loading.value=true
  try{const res=await notificationApi.inbox();if(!disposed){inbox.value=res.data;error.value=''}}
  catch(e){if(!disposed)error.value=e.response?.data?.message||'消息加载失败'}
  finally{if(!disposed)loading.value=false}
}
function show(){opened.value=true;load()}
async function mark(items,read){
  if(saving.value||loading.value||!items.length)return
  saving.value=true
  try{await notificationApi.mark(items.map(item=>item.key),read);error.value=''}
  catch(e){error.value=e.response?.data?.message||'阅读状态保存失败，请重试';return}
  finally{saving.value=false}
  await load()
}
function view(item){selected.value=item;detailOpen.value=true;if(!item.read)mark([item],true)}
async function go(item){
  if(!item.read)await mark([item],true)
  detailOpen.value=false
  if(item.category==='bill'){billId.value=item.sourceId;billOpen.value=true;return}
  opened.value=false
  await router.push(item.targetPath+(props.isAdmin&&['feedback','anomaly','workorder','diagnosis'].includes(item.category)?`?focus=${item.sourceId}`:''))
}
function refreshVisible(){if(document.visibilityState==='visible')load()}
watch(()=>route.fullPath,refreshVisible)
onMounted(()=>{load();timer=setInterval(refreshVisible,60000);document.addEventListener('visibilitychange',refreshVisible);window.addEventListener('focus',refreshVisible)})
onBeforeUnmount(()=>{disposed=true;clearInterval(timer);document.removeEventListener('visibilitychange',refreshVisible);window.removeEventListener('focus',refreshVisible)})
</script>

<style scoped>
.notice-bell{display:grid;place-items:center;width:38px;height:38px;border:0;border-radius:12px;color:#174e60;background:transparent;cursor:pointer}.notice-bell:hover,.notice-bell:focus-visible{background:#e4f5f7;outline:2px solid #0c8495;outline-offset:2px}
.notice-heading{display:flex;align-items:center;gap:14px}.notice-heading-icon{display:grid;place-items:center;width:48px;height:48px;border-radius:16px;color:#087f90;background:#e4f6f7}.notice-heading h2{margin:0;font-size:22px;color:#123947}.notice-heading p{margin:5px 0 0;font-size:13px;color:#73878e}
.notice-summary{display:flex;gap:32px;align-items:center;background:linear-gradient(115deg,#edf9fa,#f7fbff);border:1px solid #dbedf1;padding:18px 22px;border-radius:16px}.notice-summary>div{display:grid;gap:4px}.notice-summary strong{font-size:30px;color:#076c80;line-height:1.2}.notice-summary span{font-size:12px;color:#63818b}.notice-summary>.el-button{margin-left:auto}
.notice-hint{font-size:12px;line-height:1.8;color:#6e828b;margin:14px 0}.notice-alert{margin-bottom:12px}.notice-categories{display:flex;gap:8px;overflow-x:auto;padding:2px 0 14px}.notice-categories button{white-space:nowrap;border:1px solid #dde9ec;background:white;color:#536b76;padding:8px 12px;border-radius:20px;cursor:pointer;font:inherit;font-size:12px}.notice-categories button.active{background:#087f90;color:white;border-color:#087f90}.notice-categories span{margin-left:4px;font-variant-numeric:tabular-nums}.notice-toolbar{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:16px 0}
.notice-list{min-height:140px;display:grid;gap:12px}.notice-card{padding:18px;border:1px solid #e4eaed;border-radius:14px;background:#fff}.notice-card.unread{border-color:#b9e0e6;background:#f7fcfd;box-shadow:inset 3px 0 #07899b}.notice-meta{display:flex;align-items:center;gap:10px}.notice-meta time{font-size:12px;color:#788b94;margin-left:auto}.unread-dot{width:7px;height:7px;background:#07899b;border-radius:50%}.notice-title{display:block;text-align:left;border:0;background:transparent;font:inherit;font-size:15px;font-weight:600;color:#183b4b;padding:12px 0 0;cursor:pointer;overflow-wrap:anywhere}.notice-preview{font-size:13px;line-height:1.8;color:#647982;margin:9px 0 14px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;overflow-wrap:anywhere}.notice-actions{display:flex;gap:12px;flex-wrap:wrap}.notice-actions .el-button{font-size:12px;margin-left:0}.notice-actions .el-button:last-child{margin-left:auto}.notice-pagination{justify-content:center;margin-top:18px}.notice-footer{display:flex;align-items:center;justify-content:space-between;gap:8px}.notice-footer>span{font-size:12px;color:#73878e}.notice-detail-title{overflow-wrap:anywhere;line-height:1.6}.notice-detail-content{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;background:#f4fafb;padding:18px;border-radius:12px}
@media(max-width:480px){.notice-summary{gap:18px;padding:16px}.notice-summary strong{font-size:24px}.notice-toolbar{flex-wrap:wrap}.notice-card{padding:14px}}
</style>
<style>.notification-drawer .el-drawer__header{margin-bottom:0;padding:24px;border-bottom:1px solid #e8f0f2}.notification-drawer .el-drawer__body{padding:20px 24px}.notification-drawer .el-drawer__footer{padding:16px 24px;border-top:1px solid #e8f0f2}@media(max-width:480px){.notification-drawer .el-drawer__body{padding:16px}.notification-drawer .el-drawer__header{padding:20px 16px}}</style>
