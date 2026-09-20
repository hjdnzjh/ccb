<template>
  <div class="layout-container">
    <button v-if="isMobile && !isCollapsed" class="navigation-backdrop" type="button" aria-label="收起导航菜单" @click="isCollapsed=true" />
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="logo">
        <div class="brand-lockup">
          <BrandLogo :compact="isCollapsed" />
        </div>
      </div>

      <el-menu :default-active="activeMenu" :collapse="isCollapsed" router>
        <el-menu-item v-if="!isAdmin" index="/portal">
          <el-icon><User /></el-icon>
          <template #title>我的用水工作台</template>
        </el-menu-item>
        <template v-if="isAdmin">
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <template #title>主页</template>
        </el-menu-item>

        <el-menu-item index="/ai/assistant">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>水务智能助手</template>
        </el-menu-item>

        <el-menu-item index="/twin/map">
          <el-icon><MapLocation /></el-icon>
          <template #title>数字孪生地图</template>
        </el-menu-item>

        <el-menu-item index="/meter/health">
          <el-icon><FirstAidKit /></el-icon>
          <template #title>水表健康指数</template>
        </el-menu-item>

        <el-sub-menu index="meter">
          <template #title>
            <el-icon><Odometer /></el-icon>
            <span>抄表可信度</span>
          </template>
          <el-menu-item index="/meter/reading">AI抄表解释</el-menu-item>
          <el-menu-item index="/meter/list">水表资产</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="bill">
          <template #title>
            <el-icon><Tickets /></el-icon>
            <span>智能收费</span>
          </template>
          <el-menu-item index="/bill/insight">收费策略洞察</el-menu-item>
          <el-menu-item index="/bill/list">账单列表</el-menu-item>
          <el-menu-item index="/bill/payment">缴费管理</el-menu-item>
          <el-menu-item index="/bill/tariff">居民年度计费</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="anomaly">
          <template #title>
            <el-icon><Warning /></el-icon>
            <span>异常与工单</span>
          </template>
          <el-menu-item index="/anomaly/list">异常记录</el-menu-item>
          <el-menu-item index="/anomaly/workorder">工单管理</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/user/list">
          <el-icon><User /></el-icon>
          <template #title>用户管理</template>
        </el-menu-item>

        <el-menu-item index="/report">
          <el-icon><Document /></el-icon>
          <template #title>统计报表</template>
        </el-menu-item>
        <el-menu-item index="/automation">
          <el-icon><Odometer /></el-icon>
          <template #title>自动采集与计费</template>
        </el-menu-item>
        <el-menu-item index="/feedback">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>用户反馈</template>
        </el-menu-item>
        <el-menu-item index="/diagnosis"><el-icon><FirstAidKit /></el-icon><template #title>诊断中心</template></el-menu-item>
        <el-menu-item index="/lab/replay"><el-icon><DataBoard /></el-icon><template #title>创新演练</template></el-menu-item>
        </template>
      </el-menu>
    </aside>

    <div class="main-container">
      <header class="header">
        <div class="left">
          <button class="navigation-toggle" type="button" aria-label="切换导航菜单" :aria-expanded="!isCollapsed" @click="isCollapsed = !isCollapsed">
            <el-icon :size="20"><component :is="isCollapsed ? 'Expand' : 'Fold'" /></el-icon>
          </button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta?.title || item.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <span class="pill pill-info header-chip">{{ isAdmin ? '决策模式 · 主动发现' : '个人用水服务' }}</span>
        </div>

        <div class="right">
          <el-button v-if="isAdmin" class="btn-ai" size="small" round @click="$router.push('/ai/assistant')">
            <el-icon><ChatDotRound /></el-icon>
            问AI
          </el-button>
          <NotificationCenter :is-admin="isAdmin" />

          <el-dropdown>
            <div style="display: flex; align-items: center; cursor: pointer;">
              <el-avatar :size="32" style="background: linear-gradient(135deg,#088395,#05bfdb);">
                {{ userInfo.realName?.charAt(0) || 'A' }}
              </el-avatar>
              <span class="header-user-name" style="margin-left: 8px;">{{ userInfo.realName || userInfo.username || '用户' }}</span>
              <el-icon style="margin-left: 4px;"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :disabled="loggingOut" @click="handleLogout">{{ loggingOut ? '正在退出…' : '退出登录' }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { authApi } from '@/api'
import { clearSession, readUser } from '@/utils/session.js'
import BrandLogo from '@/components/BrandLogo.vue'
import NotificationCenter from '@/components/NotificationCenter.vue'

const route = useRoute()
const router = useRouter()
const mobileMedia=window.matchMedia('(max-width: 960px)')
const isMobile=ref(mobileMedia.matches)
const isCollapsed = ref(isMobile.value)
function screenChanged(event){isMobile.value=event.matches;if(event.matches)isCollapsed.value=true}
onMounted(()=>mobileMedia.addEventListener('change',screenChanged))
onBeforeUnmount(()=>mobileMedia.removeEventListener('change',screenChanged))
watch(()=>route.fullPath,()=>{if(isMobile.value)isCollapsed.value=true})
const loggingOut = ref(false)

const activeMenu = computed(() => route.path)
const breadcrumbs = computed(() => route.matched.filter(item => item.meta?.title))
const userInfo = computed(() => {
  // Navigation validates and refreshes this cache before the layout renders.
  void route.fullPath
  return readUser()
})
const isAdmin = computed(() => userInfo.value.role === 'admin')

const handleLogout = async () => {
  if (loggingOut.value) return
  const confirmed = await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(() => true).catch(() => false)
  if (!confirmed) return
  loggingOut.value = true
  try {
    await authApi.logout()
    clearSession()
    await router.replace('/login')
  } catch (error) {
    // Keep a session whose revocation failed so the user can retry logout.
    if (error.response?.status === 401) { clearSession(); await router.replace('/login') }
  } finally { loggingOut.value = false }
}
</script>

<style scoped>
.navigation-toggle{display:grid;place-items:center;flex-shrink:0;border:0;background:transparent;color:inherit;width:32px;height:36px;cursor:pointer;border-radius:8px}.navigation-toggle:focus-visible{outline:2px solid #087f90;outline-offset:2px}.navigation-backdrop{position:fixed;inset:0;z-index:999;background:rgba(0,22,30,.35);border:0;cursor:pointer}
@media(max-width:960px){.layout-container .sidebar.collapsed{transform:translateX(-100%);visibility:hidden}.layout-container .sidebar{transition:transform .2s ease;width:248px}.layout-container .main-container .header{padding:0 16px;flex-shrink:0}.layout-container .main-container .header .left{min-width:0;gap:8px}.layout-container .main-container .header .right{gap:12px;flex-shrink:0}}
@media(max-width:600px){.header-user-name{display:none}.layout-container .main-container .header .btn-ai{display:none}.layout-container .main-container .header .el-breadcrumb{overflow:hidden;max-width:160px;white-space:nowrap}.layout-container .main-container .main-content{padding:16px}}
</style>
