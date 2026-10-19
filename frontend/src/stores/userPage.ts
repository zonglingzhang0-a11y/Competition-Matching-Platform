import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 个人资料页抽屉。整个应用只有一个，挂在 MainLayout 里；任何地方点了人名，调用 open 就能打开。
 */
export const useUserPageStore = defineStore('userPage', () => {
  const visible = ref(false)
  const userId = ref<number | null>(null)
  /** 看自己时，按"其他同学看到的样子"显示 */
  const preview = ref(false)

  function open(id: number, options: { preview?: boolean } = {}) {
    userId.value = id
    preview.value = options.preview ?? false
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  return { visible, userId, preview, open, close }
})
