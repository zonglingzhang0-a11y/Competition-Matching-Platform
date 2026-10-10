/// <reference types="vite/client" />
/// <reference types="element-plus/global" />

import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    /** 只给没登录的人看（登录页、注册页） */
    guest?: boolean
  }
}
