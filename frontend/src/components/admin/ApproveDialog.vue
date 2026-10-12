<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { approveReview, listSchools } from '@/api/admin'
import type { ReviewItem, SchoolRow } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 认证通过：确认是哪所学校（选已有的或新建），并收录一个邮箱后缀，以后同校的人就能自动认证。
 */
const props = defineProps<{ item: ReviewItem | null }>()
const emit = defineEmits<{ approved: [] }>()
const open = defineModel<boolean>({ required: true })

const form = reactive({
  mode: 'existing' as 'existing' | 'new',
  schoolId: undefined as number | undefined,
  newSchoolName: '',
  province: '',
  city: '',
  domain: '',
  note: '',
})
const schools = ref<SchoolRow[]>([])
const searching = ref(false)
const saving = ref(false)

watch(open, (value) => {
  if (!value || !props.item) return
  Object.assign(form, {
    mode: 'existing',
    schoolId: undefined,
    newSchoolName: '',
    province: '',
    city: '',
    // 默认收录最短的那个（学校的主域名），子域名的邮箱也都能对上
    domain: props.item.domains[props.item.domains.length - 1] ?? '',
    note: '',
  })
  search('')
})

async function search(keyword: string) {
  searching.value = true
  try {
    schools.value = (await listSchools(keyword, 1, 20)).records
  } catch (e) {
    showError(e)
  } finally {
    searching.value = false
  }
}

const canSubmit = computed(() =>
  form.domain && (form.mode === 'existing' ? form.schoolId !== undefined : form.newSchoolName.trim() !== ''),
)

async function submit() {
  if (!props.item || !canSubmit.value) return
  saving.value = true
  try {
    const result = await approveReview(props.item.id, {
      domain: form.domain,
      note: form.note.trim() || undefined,
      ...(form.mode === 'existing'
        ? { schoolId: form.schoolId }
        : { newSchoolName: form.newSchoolName.trim(), province: form.province.trim(), city: form.city.trim() }),
    })
    ElMessage.success(
      `已通过，确认为「${result.schoolName}」` + (result.alsoApproved ? `；同校的 ${result.alsoApproved} 条申请也一起通过了` : ''),
    )
    open.value = false
    emit('approved')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" title="认证通过" width="520px">
    <template v-if="item">
      <el-descriptions :column="1" size="small" border class="who">
        <el-descriptions-item label="申请人">{{ item.user.nickname }}（{{ item.user.email }}）</el-descriptions-item>
        <el-descriptions-item label="教育邮箱">{{ item.eduEmail }}</el-descriptions-item>
        <el-descriptions-item label="档案">
          {{ [item.user.realName, item.user.major, item.user.enrollYear && `${item.user.enrollYear} 级`].filter(Boolean).join(' · ') || '还没填' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form label-width="84px" @submit.prevent="submit">
        <el-form-item label="学校">
          <el-radio-group v-model="form.mode">
            <el-radio value="existing">选已有的</el-radio>
            <el-radio value="new">新建一所</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'existing'" label="选择学校">
          <el-select
            v-model="form.schoolId"
            filterable
            remote
            :remote-method="search"
            :loading="searching"
            placeholder="输入学校名或邮箱后缀搜索"
            class="full"
          >
            <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id">
              <span>{{ s.name }}</span>
              <span class="option-sub">{{ s.domains.map((d) => d.domain).join('、') }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <template v-else>
          <el-form-item label="学校全称">
            <el-input v-model="form.newSchoolName" maxlength="64" placeholder="和学校官网上的写法一致" />
          </el-form-item>
          <el-form-item label="所在地">
            <div class="row">
              <el-input v-model="form.province" maxlength="16" placeholder="省份" />
              <el-input v-model="form.city" maxlength="32" placeholder="城市" />
            </div>
          </el-form-item>
        </template>
        <el-form-item label="收录后缀">
          <el-radio-group v-model="form.domain" class="domains">
            <el-radio v-for="d in item.domains" :key="d" :value="d">{{ d }}</el-radio>
          </el-radio-group>
          <div class="tip">收录后，以它结尾的邮箱都能自动认证，排队中的同校申请也会一起通过。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" maxlength="255" placeholder="选填，比如：核对过学校官网的邮箱说明" />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="submit">通过</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.who {
  margin-bottom: 16px;
}

.full {
  width: 100%;
}

.row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.domains {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.tip {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.option-sub {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
