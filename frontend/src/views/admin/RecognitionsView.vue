<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import UserName from '@/components/UserName.vue'
import { listRecognitions, rejectRecognition, restoreRecognition } from '@/api/admin'
import type { RecognitionAdminRow, RecognitionStatus } from '@/types/api'
import { RECOGNITION_STATUS } from '@/utils/labels'
import { fmtDateTime } from '@/utils/time'
import { safeUrl } from '@/utils/url'
import { showError } from '@/utils/feedback'

/** 各校加分认定：确认靠同校同学，管理员只看一眼、驳回乱填的 */
type Tab = 'ALL' | RecognitionStatus

const tab = ref<Tab>('ALL')
const keyword = ref('')
const rows = ref<RecognitionAdminRow[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listRecognitions(tab.value === 'ALL' ? undefined : tab.value, keyword.value.trim(), page.value)
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

async function reject(row: RecognitionAdminRow) {
  let note: string
  try {
    const res = await ElMessageBox.prompt(
      `${row.schoolName}「${row.competitionName}」认定为「${row.gradeLabel}」。驳回后不再显示，原因会发给提交的同学。`,
      '驳回',
      {
        confirmButtonText: '驳回',
        inputPlaceholder: '比如：文件链接打不开 / 和学校文件不符',
        inputValidator: (v: string) => (v?.trim() ? true : '请写驳回原因'),
      },
    )
    note = res.value.trim()
  } catch {
    return
  }
  try {
    await rejectRecognition(row.id, note)
    ElMessage.success('已驳回，已通知提交的同学')
    load()
  } catch (e) {
    showError(e)
  }
}

async function restore(row: RecognitionAdminRow) {
  try {
    await restoreRecognition(row.id)
    ElMessage.success('已恢复，按确认人数重新计算')
    load()
  } catch (e) {
    showError(e)
  }
}

watch(tab, search)
load()
</script>

<template>
  <div class="page">
    <PageHeader en="RECOGNITION" zh="加分认定" path="管理后台 / 加分认定" />
    <p class="hint">
      各校的认定级别由同校同学标注，另外几位同校同学确认后才在比赛页显示，不用管理员一条条审。
      这里能看到全部标注；同一个学校对同一个比赛有不同说法的会标出来。乱填的可以驳回。
    </p>
    <div class="toolbar">
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="等确认" name="PENDING" />
        <el-tab-pane label="已确认" name="CONFIRMED" />
        <el-tab-pane label="已驳回" name="REJECTED" />
      </el-tabs>
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        placeholder="学校或比赛名称"
        clearable
        class="search"
        @keyup.enter="search"
        @clear="search"
      />
    </div>

    <div v-loading="loading" class="content">
      <el-table :data="rows" empty-text="还没有同学标注过">
        <el-table-column label="学校 · 比赛" min-width="200">
          <template #default="{ row }">
            <div>{{ row.schoolName }}</div>
            <router-link :to="{ name: 'competition', params: { id: row.competitionId } }" class="muted">
              {{ row.competitionName }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column label="认定" min-width="220">
          <template #default="{ row }">
            <b>{{ row.gradeLabel }}</b>
            <span v-if="row.variants > 1" class="warn">　同校有 {{ row.variants }} 种说法</span>
            <div v-if="row.note" class="detail">{{ row.note }}</div>
            <a v-if="safeUrl(row.sourceUrl)" :href="safeUrl(row.sourceUrl)!" target="_blank" rel="noopener noreferrer" class="muted">
              学校文件 ↗
            </a>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="RECOGNITION_STATUS[row.status as RecognitionStatus].type">
              {{ RECOGNITION_STATUS[row.status as RecognitionStatus].label }}
            </el-tag>
            <div class="muted">{{ row.confirmCount }} 人确认</div>
          </template>
        </el-table-column>
        <el-table-column label="提交" width="150">
          <template #default="{ row }">
            <UserName :id="row.submitterId" :name="row.submitterName ?? '（已注销）'" />
            <div class="muted">{{ fmtDateTime(row.createdAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="" width="90" align="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'REJECTED'" size="small" @click="restore(row as RecognitionAdminRow)">恢复</el-button>
            <el-button v-else size="small" type="danger" plain @click="reject(row as RecognitionAdminRow)">驳回</el-button>
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
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
}

.hint {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.tabs {
  flex: 1;
  min-width: 280px;
}

.search {
  width: 220px;
}

.content {
  min-height: 120px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.warn {
  font-size: 12px;
  color: var(--el-color-danger);
}

.detail {
  margin: 4px 0;
  font-size: 13px;
  line-height: 1.6;
}

.pager {
  justify-content: center;
  margin-top: 12px;
}
</style>
