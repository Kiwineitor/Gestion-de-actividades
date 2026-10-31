<script setup>
import { computed, defineAsyncComponent } from 'vue';
import { usePermisosStore } from '../stores/permisos';
import VistaPestanas from '../components/VistaPestanas.vue';

const permisos = usePermisosStore();
const ve = (p) => permisos.puedeVer(p);

const pestanas = computed(() => [
  {
    k: 'categorias', t: 'Categorías y precios', icono: '🏷️', visible: ve('categorias'),
    ayuda: 'Cada categoría define su precio, cuántos puestos hay, cuántas personas (credenciales) incluye cada puesto y cuánto cuesta una persona extra.',
    vista: defineAsyncComponent(() => import('./Categorias.vue')),
  },
  {
    k: 'puestos', t: 'Puestos y precio propio', icono: '🔢', visible: ve('puestos'),
    ayuda: 'Para un puesto que debe valer distinto a su categoría (por ejemplo, uno peor ubicado), ponle precio propio. Vacío = usa el precio de la categoría.',
    vista: defineAsyncComponent(() => import('./Puestos.vue')),
  },
]);
</script>

<template>
  <VistaPestanas modulo="categorias" :pestanas="pestanas" />
</template>
