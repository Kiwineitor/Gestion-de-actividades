<script setup>
import { ref, nextTick, onBeforeUnmount } from 'vue';

const props = defineProps({
  acciones: { type: Array, required: true },
  etiqueta: { type: String, default: 'Acciones' },
  deshabilitado: { type: Boolean, default: false },
});

const abierto = ref(false);
const boton = ref(null);
const lista = ref(null);
const posicion = ref({ top: '0px', left: '0px' });

function colocar() {
  const r = boton.value.getBoundingClientRect();
  const ancho = lista.value?.offsetWidth || 220;
  const alto = lista.value?.offsetHeight || 200;
  const abajo = r.bottom + 4 + alto <= window.innerHeight;
  posicion.value = {
    top: `${abajo ? r.bottom + 4 : Math.max(8, r.top - alto - 4)}px`,
    left: `${Math.max(8, Math.min(r.right - ancho, window.innerWidth - ancho - 8))}px`,
  };
}

async function alternar() {
  if (props.deshabilitado) return;
  abierto.value = !abierto.value;
  if (!abierto.value) return quitarOyentes();
  await nextTick();
  colocar();
  await nextTick();
  lista.value?.querySelector('button:not(:disabled)')?.focus();
  document.addEventListener('pointerdown', fuera, true);
  document.addEventListener('keydown', tecla, true);
  window.addEventListener('scroll', cerrar, true);
  window.addEventListener('resize', cerrar);
}

function cerrar() {
  abierto.value = false;
  quitarOyentes();
}

function fuera(e) {
  if (lista.value?.contains(e.target) || boton.value?.contains(e.target)) return;
  cerrar();
}

function tecla(e) {
  if (e.key === 'Escape') {
    e.stopPropagation();
    cerrar();
    boton.value?.focus();
  }
}

function quitarOyentes() {
  document.removeEventListener('pointerdown', fuera, true);
  document.removeEventListener('keydown', tecla, true);
  window.removeEventListener('scroll', cerrar, true);
  window.removeEventListener('resize', cerrar);
}

function ejecutar(a) {
  if (a.deshabilitado) return;
  cerrar();
  a.accion?.();
}

onBeforeUnmount(quitarOyentes);
</script>

<template>
  <button ref="boton" type="button" class="btn btn-fantasma btn-sm puntos" :disabled="deshabilitado"
          :aria-label="etiqueta" :aria-expanded="abierto" aria-haspopup="menu" @click="alternar">⋮</button>
  <Teleport to="body">
    <div v-if="abierto" ref="lista" class="menu-acciones-flotante" role="menu" :style="posicion">
      <template v-for="(a, i) in acciones" :key="i">
        <hr v-if="a.separador" />
        <button v-else type="button" role="menuitem" :class="{ peligro: a.peligro }" :disabled="a.deshabilitado"
                :title="a.titulo" @click="ejecutar(a)">{{ a.txt }}</button>
      </template>
    </div>
  </Teleport>
</template>

<style scoped>
.puntos { font-size: 1.15rem; line-height: 1; padding: 0.25rem 0.55rem; }
</style>

<style>
.menu-acciones-flotante {
  position: fixed; z-index: 200; min-width: 200px; padding: 0.3rem;
  display: flex; flex-direction: column;
  background: var(--panel); color: var(--text);
  border: 1px solid var(--border); border-radius: var(--radio); box-shadow: var(--sombra-md);
}
.menu-acciones-flotante button {
  text-align: left; border: none; background: none; color: var(--text); font: inherit; font-size: 0.88rem;
  padding: 0.5rem 0.7rem; border-radius: 6px; cursor: pointer;
}
.menu-acciones-flotante button:hover:not(:disabled), .menu-acciones-flotante button:focus-visible { background: var(--bg); outline: none; }
.menu-acciones-flotante button:disabled { opacity: 0.45; cursor: default; }
.menu-acciones-flotante button.peligro { color: var(--danger); }
.menu-acciones-flotante hr { border: none; border-top: 1px solid var(--border); margin: 0.25rem 0; }
</style>
