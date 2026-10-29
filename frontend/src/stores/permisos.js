import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';

const CLAVE_CACHE = 'permisos.cache.v1';

export const usePermisosStore = defineStore('permisos', () => {
  const pantallas = ref(new Set());
  const loVeTodo = ref(false);
  const cargado = ref(false);
  let promesa = null;

  function hidratar() {
    try {
      const g = JSON.parse(localStorage.getItem(CLAVE_CACHE) || 'null');
      if (!g) return;
      pantallas.value = new Set(g.pantallas || []);
      loVeTodo.value = Boolean(g.loVeTodo);
      cargado.value = true;
    } catch {
    }
  }

  function guardar() {
    try {
      localStorage.setItem(CLAVE_CACHE, JSON.stringify({
        pantallas: [...pantallas.value], loVeTodo: loVeTodo.value,
      }));
    } catch {
    }
  }

  function asegurar() {
    if (promesa) return promesa;
    hidratar();
    promesa = (async () => {
      try {
        const r = await apiFetch('/api/app/permisos/mias');
        if (!r.ok) throw new Error(`El servidor respondio ${r.status}`);
        const d = await r.json();
        pantallas.value = new Set(d.pantallas || []);
        loVeTodo.value = Boolean(d.loVeTodo);
        cargado.value = true;
        guardar();
      } catch {
        promesa = null;
      }
    })();
    return promesa;
  }

  function recargar() {
    promesa = null;
    cargado.value = false;
    return asegurar();
  }

  const puedeVer = computed(() => (clave) => {
    if (loVeTodo.value) return true;
    if (!cargado.value) return clave === 'inicio';
    return pantallas.value.has(clave);
  });

  function limpiar() {
    pantallas.value = new Set();
    loVeTodo.value = false;
    cargado.value = false;
    promesa = null;
    try {
      localStorage.removeItem(CLAVE_CACHE);
    } catch {
    }
  }

  return { pantallas, loVeTodo, cargado, asegurar, recargar, puedeVer, limpiar };
});
