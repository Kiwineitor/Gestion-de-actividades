<script setup>
import { ref, watch } from 'vue';

const props = defineProps({
  modelValue: { type: Number, default: 0 },
  min: { type: Number, default: -Infinity },
  max: { type: Number, default: Infinity },
  paso: { type: Number, default: 1 },
  decimales: { type: Number, default: 0 },
  unidad: { type: String, default: '' },
  etiqueta: { type: String, default: '' },
  compacto: { type: Boolean, default: false },
});
const emit = defineEmits(['update:modelValue', 'cambio']);

const texto = ref(formatear(props.modelValue));
watch(() => props.modelValue, (v) => { if (Number(texto.value) !== v) texto.value = formatear(v); });

function formatear(v) {
  const n = Number(v);
  return Number.isFinite(n) ? String(Number(n.toFixed(props.decimales))) : '';
}

function acotar(v) {
  return Math.min(props.max, Math.max(props.min, Number(v.toFixed(props.decimales))));
}

function fijar(v, final = true) {
  if (!Number.isFinite(v)) return;
  const n = acotar(v);
  texto.value = formatear(n);
  emit('update:modelValue', n);
  if (final) emit('cambio', n);
}

function sumar(signo, e) {
  const factor = e?.shiftKey ? 10 : 1;
  fijar(Number(props.modelValue || 0) + signo * props.paso * factor);
}

function escribir(e) {
  texto.value = e.target.value;
  const n = Number(String(e.target.value).replace(',', '.'));
  if (e.target.value !== '' && Number.isFinite(n) && n >= props.min && n <= props.max) emit('update:modelValue', Number(n.toFixed(props.decimales)));
}

function confirmar() {
  const n = Number(String(texto.value).replace(',', '.'));
  if (texto.value === '' || !Number.isFinite(n)) { texto.value = formatear(props.modelValue); return; }
  fijar(n);
}

function tecla(e) {
  if (e.key === 'ArrowUp') { e.preventDefault(); sumar(1, e); }
  else if (e.key === 'ArrowDown') { e.preventDefault(); sumar(-1, e); }
  else if (e.key === 'Enter') { e.preventDefault(); confirmar(); e.target.blur(); }
}
</script>

<template>
  <div class="numero-paso" :class="{ compacto }" role="group" :aria-label="etiqueta">
    <button type="button" class="paso" :disabled="modelValue <= min" :aria-label="`Menos ${etiqueta}`" title="Menos (Shift = ×10)" @click="sumar(-1, $event)">−</button>
    <label class="valor">
      <input :value="texto" inputmode="decimal" :aria-label="etiqueta" @input="escribir" @change="confirmar" @blur="confirmar" @keydown="tecla" />
      <span v-if="unidad" class="unidad">{{ unidad }}</span>
    </label>
    <button type="button" class="paso" :disabled="modelValue >= max" :aria-label="`Más ${etiqueta}`" title="Más (Shift = ×10)" @click="sumar(1, $event)">+</button>
  </div>
</template>

<style scoped>
.numero-paso { display: inline-flex; align-items: stretch; border: 1px solid var(--border); border-radius: var(--radio-sm, 8px); background: var(--panel); overflow: hidden; min-width: 0; }
.paso { width: 34px; flex: none; border: 0; background: var(--panel-2, var(--bg)); color: var(--text); font: 700 1.1rem/1 system-ui, sans-serif; cursor: pointer; }
.paso:hover:not(:disabled) { background: var(--acento-suave); color: var(--acento); }
.paso:disabled { opacity: 0.35; cursor: default; }
.valor { display: flex; align-items: center; flex: 1; min-width: 0; padding: 0 0.35rem; gap: 0.2rem; }
.valor input { width: 100%; min-width: 2.6rem; border: 0; background: transparent; color: var(--text); font: inherit; font-weight: 700; text-align: center; font-variant-numeric: tabular-nums; padding: 0.35rem 0; }
.valor input:focus { outline: none; }
.numero-paso:focus-within { border-color: var(--acento); box-shadow: 0 0 0 2px color-mix(in srgb, var(--acento) 25%, transparent); }
.unidad { color: var(--muted); font-size: 0.8rem; flex: none; }
.compacto .paso { width: 28px; font-size: 1rem; }
.compacto .valor input { padding: 0.2rem 0; min-width: 2.2rem; font-size: 0.85rem; }
</style>
