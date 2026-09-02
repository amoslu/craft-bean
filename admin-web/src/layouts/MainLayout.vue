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
    <el-aside width="220px" class="sidebar">
      <div class="brand">
        <span class="brand-logo">☕</span>
        <span class="brand-name">craft-bean</span>
      </div>
      <el-menu :default-active="active" router background-color="#2c2017" text-color="#e8d9c8" active-text-color="#e8b36b">
        <el-menu-item v-for="m in menus.filter(x => !x.children)" :key="m.index" :index="m.index">{{ m.title }}</el-menu-item>
        <el-sub-menu v-for="m in menus.filter(x => x.children)" :key="m.title" :index="m.title">
          <template #title>{{ m.title }}</template>
          <el-menu-item v-for="c in m.children" :key="c.path" :index="c.path">{{ c.label }}</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="topbar">
        <span class="topbar-title">咖啡烘焙工坊管理</span>
        <el-dropdown @command="onCommand">
          <span class="topbar-user">{{ auth.user?.name || '未登录' }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main-area"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.sidebar {
  background: #2c2017;
  display: flex;
  flex-direction: column;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 18px 16px;
  color: #f5ece0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.brand-logo {
  font-size: 20px;
}
.brand-name {
  font-weight: 600;
  letter-spacing: 0.5px;
}
.sidebar :deep(.el-menu) {
  border-right: none;
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #efe6d8;
}
.topbar-title {
  font-weight: 600;
  color: #4a3628;
}
.topbar-user {
  cursor: pointer;
  color: #4a3628;
}
.main-area {
  background: #f7f3ec;
  padding: 16px;
}
</style>
