<script setup>
import { onMounted, onUnmounted, ref } from 'vue';

const props = defineProps({
  titulo: { type: String, default: '' },
  ancho: { type: String, default: '560px' },
  cerrable: { type: Boolean, default: true },
});
const emit = defineEmits(['cerrar']);
const caja = ref(null);

function tecla(e) {
  if (e.key === 'Escape' && props.cerrable) emit('cerrar');
}

onMounted(() => {
  document.addEventListener('keydown', tecla);
  document.documentElement.classList.add('pub-modal-abierto');
  caja.value?.focus();
});
onUnmounted(() => {
  document.removeEventListener('keydown', tecla);
  document.documentElement.classList.remove('pub-modal-abierto');
});
</script>

<template>
  <Teleport to="body">
    <div class="pub fondo-modal" @click.self="cerrable && emit('cerrar')">
      <section ref="caja" class="caja" role="dialog" aria-modal="true" :aria-label="titulo" tabindex="-1" :style="{ '--ancho': ancho }">
        <header class="cab">
          <slot name="cabecera"><h2>{{ titulo }}</h2></slot>
          <button v-if="cerrable" type="button" class="cerrar" aria-label="Cerrar" @click="emit('cerrar')">✕</button>
        </header>
        <div class="cuerpo"><slot /></div>
        <footer v-if="$slots.pie" class="pie"><slot name="pie" /></footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.fondo-modal {
  position: fixed; inset: 0; z-index: 80; min-height: 0; background: rgba(4, 5, 12, 0.72);
  backdrop-filter: blur(6px); display: grid; place-items: center; padding: 16px;
  animation: aparecer 0.16s ease-out;
}
.caja {
  width: min(var(--ancho), 100%); max-height: min(92dvh, 860px); display: flex; flex-direction: column;
  background: var(--pub-panel); border: 1px solid var(--pub-linea); border-radius: 20px; outline: none;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.55); animation: subir 0.2s ease-out; overflow: hidden;
}
.cab { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 18px 18px 0 20px; }
.cab :deep(h2) { margin: 0; font-size: 1.35rem; }
.cerrar {
  flex: none; width: 36px; height: 36px; border-radius: 50%; border: 1px solid var(--pub-linea);
  background: transparent; color: var(--pub-suave); cursor: pointer; font-size: 0.95rem;
}
.cerrar:hover { color: var(--pub-texto); border-color: var(--pub-acento-claro); }
.cuerpo { padding: 14px 20px 18px; overflow-y: auto; overscroll-behavior: contain; display: flex; flex-direction: column; gap: 14px; }
.pie { padding: 12px 20px 18px; border-top: 1px solid var(--pub-linea); display: flex; gap: 10px; justify-content: flex-end; flex-wrap: wrap; }
@media (max-width: 560px) {
  .fondo-modal { padding: 10px; }
  .caja { border-radius: 18px; max-height: 94dvh; }
  .cuerpo { padding: 12px 16px 16px; }
  .pie { padding: 10px 16px 14px; }
  .pie :deep(.pub-boton) { flex: 1; }
}
@keyframes aparecer { from { opacity: 0; } to { opacity: 1; } }
@keyframes subir { from { opacity: 0; transform: translateY(14px) scale(0.98); } to { opacity: 1; transform: none; } }
</style>
