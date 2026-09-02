<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <el-card>
    <el-empty :description="`${route.meta.title ?? ''} · 建设中（后续里程碑实现）`" />
  </el-card>
</template>
