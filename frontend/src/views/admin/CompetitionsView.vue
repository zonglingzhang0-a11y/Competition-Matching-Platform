<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminCompetitions,
  deleteCompetition,
  deleteEdition,
  roleTemplates,
  updateEdition,
} from '@/api/admin'
import CompetitionFormDialog from '@/components/admin/CompetitionFormDialog.vue'
import EditionFormDialog from '@/components/admin/EditionFormDialog.vue'
import NodesDialog from '@/components/admin/NodesDialog.vue'
import type { AdminCompetitionRow, AdminEditionRow, EditionPhase, EditionStatus, RoleTemplate } from '@/types/api'
import { EDITION_STATUS, LEVEL_LABEL, PHASE, PRESET_LABEL } from '@/utils/labels'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

const keyword = ref('')
const page = ref(1)
const rows = ref<AdminCompetitionRow[]>([])
const total = ref(0)
const loading = ref(false)
const templates = ref<RoleTemplate[]>([])

const competitionOpen = ref(false)
const editingCompetition = ref<AdminCompetitionRow | null>(null)

const editionOpen = ref(false)
const editionParent = ref<AdminCompetitionRow | null>(null)
const editingEdition = ref<AdminEditionRow | null>(null)

const nodesOpen = ref(false)
const nodesFor = ref<{ id: number; title: string } | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await adminCompetitions(keyword.value.trim(), page.value)
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

function editCompetition(row: AdminCompetitionRow | null) {
  editingCompetition.value = row
  competitionOpen.value = true
}

function editEdition(parent: AdminCompetitionRow, edition: AdminEditionRow | null) {
  editionParent.value = parent
  editingEdition.value = edition
  editionOpen.value = true
}

function editNodes(parent: AdminCompetitionRow, edition: AdminEditionRow) {
  nodesFor.value = { id: edition.id, title: `${parent.shortName ?? parent.name} ${edition.title}` }
  nodesOpen.value = true
}

/** 草稿一键发布、已发布一键归档 */
async function setStatus(edition: AdminEditionRow, status: EditionStatus) {
  try {
    await updateEdition(edition.id, {
      year: edition.year,
      title: edition.title,
      teamMin: edition.teamMin,
      teamMax: edition.teamMax,
      sameSchoolRequired: edition.sameSchoolRequired,
      advisorRequired: edition.advisorRequired,
      englishRequired: edition.englishRequired,
      registrationStart: edition.registrationStart,
      registrationEnd: edition.registrationEnd,
      noticeUrl: edition.noticeUrl,
      status,
    })
    ElMessage.success(status === 'PUBLISHED' ? '已发布，学生能看到了' : '已归档')
    load()
  } catch (e) {
    showError(e)
  }
}

async function removeCompetition(row: AdminCompetitionRow) {
  try {
    await ElMessageBox.confirm(`删除「${row.name}」和它的所有届次？已经有队伍的比赛删不了，可以改成归档。`, '删除比赛', {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  try {
    await deleteCompetition(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    showError(e)
  }
}

async function removeEdition(edition: AdminEditionRow) {
  try {
    await ElMessageBox.confirm(`删除届次「${edition.title}」？`, '删除届次', { type: 'warning', confirmButtonText: '删除' })
  } catch {
    return
  }
  try {
    await deleteEdition(edition.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    showError(e)
  }
}

onMounted(async () => {
  load()
  try {
    templates.value = await roleTemplates()
  } catch (e) {
    showError(e)
  }
})
</script>

<template>
  <div class="page">
    <PageHeader en="LIBRARY" zh="比赛库管理" path="管理后台 / 比赛库管理" />
    <div class="toolbar">
      <el-input v-model="keyword" clearable placeholder="搜比赛名或简称" class="search" @keyup.enter="search" @clear="search" />
      <el-button @click="search">搜索</el-button>
      <el-button type="primary" @click="editCompetition(null)">新建比赛</el-button>
    </div>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && rows.length === 0" description="没有找到比赛" />
      <el-card v-for="c in rows" :key="c.id" shadow="never" class="competition">
        <div class="head">
          <div class="info">
            <div class="name">
              {{ c.name }}
              <span v-if="c.shortName" class="short">（{{ c.shortName }}）</span>
            </div>
            <div class="tags">
              <el-tag size="small">{{ LEVEL_LABEL[c.level] }}</el-tag>
              <el-tag size="small" type="info">{{ c.category }}</el-tag>
              <el-tag v-if="c.inCaheList" size="small" type="success">竞赛目录</el-tag>
              <el-tag size="small" type="info">匹配：{{ PRESET_LABEL[c.matchPreset] }}</el-tag>
              <span v-if="c.organizer" class="muted">{{ c.organizer }}</span>
            </div>
          </div>
          <div class="actions">
            <el-button size="small" @click="editEdition(c, null)">新增届次</el-button>
            <el-button size="small" @click="editCompetition(c)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="removeCompetition(c)">删除</el-button>
          </div>
        </div>
        <el-table v-if="c.editions.length" :data="c.editions" size="small" class="editions">
          <el-table-column label="届次" min-width="150">
            <template #default="{ row }">
              {{ row.title }}
              <el-tag :type="EDITION_STATUS[row.status as EditionStatus].type" size="small" class="status">
                {{ EDITION_STATUS[row.status as EditionStatus].label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="报名" min-width="170">
            <template #default="{ row }">
              <el-tag :type="PHASE[row.phase as EditionPhase].type" size="small">{{ PHASE[row.phase as EditionPhase].label }}</el-tag>
              <span v-if="row.registrationEnd" class="muted"> 截止 {{ fmtDateTime(row.registrationEnd, true) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="人数" width="70">
            <template #default="{ row }">{{ row.teamMin }}–{{ row.teamMax }}</template>
          </el-table-column>
          <el-table-column prop="teams" label="队伍" width="60" align="right" />
          <el-table-column label="操作" width="290" align="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'DRAFT'" size="small" link type="success" @click="setStatus(row as AdminEditionRow, 'PUBLISHED')">
                发布
              </el-button>
              <el-button v-else-if="row.status === 'PUBLISHED'" size="small" link @click="setStatus(row as AdminEditionRow, 'ARCHIVED')">
                归档
              </el-button>
              <el-button size="small" link type="primary" @click="editNodes(c, row as AdminEditionRow)">时间节点（{{ row.nodes }}）</el-button>
              <el-button size="small" link type="primary" @click="editEdition(c, row as AdminEditionRow)">编辑</el-button>
              <el-button size="small" link type="danger" :disabled="row.teams > 0" @click="removeEdition(row as AdminEditionRow)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <p v-else class="muted empty">还没有届次，点"新增届次"加一届。</p>
      </el-card>
    </div>
    <el-pagination
      v-if="total > 10"
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      layout="prev, pager, next"
      class="pager"
      @current-change="load"
    />

    <CompetitionFormDialog v-model="competitionOpen" :row="editingCompetition" :templates="templates" @saved="load" />
    <EditionFormDialog
      v-model="editionOpen"
      :competition-id="editionParent?.id ?? null"
      :competition-name="editionParent?.shortName ?? editionParent?.name ?? ''"
      :edition="editingEdition"
      @saved="load"
    />
    <NodesDialog v-model="nodesOpen" :edition-id="nodesFor?.id ?? null" :title="nodesFor?.title ?? ''" @saved="load" />
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.search {
  max-width: 280px;
}

.list {
  min-height: 120px;
}

.competition {
  margin-bottom: 12px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.name {
  font-size: 15px;
  font-weight: 500;
}

.short {
  font-weight: normal;
  color: var(--el-text-color-secondary);
}

.tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.editions {
  margin-top: 10px;
}

.status {
  margin-left: 6px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.empty {
  margin: 10px 0 0;
}

.pager {
  justify-content: center;
  margin-top: 12px;
}
</style>
