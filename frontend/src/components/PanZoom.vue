<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue';

const props = defineProps({
  aspect: { type: Number, default: 2376 / 1836 },
  min: { type: Number, default: 0.6 },
  max: { type: Number, default: 10 },
  focus: { type: Object, default: () => ({ x: 0.45, y: 0.32, scale: 2.2 }) },
  contenido: { type: Object, default: null },
  selectMode: { type: Boolean, default: false },
  llenar: { type: Boolean, default: false },
  umbralDetalle: { type: Number, default: 0 },
  maxAncho: { type: Number, default: 0 },
});

const viewport = ref(null);
const world = ref(null);
const t = { x: 0, y: 0, scale: 1 };

const MS_NITIDEZ = 220;
let capaPromovida = false;
let temporizadorNitidez = null;

function promoverCapa() {
  const el = world.value;
  if (!el) return;
  if (!capaPromovida) {
    el.style.willChange = 'transform';
    capaPromovida = true;
  }
  clearTimeout(temporizadorNitidez);
  temporizadorNitidez = setTimeout(bajarCapa, MS_NITIDEZ);
}

function bajarCapa() {
  capaPromovida = false;
  if (world.value) world.value.style.willChange = 'auto';
}

let cuadroPendiente = 0;

function pintar() {
  if (cuadroPendiente) return;
  cuadroPendiente = requestAnimationFrame(() => {
    cuadroPendiente = 0;
    pintarYa();
  });
}

function pintarYa() {
  const el = world.value;
  if (!el) return;
  if (cuadroPendiente) { cancelAnimationFrame(cuadroPendiente); cuadroPendiente = 0; }
  el.style.transform = `translate(${t.x}px, ${t.y}px) scale(${t.scale})`;
}

let anchoPrevio = 0;

function anchoVisor() {
  return anchoPrevio || (viewport.value ? vpRect().width : 0);
}

function publicarAncho(ancho) {
  if (world.value && ancho) world.value.style.setProperty('--mundo', String(Math.round(ancho)));
}

function escalaMaxima() {
  const w = props.maxAncho ? anchoVisor() : 0;
  return w ? Math.max(props.max, props.maxAncho / w) : props.max;
}

const clamp = (v) => Math.min(escalaMaxima(), Math.max(props.min, v));

function vpRect() { return viewport.value.getBoundingClientRect(); }

let detalle = false;
function actualizarDetalle(anchoViewport) {
  if (!props.umbralDetalle) return;
  const visible = anchoViewport * t.scale >= props.umbralDetalle;
  if (visible === detalle) return;
  detalle = visible;
  world.value?.classList.toggle('detalle', visible);
}

function focusOn(nx, ny, scale) {
  const r = vpRect();
  const worldW = r.width;
  const worldH = r.width * props.aspect;
  t.scale = clamp(scale);
  t.x = r.width / 2 - nx * worldW * t.scale;
  t.y = r.height / 2 - ny * worldH * t.scale;
  actualizarDetalle(r.width);
  pintarYa();
}

function reset() {
  const z = props.contenido;
  if (z) {
    const r = vpRect();
    const mundoW = r.width, mundoH = r.width * props.aspect;
    const margen = 1.04;
    const escala = Math.min(
      r.width / ((z.x1 - z.x0) * mundoW * margen),
      r.height / ((z.y1 - z.y0) * mundoH * margen),
    );
    focusOn((z.x0 + z.x1) / 2, (z.y0 + z.y1) / 2, escala);
    return;
  }
  focusOn(props.focus.x, props.focus.y, props.focus.scale);
}

function zoomAt(cx, cy, factor) {
  const r = vpRect();
  const px = cx - r.left, py = cy - r.top;
  const wx = (px - t.x) / t.scale, wy = (py - t.y) / t.scale;
  const ns = clamp(t.scale * factor);
  t.scale = ns;
  t.x = px - wx * ns;
  t.y = py - wy * ns;
  actualizarDetalle(r.width);
  pintar();
}

function onWheel(e) {
  e.preventDefault();
  promoverCapa();
  zoomAt(e.clientX, e.clientY, e.deltaY < 0 ? 1.15 : 1 / 1.15);
}

const pointers = new Map();
let panning = false, last = { x: 0, y: 0 };
let pinch = null;

function dist(a, b) { return Math.hypot(a.x - b.x, a.y - b.y); }
function mid(a, b) { return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }; }

function onDown(e) {
  if (props.selectMode) return;
  viewport.value.setPointerCapture(e.pointerId);
  pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (pointers.size === 2) {
    const [a, b] = [...pointers.values()];
    pinch = { dist: dist(a, b), ...mid(a, b) };
    panning = false;
  } else {
    panning = true; last = { x: e.clientX, y: e.clientY };
  }
}
function onMove(e) {
  if (!pointers.has(e.pointerId)) return;
  promoverCapa();
  pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
  if (pointers.size === 2 && pinch) {
    const [a, b] = [...pointers.values()];
    const d = dist(a, b), m = mid(a, b);
    if (pinch.dist > 0) zoomAt(m.x, m.y, d / pinch.dist);
    pinch = { dist: d, ...m };
  } else if (panning) {
    t.x += e.clientX - last.x; t.y += e.clientY - last.y;
    last = { x: e.clientX, y: e.clientY };
    pintar();
  }
}
function onUp(e) {
  pointers.delete(e.pointerId);
  if (pointers.size < 2) pinch = null;
  if (pointers.size === 0) panning = false;
}

let ro;

function alRedimensionar() {
  const r = vpRect();
  if (!r.width) return;
  if (!anchoPrevio) { anchoPrevio = r.width; return; }
  if (Math.abs(r.width - anchoPrevio) < 0.5) return;

  const mundoAncho = anchoPrevio;
  const mundoAlto = anchoPrevio * props.aspect;
  const nx = (r.width / 2 - t.x) / (mundoAncho * t.scale);
  const ny = (r.height / 2 - t.y) / (mundoAlto * t.scale);
  anchoPrevio = r.width;
  publicarAncho(anchoPrevio);
  focusOn(nx, ny, t.scale);
}

onMounted(() => {
  anchoPrevio = vpRect().width;
  publicarAncho(anchoPrevio);
  reset();
  ro = new ResizeObserver(alRedimensionar);
  ro.observe(viewport.value);
});
onBeforeUnmount(() => {
  if (ro) ro.disconnect();
  clearTimeout(temporizadorNitidez);
});

defineExpose({ focusOn, reset, zoomAt });
</script>

<template>
  <div
    class="viewport"
    :class="{ llenar }"
    ref="viewport"
    @wheel="onWheel"
    @pointerdown="onDown"
    @pointermove="onMove"
    @pointerup="onUp"
    @pointercancel="onUp"
  >
    <div class="world" ref="world">
      <slot />
    </div>
    <div class="controls">
      <button @pointerdown.stop @click="zoomAt(vpRect().left + vpRect().width / 2, vpRect().top + vpRect().height / 2, 1.3)">+</button>
      <button @pointerdown.stop @click="zoomAt(vpRect().left + vpRect().width / 2, vpRect().top + vpRect().height / 2, 1 / 1.3)">−</button>
      <button @pointerdown.stop title="Ajustar" @click="reset">⤢</button>
    </div>
  </div>
</template>

<style scoped>
.viewport {
  position: relative; width: 100%; height: 78vh; overflow: hidden;
  background: #f8fafc; border: 1px solid var(--border); border-radius: 10px;
  touch-action: none; cursor: grab;
  user-select: none; -webkit-user-select: none;
  -webkit-tap-highlight-color: transparent;
}
.viewport:active { cursor: grabbing; }
.viewport.llenar { height: auto; flex: 1; min-height: 0; border-radius: 0; border-left: none; border-right: none; }
.world { position: absolute; top: 0; left: 0; width: 100%; transform-origin: 0 0; }
.controls {
  position: absolute; right: 10px; bottom: 10px; display: flex; flex-direction: column; gap: 4px;
}
.controls button {
  width: 38px; height: 38px; border-radius: 8px; border: 1px solid var(--border);
  background: #fff; font-size: 1.1rem; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

@media (max-width: 820px) {
  .viewport { height: calc(78vh - var(--tabbar-h)); }
  .controls button { width: 46px; height: 46px; font-size: 1.3rem; }
}
</style>
