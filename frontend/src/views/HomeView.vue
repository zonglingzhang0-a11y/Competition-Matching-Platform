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

    <div class="shortcuts">
      <router-link to="/competitions" class="shortcut">
        <el-card shadow="hover">
          <div class="shortcut-title">逛比赛库</div>
          <div class="shortcut-desc">看看哪些比赛正在报名，关注感兴趣的那一届</div>
        </el-card>
      </router-link>
      <router-link to="/profile" class="shortcut">
        <el-card shadow="hover">
          <div class="shortcut-title">完善档案</div>
          <div class="shortcut-desc">填好技能和参赛履历，推荐队友时会用到</div>
        </el-card>
      </router-link>
    </div>

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

.shortcuts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.shortcut {
  text-decoration: none;
}

.shortcut :deep(.el-card) {
  border-radius: 12px;
  height: 100%;
}

.shortcut-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 4px;
}

.shortcut-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.card {
  border-radius: 12px;
}
</style>
