<script setup>
import { computed } from 'vue';
import { url as urlApi } from '../config.js';
import { destinoDe, descuento, formatoBs, contarClic } from '../ui/promosPublicas';

const props = defineProps({
  a: { type: Object, required: true },
  variante: { type: String, default: 'slide' },
  previa: { type: Boolean, default: false },
});
const emit = defineEmits(['accion']);

const imagen = computed(() => {
  const u = props.a.imagenLocal || props.a.imagenUrl;
  if (!u) return null;
  return u.startsWith('blob:') || u.startsWith('data:') || u.startsWith('http') ? u : urlApi(u);
});
const destino = computed(() => destinoDe(props.a));
const rebaja = computed(() => descuento(props.a));
const incluye = computed(() => props.a.incluye || []);
const etiqueta = computed(() => {
  if (props.a.formato === 'COMBO') return rebaja.value ? `Combo · −${rebaja.value}%` : 'Combo';
  if (props.a.edicionNombre && props.variante !== 'emergente') return props.a.edicionNombre;
  return props.a.formato === 'EMERGENTE' ? 'Novedad' : 'Destacado';
});

function pulsar(e) {
  if (props.previa) { e.preventDefault(); return; }
  contarClic(props.a);
  emit('accion');
}
</script>

<template>
  <article class="promo" :class="[`v-${variante}`, `f-${(a.formato || 'DESTACADO').toLowerCase()}`, { 'sin-imagen': !imagen }]">
    <div v-if="imagen" class="foto" :style="{ backgroundImage: `url(${imagen})` }" role="img" :aria-label="a.titulo || ''"></div>
    <div class="cuerpo">
      <span class="etq">{{ etiqueta }}</span>
      <strong class="titulo">{{ a.titulo }}</strong>
      <p v-if="a.mensaje && variante !== 'compacta'" class="texto">{{ a.mensaje }}</p>
      <ul v-if="incluye.length && variante !== 'compacta'" class="incluye">
        <li v-for="(x, i) in incluye.slice(0, variante === 'slide' ? 3 : 8)" :key="i">{{ x }}</li>
        <li v-if="variante === 'slide' && incluye.length > 3" class="mas">y {{ incluye.length - 3 }} más</li>
      </ul>
      <div v-if="a.precio || destino" class="pie">
        <span v-if="a.precio" class="precio">
          <b>{{ formatoBs(a.precio) }}</b>
          <s v-if="a.precioAntes">{{ formatoBs(a.precioAntes) }}</s>
        </span>
        <template v-if="destino">
          <router-link v-if="destino.ruta" :to="destino.ruta" class="cta" @click="pulsar">{{ destino.texto }} →</router-link>
          <a v-else :href="destino.href" target="_blank" rel="noopener" class="cta" :class="{ wa: destino.whatsapp }" @click="pulsar">{{ destino.texto }} →</a>
        </template>
      </div>
    </div>
  </article>
</template>

<style scoped>
.promo {
  --tinte: var(--pub-acento, #8f6dff);
  position: relative; overflow: hidden; border-radius: 18px; color: var(--pub-texto, #eef0fb);
  background: linear-gradient(135deg, color-mix(in srgb, var(--tinte) 45%, #151a2e), #151a2e 70%);
  border: 1px solid color-mix(in srgb, var(--tinte) 40%, transparent);
  font-family: 'Public Sans', system-ui, sans-serif;
}
.f-combo { --tinte: var(--pub-alerta, #ffb347); }
.f-emergente { --tinte: var(--pub-proceso, #4cc3ff); }
.foto { background-size: cover; background-position: center; }
.cuerpo { position: relative; display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.etq {
  align-self: flex-start; font-size: 0.7rem; font-weight: 800; letter-spacing: 0.06em; text-transform: uppercase;
  padding: 3px 9px; border-radius: 999px; background: color-mix(in srgb, var(--tinte) 85%, #000); color: #0b0d1a;
  max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.titulo { font: 800 1.35rem/1.1 var(--pub-titulo, system-ui); text-wrap: balance; }
.texto { margin: 0; color: var(--pub-suave, #a3a9c7); font-size: 0.9rem; line-height: 1.4; overflow-wrap: anywhere; }
.incluye { margin: 0; padding: 0; list-style: none; display: flex; flex-wrap: wrap; gap: 4px 6px; }
.incluye li { font-size: 0.78rem; padding: 2px 8px; border-radius: 999px; background: rgba(255, 255, 255, 0.1); }
.incluye li::before { content: '✓ '; color: var(--pub-ok, #3fd18f); font-weight: 800; }
.incluye li.mas::before { content: ''; }
.pie { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-top: auto; padding-top: 4px; }
.precio { display: inline-flex; align-items: baseline; gap: 8px; }
.precio b { font: 800 1.45rem var(--pub-titulo, system-ui); color: #fff; }
.precio s { color: var(--pub-suave, #a3a9c7); font-size: 0.9rem; }
.cta {
  display: inline-flex; align-items: center; min-height: 40px; padding: 0 16px; border-radius: 12px; font-weight: 800; font-size: 0.9rem;
  text-decoration: none; color: #0b0d1a; background: var(--tinte); white-space: nowrap; margin-left: auto;
  transition: transform 0.15s ease, filter 0.15s ease;
}
.cta:hover { filter: brightness(1.08); transform: translateY(-1px); }
.cta.wa { background: #1fa855; color: #fff; }

.v-slide { height: 100%; display: grid; grid-template-columns: minmax(0, 1fr); }
.v-slide .foto { position: absolute; inset: 0; }
.v-slide:not(.sin-imagen) .foto::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(90deg, rgba(11, 13, 26, 0.92) 0%, rgba(11, 13, 26, 0.72) 48%, rgba(11, 13, 26, 0.12) 100%);
}
.v-slide .cuerpo { padding: 18px 20px; max-width: 520px; justify-content: flex-end; height: 100%; box-sizing: border-box; }
.v-slide .texto { display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }

.v-compacta { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 10px; padding: 8px 10px; border-radius: 14px; height: 100%; box-sizing: border-box; }
.v-compacta .foto { width: 46px; height: 46px; border-radius: 10px; }
.v-compacta.sin-imagen { grid-template-columns: minmax(0, 1fr); }
.v-compacta .cuerpo { display: grid; grid-template-columns: minmax(0, 1fr) auto; grid-template-rows: auto auto; column-gap: 8px; row-gap: 0; align-items: center; }
.v-compacta .etq { grid-column: 1; justify-self: start; font-size: 0.6rem; padding: 1px 7px; }
.v-compacta .titulo { grid-column: 1; font-size: 0.95rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.v-compacta .pie { grid-column: 2; grid-row: 1 / span 2; margin: 0; padding: 0; flex-wrap: nowrap; }
.v-compacta .precio b { font-size: 1rem; }
.v-compacta .precio s { display: none; }
.v-compacta .cta { min-height: 34px; padding: 0 12px; font-size: 0.82rem; }

.v-emergente { display: flex; flex-direction: column; border-radius: 22px; }
.v-emergente .foto { aspect-ratio: 4 / 3; max-height: 46vh; width: 100%; }
.v-emergente .cuerpo { padding: 18px 20px 20px; gap: 8px; }
.v-emergente .titulo { font-size: 1.55rem; }
.v-emergente .cta { min-height: 46px; font-size: 1rem; flex: 1; justify-content: center; }

@media (max-width: 560px) {
  .v-slide .cuerpo { padding: 14px 16px; }
  .v-slide .titulo { font-size: 1.12rem; }
  .v-slide .incluye { display: none; }
  .v-slide .precio b { font-size: 1.15rem; }
  .v-slide .cta { min-height: 36px; padding: 0 12px; }
}
</style>
