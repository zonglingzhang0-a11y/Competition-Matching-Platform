<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createEvent, deleteEvent, getEvent, skipOccurrence, updateEvent } from '@/api/calendar'
import type { EventForm, RepeatType } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 个人日程的新建和编辑。eventId 有值是编辑，没有是新建（可以用 preset 带上日历里选中的时间，或者一句话添加认出来的内容）。
 */
const props = defineProps<{
  eventId: number | null
  /** 重复日程被点中的那一天，"只删这一次"用 */
  occurrenceDate?: string | null
  preset?: Partial<EventForm> | null
}>()

const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>({ required: true })

const REPEATS: { value: RepeatType; label: string }[] = [
  { value: 'NONE', label: '不重复' },
  { value: 'DAILY', label: '每天' },
  { value: 'WEEKDAYS', label: '工作日' },
  { value: 'WEEKLY', label: '每周几' },
]
const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']

const formRef = ref<FormInstance>()
const saving = ref(false)
const loading = ref(false)
const form = reactive<EventForm>(blank())

const rules: FormRules<EventForm> = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  startAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
}

const isEdit = computed(() => props.eventId !== null)
const repeating = computed(() => form.repeatType !== 'NONE')

function blank(): EventForm {
  return {
    title: '',
    notes: '',
    color: null,
    startAt: '',
    endAt: null,
    allDay: false,
    repeatType: 'NONE',
    repeatWeekdays: [],
    repeatUntil: null,
    busy: true,
  }
}

watch(open, async (value) => {
  if (!value) return
  Object.assign(form, blank())
  if (props.eventId !== null) {
    loading.value = true
    try {
      const e = await getEvent(props.eventId)
      Object.assign(form, { ...e, notes: e.notes ?? '' })
    } catch (err) {
      showError(err)
      open.value = false
    } finally {
      loading.value = false
    }
  } else if (props.preset) {
    Object.assign(form, props.preset)
  }
})

/** 全天日程只选日期，存的时候补上 00:00 */
const dateType = computed(() => (form.allDay ? 'date' : 'datetime'))
const valueFormat = computed(() => (form.allDay ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss'))
const startValue = computed({
  get: () => (form.allDay ? form.startAt.slice(0, 10) : form.startAt),
  set: (v: string) => (form.startAt = v && form.allDay ? `${v}T00:00:00` : v),
})
const endValue = computed({
  get: () => (form.endAt ? (form.allDay ? form.endAt.slice(0, 10) : form.endAt) : ''),
  set: (v: string) => (form.endAt = v ? (form.allDay ? `${v}T00:00:00` : v) : null),
})

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const payload = { ...form, title: form.title.trim() }
    if (props.eventId !== null) await updateEvent(props.eventId, payload)
    else await createEvent(payload)
    ElMessage.success('日程已保存')
    open.value = false
    emit('saved')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

async function remove(onlyThisOne: boolean) {
  const id = props.eventId
  if (id === null) return
  try {
    await ElMessageBox.confirm(onlyThisOne ? '只删除这一天的这次日程？' : '删除这条日程？重复的话每一次都会删掉。', '删除日程', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    if (onlyThisOne && props.occurrenceDate) await skipOccurrence(id, props.occurrenceDate)
    else await deleteEvent(id)
    ElMessage.success('已删除')
    open.value = false
    emit('saved')
  } catch (e) {
    showError(e)
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="isEdit ? '编辑日程' : '新建日程'" width="520px">
    <el-form ref="formRef" v-loading="loading" :model="form" :rules="rules" label-width="72px" @submit.prevent="save">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="64" placeholder="比如 期中考试、社团换届" />
      </el-form-item>
      <el-form-item label="全天">
        <el-switch v-model="form.allDay" />
      </el-form-item>
      <el-form-item label="开始" prop="startAt">
        <el-date-picker
          v-model="startValue"
          :type="dateType"
          :value-format="valueFormat"
          :format="form.allDay ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm'"
          placeholder="开始时间"
        />
      </el-form-item>
      <el-form-item label="结束">
        <el-date-picker
          v-model="endValue"
          :type="dateType"
          :value-format="valueFormat"
          :format="form.allDay ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm'"
          placeholder="可不填"
          clearable
        />
      </el-form-item>
      <el-form-item label="重复">
        <el-select v-model="form.repeatType" class="short">
          <el-option v-for="r in REPEATS" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
        <el-date-picker
          v-if="repeating"
          v-model="form.repeatUntil"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="重复到哪天（可不填）"
          clearable
          class="until"
        />
      </el-form-item>
      <el-form-item v-if="form.repeatType === 'WEEKLY'" label="星期">
        <el-checkbox-group v-model="form.repeatWeekdays">
          <el-checkbox-button v-for="(d, i) in WEEKDAYS" :key="i" :value="i + 1">{{ d }}</el-checkbox-button>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="撞期检测">
        <el-switch v-model="form.busy" />
        <span class="hint">打开后，和比赛时间重叠时会提醒，推荐队伍时也会考虑</span>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.notes" type="textarea" :rows="2" maxlength="1000" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="footer">
        <div>
          <template v-if="isEdit">
            <el-button v-if="repeating && occurrenceDate" text type="danger" @click="remove(true)">只删这一次</el-button>
            <el-button text type="danger" @click="remove(false)">{{ repeating ? '删除全部' : '删除' }}</el-button>
          </template>
        </div>
        <div>
          <el-button @click="open = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.short {
  width: 120px;
}

.until {
  margin-left: 8px;
  width: 180px;
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.footer {
  display: flex;
  justify-content: space-between;
}
</style>
