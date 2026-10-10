<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { TeamForm } from '@/types/api'
import { MEETING_MODES } from '@/utils/labels'

const props = defineProps<{
  title: string
  /** 编辑时传入现有信息，新建时不传 */
  initial?: TeamForm
  teamMin: number
  teamMax: number
  /** 提交函数，抛出错误时弹窗不关 */
  submit: (form: TeamForm) => Promise<void>
}>()

const open = defineModel<boolean>({ required: true })

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<TeamForm>({ name: '', intro: '', maxMembers: props.teamMax, meetingMode: 'MIXED' })

const rules: FormRules<TeamForm> = {
  name: [
    { required: true, message: '请填写队名', trigger: 'blur' },
    { max: 32, message: '队名最多 32 个字', trigger: 'blur' },
  ],
}

watch(open, (value) => {
  if (!value) return
  Object.assign(form, props.initial ?? { name: '', intro: '', maxMembers: props.teamMax, meetingMode: 'MIXED' })
})

async function onSubmit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await props.submit({ ...form, name: form.name.trim() })
    open.value = false
  } catch {
    // 错误提示由调用方负责
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="title" width="480px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="onSubmit">
      <el-form-item label="队名" prop="name">
        <el-input v-model="form.name" maxlength="32" placeholder="起个好记的名字" />
      </el-form-item>
      <el-form-item label="队伍人数上限">
        <el-input-number v-model="form.maxMembers" :min="teamMin" :max="teamMax" controls-position="right" />
        <span class="hint">这届比赛每队 {{ teamMin }}–{{ teamMax }} 人</span>
      </el-form-item>
      <el-form-item label="协作方式">
        <el-radio-group v-model="form.meetingMode">
          <el-radio v-for="m in MEETING_MODES" :key="m.value" :value="m.value">{{ m.label }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="队伍简介">
        <el-input
          v-model="form.intro"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="想做什么方向、目标是什么、对队友有什么期待"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
