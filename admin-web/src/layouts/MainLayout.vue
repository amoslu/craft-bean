<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menus = [
  { index: '/dashboard', title: '工作台' },
  { title: '系统管理', children: [
    { path: '/system/users', label: '用户管理' },
    { path: '/system/audit', label: '操作日志' }
  ] },
  { title: '基础档案', children: [
    { path: '/archive/suppliers', label: '供货商' },
    { path: '/archive/customers', label: '客户' },
    { path: '/archive/green-bean', label: '生豆品种' },
    { path: '/archive/roasted-product', label: '熟豆商品' }
  ] },
  { title: '生豆', children: [{ path: '/greenbean/purchases', label: '采购入库' }] },
  { title: '烘焙', children: [{ path: '/roast/batches', label: '烘焙批次' }] },
  { title: '熟豆库存', children: [{ path: '/stock/overview', label: '库存看板' }] }
]

const active = computed(() => route.path)

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    auth.logout()
    router.push('/login')
  }
}
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside width="220px">
      <el-menu :default-active="active" router background-color="#001529" text-color="#fff" active-text-color="#ffd04b">
        <el-menu-item v-for="m in menus.filter(x => !x.children)" :key="m.index" :index="m.index">{{ m.title }}</el-menu-item>
        <el-sub-menu v-for="m in menus.filter(x => x.children)" :key="m.title" :index="m.title">
          <template #title>{{ m.title }}</template>
          <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path">{{ c.label }}</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="display: flex; align-items: center; justify-content: flex-end; border-bottom: 1px solid #eee">
        <el-dropdown @command="onCommand">
          <span style="cursor: pointer">{{ auth.user?.name || '未登录' }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>
