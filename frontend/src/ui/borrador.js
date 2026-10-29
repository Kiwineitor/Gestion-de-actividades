const PREFIJO = 'unifex.borrador.venta.';
const VIGENCIA_MS = 24 * 60 * 60 * 1000;

const clave = (idUsuario) => `${PREFIJO}${idUsuario ?? 'anon'}`;

export function guardarBorrador(idUsuario, datos) {
  try {
    localStorage.setItem(clave(idUsuario), JSON.stringify({ guardadoEn: Date.now(), datos }));
  } catch (_) {
  }
}

export function leerBorrador(idUsuario) {
  try {
    const crudo = localStorage.getItem(clave(idUsuario));
    if (!crudo) return null;
    const { guardadoEn, datos } = JSON.parse(crudo);
    if (!guardadoEn || Date.now() - guardadoEn > VIGENCIA_MS) {
      borrarBorrador(idUsuario);
      return null;
    }
    return datos;
  } catch (_) {
    return null;
  }
}

export function borrarBorrador(idUsuario) {
  try {
    localStorage.removeItem(clave(idUsuario));
  } catch (_) {
  }
}
