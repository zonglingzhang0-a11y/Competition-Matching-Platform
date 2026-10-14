<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Bell } from '@element-plus/icons-vue'
import { useAdminStore } from '@/stores/admin'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const notifications = useNotificationStore()

const admin = useAdminStore()

/** 管理员菜单上的待审核数，切换页面时刷新 */
function refreshPending() {
  if (user.isAdmin) admin.refresh()
}

/** 详情页也高亮对应的菜单 */
const activeMenu = computed(() => {
  if (route.path.startsWith('/competitions')) return '/competitions'
  if (route.path.startsWith('/teams')) return '/teams'
  return route.path
})

function onCommand(command: string) {
  if (command === 'logout') logout()
  else router.push({ name: command })
}

async function logout() {
  notifications.stop()
  await user.logout()
  router.replace({ name: 'login' })
}

onMounted(() => {
  notifications.start()
  refreshPending()
})
onBeforeUnmount(notifications.stop)
watch(
  () => route.fullPath,
  () => {
    notifications.refresh()
    refreshPending()
  },
)
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <router-link to="/" class="brand">竞赛组队</router-link>
      <el-menu mode="horizontal" router :default-active="activeMenu" :ellipsis="false" class="menu">
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/competitions">比赛库</el-menu-item>
        <el-menu-item index="/teams">我的队伍</el-menu-item>
        <el-menu-item index="/calendar">日历</el-menu-item>
        <el-menu-item index="/profile">我的档案</el-menu-item>
        <el-menu-item index="/verify">身份认证</el-menu-item>
        <el-sub-menu v-if="user.isAdmin" index="admin">
          <template #title>
            管理后台
            <span v-if="admin.pendingTotal > 0" class="count" :aria-label="`${admin.pendingTotal} 条待处理`">{{ admin.pendingTotal }}</span>
          </template>
          <el-menu-item index="/admin">数据看板</el-menu-item>
          <el-menu-item index="/admin/verifications">
            认证审核
            <span v-if="admin.pendingReviews > 0" class="count">{{ admin.pendingReviews }}</span>
          </el-menu-item>
          <el-menu-item index="/admin/competitions">比赛库管理</el-menu-item>
          <el-menu-item index="/admin/crawl">
            通知抓取
            <span v-if="admin.pendingCrawl > 0" class="count">{{ admin.pendingCrawl }}</span>
          </el-menu-item>
          <el-menu-item index="/admin/reports">
            举报与信用
            <span v-if="admin.pendingReports > 0" class="count">{{ admin.pendingReports }}</span>
          </el-menu-item>
          <el-menu-item index="/admin/match-config">匹配配置</el-menu-item>
          <el-menu-item index="/admin/users">账号管理</el-menu-item>
        </el-sub-menu>
      </el-menu>
      <div class="right">
        <el-tag v-if="user.me?.verifyStatus === 'VERIFIED'" type="success" size="small">
          {{ user.me?.school?.name }}
        </el-tag>
        <router-link to="/notifications" class="bell" aria-label="消息">
          <el-badge :value="notifications.unread" :hidden="notifications.unread === 0" :max="99">
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>
        </router-link>
        <el-dropdown trigger="click" @command="onCommand">
          <span class="user">
            {{ user.me?.nickname }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="reviews">互评与信用</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
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
  gap: 16px;
}

.bell {
  display: inline-flex;
  color: var(--el-text-color-regular);
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

/* 菜单上的待审核数 */
.count {
  display: inline-block;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--el-color-danger);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  box-sizing: border-box;
}
</style>
