<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listAuditLogs, type AuditLog } from '../../api/audit'

const loading = ref(false)
const list = ref<AuditLog[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const actionMap: Record<string, string> = {
  LOGIN: '登录',
  USER_CREATE: '新增用户',
  USER_UPDATE: '更新用户',
  USER_RESET_PASSWORD: '重置密码',
  USER_DELETE: '删除用户'
}

async function load() {
  loading.value = true
  try {
    const data = await listAuditLogs({ page: page.value, size: size.value })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="operatorName" label="操作人" width="120" />
      <el-table-column label="动作" width="120">
        <template #default="{ row }">{{ actionMap[row.action] || row.action }}</template>
      </el-table-column>
      <el-table-column prop="targetNo" label="对象" width="160" />
      <el-table-column prop="detail" label="详情" min-width="200" />
      <el-table-column prop="createdAt" label="时间" width="170" />
    </el-table>

    <el-pagination
      style="margin-top: 12px; justify-content: flex-end"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="size"
      :current-page="page"
      @current-change="(p: number) => { page = p; load() }"
    />
  </el-card>
</template>
