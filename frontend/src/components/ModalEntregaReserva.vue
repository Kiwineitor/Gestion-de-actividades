<script setup>
import UiModal from './UiModal.vue';
import EntregaReserva from './EntregaReserva.vue';

const props = defineProps({
  reservaId: { type: Number, required: true },
});
const emit = defineEmits(['cerrar', 'cambio']);
</script>

<template>
  <UiModal :titulo="`Recibo y pases · reserva #${props.reservaId}`" ancho="680px" @cerrar="emit('cerrar')">
    <EntregaReserva :reserva-id="props.reservaId" @cambio="emit('cambio')" />
    <template #pie>
      <button type="button" class="btn btn-primario" @click="emit('cerrar')">Listo</button>
    </template>
  </UiModal>
</template>
