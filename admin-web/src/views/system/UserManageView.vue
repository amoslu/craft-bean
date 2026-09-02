<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, updateUser, resetPassword, deleteUser, type User } from '../../api/users'

const loading = ref(false)
const list = ref<User[]>([])
const total = ref(0)
const query = reactive({ keyword: '', role: '', status: '', page: 1, size: 20 })

const roleOptions = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'STAFF', label: '员工' },
  { value: 'READONLY', label: '只读' }
]
const statusOptions = [
  { value: 'ACTIVE', label: '启用' },
  { value: 'DISABLED', label: '停用' }
]

const roleMap: Record<string, string> = { ADMIN: '管理员', STAFF: '员工', READONLY: '只读' }
const statusMap: Record<string, string> = { ACTIVE: '启用', DISABLED: '停用' }

async function load() {
  loading.value = true
  try {
    const data = await listUsers({
      keyword: query.keyword || undefined,
      role: query.role || undefined,
      status: query.status || undefined,
      page: query.page,
      size: query.size
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

// 新增/编辑弹窗
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ username: '', name: '', password: '', role: 'STAFF', status: 'ACTIVE' })

function openCreate() {
  editingId.value = null
  Object.assign(form, { username: '', name: '', password: '', role: 'STAFF', status: 'ACTIVE' })
  dialogVisible.value = true
}

function openEdit(row: User) {
  editingId.value = row.id
  Object.assign(form, { username: row.username, name: row.name, password: '', role: row.role, status: row.status })
  dialogVisible.value = true
}

async function submit() {
  if (editingId.value == null) {
    await createUser({ username: form.username, name: form.name, password: form.password, role: form.role })
    ElMessage.success('已创建')
  } else {
    await updateUser(editingId.value, { name: form.name, role: form.role, status: form.status })
    ElMessage.success('已保存')
  }
  dialogVisible.value = false
  load()
}

async function onResetPassword(row: User) {
  const { value } = await ElMessageBox.prompt(`为「${row.name}」设置新密码`, '重置密码', {
    inputType: 'password',
    inputValidator: (v) => (v && v.length >= 6) || '密码至少 6 位'
  })
  await resetPassword(row.id, value)
  ElMessage.success('已重置密码')
}

async function onDelete(row: User) {
  await ElMessageBox.confirm(`确认删除用户「${row.name}」？`, '删除', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

async function toggleStatus(row: User) {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  await updateUser(row.id, { name: row.name, role: row.role, status: next })
  ElMessage.success(next === 'ACTIVE' ? '已启用' : '已停用')
  load()
}

onMounted(load)
</script>

<template>
  <el-card>
    <div style="margin-bottom: 12px; display: flex; gap: 8px">
      <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable style="width: 180px" @keyup.enter="search" />
      <el-select v-model="query.role" placeholder="角色" clearable style="width: 130px" @change="search">
        <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="search">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button type="success" @click="openCreate">新增用户</el-button>
    </div>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'">{{ roleMap[row.role] || row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ statusMap[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最近登录" width="170" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" min-width="260">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" @click="onResetPassword(row)">重置密码</el-button>
          <el-button size="small" :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
          </el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.size"
      :current-page="query.page"
      @current-change="(p: number) => { query.page = p; load() }"
    />

    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新增用户' : '编辑用户'" width="420px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="editingId != null" />
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item v-if="editingId == null" label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingId != null" label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
