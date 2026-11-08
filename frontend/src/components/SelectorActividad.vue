<script setup>
import { computed, onMounted } from 'vue';
import { actividadElegida, actividades, ediciones, cargarActividades } from '../ui/actividadElegida';

defineProps({
  soloFerias: { type: Boolean, default: false },
});

const enCurso = computed(() => ediciones.value.find((e) => e.activa) || null);
const otras = computed(() => ediciones.value.filter((e) => !e.activa));
const nombreActual = computed(() => {
  if (!actividadElegida.value) return enCurso.value?.nombre || 'Actividad en curso';
  return ediciones.value.find((e) => e.clave === actividadElegida.value)?.nombre || 'Actividad';
});

onMounted(() => cargarActividades());
</script>

<template>
  <label class="selector-actividad" :class="{ unico: actividades.length <= 1 }" :title="`Estás viendo los datos de «${nombreActual}». Cambia aquí para ver otra actividad.`">
    <span class="etiqueta"><span aria-hidden="true">🗓️</span> Actividad:</span>
    <select v-if="actividades.length > 1" v-model="actividadElegida" aria-label="Actividad que estás viendo">
      <option value="">{{ enCurso ? `${enCurso.nombre} (en curso)` : 'Actividad en curso' }}</option>
      <optgroup v-if="otras.length" label="Otras actividades">
        <option v-for="e in otras" :key="e.clave" :value="e.clave">{{ e.nombre }}</option>
      </optgroup>
    </select>
    <strong v-else class="nombre">{{ nombreActual }}</strong>
    <span v-if="actividades.length > 1" class="flecha" aria-hidden="true">▾</span>
  </label>
</template>

<style scoped>
.selector-actividad {
  position: relative; display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.3rem 0.5rem 0.3rem 0.75rem;
  border: 2px solid var(--acento); border-radius: 12px; background: var(--acento-suave); color: var(--acento);
  font-weight: 700; font-size: 0.9rem; max-width: 100%; cursor: pointer;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--acento) 12%, transparent);
}
.selector-actividad.unico { cursor: default; }
.etiqueta { display: inline-flex; align-items: center; gap: 0.3rem; white-space: nowrap; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.04em; }
.selector-actividad select {
  appearance: none; -webkit-appearance: none; border: 0; background: transparent; color: var(--text); font: inherit; font-weight: 800;
  font-size: 0.95rem; padding: 0.25rem 1.3rem 0.25rem 0.2rem; max-width: min(62vw, 340px); cursor: pointer; outline: none;
  text-overflow: ellipsis;
}
.selector-actividad select option, .selector-actividad select optgroup { color: var(--text); background: var(--panel); }
.flecha { position: absolute; right: 0.6rem; pointer-events: none; font-size: 0.8rem; }
.nombre { color: var(--text); font-weight: 800; }
.selector-actividad:focus-within { box-shadow: 0 0 0 3px color-mix(in srgb, var(--acento) 35%, transparent); }
</style>
