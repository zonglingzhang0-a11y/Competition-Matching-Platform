<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import SurveyQuestionItem from '@/components/SurveyQuestionItem.vue'
import { getSurvey, myEditionAnswers, myGeneralAnswers, saveEditionAnswers, saveGeneralAnswers } from '@/api/survey'
import type { Answers, SurveyQuestion } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 问卷页。/survey 是通用部分（填一次），/survey/editions/:id 是某一届比赛的参赛意向。
 */
const route = useRoute()
const router = useRouter()

const isEdition = computed(() => route.name === 'survey-edition')
const editionId = computed(() => Number(route.params.id))

const all = ref<SurveyQuestion[]>([])
const answers = reactive<Answers>({})
const quick = ref(true)
const roles = ref<string[]>([])
const heading = ref('')
const loading = ref(true)
const saving = ref(false)
const savedBefore = ref(false)

const questions = computed(() =>
  all.value
    .filter((q) => q.part === (isEdition.value ? 'COMPETITION' : 'GENERAL'))
    .filter((q) => !quick.value || q.inQuick),
)

const answered = computed(() => questions.value.filter((q) => !isBlank(answers[q.code])).length)

function isBlank(v: unknown): boolean {
  return v === undefined || v === null || v === '' || v === false || (Array.isArray(v) && v.length === 0)
}

async function load() {
  loading.value = true
  try {
    const survey = await getSurvey()
    all.value = survey.questions
    Object.keys(answers).forEach((k) => delete answers[k])
    if (isEdition.value) {
      const mine = await myEditionAnswers(editionId.value)
      heading.value = `${mine.competitionName} · ${mine.editionTitle}`
      roles.value = mine.roles
      apply(mine.mine)
    } else {
      apply(await myGeneralAnswers())
    }
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function apply(mine: { answers: Answers; quick: boolean } | null) {
  savedBefore.value = !!mine
  quick.value = mine ? mine.quick : true
  if (mine) Object.assign(answers, mine.answers)
}

async function save() {
  saving.value = true
  try {
    // 只提交当前版本（快速版 / 完整版）显示的题，以及之前填过的其他题
    const payload: Answers = { ...answers }
    if (isEdition.value) await saveEditionAnswers(editionId.value, payload, quick.value)
    else await saveGeneralAnswers(payload, quick.value)
    ElMessage.success(quick.value ? '已保存。有空补全完整版，推荐会更准' : '已保存')
    savedBefore.value = true
    const redirect = route.query.redirect
    if (typeof redirect === 'string') router.push(redirect)
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

watch(() => route.fullPath, load, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="page">
    <el-card shadow="never" class="block">
      <h1 class="title">{{ isEdition ? '参赛意向' : '组队问卷' }}</h1>
      <p v-if="isEdition" class="sub">{{ heading }}</p>
      <p class="desc">
        <template v-if="isEdition">
          同一个人对不同比赛的目标和投入可能完全不同，所以每场比赛单独填。推荐队伍和队友时会用到这些答案。
        </template>
        <template v-else>
          作息、协作习惯、底线这些和具体比赛无关的，填一次就行。推荐队伍和队友时，会看你们在这些方面合不合得来。
        </template>
      </p>
      <div class="mode">
        <el-radio-group v-model="quick">
          <el-radio-button :value="true">快速版（约 1 分钟）</el-radio-button>
          <el-radio-button :value="false">完整版（约 {{ isEdition ? 3 : 6 }} 分钟）</el-radio-button>
        </el-radio-group>
        <span class="count">已答 {{ answered }}/{{ questions.length }}</span>
      </div>
    </el-card>

    <el-card shadow="never" class="block">
      <SurveyQuestionItem
        v-for="(q, i) in questions"
        :key="q.code"
        v-model="answers[q.code]"
        :question="q"
        :index="i + 1"
        :quick="quick"
        :roles="roles"
      />
      <div class="actions">
        <el-button type="primary" :loading="saving" @click="save">{{ savedBefore ? '保存修改' : '提交' }}</el-button>
        <router-link v-if="!isEdition && savedBefore" to="/competitions" class="next">去比赛库看看推荐</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
  margin: 0 auto;
  min-height: 200px;
}

.block {
  border-radius: 12px;
  margin-bottom: 16px;
}

.title {
  font-size: 22px;
  font-weight: 500;
  margin: 0;
}

.sub {
  margin: 4px 0 0;
  color: var(--el-text-color-regular);
}

.desc {
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.mode {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
}

.count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.next {
  font-size: 14px;
}
</style>
