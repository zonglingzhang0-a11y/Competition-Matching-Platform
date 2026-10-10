<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

async function logout() {
  await user.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <router-link to="/" class="brand">竞赛组队</router-link>
      <el-menu mode="horizontal" router :default-active="route.path" :ellipsis="false" class="menu">
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/verify">身份认证</el-menu-item>
      </el-menu>
      <div class="right">
        <el-tag v-if="user.me?.verifyStatus === 'VERIFIED'" type="success" size="small">
          {{ user.me?.school?.name }}
        </el-tag>
        <el-dropdown trigger="click" @command="logout">
          <span class="user">
            {{ user.me?.nickname }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.brand {
  font-size: 17px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  text-decoration: none;
  white-space: nowrap;
}

.menu {
  flex: 1;
  min-width: 0;
  border-bottom: none;
}

.right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.main {
  padding: 24px 16px;
}
</style>
