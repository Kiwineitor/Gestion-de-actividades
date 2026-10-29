<script setup>
import { watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import { moduloActual, puedeAbrir } from '../modulos';
import Login from '../views/Login.vue';
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
  <AppLayout v-if="auth.autenticado" />
  <Login v-else />
</template>
