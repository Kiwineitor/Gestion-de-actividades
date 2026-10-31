import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { borrarBorradoresDe } from '../ui/borradores.js';

const MAXIMO = 10;

function clave(usuario) {
  return `pestanas:${usuario || 'anonimo'}`;
}

export const usePestanasStore = defineStore('pestanas', () => {
  const lista = ref([]);
  const activa = ref(null);
  const versiones = ref({});
  let usuario = null;

  const abiertas = computed(() => lista.value.map((p) => p.m));

  function guardar() {
    if (!usuario) return;
    try { localStorage.setItem(clave(usuario), JSON.stringify({ lista: lista.value, activa: activa.value })); } catch { }
  }

  function cargar(u) {
    usuario = u;
    try {
      const g = JSON.parse(localStorage.getItem(clave(u)) || 'null');
      lista.value = Array.isArray(g?.lista) ? g.lista : [];
      activa.value = g?.activa ?? null;
    } catch {
      lista.value = [];
      activa.value = null;
    }
    return activa.value ? lista.value.find((p) => p.m === activa.value) : null;
  }

  function visitar(m, query, titulo) {
    const limpia = Object.fromEntries(Object.entries(query || {}).filter(([k, v]) => k !== 'm' && v != null && v !== ''));
    const i = lista.value.findIndex((p) => p.m === m);
    if (i >= 0) lista.value[i] = { ...lista.value[i], query: limpia, titulo };
    else {
      lista.value.push({ m, query: limpia, titulo });
      if (lista.value.length > MAXIMO) {
        const vieja = lista.value.find((p) => p.m !== m);
        if (vieja) quitar(vieja.m);
      }
    }
    activa.value = m;
    guardar();
  }

  function quitar(m) {
    lista.value = lista.value.filter((p) => p.m !== m);
    versiones.value = { ...versiones.value, [m]: (versiones.value[m] || 0) + 1 };
    borrarBorradoresDe(m);
  }

  function cerrar(m) {
    const i = lista.value.findIndex((p) => p.m === m);
    quitar(m);
    let siguiente = null;
    if (activa.value === m) {
      siguiente = lista.value[Math.min(i, lista.value.length - 1)] || null;
      activa.value = siguiente?.m ?? null;
    }
    guardar();
    return siguiente;
  }

  function cerrarOtras(m) {
    lista.value.filter((p) => p.m !== m).forEach((p) => quitar(p.m));
    activa.value = m;
    guardar();
  }

  function cerrarTodas() {
    [...lista.value].forEach((p) => quitar(p.m));
    activa.value = null;
    guardar();
  }

  function version(m) {
    return versiones.value[m] || 0;
  }

  function olvidarSesion() {
    usuario = null;
    lista.value = [];
    activa.value = null;
  }

  return { lista, activa, abiertas, cargar, visitar, cerrar, cerrarOtras, cerrarTodas, version, olvidarSesion };
});
