<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listGreenBeans, createGreenBean, updateGreenBean, type GreenBean } from '../../api/archive'

const loading = ref(false), list = ref<GreenBean[]>([]), total = ref(0)
const query = reactive({ keyword: '', status: '', page: 1, size: 20 })
const visible = ref(false), editingId = ref<number | null>(null)
const form = reactive<Partial<GreenBean>>({ name: '', origin: '', process: '', variety: '', status: 'ACTIVE', remark: '' })
async function load() { loading.value = true; try { const d = await listGreenBeans({ ...query, keyword: query.keyword || undefined, status: query.status || undefined }); list.value = d.list; total.value = d.total } finally { loading.value = false } }
function search() { query.page = 1; load() }
function openCreate() { editingId.value = null; Object.assign(form, { name: '', origin: '', process: '', variety: '', status: 'ACTIVE', remark: '' }); visible.value = true }
function openEdit(row: GreenBean) { editingId.value = row.id; Object.assign(form, row); visible.value = true }
async function submit() { if (!form.name?.trim()) return ElMessage.warning('请输入品种名称'); if (editingId.value == null) await createGreenBean(form); else await updateGreenBean(editingId.value, form); ElMessage.success('已保存'); visible.value = false; load() }
onMounted(load)
</script>
<template>
  <el-card>
    <div class="toolbar"><el-input v-model="query.keyword" placeholder="名称/产地/豆种" clearable @keyup.enter="search" /><el-select v-model="query.status" placeholder="状态" clearable @change="search"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select><el-button type="primary" @click="search">查询</el-button><el-button type="success" @click="openCreate">新增品种</el-button></div>
    <el-table v-loading="loading" :data="list" border><el-table-column prop="name" label="名称" min-width="180" /><el-table-column prop="origin" label="产地" /><el-table-column prop="process" label="处理法" /><el-table-column prop="variety" label="豆种" /><el-table-column prop="remark" label="备注" min-width="180" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="80"><template #default="{ row }"><el-button size="small" @click="openEdit(row)">编辑</el-button></template></el-table-column></el-table>
    <el-pagination class="pager" layout="total, prev, pager, next" :total="total" :page-size="query.size" :current-page="query.page" @current-change="(p: number) => { query.page = p; load() }" />
    <el-dialog v-model="visible" :title="editingId == null ? '新增生豆品种' : '编辑生豆品种'" width="460px"><el-form :model="form" label-width="80px"><el-form-item label="名称"><el-input v-model="form.name" /></el-form-item><el-form-item label="产地"><el-input v-model="form.origin" /></el-form-item><el-form-item label="处理法"><el-input v-model="form.process" /></el-form-item><el-form-item label="豆种"><el-input v-model="form.variety" /></el-form-item><el-form-item label="状态"><el-select v-model="form.status"><el-option label="启用" value="ACTIVE" /><el-option label="停用" value="DISABLED" /></el-select></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template></el-dialog>
  </el-card>
</template>
<style scoped>.toolbar { display: flex; gap: 8px; margin-bottom: 12px }.toolbar .el-input { width: 220px }.toolbar .el-select { width: 120px }.pager { margin-top: 12px; justify-content: flex-end }</style>
