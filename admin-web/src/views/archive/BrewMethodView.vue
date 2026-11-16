<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listBrewMethods, createBrewMethod, updateBrewMethod, changeBrewMethodStatus, deleteBrewMethod, type BrewMethod } from '../../api/archive'
import { useAuthStore } from '../../stores/auth'

const loading = ref(false), list = ref<BrewMethod[]>([]), total = ref(0)
const query = reactive({ keyword: '', status: '', page: 1, size: 20 })
const dialogVisible = ref(false), editingId = ref<number | null>(null)
const form = reactive<Partial<BrewMethod>>({ name: '', status: 'ACTIVE' })
const statusMap: Record<string, string> = { ACTIVE: '启用', DISABLED: '停用' }
const auth = useAuthStore()
const isAdmin = computed(() => auth.user?.role === 'ADMIN')

async function load() {
  loading.value = true
  try { const d = await listBrewMethods({ ...query, keyword: query.keyword || undefined, status: query.status || undefined }); list.value = d.list; total.value = d.total }
  finally { loading.value = false }
}
function search() { query.page = 1; load() }
function openCreate() { editingId.value = null; Object.assign(form, { name: '', status: 'ACTIVE' }); dialogVisible.value = true }
function openEdit(row: BrewMethod) { editingId.value = row.id; Object.assign(form, row); dialogVisible.value = true }
async function submit() {
  if (!form.name?.trim()) return ElMessage.warning('请输入冲煮方式名称')
  if (editingId.value == null) await createBrewMethod(form); else await updateBrewMethod(editingId.value, form)
  ElMessage.success('已保存'); dialogVisible.value = false; load()
}
async function toggle(row: BrewMethod) { await changeBrewMethodStatus(row.id, row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'); ElMessage.success('状态已更新'); load() }
async function onDelete(row: BrewMethod) {
  try {
    await ElMessageBox.confirm(`确认删除冲煮方式「${row.name}」？被生豆引用后不可删。`, '删除', { type: 'warning' })
  } catch { return }
  try {
    await deleteBrewMethod(row.id)
    ElMessage.success('已删除'); load()
  } catch (err) {
    const data = (err as { response?: { data?: { message?: string } } })?.response?.data
    ElMessage.error(data?.message || '删除失败')
  }
}
onMounted(load)
</script>
<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="名称" clearable @keyup.enter="search" />
      <el-select v-model="query.status" placeholder="状态" clearable @change="search">
        <el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button><el-button type="success" @click="openCreate">新增冲煮方式</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ statusMap[row.status] }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="230"><template #default="{ row }">
        <el-button size="small" @click="openEdit(row)">编辑</el-button>
        <el-button size="small" :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggle(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
        <el-button v-if="isAdmin" size="small" type="danger" @click="onDelete(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size" :current-page="query.page" @current-change="(p: number) => { query.page = p; load() }" />
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新增冲煮方式' : '编辑冲煮方式'" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="如 手冲 / 冰滴 / 意式" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </el-card>
</template>
<style scoped>.toolbar { display: flex; gap: 8px; margin-bottom: 12px }.toolbar .el-input { width: 220px }.toolbar .el-select { width: 130px }.pager { margin-top: 12px; justify-content: flex-end }</style>
