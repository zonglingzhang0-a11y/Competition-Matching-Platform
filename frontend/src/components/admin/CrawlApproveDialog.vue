<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { approveCrawl, crawlRecord, editionNodes } from '@/api/admin'
import NodeListEditor from '@/components/admin/NodeListEditor.vue'
import type { CrawlRecordDetail, EditionBrief, EditionForm, Extracted } from '@/types/api'
import { EDITION_STATUS } from '@/utils/labels'
import { emptyRow, firstIncomplete, toForms, toRow, type NodeRow } from '@/utils/nodeRows'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

/**
 * 采用一条抓到的通知：新建一届（草稿），或者更新这个比赛已有的一届。
 * 表单先用抽出的字段填好，没抽出的沿用这个比赛最近一届的设置；管理员核对原文后改好再提交。
 */
const props = defineProps<{ recordId: number | null }>()
const emit = defineEmits<{ done: [] }>()
const open = defineModel<boolean>({ required: true })

const EVIDENCE_LABEL: Record<string, string> = {
  registration: '报名',
  contest: '比赛时间',
  teamSize: '人数',
  sameSchool: '同校',
  advisor: '指导老师',
}

const detail = ref<CrawlRecordDetail | null>(null)
const loading = ref(false)
const saving = ref(false)
const mode = ref<'CREATE' | 'UPDATE'>('CREATE')
const editionId = ref<number | null>(null)
const form = reactive<EditionForm>(blankForm())
const rows = ref<NodeRow[]>([])
const nodesLoading = ref(false)

const extracted = computed<Extracted | null>(() => detail.value?.record.extracted ?? null)
const editions = computed(() => detail.value?.editions ?? [])
const evidence = computed(() => Object.entries(extracted.value?.evidence ?? {}))

function blankForm(): EditionForm {
  return {
    year: new Date().getFullYear(),
    title: '',
    teamMin: 1,
    teamMax: 3,
    sameSchoolRequired: false,
    advisorRequired: false,
    englishRequired: false,
    registrationStart: null,
    registrationEnd: null,
    noticeUrl: null,
    status: 'DRAFT',
  }
}

watch(open, async (value) => {
  if (!value || props.recordId === null) return
  detail.value = null
  loading.value = true
  try {
    detail.value = await crawlRecord(props.recordId)
    // 已经有同一年的届次就默认更新它，否则新建
    const sameYear = editions.value.find((e) => e.year === extracted.value?.year)
    mode.value = sameYear ? 'UPDATE' : 'CREATE'
    editionId.value = sameYear?.id ?? editions.value[0]?.id ?? null
    await fill()
  } catch (e) {
    showError(e)
    open.value = false
  } finally {
    loading.value = false
  }
})

watch([mode, editionId], () => {
  if (!loading.value && detail.value) fill()
})

/** 按当前选择重新填表：底子是要更新的那一届（或最近一届），再盖上抽出来的字段 */
async function fill() {
  const d = detail.value
  if (!d) return
  const x = d.record.extracted
  const target = mode.value === 'UPDATE' ? editions.value.find((e) => e.id === editionId.value) : undefined
  const base: EditionBrief | undefined = target ?? editions.value[0]
  const next = blankForm()
  if (base) {
    Object.assign(next, {
      teamMin: base.teamMin,
      teamMax: base.teamMax,
      sameSchoolRequired: base.sameSchoolRequired,
      advisorRequired: base.advisorRequired,
      englishRequired: base.englishRequired,
    })
  }
  if (target) {
    Object.assign(next, {
      year: target.year,
      title: target.title,
      registrationStart: target.registrationStart,
      registrationEnd: target.registrationEnd,
      status: target.status,
    })
  } else {
    next.year = x?.year ?? next.year
    next.title = x?.editionTitle ?? `${next.year} 年`
  }
  if (x) {
    if (x.teamMax !== null) {
      next.teamMin = x.teamMin ?? 1
      next.teamMax = x.teamMax
    } else if (x.teamMin !== null) {
      next.teamMin = x.teamMin
      next.teamMax = Math.max(next.teamMax, x.teamMin)
    }
    if (x.sameSchoolRequired !== null) next.sameSchoolRequired = x.sameSchoolRequired
    if (x.advisorRequired !== null) next.advisorRequired = x.advisorRequired
    if (x.registrationEnd) {
      next.registrationEnd = x.registrationEnd
      next.registrationStart = x.registrationStart ?? next.registrationStart
    }
  }
  next.noticeUrl = d.record.sourceUrl
  Object.assign(form, next)

  const fresh = x ? noticeNodes(x) : []
  if (target) {
    nodesLoading.value = true
    try {
      rows.value = merge((await editionNodes(target.id)).map(toRow), fresh)
    } catch (e) {
      showError(e)
      rows.value = fresh
    } finally {
      nodesLoading.value = false
    }
  } else {
    rows.value = fresh
  }
}

/** 抽出的报名截止和比赛时间变成两个节点 */
function noticeNodes(x: Extracted): NodeRow[] {
  const out: NodeRow[] = []
  if (x.registrationEnd) {
    out.push({ ...emptyRow(), title: '报名截止', nodeType: 'REGISTRATION', startAt: x.registrationEnd, fromNotice: true })
  }
  if (x.contestStart) {
    out.push({
      ...emptyRow(),
      title: '比赛',
      nodeType: 'CONTEST',
      startAt: x.contestStart,
      endAt: x.contestEnd,
      allDay: x.contestAllDay,
      busy: true,
      fromNotice: true,
    })
  }
  return out
}

/** 已有节点里同类型的只有一个，就改它的时间；否则（没有或有好几个，比如初赛、决赛）加在后面让管理员自己挑 */
function merge(existing: NodeRow[], fresh: NodeRow[]): NodeRow[] {
  const result = [...existing]
  for (const f of fresh) {
    const same = result.filter((r) => r.nodeType === f.nodeType)
    if (same.length === 1) {
      Object.assign(same[0]!, {
        startAt: f.startAt,
        endAt: f.endAt,
        allDay: f.allDay,
        endMode: 'FIXED',
        endLatestAt: null,
        startLatestAt: null,
        fuzzyStart: false,
        fromNotice: true,
      })
    } else {
      result.push(f)
    }
  }
  return result
}

async function submit() {
  const d = detail.value
  if (!d) return
  if (!form.title.trim()) {
    ElMessage.warning('请填写届次名称')
    return
  }
  if (form.teamMax < form.teamMin) {
    ElMessage.warning('最多人数不能少于最少人数')
    return
  }
  if (mode.value === 'UPDATE' && editionId.value === null) {
    ElMessage.warning('请选择要更新哪一届')
    return
  }
  const missing = firstIncomplete(rows.value)
  if (missing >= 0) {
    ElMessage.warning(`第 ${missing + 1} 个节点还没填名称或开始时间`)
    return
  }
  saving.value = true
  try {
    await approveCrawl(d.record.id, {
      editionId: mode.value === 'UPDATE' ? editionId.value : null,
      edition: { ...form, title: form.title.trim() },
      nodes: toForms(rows.value),
    })
    ElMessage.success(
      mode.value === 'UPDATE'
        ? `已更新「${form.title.trim()}」`
        : `已生成「${form.title.trim()}」${form.status === 'DRAFT' ? '（草稿），在比赛库管理里检查后发布' : ''}`,
    )
    open.value = false
    emit('done')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" title="采用这条通知" width="860px" top="4vh">
    <div v-loading="loading" class="body">
      <template v-if="detail">
        <div class="head">
          <a :href="detail.record.sourceUrl" target="_blank" rel="noopener noreferrer" class="title">
            {{ detail.record.title || detail.record.sourceUrl }}
          </a>
          <div class="muted">
            {{ detail.record.competitionName }} · {{ detail.record.sourceName }} · 抓取于
            {{ fmtDateTime(detail.record.fetchedAt) }}
          </div>
        </div>

        <el-alert v-if="evidence.length === 0" type="info" :closable="false" class="block">
          没从正文里找到报名时间、比赛时间和人数要求，可能写在附件里。请打开原文核对后手动填写。
        </el-alert>
        <div v-else class="block">
          <div class="section">从通知里找到的依据（已填进下面的表单，请核对）</div>
          <div v-for="[key, sentence] in evidence" :key="key" class="evidence">
            <el-tag size="small" effect="plain">{{ EVIDENCE_LABEL[key] ?? key }}</el-tag>
            <span>{{ sentence }}</span>
          </div>
        </div>

        <el-collapse class="block">
          <el-collapse-item title="通知正文" name="raw">
            <pre class="raw">{{ detail.rawText }}</pre>
          </el-collapse-item>
        </el-collapse>

        <div class="section">写到哪一届</div>
        <div class="target">
          <el-radio-group v-model="mode">
            <el-radio value="CREATE">新建一届</el-radio>
            <el-radio value="UPDATE" :disabled="editions.length === 0">更新已有的一届</el-radio>
          </el-radio-group>
          <el-select v-if="mode === 'UPDATE'" v-model="editionId" placeholder="选择届次" class="edition">
            <el-option v-for="e in editions" :key="e.id" :label="`${e.title}（${EDITION_STATUS[e.status].label}）`" :value="e.id" />
          </el-select>
        </div>

        <el-form label-width="96px" class="form" @submit.prevent>
          <div class="grid">
            <el-form-item label="年份">
              <el-input-number v-model="form.year" :min="2000" :max="2100" controls-position="right" />
            </el-form-item>
            <el-form-item label="届次名称">
              <el-input v-model="form.title" maxlength="64" placeholder="如 2027 年" />
            </el-form-item>
            <el-form-item label="每队人数">
              <el-input-number v-model="form.teamMin" :min="1" :max="30" controls-position="right" class="num" />
              <span class="to">到</span>
              <el-input-number v-model="form.teamMax" :min="1" :max="30" controls-position="right" class="num" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="form.status">
                <el-option v-for="(s, key) in EDITION_STATUS" :key="key" :label="s.label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item label="报名开始">
              <el-date-picker
                v-model="form.registrationStart"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                format="YYYY-MM-DD HH:mm"
                placeholder="可不填"
              />
            </el-form-item>
            <el-form-item label="报名截止">
              <el-date-picker
                v-model="form.registrationEnd"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                format="YYYY-MM-DD HH:mm"
                placeholder="可不填"
              />
            </el-form-item>
          </div>
          <el-form-item label="组队要求">
            <el-checkbox v-model="form.sameSchoolRequired">必须同校</el-checkbox>
            <el-checkbox v-model="form.advisorRequired">必须有指导老师</el-checkbox>
            <el-checkbox v-model="form.englishRequired">要求英语</el-checkbox>
          </el-form-item>
          <el-form-item label="通知链接">
            <el-input v-model="form.noticeUrl" maxlength="255" />
          </el-form-item>
        </el-form>

        <div class="section">
          官方时间节点
          <span class="muted">（蓝色的是从这条通知里抽出来的；提交后整体替换这一届的节点）</span>
        </div>
        <div v-loading="nodesLoading">
          <NodeListEditor v-model="rows" />
        </div>
      </template>
    </div>
    <template #footer>
      <el-button class="add" :disabled="!detail" @click="rows.push(emptyRow())">+ 添加节点</el-button>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!detail || nodesLoading" @click="submit">
        {{ mode === 'UPDATE' ? '更新这一届' : '生成届次' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.body {
  min-height: 120px;
  max-height: 72vh;
  overflow-y: auto;
  padding-right: 4px;
}

.head {
  margin-bottom: 12px;
}

.title {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-color-primary);
  text-decoration: none;
}

.title:hover {
  text-decoration: underline;
}

.muted {
  font-size: 12px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
}

.block {
  margin-bottom: 14px;
}

.section {
  margin: 4px 0 8px;
  font-size: 13px;
  font-weight: 500;
}

.evidence {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.6;
}

.evidence .el-tag {
  flex-shrink: 0;
}

.raw {
  max-height: 260px;
  margin: 0;
  overflow-y: auto;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.target {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.edition {
  width: 300px;
  max-width: 100%;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  column-gap: 12px;
}

.num {
  width: 100px;
}

.to {
  margin: 0 8px;
  color: var(--el-text-color-secondary);
}

.add {
  float: left;
}
</style>
