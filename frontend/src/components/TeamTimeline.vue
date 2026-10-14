<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Flag, Plus } from '@element-plus/icons-vue'
import { createMilestone, deleteMilestone, getTimeline, setMilestoneDone, updateMilestone } from '@/api/calendar'
import MilestoneTasks from '@/components/MilestoneTasks.vue'
import { useUserStore } from '@/stores/user'
import type { CompetitionNode, MilestoneForm, MilestoneView, TeamTimeline } from '@/types/api'
import { isFuzzy, isPast, nodeTimeText, parse } from '@/utils/time'
import { showError } from '@/utils/feedback'

/**
 * 队伍页右侧的时间线：比赛官方节点 + 队伍自己的里程碑，中间一条"今天"分隔线。
 * 里程碑由队长增删改，队员都能勾完成；它们也会出现在每个队员的日历里。
 * 每个里程碑下可以拆分工待办，见 MilestoneTasks。
 */
const props = defineProps<{ teamId: number }>()
const user = useUserStore()

const data = ref<TeamTimeline | null>(null)
const dialogOpen = ref(false)
const editing = ref<MilestoneView | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<MilestoneForm>(blank())

const rules: FormRules<MilestoneForm> = {
  title: [{ required: true, message: '请填写节点名称', trigger: 'blur' }],
  startAt: [{ required: true, message: '请选择时间', trigger: 'change' }],
}

type Row =
  | { kind: 'official'; key: string; at: number; node: CompetitionNode }
  | { kind: 'milestone'; key: string; at: number; ms: MilestoneView }
  | { kind: 'today'; key: string; at: number }

const rows = computed<Row[]>(() => {
  if (!data.value) return []
  const list: Row[] = [
    ...data.value.official.map((node) => ({ kind: 'official' as const, key: `n${node.id}`, at: parse(node.startAt).getTime(), node })),
    ...data.value.milestones.map((ms) => ({ kind: 'milestone' as const, key: `m${ms.id}`, at: parse(ms.startAt).getTime(), ms })),
  ]
  list.push({ kind: 'today', key: 'today', at: Date.now() })
  return list.sort((a, b) => a.at - b.at)
})

function blank(): MilestoneForm {
  return { title: '', note: '', startAt: '', endAt: null, allDay: false, endMode: 'FIXED', endLatestAt: null }
}

async function load() {
  try {
    data.value = await getTimeline(props.teamId)
  } catch (e) {
    showError(e)
  }
}

function open(ms: MilestoneView | null) {
  editing.value = ms
  Object.assign(form, ms ? { ...ms, note: ms.note ?? '' } : blank())
  dialogOpen.value = true
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const payload = { ...form, title: form.title.trim() }
    if (editing.value) await updateMilestone(editing.value.id, payload)
    else await createMilestone(props.teamId, payload)
    ElMessage.success(editing.value ? '节点已修改' : '节点已添加，队员的日历里也有了')
    dialogOpen.value = false
    await load()
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

async function remove(ms: MilestoneView) {
  try {
    await ElMessageBox.confirm(`删除节点「${ms.title}」？`, '删除节点', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteMilestone(ms.id)
    await load()
  } catch (e) {
    showError(e)
  }
}

async function toggleDone(ms: MilestoneView, done: boolean) {
  try {
    await setMilestoneDone(ms.id, done)
    await load()
  } catch (e) {
    showError(e)
  }
}

watch(() => props.teamId, load, { immediate: true })
defineExpose({ load })
</script>

<template>
  <div v-if="data">
    <div class="head">
      <span class="title">时间线</span>
      <el-button v-if="data.canEdit" size="small" :icon="Plus" @click="open(null)">添加节点</el-button>
    </div>
    <div v-for="r in rows" :key="r.key">
      <div v-if="r.kind === 'today'" class="today"><span>今天</span></div>

      <div v-else-if="r.kind === 'official'" class="row" :class="{ past: isPast(r.node) }">
        <el-icon class="marker official" aria-hidden="true"><Flag /></el-icon>
        <div class="body">
          <div class="name">
            {{ r.node.title }}
            <el-tag v-if="isFuzzy(r.node)" size="small" type="warning" effect="plain">时间未定</el-tag>
          </div>
          <div class="time">{{ nodeTimeText(r.node) }} · 官方</div>
        </div>
      </div>

      <div v-else class="row" :class="{ done: r.ms.doneAt }">
        <el-checkbox
          :model-value="!!r.ms.doneAt"
          class="marker"
          :aria-label="`标记「${r.ms.title}」为已完成`"
          @update:model-value="(v) => toggleDone(r.ms, !!v)"
        />
        <div class="body">
          <div class="name">
            {{ r.ms.title }}
            <span v-if="r.ms.tasks.length" class="progress">
              任务 {{ r.ms.tasks.filter((t) => t.doneAt).length }}/{{ r.ms.tasks.length }}
            </span>
          </div>
          <div class="time">{{ nodeTimeText({ ...r.ms, startLatestAt: null }) }}</div>
          <div v-if="r.ms.note" class="note">{{ r.ms.note }}</div>
          <MilestoneTasks
            v-if="user.me"
            :milestone="r.ms"
            :members="data.members"
            :captain="data.canEdit"
            :me-id="user.me.id"
            @changed="load"
          />
        </div>
        <div v-if="data.canEdit" class="ops">
          <el-button text size="small" @click="open(r.ms)">改</el-button>
          <el-button text size="small" type="danger" @click="remove(r.ms)">删</el-button>
        </div>
      </div>
    </div>
    <p v-if="!data.member" class="hint">加入后能看到队伍自己的节点，它们也会出现在你的日历里。</p>
    <p v-else-if="data.milestones.length === 0" class="hint">
      {{ data.canEdit ? '加几个节点，比如"精读往届论文""定分工和模板"，队员的日历里会自动出现。' : '队长还没有添加队伍节点。' }}
    </p>

    <el-dialog v-model="dialogOpen" :title="editing ? '修改节点' : '添加节点'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="64px" @submit.prevent="save">
        <el-form-item label="名称" prop="title">
          <el-input v-model="form.title" maxlength="64" placeholder="比如 模拟赛复盘" />
        </el-form-item>
        <el-form-item label="时间" prop="startAt">
          <el-date-picker v-model="form.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" />
        </el-form-item>
        <el-form-item label="结束">
          <el-date-picker
            v-model="form.endAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="可不填"
            clearable
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.note" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.title {
  font-size: 14px;
  font-weight: 500;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 4px;
  border-radius: 6px;
}

.row:hover {
  background: var(--el-fill-color-light);
}

.marker {
  flex: none;
  width: 18px;
  margin-top: 2px;
  height: auto;
}

.official {
  color: var(--el-text-color-secondary);
  font-size: 16px;
}

.body {
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.progress {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.time,
.note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.past .name,
.done .name {
  color: var(--el-text-color-secondary);
}

.done .name {
  text-decoration: line-through;
}

.ops {
  flex: none;
}

.today {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-color-primary);
  margin: 4px 0;
}

.today::before,
.today::after {
  content: '';
  flex: 1;
  border-top: 1px solid var(--el-color-primary-light-5);
}

.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 8px 0 0;
}
</style>
