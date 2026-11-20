<template>
  <el-container class="admin-layout">
    <!-- 左侧菜单 -->
    <el-aside width="210px" class="aside">
      <div class="brand">
        <span class="brand-icon">🐾</span>
        <div>
          <div class="brand-name">爪爪到家</div>
          <div class="brand-sub">商家管理后台</div>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/admin/orders">
          <el-icon><Tickets /></el-icon>
          <span>订单审核</span>
          <el-tag v-if="unread > 0" type="danger" size="small" round class="menu-badge">{{ unread }}</el-tag>
        </el-menu-item>
        <el-menu-item index="/admin/services">
          <el-icon><Goods /></el-icon>
          <span>服务管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/servicers">
          <el-icon><UserFilled /></el-icon>
          <span>服务师管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <el-button text @click="router.push('/user/home')">
          <el-icon><Back /></el-icon> 回到用户端
        </el-button>
      </div>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-title">{{ route.meta.title }}</div>
        <div class="header-right">
          <!-- 消息中心：催单/新订单通知都从这里看 -->
          <el-badge :value="unread" :hidden="unread === 0" class="bell-badge">
            <el-button circle text @click="openMessages">
              <el-icon :size="18"><Bell /></el-icon>
            </el-button>
          </el-badge>

          <el-dropdown @command="handleCommand">
            <span class="admin-user">
              <el-avatar :size="28" style="background: #2f9e7e">{{ (user?.nickname || 'A').slice(0, 1) }}</el-avatar>
              <span>{{ user?.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 消息抽屉 -->
    <el-drawer v-model="messageVisible" title="消息通知" size="380px">
      <div class="message-actions">
        <el-button size="small" @click="handleReadAll">全部标记已读</el-button>
      </div>
      <el-empty v-if="messages.length === 0" description="暂无消息" />
      <div v-for="item in messages" :key="item.id" class="message-item" :class="{ unread: item.isRead === 0 }">
        <div class="message-head">
          <el-tag size="small" :type="item.type === 'URGE' ? 'danger' : 'success'">
            {{ item.type === 'URGE' ? '催单' : '订单' }}
          </el-tag>
          <span class="message-title">{{ item.title }}</span>
          <span class="message-time">{{ item.createTime }}</span>
        </div>
        <div class="message-content">{{ item.content }}</div>
      </div>
    </el-drawer>
  </el-container>
</template>

<script setup>
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUser, clearLogin } from '../store/user'
import { logout, listMessages, getUnreadCount, readAllMessages } from '../api'

const route = useRoute()
const router = useRouter()
const user = ref(getUser())
const unread = ref(0)
const messages = ref([])
const messageVisible = ref(false)

const activeMenu = computed(() => route.path)

async function loadUnread() {
  try {
    const data = await getUnreadCount()
    unread.value = data?.count || 0
  } catch (e) {
    /* 静默失败，不打扰用户 */
  }
}

async function openMessages() {
  messageVisible.value = true
  const data = await listMessages({ pageNum: 1, pageSize: 20 })
  messages.value = data?.list || []
}

async function handleReadAll() {
  await readAllMessages()
  ElMessage.success('已全部标记为已读')
  messages.value = messages.value.map((m) => ({ ...m, isRead: 1 }))
  unread.value = 0
}

function handleCommand(command) {
  if (command === 'logout') {
    logout().catch(() => {}).finally(() => {
      clearLogin()
      router.replace('/login')
    })
  }
}

let timer = null
onMounted(() => {
  loadUnread()
  // 30 秒轮询未读数。真实项目会用 WebSocket/SSE 推送，这里轮询够用且实现简单
  timer = setInterval(loadUnread, 30000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.aside {
  background: #fff;
  border-right: 1px solid #eef2f1;
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: sticky;
  top: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  border-bottom: 1px solid #f2f5f4;
}

.brand-icon {
  font-size: 26px;
}

.brand-name {
  font-weight: 700;
  color: #2f9e7e;
  font-size: 16px;
}

.brand-sub {
  font-size: 12px;
  color: #9aaba7;
}

.menu {
  border-right: none;
  flex: 1;
}

.menu-badge {
  margin-left: auto;
}

.aside-footer {
  padding: 12px;
  border-top: 1px solid #f2f5f4;
}

.header {
  background: #fff;
  border-bottom: 1px solid #eef2f1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.admin-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
  font-size: 14px;
}

.main {
  background: var(--page-bg);
  padding: 20px;
}

.message-actions {
  margin-bottom: 12px;
}

.message-item {
  border: 1px solid #eef2f1;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
}

.message-item.unread {
  border-color: #c2e3d9;
  background: #f7fcfa;
}

.message-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.message-title {
  font-weight: 600;
  font-size: 13px;
}

.message-time {
  margin-left: auto;
  color: #9aaba7;
  font-size: 12px;
}

.message-content {
  font-size: 13px;
  color: #4a5c58;
  line-height: 1.6;
}
</style>
