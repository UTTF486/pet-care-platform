<template>
  <div class="page">
    <div class="head-row">
      <div>
        <h2 class="page-title">服务师管理</h2>
        <p class="page-desc">只有"可接单"状态的服务师才会出现在接单弹窗里</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增服务师</el-button>
    </div>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="姓名 / 技能 / 手机号"
        clearable
        style="width: 260px"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-button type="primary" @click="reload">查询</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column prop="skills" label="擅长技能" min-width="200" show-overflow-tooltip />
      <el-table-column label="等级" width="100">
        <template #default="{ row }">
          <el-tag :type="row.level === 3 ? 'success' : row.level === 2 ? 'primary' : 'info'" effect="plain">
            {{ levelText(row.level) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评分" width="100">
        <template #default="{ row }">
          <span class="rating">{{ row.rating }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="orderCount" label="累计接单" width="110" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            active-text="可接单"
            inactive-text="休息"
            inline-prompt
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" plain @click="openDialog(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑服务师' : '新增服务师'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" maxlength="32" placeholder="如 王阿姨" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="用于接单通知" maxlength="11" />
        </el-form-item>
        <el-form-item label="擅长技能">
          <el-input v-model="form.skills" maxlength="255" placeholder="逗号分隔，如 猫咪喂养,喂药" />
        </el-form-item>
        <el-form-item label="等级">
          <el-radio-group v-model="form.level">
            <el-radio :value="1">初级</el-radio>
            <el-radio :value="2">中级</el-radio>
            <el-radio :value="3">高级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">可接单</el-radio>
            <el-radio :value="0">休息</el-radio>
          </el-radio-group>
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
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { adminListServicers, adminSaveServicer, adminUpdateServicer } from '../../api'

const list = ref([])
const loading = ref(false)
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()

const emptyForm = () => ({ id: null, name: '', phone: '', skills: '', level: 1, status: 1 })
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }]
}

function levelText(level) {
  return { 1: '初级', 2: '中级', 3: '高级' }[level] || '初级'
}

async function loadList() {
  loading.value = true
  try {
    const data = await adminListServicers({
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
    await adminUpdateServicer(row.id, { status: val })
    ElMessage.success(val === 1 ? '已设为可接单' : '已设为休息')
  } catch (e) {
    row.status = val === 1 ? 0 : 1
  }
}

function openDialog(row) {
  Object.assign(form, row ? { ...row } : emptyForm())
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (form.id) {
      await adminUpdateServicer(form.id, { ...form })
      ElMessage.success('保存成功')
    } else {
      await adminSaveServicer({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 4px;
}

.rating {
  color: #e6a23c;
  font-weight: 600;
}
</style>
