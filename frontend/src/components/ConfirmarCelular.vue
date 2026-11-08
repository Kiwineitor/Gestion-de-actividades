<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';

const props = defineProps({
  numero: { type: String, required: true },
  extra: { type: String, default: '' },
  para: { type: String, default: 'tus entradas y tu recibo' },
});
const emit = defineEmits(['si', 'no']);

const boton = ref(null);
const legible = (n) => {
  const d = String(n || '').replace(/\D/g, '');
  if (d.length === 8) return `${d.slice(0, 4)} ${d.slice(4)}`;
  if (d.length === 11 && d.startsWith('591')) return `+591 ${d.slice(3, 7)} ${d.slice(7)}`;
  return n;
};
const principal = computed(() => legible(props.numero));
const otro = computed(() => (props.extra ? legible(props.extra) : ''));

function tecla(e) {
  if (e.key === 'Escape') emit('no');
}

onMounted(() => { boton.value?.focus(); window.addEventListener('keydown', tecla); });
onBeforeUnmount(() => window.removeEventListener('keydown', tecla));
</script>

<template>
  <div class="velo-confirma" role="dialog" aria-modal="true" aria-labelledby="titulo-confirma" @click.self="emit('no')">
    <div class="caja-confirma">
      <span class="ico">📱</span>
      <h3 id="titulo-confirma">¿Es correcto tu número de WhatsApp?</h3>
      <p class="numero">{{ principal }}</p>
      <p v-if="otro" class="otro">Otro WhatsApp: <b>{{ otro }}</b></p>
      <p class="detalle">Ahí te enviaremos {{ para }}. Si está mal escrito, no podremos contactarte.</p>
      <div class="botones">
        <button type="button" class="pub-boton secundario" @click="emit('no')">✏️ Corregir</button>
        <button ref="boton" type="button" class="pub-boton" @click="emit('si')">✓ Sí, es mi número</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.velo-confirma { position: fixed; inset: 0; z-index: 400; display: grid; place-items: center; padding: 16px; background: rgba(5, 6, 15, 0.72); backdrop-filter: blur(4px); animation: aparecer 0.18s ease; }
.caja-confirma { width: min(100%, 400px); display: flex; flex-direction: column; align-items: center; gap: 8px; text-align: center; padding: 24px 20px 20px; border-radius: 22px; background: var(--pub-panel, #161827); color: var(--pub-texto, #fff); border: 1px solid var(--pub-linea, rgba(255, 255, 255, 0.12)); box-shadow: 0 24px 60px rgba(0, 0, 0, 0.5); animation: subir 0.22s ease; }
.ico { font-size: 2.2rem; }
h3 { margin: 0; font-size: 1.15rem; }
.numero { margin: 4px 0 0; font-size: 2rem; font-weight: 900; letter-spacing: 0.04em; font-variant-numeric: tabular-nums; color: var(--pub-acento-claro, #a78bfa); }
.otro { margin: 0; font-size: 0.92rem; }
.detalle { margin: 4px 0 8px; font-size: 0.88rem; color: var(--pub-suave, #9ca3af); }
.botones { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; width: 100%; }
@keyframes aparecer { from { opacity: 0; } to { opacity: 1; } }
@keyframes subir { from { opacity: 0; transform: translateY(14px) scale(0.97); } to { opacity: 1; transform: none; } }
</style>
