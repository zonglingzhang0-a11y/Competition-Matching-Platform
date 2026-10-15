<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { ElMessage } from 'element-plus'
import { matchConfigs, saveMatchConfig } from '@/api/admin'
import MatrixDialog from '@/components/admin/MatrixDialog.vue'
import type { ConfigPage, MatchPreset } from '@/types/api'
import { PRESET_LABEL, SCORER_LABEL } from '@/utils/labels'
import { fmtDateTime } from '@/utils/time'
import { showError } from '@/utils/feedback'

/*
 * 匹配配置。表单里只改常用的几项：维度权重、题目权重、相容矩阵、预设的覆盖项；
 * 其他参数（容忍差距、一票否决等）在"直接编辑 JSON"里改。
 * 编辑稿是整份配置解析出来的对象，表单没涉及的字段原样保留，保存时整体转回 JSON。
 */

type Json = Record<string, unknown>
interface ConfigItem extends Json {
  q: string
  scorer: string
  weight: number
  options?: string[]
  matrix?: number[][]
}
interface ConfigDimension extends Json {
  key: string
  name: string
  weight: number
  items: ConfigItem[]
}
interface DefaultConfig extends Json {
  dimensions: ConfigDimension[]
}
interface Override {
  row: string
  col: string
  value: number
}
interface PresetJson extends Json {
  dimensionWeights?: Record<string, number>
  matrixOverrides?: Record<string, Override[]>
}

const PRESET_TIPS: Record<MatchPreset, string> = {
  DEFAULT: '所有比赛的基础配置。改完保存，下一次推荐就按新配置算。',
  SHORT_INTENSIVE: '给数模国赛这类几天内打完的比赛用：只写和默认配置不一样的维度权重和矩阵格子，其余沿用默认。',
  LONG_TERM: '给互联网+这类要做几个月的比赛用：只写和默认配置不一样的维度权重和矩阵格子，其余沿用默认。',
}

const page = ref<ConfigPage | null>(null)
const tab = ref<MatchPreset>('DEFAULT')
const loading = ref(false)
const saving = ref(false)

const defaultDraft = ref<DefaultConfig | null>(null)
const presetDrafts = ref<Partial<Record<MatchPreset, PresetJson>>>({})
/** 每套配置从服务器读到的原文（统一格式化过），用来判断有没有改动 */
const originals = ref<Partial<Record<MatchPreset, string>>>({})

const jsonMode = ref(false)
const jsonText = ref('')

const matrixOpen = ref(false)
const matrixItem = ref<ConfigItem | null>(null)

const newOverride = ref<{ q: string; row: string; col: string; value: number }>({ q: '', row: '', col: '', value: 0.5 })

const questions = computed(() => new Map((page.value?.questions ?? []).map((q) => [q.code, q])))
const current = computed(() => page.value?.presets.find((p) => p.preset === tab.value))
const currentDraft = computed<Json | null>(() =>
  tab.value === 'DEFAULT' ? defaultDraft.value : (presetDrafts.value[tab.value] ?? null),
)
const dirty = computed(() =>
  jsonMode.value ? true : !!currentDraft.value && JSON.stringify(currentDraft.value) !== originals.value[tab.value],
)
const dimTotal = computed(() => (defaultDraft.value?.dimensions ?? []).reduce((s, d) => s + (d.weight || 0), 0))
const singleItems = computed(() =>
  (defaultDraft.value?.dimensions ?? []).flatMap((d) => d.items).filter((i) => i.scorer === 'single' && i.options),
)
const presetDraft = computed(() => (tab.value === 'DEFAULT' ? null : (presetDrafts.value[tab.value] ?? null)))
/** 预设生效后的维度权重 */
const effectiveWeights = computed(() => {
  const dims = defaultDraft.value?.dimensions ?? []
  const own = presetDraft.value?.dimensionWeights ?? {}
  const weights = dims.map((d) => own[d.key] ?? d.weight)
  const total = weights.reduce((s, w) => s + w, 0)
  return new Map(dims.map((d, i) => [d.key, total ? (weights[i] ?? 0) / total : 0]))
})

async function load() {
  loading.value = true
  try {
    const res = await matchConfigs()
    page.value = res
    presetDrafts.value = {}
    for (const p of res.presets) {
      if (!p.config) continue
      const obj = JSON.parse(p.config) as Json
      originals.value[p.preset] = JSON.stringify(obj)
      if (p.preset === 'DEFAULT') defaultDraft.value = obj as DefaultConfig
      else presetDrafts.value[p.preset] = obj as PresetJson
    }
    jsonMode.value = false
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function title(code: string) {
  if (code === 'SKILLS') return '个人档案里的技能（看能不能补上队伍缺的技能）'
  return questions.value.get(code)?.title ?? code
}

function percent(v: number) {
  return `${Math.round(v * 100)}%`
}

function itemShare(d: ConfigDimension, item: ConfigItem) {
  const total = d.items.reduce((s, i) => s + (i.weight || 0), 0)
  return total ? (item.weight || 0) / total : 0
}

function openMatrix(item: ConfigItem) {
  matrixItem.value = item
  matrixOpen.value = true
}

function applyMatrix(m: number[][]) {
  if (matrixItem.value) matrixItem.value.matrix = m
}

// ---------- 预设 ----------

function hasOwnWeight(key: string) {
  return presetDraft.value?.dimensionWeights?.[key] !== undefined
}

function toggleOwnWeight(d: ConfigDimension, on: boolean) {
  const p = presetDraft.value
  if (!p) return
  const weights = { ...p.dimensionWeights }
  if (on) weights[d.key] = d.weight
  else delete weights[d.key]
  p.dimensionWeights = weights
}

function setOwnWeight(key: string, v: number | undefined) {
  const p = presetDraft.value
  if (!p) return
  p.dimensionWeights = { ...p.dimensionWeights, [key]: v ?? 0 }
}

type OverrideRow = Override & { q: string; index: number }

const overrideRows = computed<OverrideRow[]>(() =>
  Object.entries(presetDraft.value?.matrixOverrides ?? {}).flatMap(([q, list]) =>
    list.map((o, index) => ({ q, index, ...o })),
  ),
)

function optionLabel(q: string, value: string) {
  return questions.value.get(q)?.options?.find((o) => o.value === value)?.label ?? value
}

function defaultCell(q: string, row: string, col: string) {
  const item = singleItems.value.find((i) => i.q === q)
  const r = item?.options?.indexOf(row) ?? -1
  const c = item?.options?.indexOf(col) ?? -1
  return r >= 0 && c >= 0 ? item?.matrix?.[r]?.[c] : undefined
}

function addOverride() {
  const p = presetDraft.value
  const o = newOverride.value
  if (!p || !o.q || !o.row || !o.col) {
    ElMessage.warning('请选好题目和两个选项')
    return
  }
  const all = { ...p.matrixOverrides }
  const list = (all[o.q] ?? []).filter((x) => !(x.row === o.row && x.col === o.col) && !(x.row === o.col && x.col === o.row))
  all[o.q] = [...list, { row: o.row, col: o.col, value: o.value }]
  p.matrixOverrides = all
}

function removeOverride(q: string, index: number) {
  const p = presetDraft.value
  if (!p?.matrixOverrides?.[q]) return
  const all = { ...p.matrixOverrides }
  const list = (all[q] ?? []).filter((_, i) => i !== index)
  if (list.length) all[q] = list
  else delete all[q]
  p.matrixOverrides = all
}

const overrideOptions = computed(() => singleItems.value.find((i) => i.q === newOverride.value.q)?.options ?? [])

// ---------- JSON 模式、保存 ----------

function enterJson() {
  jsonText.value = JSON.stringify(currentDraft.value ?? {}, null, 2)
  jsonMode.value = true
}

/** 把 JSON 文本应用回编辑稿；格式不对返回 false */
function leaveJson(): boolean {
  let obj: Json
  try {
    obj = JSON.parse(jsonText.value) as Json
  } catch {
    ElMessage.error('JSON 格式不对，请检查括号和逗号')
    return false
  }
  if (tab.value === 'DEFAULT') defaultDraft.value = obj as DefaultConfig
  else presetDrafts.value[tab.value] = obj as PresetJson
  jsonMode.value = false
  return true
}

function beforeLeaveTab() {
  return jsonMode.value ? leaveJson() : true
}

function discard() {
  const raw = current.value?.config
  if (!raw) return
  const obj = JSON.parse(raw) as Json
  if (tab.value === 'DEFAULT') defaultDraft.value = obj as DefaultConfig
  else presetDrafts.value[tab.value] = obj as PresetJson
  jsonMode.value = false
}

async function save() {
  const text = jsonMode.value ? jsonText.value : JSON.stringify(currentDraft.value ?? {})
  saving.value = true
  try {
    await saveMatchConfig(tab.value, text)
    ElMessage.success('已保存，推荐马上按新配置计算')
    const keep = tab.value
    await load()
    tab.value = keep
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading" class="page">
    <PageHeader en="MATCHING" zh="匹配配置" path="管理后台 / 匹配配置" />
    <el-tabs v-model="tab" :before-leave="beforeLeaveTab">
      <el-tab-pane v-for="p in page?.presets ?? []" :key="p.preset" :label="PRESET_LABEL[p.preset]" :name="p.preset" />
    </el-tabs>

    <div class="bar">
      <div class="meta">
        <span>{{ PRESET_TIPS[tab] }}</span>
        <span v-if="current?.updatedAt" class="muted">
          上次修改：{{ fmtDateTime(current.updatedAt, true) }}{{ current.updatedBy ? ` · ${current.updatedBy}` : '' }}
        </span>
      </div>
      <div class="buttons">
        <el-tag v-if="dirty" type="warning" size="small">未保存</el-tag>
        <el-button v-if="!jsonMode" size="small" @click="enterJson">直接编辑 JSON</el-button>
        <el-button v-else size="small" @click="leaveJson">回到表单</el-button>
        <el-button size="small" :disabled="!dirty" @click="discard">撤销修改</el-button>
        <el-button size="small" type="primary" :loading="saving" :disabled="!dirty" @click="save">保存</el-button>
      </div>
    </div>

    <el-input
      v-if="jsonMode"
      v-model="jsonText"
      type="textarea"
      :autosize="{ minRows: 18, maxRows: 40 }"
      spellcheck="false"
      class="json"
    />

    <!-- 默认配置 -->
    <template v-else-if="tab === 'DEFAULT' && defaultDraft">
      <p class="hint">
        总分 = 各维度分按维度权重加权；维度分 = 维度里各题按题目权重加权。权重按比例换算，不用凑成 1。
        某道题双方没填就跳过，数据越少分数越往 50 收。
      </p>
      <el-card v-for="d in defaultDraft.dimensions" :key="d.key" shadow="never" class="dim">
        <template #header>
          <div class="dim-head">
            <span class="dim-name">{{ d.name }}</span>
            <span class="muted">{{ d.key }}</span>
            <span class="spacer" />
            <span class="muted">维度权重</span>
            <el-input-number v-model="d.weight" :min="0" :max="10" :step="0.05" :precision="2" size="small" />
            <span class="share">{{ percent(dimTotal ? d.weight / dimTotal : 0) }}</span>
          </div>
        </template>
        <el-table :data="d.items" size="small">
          <el-table-column label="题目" min-width="280">
            <template #default="{ row }">
              <b class="code">{{ row.q }}</b>
              {{ title(row.q) }}
            </template>
          </el-table-column>
          <el-table-column label="打分方式" width="110">
            <template #default="{ row }">{{ SCORER_LABEL[row.scorer] ?? row.scorer }}</template>
          </el-table-column>
          <el-table-column label="题目权重" width="200">
            <template #default="{ row }">
              <el-input-number v-model="row.weight" :min="0" :max="10" :step="0.05" :precision="2" size="small" class="w" />
              <span class="share">{{ percent(itemShare(d, row as ConfigItem)) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="" width="110" align="right">
            <template #default="{ row }">
              <el-button v-if="row.scorer === 'single' && row.matrix" size="small" link type="primary" @click="openMatrix(row as ConfigItem)">
                相容矩阵
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- 预设 -->
    <template v-else-if="presetDraft && defaultDraft">
      <el-card shadow="never" class="dim">
        <template #header>维度权重</template>
        <div v-for="d in defaultDraft.dimensions" :key="d.key" class="weight-row">
          <span class="dim-name">{{ d.name }}</span>
          <el-checkbox :model-value="hasOwnWeight(d.key)" @update:model-value="(v) => toggleOwnWeight(d, !!v)">
            单独设置
          </el-checkbox>
          <el-input-number
            v-if="hasOwnWeight(d.key)"
            :model-value="presetDraft.dimensionWeights?.[d.key]"
            :min="0"
            :max="10"
            :step="0.05"
            :precision="2"
            size="small"
            @update:model-value="(v: number | undefined) => setOwnWeight(d.key, v)"
          />
          <span v-else class="muted">沿用默认 {{ d.weight }}</span>
          <span class="spacer" />
          <span class="share">实际占 {{ percent(effectiveWeights.get(d.key) ?? 0) }}</span>
        </div>
      </el-card>

      <el-card shadow="never" class="dim">
        <template #header>相容矩阵里要改的格子</template>
        <el-table :data="overrideRows" size="small" empty-text="没有改动，全部沿用默认矩阵">
          <el-table-column label="题目" min-width="220">
            <template #default="{ row }"><b class="code">{{ row.q }}</b> {{ title(row.q) }}</template>
          </el-table-column>
          <el-table-column label="两人的选项" min-width="220">
            <template #default="{ row }">{{ optionLabel(row.q, row.row) }} × {{ optionLabel(row.q, row.col) }}</template>
          </el-table-column>
          <el-table-column label="分数" width="120">
            <template #default="{ row }">
              {{ defaultCell(row.q, row.row, row.col) ?? '?' }} → <b>{{ row.value }}</b>
            </template>
          </el-table-column>
          <el-table-column width="70" align="right">
            <template #default="{ row }">
              <el-button size="small" link type="danger" @click="removeOverride(row.q, row.index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="add-override">
          <el-select v-model="newOverride.q" placeholder="题目" size="small" class="q-select" @change="newOverride.row = newOverride.col = ''">
            <el-option v-for="i in singleItems" :key="i.q" :label="`${i.q} ${title(i.q)}`" :value="i.q" />
          </el-select>
          <el-select v-model="newOverride.row" placeholder="选项" size="small" class="o-select">
            <el-option v-for="o in overrideOptions" :key="o" :label="`${o} ${optionLabel(newOverride.q, o)}`" :value="o" />
          </el-select>
          <span>×</span>
          <el-select v-model="newOverride.col" placeholder="选项" size="small" class="o-select">
            <el-option v-for="o in overrideOptions" :key="o" :label="`${o} ${optionLabel(newOverride.q, o)}`" :value="o" />
          </el-select>
          <el-input-number v-model="newOverride.value" :min="0" :max="1" :step="0.1" :precision="2" size="small" />
          <el-button size="small" @click="addOverride">添加</el-button>
        </div>
      </el-card>
    </template>

    <MatrixDialog
      v-model="matrixOpen"
      :code="matrixItem?.q ?? ''"
      :question="matrixItem ? questions.get(matrixItem.q) : undefined"
      :options="matrixItem?.options ?? []"
      :matrix="matrixItem?.matrix ?? []"
      @apply="applyMatrix"
    />
  </div>
</template>

<style scoped>
.page {
  max-width: 1080px;
  margin: 0 auto;
  min-height: 200px;
}

.bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  max-width: 640px;
}

.buttons {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.json :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
}

.dim {
  margin-bottom: 12px;
}

.dim-head,
.weight-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.weight-row {
  padding: 6px 0;
}

.dim-name {
  min-width: 90px;
  font-weight: 500;
}

.spacer {
  flex: 1;
}

.share {
  display: inline-block;
  min-width: 44px;
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.code {
  margin-right: 4px;
}

.w {
  width: 110px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.add-override {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.q-select {
  width: 260px;
}

.o-select {
  width: 160px;
}
</style>
