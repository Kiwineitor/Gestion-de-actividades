const CLAVE = 'pub-token';

function nuevo() {
  if (globalThis.crypto?.randomUUID) return crypto.randomUUID();
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

export function tokenPublico() {
  try {
    let t = sessionStorage.getItem(CLAVE);
    if (!t) {
      t = nuevo();
      sessionStorage.setItem(CLAVE, t);
    }
    return t;
  } catch (_) {
    return (tokenPublico.memoria ||= nuevo());
  }
}

export function enlaceWhatsApp(numero, texto = '') {
  const d = String(numero || '').replace(/\D/g, '');
  if (d.length < 7) return null;
  const completo = d.length === 8 ? `591${d}` : d;
  return `https://wa.me/${completo}${texto ? `?text=${encodeURIComponent(texto)}` : ''}`;
}
