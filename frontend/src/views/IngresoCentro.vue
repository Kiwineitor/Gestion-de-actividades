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
    k: 'llegadas', t: 'Llegadas', icono: '✅',
    visible: auth.puedeEditarPlano || ['escaner', 'control-acceso', 'mesas-ingreso', 'mesas'].some(ve),
    ayuda: 'Valida al dueño de cada lugar el día del evento: búscalo por nombre, C.I., número de mesa o puesto o n.º de venta y confirma su llegada. Funciona con o sin credenciales.',
    vista: defineAsyncComponent(() => import('./Llegadas.vue')),
  },
  {
    k: 'escanear', t: 'Escanear credencial', icono: '📷',
    visible: auth.puedeEditarPlano || ['escaner', 'control-acceso', 'mesas-ingreso', 'mesas'].some(ve),
    ayuda: 'Un solo lector para todo: credenciales, pases de acompañante, personal de apoyo y entradas.',
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
