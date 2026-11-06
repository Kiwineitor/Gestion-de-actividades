export const TIPOS_ESPACIO = [
  { id: 'MESA', icono: '🍽️', uno: 'mesa', varios: 'mesas', comprador: 'PERSONA', ejemplo: 'Mesa VIP, Mesa General' },
  { id: 'PUESTO', icono: '🏪', uno: 'puesto', varios: 'puestos', comprador: 'NEGOCIO', ejemplo: 'Gastronomía, Artesanía' },
  { id: 'STAND', icono: '🎪', uno: 'stand', varios: 'stands', comprador: 'NEGOCIO', ejemplo: 'Stand Premium, Stand Básico' },
  { id: 'CASETA', icono: '🛖', uno: 'caseta', varios: 'casetas', comprador: 'NEGOCIO', ejemplo: 'Caseta grande, Caseta chica' },
  { id: 'CARPA', icono: '⛺', uno: 'carpa', varios: 'carpas', comprador: 'PERSONA', ejemplo: 'Carpa familiar, Carpa doble' },
  { id: 'OTRO', icono: '✳️', uno: 'espacio', varios: 'espacios', comprador: 'NEGOCIO', ejemplo: 'Zona A, Zona B' },
];

export function tipoEspacio(id) {
  return TIPOS_ESPACIO.find((t) => t.id === id) || TIPOS_ESPACIO[1];
}

export function nombreEspacio(actividad, plural = false) {
  const t = tipoEspacio(actividad?.tipoEspacio);
  if (t.id === 'OTRO' && actividad?.etiquetaEspacio) {
    const e = actividad.etiquetaEspacio.trim().toLowerCase();
    return plural ? (/[aeiou]$/.test(e) ? `${e}s` : `${e}es`) : e;
  }
  return plural ? t.varios : t.uno;
}

export const mayuscula = (s) => (s ? s.charAt(0).toUpperCase() + s.slice(1) : s);
