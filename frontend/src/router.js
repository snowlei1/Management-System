import { createRouter, createWebHistory } from 'vue-router'
import { authState, loadCurrentUser } from './auth/state.js'
import LoginView from './views/LoginView.vue'
import AppLayout from './views/AppLayout.vue'
import HomeView from './views/HomeView.vue'
import ProfileView from './views/ProfileView.vue'
import UserManagementView from './views/UserManagementView.vue'
import BaseDataManagementView from './views/BaseDataManagementView.vue'
import ResourceDraftListView from './views/ResourceDraftListView.vue'
import ResourceDraftFormView from './views/ResourceDraftFormView.vue'
import ResourceDraftDetailView from './views/ResourceDraftDetailView.vue'
import ResourceReviewListView from './views/ResourceReviewListView.vue'
import ResourceReviewDetailView from './views/ResourceReviewDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    {
      path: '/', component: AppLayout, children: [
        { path: '', name: 'home', component: HomeView },
        { path: 'profile', name: 'profile', component: ProfileView },
        { path: 'my-resources', name: 'my-resources', component: ResourceDraftListView, meta: { role: 'TEACHER' } },
        { path: 'my-resources/new', name: 'resource-create', component: ResourceDraftFormView, meta: { role: 'TEACHER' } },
        { path: 'my-resources/:id/edit', name: 'resource-edit', component: ResourceDraftFormView, meta: { role: 'TEACHER' } },
        { path: 'my-resources/:id', name: 'resource-detail', component: ResourceDraftDetailView, meta: { role: 'TEACHER' } },
        { path: 'users', name: 'users', component: UserManagementView, meta: { role: 'ADMIN' } },
        { path: 'resource-reviews', name: 'resource-reviews', component: ResourceReviewListView, meta: { role: 'ADMIN' } },
        { path: 'resource-reviews/:id', name: 'resource-review-detail', component: ResourceReviewDetailView, meta: { role: 'ADMIN' } },
        { path: 'courses', name: 'courses', component: BaseDataManagementView, props: { kind: 'courses' }, meta: { role: 'ADMIN' } },
        { path: 'ideological-elements', name: 'ideological-elements', component: BaseDataManagementView, props: { kind: 'ideological-elements' }, meta: { role: 'ADMIN' } },
        { path: 'resource-categories', name: 'resource-categories', component: BaseDataManagementView, props: { kind: 'resource-categories' }, meta: { role: 'ADMIN' } },
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
