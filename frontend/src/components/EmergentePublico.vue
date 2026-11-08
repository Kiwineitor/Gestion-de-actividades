<script setup>
import { ref, computed, watch, onUnmounted } from 'vue';
import PromoTarjeta from './PromoTarjeta.vue';
import { usarPromos, promosDe, contarVista } from '../ui/promosPublicas';

const props = defineProps({
  edicion: { type: [String, Number], default: null },
  espera: { type: Number, default: 1500 },
});

const CLAVE = 'pub.emergentes.cerrados';
const lista = usarPromos();
const cerrados = ref(new Set(leer()));
const mostrar = ref(null);
let temporizador = null;

function leer() {
  try { return JSON.parse(sessionStorage.getItem(CLAVE) || '[]'); } catch { return []; }
}

const candidato = computed(() => promosDe(lista.value, { formatos: ['EMERGENTE'], edicion: props.edicion, soloGenerales: true })
  .find((a) => !cerrados.value.has(a.id)) || null);

function cerrar() {
  if (!mostrar.value) return;
  const s = new Set(cerrados.value).add(mostrar.value.id);
  cerrados.value = s;
  try { sessionStorage.setItem(CLAVE, JSON.stringify([...s])); } catch { }
  mostrar.value = null;
  document.documentElement.classList.remove('pub-modal-abierto');
}

function tecla(e) { if (e.key === 'Escape') cerrar(); }

watch(candidato, (a) => {
  clearTimeout(temporizador);
  if (mostrar.value && (!a || a.id !== mostrar.value.id) && !lista.value.some((x) => x.id === mostrar.value.id)) mostrar.value = null;
  if (!a || mostrar.value) return;
  temporizador = setTimeout(() => {
    if (document.documentElement.classList.contains('pub-modal-abierto')) return;
    mostrar.value = a;
    contarVista(a);
  }, props.espera);
}, { immediate: true });

watch(mostrar, (a) => {
  if (a) {
    document.addEventListener('keydown', tecla);
    document.documentElement.classList.add('pub-modal-abierto');
  } else {
    document.removeEventListener('keydown', tecla);
  }
});

onUnmounted(() => {
  clearTimeout(temporizador);
  document.removeEventListener('keydown', tecla);
  if (mostrar.value) document.documentElement.classList.remove('pub-modal-abierto');
});
</script>

<template>
  <Teleport to="body">
    <Transition name="emergente">
      <div v-if="mostrar" class="pub fondo" @click.self="cerrar">
        <div class="caja" role="dialog" aria-modal="true" :aria-label="mostrar.titulo || 'Novedad'">
          <button type="button" class="cerrar" aria-label="Cerrar" @click="cerrar">✕</button>
          <PromoTarjeta :a="mostrar" variante="emergente" @accion="cerrar" />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fondo {
  position: fixed; inset: 0; z-index: 120; min-height: 0; display: grid; place-items: center; padding: 16px;
  background: rgba(5, 6, 14, 0.72); backdrop-filter: blur(4px);
}
.caja { position: relative; width: min(440px, 100%); max-height: calc(100dvh - 32px); overflow: auto; border-radius: 22px; box-shadow: 0 30px 80px rgba(0, 0, 0, 0.55); }
.cerrar {
  position: absolute; top: 10px; right: 10px; z-index: 2; width: 36px; height: 36px; border-radius: 50%; border: 0;
  background: rgba(11, 13, 26, 0.7); color: #fff; font-size: 1rem; cursor: pointer; backdrop-filter: blur(4px);
}
.emergente-enter-active, .emergente-leave-active { transition: opacity 0.25s ease; }
.emergente-enter-active .caja, .emergente-leave-active .caja { transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1); }
.emergente-enter-from, .emergente-leave-to { opacity: 0; }
.emergente-enter-from .caja, .emergente-leave-to .caja { transform: translateY(24px) scale(0.96); }
@media (prefers-reduced-motion: reduce) {
  .emergente-enter-active, .emergente-leave-active, .emergente-enter-active .caja, .emergente-leave-active .caja { transition: none; }
}
</style>
