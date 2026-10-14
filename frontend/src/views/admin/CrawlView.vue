<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { crawlRecords, crawlSources, crawlStatus, deleteCrawlSource, ignoreCrawl, runCrawl, updateCrawlSource } from '@/api/admin'
import CrawlApproveDialog from '@/components/admin/CrawlApproveDialog.vue'
import CrawlSourceDialog from '@/components/admin/CrawlSourceDialog.vue'
import { useAdminStore } from '@/stores/admin'
import type { CrawlRecordRow, CrawlRunStatus, CrawlSourceRow, CrawlStatus, Extracted } from '@/types/api'
import { CRAWL_STATUS } from '@/utils/labels'
import { fmtDate, fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

/**
 * 通知抓取：每天从比赛官网抓新通知，规则抽出报名时间、比赛时间、人数等，管理员确认后写进比赛库。
 */
type Tab = 'PENDING' | 'DONE' | 'SOURCES'

const route = useRoute()
const router = useRouter()
const admin = useAdminStore()

const tab = ref<Tab>((route.query.tab as Tab) || 'PENDING')
const records = ref<CrawlRecordRow[]>([])
const total = ref(0)
const page = ref(1)
const sources = ref<CrawlSourceRow[]>([])
const loading = ref(false)
const status = ref<CrawlRunStatus | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined

const approveOpen = ref(false)
const approveId = ref<number | null>(null)
const sourceOpen = ref(false)
const editing = ref<CrawlSourceRow | null>(null)

async function load() {
  loading.value = true
  try {
    if (tab.value === 'SOURCES') sources.value = await crawlSources()
    else {
      const res = await crawlRecords(tab.value, page.value)
      records.value = res.records
      total.value = res.total
    }
    admin.refresh()
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

/** 抓取在后台跑，跑着的时候每 2 秒看一次，跑完刷新列表 */
async function poll() {
  clearTimeout(timer)
  try {
    const wasRunning = status.value?.running
    status.value = await crawlStatus()
    if (status.value.running) timer = setTimeout(poll, 2000)
    else if (wasRunning) {
      ElMessage.success(status.value.summary ?? '抓取完成')
      load()
    }
  } catch (e) {
    showError(e)
  }
}

async function run(source?: CrawlSourceRow) {
  try {
    await runCrawl(source?.id)
    ElMessage.info(source ? `开始抓取「${source.name}」` : '开始抓取全部启用的来源')
    status.value = { running: true, startedAt: null, finishedAt: null, summary: null }
    timer = setTimeout(poll, 1000)
  } catch (e) {
    showError(e)
  }
}

function openApprove(row: CrawlRecordRow) {
  approveId.value = row.id
  approveOpen.value = true
}

async function ignore(row: CrawlRecordRow) {
  try {
    await ignoreCrawl(row.id)
    ElMessage.success('已忽略')
    load()
  } catch (e) {
    showError(e)
  }
}

function openSource(row: CrawlSourceRow | null) {
  editing.value = row
  sourceOpen.value = true
}

async function toggle(row: CrawlSourceRow, enabled: boolean) {
  try {
    await updateCrawlSource(row.id, { ...row, enabled })
    row.enabled = enabled
  } catch (e) {
    showError(e)
  }
}

async function removeSource(row: CrawlSourceRow) {
  try {
    await ElMessageBox.confirm(`删除抓取来源「${row.name}」？`, '删除', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await deleteCrawlSource(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    showError(e)
  }
}

/** 抽取结果写成一两行字 */
function summary(x: Extracted | null): string[] {
  if (!x) return []
  const lines: string[] = []
  if (x.registrationEnd) lines.push(`报名截止 ${fmtDateTime(x.registrationEnd, true)}`)
  if (x.contestStart) {
    const show = (iso: string) => (x.contestAllDay ? fmtDate(iso, true) : fmtDateTime(iso, true))
    lines.push(`比赛 ${show(x.contestStart)}${x.contestEnd && x.contestEnd !== x.contestStart ? ' 至 ' + show(x.contestEnd) : ''}`)
  }
  const tags: string[] = []
  if (x.teamMax !== null) tags.push(x.teamMin === x.teamMax ? `${x.teamMax} 人一队` : `${x.teamMin ?? 1}–${x.teamMax} 人`)
  else if (x.teamMin !== null) tags.push(`至少 ${x.teamMin} 人`)
  if (x.sameSchoolRequired !== null) tags.push(x.sameSchoolRequired ? '须同校' : '可跨校')
  if (x.advisorRequired !== null) tags.push(x.advisorRequired ? '须有指导老师' : '指导老师可选')
  if (tags.length) lines.push(tags.join(' · '))
  return lines
}

function done() {
  load()
}

watch(tab, (t) => {
  page.value = 1
  router.replace({ query: { tab: t } })
  load()
})
load()
poll()
onBeforeUnmount(() => clearTimeout(timer))
</script>

<template>
  <div class="page">
    <div class="bar">
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane label="待确认" name="PENDING" />
        <el-tab-pane label="已处理" name="DONE" />
        <el-tab-pane label="抓取来源" name="SOURCES" />
      </el-tabs>
      <div class="run">
        <span v-if="status?.running" class="muted">正在抓取…</span>
        <span v-else-if="status?.finishedAt" class="muted" :title="status.summary ?? ''">
          上次 {{ fmtDateTime(status.finishedAt) }}：{{ status.summary }}
        </span>
        <el-button type="primary" :loading="status?.running" @click="run()">立即抓取</el-button>
      </div>
    </div>

    <div v-loading="loading" class="content">
      <template v-if="tab !== 'SOURCES'">
        <p v-if="tab === 'PENDING'" class="hint">
          每天早上从比赛官网抓一次新通知，自动找出报名时间、比赛时间和组队要求。打开原文核对后"采用"，会生成一届草稿（或更新已有的一届），在比赛库管理里检查后发布。
        </p>
        <el-table :data="records" :empty-text="tab === 'PENDING' ? '没有等确认的通知' : '还没有处理过的通知'">
          <el-table-column label="通知" min-width="300">
            <template #default="{ row }">
              <a :href="row.sourceUrl" target="_blank" rel="noopener noreferrer" class="link">{{ row.title || row.sourceUrl }}</a>
              <div class="muted">{{ row.competitionName }} · 抓取于 {{ fmtDateTime(row.fetchedAt) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="抽取结果" min-width="250">
            <template #default="{ row }">
              <template v-if="summary(row.extracted as Extracted | null).length">
                <div v-for="line in summary(row.extracted as Extracted | null)" :key="line" class="line">{{ line }}</div>
              </template>
              <span v-else class="muted">没找到时间和人数，可能在附件里</span>
            </template>
          </el-table-column>
          <el-table-column v-if="tab === 'PENDING'" label="" width="150" align="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openApprove(row as CrawlRecordRow)">采用</el-button>
              <el-button size="small" @click="ignore(row as CrawlRecordRow)">忽略</el-button>
            </template>
          </el-table-column>
          <el-table-column v-else label="结果" min-width="200">
            <template #default="{ row }">
              <el-tag :type="CRAWL_STATUS[row.status as CrawlStatus].type" size="small">
                {{ CRAWL_STATUS[row.status as CrawlStatus].label }}
              </el-tag>
              <span v-if="row.editionTitle" class="line"> → {{ row.editionTitle }}</span>
              <div v-if="row.note" class="muted">{{ row.note }}</div>
              <div v-if="row.reviewedAt" class="muted">{{ row.reviewerName }} · {{ fmtDateTime(row.reviewedAt) }}</div>
              <el-button
                v-if="row.status === 'IGNORED'"
                size="small"
                link
                type="primary"
                @click="openApprove(row as CrawlRecordRow)"
              >
                改为采用
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
      </template>

      <template v-else>
        <div class="sources-bar">
          <p class="hint">
            每个比赛官网一条。抓取时先看网站的 robots.txt，同一网站两次请求至少隔 2 秒，每次每个来源最多打开 10 篇新通知。
          </p>
          <el-button @click="openSource(null)">+ 新增来源</el-button>
        </div>
        <el-table :data="sources" empty-text="还没有抓取来源">
          <el-table-column label="来源" min-width="220">
            <template #default="{ row }">
              <div>{{ row.name }}</div>
              <div class="muted">{{ row.competitionName }}</div>
              <a :href="row.listUrl" target="_blank" rel="noopener noreferrer" class="link small">{{ row.listUrl }}</a>
            </template>
          </el-table-column>
          <el-table-column label="每天自动抓" width="100">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" @change="(v: string | number | boolean) => toggle(row as CrawlSourceRow, !!v)" />
            </template>
          </el-table-column>
          <el-table-column label="上次抓取" min-width="220">
            <template #default="{ row }">
              <template v-if="row.lastRunAt">
                <div class="muted">{{ fmtDateTime(row.lastRunAt) }}</div>
                <div class="line" :class="{ error: row.lastResult?.startsWith('出错') }">{{ row.lastResult }}</div>
              </template>
              <span v-else class="muted">还没抓过</span>
              <div v-if="!row.allowLlm" class="muted">只用规则抽取</div>
              <div v-if="row.note" class="muted">{{ row.note }}</div>
            </template>
          </el-table-column>
          <el-table-column label="通知" width="100">
            <template #default="{ row }">
              <div>共 {{ row.total }} 条</div>
              <div v-if="row.pending" class="muted">待确认 {{ row.pending }}</div>
            </template>
          </el-table-column>
          <el-table-column label="" width="200" align="right">
            <template #default="{ row }">
              <el-button size="small" :disabled="status?.running" @click="run(row as CrawlSourceRow)">抓取</el-button>
              <el-button size="small" @click="openSource(row as CrawlSourceRow)">编辑</el-button>
              <el-button size="small" type="danger" plain @click="removeSource(row as CrawlSourceRow)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <CrawlApproveDialog v-model="approveOpen" :record-id="approveId" @done="done" />
    <CrawlSourceDialog v-model="sourceOpen" :source="editing" @saved="load" />
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
}

.bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.tabs {
  flex: 1;
  min-width: 280px;
}

.run {
  display: flex;
  gap: 10px;
  align-items: center;
  max-width: 100%;
  padding-top: 4px;
}

.run .muted {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  min-height: 120px;
}

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.sources-bar {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.link {
  color: var(--el-color-primary);
  text-decoration: none;
  word-break: break-all;
}

.link:hover {
  text-decoration: underline;
}

.small {
  font-size: 12px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.line {
  font-size: 13px;
  line-height: 1.6;
}

.error {
  color: var(--el-color-danger);
}

.pager {
  justify-content: center;
  margin-top: 12px;
}
</style>
