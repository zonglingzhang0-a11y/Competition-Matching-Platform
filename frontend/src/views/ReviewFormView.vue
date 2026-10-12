<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import UserCard from '@/components/UserCard.vue'
import { reviewForm, submitReview } from '@/api/review'
import type { RoundForm } from '@/types/api'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

/**
 * 填互评：每位队友 6 项行为，按频率选"从未 / 偶尔 / 有时 / 经常"。自评选填。
 */
const route = useRoute()
const router = useRouter()

const form = ref<RoundForm | null>(null)
const loading = ref(true)
const saving = ref(false)
/** 被评人 id → 6 项的选择，没选是 null */
const answers = ref<Record<number, (number | null)[]>>({})
const includeSelf = ref(false)

const teammates = computed(() => form.value?.targets.filter((t) => !t.self) ?? [])
const self = computed(() => form.value?.targets.find((t) => t.self))

async function load() {
  loading.value = true
  try {
    const f = await reviewForm(Number(route.params.id))
    form.value = f
    answers.value = Object.fromEntries(
      f.targets.map((t) => [t.user.id, t.answers ? [...t.answers] : f.items.map(() => null)]),
    )
    includeSelf.value = !!self.value?.answers
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function filled(userId: number) {
  return (answers.value[userId] ?? []).every((v) => v !== null)
}

function setAnswer(userId: number, index: number, value: number) {
  const row = answers.value[userId]
  if (row) row[index] = value
}

async function submit() {
  const f = form.value
  if (!f) return
  const missing = teammates.value.filter((t) => !filled(t.user.id)).map((t) => t.user.nickname)
  if (missing.length) {
    ElMessage.warning(`还有没选完的：${missing.join('、')}`)
    return
  }
  const me = self.value
  if (includeSelf.value && me && !filled(me.user.id)) {
    ElMessage.warning('自评还没选完，不想做可以关掉')
    return
  }
  const targets = includeSelf.value ? f.targets : teammates.value
  saving.value = true
  try {
    const result = await submitReview(
      f.round.id,
      targets.map((t) => ({ rateeId: t.user.id, answers: answers.value[t.user.id] as number[] })),
    )
    if (result.published) {
      ElMessage.success('所有人都交了，结果已经揭晓')
      router.push({ name: 'reviews' })
    } else {
      ElMessage.success(`已提交，${result.raters}/${result.participants} 人已交。截止前还可以回来修改`)
      router.push({ name: 'team', params: { id: f.round.teamId } })
    }
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

watch(() => route.params.id, load, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="page">
    <template v-if="form">
      <el-button text :icon="ArrowLeft" class="back" @click="router.push({ name: 'team', params: { id: form.round.teamId } })">
        {{ form.round.teamName }}
      </el-button>
      <h1 class="title">赛后互评</h1>
      <p class="meta">
        {{ form.round.competitionName }} {{ form.round.editionTitle }} · {{ form.round.raters }}/{{ form.round.participants }} 人已交 ·
        {{ form.editable ? `${fmtDateTime(form.round.closesAt)} 截止` : '已结束' }}
      </p>

      <el-alert type="info" :closable="false" show-icon class="block">
        <template #title>按实际发生的频率选，不是打印象分</template>
        所有人交齐或到截止时间后统一揭晓。别人只能看到汇总后的信用分，看不到你具体给谁打了什么；
        和其他人差得太远的评分会被降低权重。
      </el-alert>

      <el-card v-for="t in teammates" :key="t.user.id" shadow="never" class="block">
        <template #header>
          <div class="card-head">
            <UserCard :user="t.user" :max-skills="3" />
            <el-tag v-if="filled(t.user.id)" type="success" size="small">已选完</el-tag>
          </div>
        </template>
        <div v-for="(item, i) in form.items" :key="i" class="item">
          <span class="label">{{ item }}</span>
          <el-radio-group
            :model-value="answers[t.user.id]?.[i] ?? undefined"
            size="small"
            :disabled="!form.editable"
            @update:model-value="(v) => setAnswer(t.user.id, i, Number(v))"
          >
            <el-radio-button v-for="(f, value) in form.frequencies" :key="value" :value="value">{{ f }}</el-radio-button>
          </el-radio-group>
        </div>
      </el-card>

      <el-card v-if="self" shadow="never" class="block">
        <template #header>
          <div class="card-head">
            <span>自评（选填）</span>
            <el-switch v-model="includeSelf" :disabled="!form.editable" />
          </div>
        </template>
        <p class="hint">用同样的问题评一下自己。积累够数据后，你能看到自评和队友评价的差别，只有你自己看得到。</p>
        <template v-if="includeSelf">
          <div v-for="(item, i) in form.items" :key="i" class="item">
            <span class="label">{{ item }}</span>
            <el-radio-group
              :model-value="answers[self.user.id]?.[i] ?? undefined"
              size="small"
              :disabled="!form.editable"
              @update:model-value="(v) => setAnswer(self!.user.id, i, Number(v))"
            >
              <el-radio-button v-for="(f, value) in form.frequencies" :key="value" :value="value">{{ f }}</el-radio-button>
            </el-radio-group>
          </div>
        </template>
      </el-card>

      <div v-if="form.editable" class="submit">
        <el-button type="primary" :loading="saving" @click="submit">
          {{ form.round.submitted ? '保存修改' : '提交互评' }}
        </el-button>
      </div>
      <el-alert v-else type="warning" :closable="false" title="这轮互评已经结束，不能再修改" class="block" />
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: 760px;
  margin: 0 auto;
  min-height: 200px;
}

.back {
  margin-bottom: 8px;
}

.title {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
}

.meta {
  margin: 6px 0 16px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.block {
  border-radius: 12px;
  margin-bottom: 16px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.item:last-child {
  border-bottom: none;
}

.label {
  font-size: 14px;
}

.hint {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.submit {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 24px;
}
</style>
