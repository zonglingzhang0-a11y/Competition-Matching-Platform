<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Close } from '@element-plus/icons-vue'
import { replaceSkills, skillOptions } from '@/api/profile'
import type { SkillLevel, SkillOption } from '@/types/api'
import { SKILL_LEVELS } from '@/utils/labels'
import { showError } from '@/utils/feedback'

const props = defineProps<{ initial: SkillLevel[] }>()

const MAX = 20

const options = ref<SkillOption[]>([])
const rows = ref<SkillLevel[]>(props.initial.map((s) => ({ ...s })))
const picking = ref<number>()
const dirty = ref(false)
const saving = ref(false)

/** 下拉框按类别分组，已经选过的不再出现 */
const groups = computed(() => {
  const chosen = new Set(rows.value.map((r) => r.skillId))
  const map = new Map<string, SkillOption[]>()
  for (const o of options.value) {
    if (chosen.has(o.id)) continue
    const list = map.get(o.category) ?? []
    list.push(o)
    map.set(o.category, list)
  }
  return [...map.entries()].map(([category, items]) => ({ category, items }))
})

function add(id: number | undefined) {
  const s = options.value.find((o) => o.id === id)
  if (!s) return
  rows.value.push({ skillId: s.id, name: s.name, category: s.category, level: 2 })
  picking.value = undefined
  dirty.value = true
}

function remove(index: number) {
  rows.value.splice(index, 1)
  dirty.value = true
}

async function save() {
  saving.value = true
  try {
    rows.value = await replaceSkills(rows.value.map((r) => ({ skillId: r.skillId, level: r.level })))
    dirty.value = false
    ElMessage.success('技能已保存')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    options.value = await skillOptions()
  } catch (e) {
    showError(e)
  }
})
</script>

<template>
  <div>
    <p class="hint">推荐队友时会拿你的技能去补队伍的缺口，熟练度请如实填写。</p>

    <el-empty v-if="rows.length === 0" :image-size="60" description="还没有添加技能" />
    <div v-for="(r, i) in rows" :key="r.skillId" class="row">
      <span class="name">{{ r.name }}</span>
      <span class="category">{{ r.category }}</span>
      <el-radio-group v-model="r.level" size="small" @change="dirty = true">
        <el-radio-button v-for="l in SKILL_LEVELS" :key="l.value" :value="l.value">{{ l.label }}</el-radio-button>
      </el-radio-group>
      <el-button text :icon="Close" aria-label="移除这项技能" @click="remove(i)" />
    </div>

    <div class="actions">
      <el-select
        v-model="picking"
        filterable
        default-first-option
        placeholder="添加技能"
        :disabled="rows.length >= MAX"
        class="picker"
        @change="add"
      >
        <el-option-group v-for="g in groups" :key="g.category" :label="g.category">
          <el-option v-for="o in g.items" :key="o.id" :label="o.name" :value="o.id" />
        </el-option-group>
      </el-select>
      <el-button type="primary" :disabled="!dirty" :loading="saving" @click="save">保存技能</el-button>
      <span v-if="dirty" class="unsaved">有改动还没保存</span>
    </div>
  </div>
</template>

<style scoped>
.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.name {
  min-width: 140px;
  font-size: 14px;
}

.category {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}

.picker {
  width: 200px;
}

.unsaved {
  font-size: 13px;
  color: var(--el-color-warning);
}
</style>
