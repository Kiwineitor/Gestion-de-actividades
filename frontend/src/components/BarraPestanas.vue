<script setup>
import { computed, ref, nextTick, watch } from 'vue';
import { useRouter } from 'vue-router';
import MenuAcciones from './MenuAcciones.vue';
import { usePestanasStore } from '../stores/pestanas';
import { aModulo } from '../modulos';

const props = defineProps({
  iconos: { type: Object, default: () => ({}) },
  contadores: { type: Object, default: () => ({}) },
});

const router = useRouter();
const pestanas = usePestanasStore();
const tira = ref(null);

const acciones = computed(() => [
  { txt: 'Cerrar esta pestaña', deshabilitado: !pestanas.activa, accion: () => cerrar(pestanas.activa) },
  { txt: 'Cerrar las demás', deshabilitado: pestanas.lista.length < 2, accion: () => pestanas.cerrarOtras(pestanas.activa) },
  { separador: true },
  { txt: 'Cerrar todas (queda Inicio)', peligro: true, deshabilitado: !pestanas.lista.length, accion: cerrarTodas },
]);

function ir(p) {
  router.push(aModulo(p.m, p.query));
}

async function cerrar(m) {
  if (!m) return;
  if (pestanas.activa === m) {
    const i = pestanas.lista.findIndex((p) => p.m === m);
    const resto = pestanas.lista.filter((p) => p.m !== m);
    const siguiente = resto[Math.min(i, resto.length - 1)];
    await router.push(siguiente ? aModulo(siguiente.m, siguiente.query) : aModulo('inicio'));
  }
  pestanas.cerrar(m);
}

async function cerrarTodas() {
  await router.push(aModulo('inicio'));
  pestanas.cerrarOtras('inicio');
}

watch(() => pestanas.activa, async () => {
  await nextTick();
  tira.value?.querySelector('.pestana.on')?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
});
</script>

<template>
  <div v-if="pestanas.lista.length" class="barra-pestanas">
    <div ref="tira" class="tira" role="tablist">
      <div v-for="p in pestanas.lista" :key="p.m" class="pestana" :class="{ on: p.m === pestanas.activa }" role="tab"
           :aria-selected="p.m === pestanas.activa" :title="p.titulo" tabindex="0"
           @click="ir(p)" @keydown.enter="ir(p)" @auxclick.prevent="$event.button === 1 && cerrar(p.m)">
        <span class="ico">{{ props.iconos[p.m] || '📄' }}</span>
        <span class="txt">{{ p.titulo }}</span>
        <span v-if="props.contadores[p.m]" class="contador">{{ props.contadores[p.m] }}</span>
        <button type="button" class="x" :aria-label="`Cerrar ${p.titulo}`" @click.stop="cerrar(p.m)">✕</button>
      </div>
    </div>
    <MenuAcciones :acciones="acciones" etiqueta="Opciones de pestañas" />
  </div>
</template>

<style scoped>
.barra-pestanas {
  display: flex; align-items: center; gap: 0.3rem; padding: 0.3rem calc(0.8rem + var(--safe-right)) 0 calc(0.8rem + var(--safe-left));
  background: var(--panel); border-bottom: 1px solid var(--border); position: sticky; top: 0; z-index: 9;
}
.tira { flex: 1; min-width: 0; display: flex; gap: 2px; overflow-x: auto; scrollbar-width: thin; }
.pestana {
  display: inline-flex; align-items: center; gap: 0.4rem; max-width: 220px; padding: 0.4rem 0.4rem 0.45rem 0.7rem;
  border: 1px solid transparent; border-bottom: 0; border-radius: var(--radio-sm) var(--radio-sm) 0 0;
  color: var(--muted); font-size: 0.85rem; font-weight: 600; cursor: pointer; white-space: nowrap; user-select: none; flex: none;
}
.pestana:hover { background: var(--panel-2); color: var(--text); }
.pestana.on { background: var(--bg); color: var(--text); border-color: var(--border); box-shadow: inset 0 2px 0 var(--acento); }
.pestana:focus-visible { outline: 2px solid var(--acento); outline-offset: -2px; }
.txt { overflow: hidden; text-overflow: ellipsis; }
.contador { background: var(--danger, #dc2626); color: #fff; border-radius: 999px; font-size: 0.68rem; padding: 0 0.4rem; }
.x {
  border: 0; background: none; color: var(--muted); width: 20px; height: 20px; border-radius: 4px; cursor: pointer;
  font-size: 0.7rem; display: grid; place-items: center; opacity: 0.6;
}
.pestana:hover .x, .pestana.on .x { opacity: 1; }
.x:hover { background: var(--border); color: var(--text); }
@media (max-width: 820px) { .barra-pestanas { display: none; } }
</style>
