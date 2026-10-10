<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Link, Plus } from '@element-plus/icons-vue'
import { competitionOptions } from '@/api/competition'
import { addExperience, deleteExperience } from '@/api/profile'
import type { Experience, Option } from '@/types/api'
import { AWARD_SUGGESTIONS } from '@/utils/labels'
import { showError } from '@/utils/feedback'

const props = defineProps<{ initial: Experience[] }>()

const list = ref<Experience[]>([...props.initial])
const dialogOpen = ref(false)
const saving = ref(false)
const competitions = ref<Option[]>([])
const formRef = ref<FormInstance>()

const form = reactive({ title: '', year: new Date().getFullYear(), award: '', proofUrl: '' })

const rules: FormRules<typeof form> = {
  title: [{ required: true, message: '请选择或填写比赛名称', trigger: 'blur' }],
  year: [{ required: true, message: '请填写年份', trigger: 'change' }],
}

async function open() {
  form.title = ''
  form.year = new Date().getFullYear()
  form.award = ''
  form.proofUrl = ''
  dialogOpen.value = true
  if (competitions.value.length === 0) {
    try {
      competitions.value = await competitionOptions()
    } catch (e) {
      showError(e)
    }
  }
}

/** 比赛名称的联想：从比赛库里找；不在库里的也可以直接手写 */
function suggestCompetitions(query: string, cb: (items: { value: string }[]) => void) {
  const q = query.trim()
  cb(competitions.value.filter((c) => !q || c.name.includes(q)).map((c) => ({ value: c.name })))
}

function suggestAwards(query: string, cb: (items: { value: string }[]) => void) {
  cb(AWARD_SUGGESTIONS.filter((a) => a.includes(query)).map((value) => ({ value })))
}

async function submit() {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const title = form.title.trim()
  // 名称和比赛库里的完全一致，就关联上，推荐时能用到
  const match = competitions.value.find((c) => c.name === title)
  saving.value = true
  try {
    const created = await addExperience({
      competitionId: match?.id ?? null,
      title,
      year: form.year,
      award: form.award,
      proofUrl: form.proofUrl,
    })
    list.value.unshift(created)
    list.value.sort((a, b) => b.year - a.year)
    dialogOpen.value = false
    ElMessage.success('履历已添加')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

async function remove(e: Experience) {
  try {
    await ElMessageBox.confirm(`删除「${e.year} ${e.title}」这条履历？`, '删除履历', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteExperience(e.id)
    list.value = list.value.filter((x) => x.id !== e.id)
  } catch (err) {
    showError(err)
  }
}
</script>

<template>
  <div>
    <el-empty v-if="list.length === 0" :image-size="60" description="还没有参赛履历，参加过的比赛可以补上" />
    <div v-for="e in list" :key="e.id" class="row">
      <span class="year">{{ e.year }}</span>
      <span class="title">{{ e.title }}</span>
      <el-tag v-if="e.award" size="small" effect="plain">{{ e.award }}</el-tag>
      <el-tag v-if="e.source === 'PLATFORM'" size="small" type="success" effect="plain">平台组队</el-tag>
      <el-link v-if="e.proofUrl" :href="e.proofUrl" target="_blank" :icon="Link" class="proof">证明</el-link>
      <span class="spacer" />
      <el-button v-if="e.source === 'MANUAL'" text type="danger" size="small" @click="remove(e)">删除</el-button>
    </div>
    <el-button :icon="Plus" class="add" @click="open">添加履历</el-button>

    <el-dialog v-model="dialogOpen" title="添加参赛履历" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="比赛" prop="title">
          <el-autocomplete
            v-model="form.title"
            :fetch-suggestions="suggestCompetitions"
            placeholder="输入关键字，从比赛库里选；库里没有的直接填"
            maxlength="100"
            class="full"
          />
        </el-form-item>
        <el-form-item label="年份" prop="year">
          <el-input-number v-model="form.year" :min="2000" :max="2100" controls-position="right" />
        </el-form-item>
        <el-form-item label="获奖情况">
          <el-autocomplete
            v-model="form.award"
            :fetch-suggestions="suggestAwards"
            placeholder="比如 省级二等奖、成功参赛"
            class="full"
          />
        </el-form-item>
        <el-form-item label="证明链接（选填）">
          <el-input v-model="form.proofUrl" placeholder="获奖名单、证书照片的网址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 14px;
}

.year {
  color: var(--el-text-color-secondary);
  width: 40px;
}

.proof {
  font-size: 13px;
}

.spacer {
  flex: 1;
}

.add {
  margin-top: 12px;
}

.full {
  width: 100%;
}
</style>
