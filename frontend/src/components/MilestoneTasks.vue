<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import { claimTask, createTask, deleteTask, releaseTask, setTaskDone, updateTask } from '@/api/calendar'
import type { MilestoneView, TaskView } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 时间线里一个里程碑下的分工待办。
 * 队员：加任务（分给自己或先空着）、认领没人负责的、勾自己的任务、放弃自己的任务；
 * 队长：另外能指派给任何队员、改名、删除。规则以后端 TaskService 为准。
 */
const props = defineProps<{
  milestone: MilestoneView
  members: { id: number; nickname: string }[]
  captain: boolean
  meId: number
}>()
const emit = defineEmits<{ changed: [] }>()

const showAdd = ref(false)
const adding = ref('')
/** 新任务分给谁：0 表示先不分配（下拉框的值不能是 null） */
const addTo = ref<number>(props.captain ? 0 : props.meId)
const saving = ref(false)

/** 新任务能分给谁：队长可以分给任何队员，其他人只能分给自己或先空着 */
const assignOptions = computed(() =>
  props.captain ? props.members : props.members.filter((m) => m.id === props.meId),
)

const nobody = (t: TaskView) => t.assigneeId === null || t.assigneeLeft
const mine = (t: TaskView) => t.assigneeId === props.meId && !t.assigneeLeft
const canTick = (t: TaskView) => props.captain || mine(t) || nobody(t)
/** "⋯"菜单里有没有能做的事：队长总有；队员只有认领、放弃，或者看一眼是谁负责 */
const hasActions = (t: TaskView) => props.captain || !t.doneAt || (!nobody(t) && !mine(t))

function owner(t: TaskView) {
  if (t.assigneeId === null) return '没人负责'
  if (t.assigneeLeft) return `${t.assigneeName ?? '原负责人'}已离队`
  return mine(t) ? '我' : (t.assigneeName ?? '')
}

async function run(action: () => Promise<unknown>, success?: string) {
  try {
    await action()
    if (success) ElMessage.success(success)
    emit('changed')
  } catch (e) {
    showError(e)
  }
}

async function add() {
  const title = adding.value.trim()
  if (!title || saving.value) return
  saving.value = true
  await run(() => createTask(props.milestone.id, { title, assigneeId: addTo.value || null }))
  adding.value = ''
  saving.value = false
}

function onCommand(t: TaskView, command: string) {
  if (command === 'claim') run(() => claimTask(t.id), '这个任务归你了')
  else if (command === 'release') run(() => releaseTask(t.id))
  else if (command === 'rename') rename(t)
  else if (command === 'delete') remove(t)
  else if (command.startsWith('assign:')) {
    const id = command.slice(7)
    run(() => updateTask(t.id, { title: t.title, assigneeId: id === '' ? null : Number(id) }))
  }
}

async function rename(t: TaskView) {
  let title: string
  try {
    ;({ value: title } = await ElMessageBox.prompt('任务名称', '改名', {
      inputValue: t.title,
      inputValidator: (v) => (!!v?.trim() && v.trim().length <= 64) || '1–64 个字',
    }))
  } catch {
    return
  }
  run(() => updateTask(t.id, { title: title.trim(), assigneeId: nobody(t) ? null : t.assigneeId }))
}

async function remove(t: TaskView) {
  try {
    await ElMessageBox.confirm(`删除任务「${t.title}」？`, '删除任务', { type: 'warning' })
  } catch {
    return
  }
  run(() => deleteTask(t.id))
}
</script>

<template>
  <div class="tasks">
    <div v-for="t in milestone.tasks" :key="t.id" class="task" :class="{ done: t.doneAt }">
      <el-tooltip :disabled="canTick(t)" :content="`这是${t.assigneeName}负责的任务`" placement="top">
        <el-checkbox
          :model-value="!!t.doneAt"
          :disabled="!canTick(t)"
          size="small"
          :aria-label="`标记任务「${t.title}」为已完成`"
          @update:model-value="(v) => run(() => setTaskDone(t.id, !!v))"
        />
      </el-tooltip>
      <span class="name">{{ t.title }}</span>
      <span class="owner" :class="{ me: mine(t), nobody: nobody(t) }">{{ owner(t) }}</span>
      <el-dropdown v-if="hasActions(t)" trigger="click" @command="(c: string) => onCommand(t, c)">
        <el-button text size="small" :icon="MoreFilled" class="more" :aria-label="`任务「${t.title}」的更多操作`" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="nobody(t) && !t.doneAt" command="claim">我来做</el-dropdown-item>
            <el-dropdown-item v-if="mine(t) && !t.doneAt" command="release">放弃这个任务</el-dropdown-item>
            <template v-if="captain">
              <el-dropdown-item
                v-for="m in members"
                :key="m.id"
                :command="`assign:${m.id}`"
                :disabled="t.assigneeId === m.id && !t.assigneeLeft"
                :divided="m === members[0]"
              >
                分给 {{ m.id === meId ? '我' : m.nickname }}
              </el-dropdown-item>
              <el-dropdown-item v-if="t.assigneeId !== null" command="assign:">先不分配</el-dropdown-item>
              <el-dropdown-item divided command="rename">改名</el-dropdown-item>
              <el-dropdown-item command="delete">删除</el-dropdown-item>
            </template>
            <el-dropdown-item v-if="!captain && !nobody(t) && !mine(t)" disabled>
              {{ t.assigneeName }} 负责，可以找队长调整
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <span v-else class="more-placeholder" />
    </div>

    <el-button v-if="!showAdd" text size="small" class="add-btn" @click="showAdd = true">+ 加任务</el-button>
    <div v-else class="add">
      <el-input
        v-model="adding"
        size="small"
        maxlength="64"
        placeholder="加一个任务，回车保存"
        autofocus
        :disabled="saving"
        @keyup.enter="add"
        @keyup.esc="showAdd = false"
      />
      <el-select v-model="addTo" size="small" class="to" aria-label="新任务分给谁">
        <el-option :value="0" label="先不分配" />
        <el-option v-for="m in assignOptions" :key="m.id" :value="m.id" :label="m.id === meId ? '分给我' : m.nickname" />
      </el-select>
    </div>
  </div>
</template>

<style scoped>
.tasks {
  margin-top: 6px;
}

.task {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 26px;
  font-size: 13px;
}

.name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.done .name {
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
}

.owner {
  flex: none;
  max-width: 7em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.owner.me {
  color: var(--el-color-primary);
}

.owner.nobody {
  color: var(--el-text-color-placeholder);
}

.more {
  flex: none;
  padding: 2px;
}

.more-placeholder {
  flex: none;
  width: 24px;
}

.add {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}

.add-btn {
  padding: 0;
  height: 22px;
  font-size: 12px;
}

.to {
  flex: none;
  width: 92px;
}
</style>
