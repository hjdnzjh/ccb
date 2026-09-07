<template>
  <div class="table-page">
    <div class="search-bar">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="编号">
          <el-input v-model="searchForm.username" placeholder="表号/账号" clearable />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="searchForm.realName" placeholder="小区名/姓名" clearable />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="searchForm.phone" placeholder="请输入" clearable />
        </el-form-item>
        <el-form-item label="用户类型">
          <el-select v-model="searchForm.userType" placeholder="全部" clearable>
            <el-option label="居民" value="residential" />
            <el-option label="商业" value="commercial" />
            <el-option label="工业" value="industrial" />
          </el-select>
        </el-form-item>
        <el-form-item label="信用等级">
          <el-select v-model="searchForm.creditLevel" placeholder="全部" clearable>
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
            <el-option label="D" value="D" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon> 查询
          </el-button>
          <el-button type="primary" @click="openForm()">
            <el-icon><Plus /></el-icon> 新增
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-container">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="username" label="编号" width="130" />
        <el-table-column label="姓名" width="140">
          <template #default="{ row }">
            {{ row.realName || row.username || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="userType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="getUserTypeTag(row.userType)">{{ getUserTypeLabel(row.userType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creditLevel" label="信用" width="80">
          <template #default="{ row }">
            <el-tag :type="getCreditTag(row.creditLevel)">{{ row.creditLevel || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" width="100">
          <template #default="{ row }">¥{{ Number(row.balance || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openForm(row)">编辑</el-button>
            <el-button type="primary" link @click="openMeters(row)">水表</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          layout="total, prev, pager, next"
          @current-change="load"
          @size-change="handleSearch"
        />
      </div>
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新增用户'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="88px">
        <el-form-item label="编号" required>
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="如 HZ000001" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码">
          <el-input v-model="form.password" placeholder="默认 123456" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.userType" style="width: 100%">
            <el-option label="居民" value="residential" />
            <el-option label="商业" value="commercial" />
            <el-option label="工业" value="industrial" />
          </el-select>
        </el-form-item>
        <el-form-item label="信用">
          <el-select v-model="form.creditLevel" style="width: 100%">
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
            <el-option label="D" value="D" />
          </el-select>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="metersVisible" :title="`${metersUser?.realName || metersUser?.username || ''} · 水表`" width="720px">
      <el-table :data="meters" stripe max-height="420">
        <el-table-column prop="meterNo" label="表号" width="150" />
        <el-table-column prop="meterType" label="类型" width="100" />
        <el-table-column prop="commType" label="通讯" width="100" />
        <el-table-column prop="currentReading" label="读数" width="100" />
        <el-table-column prop="installAddress" label="安装地址" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">{{ row.status === 0 ? '正常' : '异常' }}</template>
        </el-table-column>
      </el-table>
      <p v-if="!meters.length" class="empty-hint">该用户暂无绑定水表</p>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/index.js'

const searchForm = reactive({
  username: '',
  realName: '',
  phone: '',
  userType: '',
  creditLevel: ''
})

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref([])
const loading = ref(false)
const saving = ref(false)

const formVisible = ref(false)
const form = reactive({
  id: null,
  username: '',
  password: '',
  realName: '',
  phone: '',
  userType: 'residential',
  creditLevel: 'A',
  address: '',
  status: 0
})

const metersVisible = ref(false)
const metersUser = ref(null)
const meters = ref([])

const load = async () => {
  loading.value = true
  try {
    const res = await userApi.list({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      username: searchForm.username || undefined,
      realName: searchForm.realName || undefined,
      phone: searchForm.phone || undefined,
      userType: searchForm.userType || undefined,
      creditLevel: searchForm.creditLevel || undefined
    })
    tableData.value = res.data?.records || []
    pagination.total = Number(res.data?.total || 0)
  } catch (e) {
    // keep table empty on error; axios interceptor already toasts
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
})

const handleSearch = () => {
  pagination.pageNum = 1
  load()
}

const openForm = (row) => {
  if (row) {
    Object.assign(form, {
      id: row.id,
      username: row.username,
      password: '',
      realName: row.realName || '',
      phone: row.phone || '',
      userType: row.userType || 'residential',
      creditLevel: row.creditLevel || 'A',
      address: row.address || '',
      status: row.status ?? 0
    })
  } else {
    Object.assign(form, {
      id: null,
      username: '',
      password: '',
      realName: '',
      phone: '',
      userType: 'residential',
      creditLevel: 'A',
      address: '',
      status: 0
    })
  }
  formVisible.value = true
}

const saveForm = async () => {
  if (!form.username?.trim()) {
    ElMessage.warning('请填写编号')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await userApi.update(form.id, { ...form })
      ElMessage.success('已更新')
    } else {
      await userApi.create({ ...form })
      ElMessage.success('已创建')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const openMeters = async (row) => {
  metersUser.value = row
  metersVisible.value = true
  try {
    const res = await userApi.meters(row.id)
    meters.value = res.data || []
  } catch (_) {
    meters.value = []
  }
}

const getUserTypeLabel = (type) => {
  const map = { residential: '居民', commercial: '商业', industrial: '工业' }
  return map[type] || type || '—'
}
const getUserTypeTag = (type) => {
  const map = { residential: 'success', commercial: 'warning', industrial: 'danger' }
  return map[type] || 'info'
}
const getCreditTag = (level) => {
  const map = { A: 'success', B: 'primary', C: 'warning', D: 'danger' }
  return map[level] || 'info'
}
</script>

<style scoped>
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.empty-hint { color: var(--color-muted, #64748b); text-align: center; padding: 16px 0; }
</style>
