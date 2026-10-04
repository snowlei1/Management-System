import { createRouter, createWebHistory } from 'vue-router'
import { authState, loadCurrentUser } from './auth/state.js'
import LoginView from './views/LoginView.vue'
import AppLayout from './views/AppLayout.vue'
import HomeView from './views/HomeView.vue'
import ProfileView from './views/ProfileView.vue'
import UserManagementView from './views/UserManagementView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/', component: AppLayout, children: [
        { path: '', name: 'home', component: HomeView },
        { path: 'profile', name: 'profile', component: ProfileView },
        { path: 'users', name: 'users', component: UserManagementView, meta: { role: 'ADMIN' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  try {
    await loadCurrentUser()
  } catch {
    authState.user = null
    authState.ready = true
  }
  if (to.meta.public) return authState.user ? { name: 'home' } : true
  if (!authState.user) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.role && authState.user.role !== to.meta.role) return { name: 'home' }
  return true
})

export default router
