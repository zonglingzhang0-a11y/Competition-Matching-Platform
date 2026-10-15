<script setup lang="ts">
/**
 * 排序题：按点击顺序排名，再点一次取消；最多选 max 项。
 */
const model = defineModel<string[]>({ default: () => [] })

const props = defineProps<{
  options: { value: string; label: string }[]
  max: number
}>()

function rank(value: string): number {
  return model.value.indexOf(value)
}

function toggle(value: string) {
  const i = rank(value)
  if (i >= 0) {
    model.value = model.value.filter((v) => v !== value)
  } else if (model.value.length < props.max) {
    model.value = [...model.value, value]
  }
}
</script>

<template>
  <div class="options">
    <button
      v-for="o in options"
      :key="o.value"
      type="button"
      class="option"
      :class="{ picked: rank(o.value) >= 0 }"
      :disabled="rank(o.value) < 0 && model.length >= max"
      @click="toggle(o.value)"
    >
      <span class="badge">{{ rank(o.value) >= 0 ? rank(o.value) + 1 : '' }}</span>
      <span>{{ o.label }}</span>
    </button>
  </div>
  <div class="hint">按重要程度依次点选，已选 {{ model.length }}/{{ max }}，再点一次可以取消</div>
</template>

<style scoped>
.options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.option {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 2px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: 14px;
  cursor: pointer;
}

.option.picked {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.option:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.badge {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--el-border-color);
}

.picked .badge {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
}

.hint {
  width: 100%;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
