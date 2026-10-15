// Element Plus 按需引入（见 vite.config.ts）。模板里的组件自动带样式；
// 下面这几个是在代码里调用的（消息提示、确认框、通知、v-loading），样式要手动引入
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import './assets/main.css'
import './assets/theme.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElLoading, ElMessage, ElMessageBox, ElNotification, provideGlobalConfig } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import { onUnauthorized } from './api/http'
import { useUserStore } from './stores/user'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 全局中文；确认框、消息这些在组件树外创建的，也要挂上应用上下文才拿得到中文
provideGlobalConfig({ locale: zhCn }, app, true)
app.use(ElMessage).use(ElMessageBox).use(ElNotification).use(ElLoading)

// 登录过期：清掉本地状态，回到登录页
onUnauthorized(() => {
  useUserStore().clear()
  const current = router.currentRoute.value
  if (!current.meta.guest) {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
