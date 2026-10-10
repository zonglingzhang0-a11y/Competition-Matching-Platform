import { ref } from 'vue'
import { defineStore } from 'pinia'
import { unreadCount } from '@/api/notification'

/** 未读消息数，顶栏的铃铛用。每分钟刷新一次，切换页面时也刷新 */
export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)
  let timer: number | undefined

  async function refresh() {
    try {
      unread.value = await unreadCount()
    } catch {
      // 拉不到就保持原样，不打扰用户
    }
  }

  function start() {
    stop()
    refresh()
    timer = window.setInterval(refresh, 60_000)
  }

  function stop() {
    window.clearInterval(timer)
    timer = undefined
  }

  return { unread, refresh, start, stop }
})
