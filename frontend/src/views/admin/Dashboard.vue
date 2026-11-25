<template>
  <div>
    <!-- 指标卡 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="stat-card">
          <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
            <el-icon :size="20"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-value">
              {{ card.value }}<span v-if="card.unit" class="stat-unit">{{ card.unit }}</span>
            </div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <div class="card chart-card">
      <div class="chart-head">
        <div>
          <h3 class="chart-title">订单与营收趋势</h3>
          <p class="chart-desc">营收只统计已完成订单，避免把待服务和已取消的金额算进来</p>
        </div>
        <el-radio-group v-model="type" size="small" @change="loadTrend">
          <el-radio-button value="day">按日</el-radio-button>
          <el-radio-button value="week">按周</el-radio-button>
          <el-radio-button value="month">按月</el-radio-button>
        </el-radio-group>
      </div>
      <div ref="trendRef" class="chart" v-loading="trendLoading" />
    </div>

    <el-row :gutter="16">
      <!-- 排行 -->
      <el-col :span="14">
        <div class="card chart-card">
          <div class="chart-head">
            <h3 class="chart-title">服务项目排行</h3>
            <span class="chart-desc">按订单量排序 TOP 5</span>
          </div>
          <div ref="rankRef" class="chart" style="height: 280px" v-loading="rankLoading" />
        </div>
      </el-col>

      <!-- 待办 -->
      <el-col :span="10">
        <div class="card todo-card">
          <h3 class="chart-title">今日待办</h3>
          <div class="todo-item" @click="router.push('/admin/orders?status=0')">
            <div>
              <div class="todo-value">{{ overview.pendingCount || 0 }}</div>
              <div class="todo-label">待接单订单</div>
            </div>
            <el-icon :size="22" color="#e6a23c"><Tickets /></el-icon>
          </div>
          <div class="todo-item">
            <div>
              <div class="todo-value">{{ overview.todayOrderCount || 0 }}</div>
              <div class="todo-label">今日新增订单</div>
            </div>
            <el-icon :size="22" color="#2f9e7e"><TrendCharts /></el-icon>
          </div>
          <div class="todo-item">
            <div>
              <div class="todo-value">{{ overview.goodRate || 0 }}%</div>
              <div class="todo-label">好评率（4 星及以上）</div>
            </div>
            <el-icon :size="22" color="#67c23a"><Star /></el-icon>
          </div>
          <el-button type="primary" plain class="todo-btn" @click="router.push('/admin/orders?status=0')">
            去处理订单
          </el-button>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { init, use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { statsOverview, statsRanking, statsTrend } from '../../api'

use([BarChart, LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()

const overview = reactive({})
const type = ref('day')
const trendLoading = ref(false)
const rankLoading = ref(false)
const trendRef = ref()
const rankRef = ref()

let trendChart = null
let rankChart = null

// type -> 统计周期数：最近 7 天 / 8 周 / 6 个月
const rangeMap = { day: 7, week: 8, month: 6 }

const cards = computed(() => [
  { label: '今日订单', value: overview.todayOrderCount ?? 0, unit: '单', icon: 'Tickets', bg: '#e8f5f1', color: '#2f9e7e' },
  { label: '今日营收', value: overview.todayRevenue ?? 0, unit: '元', icon: 'Money', bg: '#fdf3e7', color: '#e6a23c' },
  { label: '累计营收', value: overview.totalRevenue ?? 0, unit: '元', icon: 'TrendCharts', bg: '#eaf1fb', color: '#409eff' },
  { label: '好评率', value: overview.goodRate ?? 0, unit: '%', icon: 'Star', bg: '#fdeeee', color: '#f56c6c' }
])

async function loadOverview() {
  const data = await statsOverview()
  Object.assign(overview, data || {})
}

async function loadTrend() {
  trendLoading.value = true
  try {
    const list = await statsTrend(type.value, rangeMap[type.value])
    renderTrend(list || [])
  } finally {
    trendLoading.value = false
  }
}

async function loadRanking() {
  rankLoading.value = true
  try {
    const list = await statsRanking(type.value, rangeMap[type.value], 5)
    renderRank(list || [])
  } finally {
    rankLoading.value = false
  }
}

function renderTrend(list) {
  if (!trendChart) return
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单量', '营收'], right: 10, top: 0, textStyle: { color: '#6b7c78' } },
    grid: { left: 40, right: 46, top: 44, bottom: 30 },
    xAxis: {
      type: 'category',
      data: list.map((i) => i.label),
      axisLine: { lineStyle: { color: '#e0e7e5' } },
      axisLabel: { color: '#8fa39e' }
    },
    yAxis: [
      {
        type: 'value',
        name: '订单量',
        nameTextStyle: { color: '#8fa39e' },
        splitLine: { lineStyle: { color: '#f0f4f3' } },
        axisLabel: { color: '#8fa39e' }
      },
      {
        type: 'value',
        name: '营收(元)',
        nameTextStyle: { color: '#8fa39e' },
        splitLine: { show: false },
        axisLabel: { color: '#8fa39e' }
      }
    ],
    series: [
      {
        name: '订单量',
        type: 'bar',
        barWidth: 18,
        itemStyle: { color: '#7fd0b7', borderRadius: [6, 6, 0, 0] },
        data: list.map((i) => i.orderCount)
      },
      {
        name: '营收',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 6,
        itemStyle: { color: '#e6a23c' },
        lineStyle: { width: 3 },
        areaStyle: { color: 'rgba(230, 162, 60, 0.10)' },
        data: list.map((i) => i.revenue)
      }
    ]
  })
}

function renderRank(list) {
  if (!rankChart) return
  // ECharts 横向柱状图的 y 轴是从下往上画的，反过来更符合阅读习惯
  const sorted = [...list].reverse()
  rankChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 10, right: 40, top: 10, bottom: 10, containLabel: true },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f0f4f3' } },
      axisLabel: { color: '#8fa39e' }
    },
    yAxis: {
      type: 'category',
      data: sorted.map((i) => i.serviceName),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#4a5c58', width: 130, overflow: 'truncate' }
    },
    series: [
      {
        type: 'bar',
        barWidth: 14,
        itemStyle: { color: '#2f9e7e', borderRadius: [0, 7, 7, 0] },
        label: { show: true, position: 'right', color: '#6b7c78', formatter: '{c} 单' },
        data: sorted.map((i) => i.orderCount)
      }
    ]
  })
}

function handleResize() {
  trendChart?.resize()
  rankChart?.resize()
}

onMounted(async () => {
  // 先渲染骨架，再拉数据；图表必须等 DOM 有了才能 init
  await nextTick()
  trendChart = init(trendRef.value)
  rankChart = init(rankRef.value)

  await Promise.all([loadOverview(), loadTrend(), loadRanking()])
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  // 不 dispose 会内存泄漏（尤其切页面回来会重复 init）
  trendChart?.dispose()
  rankChart?.dispose()
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: 0 2px 12px rgba(31, 45, 42, 0.05);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.stat-unit {
  font-size: 13px;
  font-weight: 400;
  color: #8fa39e;
  margin-left: 4px;
}

.stat-label {
  font-size: 12px;
  color: #8fa39e;
  margin-top: 2px;
}

.chart-card {
  margin-bottom: 16px;
}

.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
}

.chart-title {
  margin: 0;
  font-size: 15px;
}

.chart-desc {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9aaba7;
}

.chart {
  height: 300px;
  width: 100%;
}

.todo-card {
  height: 100%;
}

.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px dashed #eef2f1;
  cursor: default;
}

.todo-value {
  font-size: 20px;
  font-weight: 700;
}

.todo-label {
  font-size: 12px;
  color: #8fa39e;
}

.todo-btn {
  width: 100%;
  margin-top: 16px;
}
</style>
