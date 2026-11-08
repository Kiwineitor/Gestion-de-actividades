<script setup>
import { watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import { moduloActual, puedeAbrir } from '../modulos';
import Login from '../views/Login.vue';
import PrimerIngreso from '../views/PrimerIngreso.vue';
import UiModal from './UiModal.vue';
import CodigoRecuperacion from './CodigoRecuperacion.vue';
import AppLayout from './AppLayout.vue';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();

watch(() => auth.autenticado, async (dentro) => {
  if (!dentro || !route.query.m) return;
  const permisos = usePermisosStore();
  await permisos.asegurar();
  if (!puedeAbrir(moduloActual(route), auth, permisos)) router.replace('/admin');
});
</script>

<template>
  <PrimerIngreso v-if="auth.autenticado && auth.pendientes.length" />
  <AppLayout v-else-if="auth.autenticado" />
  <Login v-else />
  <UiModal v-if="auth.autenticado && auth.codigoRecuperacion" titulo="🗝️ Tu código de recuperación" ancho="520px" sin-cerrar>
    <CodigoRecuperacion :codigo="auth.codigoRecuperacion" :usuario="auth.usuario" @listo="auth.codigoRecuperacion = ''" />
  </UiModal>
</template>
