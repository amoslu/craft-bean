<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
</script>

<template>
  <el-card>
    <h2>工作台</h2>
    <p>你好，{{ auth.user?.name }}（{{ auth.user?.role }}）。M1 各模块功能在 S1–S6 逐里程碑上线，当前为骨架占位。</p>
  </el-card>
</template>
