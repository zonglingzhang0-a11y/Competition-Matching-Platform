<script setup lang="ts">
import { computed } from 'vue'
import UserName from '@/components/UserName.vue'
import { useUserPageStore } from '@/stores/userPage'
import type { UserBrief } from '@/types/api'
import { SKILL_LEVEL_LABEL, creditType } from '@/utils/labels'

const props = withDefaults(defineProps<{ user: UserBrief; maxSkills?: number }>(), { maxSkills: 6 })

const subtitle = computed(() =>
  [props.user.schoolName, props.user.major, props.user.enrollYear ? `${props.user.enrollYear} 级` : null]
    .filter(Boolean)
    .join(' · '),
)
const skills = computed(() => props.user.skills.slice(0, props.maxSkills))
const userPage = useUserPageStore()
</script>

<template>
  <div class="user">
    <button type="button" class="avatar" :aria-label="`查看 ${user.nickname} 的资料`" @click.stop="userPage.open(user.id)">
      {{ user.nickname.slice(0, 1) }}
    </button>
    <div class="body">
      <div class="name">
        <UserName :id="user.id" :name="user.nickname" />
        <slot name="badge" />
        <el-tag
          v-if="user.credit != null"
          size="small"
          effect="plain"
          :type="creditType(user.credit)"
          title="赛后互评得出的信用分"
        >
          信用 {{ Math.round(user.credit) }}
        </el-tag>
        <el-tag v-else-if="user.credit === null" size="small" effect="plain" type="info" title="还没有互评记录">
          新用户
        </el-tag>
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
  padding: 0;
  border: none;
  font: inherit;
  font-size: 15px;
  cursor: pointer;
}

.avatar:hover {
  outline: 2px solid var(--jd-accent);
  outline-offset: 1px;
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
