import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ElNotification } from 'element-plus'
import { markRead, pushTicket, unreadCount } from '@/api/notification'
import router from '@/router'
import type { Notification } from '@/types/api'
import { notificationRoute } from '@/utils/notificationLink'

/** 后端推来的消息，格式见后端 NotificationService */
type PushMessage =
  | { type: 'notification'; notification: Notification; unread: number }
  | { type: 'unread'; unread: number }
  | { type: 'pong' }

const HEARTBEAT_MS = 30_000
/** 这么久没收到任何东西（包括心跳的回应），就当连接已经断了 */
const DEAD_MS = 75_000
/** 连着的时候偶尔也对一下未读数；断开时每分钟查一次 */
const POLL_LIVE_MS = 5 * 60_000
const POLL_FALLBACK_MS = 60_000
/** 断线后重连的间隔，越来越长 */
const RETRY_MS = [2_000, 5_000, 10_000, 30_000, 60_000]
/** 后端关连接时说"登录已失效"，就不再重连 */
const LOGGED_OUT = 4401

/**
 * 未读消息数（顶栏的铃铛）和新消息提示。
 * 登录后和后端保持一条 WebSocket 连接，有新消息后端马上推过来；连不上时退回每分钟查一次。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)
  /** 推送连接是否连着 */
  const live = ref(false)
  /** 最近推来的一条消息，消息页看到它变了就刷新列表 */
  const latest = ref<Notification | null>(null)

  let running = false
  let connecting = false
  let socket: WebSocket | null = null
  let lastHeard = 0
  let retries = 0
  let pollTimer: number | undefined
  let heartbeatTimer: number | undefined
  let retryTimer: number | undefined

  async function refresh() {
    try {
      unread.value = await unreadCount()
    } catch {
      // 拉不到就保持原样，不打扰用户
    }
  }

  function start() {
    stop()
    running = true
    refresh()
    schedulePoll()
    connect()
    document.addEventListener('visibilitychange', wake)
    window.addEventListener('online', wake)
  }

  function stop() {
    running = false
    document.removeEventListener('visibilitychange', wake)
    window.removeEventListener('online', wake)
    window.clearInterval(pollTimer)
    window.clearTimeout(retryTimer)
    drop()
  }

  function schedulePoll() {
    window.clearInterval(pollTimer)
    pollTimer = window.setInterval(refresh, live.value ? POLL_LIVE_MS : POLL_FALLBACK_MS)
  }

  async function connect() {
    if (!running || socket || connecting) return
    connecting = true
    let ticket: string
    try {
      ticket = (await pushTicket()).ticket
    } catch {
      connecting = false
      retry()
      return
    }
    connecting = false
    if (!running) return

    const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
    const ws = new WebSocket(`${protocol}://${location.host}/api/ws/notifications?ticket=${ticket}`)
    socket = ws
    ws.onopen = () => {
      live.value = true
      retries = 0
      lastHeard = Date.now()
      schedulePoll()
      // 断开期间可能错过了消息，连上后对一下数
      refresh()
      window.clearInterval(heartbeatTimer)
      heartbeatTimer = window.setInterval(heartbeat, HEARTBEAT_MS)
    }
    ws.onmessage = (e: MessageEvent<string>) => {
      lastHeard = Date.now()
      handle(JSON.parse(e.data) as PushMessage)
    }
    ws.onclose = (e: CloseEvent) => {
      if (socket !== ws) return
      drop()
      if (e.code !== LOGGED_OUT) retry()
    }
  }

  function heartbeat() {
    const ws = socket
    if (!ws || ws.readyState !== WebSocket.OPEN) return
    if (Date.now() - lastHeard > DEAD_MS) {
      // 网断了浏览器不一定马上发现，自己判断后直接换一条
      drop()
      retry()
      return
    }
    ws.send(JSON.stringify({ type: 'ping' }))
  }

  /** 丢掉当前连接，回到定时查询 */
  function drop() {
    window.clearInterval(heartbeatTimer)
    const ws = socket
    socket = null
    if (ws && ws.readyState <= WebSocket.OPEN) ws.close(1000)
    if (live.value) {
      live.value = false
      if (running) schedulePoll()
    }
  }

  function retry() {
    if (!running) return
    window.clearTimeout(retryTimer)
    retryTimer = window.setTimeout(connect, RETRY_MS[Math.min(retries, RETRY_MS.length - 1)])
    retries++
  }

  /** 切回这个页面或网络恢复时，没连着就马上重连，不等重连间隔 */
  function wake() {
    if (!running || socket || document.visibilityState !== 'visible') return
    window.clearTimeout(retryTimer)
    retries = 0
    connect()
  }

  function handle(msg: PushMessage) {
    if (msg.type === 'notification') {
      unread.value = msg.unread
      latest.value = msg.notification
      popup(msg.notification)
    } else if (msg.type === 'unread') {
      unread.value = msg.unread
    }
  }

  /** 右上角弹出提示，点一下标为已读并跳到相关页面 */
  function popup(n: Notification) {
    const to = notificationRoute(n)
    const box = ElNotification({
      title: n.title,
      message: n.content ?? '',
      type: 'info',
      duration: 6000,
      offset: 56,
      customClass: to ? 'push-clickable' : '',
      onClick: () => {
        box.close()
        markRead(n.id).catch(() => {})
        if (to) router.push(to)
      },
    })
  }

  return { unread, live, latest, refresh, start, stop }
})
