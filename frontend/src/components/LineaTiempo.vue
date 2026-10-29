<script setup>
import { computed, ref } from 'vue';

const props = defineProps({
  puntos: { type: Array, default: () => [] },
  formato: { type: Function, default: (v) => String(v) },
  alto: { type: Number, default: 180 },
});

const ANCHO = 600;
const MARGEN = { arriba: 10, derecha: 8, abajo: 22, izquierda: 8 };

const datos = computed(() => props.puntos.filter((p) => p && Number.isFinite(Number(p.y))));
const maximo = computed(() => Math.max(1, ...datos.value.map((p) => Number(p.y))));

const geo = computed(() => {
  const n = datos.value.length;
  const alto = props.alto;
  const util = { w: ANCHO - MARGEN.izquierda - MARGEN.derecha, h: alto - MARGEN.arriba - MARGEN.abajo };
  const x = (i) => (n <= 1 ? MARGEN.izquierda + util.w / 2
    : MARGEN.izquierda + (i / (n - 1)) * util.w);
  const y = (v) => MARGEN.arriba + util.h - (Number(v) / maximo.value) * util.h;
  return { x, y, util, alto };
});

const linea = computed(() => datos.value
  .map((p, i) => `${i === 0 ? 'M' : 'L'} ${geo.value.x(i).toFixed(1)} ${geo.value.y(p.y).toFixed(1)}`)
  .join(' '));

const area = computed(() => {
  if (!datos.value.length) return '';
  const base = geo.value.alto - MARGEN.abajo;
  const primera = geo.value.x(0).toFixed(1);
  const ultima = geo.value.x(datos.value.length - 1).toFixed(1);
  return `${linea.value} L ${ultima} ${base} L ${primera} ${base} Z`;
});

const activo = ref(-1);

function alMover(e) {
  if (!datos.value.length) return;
  const caja = e.currentTarget.getBoundingClientRect();
  const px = ((e.clientX - caja.left) / caja.width) * ANCHO;
  let mejor = 0;
  let dist = Infinity;
  for (let i = 0; i < datos.value.length; i++) {
    const d = Math.abs(geo.value.x(i) - px);
    if (d < dist) { dist = d; mejor = i; }
  }
  activo.value = mejor;
}

const punto = computed(() => (activo.value >= 0 ? datos.value[activo.value] : null));
const globoDerecha = computed(() => activo.value >= 0
  && geo.value.x(activo.value) > ANCHO * 0.6);
</script>

<template>
  <figure class="linea-tiempo">
    <svg v-if="datos.length" :viewBox="`0 0 ${ANCHO} ${alto}`" :style="{ height: alto + 'px' }"
         preserveAspectRatio="none" role="img"
         :aria-label="`Serie de ${datos.length} puntos, máximo ${formato(maximo)}`"
         @mousemove="alMover" @mouseleave="activo = -1">
      <line :x1="0" :x2="ANCHO" :y1="alto - 22" :y2="alto - 22" class="eje" />

      <path :d="area" class="area" />
      <path :d="linea" class="curva" />

      <circle v-if="datos.length > 1" :cx="geo.x(datos.length - 1)" :cy="geo.y(datos[datos.length - 1].y)"
              r="4.5" class="ultimo" />

      <template v-if="punto">
        <line :x1="geo.x(activo)" :x2="geo.x(activo)" :y1="10" :y2="alto - 22" class="guia" />
        <circle :cx="geo.x(activo)" :cy="geo.y(punto.y)" r="5" class="activo" />
      </template>
    </svg>
    <p v-else class="vacio">Todavía no hay ventas que dibujar.</p>

    <div v-if="punto" class="globo" :class="{ derecha: globoDerecha }"
         :style="{ left: (geo.x(activo) / ANCHO * 100) + '%' }">
      <strong>{{ formato(punto.y) }}</strong>
      <span>{{ punto.x }}</span>
    </div>
  </figure>
</template>

<style scoped>
.linea-tiempo { position: relative; margin: 0; }
svg { width: 100%; display: block; overflow: visible; }

.eje { stroke: color-mix(in srgb, var(--text) 18%, transparent); stroke-width: 1; }
.curva { fill: none; stroke: var(--acento); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.area { fill: color-mix(in srgb, var(--acento) 16%, transparent); stroke: none; }
.ultimo { fill: var(--acento); }
.activo { fill: var(--acento); stroke: var(--panel); stroke-width: 2; }
.guia { stroke: color-mix(in srgb, var(--text) 28%, transparent); stroke-width: 1; stroke-dasharray: 3 3; }

.globo {
  position: absolute; top: 0; transform: translateX(-50%);
  background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio-sm);
  box-shadow: var(--sombra-md); padding: 0.35rem 0.6rem;
  display: flex; flex-direction: column; gap: 0.1rem;
  font-size: 0.82rem; white-space: nowrap; pointer-events: none;
}
.globo.derecha { transform: translateX(-100%); }
.globo span { color: var(--muted); }
.vacio { margin: 0; padding: 1.5rem 0; text-align: center; color: var(--muted); font-size: 0.9rem; }
</style>
