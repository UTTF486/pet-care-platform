<template>
  <div class="page">
    <h2 class="page-title">用户管理</h2>
    <p class="page-desc">禁用用户会立即踢下线（后端删除 Redis 登录态），下次请求就需要重新登录</p>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="账号 / 昵称 / 手机号"
        clearable
        style="width: 260px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" @click="reload">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="账号" width="140" />
      <el-table-column prop="nickname" label="昵称" width="150" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="角色" width="110">
        <template #default="{ row }">
          <el-tag :type="row.role === 1 ? 'warning' : 'info'" effect="plain">
            {{ row.role === 1 ? '商家管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            :disabled="row.role === 1"
            active-text="正常"
            inactive-text="禁用"
            inline-prompt
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="170" />
      <el-table-column label="说明" min-width="160">
        <template #default="{ row }">
          <span v-if="row.role === 1" class="muted">管理员账号不允许禁用</span>
          <span v-else-if="row.status === 1" class="muted">可正常下单预约</span>
          <span v-else class="danger-text">账号已禁用，无法登录</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        background
        @current-change="loadList"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminChangeUserStatus, adminListUsers } from '../../api'

const list = ref([])
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

async function loadList() {
  loading.value = true
  try {
    const data = await adminListUsers({
      keyword: keyword.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  loadList()
}

async function handleStatusChange(row, val) {
  try {
    // 禁用属于风险操作，先二次确认
    if (val === 0) {
      await ElMessageBox.confirm(`确定禁用「${row.nickname}」吗？该用户会被立即踢下线。`, '禁用确认', {
        type: 'warning'
      })
    }
    await adminChangeUserStatus(row.id, val)
    ElMessage.success(val === 1 ? '已启用' : '已禁用并踢下线')
  } catch (e) {
    row.status = val === 1 ? 0 : 1
  }
}

onMounted(loadList)
</script>

<style scoped>
.muted {
  color: #9aaba7;
  font-size: 12px;
}

.danger-text {
  color: #f56c6c;
  font-size: 12px;
}
</style>
