<script setup>
import { computed, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from './AppShell.vue';
import { usePuestosStore } from '../stores/puestos';
import { moduloActual, VISTAS_EN_CACHE } from '../modulos';

const route = useRoute();
const vista = computed(() => moduloActual(route).vista);

const puestos = usePuestosStore();
onUnmounted(() => puestos.desconectar());
</script>

<template>
  <AppShell>
    <KeepAlive :include="VISTAS_EN_CACHE">
      <component :is="vista" />
    </KeepAlive>
  </AppShell>
</template>
