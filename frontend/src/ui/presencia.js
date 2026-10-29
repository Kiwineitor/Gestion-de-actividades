import { apiFetch } from '../api';

const INTERVALO_MS = 25000;

let temporizador = null;
let ultima = { pantalla: null, titulo: null };
let avance = { avance: null, avancePaso: null, avanceFaltan: null };

const origen = () => (window.Capacitor?.isNativePlatform?.() ? 'APK' : 'WEB');

async function latir() {
  if (document.visibilityState === 'hidden') return;
  try {
    await apiFetch('/api/app/presencia', {
      method: 'POST',
      body: JSON.stringify({ ...ultima, ...avance, origen: origen() }),
    });
  } catch {
  }
}

export function iniciarPresencia() {
  if (temporizador) return;
  temporizador = setInterval(latir, INTERVALO_MS);
  document.addEventListener('visibilitychange', alVolver);
  latir();
}

function alVolver() {
  if (document.visibilityState === 'visible') latir();
}

export function marcarPantalla(pantalla, titulo) {
  ultima = { pantalla: pantalla || null, titulo: titulo || null };
  if (pantalla !== 'venta') avance = { avance: null, avancePaso: null, avanceFaltan: null };
  if (temporizador) latir();
}

export function marcarAvance({ porcentaje = null, paso = null, faltan = null } = {}) {
  avance = {
    avance: porcentaje == null ? null : Math.max(0, Math.min(100, Math.round(porcentaje))),
    avancePaso: paso || null,
    avanceFaltan: faltan || null,
  };
}

export function limpiarAvance() {
  avance = { avance: null, avancePaso: null, avanceFaltan: null };
  if (temporizador) latir();
}

export async function detenerPresencia() {
  if (temporizador) clearInterval(temporizador);
  temporizador = null;
  document.removeEventListener('visibilitychange', alVolver);
  try {
    await apiFetch('/api/app/presencia/salir', { method: 'POST' });
  } catch {
  }
}
