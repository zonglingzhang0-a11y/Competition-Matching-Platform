<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <div class="auth-page">
    <div class="brand">竞赛组队匹配平台</div>
    <el-card class="auth-card" shadow="never">
      <h1 class="title">{{ title }}</h1>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      <slot />
    </el-card>
    <div class="footer"><slot name="footer" /></div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
}

.brand {
  font-size: 15px;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
  letter-spacing: 1px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  border-radius: 12px;
}

.title {
  font-size: 22px;
  font-weight: 500;
  margin: 4px 0 4px;
}

.subtitle {
  margin: 0 0 20px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.footer {
  margin-top: 16px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
</style>
