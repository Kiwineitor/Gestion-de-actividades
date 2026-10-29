<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { marca, cargarMarca } from '../ui/marca';

const emit = defineEmits(['fin']);

const VISIBLE_MS = 1400;
const SALIDA_MS = 300;
const ESPERA_MAX_MS = 2500;

const saliendo = ref(false);
const logo = ref(null);
let tVisible = null;
let tSalida = null;
let tTope = null;
let arrancada = false;

function terminar() {
  if (saliendo.value) return;
  saliendo.value = true;
  tSalida = setTimeout(() => emit('fin'), SALIDA_MS);
}

function arrancarCuenta() {
  if (arrancada) return;
  arrancada = true;
  clearTimeout(tTope);
  tVisible = setTimeout(terminar, VISIBLE_MS);
}

onMounted(async () => {
  tTope = setTimeout(arrancarCuenta, ESPERA_MAX_MS);
  await cargarMarca();
  if (!marca.logoUrl) arrancarCuenta();
});

onUnmounted(() => {
  clearTimeout(tVisible);
  clearTimeout(tSalida);
  clearTimeout(tTope);
});
</script>

<template>
  <div class="bienvenida" :class="{ saliendo }" @click="terminar" role="presentation">
    <div class="halo"></div>
    <div class="marca">
      <img v-if="marca.logoUrl" ref="logo" :src="marca.logoUrl" :alt="marca.nombre"
           fetchpriority="high" decoding="async" @load="arrancarCuenta" @error="arrancarCuenta" />
      <h1 v-else-if="marca.lista" class="nombre">{{ marca.nombre }}</h1>
      <div class="barra"><span></span></div>
    </div>
  </div>
</template>

<style scoped>
.bienvenida {
  position: fixed; inset: 0; z-index: 3000;
  display: grid; place-items: center;
  background: #000;
  overflow: hidden;
  transition: opacity 0.3s ease;
}
.bienvenida.saliendo { opacity: 0; pointer-events: none; }

.halo {
  position: absolute; width: 140vmin; height: 140vmin; border-radius: 50%;
  background: radial-gradient(circle, rgba(124, 92, 255, 0.30) 0%, rgba(124, 92, 255, 0.08) 42%, transparent 68%);
  animation: abrir 0.9s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.marca { position: relative; display: flex; flex-direction: column; align-items: center; gap: 1.6rem; }

.nombre {
  margin: 0; padding: 0 1.5rem; text-align: center; color: #fff;
  font-size: clamp(1.8rem, 6vw, 3rem); font-weight: 800; letter-spacing: 0.02em;
  text-shadow: 0 0 28px rgba(124, 92, 255, 0.55);
  animation: entrar 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}
.marca img {
  width: min(60vw, 320px); max-height: 40vh; object-fit: contain; height: auto; display: block;
  filter: drop-shadow(0 0 28px rgba(124, 92, 255, 0.45));
  animation: entrar 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
}

.barra {
  width: min(48vw, 220px); height: 3px; border-radius: 999px;
  background: rgba(255, 255, 255, 0.14); overflow: hidden;
  animation: aparecer 0.35s 0.3s ease both;
}
.barra span {
  display: block; width: 40%; height: 100%; border-radius: inherit;
  background: linear-gradient(90deg, transparent, #a99bff, #7c5cff, transparent);
  animation: recorrer 0.85s ease-in-out infinite;
}

@keyframes entrar {
  from { opacity: 0; transform: translateY(14px) scale(0.94); }
  to   { opacity: 1; transform: none; }
}
@keyframes abrir {
  from { opacity: 0; transform: scale(0.6); }
  to   { opacity: 1; transform: scale(1); }
}
@keyframes aparecer { from { opacity: 0; } to { opacity: 1; } }
@keyframes recorrer {
  from { transform: translateX(-110%); }
  to   { transform: translateX(360%); }
}

@media (prefers-reduced-motion: reduce) {
  .halo, .marca img, .nombre, .barra, .barra span { animation: none; }
  .barra span { width: 100%; }
}
</style>
