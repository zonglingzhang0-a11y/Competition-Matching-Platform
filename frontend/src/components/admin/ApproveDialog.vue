<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { approveReview, listSchools, reviewImage } from '@/api/admin'
import type { ReviewItem, SchoolRow } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 认证通过：确认是哪所学校（选已有的或新建）。
 * - 教育邮箱：顺便收录一个邮箱后缀，以后同校的人就能自动认证。
 * - 学生证截图：看原图和 OCR 比对结果，图里认出了已收录的学校就默认选它。
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
const imageUrl = ref('')
const imageError = ref('')

const shot = computed(() => (props.item?.method === 'SCREENSHOT' ? props.item.screenshot : null))

watch(open, async (value) => {
  if (!value) {
    if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
    return
  }
  const item = props.item
  if (!item) return
  Object.assign(form, {
    mode: 'existing',
    schoolId: undefined,
    newSchoolName: '',
    province: '',
    city: '',
    // 默认收录最短的那个（学校的主域名），子域名的邮箱也都能对上
    domain: item.domains[item.domains.length - 1] ?? '',
    note: '',
  })
  await search('')
  const s = shot.value
  if (s?.suggestedSchool) {
    form.schoolId = s.suggestedSchool.id
    if (!schools.value.some((x) => x.id === s.suggestedSchool!.id)) {
      schools.value.unshift({ ...s.suggestedSchool, province: null, city: null, domains: [], verifiedUsers: 0 })
    }
  } else if (s?.schoolName) {
    // 图里的学校还没收录：默认新建，名字先填上学生写的
    form.mode = 'new'
    form.newSchoolName = s.schoolName
  }
  if (s?.hasImage) loadImage(item.id)
})

async function loadImage(id: number) {
  imageError.value = ''
  try {
    imageUrl.value = URL.createObjectURL(await reviewImage(id))
  } catch {
    imageError.value = '原图加载不出来，可能已经删除了'
  }
}

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

const canSubmit = computed(
  () =>
    (shot.value || form.domain) &&
    (form.mode === 'existing' ? form.schoolId !== undefined : form.newSchoolName.trim() !== ''),
)

/** 比对结果的标签：认出来了 / 没认出来 / 没能识别 */
function matchTag(ok: boolean | undefined) {
  if (ok === undefined) return { type: 'info' as const, text: '没能识别' }
  return ok ? { type: 'success' as const, text: '图里认出来了' } : { type: 'danger' as const, text: '图里没找到' }
}

async function submit() {
  if (!props.item || !canSubmit.value) return
  saving.value = true
  try {
    const result = await approveReview(props.item.id, {
      domain: shot.value ? undefined : form.domain,
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
  <el-dialog v-model="open" title="认证通过" :width="shot ? '680px' : '520px'" top="6vh">
    <template v-if="item">
      <el-descriptions :column="1" size="small" border class="who">
        <el-descriptions-item label="申请人">{{ item.user.nickname }}（{{ item.user.email }}）</el-descriptions-item>
        <el-descriptions-item v-if="!shot" label="教育邮箱">{{ item.eduEmail }}</el-descriptions-item>
        <el-descriptions-item label="档案">
          {{ [item.user.realName, item.user.major, item.user.enrollYear && `${item.user.enrollYear} 级`].filter(Boolean).join(' · ') || '还没填' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 截图认证：原图 + OCR 比对 -->
      <div v-if="shot" class="shot">
        <div class="image-box">
          <el-image
            v-if="imageUrl"
            :src="imageUrl"
            :preview-src-list="[imageUrl]"
            fit="contain"
            class="image"
            alt="学生上传的认证图片"
          />
          <div v-else class="image-missing">{{ imageError || (shot.hasImage ? '加载中…' : '原图已删除') }}</div>
          <div class="tip">点图片可以放大。截图可能是 P 的，请注意字体、排版是否异常。</div>
        </div>
        <div class="compare">
          <div class="compare-title">
            学生填写的信息
            <el-tag v-if="shot.confidence === 'HIGH'" type="success" size="small">三项一致</el-tag>
            <el-tag v-else-if="shot.confidence === 'LOW'" type="warning" size="small">有出入</el-tag>
            <el-tag v-else type="info" size="small">没能识别</el-tag>
          </div>
          <div class="compare-row">
            <span class="k">姓名</span><span class="v">{{ shot.realName }}</span>
            <el-tag :type="matchTag(shot.matched?.realName).type" size="small" effect="plain">
              {{ matchTag(shot.matched?.realName).text }}
            </el-tag>
          </div>
          <div class="compare-row">
            <span class="k">学校</span><span class="v">{{ shot.schoolName }}</span>
            <el-tag :type="matchTag(shot.matched?.schoolName).type" size="small" effect="plain">
              {{ matchTag(shot.matched?.schoolName).text }}
            </el-tag>
          </div>
          <div class="compare-row">
            <span class="k">入学年份</span><span class="v">{{ shot.enrollYear }}</span>
            <el-tag :type="matchTag(shot.matched?.enrollYear).type" size="small" effect="plain">
              {{ matchTag(shot.matched?.enrollYear).text }}
            </el-tag>
          </div>
          <el-collapse v-if="shot.lines.length" class="lines">
            <el-collapse-item :title="`认出来的全部文字（${shot.lines.length} 行）`">
              <div class="lines-text">{{ shot.lines.join(' / ') }}</div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>

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
          <div v-if="shot?.suggestedSchool" class="tip">图里认出了「{{ shot.suggestedSchool.name }}」，已默认选上。</div>
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
        <el-form-item v-if="!shot" label="收录后缀">
          <el-radio-group v-model="form.domain" class="domains">
            <el-radio v-for="d in item.domains" :key="d" :value="d">{{ d }}</el-radio>
          </el-radio-group>
          <div class="tip">收录后，以它结尾的邮箱都能自动认证，排队中的同校申请也会一起通过。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" maxlength="255" placeholder="选填，比如：核对过学校官网的邮箱说明" />
        </el-form-item>
      </el-form>
      <p v-if="shot" class="tip">通过或驳回后，原图会立即删除，只保留认出来的文字。</p>
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
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.option-sub {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.shot {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.image {
  width: 100%;
  height: 220px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}

.image-missing {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 220px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.compare-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-weight: 500;
}

.compare-row {
  display: grid;
  grid-template-columns: 64px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.k {
  color: var(--el-text-color-secondary);
}

.lines {
  margin-top: 8px;
}

.lines-text {
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

@media (max-width: 640px) {
  .shot {
    grid-template-columns: 1fr;
  }
}
</style>
