<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listNotifications, markAllRead, markRead } from '@/api/notification'
import { useNotificationStore } from '@/stores/notification'
import type { Notification } from '@/types/api'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

const router = useRouter()
const badge = useNotificationStore()

const items = ref<Notification[]>([])
const total = ref(0)
const page = ref(1)
const unreadOnly = ref(false)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listNotifications(page.value, unreadOnly.value)
    items.value = res.records
    total.value = res.total
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

async function open(n: Notification) {
  if (!n.readAt) {
    try {
      await markRead(n.id)
      n.readAt = new Date().toISOString()
      badge.refresh()
    } catch (e) {
      showError(e)
    }
  }
  if (n.refType === 'TEAM' && n.refId) {
    router.push({ name: 'team', params: { id: n.refId } })
  } else if (n.refType === 'COMPETITION' && n.refId) {
    router.push({ name: 'competition', params: { id: n.refId } })
  } else if (n.type === 'VERIFY_RESULT') {
    router.push({ name: 'verify' })
  }
}

async function readAll() {
  try {
    await markAllRead()
    await Promise.all([load(), badge.refresh()])
  } catch (e) {
    showError(e)
  }
}

function filterChanged() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-checkbox v-model="unreadOnly" @change="filterChanged">只看未读</el-checkbox>
      <el-button size="small" :disabled="badge.unread === 0" @click="readAll">全部标为已读</el-button>
    </div>
    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && items.length === 0" description="没有消息" />
      <div
        v-for="n in items"
        :key="n.id"
        class="item"
        :class="{ unread: !n.readAt, clickable: !!n.refType || n.type === 'VERIFY_RESULT' || !n.readAt }"
        @click="open(n)"
      >
        <span v-if="!n.readAt" class="dot" aria-label="未读" />
        <div class="body">
          <div class="title">{{ n.title }}</div>
          <div v-if="n.content" class="content">{{ n.content }}</div>
          <div class="time">{{ fmtDateTime(n.createdAt) }}</div>
        </div>
      </div>
    </div>
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next"
      class="pager"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.list {
  background: var(--el-bg-color);
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  min-height: 120px;
}

.item {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.item:last-child {
  border-bottom: none;
}

.clickable {
  cursor: pointer;
}

.clickable:hover {
  background: var(--el-fill-color-light);
}

.dot {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--el-color-primary);
}

.item:not(.unread) .body {
  padding-left: 18px;
}

.title {
  font-size: 14px;
  font-weight: 500;
}

.content {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  justify-content: center;
  margin-top: 12px;
}
</style>
