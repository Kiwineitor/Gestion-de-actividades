<script setup>
import { useAnunciosStore } from '../stores/anuncios';

const anuncios = useAnunciosStore();

const ICONO = { urgente: '🚨', aviso: '⚠️', info: 'ℹ️' };

const hora = (t) => {
  if (!t) return '';
  try {
    return new Date(t).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
};
</script>

<template>
  <div class="pila-anuncios" role="status" aria-live="polite">
    <TransitionGroup name="anuncio">
      <article v-for="a in anuncios.visibles" :key="a.id"
               class="anuncio" :class="`nivel-${a.nivel || 'info'}`">
        <span class="icono" aria-hidden="true">{{ ICONO[a.nivel] || ICONO.info }}</span>
        <div class="cuerpo">
          <strong v-if="a.titulo" class="titulo">{{ a.titulo }}</strong>
          <p class="mensaje">{{ a.mensaje }}</p>
          <span class="firma muted">
            {{ a.publicadoPor || 'Administración' }}<template v-if="hora(a.publicado)"> · {{ hora(a.publicado) }}</template>
          </span>
        </div>
        <button class="cerrar" title="Cerrar este anuncio" aria-label="Cerrar este anuncio"
                @click="anuncios.cerrar(a.id)">✕</button>
      </article>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.pila-anuncios {
  position: fixed;
  top: calc(0.75rem + var(--safe-top, 0px));
  right: calc(0.75rem + var(--safe-right, 0px));
  z-index: 1400;
  display: flex; flex-direction: column; gap: 0.6rem;
  width: min(380px, calc(100vw - 1.5rem));
  pointer-events: none;
}

.anuncio {
  pointer-events: auto;
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 0.6rem;
  padding: 0.8rem 0.9rem;
  border-radius: var(--radio, 12px);
  background: var(--panel, #fff);
  border-left: 5px solid var(--color-nivel, var(--acento));
  box-shadow: 0 10px 30px rgba(2, 6, 23, 0.22);
}
.nivel-info    { --color-nivel: var(--acento, #2563eb); }
.nivel-aviso   { --color-nivel: var(--tramite, #d97706); }
.nivel-urgente { --color-nivel: var(--danger, #dc2626); }

.nivel-urgente { animation: latido 2.4s ease-in-out infinite; }
@keyframes latido {
  0%, 100% { box-shadow: 0 10px 30px rgba(2, 6, 23, 0.22); }
  50%      { box-shadow: 0 10px 30px rgba(220, 38, 38, 0.45); }
}
@media (prefers-reduced-motion: reduce) {
  .nivel-urgente { animation: none; }
}

.icono { font-size: 1.25rem; line-height: 1.2; }
.cuerpo { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }
.titulo { font-size: 1rem; }
.mensaje { margin: 0; line-height: 1.45; overflow-wrap: anywhere; }
.firma { font-size: 0.78rem; }

.cerrar {
  align-self: start; border: none; background: transparent; cursor: pointer;
  font-size: 1rem; line-height: 1; color: inherit; opacity: 0.55;
  min-width: 32px; min-height: 32px;
}
.cerrar:hover { opacity: 1; }

.anuncio-enter-active, .anuncio-leave-active { transition: opacity 0.25s, transform 0.25s; }
.anuncio-enter-from, .anuncio-leave-to { opacity: 0; transform: translateX(20px); }

@media (max-width: 480px) {
  .pila-anuncios { left: calc(0.75rem + var(--safe-left, 0px)); width: auto; }
}
</style>
