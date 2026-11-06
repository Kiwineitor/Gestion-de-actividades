<script setup>
import { ref, computed } from 'vue';
import { url as urlApi } from '../config.js';

const props = defineProps({
  mesas: { type: Array, default: () => [] },
  elementos: { type: Array, default: () => [] },
  planoUrl: { type: String, default: null },
  aspecto: { type: Number, default: 0.62 },
  editable: { type: Boolean, default: false },
  seleccionada: { type: [Number, String, Array], default: null },
  elementoSel: { type: String, default: null },
  gestion: { type: Boolean, default: false },
  rejilla: { type: Boolean, default: false },
  resaltadas: { type: Set, default: () => new Set() },
  ocupada: { type: [Number, String], default: null },
});
const emit = defineEmits(['elegir', 'mover', 'soltar', 'elegir-elemento', 'cambiar-elemento', 'vacio']);

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

function estiloElemento(e) {
  return {
    left: `${e.x * 100}%`,
    top: `${e.y * 100}%`,
    width: `${e.ancho * 100}%`,
    height: `${e.alto * 100}%`,
    transform: e.rotacion ? `rotate(${e.rotacion}deg)` : null,
    '--el-color': e.color || (e.tipo === 'zona' ? '#e9e4ff' : '#ffffff'),
    '--el-fondo': e.fondo || '#7c5cff',
    fontSize: `${(e.tamano || 0.025) * 100}cqi`,
  };
}

function posicion(e) {
  const r = lienzo.value.getBoundingClientRect();
  return { x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height };
}

const acotar = (v, min, max) => Math.min(max, Math.max(min, v));
const PASO = 0.025;
const iman = (v, paso) => (props.rejilla ? Math.round(v / paso) * paso : v);
const imanX = (v) => iman(v, PASO);
const imanY = (v) => iman(v, PASO / props.aspecto);

function bajar(e, m) {
  if (!props.editable) return;
  e.currentTarget.setPointerCapture(e.pointerId);
  arrastre = { tipo: 'mesa', mesa: m, x0: e.clientX, y0: e.clientY, movido: false };
}

function bajarElemento(e, el, modo) {
  if (!props.editable) return;
  e.stopPropagation();
  e.currentTarget.setPointerCapture(e.pointerId);
  emit('elegir-elemento', el);
  const p = posicion(e);
  arrastre = { tipo: 'elemento', modo, el, x0: e.clientX, y0: e.clientY, p0: p, base: { x: el.x, y: el.y, ancho: el.ancho, alto: el.alto }, movido: false };
}

function mover(e) {
  if (!arrastre) return;
  if (Math.abs(e.clientX - arrastre.x0) + Math.abs(e.clientY - arrastre.y0) > 4) arrastre.movido = true;
  if (!arrastre.movido) return;
  const p = posicion(e);
  if (arrastre.tipo === 'mesa') {
    emit('mover', arrastre.mesa, acotar(imanX(p.x), 0, 1), acotar(imanY(p.y), 0, 1));
    return;
  }
  const b = arrastre.base;
  const dx = p.x - arrastre.p0.x;
  const dy = p.y - arrastre.p0.y;
  const r = (v) => Math.round(v * 1000) / 1000;
  if (arrastre.modo === 'mover') {
    emit('cambiar-elemento', arrastre.el, { x: r(acotar(imanX(b.x + dx), 0, 1 - b.ancho)), y: r(acotar(imanY(b.y + dy), 0, 1 - b.alto)) });
  } else {
    emit('cambiar-elemento', arrastre.el, { ancho: r(acotar(imanX(b.ancho + dx), 0.02, 1 - b.x)), alto: r(acotar(imanY(b.alto + dy), 0.02, 1 - b.y)) });
  }
}

function subir(m) {
  if (!arrastre) return;
  if (arrastre.tipo === 'mesa') {
    if (arrastre.movido) emit('soltar', arrastre.mesa);
    else emit('elegir', m);
  }
  arrastre = null;
}

function tocar(m) {
  if (!props.editable) emit('elegir', m);
}

function tocarFondo(e) {
  if (e.target === lienzo.value || e.target.tagName === 'IMG') emit('vacio');
}
</script>

<template>
  <div ref="lienzo" class="plano-mesas" :class="{ editable, gestion, rejilla, 'sin-plano': !planoUrl }"
       :style="{ aspectRatio: `${1 / aspecto}` }" @pointerdown="tocarFondo">
    <img v-if="planoUrl" :src="urlApi(planoUrl)" alt="Plano del local" draggable="false" />
    <div v-if="rejilla" class="capa-rejilla"></div>
    <div v-for="el in elementos" :key="el.id" class="elemento" :class="[`tipo-${el.tipo}`, { sel: elementoSel === el.id }]"
         :style="estiloElemento(el)"
         @pointerdown="bajarElemento($event, el, 'mover')" @pointermove="mover" @pointerup="subir()" @pointercancel="subir()">
      <span class="el-texto">{{ el.texto }}</span>
      <span v-if="editable && elementoSel === el.id" class="asa" title="Arrastra para cambiar el tamaño"
            @pointerdown="bajarElemento($event, el, 'tamano')" @pointermove="mover" @pointerup="subir()" @pointercancel="subir()"></span>
    </div>
    <button v-for="m in visibles" :key="m.id ?? m.clave" type="button" class="mesa"
            :class="[`forma-${m.forma || 'circulo'}`, `est-${m.estado || 'L'}`, { sel: Array.isArray(seleccionada) ? seleccionada.includes(m.id) : seleccionada != null && (m.id ?? m.clave) === seleccionada, liberada: resaltadas.has(m.id), cargando: ocupada === m.id }]"
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
  background-image: linear-gradient(rgba(255,255,255,.06) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.06) 1px, transparent 1px);
  background-size: 5% 5cqi;
}
.capa-rejilla {
  position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(255,255,255,.28) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.28) 1px, transparent 1px),
    linear-gradient(rgba(255,255,255,.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.1) 1px, transparent 1px);
  background-size: 5cqi 5cqi, 5cqi 5cqi, 2.5cqi 2.5cqi, 2.5cqi 2.5cqi;
  mix-blend-mode: difference;
}
.plano-mesas img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: fill; pointer-events: none; }
.editable { touch-action: none; }
.elemento {
  position: absolute; display: grid; place-items: center; text-align: center; box-sizing: border-box;
  color: var(--el-color); font-weight: 800; letter-spacing: 0.12em; text-transform: uppercase; line-height: 1.1;
  transform-origin: center; pointer-events: none; padding: 0.2em;
}
.elemento.tipo-zona {
  background: color-mix(in srgb, var(--el-fondo) 22%, transparent);
  border: 2px solid color-mix(in srgb, var(--el-fondo) 80%, transparent); border-radius: 0.6em;
}
.el-texto { overflow: hidden; word-break: break-word; }
.editable .elemento { pointer-events: auto; cursor: move; }
.editable .elemento:hover { outline: 1px dashed rgba(255,255,255,.6); outline-offset: 2px; }
.elemento.sel { outline: 2px solid #fff; outline-offset: 3px; z-index: 3; }
.asa {
  position: absolute; right: -7px; bottom: -7px; width: 14px; height: 14px; border-radius: 4px;
  background: #fff; border: 2px solid #7c5cff; cursor: nwse-resize; pointer-events: auto;
}
.mesa {
  position: absolute; transform: translate(-50%, -50%); aspect-ratio: 1; padding: 0; z-index: 2;
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
.sel { box-shadow: 0 0 0 4px #fff, 0 0 0 7px var(--zona); z-index: 4; }
.editable .mesa { cursor: grab; }
.num { pointer-events: none; }
@keyframes proceso { 50% { opacity: 0.55; } }
@keyframes liberada {
  0% { box-shadow: 0 0 0 0 rgba(63, 209, 143, 0.9); }
  100% { box-shadow: 0 0 0 14px rgba(63, 209, 143, 0); }
}
@media (prefers-reduced-motion: reduce) { .est-P, .liberada { animation: none; } }
</style>
