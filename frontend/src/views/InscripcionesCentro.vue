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
    k: 'todas', t: 'Todas', icono: '📋',
    visible: auth.puedeEditarPlano || ['inscripciones', 'control-ventas', 'reportes', 'direccion'].some(ve),
    ayuda: 'Todas las inscripciones de ferias y las reservas de mesas en un solo listado. Filtra por actividad, categoría, vendedor o estado, y vuelve a descargar o enviar el recibo y las credenciales.',
    vista: defineAsyncComponent(() => import('./InscripcionesGeneral.vue')),
  },
  {
    k: 'ventas', t: 'Rendición de cuentas', icono: '🧾',
    visible: auth.puedeEditarPlano || ve('control-ventas') || ve('reportes') || ve('direccion'),
    ayuda: 'Solo ferias: cada venta con los datos del cliente, de la compra y del cobro, lo vendido, lo cobrado y lo que falta rendir. Toca una fila para ver la ficha o adjuntar el comprobante.',
    vista: defineAsyncComponent(() => import('./ControlVentas.vue')),
  },
  {
    k: 'extras', t: 'Personas extra', icono: '➕', visible: auth.puedeEditarPlano || ve('responsables-extra'),
    ayuda: 'Ayudantes que se sumaron a un puesto pagando su credencial adicional. Se agregan desde la ficha de cada venta.',
    vista: defineAsyncComponent(() => import('./ResponsablesExtra.vue')),
  },
  {
    k: 'anulaciones', t: 'Anulaciones', icono: '🗂️', visible: ve('inscripciones'),
    vista: defineAsyncComponent(() => import('./Inscripciones.vue')),
  },
]);

const pestanaActual = computed(() => {
  const visibles = pestanas.value.filter((p) => p.visible !== false);
  return (visibles.find((p) => p.k === route.query.tab) || visibles[0])?.k;
});
const conSelector = computed(() => pestanaActual.value === 'ventas' || pestanaActual.value === 'extras');
</script>

<template>
  <VistaPestanas modulo="inscripciones" :pestanas="pestanas">
    <template v-if="conSelector" #extra><SelectorActividad solo-ferias /></template>
  </VistaPestanas>
</template>
