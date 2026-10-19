<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Search } from '@element-plus/icons-vue'
import UserPageDrawer from '@/components/UserPageDrawer.vue'
import { useAdminStore } from '@/stores/admin'
import { useNotificationStore } from '@/stores/notification'
import { useUserPageStore } from '@/stores/userPage'
import { useUserStore } from '@/stores/user'

/**
 * 登录后的外壳：顶栏（品牌、导航、搜索、消息、个人菜单）+ 内容 + 底部状态栏。
 * 档案、身份认证、互评与信用收在右上角的个人菜单里。
 */
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const notifications = useNotificationStore()
const admin = useAdminStore()
const userPage = useUserPageStore()

const keyword = ref('')
const clock = ref(now())
let clockTimer: number | undefined

function now() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}

/** 管理员菜单上的待审核数，切换页面时刷新 */
function refreshPending() {
  if (user.isAdmin) admin.refresh()
}

/** 详情页也高亮对应的菜单 */
const activeMenu = computed(() => {
  if (route.path.startsWith('/competitions')) return '/competitions'
  if (route.path.startsWith('/teams')) return '/teams'
  if (route.path.startsWith('/admin')) return route.path
  return route.path
})

const initial = computed(() => user.me?.nickname?.slice(0, 1) ?? '')

function search() {
  const kw = keyword.value.trim()
  router.push({ name: 'competitions', query: kw ? { keyword: kw } : {} })
}

function onCommand(command: string) {
  if (command === 'logout') logout()
  else if (command === 'my-page') {
    if (user.me) userPage.open(user.me.id)
  } else router.push({ name: command })
}

async function logout() {
  notifications.stop()
  await user.logout()
  router.replace({ name: 'login' })
}

onMounted(() => {
  notifications.start()
  refreshPending()
  clockTimer = window.setInterval(() => (clock.value = now()), 15_000)
})
onBeforeUnmount(() => {
  notifications.stop()
  window.clearInterval(clockTimer)
})
watch(
  () => route.fullPath,
  () => {
    notifications.refresh()
    refreshPending()
  },
)
</script>

<template>
  <div class="layout">
    <header class="header">
      <router-link to="/" class="brand" aria-label="竞搭 首页">
        <span class="brand-name">竞搭</span>
        <span class="brand-sub">COMPETITION<br />TEAM MATCHING</span>
      </router-link>

      <el-menu mode="horizontal" router :default-active="activeMenu" :ellipsis="false" class="menu">
        <el-menu-item index="/">我的主页</el-menu-item>
        <el-menu-item index="/competitions">比赛库</el-menu-item>
        <el-menu-item index="/teams">我的队伍</el-menu-item>
        <el-menu-item index="/calendar">日历</el-menu-item>
        <el-sub-menu v-if="user.isAdmin" index="admin" popper-class="jd-admin-menu">
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
        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          placeholder="搜索比赛"
          size="small"
          class="search"
          aria-label="搜索比赛"
          @keyup.enter="search"
        />
        <router-link to="/notifications" class="bell" aria-label="消息">
          <el-badge :value="notifications.unread" :hidden="notifications.unread === 0" :max="99">
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>
        </router-link>
        <router-link v-if="user.me && user.me.verifyStatus !== 'VERIFIED'" to="/verify" class="unverified">未认证</router-link>
        <el-dropdown trigger="click" @command="onCommand">
          <button type="button" class="user" aria-label="个人菜单">
            <span class="avatar" aria-hidden="true">{{ initial }}</span>
            <span class="who">
              <strong>{{ user.me?.nickname }}</strong>
              <small>{{ user.me?.school?.name ?? '未认证学校' }}</small>
            </span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="my-page">我的资料页</el-dropdown-item>
              <el-dropdown-item command="profile">我的档案</el-dropdown-item>
              <el-dropdown-item command="verify">身份认证</el-dropdown-item>
              <el-dropdown-item command="reviews">互评与信用</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="main">
      <router-view />
    </main>

    <!-- 个人资料页：点任何人的名字从右边滑出 -->
    <UserPageDrawer />

    <footer class="status">
      <span class="kicker">
        <i class="light" :class="{ on: notifications.live }" aria-hidden="true" />
        {{ notifications.live ? '实时推送已连接' : '每分钟同步消息' }}
      </span>
      <span class="kicker">
        竞搭 <span class="sep">/</span> {{ user.me?.nickname }} <span class="sep">/</span> <span class="num">{{ clock }}</span>
      </span>
    </footer>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 28px;
  height: 60px;
  padding: 0 24px;
  background: rgba(235, 232, 226, 0.94);
  backdrop-filter: blur(6px);
  border-bottom: 1px solid var(--jd-line-light);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--jd-ink);
  text-decoration: none;
  white-space: nowrap;
}

.brand-name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.brand-sub {
  padding-left: 10px;
  border-left: 1px solid var(--jd-ink);
  font-size: 8px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: 0.16em;
}

.menu {
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: transparent;
  --el-menu-text-color: var(--jd-muted);
  --el-menu-hover-text-color: var(--jd-ink);
  --el-menu-active-color: var(--jd-ink);
  --el-menu-horizontal-height: 59px;
  --el-menu-item-font-size: 14px;
  flex: 1;
  min-width: 0;
  border-bottom: none;
}

.menu :deep(.el-menu-item.is-active),
.menu :deep(.el-sub-menu.is-active .el-sub-menu__title) {
  font-weight: 700;
  border-bottom-width: 2px;
}

.right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search {
  width: 180px;
}

.bell {
  display: inline-flex;
  color: var(--jd-ink-2);
}

.bell:hover {
  color: var(--jd-accent);
}

.unverified {
  font-size: 12px;
  color: var(--jd-red);
  text-decoration: none;
  border: 1px solid var(--el-color-danger-light-5);
  padding: 1px 6px;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  color: var(--jd-ink);
  font: inherit;
  text-align: left;
}

.avatar {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  background: var(--jd-ink);
  color: var(--jd-paper);
  font-size: 13px;
  font-weight: 700;
}

.who {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.who strong {
  font-size: 13px;
}

.who small {
  font-size: 11px;
  color: var(--jd-muted);
}

.main {
  flex: 1;
  padding: 24px;
}

.status {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 24px 14px;
  border-top: 1px solid var(--jd-line-lighter);
}

.status .kicker {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
}

.sep {
  color: var(--jd-line-strong);
}

.light {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: var(--jd-line-strong);
}

.light.on {
  background: var(--jd-olive);
}

/* 菜单上的待处理数 */
.count {
  display: inline-block;
  min-width: 18px;
  height: 18px;
  margin-left: 6px;
  padding: 0 5px;
  background: var(--jd-red);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}

@media (max-width: 960px) {
  .brand-sub,
  .who,
  .search {
    display: none;
  }

  .header {
    gap: 12px;
    padding: 0 12px;
  }

  .main {
    padding: 16px 12px;
  }
}
</style>
