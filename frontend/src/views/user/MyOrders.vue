<template>
  <div class="page">
    <h2 class="page-title">我的订单</h2>
    <p class="page-desc">预约进度、催单、评价都在这里</p>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane v-for="tab in tabs" :key="tab.name" :label="tab.label" :name="tab.name" />
    </el-tabs>

    <div v-loading="loading" class="order-list">
      <el-empty v-if="!loading && orders.length === 0" description="还没有订单，去看看服务吧">
        <el-button type="primary" @click="router.push('/user/home')">去预约</el-button>
      </el-empty>

      <div v-for="order in orders" :key="order.id" class="order-card">
        <div class="order-head">
          <span class="order-no">订单号 {{ order.orderNo }}</span>
          <span class="create-time">{{ order.createTime }}</span>
          <el-tag :type="statusType(order.status)" effect="light" class="status-tag">{{ order.statusText }}</el-tag>
        </div>

        <div class="order-body">
          <div class="order-main">
            <div class="service-name">{{ order.serviceName }}</div>
            <div class="order-meta">
              <span><el-icon><Avatar /></el-icon> {{ order.petName || '宠物' }}</span>
              <span><el-icon><Calendar /></el-icon> {{ order.appointDate }} {{ order.timeSlot }}</span>
              <span><el-icon><Location /></el-icon> {{ order.address }}</span>
            </div>
          </div>
          <div class="order-amount">
            <div class="price">{{ order.amount }}</div>
            <div v-if="order.servicerName" class="servicer">服务师：{{ order.servicerName }}</div>
          </div>
        </div>

        <div class="order-footer">
          <el-alert
            v-if="order.status === 0"
            type="warning"
            :closable="false"
            show-icon
            class="footer-tip"
            :title="`商家需在 ${order.expireTime} 前接单，超时将自动取消`"
          />
          <div class="actions">
            <el-button size="small" @click="openDetail(order)">查看详情</el-button>
            <el-button v-if="order.status === 0" size="small" type="warning" plain @click="handleUrge(order)">
              催单
            </el-button>
            <el-button
              v-if="order.status === 0 || order.status === 1"
              size="small"
              type="danger"
              plain
              @click="handleCancel(order)"
            >
              取消订单
            </el-button>
            <el-button
              v-if="order.status === 2 && !order.reviewed"
              size="small"
              type="primary"
              @click="openReview(order)"
            >
              去评价
            </el-button>
            <el-tag v-if="order.status === 2 && order.reviewed" type="success" size="small">已评价</el-tag>
          </div>
        </div>
      </div>

      <div v-if="total > pageSize" class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="loadOrders"
        />
      </div>
    </div>

    <!-- 订单详情 -->
    <el-dialog v-model="detailVisible" title="订单详情" width="560px">
      <el-descriptions v-if="current" :column="1" border>
        <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="服务项目">{{ current.serviceName }}</el-descriptions-item>
        <el-descriptions-item label="服务宠物">{{ current.petName }}（{{ current.petType }}）</el-descriptions-item>
        <el-descriptions-item label="预约时间">{{ current.appointDate }} {{ current.timeSlot }}</el-descriptions-item>
        <el-descriptions-item label="上门地址">{{ current.address }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ current.contactName }} {{ current.contactPhone }}</el-descriptions-item>
        <el-descriptions-item label="金额">
          <span class="price">{{ current.amount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ current.remark || '无' }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusType(current.status)">{{ current.statusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="current.cancelReason" label="取消原因">
          {{ current.cancelReason }}
        </el-descriptions-item>
      </el-descriptions>

      <el-timeline v-if="current" class="timeline">
        <el-timeline-item :timestamp="current.createTime" type="primary">提交预约</el-timeline-item>
        <el-timeline-item v-if="current.acceptTime" :timestamp="current.acceptTime" type="success">
          商家接单{{ current.servicerName ? '，指派 ' + current.servicerName : '' }}
        </el-timeline-item>
        <el-timeline-item v-if="current.finishTime" :timestamp="current.finishTime" type="success">服务完成</el-timeline-item>
        <el-timeline-item v-if="current.cancelTime" :timestamp="current.cancelTime" type="info">订单取消</el-timeline-item>
      </el-timeline>
    </el-dialog>

    <!-- 评价 -->
    <el-dialog v-model="reviewVisible" title="评价本次服务" width="440px">
      <div class="review-row">
        <span>服务评分</span>
        <el-rate v-model="reviewForm.rating" show-text :texts="['很差', '较差', '一般', '满意', '非常满意']" />
      </div>
      <el-input
        v-model="reviewForm.content"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        placeholder="说说服务师的表现，帮其他铲屎官做参考"
      />
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, listMyOrders, reviewOrder, urgeOrder } from '../../api'

const router = useRouter()

const tabs = [
  { label: '全部', name: 'all' },
  { label: '待接单', name: '0' },
  { label: '待服务', name: '1' },
  { label: '已完成', name: '2' },
  { label: '已取消', name: '3' }
]

const activeTab = ref('all')
const orders = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(5)
const total = ref(0)

const detailVisible = ref(false)
const reviewVisible = ref(false)
const current = ref(null)
const submitting = ref(false)
const reviewForm = reactive({ orderId: null, rating: 5, content: '' })

/** 状态 -> el-tag 颜色 */
function statusType(status) {
  return { 0: 'warning', 1: 'primary', 2: 'success', 3: 'info' }[status] || 'info'
}

async function loadOrders() {
  loading.value = true
  try {
    const status = activeTab.value === 'all' ? undefined : Number(activeTab.value)
    const data = await listMyOrders({ status, pageNum: pageNum.value, pageSize: pageSize.value })
    orders.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function handleTabChange() {
  pageNum.value = 1
  loadOrders()
}

function openDetail(order) {
  current.value = order
  detailVisible.value = true
}

async function handleUrge(order) {
  await urgeOrder(order.id)
  ElMessage.success('已提醒商家，请耐心等待')
}

async function handleCancel(order) {
  // 用输入框收集取消原因，方便后续统计用户流失原因
  const { value } = await ElMessageBox.prompt('请填写取消原因，方便我们改进服务', '取消订单', {
    confirmButtonText: '确认取消',
    cancelButtonText: '再想想',
    inputPlaceholder: '如：临时出差、时间冲突',
    inputValidator: (val) => (val && val.trim().length > 0 ? true : '请填写取消原因')
  })
  await cancelOrder(order.id, value)
  ElMessage.success('订单已取消')
  loadOrders()
}

function openReview(order) {
  reviewForm.orderId = order.id
  reviewForm.rating = 5
  reviewForm.content = ''
  reviewVisible.value = true
}

async function submitReview() {
  submitting.value = true
  try {
    await reviewOrder({ ...reviewForm })
    ElMessage.success('感谢你的评价')
    reviewVisible.value = false
    loadOrders()
  } finally {
    submitting.value = false
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.order-list {
  min-height: 200px;
}

.order-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 18px;
  margin-bottom: 14px;
  box-shadow: 0 2px 12px rgba(31, 45, 42, 0.05);
}

.order-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #eef2f1;
  font-size: 13px;
  color: #6b7c78;
}

.order-no {
  font-weight: 600;
  color: #1f2d2a;
}

.create-time {
  color: #9aaba7;
}

.status-tag {
  margin-left: auto;
}

.order-body {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 0;
}

.order-main {
  flex: 1;
}

.service-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

.order-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 13px;
  color: #6b7c78;
}

.order-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.order-amount {
  text-align: right;
}

.order-amount .price {
  font-size: 20px;
}

.servicer {
  font-size: 12px;
  color: #9aaba7;
  margin-top: 4px;
}

.order-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px dashed #eef2f1;
}

.footer-tip {
  flex: 1;
  padding: 6px 10px;
}

.actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.timeline {
  margin-top: 20px;
}

.review-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
  font-size: 14px;
}
</style>
