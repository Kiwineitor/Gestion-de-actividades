<script setup>
import { ref, computed, watch, defineAsyncComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useSolicitudesStore } from '../stores/solicitudes';
import { usePermisosStore } from '../stores/permisos';
import { useAuthStore } from '../stores/auth';
import { aModulo } from '../modulos';

const Interesados = defineAsyncComponent(() => import('./Interesados.vue'));
const EntradasPorValidar = defineAsyncComponent(() => import('../components/EntradasPorValidar.vue'));

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const permisos = usePermisosStore();
const solicitudes = useSolicitudesStore();

const veEspacios = computed(() => auth.puedeEditarPlano || permisos.puedeVer('interesados'));
const veEntradas = computed(() => auth.puedeEditarPlano || permisos.puedeVer('interesados') || permisos.puedeVer('mesas'));
const inicial = route.query.tab === 'entradas' || !veEspacios.value ? 'entradas' : 'ferias';
const pestana = ref(inicial);

watch(pestana, (p) => {
  if (route.query.m === 'solicitudes' && route.query.tab !== p) router.replace({ query: { ...route.query, tab: p } });
});
watch(() => route.query.tab, (t) => {
  if (route.query.m === 'solicitudes' && (t === 'ferias' || t === 'entradas')) pestana.value = t;
});
</script>

<template>
  <div class="solicitudes">
    <nav class="pestanas">
      <button v-if="veEspacios" :class="{ on: pestana === 'ferias' }" @click="pestana = 'ferias'">
        Espacios <span v-if="solicitudes.nuevosFeria" class="contador">{{ solicitudes.nuevosFeria }}</span>
      </button>
      <button v-if="veEntradas" :class="{ on: pestana === 'entradas' }" @click="pestana = 'entradas'">
        Entradas <span v-if="solicitudes.nuevasEntradas" class="contador">{{ solicitudes.nuevasEntradas }}</span>
      </button>
      <span class="crecer"></span>
      <router-link :to="aModulo('cobros', { tab: 'validar' })" class="btn btn-sm btn-fantasma">Ver todo en Ventas y cobros →</router-link>
    </nav>
    <Interesados v-if="pestana === 'ferias' && veEspacios" />
    <EntradasPorValidar v-else-if="veEntradas" />
  </div>
</template>

<style scoped>
.solicitudes { display: flex; flex-direction: column; gap: 1rem; }
.pestanas { display: flex; align-items: center; gap: 0.3rem; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.pestanas button { border: none; background: none; color: var(--muted); font: inherit; font-weight: 700; padding: 0.55rem 0.8rem; border-bottom: 2px solid transparent; cursor: pointer; display: inline-flex; gap: 0.4rem; align-items: center; }
.pestanas button.on { color: var(--text); border-bottom-color: var(--acento); }
.contador { background: var(--danger, #dc2626); color: #fff; border-radius: 999px; font-size: 0.72rem; padding: 0 0.45rem; min-width: 1.2rem; text-align: center; }
.crecer { flex: 1; }
</style>
