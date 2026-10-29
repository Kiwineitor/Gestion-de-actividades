import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';
import { url as urlServidor } from '../config.js';

const RESPALDO = { url: `${import.meta.env.BASE_URL}mapa.png`, ancho: 1836, alto: 2376, version: 0, propio: false };

const CLAVE_CACHE = 'plano.cache.v1';

function leerCache() {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE_CACHE) || 'null');
    return guardado && guardado.url && guardado.ancho ? guardado : null;
  } catch {
    return null;
  }
}

function guardarCache(p) {
  try {
    localStorage.setItem(CLAVE_CACHE, JSON.stringify(p));
  } catch {
  }
}

export const usePlanoStore = defineStore('plano', () => {
  const plano = ref(leerCache() || { ...RESPALDO });
  let promesa = null;

  const src = computed(() =>
    plano.value.propio ? urlServidor(plano.value.url) : plano.value.url);

  const aspecto = computed(() => (plano.value.alto || 1) / (plano.value.ancho || 1));

  function invalidarCache() {
    try {
      localStorage.removeItem(CLAVE_CACHE);
    } catch {}
    plano.value = { ...RESPALDO };
    promesa = null;
  }

  function asegurar() {
    if (promesa) return promesa;
    promesa = (async () => {
      try {
        const r = await apiFetch('/api/app/plano');
        if (r.ok) {
          const datos = await r.json();
          if (datos.propio !== plano.value.propio || datos.version !== plano.value.version) {
            plano.value = datos;
            guardarCache(datos);
          }
        } else {
          console.warn('[Plano] API respondió error:', r.status);
          if (!plano.value.propio) invalidarCache();
        }
      } catch (e) {
        console.warn('[Plano] Error al obtener plano del servidor:', e.message);
      }
    })();
    return promesa;
  }

  function aplicar(nuevo) {
    if (!nuevo) return;
    plano.value = nuevo;
    guardarCache(nuevo);
  }

  return { plano, src, aspecto, asegurar, aplicar, invalidarCache };
});
