import { useAuthStore } from './stores/auth.js';
import { url as urlApi } from './config.js';
import { registrarError } from './ui/registroErrores.js';

export async function apiFetch(ruta, options = {}) {
  const auth = useAuthStore();
  const headers = { ...(options.headers || {}) };
  if (auth.token) headers['Authorization'] = `Bearer ${auth.token}`;
  if (!headers['X-Origen']) {
    headers['X-Origen'] = typeof window !== 'undefined' && window.Capacitor ? 'APK' : 'WEB';
  }
  const esFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  if (options.body && !esFormData && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  let res;
  try {
    res = await fetch(urlApi(ruta), { ...options, headers });
  } catch (error) {
    if (error.name !== 'AbortError') registrarError(error, `${options.method || 'GET'} ${ruta.split('?')[0]}`);
    throw error;
  }

  if (res.status === 401) {
    auth.logout();
    throw new Error('Sesion expirada');
  }
  if (res.status === 423) {
    const data = await res.clone().json().catch(() => ({}));
    if (data.codigo === 'MANTENIMIENTO') {
      auth.logout();
      window.dispatchEvent(new CustomEvent('unifex:mantenimiento', {
        detail: { mensaje: data.mensaje || 'Sistema en mantenimiento' },
      }));
      throw new Error(data.mensaje || 'Sistema en mantenimiento');
    }
  }
  return res;
}

export const MENSAJE_SIN_PERMISO = 'Tu rol no tiene permiso en el servidor para ver estos datos. '
  + 'La pantalla está habilitada en «Permisos», pero el servidor solo la atiende para '
  + 'ciertos roles del sistema. Pide a un administrador que revise tu rol.';

export async function jsonOError(res, porDefecto = 'No se pudo cargar la información') {
  if (res.ok) return res.json();
  if (res.status === 403) throw new Error(MENSAJE_SIN_PERMISO);
  const cuerpo = await res.json().catch(() => ({}));
  throw new Error(cuerpo.mensaje || porDefecto);
}
