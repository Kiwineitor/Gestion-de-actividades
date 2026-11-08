<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import PromoTarjeta from './PromoTarjeta.vue';
import { usarPromos, promosDe, contarVista } from '../ui/promosPublicas';

const props = defineProps({
  edicion: { type: [String, Number], default: null },
  compacto: { type: Boolean, default: false },
  intervalo: { type: Number, default: 6500 },
});

const lista = usarPromos();
const items = computed(() => promosDe(lista.value, { formatos: ['DESTACADO', 'COMBO'], edicion: props.edicion }));
const actual = ref(0);
const raiz = ref(null);
const enPantalla = ref(false);
const pausado = ref(false);
const reducido = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
const progreso = ref(0);
let reloj = null;
let observador = null;
let inicioX = null;
let arrastro = false;
const PASO = 100;

const activo = computed(() => items.value[actual.value] || null);

function ir(i) {
  const n = items.value.length;
  if (!n) return;
  actual.value = ((i % n) + n) % n;
  reiniciar();
}

function reiniciar() {
  clearInterval(reloj);
  progreso.value = 0;
  if (reducido || items.value.length < 2) return;
  reloj = setInterval(() => {
    if (pausado.value || !enPantalla.value || document.hidden) return;
    progreso.value += PASO / props.intervalo;
    if (progreso.value >= 1) {
      progreso.value = 0;
      actual.value = (actual.value + 1) % items.value.length;
    }
  }, PASO);
}

function tocar(e) { inicioX = e.clientX; arrastro = false; }
function soltar(e) {
  if (inicioX == null) return;
  const d = e.clientX - inicioX;
  inicioX = null;
  if (Math.abs(d) > 40) {
    arrastro = true;
    ir(actual.value + (d < 0 ? 1 : -1));
  }
}
function evitarClic(e) {
  if (!arrastro) return;
  arrastro = false;
  e.preventDefault();
  e.stopPropagation();
}

watch(() => items.value.map((a) => a.id).join(), () => {
  if (actual.value >= items.value.length) actual.value = 0;
  reiniciar();
});
watch([activo, enPantalla], ([a, visible]) => { if (a && visible) contarVista(a); });

onMounted(() => {
  if (typeof IntersectionObserver !== 'undefined') {
    observador = new IntersectionObserver(([e]) => { enPantalla.value = e.isIntersecting; }, { threshold: 0.5 });
    watch(raiz, (el, antes) => {
      if (antes) observador.unobserve(antes);
      if (el) observador.observe(el);
    }, { immediate: true });
  } else {
    enPantalla.value = true;
  }
  reiniciar();
});
onUnmounted(() => { clearInterval(reloj); observador?.disconnect(); });
</script>

<template>
  <section v-if="items.length" ref="raiz" class="carrusel" :class="{ compacto }" aria-roledescription="carrusel" aria-label="Promociones"
           @mouseenter="pausado = true" @mouseleave="pausado = false" @focusin="pausado = true" @focusout="pausado = false"
           @pointerdown="tocar" @pointerup="soltar" @pointercancel="inicioX = null" @click.capture="evitarClic">
    <div class="pista" :style="{ transform: `translateX(-${actual * 100}%)` }">
      <div v-for="(a, i) in items" :key="a.id" class="diapo" :aria-hidden="i !== actual" :inert="i !== actual || undefined"
           role="group" aria-roledescription="diapositiva" :aria-label="`${i + 1} de ${items.length}`">
        <PromoTarjeta :a="a" :variante="compacto ? 'compacta' : 'slide'" />
      </div>
    </div>
    <template v-if="items.length > 1">
      <button v-if="!compacto" type="button" class="flecha izq" aria-label="Anterior" @click="ir(actual - 1)">‹</button>
      <button v-if="!compacto" type="button" class="flecha der" aria-label="Siguiente" @click="ir(actual + 1)">›</button>
      <div class="puntos">
        <button v-for="(a, i) in items" :key="a.id" type="button" :class="{ on: i === actual }"
                :aria-label="`Ver promoción ${i + 1}`" :aria-current="i === actual" @click="ir(i)"><i :style="{ transform: `scaleX(${i === actual ? (reducido ? 1 : progreso) : 0})` }"></i></button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.carrusel { position: relative; overflow: hidden; border-radius: 18px; height: 210px; touch-action: pan-y; user-select: none; flex: none; }
.carrusel.compacto { height: 64px; border-radius: 14px; }
.pista { display: flex; height: 100%; transition: transform 0.55s cubic-bezier(0.22, 1, 0.36, 1); }
.diapo { flex: 0 0 100%; height: 100%; min-width: 0; }
.flecha {
  position: absolute; top: 50%; transform: translateY(-50%); width: 34px; height: 34px; border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.2); background: rgba(11, 13, 26, 0.55); color: #fff; font-size: 1.3rem; line-height: 1;
  cursor: pointer; opacity: 0; transition: opacity 0.2s ease; backdrop-filter: blur(4px);
}
.carrusel:hover .flecha, .flecha:focus-visible { opacity: 1; }
.izq { left: 8px; }
.der { right: 8px; }
.puntos { position: absolute; right: 14px; top: 12px; display: flex; gap: 5px; }
.compacto .puntos { top: auto; bottom: 4px; right: 50%; transform: translateX(50%); gap: 3px; }
.puntos button { width: 18px; height: 5px; padding: 0; border: 0; border-radius: 999px; background: rgba(255, 255, 255, 0.25); cursor: pointer; overflow: hidden; }
.compacto .puntos button { width: 10px; height: 3px; }
.puntos button.on { width: 30px; background: rgba(255, 255, 255, 0.3); }
.compacto .puntos button.on { width: 16px; }
.puntos i { display: block; height: 100%; width: 100%; background: #fff; border-radius: inherit; transform-origin: left; transition: transform 0.1s linear; }
@media (max-width: 560px) {
  .carrusel:not(.compacto) { height: 170px; }
  .flecha { display: none; }
}
@media (prefers-reduced-motion: reduce) { .pista { transition: none; } }
</style>
