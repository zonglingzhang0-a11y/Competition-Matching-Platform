import 'element-plus/dist/index.css'
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

import App from './App.vue'
import router from './router'
import { onUnauthorized } from './api/http'
import { useUserStore } from './stores/user'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 登录过期：清掉本地状态，回到登录页
onUnauthorized(() => {
  useUserStore().clear()
  const current = router.currentRoute.value
  if (!current.meta.guest) {
    router.push({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
