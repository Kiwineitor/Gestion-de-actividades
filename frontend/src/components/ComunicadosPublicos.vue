<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { url as urlApi } from '../config.js';
import { alCambiarPublico } from '../ui/canalPublico';

const CLAVE = 'pub.comunicados.cerrados';
const lista = ref([]);
const cerrados = ref(new Set(leer()));
let soltar = null;
let sondeo = null;

function leer() {
  try { return JSON.parse(sessionStorage.getItem(CLAVE) || '[]'); } catch { return []; }
}

const visibles = computed(() => lista.value.filter((a) => !cerrados.value.has(a.id)).slice(0, 2));

async function cargar() {
  try {
    const r = await fetch(urlApi('/api/publico/anuncios'));
    if (r.ok) lista.value = await r.json();
  } catch { }
}

function cerrar(a) {
  const s = new Set(cerrados.value).add(a.id);
  cerrados.value = s;
  try { sessionStorage.setItem(CLAVE, JSON.stringify([...s])); } catch { }
}

function restante(a) {
  if (!a.vigenteHasta) return '';
  const ms = new Date(a.vigenteHasta).getTime() - Date.now();
  if (ms <= 0) return '';
  const h = Math.floor(ms / 3600000);
  if (h >= 48) return `Hasta el ${new Date(a.vigenteHasta).toLocaleDateString('es-BO', { day: 'numeric', month: 'long' })}`;
  if (h >= 1) return `Quedan ${h} h`;
  return `Quedan ${Math.max(1, Math.round(ms / 60000))} min`;
}

onMounted(() => {
  cargar();
  soltar = alCambiarPublico((m) => { if (m?.tipo === 'anuncios') cargar(); }, { reconectado: cargar });
  sondeo = setInterval(cargar, 120000);
});
onUnmounted(() => { soltar?.(); clearInterval(sondeo); });
</script>

<template>
  <TransitionGroup v-if="visibles.length" tag="div" name="comunicado" class="comunicados" aria-live="polite">
    <article v-for="a in visibles" :key="a.id" class="comunicado" :class="`n-${a.nivel}`">
      <span class="ico" aria-hidden="true">{{ a.nivel === 'urgente' ? '🚨' : a.nivel === 'aviso' ? '📢' : '✨' }}</span>
      <div class="txt">
        <b v-if="a.titulo">{{ a.titulo }}</b>
        <p>{{ a.mensaje }}</p>
        <div class="pie">
          <a v-if="a.enlace" :href="a.enlace" target="_blank" rel="noopener" class="ver">Ver más →</a>
          <small v-if="restante(a)">{{ restante(a) }}</small>
        </div>
      </div>
      <button type="button" class="cerrar" aria-label="Cerrar anuncio" @click="cerrar(a)">✕</button>
    </article>
  </TransitionGroup>
</template>

<style scoped>
.comunicados {
  position: fixed; left: max(12px, env(safe-area-inset-left, 0px)); bottom: max(12px, env(safe-area-inset-bottom, 0px));
  z-index: 80; display: flex; flex-direction: column; gap: 8px; width: min(380px, calc(100vw - 24px));
}
.comunicado {
  --c: var(--pub-acento, #6366f1);
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 10px; align-items: start;
  padding: 12px 12px 12px 14px; border-radius: 14px; color: var(--pub-texto, #fff);
  background: color-mix(in srgb, var(--c) 22%, var(--pub-panel, #151a2e)); border: 1px solid color-mix(in srgb, var(--c) 55%, transparent);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35); backdrop-filter: blur(6px);
}
.comunicado.n-aviso { --c: var(--pub-alerta, #f59e0b); }
.comunicado.n-urgente { --c: var(--pub-error, #ef4444); }
.ico { font-size: 1.3rem; }
.txt { display: flex; flex-direction: column; gap: 3px; }
.txt b { font-size: 0.95rem; }
.txt p { margin: 0; font-size: 0.86rem; color: var(--pub-suave, #cbd5e1); line-height: 1.4; overflow-wrap: anywhere; }
.pie { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ver { color: var(--pub-texto, #fff); font-weight: 700; font-size: 0.85rem; }
.pie small { color: var(--pub-suave, #cbd5e1); font-size: 0.75rem; }
.cerrar { border: 0; background: none; color: var(--pub-suave, #cbd5e1); font-size: 1rem; cursor: pointer; padding: 2px 4px; }
.comunicado-enter-active, .comunicado-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.comunicado-enter-from, .comunicado-leave-to { opacity: 0; transform: translateY(12px); }
</style>
