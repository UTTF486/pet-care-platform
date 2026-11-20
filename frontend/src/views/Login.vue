<template>
  <div class="login-page">
    <!-- 左侧品牌区（纯展示） -->
    <div class="brand-side">
      <div class="brand-inner">
        <div class="brand-logo">🐾 爪爪到家</div>
        <h1 class="brand-title">让每一只毛孩子<br />都被好好照顾</h1>
        <p class="brand-desc">
          上门喂养 · 上门洗护 · 遛狗陪玩 · 健康陪护<br />
          专业服务师上门，全程拍照反馈，出差旅行不再担心
        </p>
        <div class="brand-points">
          <div class="point"><el-icon><CircleCheck /></el-icon> 时段预约，到点上门</div>
          <div class="point"><el-icon><CircleCheck /></el-icon> 服务过程留痕，可评价可追溯</div>
          <div class="point"><el-icon><CircleCheck /></el-icon> 超时未接单自动取消，不耽误行程</div>
        </div>
      </div>
    </div>

    <!-- 右侧表单区 -->
    <div class="form-side">
      <el-card class="login-card" shadow="never">
        <el-tabs v-model="mode" stretch>
          <!-- ============ 登录 ============ -->
          <el-tab-pane label="登录" name="login">
            <el-form ref="loginRef" :model="loginForm" :rules="loginRules" label-position="top" @keyup.enter="handleLogin">
              <el-form-item label="账号" prop="username">
                <el-input v-model="loginForm.username" placeholder="请输入账号" size="large" clearable>
                  <template #prefix><el-icon><User /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" size="large" show-password>
                  <template #prefix><el-icon><Lock /></el-icon></template>
                </el-input>
              </el-form-item>
              <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleLogin">
                登录
              </el-button>
            </el-form>

            <el-divider>演示账号</el-divider>
            <div class="demo-accounts">
              <div class="demo-item" @click="fillDemo('demo')">
                <el-tag size="small" type="success">用户端</el-tag>
                <span>demo / 123456</span>
              </div>
              <div class="demo-item" @click="fillDemo('admin')">
                <el-tag size="small" type="warning">商家后台</el-tag>
                <span>admin / 123456</span>
              </div>
            </div>
          </el-tab-pane>

          <!-- ============ 注册 ============ -->
          <el-tab-pane label="注册" name="register">
            <el-form ref="registerRef" :model="registerForm" :rules="registerRules" label-position="top">
              <el-form-item label="账号" prop="username">
                <el-input v-model="registerForm.username" placeholder="3-20 位字母或数字" size="large" clearable />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="registerForm.password" type="password" placeholder="6-20 位" size="large" show-password />
              </el-form-item>
              <el-form-item label="昵称" prop="nickname">
                <el-input v-model="registerForm.nickname" placeholder="怎么称呼你" size="large" clearable />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="registerForm.phone" placeholder="用于服务师联系" size="large" clearable />
              </el-form-item>
              <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="handleRegister">
                注册并登录
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, register } from '../api'
import { saveLogin } from '../store/user'

const router = useRouter()
const route = useRoute()

const mode = ref('login')
const loading = ref(false)
const loginRef = ref()
const registerRef = ref()

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ username: '', password: '', nickname: '', phone: '' })

const loginRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 20, message: '长度需为 3-20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度需为 6-20 位', trigger: 'blur' }
  ],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

function fillDemo(type) {
  mode.value = 'login'
  loginForm.username = type
  loginForm.password = '123456'
}

/** 登录成功后的跳转：商家去后台，用户回原页面或首页 */
function afterLogin(data) {
  saveLogin(data)
  ElMessage.success(`欢迎回来，${data.user.nickname}`)
  if (data.user.role === 1) {
    router.replace('/admin/dashboard')
  } else {
    router.replace(route.query.redirect || '/user/home')
  }
}

async function handleLogin() {
  await loginRef.value.validate()
  loading.value = true
  try {
    const data = await login(loginForm)
    afterLogin(data)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  await registerRef.value.validate()
  loading.value = true
  try {
    const data = await register(registerForm)
    afterLogin(data)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  background: #f5f7f6;
}

.brand-side {
  flex: 1.1;
  background: linear-gradient(135deg, #2f9e7e 0%, #48b894 55%, #7fd0b7 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.brand-inner {
  max-width: 460px;
}

.brand-logo {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
  margin-bottom: 36px;
}

.brand-title {
  font-size: 36px;
  line-height: 1.4;
  margin: 0 0 18px;
  font-weight: 700;
}

.brand-desc {
  font-size: 14px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.9);
  margin: 0 0 32px;
}

.brand-points {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.point {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.95);
}

.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  border-radius: 16px;
  border: 1px solid #e8eeec;
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
}

.demo-accounts {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.demo-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #4a5c58;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 8px;
  transition: background 0.2s;
}

.demo-item:hover {
  background: #f0f7f5;
}

@media (max-width: 900px) {
  .brand-side {
    display: none;
  }
}
</style>
