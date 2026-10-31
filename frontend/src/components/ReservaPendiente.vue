<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { misReservas, verificarReservas } from '../ui/reservasGuardadas';

const props = defineProps({
  eventoId: { type: [Number, String], default: null },
});

const ahora = ref(Date.now());
let reloj = null;

const TEXTOS = {
  BLOQUEADA: 'Te falta adjuntar el comprobante',
  EN_REVISION: 'Tu pago está en revisión',
  CONFIRMADA: 'Tus pases están listos',
};

const visibles = computed(() => misReservas.value
  .filter((r) => props.eventoId == null || String(r.eventoId) === String(props.eventoId))
  .filter((r) => !(r.estado === 'BLOQUEADA' && r.expira && r.expira <= ahora.value))
  .slice(0, 2));

function resta(r) {
  if (r.estado !== 'BLOQUEADA' || !r.expira) return '';
  const s = Math.max(0, Math.round((r.expira - ahora.value) / 1000));
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
}

function alVolver() {
  if (document.visibilityState === 'visible') verificarReservas();
}

onMounted(() => {
  verificarReservas();
  reloj = setInterval(() => { ahora.value = Date.now(); }, 1000);
  document.addEventListener('visibilitychange', alVolver);
});
onUnmounted(() => {
  clearInterval(reloj);
  document.removeEventListener('visibilitychange', alVolver);
});
</script>

<template>
  <div v-if="visibles.length" class="reserva-pendiente">
    <router-link v-for="r in visibles" :key="r.codigo" :to="{ path: '/', query: { reserva: r.codigo } }"
                 class="aviso" :class="r.estado.toLowerCase()">
      <span class="ico" aria-hidden="true">{{ r.estado === 'BLOQUEADA' ? '⏳' : r.estado === 'EN_REVISION' ? '🕓' : '🎟️' }}</span>
      <span class="txt">
        <b>{{ TEXTOS[r.estado] }}</b>
        <small>Mesa {{ r.mesa }}<template v-if="r.evento"> · {{ r.evento }}</template></small>
      </span>
      <span v-if="resta(r)" class="reloj">{{ resta(r) }}</span>
      <span class="ir">Continuar →</span>
    </router-link>
  </div>
</template>

<style scoped>
.reserva-pendiente { display: flex; flex-direction: column; gap: 8px; }
.aviso {
  display: flex; align-items: center; gap: 10px; padding: 10px 14px; border-radius: 14px; text-decoration: none;
  color: var(--pub-texto); background: color-mix(in srgb, var(--pub-alerta) 16%, var(--pub-panel));
  border: 1px solid color-mix(in srgb, var(--pub-alerta) 45%, transparent);
  animation: entrar 0.3s ease;
}
.aviso.en_revision { background: color-mix(in srgb, var(--pub-acento) 16%, var(--pub-panel)); border-color: color-mix(in srgb, var(--pub-acento) 45%, transparent); }
.aviso.confirmada { background: color-mix(in srgb, var(--pub-ok) 14%, var(--pub-panel)); border-color: color-mix(in srgb, var(--pub-ok) 45%, transparent); }
.ico { font-size: 1.3rem; }
.txt { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.txt b { font-size: 0.92rem; }
.txt small { color: var(--pub-suave); font-size: 0.8rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.reloj { font: 600 0.95rem var(--pub-mono); color: var(--pub-alerta); }
.ir { font-weight: 700; font-size: 0.85rem; color: var(--pub-acento-claro); white-space: nowrap; }
@keyframes entrar { from { opacity: 0; transform: translateY(-6px); } to { opacity: 1; transform: none; } }
@media (max-width: 480px) { .ir { display: none; } }
</style>
