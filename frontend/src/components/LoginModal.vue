<template>
  <!-- 登录弹窗：B站风格，三个 Tab（密码登录 / 验证码登录 / 注册） -->
  <div class="modal-mask" @click.self="close">
    <div class="modal">
      <!-- 顶部：关闭按钮 -->
      <button class="close-btn" @click="close">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
        </svg>
      </button>

      <!-- Tab 切换 -->
      <div class="tabs">
        <span
          v-for="tab in tabs"
          :key="tab.key"
          class="tab"
          :class="{ active: activeTab === tab.key }"
          @click="switchTab(tab.key)"
        >
          {{ tab.label }}
        </span>
      </div>

      <!-- ============ 密码登录 ============ -->
      <div v-if="activeTab === 'pwd'" class="form">
        <input v-model="pwdForm.username" class="field" placeholder="用户名" @keyup.enter="handlePwdLogin" />
        <input
          v-model="pwdForm.password"
          class="field"
          type="password"
          placeholder="密码"
          @keyup.enter="handlePwdLogin"
        />
        <button class="submit-btn" :disabled="loading" @click="handlePwdLogin">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </div>

      <!-- ============ 验证码登录 ============ -->
      <div v-if="activeTab === 'phone'" class="form">
        <input v-model="phoneForm.phone" class="field" placeholder="手机号" @keyup.enter="handlePhoneLogin" />
        <div class="code-row">
          <input
            v-model="phoneForm.code"
            class="field"
            placeholder="验证码（见后端控制台）"
            @keyup.enter="handlePhoneLogin"
          />
          <button class="code-btn" :disabled="countdown > 0" @click="handleSendCode">
            {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
          </button>
        </div>
        <button class="submit-btn" :disabled="loading" @click="handlePhoneLogin">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </div>

      <!-- ============ 注册 ============ -->
      <div v-if="activeTab === 'register'" class="form">
        <input v-model="regForm.username" class="field" placeholder="用户名" />
        <input v-model="regForm.phone" class="field" placeholder="手机号" />
        <div class="code-row">
          <input v-model="regForm.code" class="field" placeholder="验证码（见后端控制台）" />
          <button class="code-btn" :disabled="countdown > 0" @click="handleSendCode">
            {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
          </button>
        </div>
        <input v-model="regForm.password" class="field" type="password" placeholder="设置密码" />
        <button class="submit-btn" :disabled="loading" @click="handleRegister">
          {{ loading ? '注册中...' : '注册' }}
        </button>
      </div>

      <!-- 提示信息（成功/失败） -->
      <p v-if="message" class="message" :class="{ error: isError }">{{ message }}</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { login, phoneLogin, register, sendCode } from '../api/auth'
import { setToken, setUser, userStore } from '../store/user'

const router = useRouter()

// 三个 Tab 的配置
const tabs = [
  { key: 'pwd', label: '密码登录' },
  { key: 'phone', label: '验证码登录' },
  { key: 'register', label: '注册' }
]

const activeTab = ref('pwd')
const loading = ref(false)
const countdown = ref(0)
const message = ref('')
const isError = ref(false)
let timer = null

// 三个表单的输入数据
const pwdForm = reactive({ username: '', password: '' })
const phoneForm = reactive({ phone: '', code: '' })
const regForm = reactive({ username: '', phone: '', code: '', password: '' })

/** 关闭弹窗（仅关闭界面，不清空表单） */
function close() {
  userStore.loginVisible = false
}

/** 切换 Tab，并清空提示 */
function switchTab(key) {
  activeTab.value = key
  message.value = ''
}

/** 显示提示信息 */
function showMessage(msg, error = false) {
  message.value = msg
  isError.value = error
}

/** 登录成功后的公共处理：保存 token + 用户信息 + 关闭弹窗 */
function onLoginSuccess(data) {
  setToken(data.token)
  setUser(data.user)
  close()
  // 若之前在访问需登录的页面（如投稿/充值）时被路由守卫拦截，登录后自动跳回目标页
  const target = userStore.redirectTo
  userStore.redirectTo = null
  if (target) {
    router.push(target)
  }
}

// 弹窗关闭/组件销毁时清理倒计时定时器，防止定时器泄漏
onBeforeUnmount(() => {
  clearInterval(timer)
})

/** 密码登录 */
async function handlePwdLogin() {
  if (!pwdForm.username || !pwdForm.password) {
    return showMessage('请输入用户名和密码', true)
  }
  loading.value = true
  try {
    const res = await login({ username: pwdForm.username, password: pwdForm.password })
    onLoginSuccess(res.data)
  } catch (e) {
    showMessage(e.message, true)
  } finally {
    loading.value = false
  }
}

/** 验证码登录 */
async function handlePhoneLogin() {
  if (!phoneForm.phone || !phoneForm.code) {
    return showMessage('请输入手机号和验证码', true)
  }
  loading.value = true
  try {
    const res = await phoneLogin({ phone: phoneForm.phone, code: phoneForm.code })
    onLoginSuccess(res.data)
  } catch (e) {
    showMessage(e.message, true)
  } finally {
    loading.value = false
  }
}

/** 注册 */
async function handleRegister() {
  const { username, phone, code, password } = regForm
  if (!username || !phone || !code || !password) {
    return showMessage('请填写完整的注册信息', true)
  }
  loading.value = true
  try {
    await register({ username, password, phone, code })
    showMessage('注册成功，请登录')
    // 注册成功后切到密码登录 Tab，并带入用户名
    switchTab('pwd')
    pwdForm.username = username
  } catch (e) {
    showMessage(e.message, true)
  } finally {
    loading.value = false
  }
}

/** 发送验证码 + 60 秒倒计时 */
async function handleSendCode() {
  const phone = activeTab.value === 'phone' ? phoneForm.phone : regForm.phone
  if (!phone) {
    return showMessage('请输入手机号', true)
  }
  try {
    await sendCode(phone)
    showMessage('验证码已发送，请查看后端控制台')
    // 启动 60 秒倒计时
    countdown.value = 60
    clearInterval(timer)
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    showMessage(e.message, true)
  }
}
</script>

<style scoped>
/* 半透明遮罩层 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

/* 弹窗主体 */
.modal {
  position: relative;
  width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 32px 40px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.3);
}

/* 右上角关闭按钮 */
.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  padding: 4px;
}

.close-btn:hover {
  color: #fb7299;
}

/* Tab 切换 */
.tabs {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
}

.tab {
  font-size: 18px;
  color: #999;
  cursor: pointer;
  padding-bottom: 6px;
  border-bottom: 2px solid transparent;
}

.tab.active {
  color: #fb7299;
  font-weight: bold;
  border-bottom-color: #fb7299;
}

/* 表单 */
.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  height: 44px;
  border: none;
  border-bottom: 1px solid #e0e0e0;
  outline: none;
  font-size: 15px;
  padding: 0 4px;
  transition: border-color 0.2s;
}

.field:focus {
  border-bottom-color: #fb7299;
}

/* 验证码行：输入框 + 获取按钮 */
.code-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.code-row .field {
  flex: 1;
}

.code-btn {
  height: 40px;
  padding: 0 16px;
  border: 1px solid #fb7299;
  background: #fff;
  color: #fb7299;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}

.code-btn:disabled {
  border-color: #ccc;
  color: #ccc;
  cursor: not-allowed;
}

/* 提交按钮：粉色渐变 */
.submit-btn {
  height: 46px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(90deg, #fb7299, #f7598c);
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  margin-top: 4px;
  transition: opacity 0.2s;
}

.submit-btn:hover {
  opacity: 0.9;
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 提示信息 */
.message {
  margin-top: 16px;
  font-size: 13px;
  color: #2a9d5f;
  text-align: center;
}

.message.error {
  color: #e04343;
}
</style>
