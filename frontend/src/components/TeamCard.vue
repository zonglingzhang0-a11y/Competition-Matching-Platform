<script setup lang="ts">
import { computed } from 'vue'
import SkillRadar from '@/components/SkillRadar.vue'
import UserName from '@/components/UserName.vue'
import type { TeamSummary } from '@/types/api'
import { TEAM_STATUS } from '@/utils/labels'

const props = defineProps<{ team: TeamSummary; showCompetition?: boolean }>()

/** 队长勾了技能要求才画小雷达；只写了角色名的话没什么可画的 */
const hasNeedRadar = computed(() => props.team.needRadar.axes.some((a) => a.score > 0))
const needSeries = computed(() => [
  { name: '招募需求', values: props.team.needRadar.axes.map((a) => a.score), color: '#b0442d' },
])
</script>

<template>
  <el-card shadow="hover" class="team" @click="$router.push({ name: 'team', params: { id: team.id } })">
    <div class="head">
      <span class="name">{{ team.name }}</span>
      <el-tag size="small" :type="TEAM_STATUS[team.status].type">{{ TEAM_STATUS[team.status].label }}</el-tag>
      <span class="count">{{ team.memberCount }}/{{ team.maxMembers }} 人</span>
    </div>
    <div v-if="showCompetition" class="competition">
      {{ team.edition.competitionName }} · {{ team.edition.editionTitle }}
    </div>
    <div class="captain">
      队长 <UserName :id="team.captain.id" :name="team.captain.nickname" /><template v-if="team.captain.schoolName"> · {{ team.captain.schoolName }}</template>
    </div>
    <p v-if="team.intro" class="intro">{{ team.intro }}</p>
    <div v-if="team.openNeeds.length" class="need-row">
      <div class="needs">
        <span class="label">在招</span>
        <el-tag v-for="n in team.openNeeds" :key="n.id" size="small" effect="plain">
          {{ n.roleName }} {{ n.headcount - n.filled }} 人
        </el-tag>
      </div>
      <!-- 招募需求的六维形状：缺程序员还是缺建模的，一眼看出来 -->
      <SkillRadar
        v-if="hasNeedRadar"
        class="mini"
        compact
        :axes="team.needRadar.axes"
        :series="needSeries"
        :height="104"
      />
    </div>
  </el-card>
</template>

<style scoped>
.team {
  border-radius: 2px;
  cursor: pointer;
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name {
  font-size: 15px;
  font-weight: 500;
}

.count {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.competition,
.captain {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}

.intro {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.need-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.needs {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.mini {
  flex: none;
  width: 108px;
}

.label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
