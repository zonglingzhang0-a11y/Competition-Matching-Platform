<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { analyzeRecruit, getRecruitAdvice } from '@/api/ai'
import type { AiTeamAdvice } from '@/types/api'
import { showError } from '@/utils/feedback'
import { fmtDateTime } from '@/utils/time'

/**
 * AI 招募建议（只有队长看得到）：把队员的技能、雷达、问卷特点和还开着的需求发给模型，
 * 给出队伍最缺哪类能力、该找什么样的人、组队前该问什么。
 * 需求里的技能差距是后端本地算好的，模型只在文字里解释，不会自己编数字。
 */
const props = defineProps<{ teamId: number }>()

const advice = ref<AiTeamAdvice | null>(null)
const loading = ref(true)
const analyzing = ref(false)

const body = computed(() => advice.value?.body)
const sourceText = computed(() =>
  advice.value?.source === 'LLM' ? `AI 模型 · ${advice.value.model ?? '默认'}` : '本地规则',
)
const levelText = ['没填', '入门', '熟练', '精通']

function level(n: number) {
  return levelText[n] ?? '没填'
}

async function load() {
  loading.value = true
  try {
    advice.value = await getRecruitAdvice(props.teamId)
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

async function run() {
  analyzing.value = true
  try {
    advice.value = await analyzeRecruit(props.teamId)
    ElMessage.success(advice.value.source === 'LLM' ? '招募建议已生成' : '这次用本地规则生成（没打开 AI 分析，或者模型没连上）')
  } catch (e) {
    showError(e)
  } finally {
    analyzing.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="block">
    <template #header>
      <div class="card-head">
        <span>AI 招募建议</span>
        <div class="head-right">
          <el-tag v-if="advice" size="small" :type="advice.source === 'LLM' ? 'success' : 'info'">{{ sourceText }}</el-tag>
          <el-button text size="small" :loading="analyzing" @click="run">
            {{ advice ? '重新分析' : '生成建议' }}
          </el-button>
        </div>
      </div>
    </template>

    <p class="hint">
      把队员的技能、能力雷达、问卷里合作相关的几道题和还开着的需求放一起看，给队长一份招募建议。只发这些，不发联系方式。
    </p>

    <el-alert
      v-if="advice?.source === 'RULE'"
      type="info"
      :closable="false"
      show-icon
      class="note"
      title="现在用的是本地规则"
      description="这一份是按雷达和需求算出来的：后端没打开 AI 分析（app.llm 和 app.ai），或者模型正忙、没连上。打开以后再点“重新分析”，会由模型来写。"
    />

    <div v-if="!advice" class="empty">
      <p>还没有分析过。点“生成建议”，看看这支队伍最缺什么、下一个该招什么样的人。</p>
      <el-button type="primary" :loading="analyzing" @click="run">生成建议</el-button>
    </div>

    <template v-else-if="body">
      <p class="summary">{{ body.summary }}</p>

      <section v-if="body.weakAxes.length" class="section">
        <h4>最缺的能力</h4>
        <div class="chips">
          <el-tag v-for="g in body.weakAxes" :key="g.key" size="small" effect="plain" :type="g.status === 'MISSING' ? 'danger' : 'warning'">
            {{ g.label }}：需求 {{ g.need }} / 队里最高 {{ g.best }}
            <span class="muted">{{ g.status === 'MISSING' ? '还没有人会' : '差一档' }}</span>
          </el-tag>
        </div>
      </section>

      <section v-if="body.skillGaps.length" class="section">
        <h4>需求技能 vs 队里现有水平</h4>
        <div class="chips">
          <el-tag v-for="g in body.skillGaps" :key="g.name" size="small" :type="g.status === 'COVERED' ? 'success' : g.status === 'PARTIAL' ? 'warning' : 'info'">
            {{ g.name }} ≥ {{ level(g.minLevel) }} · 队里最高 {{ g.bestLevel === 0 ? '没人会' : level(g.bestLevel) }}
          </el-tag>
        </div>
      </section>

      <section v-if="body.suggestions.length" class="section">
        <h4>建议</h4>
        <ul class="list">
          <li v-for="(s, i) in body.suggestions" :key="i">{{ s }}</li>
        </ul>
      </section>

      <section v-if="body.candidateProfile.length" class="section">
        <h4>该找什么样的人</h4>
        <ul class="list">
          <li v-for="(s, i) in body.candidateProfile" :key="i">{{ s }}</li>
        </ul>
      </section>

      <section v-if="body.questions.length" class="section">
        <h4>组队前 / 面试时问什么</h4>
        <ul class="list">
          <li v-for="(s, i) in body.questions" :key="i">{{ s }}</li>
        </ul>
      </section>

      <div class="footer">
        <span class="muted">{{ fmtDateTime(advice.createdAt) }} 分析 · 只是建议，不会自动处理申请</span>
      </div>
    </template>
  </el-card>
</template>

<style scoped>
.block {
  border-radius: 2px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hint {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.note {
  margin-bottom: 12px;
}

.empty {
  padding: 8px 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.summary {
  margin: 0 0 12px;
  line-height: 1.7;
}

.section {
  margin-top: 14px;
}

h4 {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.list {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
}

.muted {
  color: var(--el-text-color-secondary);
}

.footer {
  margin-top: 16px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
