<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createCompetition, updateCompetition } from '@/api/admin'
import type { AdminCompetitionRow, CompetitionForm, RoleTemplate } from '@/types/api'
import { CATEGORIES, LEVEL_LABEL, PRESET_LABEL } from '@/utils/labels'
import { showError } from '@/utils/feedback'

/** 新建或编辑一个比赛。row 有值是编辑 */
const props = defineProps<{ row: AdminCompetitionRow | null; templates: RoleTemplate[] }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>({ required: true })

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<CompetitionForm>(blank())

const rules: FormRules<CompetitionForm> = {
  name: [{ required: true, message: '请填写比赛名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择学科类别', trigger: 'change' }],
  level: [{ required: true, message: '请选择级别', trigger: 'change' }],
}

const PRESET_TIPS: Record<CompetitionForm['matchPreset'], string> = {
  DEFAULT: '各维度均衡',
  SHORT_INTENSIVE: '比赛就几天（如数模国赛），更看重时间和节奏',
  LONG_TERM: '要做几个月（如互联网+），更看重目标和投入',
}

const roles = computed(() => props.templates.find((t) => t.id === form.roleTemplateId)?.roles ?? [])

function blank(): CompetitionForm {
  return {
    name: '',
    shortName: null,
    organizer: null,
    category: '',
    level: 'NATIONAL',
    inCaheList: true,
    officialUrl: null,
    description: null,
    roleTemplateId: null,
    matchPreset: 'DEFAULT',
  }
}

watch(open, (value) => {
  if (!value) return
  const r = props.row
  Object.assign(form, r ? { ...blank(), ...pick(r) } : blank())
  formRef.value?.clearValidate()
})

function pick(r: AdminCompetitionRow): CompetitionForm {
  const { name, shortName, organizer, category, level, inCaheList, officialUrl, description, roleTemplateId, matchPreset } = r
  return { name, shortName, organizer, category, level, inCaheList, officialUrl, description, roleTemplateId, matchPreset }
}

async function save() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    const payload = { ...form, name: form.name.trim() }
    if (props.row) await updateCompetition(props.row.id, payload)
    else await createCompetition(payload)
    ElMessage.success(props.row ? '已保存' : '已新建，接着给它加一届吧')
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
  <el-dialog v-model="open" :title="row ? '编辑比赛' : '新建比赛'" width="600px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" @submit.prevent="save">
      <el-form-item label="比赛名称" prop="name">
        <el-input v-model="form.name" maxlength="100" placeholder="全称，比如 全国大学生数学建模竞赛" />
      </el-form-item>
      <el-form-item label="简称">
        <el-input v-model="form.shortName" maxlength="32" placeholder="日历里显示用，比如 国赛" />
      </el-form-item>
      <el-form-item label="主办单位">
        <el-input v-model="form.organizer" maxlength="200" />
      </el-form-item>
      <el-form-item label="类别和级别" required>
        <div class="row">
          <el-form-item prop="category" class="inline">
            <el-select v-model="form.category" placeholder="学科类别">
              <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item prop="level" class="inline">
            <el-select v-model="form.level">
              <el-option v-for="(label, value) in LEVEL_LABEL" :key="value" :label="label" :value="value" />
            </el-select>
          </el-form-item>
        </div>
      </el-form-item>
      <el-form-item label="竞赛目录">
        <el-checkbox v-model="form.inCaheList">在中国高等教育学会的竞赛目录里</el-checkbox>
      </el-form-item>
      <el-form-item label="官网">
        <el-input v-model="form.officialUrl" maxlength="255" placeholder="https://" />
      </el-form-item>
      <el-form-item label="简介">
        <el-input v-model="form.description" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item label="分工模板">
        <el-select v-model="form.roleTemplateId" clearable placeholder="不设分工" class="full">
          <el-option v-for="t in templates" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <div v-if="roles.length" class="tip">队内角色：{{ roles.join('、') }}（问卷 P4 和招募需求里用）</div>
      </el-form-item>
      <el-form-item label="匹配权重">
        <el-radio-group v-model="form.matchPreset">
          <el-radio v-for="(label, value) in PRESET_LABEL" :key="value" :value="value">{{ label }}</el-radio>
        </el-radio-group>
        <div class="tip">{{ PRESET_TIPS[form.matchPreset] }}</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.row {
  display: flex;
  gap: 8px;
}

.inline {
  margin-bottom: 0;
}

.full {
  width: 100%;
}

.tip {
  width: 100%;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
