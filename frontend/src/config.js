export const API_BASE = (import.meta.env?.VITE_API_BASE || '').replace(/\/+$/, '');

export const EN_APK = typeof window !== 'undefined'
  && Boolean(window.Capacitor?.isNativePlatform?.());

export function url(ruta) {
  return API_BASE ? API_BASE + ruta : ruta;
}

export function urlWebSocket() {
  if (API_BASE) return API_BASE.replace(/^http/, 'ws') + '/ws';
  const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocolo}//${location.host}/ws`;
}
