<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addSchoolDomain, createSchool, listSchools, removeSchoolDomain } from '@/api/admin'
import type { SchoolRow } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 学校和教育邮箱后缀。收录了后缀的学校，学生用教育邮箱就能自动认证。
 */
const emit = defineEmits<{ changed: [] }>()

const keyword = ref('')
const page = ref(1)
const rows = ref<SchoolRow[]>([])
const total = ref(0)
const loading = ref(false)

/** 正在哪一行输入新后缀 */
const addingFor = ref<number | null>(null)
const newDomain = ref('')
const domainInput = ref<{ focus: () => void }>()

const createOpen = ref(false)
const createForm = reactive({ name: '', province: '', city: '' })
const creating = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await listSchools(keyword.value.trim(), page.value)
    rows.value = res.records
    total.value = res.total
  } catch (e) {
    showError(e)
  } finally {
    loading.value = false
  }
}

function searchChanged() {
  page.value = 1
  load()
}

async function startAdding(row: SchoolRow) {
  addingFor.value = row.id
  newDomain.value = ''
  await nextTick()
  domainInput.value?.focus()
}

async function confirmDomain(row: SchoolRow) {
  const domain = newDomain.value.trim()
  addingFor.value = null
  if (!domain) return
  try {
    const approved = await addSchoolDomain(row.id, domain)
    ElMessage.success(approved ? `已收录，顺带通过了 ${approved} 条待审核的申请` : '已收录')
    await load()
    if (approved) emit('changed')
  } catch (e) {
    showError(e)
  }
}

async function removeDomain(row: SchoolRow, d: { id: number; domain: string }) {
  try {
    await ElMessageBox.confirm(
      `删掉「${row.name}」的后缀 ${d.domain}？已经认证的人不受影响，以后用这个后缀的邮箱要人工审核。`,
      '删除后缀',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await removeSchoolDomain(d.id)
    await load()
  } catch (e) {
    showError(e)
  }
}

async function create() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请填写学校全称')
    return
  }
  creating.value = true
  try {
    await createSchool({
      name: createForm.name.trim(),
      province: createForm.province.trim(),
      city: createForm.city.trim(),
    })
    ElMessage.success('已新建，记得给它加上邮箱后缀')
    createOpen.value = false
    keyword.value = createForm.name.trim()
    Object.assign(createForm, { name: '', province: '', city: '' })
    searchChanged()
  } catch (e) {
    showError(e)
  } finally {
    creating.value = false
  }
}

onMounted(load)
defineExpose({ reload: load })
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜学校名或邮箱后缀"
        class="search"
        @keyup.enter="searchChanged"
        @clear="searchChanged"
      />
      <el-button @click="searchChanged">搜索</el-button>
      <el-button type="primary" @click="createOpen = true">新建学校</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" empty-text="没有找到学校">
      <el-table-column label="学校" min-width="180">
        <template #default="{ row }">
          <div>{{ row.name }}</div>
          <div v-if="row.province || row.city" class="muted">{{ [row.province, row.city].filter(Boolean).join(' ') }}</div>
        </template>
      </el-table-column>
      <el-table-column label="邮箱后缀" min-width="300">
        <template #default="{ row }">
          <div class="domains">
            <el-tag v-for="d in row.domains" :key="d.id" closable @close="removeDomain(row as SchoolRow, d)">{{ d.domain }}</el-tag>
            <el-input
              v-if="addingFor === row.id"
              ref="domainInput"
              v-model="newDomain"
              size="small"
              placeholder="如 stu.xxx.edu.cn"
              class="domain-input"
              @keyup.enter="confirmDomain(row as SchoolRow)"
              @blur="confirmDomain(row as SchoolRow)"
            />
            <el-button v-else size="small" @click="startAdding(row as SchoolRow)">+ 后缀</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="verifiedUsers" label="已认证" width="80" align="right" />
    </el-table>
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next"
      class="pager"
      @current-change="load"
    />

    <el-dialog v-model="createOpen" title="新建学校" width="420px">
      <el-form label-width="72px" @submit.prevent="create">
        <el-form-item label="学校全称" required>
          <el-input v-model="createForm.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="createForm.province" maxlength="16" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="createForm.city" maxlength="32" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">新建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.search {
  max-width: 280px;
}

.domains {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.domain-input {
  width: 180px;
}

.muted {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  justify-content: center;
  margin-top: 12px;
}
</style>
