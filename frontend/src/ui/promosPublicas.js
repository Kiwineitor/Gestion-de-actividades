import { ref, onMounted, onUnmounted } from 'vue';
import { url as urlApi } from '../config.js';
import { alCambiarPublico } from './canalPublico';
import { enlaceWhatsApp } from './tokenPublico';

export const promos = ref([]);

const CLAVE_VISTOS = 'pub.promos.vistos';
let usuarios = 0;
let soltar = null;
let sondeo = null;
let enCurso = null;
const vistos = new Set(leer(CLAVE_VISTOS));

function leer(clave) {
  try { return JSON.parse(sessionStorage.getItem(clave) || '[]'); } catch { return []; }
}

export function cargarPromos() {
  if (enCurso) return enCurso;
  enCurso = fetch(urlApi('/api/publico/anuncios'))
    .then((r) => (r.ok ? r.json() : null))
    .then((lista) => { if (Array.isArray(lista)) promos.value = lista; })
    .catch(() => {})
    .finally(() => { enCurso = null; });
  return enCurso;
}

export function usarPromos() {
  onMounted(() => {
    usuarios += 1;
    if (usuarios > 1) return;
    cargarPromos();
    soltar = alCambiarPublico((m) => {
      if (m?.tipo === 'anuncios' || m?.tipo === 'actividad') cargarPromos();
    }, { reconectado: cargarPromos });
    sondeo = setInterval(cargarPromos, 120000);
  });
  onUnmounted(() => {
    usuarios -= 1;
    if (usuarios > 0) return;
    soltar?.();
    soltar = null;
    clearInterval(sondeo);
  });
  return promos;
}

function enviar(id, que) {
  try {
    fetch(urlApi(`/api/publico/anuncios/${id}/${que}`), { method: 'POST', keepalive: true }).catch(() => {});
  } catch { }
}

export function contarVista(a) {
  if (!a?.id || vistos.has(a.id)) return;
  vistos.add(a.id);
  try { sessionStorage.setItem(CLAVE_VISTOS, JSON.stringify([...vistos])); } catch { }
  enviar(a.id, 'vista');
}

export function contarClic(a) {
  if (a?.id) enviar(a.id, 'clic');
}

export const formatoBs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;

export function descuento(a) {
  const p = Number(a?.precio);
  const antes = Number(a?.precioAntes);
  if (!p || !antes || antes <= p) return 0;
  return Math.round((1 - p / antes) * 100);
}

export function destinoDe(a) {
  if (!a) return null;
  const texto = a.botonTexto || 'Ver más';
  if (a.botonAccion === 'ACTIVIDAD' && a.edicionId) {
    return { texto, ruta: { path: '/', query: { v: 'actividad', edicion: String(a.edicionId) } } };
  }
  if (a.botonAccion === 'WHATSAPP') {
    const href = enlaceWhatsApp(a.contacto, `Hola, me interesa «${a.titulo || 'la promoción'}»${a.edicionNombre ? ` de ${a.edicionNombre}` : ''}.`);
    return href ? { texto, href, whatsapp: true } : null;
  }
  if (a.botonAccion === 'ENLACE' && a.enlace) return { texto, href: a.enlace };
  return null;
}

export function promosDe(lista, { formatos, edicion = null, soloGenerales = false }) {
  const e = edicion == null || edicion === '' ? null : String(edicion);
  return lista.filter((a) => formatos.includes(a.formato)
    && (e ? String(a.edicionId ?? '') === e : soloGenerales ? a.edicionId == null : true));
}
