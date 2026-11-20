<template>
  <div>
    <!-- 顶部 Banner -->
    <section class="hero">
      <div class="hero-inner">
        <div class="hero-text">
          <h1>出差、加班、旅行<br />家里的毛孩子交给我们</h1>
          <p>持证服务师上门，喂食铲屎、洗澡遛弯，全程拍照反馈，随时可查</p>
          <div class="hero-stats">
            <div class="stat"><b>1200+</b><span>累计服务次数</span></div>
            <div class="stat"><b>98%</b><span>用户好评率</span></div>
            <div class="stat"><b>30min</b><span>平均接单时长</span></div>
          </div>
        </div>
        <div class="hero-emoji">🐱🐶</div>
      </div>
    </section>

    <div class="page">
      <!-- 品类筛选 -->
      <div class="section-head">
        <h2 class="page-title">选择服务</h2>
        <p class="page-desc">按品类筛选，或直接搜索服务名称</p>
      </div>

      <div class="toolbar">
        <el-radio-group v-model="categoryId" @change="reload">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button v-for="c in categories" :key="c.id" :value="c.id">
            {{ c.name }}
          </el-radio-button>
        </el-radio-group>
        <el-input
          v-model="keyword"
          placeholder="搜索服务，如 喂猫、洗澡"
          clearable
          style="width: 240px"
          @keyup.enter="reload"
          @clear="reload"
        >
          <template #append>
            <el-button :icon="Search" @click="reload" />
          </template>
        </el-input>
      </div>

      <!-- 热门推荐：走 Redis 缓存 -->
      <div v-if="hotServices.length" class="hot-block">
        <div class="hot-title">
          <el-icon color="#e6532f"><StarFilled /></el-icon>
          <span>热门推荐</span>
          <el-tag size="small" type="danger" effect="plain">Redis 缓存</el-tag>
        </div>
        <div class="hot-list">
          <div v-for="item in hotServices" :key="'hot-' + item.id" class="hot-item" @click="goDetail(item.id)">
            <div class="hot-emoji">{{ emojiOf(item.categoryId) }}</div>
            <div class="hot-name">{{ item.name }}</div>
            <div class="price">{{ item.price }}</div>
          </div>
        </div>
      </div>

      <!-- 服务卡片列表 -->
      <div v-loading="loading" class="service-grid">
        <el-empty v-if="!loading && services.length === 0" description="没有找到相关服务" style="grid-column: 1 / -1" />
        <div v-for="item in services" :key="item.id" class="service-card" @click="goDetail(item.id)">
          <div class="card-cover">
            <span class="cover-emoji">{{ emojiOf(item.categoryId) }}</span>
            <el-tag class="cover-tag" size="small" effect="dark" type="success">{{ item.categoryName }}</el-tag>
          </div>
          <div class="card-body">
            <div class="card-name">{{ item.name }}</div>
            <div class="card-desc">{{ item.description || '专业服务师上门，全程反馈' }}</div>
            <div class="card-meta">
              <span class="price">{{ item.price }}</span>
              <span class="duration">约 {{ item.duration }} 分钟</span>
              <span class="sales">已售 {{ item.sales }}</span>
            </div>
            <el-button type="primary" class="book-btn" @click.stop="goDetail(item.id)">立即预约</el-button>
          </div>
        </div>
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
          @current-change="loadServices"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, StarFilled } from '@element-plus/icons-vue'
import { listCategories, listHotServices, listServices } from '../../api'

const router = useRouter()

const categories = ref([])
const hotServices = ref([])
const services = ref([])
const loading = ref(false)

const categoryId = ref(null)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(8)
const total = ref(0)

/** 品类 -> emoji，纯前端展示用，避免依赖图片资源 */
const emojiMap = { 1: '🍚', 2: '🛁', 3: '🦮', 4: '💊' }
function emojiOf(categoryId) {
  return emojiMap[categoryId] || '🐾'
}

async function loadServices() {
  loading.value = true
  try {
    const data = await listServices({
      categoryId: categoryId.value,
      keyword: keyword.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    services.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  loadServices()
}

function goDetail(id) {
  router.push(`/user/service/${id}`)
}

onMounted(async () => {
  // 三个请求互不依赖，并行发，首屏更快
  const [cats, hot] = await Promise.all([listCategories(), listHotServices()])
  categories.value = cats || []
  hotServices.value = hot || []
  loadServices()
})
</script>

<style scoped>
.hero {
  background: linear-gradient(120deg, #2f9e7e 0%, #4bbd99 60%, #8ad8c1 100%);
  color: #fff;
  padding: 52px 0 46px;
}

.hero-inner {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.hero-text h1 {
  font-size: 32px;
  line-height: 1.45;
  margin: 0 0 14px;
}

.hero-text p {
  margin: 0 0 26px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

.hero-stats {
  display: flex;
  gap: 40px;
}

.stat b {
  display: block;
  font-size: 22px;
}

.stat span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
}

.hero-emoji {
  font-size: 84px;
  opacity: 0.9;
}

.section-head {
  margin-bottom: 14px;
}

.hot-block {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(31, 45, 42, 0.05);
}

.hot-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 14px;
}

.hot-list {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.hot-item {
  border: 1px solid #eef2f1;
  border-radius: 10px;
  padding: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.hot-item:hover {
  border-color: #93cdbc;
  transform: translateY(-2px);
}

.hot-emoji {
  font-size: 26px;
}

.hot-name {
  font-size: 13px;
  margin: 6px 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  min-height: 200px;
}

.service-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 2px 12px rgba(31, 45, 42, 0.05);
  transition: transform 0.2s, box-shadow 0.2s;
}

.service-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 24px rgba(47, 158, 126, 0.16);
}

.card-cover {
  height: 108px;
  background: linear-gradient(135deg, #edf7f4, #d7efe8);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.cover-emoji {
  font-size: 44px;
}

.cover-tag {
  position: absolute;
  top: 10px;
  left: 10px;
}

.card-body {
  padding: 14px;
}

.card-name {
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 6px;
}

.card-desc {
  font-size: 12px;
  color: #8fa39e;
  line-height: 1.6;
  height: 38px;
  overflow: hidden;
  margin-bottom: 10px;
}

.card-meta {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}

.card-meta .price {
  font-size: 18px;
}

.duration,
.sales {
  font-size: 12px;
  color: #9aaba7;
}

.sales {
  margin-left: auto;
}

.book-btn {
  width: 100%;
}

@media (max-width: 1100px) {
  .service-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .hot-list {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
