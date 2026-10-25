<script setup>
import { ref, onMounted } from 'vue';
import { apiFetch, jsonOError } from '../../api';
import TableroVentas from './TableroVentas.vue';

const tablero = ref(null);
const error = ref('');
const cargando = ref(true);

async function cargar() {
  cargando.value = true;
  try {
    tablero.value = await jsonOError(await apiFetch('/api/app/control-ventas'), 'No se pudieron cargar los cobros');
    error.value = '';
  } catch (e) {
    error.value = e.message;
  } finally {
    cargando.value = false;
  }
}

onMounted(cargar);
</script>

<template>
  <div class="cobros">
    <div class="barra">
      <span class="muted">Vendido, cobrado y pendiente de rendir de la actividad actual.</span>
      <button class="btn btn-fantasma btn-sm" :disabled="cargando" @click="cargar">Actualizar</button>
    </div>
    <p v-if="error" class="card aviso-error">{{ error }}</p>
    <div v-else-if="cargando && !tablero" class="vacio">Cargando…</div>
    <TableroVentas v-else-if="tablero" :tablero="tablero" />
  </div>
</template>

<style scoped>
.cobros { display: flex; flex-direction: column; gap: 0.8rem; }
.barra { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.aviso-error { padding: 0.8rem; color: var(--danger); }
</style>
