import { reactive, computed } from 'vue';
import { url as urlApi } from '../config.js';

const NOMBRE = 'Gestión de Actividades';

export const marca = reactive({ nombre: NOMBRE, lema: '', contacto: '', logoUrl: null, lista: false });

let pedido = null;

export function cargarMarca(forzar = false) {
  if (pedido && !forzar) return pedido;
  pedido = fetch(urlApi('/api/publico/sistema'))
    .then((r) => (r.ok ? r.json() : null))
    .then((d) => { if (d) aplicarMarca(d); })
    .catch(() => {})
    .finally(() => { marca.lista = true; });
  return pedido;
}

export function aplicarMarca(d) {
  marca.nombre = d.nombre || NOMBRE;
  marca.lema = d.lema || '';
  marca.contacto = d.contacto || '';
  marca.logoUrl = d.logoUrl ? urlApi(d.logoUrl) : null;
}

export const iniciales = computed(() => marca.nombre
  .split(/\s+/)
  .filter((p) => p.length > 2 || /^[A-ZÁÉÍÓÚÑ]/.test(p))
  .slice(0, 2)
  .map((p) => p.charAt(0).toUpperCase())
  .join('') || 'GA');
