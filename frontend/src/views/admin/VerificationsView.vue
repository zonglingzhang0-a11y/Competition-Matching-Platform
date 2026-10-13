<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listReviews, rejectReview } from '@/api/admin'
import ApproveDialog from '@/components/admin/ApproveDialog.vue'
import SchoolsPanel from '@/components/admin/SchoolsPanel.vue'
import { useAdminStore } from '@/stores/admin'
import type { ReviewItem, ReviewStatus } from '@/types/api'
import { REVIEW_STATUS } from '@/utils/labels'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

type Tab = 'PENDING' | 'ALL' | 'SCHOOLS'

const admin = useAdminStore()

const tab = ref<Tab>('PENDING')
const items = ref<ReviewItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

const approving = ref<ReviewItem | null>(null)
const approveOpen = ref(false)

async function load() {
  if (tab.value === 'SCHOOLS') return
  loading.value = true
  try {
    // "全部记录"按时间倒序，通过、没通过、还在排队的都在里面
    const status: ReviewStatus | undefined = tab.value === 'PENDING' ? 'PENDING' : undefined
    const res = await listReviews(status, page.value)
    items.value = res.records
    total.value = res.total
    // 顺便刷新菜单上的待审核数（审核、收录后缀之后都会走到这里）
    admin.refresh()
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function approve(item: ReviewItem) {
  approving.value = item
  approveOpen.value = true
}

async function reject(item: ReviewItem) {
  let note: string
  try {
    const res = await ElMessageBox.prompt(`${item.user.nickname} 的教育邮箱 ${item.eduEmail}，不通过的原因会发给对方。`, '不通过', {
      inputPlaceholder: '比如：查不到这所学校，或者邮箱不像学校发的',
      inputValidator: (v: string) => (v && v.trim() ? true : '请写明原因'),
      confirmButtonText: '不通过',
      confirmButtonClass: 'el-button--danger',
    })
    note = res.value.trim()
  } catch {
    return
  }
  try {
    await rejectReview(item.id, note)
    ElMessage.success('已处理，已通知对方')
    load()
  } catch (e) {
    showError(e)
  }
}

watch(tab, () => {
  page.value = 1
  load()
})
onMounted(load)
</script>

<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="待审核" name="PENDING" />
      <el-tab-pane label="全部记录" name="ALL" />
      <el-tab-pane label="学校与邮箱后缀" name="SCHOOLS" />
    </el-tabs>

    <SchoolsPanel v-if="tab === 'SCHOOLS'" @changed="admin.refresh()" />

    <template v-else>
      <p v-if="tab === 'PENDING'" class="hint">
        两种申请：学校还没收录的教育邮箱（邮箱已经验证过能收信，通过时会顺便收录后缀），和学生证截图（先看原图和识别结果再决定）。
      </p>
      <el-table v-loading="loading" :data="items" :empty-text="tab === 'PENDING' ? '没有待审核的申请' : '还没有记录'">
        <el-table-column label="申请人" min-width="160">
          <template #default="{ row }">
            <div>{{ row.user.nickname }}</div>
            <div class="muted">{{ row.user.email }}</div>
          </template>
        </el-table-column>
        <el-table-column label="认证方式" min-width="220">
          <template #default="{ row }">
            <div v-if="row.method === 'SCREENSHOT'" class="shot">
              <el-tag size="small" effect="plain">学生证截图</el-tag>
              <el-tag v-if="row.screenshot?.confidence === 'HIGH'" size="small" type="success">三项一致</el-tag>
              <el-tag v-else-if="row.screenshot?.confidence === 'LOW'" size="small" type="warning">有出入</el-tag>
              <el-tag v-else size="small" type="info">没能识别</el-tag>
              <span class="muted">{{ row.screenshot?.schoolName }}</span>
            </div>
            <div v-else>{{ row.eduEmail }}</div>
            <div v-if="row.user.realName || row.user.major" class="muted">
              档案：{{ [row.user.realName, row.user.major, row.user.enrollYear && `${row.user.enrollYear} 级`].filter(Boolean).join(' · ') }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="130">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="tab === 'PENDING'" label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="approve(row as ReviewItem)">通过</el-button>
            <el-button size="small" @click="reject(row as ReviewItem)">不通过</el-button>
          </template>
        </el-table-column>
        <template v-else>
          <el-table-column label="结果" min-width="200">
            <template #default="{ row }">
              <el-tag :type="REVIEW_STATUS[row.status as ReviewStatus].type" size="small">
                {{ REVIEW_STATUS[row.status as ReviewStatus].label }}
              </el-tag>
              <span v-if="row.school" class="school">{{ row.school.name }}</span>
              <div v-if="row.reviewNote" class="muted">{{ row.reviewNote }}</div>
            </template>
          </el-table-column>
          <el-table-column label="处理" width="150">
            <template #default="{ row }">
              <template v-if="row.reviewedAt">
                <div>{{ row.reviewer ?? '系统' }}</div>
                <div class="muted">{{ fmtDateTime(row.reviewedAt) }}</div>
              </template>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </template>
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

    <ApproveDialog v-model="approveOpen" :item="approving" @approved="load" />
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
}

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.shot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.school {
  margin-left: 8px;
}

.pager {
  justify-content: center;
  margin-top: 12px;
}
</style>
