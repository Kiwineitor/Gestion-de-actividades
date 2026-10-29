import { ref } from 'vue';
import { apiFetch } from '../api';

let cache = null;
let enVuelo = null;

export const categorias = ref(null);

export async function asegurarCategorias() {
  if (cache) { categorias.value = cache; return cache; }
  if (!enVuelo) {
    enVuelo = (async () => {
      try {
        const r = await apiFetch('/api/app/categorias');
        cache = r.ok ? await r.json() : [];
      } catch {
        cache = [];
      } finally {
        enVuelo = null;
      }
      categorias.value = cache;
      return cache;
    })();
  }
  return enVuelo;
}

export function opcionesDe(categoriaId) {
  if (!cache || categoriaId == null) return [];
  return cache.find((c) => c.id === categoriaId)?.opciones || [];
}

export function tieneVariosPrecios(puesto) {
  if (!puesto || puesto.precioPropio) return false;
  return opcionesDe(puesto.categoriaId).length > 1;
}
