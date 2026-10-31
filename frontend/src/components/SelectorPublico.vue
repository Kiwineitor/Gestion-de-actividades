<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue';

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  opciones: { type: Array, default: () => [] },
  marcador: { type: String, default: 'Elige una opción' },
  buscarMarcador: { type: String, default: 'Buscar…' },
  bloqueado: { type: Boolean, default: false },
  id: { type: String, default: null },
});
const emit = defineEmits(['update:modelValue']);

const abierto = ref(false);
const busqueda = ref('');
const resaltado = ref(0);
const raiz = ref(null);
const buscador = ref(null);

const normal = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const elegida = computed(() => props.opciones.find((o) => String(o.valor) === String(props.modelValue)) || null);
const filtradas = computed(() => {
  const q = normal(busqueda.value.trim());
  return q ? props.opciones.filter((o) => normal(`${o.etiqueta} ${o.detalle || ''}`).includes(q)) : props.opciones;
});

function abrir() {
  if (props.bloqueado) return;
  abierto.value = !abierto.value;
  busqueda.value = '';
  resaltado.value = Math.max(0, filtradas.value.findIndex((o) => String(o.valor) === String(props.modelValue)));
  if (abierto.value) nextTick(() => buscador.value?.focus());
}

function elegir(o) {
  if (!o || o.deshabilitado) return;
  emit('update:modelValue', o.valor);
  abierto.value = false;
}

function tecla(e) {
  if (e.key === 'ArrowDown') { e.preventDefault(); resaltado.value = Math.min(filtradas.value.length - 1, resaltado.value + 1); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); resaltado.value = Math.max(0, resaltado.value - 1); }
  else if (e.key === 'Enter') { e.preventDefault(); elegir(filtradas.value[resaltado.value]); }
  else if (e.key === 'Escape') { e.stopPropagation(); abierto.value = false; }
}

function fuera(e) {
  if (abierto.value && raiz.value && !raiz.value.contains(e.target)) abierto.value = false;
}

onMounted(() => document.addEventListener('pointerdown', fuera));
onUnmounted(() => document.removeEventListener('pointerdown', fuera));
</script>

<template>
  <div ref="raiz" class="selector" :class="{ abierto, bloqueado }">
    <button :id="id" type="button" class="disparador" :aria-expanded="abierto" :disabled="bloqueado" @click="abrir">
      <template v-if="elegida">
        <i v-if="elegida.color" class="punto" :style="{ background: elegida.color }"></i>
        <span class="txt"><strong>{{ elegida.etiqueta }}</strong><small v-if="elegida.detalle">{{ elegida.detalle }}</small></span>
      </template>
      <span v-else class="txt sin-eleccion">{{ marcador }}</span>
      <span v-if="!bloqueado" class="flecha">▾</span>
    </button>
    <div v-if="abierto" class="panel" role="listbox">
      <input ref="buscador" v-model="busqueda" class="buscar" :placeholder="buscarMarcador" @keydown="tecla" @input="resaltado = 0" />
      <ul>
        <li v-for="(o, i) in filtradas" :key="o.valor">
          <button type="button" role="option" :aria-selected="String(o.valor) === String(modelValue)"
                  :class="{ on: i === resaltado, elegida: String(o.valor) === String(modelValue) }" :disabled="o.deshabilitado"
                  @mouseenter="resaltado = i" @click="elegir(o)">
            <i v-if="o.color" class="punto" :style="{ background: o.color }"></i>
            <span class="txt"><strong>{{ o.etiqueta }}</strong><small v-if="o.detalle">{{ o.detalle }}</small></span>
            <span v-if="o.extra" class="extra">{{ o.extra }}</span>
          </button>
        </li>
        <li v-if="!filtradas.length" class="nada">No hay coincidencias</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.selector { position: relative; }
.disparador {
  width: 100%; min-height: 48px; display: flex; align-items: center; gap: 10px; padding: 6px 12px; text-align: left; cursor: pointer;
  background: var(--pub-fondo); border: 1px solid var(--pub-linea); border-radius: 10px; color: var(--pub-texto); font: inherit;
}
.disparador:focus-visible, .abierto .disparador { outline: 2px solid var(--pub-acento-claro); border-color: transparent; }
.bloqueado .disparador { cursor: default; background: var(--pub-panel-2); }
.txt { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.txt strong { font-weight: 700; font-size: 0.95rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.txt small { color: var(--pub-suave); font-size: 0.78rem; }
.sin-eleccion { color: var(--pub-suave); }
.flecha { color: var(--pub-suave); }
.punto { width: 12px; height: 12px; border-radius: 50%; flex: none; box-shadow: 0 0 0 2px rgba(255,255,255,.15); }
.panel {
  position: absolute; z-index: 30; left: 0; right: 0; top: calc(100% + 6px); max-height: 300px; display: flex; flex-direction: column;
  background: var(--pub-panel); border: 1px solid var(--pub-linea); border-radius: 12px; box-shadow: 0 18px 40px rgba(0,0,0,.45); overflow: hidden;
  animation: pub-entrar 0.15s ease-out;
}
.buscar {
  margin: 8px; min-height: 40px; padding: 0 12px; border-radius: 8px; border: 1px solid var(--pub-linea);
  background: var(--pub-fondo); color: var(--pub-texto); font: inherit;
}
.buscar:focus { outline: 2px solid var(--pub-acento-claro); border-color: transparent; }
ul { list-style: none; margin: 0; padding: 0 6px 6px; overflow-y: auto; }
li button {
  width: 100%; display: flex; align-items: center; gap: 10px; padding: 8px 10px; border: 0; border-radius: 8px; cursor: pointer;
  background: none; color: var(--pub-texto); font: inherit; text-align: left;
}
li button.on { background: var(--pub-panel-2); }
li button.elegida { box-shadow: inset 0 0 0 1px var(--pub-acento); }
li button:disabled { opacity: 0.45; cursor: not-allowed; }
.extra { font-size: 0.78rem; font-weight: 700; color: var(--pub-ok); white-space: nowrap; }
.nada { padding: 12px; color: var(--pub-suave); font-size: 0.88rem; text-align: center; }
</style>
