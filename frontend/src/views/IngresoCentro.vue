<script setup>
import { computed, defineAsyncComponent } from 'vue';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import VistaPestanas from '../components/VistaPestanas.vue';

const auth = useAuthStore();
const permisos = usePermisosStore();
const ve = (p) => permisos.puedeVer(p);

const pestanas = computed(() => [
  {
    k: 'escanear', t: 'Escanear', icono: '📷',
    visible: auth.puedeEditarPlano || ['escaner', 'control-acceso', 'mesas-ingreso', 'mesas'].some(ve),
    ayuda: 'Un solo lector para todo: credenciales de expositores y de apoyo, y pases de mesa.',
    vista: defineAsyncComponent(() => import('./Escaner.vue')),
  },
  {
    k: 'movimientos', t: 'Movimientos y quién está dentro', icono: '🚪',
    visible: auth.puedeEditarPlano || ve('control-acceso'),
    vista: defineAsyncComponent(() => import('./ControlAcceso.vue')),
  },
]);
</script>

<template>
  <VistaPestanas modulo="ingreso" :pestanas="pestanas" />
</template>
