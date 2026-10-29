import { defineStore } from 'pinia';
import { ref } from 'vue';
import { apiFetch } from '../api';
import { escucharTopic } from '../ws';
import { alertaConAccion } from '../ui/alerta';

export const useActividadStore = defineStore('actividad', () => {
  const actual = ref(null);
  let pedido = null;
  let cerrarCanal = null;

  async function cargar() {
    const r = await apiFetch('/api/app/ediciones');
    if (!r.ok) return;
    const lista = await r.json();
    actual.value = lista.find((e) => e.activa) || null;
  }

  function asegurar() {
    if (!pedido) pedido = cargar().catch(() => { pedido = null; });
    if (!cerrarCanal) {
      cerrarCanal = escucharTopic('/topic/actividad', {
        onMensaje: async (m) => {
          if (!m?.id || m.id === actual.value?.id) return;
          actual.value = { ...(actual.value || {}), id: m.id, nombre: m.nombre, activa: true };
          const recargar = await alertaConAccion(
            `La actividad activa ahora es "${m.nombre}". Recarga para ver su plano, puestos y ventas.`,
            'advertencia', () => {});
          if (recargar) window.location.reload();
        },
      });
    }
    return pedido;
  }

  function desconectar() {
    if (cerrarCanal) cerrarCanal();
    cerrarCanal = null;
    pedido = null;
  }

  return { actual, asegurar, cargar, desconectar };
});
