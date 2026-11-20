<template>
  <div class="page">
    <h2 class="page-title">订单审核</h2>
    <p class="page-desc">待接单的订单排在最前面，接单后请指派服务师</p>

    <div class="toolbar">
      <el-select v-model="status" placeholder="订单状态" clearable style="width: 150px" @change="reload">
        <el-option label="待接单" :value="0" />
        <el-option label="待服务" :value="1" />
        <el-option label="已完成" :value="2" />
        <el-option label="已取消" :value="3" />
      </el-select>
      <el-input
        v-model="keyword"
        placeholder="订单号 / 联系人 / 手机号 / 地址"
        clearable
        style="width: 280px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <el-tag type="warning" effect="plain">待接单 {{ pendingCount }} 单</el-tag>
    </div>

    <el-table :data="orders" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="orderNo" label="订单号" width="170" />
      <el-table-column prop="userNickname" label="下单用户" width="110" />
      <el-table-column prop="serviceName" label="服务项目" min-width="150" show-overflow-tooltip />
      <el-table-column label="宠物" width="100">
        <template #default="{ row }">{{ row.petName || '-' }}</template>
      </el-table-column>
      <el-table-column label="预约时间" width="180">
        <template #default="{ row }">{{ row.appointDate }} {{ row.timeSlot }}</template>
      </el-table-column>
      <el-table-column label="金额" width="100">
        <template #default="{ row }"><span class="price">{{ row.amount }}</span></template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" effect="light">{{ row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="servicerName" label="服务师" width="100">
        <template #default="{ row }">{{ row.servicerName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="下单时间" width="165" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" size="small" type="primary" @click="openAccept(row)">接单</el-button>
          <el-button v-if="row.status === 0" size="small" type="danger" plain @click="handleReject(row)">拒单</el-button>
          <el-button v-if="row.status === 1" size="small" type="success" @click="handleFinish(row)">完成</el-button>
          <el-button size="small" text @click="openDetail(row)">详情</el-button>
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
        @current-change="loadOrders"
      />
    </div>

    <!-- 接单：选择服务师 -->
    <el-dialog v-model="acceptVisible" title="接单并指派服务师" width="440px">
      <el-form label-width="90px">
        <el-form-item label="订单号">
          <span>{{ current?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="服务项目">
          <span>{{ current?.serviceName }}</span>
        </el-form-item>
        <el-form-item label="服务师">
          <el-select v-model="selectedServicerId" placeholder="请选择可接单的服务师" style="width: 100%">
            <el-option
              v-for="s in servicers"
              :key="s.id"
              :label="`${s.name} · ${levelText(s.level)} · 评分 ${s.rating}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="servicers.length === 0" type="warning" :closable="false" show-icon title="当前没有可接单的服务师，请先到服务师管理调整状态" />
      <template #footer>
        <el-button @click="acceptVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAccept">确认接单</el-button>
      </template>
    </el-dialog>

    <!-- 订单详情 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="560px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="服务项目">{{ current.serviceName }}（{{ current.servicePrice }} 元）</el-descriptions-item>
        <el-descriptions-item label="宠物">{{ current.petName }}（{{ current.petType }}）</el-descriptions-item>
        <el-descriptions-item label="预约时间">{{ current.appointDate }} {{ current.timeSlot }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ current.contactName }} {{ current.contactPhone }}</el-descriptions-item>
        <el-descriptions-item label="地址">{{ current.address }}</el-descriptions-item>
        <el-descriptions-item label="用户备注">{{ current.remark || '无' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(current.status)">{{ current.statusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.cancelReason" label="取消原因">{{ current.cancelReason }}</el-descriptions-item>
      </el-descriptions>
      <el-timeline style="margin-top: 18px">
        <el-timeline-item v-if="current" :timestamp="current.createTime" type="primary">用户下单</el-timeline-item>
        <el-timeline-item v-if="current?.acceptTime" :timestamp="current.acceptTime" type="success">商家接单</el-timeline-item>
        <el-timeline-item v-if="current?.finishTime" :timestamp="current.finishTime" type="success">服务完成</el-timeline-item>
        <el-timeline-item v-if="current?.cancelTime" :timestamp="current.cancelTime" type="info">订单取消</el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminAcceptOrder,
  adminAvailableServicers,
  adminFinishOrder,
  adminListOrders,
  adminRejectOrder,
  statsOverview
} from '../../api'

const route = useRoute()

const orders = ref([])
const loading = ref(false)
const status = ref(route.query.status !== undefined ? Number(route.query.status) : null)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const pendingCount = ref(0)

const acceptVisible = ref(false)
const detailVisible = ref(false)
const current = ref(null)
const servicers = ref([])
const selectedServicerId = ref(null)
const submitting = ref(false)

function statusType(s) {
  return { 0: 'warning', 1: 'primary', 2: 'success', 3: 'info' }[s] || 'info'
}

function levelText(level) {
  return { 1: '初级', 2: '中级', 3: '高级' }[level] || '初级'
}

async function loadOrders() {
  loading.value = true
  try {
    const data = await adminListOrders({
      status: status.value,
      keyword: keyword.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    orders.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadPendingCount() {
  const data = await statsOverview()
  pendingCount.value = data?.pendingCount || 0
}

function reload() {
  pageNum.value = 1
  loadOrders()
}

function reset() {
  status.value = null
  keyword.value = ''
  reload()
}

async function openAccept(row) {
  current.value = row
  selectedServicerId.value = null
  servicers.value = await adminAvailableServicers()
  acceptVisible.value = true
}

async function handleAccept() {
  if (!selectedServicerId.value) {
    ElMessage.warning('请选择服务师')
    return
  }
  submitting.value = true
  try {
    await adminAcceptOrder(current.value.id, selectedServicerId.value)
    ElMessage.success('接单成功')
    acceptVisible.value = false
    await Promise.all([loadOrders(), loadPendingCount()])
  } finally {
    submitting.value = false
  }
}

async function handleFinish(row) {
  await ElMessageBox.confirm('确认服务已完成？完成后用户将收到评价提醒。', '完成服务', { type: 'info' })
  await adminFinishOrder(row.id)
  ElMessage.success('订单已完成')
  loadOrders()
}

async function handleReject(row) {
  const { value } = await ElMessageBox.prompt('请填写拒单原因，会通知给用户', '拒单', {
    inputPlaceholder: '如：该时段服务师已排满',
    inputValidator: (val) => (val && val.trim() ? true : '请填写拒单原因')
  })
  await adminRejectOrder(row.id, value)
  ElMessage.success('已拒单并通知用户')
  await Promise.all([loadOrders(), loadPendingCount()])
}

function openDetail(row) {
  current.value = row
  detailVisible.value = true
}

onMounted(() => {
  loadOrders()
  loadPendingCount()
})
</script>
