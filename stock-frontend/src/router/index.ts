import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/home'
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue'),
      meta: { public: true }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/Register.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      name: 'Layout',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        {
          path: 'home',
          name: 'Home',
          component: () => import('@/views/Home.vue'),
          meta: { public: true }
        },
        {
          path: '',
          name: 'HomeDefault',
          component: () => import('@/views/Home.vue'),
          meta: { public: true }
        },
        {
          path: 'market',
          name: 'Market',
          component: () => import('@/views/Market.vue'),
          meta: { public: true }
        },
        {
          path: 'stock/:symbol',
          name: 'StockDetail',
          component: () => import('@/views/StockDetail.vue'),
          meta: { public: true }
        },
        {
          path: 'favorites',
          name: 'Favorites',
          component: () => import('@/views/Favorites.vue')
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('@/views/Profile.vue')
        }
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  
  if (!to.meta.public && !userStore.token) {
    next('/login')
  } else if ((to.path === '/login' || to.path === '/register') && userStore.token) {
    next('/')
  } else {
    next()
  }
})

export default router
