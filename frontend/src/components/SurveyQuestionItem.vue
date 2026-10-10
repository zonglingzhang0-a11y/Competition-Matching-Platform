<script setup lang="ts">
import { computed } from 'vue'
import RankingPicker from '@/components/RankingPicker.vue'
import SlotGrid from '@/components/SlotGrid.vue'
import type { CheckboxGroupValueType } from 'element-plus'
import type { SurveyOption, SurveyQuestion } from '@/types/api'

/**
 * 按题型渲染一道题。quick：快速版里排序题有的会退化成"选 1–2 项"。
 */
const props = defineProps<{
  question: SurveyQuestion
  index: number
  quick: boolean
  /** 选项来自比赛分工模板的题（P4）用这里的角色 */
  roles?: string[]
}>()

const model = defineModel<unknown>()

const num = (key: string, fallback: number) => {
  const v = props.question.settings[key]
  return typeof v === 'number' ? v : fallback
}

const options = computed<SurveyOption[]>(() => {
  if (props.question.settings.optionSource === 'roleTemplate') {
    return (props.roles ?? []).map((r) => ({ value: r, label: r, group: null }))
  }
  return props.question.options ?? []
})

/** 排序题最多选几项；快速版有自己的上限 */
const rankingMax = computed(() => {
  const quickSettings = props.question.settings.quick as { maxSelect?: number } | undefined
  if (props.quick && quickSettings?.maxSelect) return quickSettings.maxSelect
  return Math.min(num('selectCount', 1), options.value.length)
})

const multiMax = computed(() => num('maxSelect', options.value.length))

const exclusive = computed(() => (props.question.settings.exclusive as string[] | undefined) ?? [])

const listValue = computed<string[]>({
  get: () => (Array.isArray(model.value) ? (model.value as string[]) : []),
  set: (v) => (model.value = v),
})

/** 多选题：选了"没有限制"这类互斥项时，清掉其他选项；反之亦然 */
function onMultiChange(value: CheckboxGroupValueType) {
  const next = value.map(String)
  const added = next.find((v) => !listValue.value.includes(v))
  if (added && exclusive.value.includes(added)) listValue.value = [added]
  else listValue.value = next.filter((v) => !exclusive.value.includes(v) || next.length === 1)
}

const singleValue = computed<string>({
  get: () => (typeof model.value === 'string' ? model.value : ''),
  set: (v) => (model.value = v),
})

const sliderValue = computed<number>({
  get: () => (typeof model.value === 'number' ? model.value : num('min', 0)),
  set: (v) => (model.value = v),
})

const textValue = computed<string>({
  get: () => (typeof model.value === 'string' ? model.value : ''),
  set: (v) => (model.value = v),
})

const pledgeValue = computed<boolean>({
  get: () => model.value === true,
  set: (v) => (model.value = v),
})

const slotValue = computed<number>({
  get: () => (typeof model.value === 'number' ? model.value : 0),
  set: (v) => (model.value = v),
})

const groups = computed(() => {
  const map = new Map<string, SurveyOption[]>()
  for (const o of options.value) {
    const g = o.group ?? ''
    map.set(g, [...(map.get(g) ?? []), o])
  }
  return [...map.entries()]
})

const GROUP_LABEL: Record<string, string> = { time: '占用时间的事', other: '其他限制' }
</script>

<template>
  <div class="question">
    <div class="title">
      <span class="index">{{ index }}.</span>
      {{ question.title }}
      <span v-if="!question.required" class="optional">选填</span>
    </div>
    <div v-if="question.note" class="note">{{ question.note }}</div>

    <el-radio-group v-if="question.type === 'SINGLE'" v-model="singleValue" class="vertical">
      <el-radio v-for="o in options" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
    </el-radio-group>

    <template v-else-if="question.type === 'MULTI'">
      <div v-for="[g, items] in groups" :key="g" class="multi-group">
        <div v-if="g" class="group-label">{{ GROUP_LABEL[g] ?? g }}</div>
        <el-checkbox-group :model-value="listValue" :max="multiMax" class="wrap" @update:model-value="onMultiChange">
          <el-checkbox v-for="o in items" :key="o.value" :value="o.value">{{ o.label }}</el-checkbox>
        </el-checkbox-group>
      </div>
    </template>

    <template v-else-if="question.type === 'RANKING'">
      <el-empty v-if="options.length === 0" :image-size="40" description="这场比赛还没有设置分工模板" />
      <RankingPicker v-else v-model="listValue" :options="options" :max="rankingMax" />
    </template>

    <div v-else-if="question.type === 'SLIDER'" class="slider">
      <el-slider v-model="sliderValue" :min="num('min', 0)" :max="num('max', 100)" :step="num('step', 1)" />
      <span class="slider-value">{{ sliderValue }} {{ question.settings.unit ?? '' }}</span>
    </div>

    <template v-else-if="question.type === 'SLOT_GRID'">
      <SlotGrid v-model="slotValue" />
    </template>

    <el-input
      v-else-if="question.type === 'TEXT'"
      v-model="textValue"
      type="textarea"
      :rows="2"
      :maxlength="num('maxLength', 500)"
      show-word-limit
      :placeholder="(question.settings.placeholder as string) ?? ''"
    />

    <el-checkbox v-else-if="question.type === 'PLEDGE'" v-model="pledgeValue">我承诺</el-checkbox>
  </div>
</template>

<style scoped>
.question {
  padding: 18px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.title {
  font-size: 15px;
  font-weight: 500;
  margin-bottom: 4px;
}

.index {
  color: var(--el-text-color-secondary);
  margin-right: 2px;
}

.optional {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  margin-left: 6px;
}

.note {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 10px;
}

.vertical {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin-top: 8px;
}

.wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 0;
}

.multi-group + .multi-group {
  margin-top: 8px;
}

.group-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin: 6px 0 2px;
}

.slider {
  display: flex;
  align-items: center;
  gap: 16px;
  max-width: 480px;
  padding: 0 8px;
}

.slider .el-slider {
  flex: 1;
}

.slider-value {
  min-width: 64px;
  font-size: 14px;
  font-weight: 500;
}
</style>
