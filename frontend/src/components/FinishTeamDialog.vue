<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { finishTeam } from '@/api/review'
import { AWARD_SUGGESTIONS } from '@/utils/labels'
import { showError } from '@/utils/feedback'

/**
 * 队长标记比赛结束：录入奖项（可以不填），队伍归档，每个队员的履历里自动加一条，两人及以上的队开启互评。
 */
const props = defineProps<{ teamId: number; teamName: string; memberCount: number }>()
const emit = defineEmits<{ finished: [] }>()
const open = defineModel<boolean>({ required: true })
const router = useRouter()

const award = ref('')
const saving = ref(false)

watch(open, (value) => {
  if (value) award.value = ''
})

function suggest(query: string, cb: (items: { value: string }[]) => void) {
  cb(AWARD_SUGGESTIONS.filter((a) => !query || a.includes(query)).map((value) => ({ value })))
}

async function submit() {
  saving.value = true
  try {
    const roundId = await finishTeam(props.teamId, award.value.trim())
    open.value = false
    if (roundId) {
      ElMessage.success('已标记比赛结束，互评开放 7 天，先给队友打个分吧')
      router.push({ name: 'review-form', params: { id: roundId } })
    } else {
      ElMessage.success('已标记比赛结束，履历里已经记上了')
      emit('finished')
    }
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="`比赛结束 · ${teamName}`" width="460px">
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="获奖情况（选填，以后也能在履历里补）">
        <el-autocomplete
          v-model="award"
          :fetch-suggestions="suggest"
          maxlength="32"
          placeholder="比如 省级二等奖、成功参赛"
          class="full"
        />
      </el-form-item>
    </el-form>
    <ul class="effects">
      <li>队伍归档，成员和招募需求不能再改动</li>
      <li>每位队员的参赛履历里自动加上这场比赛</li>
      <li v-if="memberCount >= 2">开启 7 天的匿名互评，所有人交齐或到期后统一揭晓，结果计入信用分</li>
    </ul>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确认结束</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.full {
  width: 100%;
}

.effects {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--el-text-color-regular);
}
</style>
