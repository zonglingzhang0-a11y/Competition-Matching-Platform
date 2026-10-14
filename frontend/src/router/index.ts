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
        {
          path: 'competitions',
          name: 'competitions',
          component: () => import('@/views/CompetitionListView.vue'),
        },
        {
          path: 'competitions/:id(\\d+)',
          name: 'competition',
          component: () => import('@/views/CompetitionDetailView.vue'),
        },
        { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },
        { path: 'teams', name: 'my-teams', component: () => import('@/views/MyTeamsView.vue') },
        { path: 'teams/:id(\\d+)', name: 'team', component: () => import('@/views/TeamDetailView.vue') },
        { path: 'notifications', name: 'notifications', component: () => import('@/views/NotificationsView.vue') },
        { path: 'calendar', name: 'calendar', component: () => import('@/views/CalendarView.vue') },
        { path: 'survey', name: 'survey', component: () => import('@/views/SurveyView.vue') },
        {
          path: 'survey/editions/:id(\\d+)',
          name: 'survey-edition',
          component: () => import('@/views/SurveyView.vue'),
        },
        { path: 'reviews', name: 'reviews', component: () => import('@/views/ReviewsView.vue') },
        {
          path: 'reviews/:id(\\d+)',
          name: 'review-form',
          component: () => import('@/views/ReviewFormView.vue'),
        },
        {
          path: 'admin',
          meta: { admin: true },
          children: [
            { path: '', name: 'admin-dashboard', component: () => import('@/views/admin/DashboardView.vue') },
            {
              path: 'verifications',
              name: 'admin-verifications',
              component: () => import('@/views/admin/VerificationsView.vue'),
            },
            {
              path: 'competitions',
              name: 'admin-competitions',
              component: () => import('@/views/admin/CompetitionsView.vue'),
            },
            { path: 'crawl', name: 'admin-crawl', component: () => import('@/views/admin/CrawlView.vue') },
            {
              path: 'match-config',
              name: 'admin-match-config',
              component: () => import('@/views/admin/MatchConfigView.vue'),
            },
            { path: 'users', name: 'admin-users', component: () => import('@/views/admin/UsersView.vue') },
            { path: 'reports', name: 'admin-reports', component: () => import('@/views/admin/ReportsView.vue') },
          ],
        },
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
  // 管理页面只给管理员；后端接口本身也会检查，这里只是不让普通用户看到空页面
  if (to.matched.some((r) => r.meta.admin) && !user.isAdmin) {
    return { name: 'home' }
  }
  return true
})

export default router
