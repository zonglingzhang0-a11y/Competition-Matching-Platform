<script setup lang="ts">
import { useUserStore } from '@/stores/user'

const user = useUserStore()
</script>

<template>
  <div class="home">
    <h1 class="greeting">你好，{{ user.me?.nickname }}</h1>

    <el-alert
      v-if="user.me?.verifyStatus === 'NONE'"
      type="warning"
      :closable="false"
      show-icon
      title="完成学校认证后，就能建队和申请入队"
      class="notice"
    >
      <router-link to="/verify">去认证</router-link>
    </el-alert>

    <el-card shadow="never" class="card">
      <template #header>我的账号</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="邮箱">{{ user.me?.email }}</el-descriptions-item>
        <el-descriptions-item label="学校">{{ user.me?.school?.name ?? '未认证' }}</el-descriptions-item>
        <el-descriptions-item label="认证状态">
          <el-tag v-if="user.me?.verifyStatus === 'VERIFIED'" type="success">已认证</el-tag>
          <el-tag v-else-if="user.me?.verifyStatus === 'PENDING'" type="info">学校待确认</el-tag>
          <el-tag v-else type="warning">未认证</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.home {
  max-width: 720px;
  margin: 0 auto;
}

.greeting {
  font-size: 22px;
  font-weight: 500;
  margin: 0 0 16px;
}

.notice {
  margin-bottom: 16px;
}

.card {
  border-radius: 12px;
}
</style>
