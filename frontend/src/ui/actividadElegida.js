import { ref, computed, watch } from 'vue';
import { apiFetch } from '../api';

const CLAVE = 'actividad.elegida';

function leer() {
  try {
    const v = sessionStorage.getItem(CLAVE) || '';
    return /^\d+$/.test(v) ? `f-${v}` : v;
  } catch { return ''; }
}

export const actividadElegida = ref(leer());

watch(actividadElegida, (v) => {
  try { if (v) sessionStorage.setItem(CLAVE, v); else sessionStorage.removeItem(CLAVE); } catch { }
});

export const actividades = ref([]);
export const ediciones = computed(() => actividades.value.filter((a) => a.tipo === 'FERIA'));
export const eventos = computed(() => actividades.value.filter((a) => a.tipo === 'MESAS'));

export const edicionElegida = computed(() => (/^f-\d+$/.test(actividadElegida.value) ? actividadElegida.value.slice(2) : ''));
export const eventoElegido = computed(() => (/^e-\d+$/.test(actividadElegida.value) ? actividadElegida.value.slice(2) : ''));
export const esEventoElegido = computed(() => Boolean(eventoElegido.value));

export const actividadActual = computed(() => {
  const clave = actividadElegida.value || ediciones.value.find((e) => e.activa)?.clave;
  return actividades.value.find((a) => a.clave === clave) || null;
});

let promesa = null;

export function cargarActividades(forzar = false) {
  if (forzar) promesa = null;
  if (!promesa) {
    promesa = apiFetch('/api/app/actividades')
      .then((r) => (r.ok ? r.json() : []))
      .then((l) => {
        actividades.value = Array.isArray(l) ? l : [];
        if (actividadElegida.value && !actividades.value.some((a) => a.clave === actividadElegida.value)) actividadElegida.value = '';
      })
      .catch(() => { promesa = null; });
  }
  return promesa;
}

export const cargarEdiciones = cargarActividades;

export function conEdicion(ruta) {
  if (!edicionElegida.value) return ruta;
  return `${ruta}${ruta.includes('?') ? '&' : '?'}edicion=${encodeURIComponent(edicionElegida.value)}`;
}
