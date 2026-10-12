<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, setUserStatus } from '@/api/admin'
import type { AdminUserRow, UserRole, UserStatus, VerifyStatus } from '@/types/api'
import { ROLE_LABEL, USER_STATUS, VERIFY_STATUS } from '@/utils/labels'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'
import { useUserStore } from '@/stores/user'

const me = useUserStore()

const query = reactive<{ keyword: string; role?: UserRole; status?: UserStatus }>({ keyword: '' })
const page = ref(1)
const rows = ref<AdminUserRow[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listUsers({
      keyword: query.keyword.trim() || undefined,
      role: query.role,
      status: query.status,
      page: page.value,
    })
    rows.value = res.records
    total.value = res.total
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function toggle(row: AdminUserRow) {
  const ban = row.status === 'ACTIVE'
  try {
    await ElMessageBox.confirm(
      ban
        ? `停用「${row.nickname}」（${row.email}）？对方会立刻被踢下线，之后登录不了，也不会再被推荐给队长。`
        : `恢复「${row.nickname}」的账号？`,
      ban ? '停用账号' : '恢复账号',
      { type: ban ? 'warning' : 'info', confirmButtonText: ban ? '停用' : '恢复' },
    )
  } catch {
    return
  }
  try {
    await setUserStatus(row.id, ban ? 'BANNED' : 'ACTIVE')
    ElMessage.success(ban ? '已停用' : '已恢复')
    load()
  } catch (e) {
    showError(e)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="搜邮箱或昵称"
        class="search"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.role" clearable placeholder="角色" class="filter" @change="search">
        <el-option v-for="(label, value) in ROLE_LABEL" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="query.status" clearable placeholder="状态" class="filter" @change="search">
        <el-option v-for="(s, value) in USER_STATUS" :key="value" :label="s.label" :value="value" />
      </el-select>
      <el-button @click="search">搜索</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" empty-text="没有找到账号">
      <el-table-column label="账号" min-width="200">
        <template #default="{ row }">
          <div>
            {{ row.nickname }}
            <el-tag v-if="row.role !== 'STUDENT'" size="small" type="primary" class="role">
              {{ ROLE_LABEL[row.role as UserRole] }}
            </el-tag>
          </div>
          <div class="muted">{{ row.email }}</div>
        </template>
      </el-table-column>
      <el-table-column label="认证" min-width="160">
        <template #default="{ row }">
          <el-tag :type="VERIFY_STATUS[row.verifyStatus as VerifyStatus].type" size="small">
            {{ VERIFY_STATUS[row.verifyStatus as VerifyStatus].label }}
          </el-tag>
          <span v-if="row.schoolName" class="school">{{ row.schoolName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="注册" width="120">
        <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="最近登录" width="120">
        <template #default="{ row }">{{ row.lastLoginAt ? fmtDateTime(row.lastLoginAt) : '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="150" fixed="right">
        <template #default="{ row }">
          <el-tag :type="USER_STATUS[row.status as UserStatus].type" size="small">
            {{ USER_STATUS[row.status as UserStatus].label }}
          </el-tag>
          <el-button
            v-if="row.role !== 'ADMIN' && row.id !== me.me?.id"
            size="small"
            link
            :type="row.status === 'ACTIVE' ? 'danger' : 'primary'"
            class="action"
            @click="toggle(row as AdminUserRow)"
          >
            {{ row.status === 'ACTIVE' ? '停用' : '恢复' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
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
  max-width: 1080px;
  margin: 0 auto;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.search {
  max-width: 260px;
}

.filter {
  width: 120px;
}

.role {
  margin-left: 6px;
}

.school {
  margin-left: 8px;
}

.action {
  margin-left: 8px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  justify-content: center;
  margin-top: 12px;
}
</style>
