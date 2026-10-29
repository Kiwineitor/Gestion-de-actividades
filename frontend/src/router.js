import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from './stores/auth';
import { usePermisosStore } from './stores/permisos';
import { EN_APK } from './config';
import { marca } from './ui/marca';
import { MODULOS, claveModulo, moduloActual, puedeAbrir, destinoAntiguo } from './modulos';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', component: () => import('./views/Portal.vue'), meta: { publico: true } },
    { path: '/admin', component: () => import('./components/PanelAdmin.vue') },
    { path: '/:pathMatch(.*)*', redirect: (to) => destinoAntiguo(to.path, to.query) },
  ],
});

router.beforeEach(async (to) => {
  if (to.path === '/' && EN_APK && !Object.keys(to.query).length) return '/admin';
  if (to.path !== '/admin') return;
  const auth = useAuthStore();
  if (!auth.autenticado) return;
  if (to.query.m && !MODULOS[to.query.m]) return '/admin';
  const modulo = moduloActual(to);
  if (claveModulo(to) === 'inicio') return;
  const permisos = usePermisosStore();
  await permisos.asegurar();
  if (!puedeAbrir(modulo, auth, permisos)) return '/admin';
});

router.afterEach((to) => {
  if (to.path === '/admin') {
    const auth = useAuthStore();
    document.title = auth.autenticado ? `${moduloActual(to).titulo} · ${marca.nombre}` : marca.nombre;
  }
});

export default router;
