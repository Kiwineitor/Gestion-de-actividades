import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';

export const useNotificacionesStore = defineStore('notificaciones', () => {
  const lista = ref([]);
  const cargando = ref(false);
  const error = ref('');

  const filtroLeida = ref('todas');
  const filtroTipo = ref('');

  const countNoLeidas = computed(() => lista.value.filter((n) => !n.leida).length);

  const filtradas = computed(() => {
    let arr = lista.value;
    if (filtroLeida.value === 'leidas') arr = arr.filter((n) => n.leida);
    else if (filtroLeida.value === 'no-leidas') arr = arr.filter((n) => !n.leida);
    if (filtroTipo.value) arr = arr.filter((n) => n.tipo === filtroTipo.value);
    return arr;
  });

  const tiposDisponibles = computed(() =>
    [...new Set(lista.value.map((n) => n.tipo))].sort()
  );

  async function cargar() {
    cargando.value = true;
    error.value = '';
    try {
      const r = await apiFetch('/api/app/notificaciones');
      lista.value = await r.json();
    } catch (e) {
      error.value = e.message;
      throw e;
    } finally {
      cargando.value = false;
    }
  }

  async function refrescarContador() {
    try {
      const r = await apiFetch('/api/app/notificaciones/count');
    } catch {
    }
  }

  function aplicarWS(notificacion) {
    const existe = lista.value.some((n) => n.inscripcionId === notificacion.inscripcionId && n.fecha === notificacion.fecha);
    if (!existe) {
      lista.value.unshift({
        id: Date.now() + Math.random(),
        ...notificacion,
        leida: false,
      });
    }
  }

  async function marcarLeida(id) {
    const n = lista.value.find((x) => x.id === id);
    if (!n || n.leida) return;
    try {
      await apiFetch(`/api/app/notificaciones/${id}/leer`, { method: 'POST' });
      n.leida = true;
      n.leidaEn = new Date().toISOString();
    } catch (e) {
      error.value = e.message;
    }
  }

  async function marcarTodasLeidas() {
    const noLeidas = lista.value.filter((n) => !n.leida);
    if (!noLeidas.length) return;
    try {
      await apiFetch('/api/app/notificaciones/leer-todas', { method: 'POST' });
      noLeidas.forEach((n) => {
        n.leida = true;
        n.leidaEn = new Date().toISOString();
      });
    } catch (e) {
      error.value = e.message;
    }
  }

  async function cargarHilo(notificacionPadreId) {
    try {
      const r = await apiFetch(`/api/app/notificaciones/${notificacionPadreId}/hilo`);
      return await r.json();
    } catch (e) {
      error.value = e.message;
      return [];
    }
  }

  async function responder(notificacionPadreId, respuesta) {
    try {
      await apiFetch(`/api/app/notificaciones/${notificacionPadreId}/responder`, {
        method: 'POST',
        body: JSON.stringify({ respuesta }),
      });
    } catch (e) {
      error.value = e.message;
      throw e;
    }
  }

  async function resolver(notificacionPadreId, respuesta) {
    try {
      await apiFetch(`/api/app/notificaciones/${notificacionPadreId}/resolver`, {
        method: 'POST',
        body: JSON.stringify({ respuesta }),
      });
    } catch (e) {
      error.value = e.message;
      throw e;
    }
  }

  async function crearObservacion({ vendedorId, asunto, cuerpo, inscripcionId, puestoId }) {
    try {
      await apiFetch('/api/app/notificaciones/observacion', {
        method: 'POST',
        body: JSON.stringify({ vendedorId, asunto, cuerpo, inscripcionId, puestoId }),
      });
    } catch (e) {
      error.value = e.message;
      throw e;
    }
  }

  return {
    lista, cargando, error, countNoLeidas, filtradas, filtroLeida, filtroTipo, tiposDisponibles,
    cargar, refrescarContador, aplicarWS, marcarLeida, marcarTodasLeidas,
    cargarHilo, responder, resolver, crearObservacion,
  };
});