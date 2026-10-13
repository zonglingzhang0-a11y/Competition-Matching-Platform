<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, type UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { submitScreenshot } from '@/api/auth'
import { getProfile } from '@/api/profile'
import type { ScreenshotResult } from '@/types/api'
import { showError } from '@/utils/feedback'

/**
 * 截图认证：上传学信网学籍页截图或学生证照片，填上和图片上一致的姓名、学校、入学年份。
 * 后端用本地 OCR 先比对一遍，再交给管理员审核。
 */
const emit = defineEmits<{ submitted: [] }>()

const MAX_BYTES = 5 * 1024 * 1024
const thisYear = new Date().getFullYear()
const years = Array.from({ length: 9 }, (_, i) => thisYear + 1 - i)

const form = reactive<{ realName: string; schoolName: string; enrollYear: number | null }>({
  realName: '',
  schoolName: '',
  enrollYear: null,
})
const file = ref<File | null>(null)
const preview = ref('')
const saving = ref(false)
const result = ref<ScreenshotResult | null>(null)

onMounted(async () => {
  try {
    // 档案里填过的先带上
    const p = await getProfile()
    form.realName = p.realName ?? ''
    form.enrollYear = p.enrollYear ?? null
  } catch {
    // 拿不到就让用户自己填
  }
})

onBeforeUnmount(() => {
  if (preview.value) URL.revokeObjectURL(preview.value)
})

function pick(uploadFile: UploadFile) {
  const raw = uploadFile.raw
  if (!raw) return
  if (!['image/jpeg', 'image/png'].includes(raw.type)) {
    ElMessage.warning('只支持 jpg、png 格式的图片')
    return
  }
  if (raw.size > MAX_BYTES) {
    ElMessage.warning('图片太大了，最多 5 MB，可以截图后再传')
    return
  }
  if (preview.value) URL.revokeObjectURL(preview.value)
  file.value = raw
  preview.value = URL.createObjectURL(raw)
  result.value = null
}

async function submit() {
  if (!form.realName.trim() || !form.schoolName.trim() || !form.enrollYear) {
    ElMessage.warning('姓名、学校、入学年份都要填，和图片上的一致')
    return
  }
  if (!file.value) {
    ElMessage.warning('请选择一张图片')
    return
  }
  saving.value = true
  try {
    result.value = await submitScreenshot(file.value, form.realName.trim(), form.schoolName.trim(), form.enrollYear)
    ElMessage.success('已提交，等管理员审核')
    emit('submitted')
  } catch (e) {
    showError(e)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <p class="intro">
      没有教育邮箱的话，上传<b>学信网"学籍信息"页的截图</b>（推荐，最清楚）或者<b>学生证照片</b>，管理员核对后通过。
    </p>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="真实姓名" required>
        <el-input v-model="form.realName" maxlength="32" placeholder="和图片上的一致" />
      </el-form-item>
      <el-form-item label="学校全称" required>
        <el-input v-model="form.schoolName" maxlength="64" placeholder="和图片上的一致，比如 示例大学" />
      </el-form-item>
      <el-form-item label="入学年份" required>
        <el-select v-model="form.enrollYear" placeholder="选择入学年份" class="year">
          <el-option v-for="y in years" :key="y" :label="`${y} 年`" :value="y" />
        </el-select>
      </el-form-item>
      <el-form-item label="图片" required>
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="false"
          accept=".jpg,.jpeg,.png"
          class="upload"
          :on-change="pick"
        >
          <img v-if="preview" :src="preview" alt="待上传的认证图片预览" class="preview" />
          <template v-else>
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div>把图片拖到这里，或<em>点击选择</em></div>
          </template>
          <template #tip>
            <div class="tip">jpg 或 png，5 MB 以内。点图片可以重新选。</div>
          </template>
        </el-upload>
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="saving">提交审核</el-button>
    </el-form>

    <el-alert
      v-if="result"
      :type="result.confidence === 'HIGH' ? 'success' : 'info'"
      :closable="false"
      show-icon
      class="result"
      :title="result.message"
    >
      <div v-if="result.matched" class="matched">
        <el-tag :type="result.matched.realName ? 'success' : 'danger'" size="small">
          姓名{{ result.matched.realName ? '已认出' : '没认出' }}
        </el-tag>
        <el-tag :type="result.matched.schoolName ? 'success' : 'danger'" size="small">
          学校{{ result.matched.schoolName ? '已认出' : '没认出' }}
        </el-tag>
        <el-tag :type="result.matched.enrollYear ? 'success' : 'danger'" size="small">
          入学年份{{ result.matched.enrollYear ? '已认出' : '没认出' }}
        </el-tag>
      </div>
    </el-alert>

    <ul class="privacy">
      <li>图片只有管理员能看到，<b>审核完立即删除</b>，只保留认出来的文字。</li>
      <li>文字识别在平台自己的服务器上完成，图片不会发给任何第三方。</li>
      <li>身份证号、学号这类和认证无关的信息，可以先打码再上传。</li>
    </ul>
  </div>
</template>

<style scoped>
.intro {
  margin: 0 0 16px;
  font-size: 14px;
  line-height: 1.7;
}

.year {
  width: 160px;
}

.upload {
  width: 100%;
}

.upload :deep(.el-upload-dragger) {
  padding: 20px;
}

.upload-icon {
  font-size: 40px;
  color: var(--el-text-color-placeholder);
}

.preview {
  max-width: 100%;
  max-height: 260px;
  object-fit: contain;
}

.tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result {
  margin-top: 16px;
}

.matched {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}

.privacy {
  margin: 16px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
</style>
