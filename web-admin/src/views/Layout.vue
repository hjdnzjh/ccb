<template>
  <div class="layout-container">
    <aside class="sidebar" :class="{ collapsed: isCollapsed }">
      <div class="logo">
        <div class="logo-mark">
          <el-icon :size="20"><Odometer /></el-icon>
        </div>
        <div class="logo-text" v-show="!isCollapsed">
          <strong>AquaMind</strong>
          <span>水务AI运营决策平台</span>
        </div>
      </div>

      <el-menu :default-active="activeMenu" :collapse="isCollapsed" router>
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
      </el-menu>
    </aside>

    <div class="main-container">
      <header class="header">
        <div class="left">
          <el-icon class="collapse-btn" style="cursor: pointer; font-size: 20px;" @click="isCollapsed = !isCollapsed">
            <component :is="isCollapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.meta?.title || item.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <span class="pill pill-info header-chip">决策模式 · 主动发现</span>
        </div>

        <div class="right">
          <el-button class="btn-ai" size="small" round @click="$router.push('/ai/assistant')">
            <el-icon><ChatDotRound /></el-icon>
            问AI
          </el-button>
          <el-badge :value="signalCount" :max="99" :hidden="!signalCount">
            <el-icon style="cursor: pointer; font-size: 20px;" @click="$router.push('/dashboard')"><Bell /></el-icon>
          </el-badge>

          <el-dropdown>
            <div style="display: flex; align-items: center; cursor: pointer;">
              <el-avatar :size="32" style="background: linear-gradient(135deg,#088395,#05bfdb);">
                {{ userInfo.realName?.charAt(0) || 'A' }}
              </el-avatar>
              <span style="margin-left: 8px;">{{ userInfo.realName || '运营官' }}</span>
              <el-icon style="margin-left: 4px;"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人中心</el-dropdown-item>
                <el-dropdown-item>系统设置</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { opsApi } from '@/api'

const route = useRoute()
const router = useRouter()
const isCollapsed = ref(false)
const signalCount = ref(0)

const activeMenu = computed(() => route.path)
const breadcrumbs = computed(() => route.matched.filter(item => item.meta?.title))
const userInfo = computed(() => {
  const info = localStorage.getItem('userInfo')
  return info ? JSON.parse(info) : {}
})

onMounted(() => {
  opsApi.cockpit().then(res => {
    signalCount.value = (res.data?.signals || []).length
  }).catch(() => {})
})

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    router.push('/login')
  })
}
</script>
