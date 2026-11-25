<template>
  <div class="user-layout">
    <!-- 顶部导航 -->
    <header class="header">
      <div class="header-inner">
        <div class="logo">
          <span class="logo-icon">🐾</span>
          <span class="logo-text">爪爪到家</span>
          <span class="logo-sub">宠物上门服务</span>
        </div>

        <nav class="nav">
          <router-link to="/user/home" class="nav-item" active-class="active">预约服务</router-link>
          <router-link to="/user/orders" class="nav-item" active-class="active">我的订单</router-link>
          <router-link to="/user/pets" class="nav-item" active-class="active">宠物档案</router-link>
        </nav>

        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-avatar :size="30" style="background: #2f9e7e">{{ avatarText }}</el-avatar>
            <span class="nickname">{{ user?.nickname || '未登录' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="isAdminUser" command="admin">进入商家后台</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="main">
      <router-view />
    </main>

    <ChangePasswordDialog v-model="passwordVisible" />
    <footer class="footer">智慧宠物服务上门平台</footer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUser, isAdmin, clearLogin } from '../store/user'
import { logout } from '../api'
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue'

const router = useRouter()
const user = computed(() => getUser())
const isAdminUser = computed(() => isAdmin())
const avatarText = computed(() => (user.value?.nickname || 'U').slice(0, 1))
const passwordVisible = ref(false)

function handleCommand(command) {
  if (command === 'admin') {
    router.push('/admin/dashboard')
  }
  if (command === 'password') {
    passwordVisible.value = true
  }
  if (command === 'logout') {
    // 即使接口失败也要清本地，保证用户能退出
    logout().catch(() => {}).finally(() => {
      clearLogin()
      ElMessage.success('已退出登录')
      router.replace('/login')
    })
  }
}
</script>

<style scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background: #fff;
  border-bottom: 1px solid #e8eeec;
  position: sticky;
  top: 0;
  z-index: 10;
}

.header-inner {
  max-width: 1180px;
  margin: 0 auto;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 40px;
  padding: 0 20px;
}

.logo {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.logo-icon {
  font-size: 22px;
}

.logo-text {
  font-size: 19px;
  font-weight: 700;
  color: #2f9e7e;
  letter-spacing: 1px;
}

.logo-sub {
  font-size: 12px;
  color: #8fa39e;
}

.nav {
  display: flex;
  gap: 8px;
  flex: 1;
}

.nav-item {
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 14px;
  color: #4a5c58;
  transition: all 0.2s;
}

.nav-item:hover {
  background: #f0f7f5;
  color: #2f9e7e;
}

.nav-item.active {
  background: #e8f5f1;
  color: #2f9e7e;
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}

.nickname {
  font-size: 14px;
  color: #1f2d2a;
}

.main {
  flex: 1;
}

.footer {
  text-align: center;
  color: #9aaba7;
  font-size: 12px;
  padding: 20px 0 28px;
}
</style>
