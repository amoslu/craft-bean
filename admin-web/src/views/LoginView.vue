<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: 'admin123' })
const loading = ref(false)

async function onSubmit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    router.push('/dashboard')
  } catch {
    ElMessage.error('用户名或密码错误')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <span class="brand-logo">☕</span>
        <h2 class="title">craft-bean</h2>
        <p class="subtitle">咖啡烘焙工坊管理</p>
      </div>
      <el-form :model="form" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名"><el-input v-model="form.username" placeholder="请输入用户名" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password placeholder="请输入密码" /></el-form-item>
        <el-button native-type="submit" type="primary" :loading="loading" style="width: 100%">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #2c2017 0%, #4a3628 55%, #b5803c 100%);
}
.login-card {
  width: 380px;
  border-radius: 12px;
  border: none;
}
.brand {
  text-align: center;
  margin-bottom: 8px;
}
.brand-logo {
  font-size: 40px;
}
.title {
  margin: 8px 0 0;
  color: #4a3628;
  letter-spacing: 1px;
}
.subtitle {
  margin: 4px 0 16px;
  color: #b5803c;
  font-size: 14px;
}
</style>
