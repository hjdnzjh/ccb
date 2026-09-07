<template>
  <div class="login-page">
    <div class="login-atmosphere" aria-hidden="true" />
    <div class="login-shell">
      <section class="brand-pane">
        <p class="eyebrow">AquaMind Decision OS</p>
        <h1>面向智慧水务的<br />AI智能运营决策平台</h1>
        <p class="lead">
          不只是增删改查后台——系统主动发现漏损、解释抄表可信度，并给出可执行运营方案。
        </p>
        <ul class="bullets">
          <li>AI主动研判 · 漏水概率与处置优先级</li>
          <li>数字孪生地图 · 分区健康态势一目了然</li>
          <li>人机协同 · 低置信度抄表自动转入审核</li>
        </ul>
      </section>

      <section class="form-pane">
        <div class="form-card">
          <h2>进入运营中心</h2>
          <p class="hint">演示账号 admin / admin123</p>
          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form" @submit.prevent>
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" placeholder="用户名" prefix-icon="User" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="密码"
                prefix-icon="Lock"
                show-password
                size="large"
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="loginForm.remember">记住登录状态</el-checkbox>
            </el-form-item>
            <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
              进入 AI 决策平台
            </el-button>
          </el-form>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loginFormRef = ref()
const loading = ref(false)

const loginForm = reactive({
  username: 'admin',
  password: 'admin123',
  remember: true
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const { authApi } = await import('@/api')
    const res = await authApi.login({
      username: loginForm.username,
      password: loginForm.password
    })
    const data = res.data || {}
    localStorage.setItem('token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(data.userInfo || {}))
    ElMessage.success(res.message || '登录成功')
    router.push('/')
  } catch (error) {
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background: #06141b;
}

.login-atmosphere {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 50% at 20% 30%, rgba(5, 191, 219, 0.35), transparent 55%),
    radial-gradient(ellipse 60% 40% at 80% 70%, rgba(8, 131, 149, 0.28), transparent 50%),
    linear-gradient(160deg, #06141b 0%, #0b2430 45%, #123445 100%);
  animation: drift 14s ease-in-out infinite alternate;
}

@keyframes drift {
  from { transform: scale(1) translateY(0); }
  to { transform: scale(1.05) translateY(-12px); }
}

.login-shell {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 32px;
  padding: 48px clamp(24px, 6vw, 80px);
  align-items: center;
}

.brand-pane {
  color: #e8f7f8;
  max-width: 560px;

  .eyebrow {
    font-size: 13px;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: #7ce7f4;
    margin-bottom: 16px;
  }

  h1 {
    font-family: var(--font-display);
    font-size: clamp(32px, 4.5vw, 52px);
    line-height: 1.12;
    margin-bottom: 18px;
  }

  .lead {
    font-size: 16px;
    line-height: 1.7;
    color: rgba(232, 247, 248, 0.78);
    margin-bottom: 24px;
  }

  .bullets {
    list-style: none;
    display: grid;
    gap: 10px;

    li {
      padding-left: 18px;
      position: relative;
      color: rgba(232, 247, 248, 0.88);
      font-size: 14px;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 0.55em;
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #05bfdb;
        box-shadow: 0 0 12px #05bfdb;
      }
    }
  }
}

.form-pane {
  display: flex;
  justify-content: flex-end;
}

.form-card {
  width: min(100%, 420px);
  background: rgba(255, 255, 255, 0.94);
  border-radius: 22px;
  padding: 36px 32px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.35);
  border: 1px solid rgba(5, 191, 219, 0.2);

  h2 {
    font-family: var(--font-display);
    font-size: 24px;
    margin-bottom: 6px;
    color: #0b2430;
  }

  .hint {
    color: #5a7380;
    font-size: 13px;
    margin-bottom: 22px;
  }

  .login-btn {
    width: 100%;
    height: 46px;
    font-size: 15px;
    font-weight: 600;
  }
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
    padding-top: 36px;
  }
  .form-pane {
    justify-content: stretch;
  }
}
</style>
