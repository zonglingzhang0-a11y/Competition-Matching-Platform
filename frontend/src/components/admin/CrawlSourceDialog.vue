<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { adminCompetitions, createCrawlSource, previewCrawlSource, updateCrawlSource } from '@/api/admin'
import type { CrawlSourceForm, CrawlSourceRow, PreviewResult } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 新增或编辑一个抓取来源。选择器对着官网页面写，写好后点"试抓"看看哪些链接会被抓到。
 */
const props = defineProps<{ source: CrawlSourceRow | null }>()
const emit = defineEmits<{ saved: [] }>()
const open = defineModel<boolean>({ required: true })

const form = reactive<CrawlSourceForm>(blank())
const competitions = ref<{ id: number; name: string }[]>([])
const searching = ref(false)
const saving = ref(false)
const previewing = ref(false)
const preview = ref<PreviewResult | null>(null)

function blank(): CrawlSourceForm {
  return {
    competitionId: null,
    name: '',
    listUrl: '',
    linkSelector: 'a',
    titleKeywords: '通知,报名',
    excludeKeywords: '获奖,名单,公示',
    contentSelector: null,
    allowLlm: true,
    enabled: true,
    note: null,
  }
}

watch(open, (value) => {
  if (!value) return
  preview.value = null
  const s = props.source
  Object.assign(form, s ? pick(s) : blank())
  competitions.value = s ? [{ id: s.competitionId, name: s.competitionName ?? `比赛 ${s.competitionId}` }] : []
  if (!s) search('')
})

function pick(s: CrawlSourceRow): CrawlSourceForm {
  return {
    competitionId: s.competitionId,
    name: s.name,
    listUrl: s.listUrl,
    linkSelector: s.linkSelector,
    titleKeywords: s.titleKeywords,
    excludeKeywords: s.excludeKeywords,
    contentSelector: s.contentSelector,
    allowLlm: s.allowLlm,
    enabled: s.enabled,
    note: s.note,
  }
}

async function search(keyword: string) {
  searching.value = true
  try {
    competitions.value = (await adminCompetitions(keyword, 1)).records.map((c) => ({ id: c.id, name: c.name }))
  } catch (e) {
    showError(e)
  } finally {
    searching.value = false
  }
}

function payload(): CrawlSourceForm | null {
  if (form.competitionId === null) {
    ElMessage.warning('请选择比赛')
    return null
  }
  if (!form.name.trim() || !form.listUrl.trim() || !form.linkSelector.trim()) {
    ElMessage.warning('名称、网址、通知链接选择器都要填')
    return null
  }
  return { ...form, name: form.name.trim(), listUrl: form.listUrl.trim(), linkSelector: form.linkSelector.trim() }
}

async function runPreview() {
  const body = payload()
  if (!body) return
  previewing.value = true
  try {
    preview.value = await previewCrawlSource(body)
  } catch (e) {
    showError(e)
  } finally {
    previewing.value = false
  }
}

async function save() {
  const body = payload()
  if (!body) return
  saving.value = true
  try {
    if (props.source) await updateCrawlSource(props.source.id, body)
    else await createCrawlSource(body)
    ElMessage.success('已保存')
    open.value = false
    emit('saved')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" :title="source ? '编辑抓取来源' : '新增抓取来源'" width="720px" top="5vh">
    <el-form label-width="120px" @submit.prevent>
      <el-form-item label="比赛" required>
        <el-select
          v-model="form.competitionId"
          filterable
          remote
          :remote-method="search"
          :loading="searching"
          placeholder="搜索比赛名称"
          class="wide"
        >
          <el-option v-for="c in competitions" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源名称" required>
        <el-input v-model="form.name" maxlength="64" placeholder="如 数模官网首页" />
      </el-form-item>
      <el-form-item label="列表页网址" required>
        <el-input v-model="form.listUrl" maxlength="512" placeholder="比赛官网首页或通知列表页，https://…" />
      </el-form-item>
      <el-form-item label="通知链接" required>
        <el-input v-model="form.linkSelector" maxlength="255" placeholder="CSS 选择器，如 #news a 或 a[href*=/news/]" />
      </el-form-item>
      <el-form-item label="标题关键词">
        <el-input v-model="form.titleKeywords" maxlength="255" placeholder="逗号分隔，标题至少含一个才抓；不填就都抓" />
      </el-form-item>
      <el-form-item label="排除词">
        <el-input v-model="form.excludeKeywords" maxlength="255" placeholder="逗号分隔，标题含任何一个就跳过" />
      </el-form-item>
      <el-form-item label="正文">
        <el-input v-model="form.contentSelector" maxlength="255" placeholder="详情页正文的 CSS 选择器；不填就取整页文字" />
      </el-form-item>
      <el-form-item label="选项">
        <el-checkbox v-model="form.enabled">每天自动抓</el-checkbox>
        <el-checkbox v-model="form.allowLlm">允许交给大模型抽取</el-checkbox>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.note" maxlength="255" />
      </el-form-item>
    </el-form>
    <p class="tip">
      只抓和列表页同一网站的链接；先看网站的 robots.txt，不让抓的不抓。网站在 robots.txt 里拒绝 AI 爬虫的，会自动取消"允许交给大模型"。
    </p>

    <div v-if="preview" class="preview">
      <div class="section">
        列表页里找到 {{ preview.linkCount }} 个链接，会抓 {{ preview.links.filter((l) => l.picked).length }} 篇
      </div>
      <el-table :data="preview.links" max-height="260" size="small" empty-text="选择器没选中任何链接">
        <el-table-column label="标题" min-width="300">
          <template #default="{ row }">
            <a :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.title || row.url }}</a>
          </template>
        </el-table-column>
        <el-table-column label="" width="190">
          <template #default="{ row }">
            <el-tag :type="row.picked ? 'success' : 'info'" size="small">{{ row.reason }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button class="left" :loading="previewing" @click="runPreview">试抓</el-button>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.wide {
  width: 100%;
}

.tip {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.preview {
  margin-top: 8px;
}

.section {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
}

.preview a {
  color: var(--el-color-primary);
  text-decoration: none;
}

.left {
  float: left;
}
</style>
