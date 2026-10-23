<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  confirmRecognition,
  getRecognitions,
  saveMyRecognition,
  unconfirmRecognition,
  withdrawMyRecognition,
} from '@/api/competition'
import { useUserStore } from '@/stores/user'
import type { RecognitionBoard, RecognitionEntry } from '@/types/api'
import { RECOGNITION_STATUS, RECOGNITION_SUGGESTIONS } from '@/utils/labels'
import { isHttpUrlOrEmpty, safeUrl } from '@/utils/url'
import { showError } from '@/utils/feedback'

/** 比赛详情页的"加分认定"：本校同学标注、几位同学确认后显示，另外列出其他学校已经确认的 */
const props = defineProps<{ competitionId: number }>()
const user = useUserStore()

const board = ref<RecognitionBoard | null>(null)
const loading = ref(true)
const busy = ref<number | null>(null)

const school = computed(() => board.value?.mySchool ?? null)
const myEntry = computed(() => school.value?.entries.find((e) => e.mine) ?? null)
const myConfirmed = computed(() => school.value?.entries.find((e) => e.confirmedByMe) ?? null)

async function load() {
  loading.value = true
  try {
    board.value = await getRecognitions(props.competitionId)
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function lacking(e: RecognitionEntry): number {
  return Math.max((board.value?.confirmsNeeded ?? 0) - e.confirmCount, 0)
}

async function toggleConfirm(e: RecognitionEntry) {
  busy.value = e.id
  try {
    if (e.confirmedByMe) await unconfirmRecognition(e.id)
    else await confirmRecognition(e.id)
    await load()
  } catch (err) {
    showError(err)
  } finally {
    busy.value = null
  }
}

// ---------- 标注 / 修改 ----------

const dialogOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ gradeLabel: '', note: '', sourceUrl: '' })
const rules: FormRules<typeof form> = {
  gradeLabel: [{ required: true, whitespace: true, message: '请填写认定级别', trigger: 'blur' }],
  sourceUrl: [
    {
      validator: (_rule, value: string, cb) => (isHttpUrlOrEmpty(value) ? cb() : cb(new Error('请填 http:// 或 https:// 开头的网址'))),
      trigger: 'blur',
    },
  ],
}

/** 改了级别或链接，之前的确认会清零：提醒一下 */
const resetsConfirms = computed(() => {
  const e = myEntry.value
  return !!e && e.confirmCount > 0 && (form.gradeLabel.trim() !== e.gradeLabel || form.sourceUrl.trim() !== (e.sourceUrl ?? ''))
})

function openForm() {
  const e = myEntry.value
  Object.assign(form, { gradeLabel: e?.gradeLabel ?? '', note: e?.note ?? '', sourceUrl: e?.sourceUrl ?? '' })
  dialogOpen.value = true
}

function suggest(query: string, cb: (items: { value: string }[]) => void) {
  cb(RECOGNITION_SUGGESTIONS.filter((s) => s.includes(query.trim())).map((value) => ({ value })))
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    board.value = await saveMyRecognition(props.competitionId, { ...form })
    dialogOpen.value = false
    ElMessage.success('提交好了，等同校同学确认')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

async function withdraw() {
  try {
    await ElMessageBox.confirm('撤回后这条标注和同学们的确认都会删掉。', '撤回标注', {
      type: 'warning',
      confirmButtonText: '撤回',
    })
  } catch {
    return
  }
  try {
    board.value = await withdrawMyRecognition(props.competitionId)
    ElMessage.success('已撤回')
  } catch (e) {
    showError(e)
  }
}

watch(() => props.competitionId, load, { immediate: true })
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="block">
    <div class="head">
      <h3 class="title">加分认定 <span class="kicker">RECOGNITION</span></h3>
      <span v-if="board" class="hint">
        同校同学标注，另外 {{ board.confirmsNeeded }} 位同学确认后显示。仅供参考，以学校当年发布的文件为准。
      </span>
    </div>

    <template v-if="board">
      <section v-if="school" class="section">
        <div class="school">本校 · {{ school.schoolName }}</div>
        <p v-if="school.entries.length === 0" class="empty">还没有同学标注过本校怎么认定这个比赛。</p>
        <p v-else-if="school.shownId === null" class="empty">还没有确认够的说法，知道的话帮忙确认一下。</p>

        <div
          v-for="e in school.entries"
          :key="e.id"
          class="entry"
          :class="{ shown: e.id === school.shownId, rejected: e.status === 'REJECTED' }"
        >
          <div class="entry-head">
            <span class="grade">{{ e.gradeLabel }}</span>
            <el-tag v-if="e.id === school.shownId" size="small" type="success" effect="plain">当前显示</el-tag>
            <el-tag v-else size="small" :type="RECOGNITION_STATUS[e.status].type" effect="plain">
              {{ e.status === 'PENDING' ? `还差 ${lacking(e)} 人确认` : RECOGNITION_STATUS[e.status].label }}
            </el-tag>
            <span class="count">{{ e.confirmCount }} 位同学确认</span>
            <span v-if="e.mine" class="mine">你提交的</span>
            <a v-if="safeUrl(e.sourceUrl)" :href="safeUrl(e.sourceUrl)!" target="_blank" rel="noopener noreferrer" class="file">
              学校文件 ↗
            </a>
          </div>
          <p v-if="e.note" class="note">{{ e.note }}</p>
          <p v-if="e.mine && e.status === 'REJECTED'" class="note warn">管理员驳回了这条，原因见消息。改了级别或文件链接后会重新让同学确认。</p>
          <div v-if="school.canContribute" class="entry-actions">
            <template v-if="e.mine">
              <el-button size="small" text @click="openForm">修改</el-button>
              <el-button size="small" text type="danger" @click="withdraw">撤回</el-button>
            </template>
            <el-button
              v-else-if="!school.hasMine && e.status !== 'REJECTED'"
              size="small"
              :type="e.confirmedByMe ? 'default' : 'primary'"
              :plain="!e.confirmedByMe"
              :loading="busy === e.id"
              @click="toggleConfirm(e)"
            >
              {{ e.confirmedByMe ? '取消确认' : '我也确认' }}
            </el-button>
          </div>
        </div>

        <div v-if="school.canContribute && !school.hasMine" class="add">
          <el-button size="small" @click="openForm">
            {{ school.entries.length ? '我知道的不一样' : '标注本校的认定' }}
          </el-button>
        </div>
        <p v-else-if="!school.canContribute" class="hint">认证有效的同学才能标注和确认。</p>
      </section>
      <p v-else-if="user.isAdmin" class="hint section">
        各校同学的标注在「<router-link :to="{ name: 'admin-recognitions' }">管理后台 → 加分认定</router-link>」里审核。
      </p>
      <p v-else class="hint section">
        完成学校认证后，可以标注和确认本校的认定级别。
        <router-link :to="{ name: 'verify' }">去认证</router-link>
      </p>

      <section v-if="board.others.length" class="section">
        <div class="school">其他学校</div>
        <div v-for="o in board.others" :key="o.schoolId" class="other">
          <span class="other-school">{{ o.schoolName }}</span>
          <span class="other-grade">{{ o.gradeLabel }}</span>
          <span class="count">{{ o.confirmCount }} 位同学确认</span>
          <a v-if="safeUrl(o.sourceUrl)" :href="safeUrl(o.sourceUrl)!" target="_blank" rel="noopener noreferrer" class="file">
            学校文件 ↗
          </a>
          <span v-if="o.note" class="other-note">{{ o.note }}</span>
        </div>
      </section>
    </template>

    <el-dialog v-model="dialogOpen" :title="myEntry ? '修改我的标注' : '标注本校的认定'" width="min(480px, 94vw)" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="认定级别" prop="gradeLabel">
          <el-autocomplete
            v-model="form.gradeLabel"
            :fetch-suggestions="suggest"
            :maxlength="32"
            placeholder="按学校文件里的写法，比如 A 类、一类竞赛"
            class="full"
          />
        </el-form-item>
        <el-form-item label="说明（选填）" prop="note">
          <el-input
            v-model="form.note"
            type="textarea"
            :rows="2"
            :maxlength="255"
            show-word-limit
            placeholder="比如：国家级一等奖加 3 分，按 2025 版综测细则"
          />
        </el-form-item>
        <el-form-item label="学校文件链接（选填，有的话同学更好确认）" prop="sourceUrl">
          <el-input v-model="form.sourceUrl" placeholder="教务处或学院发布的文件网址" />
        </el-form-item>
      </el-form>
      <el-alert v-if="resetsConfirms" type="warning" :closable="false" show-icon class="tip">
        改了级别或文件链接，之前 {{ myEntry?.confirmCount }} 位同学的确认会清零，需要重新确认。
      </el-alert>
      <el-alert v-else-if="!myEntry && myConfirmed" type="info" :closable="false" show-icon class="tip">
        提交后，你对「{{ myConfirmed.gradeLabel }}」的确认会撤掉（每人只算一种说法）。
      </el-alert>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.block {
  border-radius: 2px;
  margin-bottom: 16px;
  min-height: 80px;
}

.head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 12px;
}

.title {
  font-size: 15px;
  font-weight: 500;
  margin: 0;
}

.kicker {
  margin-left: 4px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}

.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.section {
  margin-top: 14px;
}

.school {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.empty {
  margin: 0 0 10px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.entry {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 2px;
  margin-bottom: 8px;
}

.entry.shown {
  border-color: var(--el-color-success);
}

.entry.rejected {
  opacity: 0.7;
}

.entry-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
}

.grade {
  font-size: 16px;
  font-weight: 500;
}

.count,
.mine {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.mine {
  color: var(--el-color-warning);
}

.file {
  font-size: 13px;
}

.note {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}

.note.warn {
  color: var(--el-color-danger);
}

.entry-actions {
  margin-top: 6px;
}

.add {
  margin-top: 4px;
}

.other {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 14px;
}

.other:last-child {
  border-bottom: none;
}

.other-school {
  min-width: 8em;
}

.other-grade {
  font-weight: 500;
}

.other-note {
  flex-basis: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.full {
  width: 100%;
}

.tip {
  margin-top: 4px;
}
</style>
