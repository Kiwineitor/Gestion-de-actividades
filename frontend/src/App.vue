<script setup>
import { ref, onMounted } from 'vue';
import ToastHost from './components/ToastHost.vue';
import AlertaModal from './components/AlertaModal.vue';
import Bienvenida from './components/Bienvenida.vue';
import CargandoOverlay from './components/CargandoOverlay.vue';
import { iniciarTema } from './ui/tema';
import { EN_APK } from './config';

const bienvenida = ref(EN_APK || /\/admin\/?$/.test(window.location.pathname));

function cerrarBienvenida() {
  bienvenida.value = false;
}

onMounted(() => {
  iniciarTema();
  if (EN_APK) document.documentElement.classList.add('apk');
  document.getElementById('arranque')?.remove();
});
</script>

<template>
  <router-view />
  <ToastHost />
  <AlertaModal />
  <CargandoOverlay />
  <Bienvenida v-if="bienvenida" @fin="cerrarBienvenida" />
</template>
