<template>
  <div class="page">
    <div class="head-row">
      <div>
        <h2 class="page-title">服务管理</h2>
        <p class="page-desc">上下架、改价、新增服务项目；下架后用户端立即不可见，历史订单不受影响</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增服务</el-button>
    </div>

    <div class="toolbar">
      <el-select v-model="query.categoryId" placeholder="全部品类" clearable style="width: 150px" @change="reload">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="上架状态" clearable style="width: 140px" @change="reload">
        <el-option label="已上架" :value="1" />
        <el-option label="已下架" :value="0" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="服务名称" clearable style="width: 220px" @keyup.enter="reload" />
      <el-button type="primary" @click="reload">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="服务名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="品类" width="120" />
      <el-table-column label="价格" width="110">
        <template #default="{ row }"><span class="price">{{ row.price }}</span></template>
      </el-table-column>
      <el-table-column prop="duration" label="时长(分钟)" width="110" />
      <el-table-column prop="sales" label="销量" width="90" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="1"
            :inactive-value="0"
            active-text="上架"
            inactive-text="下架"
            inline-prompt
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="165" />
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑服务' : '新增服务'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="服务名称" prop="name">
          <el-input v-model="form.name" maxlength="64" placeholder="如 猫咪上门喂养·单次" />
        </el-form-item>
        <el-form-item label="所属品类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格(元)" prop="price">
          <el-input-number
            v-model="form.price"
            :min="0.01"
            :max="99999999.99"
            :precision="2"
            :step="10"
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="form.duration" :min="1" :max="1440" :step="10" style="width: 180px" />
        </el-form-item>
        <el-form-item label="服务说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="写清楚包含哪些动作，减少沟通成本"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架</el-radio>
            <el-radio :value="0">下架</el-radio>
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
import {
  adminChangeServiceStatus,
  adminListServices,
  adminSaveService,
  adminUpdateService,
  listCategories
} from '../../api'

const categories = ref([])
const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const query = reactive({ categoryId: null, status: null, keyword: '' })

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref()

const emptyForm = () => ({ id: null, name: '', categoryId: null, price: 49, duration: 60, description: '', status: 1 })
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请填写服务名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择品类', trigger: 'change' }],
  price: [{ required: true, message: '请填写价格', trigger: 'blur' }]
}

async function loadList() {
  loading.value = true
  try {
    const data = await adminListServices({
      categoryId: query.categoryId,
      status: query.status,
      keyword: query.keyword || undefined,
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

function reset() {
  query.categoryId = null
  query.status = null
  query.keyword = ''
  reload()
}

async function handleStatusChange(row, val) {
  try {
    await adminChangeServiceStatus(row.id, val)
    ElMessage.success(val === 1 ? '已上架' : '已下架')
  } catch (e) {
    // 接口失败要把开关拨回去，避免界面和后端不一致
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
      await adminUpdateService(form.id, { ...form })
      ElMessage.success('保存成功')
    } else {
      await adminSaveService({ ...form })
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadList()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  categories.value = (await listCategories()) || []
  loadList()
})
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 4px;
}
</style>
