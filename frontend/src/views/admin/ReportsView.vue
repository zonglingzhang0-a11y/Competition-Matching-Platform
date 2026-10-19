<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import UserName from '@/components/UserName.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { flaggedReviews, handleReport, listReports, liftSuspension, suspendedUsers } from '@/api/admin'
import { useAdminStore } from '@/stores/admin'
import type { FlaggedRow, ReportAction, ReportReason, ReportRow, ReportStatus, SuspendedRow } from '@/types/api'
import { REPORT_ACTION, REPORT_REASON, REPORT_STATUS } from '@/utils/labels'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

/** 举报处理和信用复核 */
type Tab = 'PENDING' | 'ALL' | 'SUSPENDED' | 'FLAGGED'

const route = useRoute()
const router = useRouter()
const admin = useAdminStore()

const tab = ref<Tab>((route.query.tab as Tab) || 'PENDING')
const reports = ref<ReportRow[]>([])
const total = ref(0)
const page = ref(1)
const suspended = ref<SuspendedRow[]>([])
const flagged = ref<FlaggedRow[]>([])
const loading = ref(false)

const handling = ref<ReportRow | null>(null)
const handleOpen = ref(false)
const form = reactive<{ result: ReportStatus; action: ReportAction; note: string }>({
  result: 'CONFIRMED',
  action: 'NONE',
  note: '',
})
const saving = ref(false)

const FREQ = ['从未', '偶尔', '有时', '经常']

/** 举报用户能停推荐、停账号；举报队伍只能关掉招募 */
const actions = computed<ReportAction[]>(() =>
  handling.value?.targetType === 'TEAM' ? ['NONE', 'CLOSE_TEAM'] : ['NONE', 'SUSPEND_30', 'BLOCK', 'BAN'],
)

async function load() {
  loading.value = true
  try {
    if (tab.value === 'SUSPENDED') suspended.value = await suspendedUsers()
    else if (tab.value === 'FLAGGED') flagged.value = await flaggedReviews()
    else {
      const res = await listReports(tab.value === 'PENDING' ? 'PENDING' : undefined, page.value)
      reports.value = res.records
      total.value = res.total
    }
    admin.refresh()
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function openHandle(row: ReportRow) {
  handling.value = row
  Object.assign(form, { result: 'CONFIRMED', action: 'NONE', note: '' })
  handleOpen.value = true
}

async function submitHandle() {
  const row = handling.value
  if (!row) return
  if (!form.note.trim()) {
    ElMessage.warning('请写处理说明，会发给举报人')
    return
  }
  saving.value = true
  try {
    await handleReport(row.id, { result: form.result, action: form.result === 'CONFIRMED' ? form.action : 'NONE', note: form.note.trim() })
    ElMessage.success('已处理，已通知举报人')
    handleOpen.value = false
    load()
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

async function lift(row: SuspendedRow) {
  try {
    await ElMessageBox.confirm(`让 ${row.nickname} 重新出现在推荐里？`, '解除暂停', { type: 'info', confirmButtonText: '解除' })
  } catch {
    return
  }
  try {
    await liftSuspension(row.userId)
    ElMessage.success('已解除，已通知对方')
    load()
  } catch (e) {
    showError(e)
  }
}

function answerText(answers: number[]) {
  return answers.map((v) => FREQ[v] ?? v).join(' / ')
}

watch(tab, (t) => {
  page.value = 1
  router.replace({ query: { tab: t } })
  load()
})
load()
</script>

<template>
  <div class="page">
    <PageHeader en="REPORTS" zh="举报与信用" path="管理后台 / 举报与信用" />
    <el-tabs v-model="tab">
      <el-tab-pane label="待处理举报" name="PENDING" />
      <el-tab-pane label="全部举报" name="ALL" />
      <el-tab-pane label="暂停推荐的人" name="SUSPENDED" />
      <el-tab-pane label="异常评分" name="FLAGGED" />
    </el-tabs>

    <div v-loading="loading" class="content">
      <!-- 举报 -->
      <template v-if="tab === 'PENDING' || tab === 'ALL'">
        <el-table :data="reports" :empty-text="tab === 'PENDING' ? '没有待处理的举报' : '还没有举报'">
          <el-table-column label="被举报" min-width="170">
            <template #default="{ row }">
              <div>
                <el-tag size="small" effect="plain">{{ row.targetType === 'TEAM' ? '队伍' : '用户' }}</el-tag>
                {{ row.targetName }}
              </div>
              <div class="muted">{{ row.targetSub }}</div>
              <div v-if="row.sameTarget > 1" class="warn">共被举报 {{ row.sameTarget }} 次</div>
            </template>
          </el-table-column>
          <el-table-column label="原因和说明" min-width="240">
            <template #default="{ row }">
              <b>{{ REPORT_REASON[row.reason as ReportReason] }}</b>
              <div v-if="row.detail" class="detail">{{ row.detail }}</div>
              <div class="muted">{{ row.reporter }} 举报于 {{ fmtDateTime(row.createdAt) }}</div>
            </template>
          </el-table-column>
          <el-table-column v-if="tab === 'PENDING'" label="" width="90" align="right">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openHandle(row as ReportRow)">处理</el-button>
            </template>
          </el-table-column>
          <el-table-column v-else label="结果" min-width="180">
            <template #default="{ row }">
              <el-tag :type="REPORT_STATUS[row.status as ReportStatus].type" size="small">
                {{ REPORT_STATUS[row.status as ReportStatus].label }}
              </el-tag>
              <div v-if="row.resultNote" class="detail">{{ row.resultNote }}</div>
              <div v-if="row.handledAt" class="muted">{{ row.handler }} · {{ fmtDateTime(row.handledAt) }}</div>
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

      <!-- 暂停推荐 -->
      <template v-if="tab === 'SUSPENDED'">
        <p class="hint">
          互评里在两场比赛都被多数队友评为"经常失联或不交付"的人，系统会自动暂停推荐 30 天；举报核实后也可能被暂停或永久停止推荐。
          被暂停的人仍然可以自己找队伍、申请入队，只是不会出现在队长的推荐列表里。
        </p>
        <el-table :data="suspended" empty-text="现在没有被暂停推荐的人">
          <el-table-column label="用户" min-width="180">
            <template #default="{ row }">
              <div><UserName :id="row.userId" :name="row.nickname" /></div>
              <div class="muted">{{ row.email }}</div>
            </template>
          </el-table-column>
          <el-table-column label="信用分" width="140">
            <template #default="{ row }">
              {{ row.score === null ? '无互评记录' : row.score }}
              <span v-if="row.ratedRounds" class="muted">（{{ row.ratedRounds }} 场）</span>
            </template>
          </el-table-column>
          <el-table-column label="暂停到" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.permanent" type="danger" size="small">永久</el-tag>
              <span v-else>{{ fmtDateTime(row.suspendedUntil, true) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="" width="100" align="right">
            <template #default="{ row }">
              <el-button size="small" @click="lift(row as SuspendedRow)">解除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 异常评分 -->
      <template v-if="tab === 'FLAGGED'">
        <p class="hint">
          某个评分人在多数问题上和其他队友的评价差两档以上，他打的分权重会降到一半。这里列出来供复核，比如是不是在报复。
        </p>
        <el-table :data="flagged" empty-text="还没有被标记的评分">
          <el-table-column label="队伍" prop="teamName" min-width="120" />
          <el-table-column label="评分人 → 被评人" min-width="150">
            <template #default="{ row }">{{ row.rater }} → {{ row.ratee }}</template>
          </el-table-column>
          <el-table-column label="6 项评价" min-width="280">
            <template #default="{ row }">
              <span class="answers">{{ answerText(row.answers as number[]) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="提交" width="120">
            <template #default="{ row }">{{ fmtDateTime(row.submittedAt) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <el-dialog v-model="handleOpen" title="处理举报" width="480px">
      <template v-if="handling">
        <p class="summary">
          {{ handling.targetType === 'TEAM' ? '队伍' : '用户' }}「{{ handling.targetName }}」 ·
          {{ REPORT_REASON[handling.reason] }}
        </p>
        <p v-if="handling.detail" class="detail">{{ handling.detail }}</p>
        <el-form label-position="top" @submit.prevent="submitHandle">
          <el-form-item label="核实结果">
            <el-radio-group v-model="form.result">
              <el-radio value="CONFIRMED">属实</el-radio>
              <el-radio value="DISMISSED">不属实</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.result === 'CONFIRMED'" label="处理方式">
            <el-radio-group v-model="form.action" class="actions">
              <el-radio v-for="a in actions" :key="a" :value="a">{{ REPORT_ACTION[a] }}</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="处理说明（会发给举报人）" required>
            <el-input v-model="form.note" type="textarea" :rows="3" maxlength="255" show-word-limit />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="handleOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
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

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.answers {
  font-size: 13px;
}

.summary {
  margin: 0;
  font-weight: 500;
}

.actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.pager {
  justify-content: center;
  margin-top: 12px;
}
</style>
