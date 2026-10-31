<script setup>
import { computed, defineAsyncComponent } from 'vue';
import { useRoute } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import VistaPestanas from '../components/VistaPestanas.vue';
import SelectorActividad from '../components/SelectorActividad.vue';

const auth = useAuthStore();
const permisos = usePermisosStore();
const route = useRoute();
const ve = (p) => permisos.puedeVer(p);

const pestanas = computed(() => [
  {
    k: 'diseno', t: 'Diseño', icono: '🎨', visible: auth.puedeEditarPlano || ve('credenciales-diseno'), llena: true,
    vista: defineAsyncComponent(() => import('./DisenoCredenciales.vue')),
  },
  {
    k: 'impresion', t: 'Impresión masiva', icono: '🖨️', visible: auth.puedeEditarPlano || ve('impresion-masiva'),
    ayuda: 'Para reenviar el recibo o las credenciales de una sola venta o reserva, búscala en Inscripciones y pulsa «📲 Recibo y credenciales».',
    vista: defineAsyncComponent(() => import('./ImpresionMasiva.vue')),
  },
  {
    k: 'apoyo', t: 'Personal de apoyo', icono: '🤝', visible: ve('personal-apoyo') || ve('credenciales-apoyo'),
    vista: defineAsyncComponent(() => import('./ApoyoCredenciales.vue')),
  },
]);

const conSelector = computed(() => {
  const visibles = pestanas.value.filter((p) => p.visible !== false);
  const activa = visibles.find((p) => p.k === route.query.tab) || visibles[0];
  return activa?.k === 'impresion';
});
</script>

<template>
  <VistaPestanas modulo="credenciales" :pestanas="pestanas">
    <template v-if="conSelector" #extra><SelectorActividad /></template>
  </VistaPestanas>
</template>
