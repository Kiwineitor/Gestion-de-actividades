<script setup>
import { computed, defineAsyncComponent, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { usePermisosStore } from '../stores/permisos';

const PESTANAS = [
  { k: 'resumen', t: 'Resumen', pantalla: 'direccion', vista: defineAsyncComponent(() => import('./Direccion.vue')) },
  { k: 'cobros', t: 'Cobros', pantalla: 'control-ventas', vista: defineAsyncComponent(() => import('../components/control-ventas/CobrosDashboard.vue')) },
  { k: 'reportes', t: 'Reportes', pantalla: 'reportes', vista: defineAsyncComponent(() => import('./Reportes.vue')) },
];

const route = useRoute();
const router = useRouter();
const permisos = usePermisosStore();

const visibles = computed(() => PESTANAS.filter((p) => permisos.puedeVer(p.pantalla)));
const activa = computed(() => visibles.value.find((p) => p.k === route.query.tab) || visibles.value[0] || null);

function ir(k) {
  router.replace({ query: { ...route.query, tab: k } });
}

watch(visibles, (v) => {
  if (route.query.m === 'dashboard' && v.length && !v.some((p) => p.k === route.query.tab)) ir(v[0].k);
}, { immediate: true });
</script>

<template>
  <div class="dashboard">
    <nav v-if="visibles.length > 1" class="pestanas" role="tablist">
      <button v-for="p in visibles" :key="p.k" role="tab" class="pestana"
              :class="{ activa: activa?.k === p.k }" :aria-selected="activa?.k === p.k" @click="ir(p.k)">
        {{ p.t }}
      </button>
    </nav>
    <KeepAlive>
      <component :is="activa.vista" v-if="activa" :key="activa.k" />
    </KeepAlive>
    <p v-if="!activa" class="vacio">No tienes acceso a ningún tablero.</p>
  </div>
</template>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 1rem; }
.pestanas { display: flex; gap: 0.3rem; border-bottom: 1px solid var(--border); overflow-x: auto; }
.pestana {
  border: none; background: none; color: var(--muted); font: inherit; font-weight: 700;
  padding: 0.6rem 0.9rem; border-bottom: 2px solid transparent; cursor: pointer; white-space: nowrap;
}
.pestana.activa { color: var(--text); border-bottom-color: var(--acento); }
</style>
