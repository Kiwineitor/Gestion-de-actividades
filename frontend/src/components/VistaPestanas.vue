<script setup>
import { computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const props = defineProps({
  modulo: { type: String, required: true },
  pestanas: { type: Array, required: true },
  vacio: { type: String, default: 'No tienes acceso a ninguna de estas opciones.' },
});

const route = useRoute();
const router = useRouter();

const visibles = computed(() => props.pestanas.filter((p) => p.visible !== false));
const activa = computed(() => visibles.value.find((p) => p.k === route.query.tab) || visibles.value[0] || null);

function ir(k) {
  if (route.query.tab === k) return;
  router.replace({ query: { ...route.query, tab: k } });
}

watch(visibles, (v) => {
  if (route.query.m === props.modulo && v.length && !v.some((p) => p.k === route.query.tab)) ir(v[0].k);
}, { immediate: true });
</script>

<template>
  <div class="vista-pestanas" :class="{ llena: activa?.llena }">
    <div v-if="visibles.length > 1 || $slots.extra" class="barra">
      <nav v-if="visibles.length > 1" class="pestanas" role="tablist">
        <button v-for="p in visibles" :key="p.k" type="button" role="tab" class="pestana"
                :class="{ activa: activa?.k === p.k }" :aria-selected="activa?.k === p.k" @click="ir(p.k)">
          <span v-if="p.icono" aria-hidden="true">{{ p.icono }}</span>{{ p.t }}
          <span v-if="p.contador" class="contador">{{ p.contador }}</span>
        </button>
      </nav>
      <div v-if="$slots.extra" class="extra"><slot name="extra" /></div>
    </div>
    <p v-if="activa?.ayuda" class="ayuda">{{ activa.ayuda }}</p>
    <div class="cuerpo">
      <KeepAlive>
        <component :is="activa.vista" v-if="activa" :key="activa.k" v-bind="activa.props || {}" />
      </KeepAlive>
    </div>
    <p v-if="!activa" class="sin-acceso">{{ vacio }}</p>
  </div>
</template>

<style scoped>
.vista-pestanas { display: flex; flex-direction: column; gap: 0.9rem; min-height: 0; }
.vista-pestanas.llena { height: 100%; }
.cuerpo { min-height: 0; display: flex; flex-direction: column; }
.llena .cuerpo { flex: 1; }
.llena .cuerpo > * { flex: 1; min-height: 0; }
.barra { display: flex; align-items: flex-end; gap: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); flex-wrap: wrap-reverse; flex: none; }
.pestanas { display: flex; gap: 0.25rem; overflow-x: auto; scrollbar-width: none; flex: 1 1 auto; min-width: 0; }
.extra { margin-left: auto; padding-bottom: 0.4rem; max-width: 100%; }
.pestanas::-webkit-scrollbar { display: none; }
.pestana {
  display: inline-flex; align-items: center; gap: 0.4rem; border: none; background: none; color: var(--muted);
  font: inherit; font-weight: 700; font-size: 0.92rem; padding: 0.6rem 0.85rem; border-bottom: 2px solid transparent;
  cursor: pointer; white-space: nowrap;
}
.pestana:hover { color: var(--text); }
.pestana.activa { color: var(--text); border-bottom-color: var(--acento); }
.contador { background: var(--danger, #dc2626); color: #fff; border-radius: 999px; font-size: 0.7rem; padding: 0 0.4rem; }
.ayuda { margin: -0.3rem 0 0; color: var(--muted); font-size: 0.85rem; }
.sin-acceso { color: var(--muted); text-align: center; padding: 2rem 0; }
</style>
