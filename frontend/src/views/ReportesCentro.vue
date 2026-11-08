<script setup>
import { computed, defineAsyncComponent } from 'vue';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import VistaPestanas from '../components/VistaPestanas.vue';
import SelectorActividad from '../components/SelectorActividad.vue';

const auth = useAuthStore();
const permisos = usePermisosStore();
const ve = (p) => permisos.puedeVer(p);

const deFeria = computed(() => [
  {
    k: 'resumen', t: 'Resumen', icono: '📊', visible: ve('direccion'),
    ayuda: 'Cuánto se vendió, cuánto se cobró, ocupación y avance de la actividad elegida.',
    vista: defineAsyncComponent(() => import('./Direccion.vue')),
  },
  {
    k: 'usuarios', t: 'Por usuario', icono: '👥', visible: auth.puedeEditarPlano || ve('control-ventas'),
    ayuda: 'Cuánto vendió y aprobó cada usuario, cuánto cobró y cuánto le falta rendir.',
    vista: defineAsyncComponent(() => import('../components/control-ventas/CobrosDashboard.vue')),
  },
  {
    k: 'responsables', t: 'Responsables', icono: '🪪', visible: ve('reporte-responsables'),
    ayuda: 'Personas registradas por puesto: con o sin foto y con o sin credencial impresa.',
    vista: defineAsyncComponent(() => import('./ReporteResponsables.vue')),
  },
  {
    k: 'descargas', t: 'Descargas', icono: '⬇️', visible: ve('reportes'),
    vista: defineAsyncComponent(() => import('./Reportes.vue')),
  },
]);

const pestanas = deFeria;
</script>

<template>
  <VistaPestanas modulo="reportes" :pestanas="pestanas">
    <template #extra><SelectorActividad /></template>
  </VistaPestanas>
</template>
