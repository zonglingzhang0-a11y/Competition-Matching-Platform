<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { skillOptions } from '@/api/profile'
import type { NeedForm, NeedView, SkillOption } from '@/types/api'
import { SKILL_LEVELS } from '@/utils/labels'
import { showError } from '@/utils/feedback'

const props = defineProps<{
  /** 编辑时传入现有需求，新建时不传 */
  need?: NeedView | null
  /** 这个比赛常见的分工，用来做角色名的联想 */
  roles: string[]
  submit: (form: NeedForm) => Promise<void>
}>()

const open = defineModel<boolean>({ required: true })

const formRef = ref<FormInstance>()
const saving = ref(false)
const options = ref<SkillOption[]>([])
const picking = ref<number>()
const form = reactive<NeedForm>({ roleName: '', headcount: 1, note: '', skills: [] })

const rules: FormRules<NeedForm> = {
  roleName: [{ required: true, message: '请填写缺的角色', trigger: 'blur' }],
}

const nameOf = (id: number) => options.value.find((o) => o.id === id)?.name ?? ''
const available = computed(() => {
  const chosen = new Set(form.skills.map((s) => s.skillId))
  return options.value.filter((o) => !chosen.has(o.id))
})

/** 技能字典按类别分组，70 多项技能也能快速找到 */
const groups = computed(() => {
  const map = new Map<string, SkillOption[]>()
  for (const o of available.value) {
    const list = map.get(o.category) ?? []
    list.push(o)
    map.set(o.category, list)
  }
  return [...map.entries()].map(([category, items]) => ({ category, items }))
})

watch(open, async (value) => {
  if (!value) return
  const n = props.need
  Object.assign(form, {
    roleName: n?.roleName ?? '',
    headcount: n?.headcount ?? 1,
    note: n?.note ?? '',
    skills: n?.skills.map((s) => ({ skillId: s.skillId, minLevel: s.minLevel })) ?? [],
  })
  if (options.value.length === 0) {
    try {
      options.value = await skillOptions()
    } catch (e) {
      showError(e)
    }
  }
})

function suggestRoles(query: string, cb: (items: { value: string }[]) => void) {
  cb(props.roles.filter((r) => r.includes(query.trim())).map((value) => ({ value })))
}

function addSkill(id: number | undefined) {
  if (id === undefined) return
  form.skills.push({ skillId: id, minLevel: 2 })
  picking.value = undefined
}

async function onSubmit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await props.submit({ ...form, roleName: form.roleName.trim() })
    open.value = false
  } catch {
    // 错误提示由调用方负责
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="need ? '修改招募需求' : '添加招募需求'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="onSubmit">
      <el-form-item label="缺什么角色" prop="roleName">
        <el-autocomplete
          v-model="form.roleName"
          :fetch-suggestions="suggestRoles"
          maxlength="32"
          placeholder="比如 前端、论文写作与排版"
          class="full"
        />
      </el-form-item>
      <el-form-item label="招几个人">
        <el-input-number v-model="form.headcount" :min="need?.filled || 1" :max="10" controls-position="right" />
        <span v-if="need?.filled" class="hint">已经招到 {{ need.filled }} 人</span>
      </el-form-item>
      <el-form-item label="技能要求">
        <div class="skills">
          <div v-for="(s, i) in form.skills" :key="s.skillId" class="skill-row">
            <span class="skill-name">{{ nameOf(s.skillId) }}</span>
            <span class="at-least">至少</span>
            <el-radio-group v-model="s.minLevel" size="small">
              <el-radio-button v-for="l in SKILL_LEVELS" :key="l.value" :value="l.value">{{ l.label }}</el-radio-button>
            </el-radio-group>
            <el-button text :icon="Close" aria-label="移除这项技能要求" @click="form.skills.splice(i, 1)" />
          </div>
          <el-select
            v-model="picking"
            filterable
            default-first-option
            placeholder="添加技能要求（按类别分组）"
            :disabled="form.skills.length >= 10"
            class="picker"
            @change="addSkill"
          >
            <el-option-group v-for="g in groups" :key="g.category" :label="g.category">
              <el-option v-for="o in g.items" :key="o.id" :label="o.name" :value="o.id" />
            </el-option-group>
          </el-select>
          <div class="hint block">
            最多 10 项。推荐队友时，会拿这里的技能去和每个人的档案比，看谁能补上这个缺口；队伍页的需求雷达也按这六类能力画。
          </div>
        </div>
      </el-form-item>
      <el-form-item label="补充说明">
        <el-input v-model="form.note" maxlength="255" placeholder="比如 最好做过可视化、能周末线下" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full {
  width: 100%;
}

.skills {
  width: 100%;
}

.skill-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
}

.skill-name {
  min-width: 120px;
  font-size: 14px;
}

.at-least {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.picker {
  width: 240px;
  margin-top: 6px;
}

.hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.hint.block {
  margin: 8px 0 0;
  line-height: 1.5;
}
</style>
