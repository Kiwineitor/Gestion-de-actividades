import { ref, computed } from 'vue';

const pendientes = ref(0);
export const textoCarga = ref('');
export const esperaMs = ref(0);

export const cargando = computed(() => pendientes.value > 0);

export const AVISO_LENTO_MS = 2500;
export const AVISO_SIN_SENAL_MS = 9000;

export const estadoEspera = computed(() => {
  if (esperaMs.value >= AVISO_SIN_SENAL_MS) return 'sin-senal';
  if (esperaMs.value >= AVISO_LENTO_MS) return 'lento';
  return 'normal';
});

export function mostrarCarga(texto = 'Un momento…') {
  if (pendientes.value === 0) {
    textoCarga.value = texto;
    esperaMs.value = 0;
  }
  pendientes.value++;
}

export function cambiarTextoCarga(texto) {
  if (pendientes.value > 0 && texto) textoCarga.value = texto;
}

export function ocultarCarga() {
  pendientes.value = Math.max(0, pendientes.value - 1);
  if (pendientes.value === 0) esperaMs.value = 0;
}
