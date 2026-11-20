<template>
  <div class="page" v-loading="loading">
    <el-page-header content="服务详情" @back="router.back()" class="back" />

    <div v-if="service" class="detail-wrap">
      <!-- 左侧：服务信息 -->
      <div class="card info-card">
        <div class="cover">🐾</div>
        <div class="info-body">
          <el-tag size="small" type="success" effect="plain">{{ service.categoryName }}</el-tag>
          <h2 class="service-name">{{ service.name }}</h2>
          <div class="price-row">
            <span class="price big">{{ service.price }}</span>
            <span class="unit">/ 次</span>
            <span class="meta">约 {{ service.duration }} 分钟 · 已售 {{ service.sales }}</span>
          </div>
          <el-divider />
          <h4 class="block-title">服务内容</h4>
          <p class="description">{{ service.description || '专业服务师上门服务，服务过程全程拍照反馈。' }}</p>

          <h4 class="block-title">服务流程</h4>
          <el-steps :active="4" align-center class="steps">
            <el-step title="提交预约" description="选择时段与宠物" />
            <el-step title="商家接单" description="指派服务师" />
            <el-step title="上门服务" description="拍照反馈" />
            <el-step title="完成评价" description="售后可追溯" />
          </el-steps>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            title="下单后 30 分钟内商家未接单，系统会自动取消并通知你，不占用你的时间。"
            class="tip"
          />
        </div>
      </div>

      <!-- 右侧：预约表单 -->
      <div class="card form-card">
        <h3 class="form-title">预约上门</h3>

        <el-alert
          v-if="pets.length === 0"
          type="warning"
          :closable="false"
          show-icon
          title="还没有宠物档案"
          description="先添加宠物信息，服务师才能提前了解它的习性"
          class="no-pet"
        >
          <el-button size="small" type="primary" plain @click="router.push('/user/pets')">去添加宠物</el-button>
        </el-alert>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="服务宠物" prop="petId">
            <el-select v-model="form.petId" placeholder="请选择宠物" style="width: 100%">
              <el-option v-for="pet in pets" :key="pet.id" :label="`${pet.name}（${pet.breed || pet.petType}）`" :value="pet.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="预约日期" prop="appointDate">
            <el-date-picker
              v-model="form.appointDate"
              type="date"
              placeholder="选择上门日期"
              value-format="YYYY-MM-DD"
              :disabled-date="disabledDate"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="预约时段" prop="timeSlot">
            <el-select v-model="form.timeSlot" placeholder="选择时段" style="width: 100%">
              <el-option v-for="slot in timeSlots" :key="slot" :label="slot" :value="slot" />
            </el-select>
          </el-form-item>

          <el-form-item label="联系人" prop="contactName">
            <el-input v-model="form.contactName" placeholder="上门时联系谁" />
          </el-form-item>

          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" placeholder="11 位手机号" maxlength="11" />
          </el-form-item>

          <el-form-item label="上门地址" prop="address">
            <el-input v-model="form.address" type="textarea" :rows="2" placeholder="小区、楼栋、门牌号，越详细越好" />
          </el-form-item>

          <el-form-item label="备注（选填）">
            <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="如：猫怕生，进门先别抱；钥匙放门口鞋柜" />
          </el-form-item>

          <div class="amount-row">
            <span>合计</span>
            <span class="price big">{{ service.price }}</span>
          </div>

          <el-button type="primary" size="large" class="submit" :loading="submitting" @click="handleSubmit">
            确认预约
          </el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder, getService, listPets } from '../../api'

const route = useRoute()
const router = useRouter()

const service = ref(null)
const pets = ref([])
const loading = ref(false)
const submitting = ref(false)
const formRef = ref()

// 与后端 OrderService.TIME_SLOTS 保持一致，后端还会再校验一次
const timeSlots = ['08:00-10:00', '10:00-12:00', '14:00-16:00', '16:00-18:00', '18:00-20:00']

const form = reactive({
  petId: null,
  appointDate: '',
  timeSlot: '',
  contactName: '',
  contactPhone: '',
  address: '',
  remark: ''
})

const rules = {
  petId: [{ required: true, message: '请选择宠物', trigger: 'change' }],
  appointDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  timeSlot: [{ required: true, message: '请选择时段', trigger: 'change' }],
  contactName: [{ required: true, message: '请填写联系人', trigger: 'blur' }],
  contactPhone: [
    { required: true, message: '请填写联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  address: [{ required: true, message: '请填写上门地址', trigger: 'blur' }]
}

/** 今天之前的日期不可选 */
function disabledDate(date) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

async function handleSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await createOrder({ ...form, serviceId: service.value.id })
    ElMessage.success('预约成功，等待商家接单')
    router.push('/user/orders')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    service.value = await getService(route.params.id)
    pets.value = await listPets()
    // 有宠物就默认选第一只，减少用户操作
    if (pets.value.length > 0) {
      form.petId = pets.value[0].id
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.back {
  margin-bottom: 16px;
}

.detail-wrap {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 20px;
  align-items: start;
}

.info-card {
  padding: 0;
  overflow: hidden;
}

.cover {
  height: 160px;
  background: linear-gradient(135deg, #edf7f4, #d7efe8);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64px;
}

.info-body {
  padding: 20px 24px 24px;
}

.service-name {
  margin: 10px 0 12px;
  font-size: 22px;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.price.big {
  font-size: 26px;
}

.unit {
  color: #8fa39e;
  font-size: 13px;
}

.meta {
  margin-left: auto;
  font-size: 12px;
  color: #9aaba7;
}

.block-title {
  margin: 18px 0 10px;
  font-size: 14px;
}

.description {
  color: #4a5c58;
  line-height: 1.8;
  font-size: 13px;
  margin: 0;
}

.steps {
  margin: 8px 0 20px;
}

.tip {
  margin-top: 8px;
}

.form-card {
  position: sticky;
  top: 84px;
}

.form-title {
  margin: 0 0 16px;
  font-size: 17px;
}

.no-pet {
  margin-bottom: 16px;
}

.amount-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-top: 1px dashed #e8eeec;
  margin-bottom: 8px;
  font-size: 14px;
}

.submit {
  width: 100%;
}

@media (max-width: 900px) {
  .detail-wrap {
    grid-template-columns: 1fr;
  }
  .form-card {
    position: static;
  }
}
</style>
