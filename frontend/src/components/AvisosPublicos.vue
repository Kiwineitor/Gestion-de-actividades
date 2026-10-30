<script setup>
import { avisosPublicos } from '../ui/avisosPublicos';
</script>

<template>
  <div class="avisos" aria-live="polite">
    <TransitionGroup name="aviso">
      <p v-for="a in avisosPublicos" :key="a.id" class="aviso" :class="a.tipo">{{ a.texto }}</p>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.avisos {
  position: fixed; left: 50%; bottom: calc(18px + env(safe-area-inset-bottom, 0px)); transform: translateX(-50%);
  z-index: 90; display: flex; flex-direction: column; gap: 8px; align-items: center; pointer-events: none;
  width: min(420px, calc(100% - 32px));
}
.aviso {
  margin: 0; padding: 10px 16px; border-radius: 999px; font-size: 0.88rem; font-weight: 600;
  background: rgba(21, 26, 46, 0.94); color: var(--pub-texto, #eef0fb); border: 1px solid var(--pub-linea, #272d4f);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4); backdrop-filter: blur(8px); text-align: center;
}
.aviso.libre { border-color: var(--pub-ok, #3fd18f); }
.aviso.libre::before { content: '● '; color: var(--pub-ok, #3fd18f); }
.aviso.error { border-color: var(--pub-error, #ff6b8b); }
.aviso-enter-active, .aviso-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.aviso-enter-from, .aviso-leave-to { opacity: 0; transform: translateY(10px); }
</style>
