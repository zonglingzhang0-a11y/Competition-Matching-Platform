<script setup lang="ts">
import { computed } from 'vue'
import type { UserBrief } from '@/types/api'
import { SKILL_LEVEL_LABEL } from '@/utils/labels'

const props = withDefaults(defineProps<{ user: UserBrief; maxSkills?: number }>(), { maxSkills: 6 })

const subtitle = computed(() =>
  [props.user.schoolName, props.user.major, props.user.enrollYear ? `${props.user.enrollYear} 级` : null]
    .filter(Boolean)
    .join(' · '),
)
const skills = computed(() => props.user.skills.slice(0, props.maxSkills))
</script>

<template>
  <div class="user">
    <div class="avatar" aria-hidden="true">{{ user.nickname.slice(0, 1) }}</div>
    <div class="body">
      <div class="name">
        {{ user.nickname }}
        <slot name="badge" />
      </div>
      <div v-if="subtitle" class="sub">{{ subtitle }}</div>
      <div v-if="skills.length" class="skills">
        <el-tag v-for="s in skills" :key="s.skillId" size="small" effect="plain" type="info">
          {{ s.name }} · {{ SKILL_LEVEL_LABEL[s.level] }}
        </el-tag>
        <span v-if="user.skills.length > maxSkills" class="more">等 {{ user.skills.length }} 项</span>
      </div>
      <slot />
    </div>
  </div>
</template>

<style scoped>
.user {
  display: flex;
  gap: 12px;
  min-width: 0;
}

.avatar {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.body {
  min-width: 0;
}

.name {
  font-size: 14px;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.skills {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}

.more {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
