import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { apiFetch } from '../api';
import { escucharTopic } from '../ws';

const CLAVE_CERRADOS = 'anuncios.cerrados.v1';

export const useAnunciosStore = defineStore('anuncios', () => {
  const lista = ref([]);
  const cerrados = ref(leerCerrados());
  let cerrarCanal = null;
  let arrancado = false;

  function leerCerrados() {
    try {
      const v = JSON.parse(localStorage.getItem(CLAVE_CERRADOS) || '[]');
      return new Set(Array.isArray(v) ? v : []);
    } catch {
      return new Set();
    }
  }

  function guardarCerrados() {
    try {
      const vivos = new Set(lista.value.map((a) => a.id));
      const podados = [...cerrados.value].filter((id) => vivos.has(id));
      localStorage.setItem(CLAVE_CERRADOS, JSON.stringify(podados));
    } catch { }
  }

  const visibles = computed(() => lista.value.filter((a) => !cerrados.value.has(a.id)));

  function aplicar(dto) {
    if (!dto || dto.id == null) return;
    const i = lista.value.findIndex((a) => a.id === dto.id);
    if (dto.activo === false) {
      if (i >= 0) lista.value.splice(i, 1);
      return;
    }
    if (i < 0) lista.value.unshift(dto);
    else lista.value[i] = dto;
  }

  async function cargar() {
    try {
      const r = await apiFetch('/api/app/anuncios');
      if (r.ok) lista.value = await r.json();
    } catch {
    }
  }

  function asegurar() {
    if (arrancado) return;
    arrancado = true;
    cargar();
    cerrarCanal = escucharTopic('/topic/anuncios', {
      onMensaje: aplicar,
      onConectado: cargar,
    });
  }

  function cerrar(id) {
    cerrados.value = new Set(cerrados.value).add(id);
    guardarCerrados();
  }

  function desconectar() {
    if (cerrarCanal) cerrarCanal();
    cerrarCanal = null;
    arrancado = false;
    lista.value = [];
  }

  return { lista, visibles, asegurar, cargar, aplicar, cerrar, desconectar };
});
