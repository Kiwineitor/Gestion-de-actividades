<script setup>
import { onMounted } from 'vue';
import { actividadElegida, actividades, ediciones, eventos, cargarActividades } from '../ui/actividadElegida';

const props = defineProps({
  soloFerias: { type: Boolean, default: false },
});

onMounted(() => cargarActividades());
</script>

<template>
  <label v-if="actividades.length > 1" class="selector-actividad" title="Actividad que se muestra en todas las pestañas">
    <span aria-hidden="true">🗓️</span>
    <select v-model="actividadElegida" aria-label="Actividad">
      <option value="">Feria en curso</option>
      <optgroup v-if="ediciones.length" label="Ferias (puestos)">
        <option v-for="e in ediciones" :key="e.clave" :value="e.clave">{{ e.nombre }}{{ e.activa ? ' (en curso)' : '' }}</option>
      </optgroup>
      <optgroup v-if="eventos.length && !props.soloFerias" label="Eventos con mesas">
        <option v-for="e in eventos" :key="e.clave" :value="e.clave">{{ e.nombre }}</option>
      </optgroup>
    </select>
  </label>
</template>

<style scoped>
.selector-actividad {
  display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.15rem 0.2rem 0.15rem 0.6rem;
  border: 1px solid var(--acento); border-radius: 999px; background: var(--acento-suave); color: var(--acento);
  font-weight: 700; font-size: 0.85rem; max-width: 100%;
}
.selector-actividad select {
  border: 0; background: transparent; color: inherit; font: inherit; font-weight: 700; padding: 0.3rem 0.4rem;
  max-width: min(60vw, 280px); cursor: pointer; outline: none;
}
</style>
