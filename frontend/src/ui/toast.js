import { ref } from 'vue';
import { alerta } from './alerta.js';

export const toasts = ref([]);
let secuencia = 0;

export function toast(mensaje, tipo = 'info', ms = 3500) {
  if (tipo === 'error' || tipo === 'advertencia') {
    return alerta(mensaje, tipo, ms === 3500 ? 5000 : ms);
  }
  const id = ++secuencia;
  toasts.value.push({ id, mensaje, tipo });
  setTimeout(() => cerrar(id), ms);
  return id;
}

export function cerrar(id) {
  toasts.value = toasts.value.filter((t) => t.id !== id);
}