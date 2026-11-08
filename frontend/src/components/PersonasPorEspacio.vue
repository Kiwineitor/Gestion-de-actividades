<script setup>
import { computed } from 'vue';
import NumeroPaso from './NumeroPaso.vue';

const conPersonas = defineModel('conPersonas', { type: Boolean, default: false });
const personas = defineModel('personas', { type: Number, default: 2 });
const costoExtra = defineModel('costoExtra', { type: Number, default: 0 });

const props = defineProps({
  uno: { type: String, default: 'espacio' },
  registraPersonas: { type: Boolean, default: true },
});

const conExtras = computed(() => Number(costoExtra.value) > 0);

const art = computed(() => (props.uno.endsWith('a') ? 'la' : 'el'));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;

function elegirExtras(v) {
  if (!v) costoExtra.value = 0;
  else if (!(Number(costoExtra.value) > 0)) costoExtra.value = 20;
}

function elegirCupo(v) {
  conPersonas.value = v;
  if (v && !(Number(personas.value) >= 1)) personas.value = 2;
}

const ejemplo = computed(() => {
  const n = Math.max(1, Number(personas.value) || 1);
  const base = `Cada ${props.uno} incluye ${n} persona${n === 1 ? '' : 's'}, cada una con su credencial`;
  return conExtras.value ? `${base}. Desde la ${n + 1}.ª, cada persona paga ${bs(costoExtra.value)}.` : `${base}. No se aceptan más.`;
});
</script>

<template>
  <fieldset class="personas-espacio">
    <legend>¿Cuántas personas por {{ uno }}?</legend>
    <div class="opciones-pe" role="radiogroup">
      <button type="button" class="opcion-pe" :class="{ on: !conPersonas }" role="radio" :aria-checked="!conPersonas" @click="elegirCupo(false)">
        <b>🏷️ Solo se vende {{ art }} {{ uno }}</b>
        <small>No se registran personas ni credenciales por persona.</small>
      </button>
      <button type="button" class="opcion-pe" :class="{ on: conPersonas }" role="radio" :aria-checked="conPersonas" @click="elegirCupo(true)">
        <b>👥 Con cupo de personas</b>
        <small>Cada {{ uno }} incluye un número de personas con su credencial.</small>
      </button>
    </div>

    <template v-if="conPersonas">
      <div class="fila-pe">
        <div class="campo"><span>Personas incluidas en el precio</span>
          <NumeroPaso v-model="personas" :min="1" :max="50" etiqueta="Personas incluidas" />
          <small class="muted">Cuenta a quien compra. Ej.: 2 = el titular + 1 acompañante.</small>
        </div>
        <div class="campo"><span>¿Pueden sumarse más personas pagando?</span>
          <div class="si-no" role="radiogroup">
            <button type="button" class="btn btn-sm" :class="{ 'btn-primario': !conExtras }" role="radio" :aria-checked="!conExtras" @click="elegirExtras(false)">No, cupo fijo</button>
            <button type="button" class="btn btn-sm" :class="{ 'btn-primario': conExtras }" role="radio" :aria-checked="conExtras" @click="elegirExtras(true)">Sí, pagando</button>
          </div>
        </div>
        <div v-if="conExtras" class="campo"><span>Precio por persona extra (Bs)</span>
          <NumeroPaso v-model="costoExtra" :min="1" :max="99999" :paso="5" :decimales="2" etiqueta="Precio por persona extra" />
          <small class="muted">La persona extra recibe su propia credencial y su recibo.</small>
        </div>
      </div>
      <p class="ejemplo-pe">{{ ejemplo }}</p>
      <p v-if="!registraPersonas" class="aviso-pe">⚠️ Esta actividad no usa credenciales por persona, así que el cupo y las personas extra no se aplican. Actívalas en el paso «Credenciales» de la actividad («Credencial para quien compra»).</p>
    </template>
  </fieldset>
</template>

<style scoped>
.personas-espacio { border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.6rem 0.75rem 0.75rem; margin: 0; display: flex; flex-direction: column; gap: 0.6rem; min-width: 0; }
.personas-espacio legend { font-weight: 700; font-size: 0.9rem; padding: 0 0.3rem; }
.opciones-pe { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 190px), 1fr)); gap: 0.45rem; }
.opcion-pe { display: flex; flex-direction: column; gap: 0.2rem; text-align: left; padding: 0.55rem 0.7rem; border-radius: var(--radio-sm); border: 2px solid var(--border); background: var(--panel); color: var(--text); font: inherit; cursor: pointer; }
.opcion-pe small { color: var(--muted); font-size: 0.78rem; line-height: 1.3; }
.opcion-pe.on { border-color: var(--acento); background: var(--acento-suave); }
.fila-pe { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr)); gap: 0.6rem; align-items: start; }
.campo small { font-size: 0.76rem; }
.si-no { display: flex; gap: 0.35rem; flex-wrap: wrap; }
.ejemplo-pe { margin: 0; font-size: 0.82rem; padding: 0.45rem 0.6rem; border-radius: var(--radio-sm); background: var(--panel-2); }
.aviso-pe { margin: 0; font-size: 0.8rem; padding: 0.45rem 0.6rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--tramite) 14%, transparent); }
</style>
