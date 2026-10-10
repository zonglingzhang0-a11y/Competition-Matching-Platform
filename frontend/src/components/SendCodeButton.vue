<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { showError } from '@/utils/feedback'

const props = withDefaults(
  defineProps<{
    /** 真正发验证码的函数；失败时抛出错误，按钮会提示并允许重试 */
    send: () => Promise<void>
    seconds?: number
  }>(),
  { seconds: 60 },
)

const left = ref(0)
const sending = ref(false)
let timer: number | undefined

async function onClick() {
  if (sending.value || left.value > 0) return
  sending.value = true
  try {
    await props.send()
    startCountdown()
  } catch (e) {
    showError(e)
  } finally {
    sending.value = false
  }
}

function startCountdown() {
  left.value = props.seconds
  window.clearInterval(timer)
  timer = window.setInterval(() => {
    left.value -= 1
    if (left.value <= 0) window.clearInterval(timer)
  }, 1000)
}

onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <el-button :loading="sending" :disabled="left > 0" @click="onClick">
    {{ left > 0 ? `${left} 秒后可重发` : '发送验证码' }}
  </el-button>
</template>
