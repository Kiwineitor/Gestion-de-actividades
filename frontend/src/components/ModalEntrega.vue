<script setup>
import { ref, computed, onMounted } from 'vue';
import UiModal from './UiModal.vue';
import EntregaVenta from './EntregaVenta.vue';
import { apiFetch } from '../api';

const props = defineProps({
  inscripcionId: { type: Number, required: true },
});
const emit = defineEmits(['cerrar']);

const entrega = ref(null);
const error = ref('');

const titular = computed(() => ({
  nombre: entrega.value?.representante || entrega.value?.entidad || '',
  celular: entrega.value?.celular || '',
  empresa: entrega.value?.entidad || '',
}));

async function cargar() {
  const r = await apiFetch(`/api/app/inscripciones/${props.inscripcionId}/entrega`);
  if (!r.ok) { error.value = r.status === 403 ? 'Esta venta no es tuya.' : 'No se pudo cargar la venta.'; return; }
  entrega.value = await r.json();
}

onMounted(cargar);
</script>

<template>
  <UiModal :titulo="`Recibo y credenciales · venta #${inscripcionId}`" ancho="640px" @cerrar="emit('cerrar')">
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="!entrega" class="muted">Cargando…</p>
    <EntregaVenta v-else :entrega="entrega" :titular="titular" :actividad="entrega.actividad || ''" @actualizar="cargar" />
    <template #pie>
      <button type="button" class="btn btn-primario" @click="emit('cerrar')">Listo</button>
    </template>
  </UiModal>
</template>

<style scoped>
.error { color: var(--danger); margin: 0; }
.muted { color: var(--muted); margin: 0; }
</style>
