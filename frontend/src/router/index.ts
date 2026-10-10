import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guest: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { guest: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
        { path: 'verify', name: 'verify', component: () => import('@/views/VerifyView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const user = useUserStore()

  if (to.meta.guest) {
    return user.isLoggedIn ? { name: 'home' } : true
  }
  if (!user.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!user.me) {
    try {
      await user.refreshMe()
    } catch {
      user.clear()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

export default router
