const CLAVE = 'entradas.compras.v1';
const VIGENCIA_MS = 120 * 24 * 60 * 60 * 1000;

export function comprasGuardadas() {
  try {
    const lista = JSON.parse(localStorage.getItem(CLAVE) || '[]');
    return Array.isArray(lista) ? lista.filter((c) => c && c.codigo && Date.now() - (c.t || 0) < VIGENCIA_MS) : [];
  } catch { return []; }
}

export function guardarCompra(codigo, actividad) {
  try {
    const lista = comprasGuardadas().filter((c) => c.codigo !== codigo);
    lista.unshift({ codigo, actividad: actividad || '', t: Date.now() });
    localStorage.setItem(CLAVE, JSON.stringify(lista.slice(0, 10)));
  } catch { }
}
