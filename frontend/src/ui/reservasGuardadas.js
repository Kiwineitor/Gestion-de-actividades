import { ref } from 'vue';
import { url as urlApi } from '../config.js';

const CLAVE = 'mesas.mis-reservas.v1';
const VIGENTES = ['BLOQUEADA', 'EN_REVISION', 'CONFIRMADA'];
const DURACION_MS = 3 * 24 * 60 * 60 * 1000;

function leer() {
  try {
    const lista = JSON.parse(localStorage.getItem(CLAVE) || '[]');
    return Array.isArray(lista) ? lista : [];
  } catch {
    return [];
  }
}

function escribir(lista) {
  try { localStorage.setItem(CLAVE, JSON.stringify(lista)); } catch { }
}

function depurar(lista) {
  const ahora = Date.now();
  return lista.filter((r) => VIGENTES.includes(r.estado) && ahora - (r.guardada || 0) < DURACION_MS
    && !(r.estado === 'BLOQUEADA' && r.expira && r.expira < ahora));
}

export const misReservas = ref(depurar(leer()));

export function recordarReserva(reserva) {
  if (!reserva?.codigo) return;
  const resto = leer().filter((r) => r.codigo !== reserva.codigo);
  if (VIGENTES.includes(reserva.estado)) {
    resto.unshift({
      codigo: reserva.codigo,
      estado: reserva.estado,
      mesa: reserva.mesa,
      evento: reserva.evento?.nombre || '',
      eventoId: reserva.evento?.id ?? null,
      expira: reserva.estado === 'BLOQUEADA' ? Date.now() + Math.max(0, reserva.segundosRestantes || 0) * 1000 : null,
      guardada: Date.now(),
    });
  }
  const lista = depurar(resto).slice(0, 5);
  escribir(lista);
  misReservas.value = lista;
}

export function refrescarReservas() {
  const lista = depurar(leer());
  escribir(lista);
  misReservas.value = lista;
  return lista;
}

export function olvidarReserva(codigo) {
  const lista = leer().filter((r) => r.codigo !== codigo);
  escribir(lista);
  misReservas.value = depurar(lista);
}

export async function verificarReservas() {
  for (const r of refrescarReservas()) {
    try {
      const resp = await fetch(urlApi(`/api/publico/mesas/reservas/${encodeURIComponent(r.codigo)}`));
      if (resp.status === 404) { olvidarReserva(r.codigo); continue; }
      if (!resp.ok) continue;
      const actual = await resp.json();
      if (actual.estado !== r.estado || actual.mesa !== r.mesa) recordarReserva(actual);
    } catch {
    }
  }
}
