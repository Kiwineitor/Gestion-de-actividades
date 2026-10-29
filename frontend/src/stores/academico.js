import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';

export const useAcademicoStore = defineStore('academico', () => {
  const areas = ref([]);
  const cargado = ref(false);
  let promesa = null;

  const carreras = computed(() => areas.value.flatMap((a) => a.carreras || []));

  const nombreDeCarrera = (id) => carreras.value.find((c) => c.id === id)?.nombre || null;

  const etiquetaArea = (a) =>
    (a?.nombre && a.nombre !== a.sigla ? `${a.sigla} · ${a.nombre}` : a?.sigla || '');

  function asegurar() {
    if (promesa) return promesa;
    promesa = (async () => {
      try {
        const r = await apiFetch('/api/app/areas');
        if (!r.ok) throw new Error('No se pudo cargar áreas y carreras');
        areas.value = await r.json();
        cargado.value = true;
      } catch (e) {
        promesa = null;
        throw e;
      }
    })();
    return promesa;
  }

  return { areas, carreras, cargado, asegurar, nombreDeCarrera, etiquetaArea };
});
