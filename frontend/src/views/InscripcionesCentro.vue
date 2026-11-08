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
    k: 'todas', t: 'Todas las ventas', icono: '📋',
    visible: auth.puedeEditarPlano || ['inscripciones', 'control-ventas', 'reportes', 'direccion'].some(ve),
    ayuda: 'Cada venta en un solo listado: en línea (aprobadas) y en persona. Filtra por actividad, categoría, quién atendió o estado; vuelve a enviar el recibo y las credenciales, o anula una venta indicando el motivo.',
    vista: defineAsyncComponent(() => import('./InscripcionesGeneral.vue')),
  },
  {
    k: 'ventas', t: 'Rendición de cuentas', icono: '🧾',
    visible: auth.puedeEditarPlano || ve('control-ventas') || ve('reportes') || ve('direccion'),
    ayuda: 'Lo vendido, lo cobrado y lo que falta cobrar de la actividad elegida arriba. En línea: el cliente adjunta su comprobante. En persona: el personal confirma que recibió el dinero (efectivo) o adjunta el comprobante. Toca una fila para ver la ficha.',
    vista: defineAsyncComponent(() => import('./ControlVentas.vue')),
  },
  {
    k: 'extras', t: 'Personas extra', icono: '➕', visible: auth.puedeEditarPlano || ve('responsables-extra'),
    ayuda: 'Ayudantes que se sumaron a un puesto pagando su credencial adicional. Se agregan desde la ficha de cada venta.',
    vista: defineAsyncComponent(() => import('./ResponsablesExtra.vue')),
  },
  {
    k: 'anulaciones', t: 'Anulaciones', icono: '🗂️', visible: auth.puedeEditarPlano || ve('inscripciones'),
    ayuda: 'Ventas anuladas y solicitudes o compras rechazadas, con quién lo hizo, cuándo y el motivo.',
    vista: defineAsyncComponent(() => import('./Anulaciones.vue')),
  },
]);

const pestanaActual = computed(() => {
  const visibles = pestanas.value.filter((p) => p.visible !== false);
  return (visibles.find((p) => p.k === route.query.tab) || visibles[0])?.k;
});
const conSelector = computed(() => ['ventas', 'extras', 'anulaciones'].includes(pestanaActual.value));
</script>

<template>
  <VistaPestanas modulo="inscripciones" :pestanas="pestanas">
    <template v-if="conSelector" #extra><SelectorActividad solo-ferias /></template>
  </VistaPestanas>
</template>
