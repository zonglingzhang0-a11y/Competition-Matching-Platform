<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ConfigQuestion } from '@/types/api'

/**
 * 编辑一道单选题的相容矩阵：第 i 行第 j 列是"选 i 的人和选 j 的人"的契合度，0 到 1。
 * 默认对称编辑（改一格，对角那一格跟着改）。
 */
const props = defineProps<{
  question: ConfigQuestion | undefined
  code: string
  options: string[]
  matrix: number[][]
}>()
const emit = defineEmits<{ apply: [matrix: number[][]] }>()
const open = defineModel<boolean>({ required: true })

const draft = ref<number[][]>([])
const symmetric = ref(true)

watch(open, (value) => {
  if (value) draft.value = props.matrix.map((row) => [...row])
})

function label(value: string) {
  return props.question?.options?.find((o) => o.value === value)?.label ?? value
}

function changed(i: number, j: number, v: number | undefined) {
  const value = Math.min(1, Math.max(0, v ?? 0))
  const row = draft.value[i]
  if (row) row[j] = value
  const mirror = draft.value[j]
  if (symmetric.value && mirror) mirror[i] = value
}

/** 分数越高颜色越深 */
function cellStyle(v: number) {
  return { background: `rgba(64, 158, 255, ${0.08 + v * 0.42})` }
}

function apply() {
  emit('apply', draft.value.map((row) => [...row]))
  open.value = false
}
</script>

<template>
  <el-dialog v-model="open" :title="`相容矩阵 · ${code}`" width="720px">
    <p class="question">{{ question?.title }}</p>
    <div class="scroll">
      <table class="matrix">
        <thead>
          <tr>
            <th />
            <th v-for="o in options" :key="o" :title="label(o)">{{ o }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in options" :key="r">
            <th class="row-head">
              <b>{{ r }}</b>
              <span>{{ label(r) }}</span>
            </th>
            <td v-for="(c, j) in options" :key="c" :style="cellStyle(draft[i]?.[j] ?? 0)">
              <el-input-number
                :model-value="draft[i]?.[j] ?? 0"
                :min="0"
                :max="1"
                :step="0.1"
                :precision="2"
                :controls="false"
                size="small"
                class="cell"
                :aria-label="`${r} 和 ${c} 的契合度`"
                @update:model-value="(v: number | undefined) => changed(i, j, v)"
              />
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <el-checkbox v-model="symmetric" class="sym">对称编辑（A 配 B 和 B 配 A 一样）</el-checkbox>
    <p class="tip">1 表示非常合得来，0 表示基本合不来。对角线是两人选得一样时的分数，不一定是 1，比如两个人都"拖到最后"反而不好。</p>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" @click="apply">用这个矩阵</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.question {
  margin: 0 0 12px;
  font-weight: 500;
}

.scroll {
  overflow-x: auto;
}

.matrix {
  border-collapse: separate;
  border-spacing: 3px;
}

.matrix th {
  font-weight: 500;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.row-head {
  max-width: 220px;
  padding-right: 8px;
  text-align: left;
}

.row-head span {
  margin-left: 6px;
  font-weight: normal;
  font-size: 12px;
}

.matrix td {
  padding: 4px;
  border-radius: 6px;
}

.cell {
  width: 64px;
}

.sym {
  margin-top: 12px;
}

.tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
