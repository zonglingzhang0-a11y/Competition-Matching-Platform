<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createEvent } from '@/api/calendar'
import type { EventForm } from '@/types/api'
import { parseQuick } from '@/utils/quickParse'
import { showError } from '@/utils/feedback'
import { toDateStr } from '@/utils/time'

/**
 * 一句话添加个人日程：边打字边显示认出来的日期、时间和重复，回车直接加；认得不对就点"细调"打开完整的表单。
 */
const props = defineProps<{
  /** 今天（2026-10-16），不传就用本机日期 */
  today?: string
}>()
const emit = defineEmits<{
  added: []
  /** 打开完整表单，带上认出来的内容 */
  adjust: [form: EventForm]
}>()

const EXAMPLES = ['明天下午3点到5点 队伍开会', '周五前交论文初稿', '每周六上午9点到12点 数模集训 到11月30日']

const text = ref('')
const saving = ref(false)
const focused = ref(false)

const draft = computed(() => (text.value.trim() ? parseQuick(text.value, props.today ?? toDateStr(new Date())) : null))
const problem = computed(() => {
  if (!draft.value) return ''
  if (!draft.value.title) return '还差个标题，比如「队伍开会」'
  if (draft.value.title.length > 64) return '标题最多 64 个字'
  return ''
})

function toForm(): EventForm | null {
  const d = draft.value
  if (!d) return null
  return {
    title: d.title.slice(0, 64),
    notes: '',
    color: null,
    startAt: d.startAt,
    endAt: d.endAt,
    allDay: d.allDay,
    repeatType: d.repeatType,
    repeatWeekdays: d.repeatWeekdays,
    repeatUntil: d.repeatUntil,
    startLatestAt: d.startLatestAt,
    endMode: d.endMode,
    endLatestAt: d.endLatestAt,
    // 时间说不准的不参与撞期检测
    busy: !d.startLatestAt && d.endMode === 'FIXED',
  }
}

async function add() {
  const form = toForm()
  if (!form || problem.value || saving.value) return
  saving.value = true
  try {
    await createEvent(form)
    const when = draft.value!.tokens.filter((t) => t.kind !== 'warn').map((t) => t.label).join(' ')
    ElMessage.success(`已添加「${form.title}」：${when}`)
    text.value = ''
    emit('added')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

function onEnter(e: KeyboardEvent | Event) {
  // 输入法选字时按的回车不算
  if ((e as KeyboardEvent).isComposing) return
  add()
}

/** 内容交给完整表单，输入框清空，免得改完保存后再按回车又加一条 */
function adjust() {
  const form = toForm()
  if (!form) return
  emit('adjust', form)
  text.value = ''
}
</script>

<template>
  <div class="quick">
    <div class="row">
      <el-input
        v-model="text"
        class="input"
        maxlength="120"
        clearable
        placeholder="一句话添加日程，比如：周三晚上7点到9点 队伍开会"
        aria-label="一句话添加日程"
        @focus="focused = true"
        @blur="focused = false"
        @keydown.enter="onEnter"
      />
      <el-button type="primary" :disabled="!draft || !!problem" :loading="saving" @click="add">添加</el-button>
      <el-button :disabled="!draft" @click="adjust">细调</el-button>
    </div>

    <div v-if="draft" class="preview" aria-live="polite">
      <span v-for="(t, i) in draft.tokens" :key="i" class="token" :class="`is-${t.kind}`">{{ t.label }}</span>
      <span v-if="problem" class="problem">{{ problem }}</span>
      <template v-else>
        <span class="title">{{ draft.title }}</span>
        <span class="enter">回车添加</span>
      </template>
    </div>
    <div v-else-if="focused" class="examples">
      <span class="kicker">试试</span>
      <!-- mousedown 先于输入框失焦，用它填例子 -->
      <button v-for="ex in EXAMPLES" :key="ex" type="button" class="example" @mousedown.prevent="text = ex">{{ ex }}</button>
    </div>
  </div>
</template>

<style scoped>
.quick {
  margin-bottom: 10px;
}

.row {
  display: flex;
  gap: 8px;
}

.row .el-button + .el-button {
  margin-left: 0;
}

.input {
  flex: 1;
  min-width: 0;
}

.preview,
.examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  font-size: 12px;
}

.token {
  padding: 1px 6px;
  border: 1px solid var(--jd-line);
  background: var(--jd-field);
  color: var(--jd-ink-2);
}

.token.is-time {
  border-color: var(--jd-ink-2);
}

.token.is-repeat {
  border-color: var(--jd-accent);
  color: var(--jd-accent);
}

.token.is-warn {
  border-color: var(--jd-red);
  color: var(--jd-red);
}

.title {
  font-weight: 700;
  color: var(--jd-ink);
}

.problem {
  color: var(--jd-red);
}

.enter {
  margin-left: auto;
  color: var(--jd-placeholder);
}

.examples .kicker {
  margin-right: 2px;
}

.example {
  padding: 1px 6px;
  border: 1px dashed var(--jd-line-strong);
  background: transparent;
  color: var(--jd-ink-2);
  font: inherit;
  cursor: pointer;
}

.example:hover {
  border-color: var(--jd-accent);
  color: var(--jd-accent);
}
</style>
