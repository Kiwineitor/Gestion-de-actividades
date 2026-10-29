<script setup>
import { ref, computed, watch } from 'vue';

const props = defineProps({
  modelValue: { type: String, default: '' },
  falta: { type: Boolean, default: false },
  id: { type: String, default: null },
});
const emit = defineEmits(['update:modelValue']);

const PAISES = [
  { codigo: '591', pais: 'BO', bandera: '🇧🇴', nombre: 'Bolivia', largo: [8], ejemplo: '71234567' },
  { codigo: '55', pais: 'BR', bandera: '🇧🇷', nombre: 'Brasil', largo: [10, 11], ejemplo: '11987654321' },
];
const POR_DEFECTO = PAISES[0];

const soloDigitos = (v) => String(v || '').replace(/\D/g, '');

function partir(valor) {
  const d = soloDigitos(valor);
  for (const p of PAISES) {
    if (d.startsWith(p.codigo) && p.largo.includes(d.length - p.codigo.length)) {
      return { pais: p.pais, numero: d.slice(p.codigo.length) };
    }
  }
  return { pais: POR_DEFECTO.pais, numero: d };
}

const inicial = partir(props.modelValue);
const pais = ref(inicial.pais);
const numero = ref(inicial.numero);

const paisActual = computed(() => PAISES.find((p) => p.pais === pais.value) || POR_DEFECTO);

function avisar() {
  const n = soloDigitos(numero.value);
  emit('update:modelValue', n ? paisActual.value.codigo + n : '');
}

function alEscribir(e) {
  numero.value = soloDigitos(e.target.value);
  avisar();
}

watch(pais, avisar);

watch(() => props.modelValue, (v) => {
  const actual = soloDigitos(numero.value)
    ? paisActual.value.codigo + soloDigitos(numero.value) : '';
  if (soloDigitos(v) === soloDigitos(actual)) return;
  const p = partir(v);
  pais.value = p.pais;
  numero.value = p.numero;
});
</script>

<template>
  <div class="celular" :class="{ falta }">
    <select v-model="pais" class="pais" :aria-label="`País: ${paisActual.nombre}`">
      <option v-for="p in PAISES" :key="p.pais" :value="p.pais">
        {{ p.bandera }} +{{ p.codigo }}
      </option>
    </select>
    <input
      :id="id"
      class="control numero"
      type="tel"
      inputmode="numeric"
      autocomplete="tel-national"
      :value="numero"
      :placeholder="paisActual.ejemplo"
      @input="alEscribir"
    />
  </div>
</template>

<style scoped>
.celular { display: flex; align-items: stretch; gap: 0; }
.pais {
  flex: none; font: inherit; cursor: pointer;
  border: 1px solid var(--border); border-right: none;
  border-radius: var(--radio-sm) 0 0 var(--radio-sm);
  background: var(--panel-2); color: var(--texto);
  padding: 0 0.5rem; min-height: 44px;
}
.numero { border-radius: 0 var(--radio-sm) var(--radio-sm) 0; flex: 1; min-width: 0; }
.celular:focus-within .pais { border-color: var(--acento); }
.celular.falta .pais,
.celular.falta .numero { border-color: var(--danger); border-width: 2px; }
.celular.falta .pais { border-right: none; }
.celular.falta .numero { background: var(--danger-suave); }
</style>
