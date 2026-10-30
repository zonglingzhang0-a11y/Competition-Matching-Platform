<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { applyAiSkills, analyzeProfile, getProfileAnalysis } from '@/api/ai'
import type { AiProfileAnalysis, AiSuggestedSkill } from '@/types/api'
import { SKILL_LEVELS } from '@/utils/labels'
import { showError } from '@/utils/feedback'
import { fmtDateTime } from '@/utils/time'

/**
 * AI 能力分析：读档案里的技能、履历、简介和问卷（合作相关的几道题），
 * 给出优势、合作风格、建议补的技能。技能一定要用户勾选确认才写进档案。
 * 没打开 AI 分析、模型没配好、正忙或出错时，后端用本地规则算，卡片上会标"本地规则"，不会假装是 AI。
 */
const emit = defineEmits<{ applied: [] }>()

const analysis = ref<AiProfileAnalysis | null>(null)
const loading = ref(true)
const analyzing = ref(false)
const applying = ref(false)
/** 勾选的技能：名字 → 熟练度；不勾的不会写进档案 */
const picked = ref<Record<string, number>>({})

const body = computed(() => analysis.value?.body)
const sourceText = computed(() =>
  analysis.value?.source === 'LLM' ? `AI 模型 · ${analysis.value.model ?? '默认'}` : '本地规则',
)
const skillCount = computed(() => Object.keys(picked.value).length)

function isPicked(name: string) {
  return name in picked.value
}

function toggle(s: AiSuggestedSkill) {
  if (isPicked(s.name)) {
    const next = { ...picked.value }
    delete next[s.name]
    picked.value = next
  } else {
    picked.value = { ...picked.value, [s.name]: s.level }
  }
}

function setLevel(name: string, level: number) {
  picked.value = { ...picked.value, [name]: level }
}

async function load() {
  loading.value = true
  try {
    analysis.value = await getProfileAnalysis()
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

async function run() {
  analyzing.value = true
  try {
    analysis.value = await analyzeProfile()
    picked.value = {}
    ElMessage.success(
      analysis.value.source === 'LLM' ? '分析完成，只把确认过的技能写进档案' : '这次用本地规则分析（没打开 AI 分析，或者模型没连上）',
    )
  } catch (e) {
    showError(e)
  } finally {
    analyzing.value = false
  }
}

async function apply() {
  if (!skillCount.value) return
  applying.value = true
  try {
    await applyAiSkills(Object.entries(picked.value).map(([name, level]) => ({ name, level })))
    ElMessage.success('已加入你的技能')
    picked.value = {}
    emit('applied')
    await load()
  } catch (e) {
    showError(e)
  } finally {
    applying.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading" shadow="never" class="block">
    <template #header>
      <div class="card-head">
        <span>AI 能力分析</span>
        <el-tag v-if="analysis" size="small" :type="analysis.source === 'LLM' ? 'success' : 'info'">
          {{ sourceText }}
        </el-tag>
      </div>
    </template>

    <p class="hint">
      按你的技能、履历、简介和问卷（合作相关的几道题）分析优势、合作风格和适合的角色。分析只给建议，技能要你确认才写进档案。
    </p>

    <el-alert
      v-if="analysis?.source === 'RULE'"
      type="info"
      :closable="false"
      show-icon
      class="note"
      title="现在用的是本地规则分析"
      description="这一份是按技能和问卷算出来的：后端没打开 AI 分析（app.llm 和 app.ai），或者模型正忙、没连上。打开以后点“重新分析”，会用模型重做。"
    />

    <div v-if="!analysis" class="empty">
      <p>还没有分析过。填好技能和问卷后点一下，就能看到自己的优势、合作风格和建议补的技能。</p>
      <el-button type="primary" :loading="analyzing" @click="run">开始分析</el-button>
    </div>

    <template v-else-if="body">
      <p class="summary">{{ body.summary }}</p>

      <section v-if="body.strengths.length" class="section">
        <h4>优势</h4>
        <div v-for="s in body.strengths" :key="s.title" class="item">
          <b>{{ s.title }}</b>
          <span v-if="s.detail"> · {{ s.detail }}</span>
          <div class="evidence">依据：{{ s.evidence }}</div>
        </div>
      </section>

      <section v-if="body.personality.length" class="section">
        <h4>合作风格</h4>
        <div class="traits">
          <el-tooltip v-for="t in body.personality" :key="t.trait" :content="`依据：${t.evidence}`" placement="top">
            <el-tag size="small" effect="plain">{{ t.trait }}</el-tag>
          </el-tooltip>
        </div>
      </section>

      <section v-if="body.skills.length" class="section">
        <h4>建议补的技能 <span class="muted">（勾选后才会写进档案）</span></h4>
        <div v-for="s in body.skills" :key="s.name" class="skill">
          <el-checkbox :model-value="isPicked(s.name)" @change="toggle(s)">
            <b>{{ s.name }}</b>
            <span class="muted"> {{ s.category }}</span>
            <el-tag v-if="s.owned" size="small" effect="plain" class="owned">档案里已有</el-tag>
          </el-checkbox>
          <el-radio-group
            v-if="isPicked(s.name)"
            :model-value="picked[s.name]"
            size="small"
            @update:model-value="(v) => setLevel(s.name, Number(v))"
          >
            <el-radio-button v-for="l in SKILL_LEVELS" :key="l.value" :value="l.value">{{ l.label }}</el-radio-button>
          </el-radio-group>
          <div class="evidence">依据：{{ s.evidence }}</div>
        </div>
        <el-button type="primary" :disabled="!skillCount" :loading="applying" @click="apply">
          加入我的技能（{{ skillCount }}）
        </el-button>
      </section>

      <section v-if="body.axisHints.length" class="section">
        <h4>可以加强的方向</h4>
        <div v-for="h in body.axisHints" :key="h.key" class="item">
          <b>{{ h.label }}</b> · {{ h.reason }}
        </div>
      </section>

      <div class="footer">
        <span class="muted">{{ fmtDateTime(analysis.createdAt) }} 分析 · 结果只是建议，不会自动改你的档案</span>
        <el-button text :loading="analyzing" @click="run">重新分析</el-button>
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

.item {
  padding: 4px 0;
  font-size: 13px;
  line-height: 1.6;
}

.evidence {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.traits {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.skill {
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.skill:last-of-type {
  border-bottom: none;
}

.owned {
  margin-left: 6px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
