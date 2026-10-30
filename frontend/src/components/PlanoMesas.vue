<script setup>
import { ref, computed } from 'vue';
import { url as urlApi } from '../config.js';

const props = defineProps({
  mesas: { type: Array, default: () => [] },
  planoUrl: { type: String, default: null },
  aspecto: { type: Number, default: 0.62 },
  editable: { type: Boolean, default: false },
  seleccionada: { type: [Number, String], default: null },
  gestion: { type: Boolean, default: false },
  resaltadas: { type: Set, default: () => new Set() },
  ocupada: { type: [Number, String], default: null },
});
const emit = defineEmits(['elegir', 'mover', 'soltar']);

const lienzo = ref(null);
let arrastre = null;

const visibles = computed(() => props.mesas.filter((m) => m.mapaX != null && m.mapaY != null));

const ETIQUETA = { L: 'Libre', P: 'En proceso de compra', T: 'Bloqueada', R: props.gestion ? 'En revisión' : 'Reservada', O: 'Vendida', X: 'No disponible' };

function estiloMesa(m) {
  return {
    left: `${m.mapaX * 100}%`,
    top: `${m.mapaY * 100}%`,
    width: `${(m.tamano || 0.06) * 100}%`,
    '--zona': m.color || 'var(--mesa-zona, #6d5bd0)',
  };
}

function posicion(e) {
  const r = lienzo.value.getBoundingClientRect();
  return {
    x: Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)),
    y: Math.min(1, Math.max(0, (e.clientY - r.top) / r.height)),
  };
}

function bajar(e, m) {
  if (!props.editable) return;
  e.currentTarget.setPointerCapture(e.pointerId);
  arrastre = { mesa: m, x0: e.clientX, y0: e.clientY, movido: false };
}

function mover(e) {
  if (!arrastre) return;
  if (Math.abs(e.clientX - arrastre.x0) + Math.abs(e.clientY - arrastre.y0) > 4) arrastre.movido = true;
  if (!arrastre.movido) return;
  const p = posicion(e);
  emit('mover', arrastre.mesa, p.x, p.y);
}

function subir(m) {
  if (arrastre?.movido) emit('soltar', arrastre.mesa);
  else emit('elegir', m);
  arrastre = null;
}

function tocar(m) {
  if (!props.editable) emit('elegir', m);
}
</script>

<template>
  <div ref="lienzo" class="plano-mesas" :class="{ editable, gestion, 'sin-plano': !planoUrl }"
       :style="{ aspectRatio: `${1 / aspecto}` }">
    <img v-if="planoUrl" :src="urlApi(planoUrl)" alt="Plano del local" draggable="false" />
    <button v-for="m in visibles" :key="m.id ?? m.clave" type="button" class="mesa"
            :class="[`forma-${m.forma || 'circulo'}`, `est-${m.estado || 'L'}`, { sel: seleccionada != null && (m.id ?? m.clave) === seleccionada, liberada: resaltadas.has(m.id), cargando: ocupada === m.id }]"
            :style="estiloMesa(m)"
            :aria-label="`Mesa ${m.codigo}, ${ETIQUETA[m.estado || 'L']}`"
            :title="`Mesa ${m.codigo} · ${m.capacidad} personas · ${ETIQUETA[m.estado || 'L']}`"
            @pointerdown="bajar($event, m)" @pointermove="mover" @pointerup="subir(m)" @click="tocar(m)">
      <span class="num">{{ m.codigo }}</span>
    </button>
    <slot />
  </div>
</template>

<style scoped>
.plano-mesas {
  --m-libre: #1f9d63; --m-proceso: #2b8fd6; --m-reservada: #d98b0b; --m-revision: #7c5cff; --m-vendida: #d23b5a; --m-fuera: #8a90a6;
  position: relative; width: 100%; border-radius: 14px; overflow: hidden;
  background: var(--plano-fondo, #1b1f33); container-type: inline-size; user-select: none; touch-action: manipulation;
}
.plano-mesas.sin-plano {
  background-color: var(--plano-fondo, #1b1f33);
  background-image: linear-gradient(rgba(255,255,255,.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.05) 1px, transparent 1px);
  background-size: 5% calc(5% / 0.62);
}
.plano-mesas img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: fill; pointer-events: none; }
.editable { touch-action: none; }
.mesa {
  position: absolute; transform: translate(-50%, -50%); aspect-ratio: 1; padding: 0;
  display: grid; place-items: center; border: 3px solid var(--zona); cursor: pointer;
  background: var(--m-libre); color: #fff; font: 700 clamp(9px, 2.2cqi, 16px)/1 system-ui, sans-serif;
  box-shadow: 0 2px 8px rgba(0,0,0,.35); transition: transform .12s ease, box-shadow .12s ease;
}
.mesa:hover { transform: translate(-50%, -50%) scale(1.06); }
.mesa:focus-visible { outline: 3px solid #fff; outline-offset: 2px; }
.forma-circulo { border-radius: 50%; }
.forma-cuadrado { border-radius: 14%; }
.forma-rectangulo { aspect-ratio: 1.7; border-radius: 12%; }
.est-T, .est-R { background: var(--m-reservada); }
.plano-mesas:not(.editable) .est-R, .plano-mesas:not(.editable) .est-O, .plano-mesas:not(.editable) .est-X { cursor: not-allowed; }
.est-O { background: var(--m-vendida); }
.gestion .est-R { background: var(--m-revision); }
.est-X { background: var(--m-fuera); }
.est-P { background: var(--m-proceso); border-style: dashed; animation: proceso 1.6s ease-in-out infinite; }
.plano-mesas:not(.editable) .est-P { cursor: not-allowed; }
.liberada { animation: liberada 1.2s ease-out 3; box-shadow: 0 0 0 4px rgba(63, 209, 143, 0.85), 0 0 22px rgba(63, 209, 143, 0.7); z-index: 2; }
.cargando { opacity: 0.6; }
@keyframes proceso { 50% { opacity: 0.55; } }
@keyframes liberada {
  0% { box-shadow: 0 0 0 0 rgba(63, 209, 143, 0.9); }
  100% { box-shadow: 0 0 0 14px rgba(63, 209, 143, 0); }
}
.sel { box-shadow: 0 0 0 4px #fff, 0 0 0 7px var(--zona); z-index: 2; }
.editable .mesa { cursor: grab; }
.num { pointer-events: none; }
</style>
