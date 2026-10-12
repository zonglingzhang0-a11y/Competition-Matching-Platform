<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createEdition, updateEdition } from '@/api/admin'
import type { AdminEditionRow, EditionForm } from '@/types/api'
import { EDITION_STATUS } from '@/utils/labels'
import { showError } from '@/utils/feedback'

/** 新建或编辑一届比赛。edition 有值是编辑 */
const props = defineProps<{ competitionId: number | null; competitionName: string; edition: AdminEditionRow | null }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>({ required: true })

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<EditionForm>(blank())

const rules: FormRules<EditionForm> = {
  year: [{ required: true, message: '请填写年份', trigger: 'blur' }],
  title: [{ required: true, message: '请填写届次名称', trigger: 'blur' }],
}

function blank(): EditionForm {
  const year = new Date().getFullYear()
  return {
    year,
    title: `${year} 年`,
    teamMin: 1,
    teamMax: 3,
    sameSchoolRequired: true,
    advisorRequired: false,
    englishRequired: false,
    registrationStart: null,
    registrationEnd: null,
    noticeUrl: null,
    status: 'DRAFT',
  }
}

watch(open, (value) => {
  if (!value) return
  const e = props.edition
  Object.assign(form, blank())
  if (e) {
    Object.assign(form, {
      year: e.year,
      title: e.title,
      teamMin: e.teamMin,
      teamMax: e.teamMax,
      sameSchoolRequired: e.sameSchoolRequired,
      advisorRequired: e.advisorRequired,
      englishRequired: e.englishRequired,
      registrationStart: e.registrationStart,
      registrationEnd: e.registrationEnd,
      noticeUrl: e.noticeUrl,
      status: e.status,
    })
  }
  formRef.value?.clearValidate()
})

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const payload = { ...form, title: form.title.trim() }
    if (props.edition) await updateEdition(props.edition.id, payload)
    else if (props.competitionId !== null) await createEdition(props.competitionId, payload)
    ElMessage.success(props.edition ? '已保存' : '已新建，接着填官方时间节点')
    open.value = false
    emit('saved')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="(edition ? '编辑届次 · ' : '新增届次 · ') + competitionName" width="560px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent="save">
      <el-form-item label="年份" prop="year">
        <el-input-number v-model="form.year" :min="2000" :max="2100" controls-position="right" />
      </el-form-item>
      <el-form-item label="届次名称" prop="title">
        <el-input v-model="form.title" maxlength="64" placeholder="比如 2026 年" />
      </el-form-item>
      <el-form-item label="队伍人数">
        <el-input-number v-model="form.teamMin" :min="1" :max="30" controls-position="right" class="num" />
        <span class="to">到</span>
        <el-input-number v-model="form.teamMax" :min="form.teamMin" :max="30" controls-position="right" class="num" />
        <span class="to">人</span>
      </el-form-item>
      <el-form-item label="参赛要求">
        <el-checkbox v-model="form.sameSchoolRequired">队员必须同校</el-checkbox>
        <el-checkbox v-model="form.advisorRequired">需要指导老师</el-checkbox>
        <el-checkbox v-model="form.englishRequired">英文赛题</el-checkbox>
      </el-form-item>
      <el-form-item label="报名开始">
        <el-date-picker
          v-model="form.registrationStart"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          placeholder="还没公布可以先不填"
        />
      </el-form-item>
      <el-form-item label="报名截止">
        <el-date-picker
          v-model="form.registrationEnd"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          :default-time="new Date(2000, 0, 1, 23, 59, 59)"
          placeholder="截止前会提醒关注的人"
        />
      </el-form-item>
      <el-form-item label="通知链接">
        <el-input v-model="form.noticeUrl" maxlength="255" placeholder="官方通知的网址" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio v-for="(s, value) in EDITION_STATUS" :key="value" :value="value">{{ s.label }}</el-radio>
        </el-radio-group>
        <div class="tip">草稿和已归档的届次学生看不到。</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.num {
  width: 110px;
}

.to {
  margin: 0 8px;
  color: var(--el-text-color-secondary);
}

.tip {
  width: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
