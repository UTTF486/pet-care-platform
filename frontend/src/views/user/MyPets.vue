<template>
  <div class="page">
    <div class="head-row">
      <div>
        <h2 class="page-title">宠物档案</h2>
        <p class="page-desc">服务师上门前会先看这里，信息越全服务越贴心</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">添加宠物</el-button>
    </div>

    <div v-loading="loading" class="pet-grid">
      <el-empty v-if="!loading && pets.length === 0" description="还没有宠物档案" style="grid-column: 1 / -1">
        <el-button type="primary" @click="openDialog()">添加第一只宠物</el-button>
      </el-empty>

      <div v-for="pet in pets" :key="pet.id" class="pet-card">
        <div class="pet-avatar">{{ pet.petType === '狗' ? '🐶' : pet.petType === '猫' ? '🐱' : '🐾' }}</div>
        <div class="pet-info">
          <div class="pet-name">
            {{ pet.name }}
            <el-tag size="small" effect="plain">{{ pet.breed || pet.petType }}</el-tag>
          </div>
          <div class="pet-meta">
            <span>{{ genderText(pet.gender) }}</span>
            <span>{{ pet.age }} 个月</span>
            <span v-if="pet.weight">{{ pet.weight }} kg</span>
          </div>
          <div class="pet-remark">{{ pet.remark || '暂无备注' }}</div>
        </div>
        <div class="pet-actions">
          <el-button size="small" text type="primary" @click="openDialog(pet)">编辑</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(pet)">删除</el-button>
        </div>
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑宠物' : '添加宠物'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="宠物名" prop="name">
          <el-input v-model="form.name" placeholder="如 奶糖" />
        </el-form-item>
        <el-form-item label="类型" prop="petType">
          <el-radio-group v-model="form.petType">
            <el-radio-button value="猫">猫</el-radio-button>
            <el-radio-button value="狗">狗</el-radio-button>
            <el-radio-button value="其他">其他</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="品种">
          <el-input v-model="form.breed" placeholder="如 布偶 / 柯基" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :value="0">未知</el-radio>
            <el-radio :value="1">公</el-radio>
            <el-radio :value="2">母</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄(月)">
          <el-input-number v-model="form.age" :min="0" :max="360" />
        </el-form-item>
        <el-form-item label="体重(kg)">
          <el-input-number v-model="form.weight" :min="0" :max="120" :precision="2" :step="0.5" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="如：怕生、肠胃敏感只吃自带粮、需要喂药"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { addPet, deletePet, listPets, updatePet } from '../../api'

const pets = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()

const emptyForm = () => ({ id: null, name: '', petType: '猫', breed: '', gender: 0, age: 12, weight: null, remark: '' })
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请填写宠物名', trigger: 'blur' }],
  petType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

function genderText(gender) {
  return { 0: '性别未知', 1: '公', 2: '母' }[gender] || '性别未知'
}

async function loadPets() {
  loading.value = true
  try {
    pets.value = await listPets()
  } finally {
    loading.value = false
  }
}

function openDialog(pet) {
  Object.assign(form, pet ? { ...pet } : emptyForm())
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (form.id) {
      await updatePet({ ...form })
      ElMessage.success('修改成功')
    } else {
      await addPet({ ...form })
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadPets()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(pet) {
  await ElMessageBox.confirm(`确定删除「${pet.name}」的档案吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await deletePet(pet.id)
  ElMessage.success('已删除')
  loadPets()
}

onMounted(loadPets)
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.pet-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  min-height: 180px;
}

.pet-card {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  display: flex;
  gap: 14px;
  box-shadow: 0 2px 12px rgba(31, 45, 42, 0.05);
  position: relative;
}

.pet-avatar {
  width: 54px;
  height: 54px;
  border-radius: 14px;
  background: #edf7f4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
}

.pet-info {
  flex: 1;
  min-width: 0;
}

.pet-name {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.pet-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #8fa39e;
  margin-bottom: 8px;
}

.pet-remark {
  font-size: 12px;
  color: #6b7c78;
  line-height: 1.6;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.pet-actions {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 2px;
}

@media (max-width: 1000px) {
  .pet-grid {
    grid-template-columns: 1fr;
  }
}
</style>
