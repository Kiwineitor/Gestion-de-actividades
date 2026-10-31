import { ref, watch, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';

const PREFIJO = 'borrador:';
const cerrados = new Set();

function usuarioActual() {
  try { return localStorage.getItem('usuario') || 'anonimo'; } catch { return 'anonimo'; }
}

function leer(clave) {
  try { return JSON.parse(localStorage.getItem(clave) || 'null'); } catch { return null; }
}

function escribir(clave, valor) {
  try {
    if (valor == null) localStorage.removeItem(clave);
    else localStorage.setItem(clave, JSON.stringify(valor));
  } catch { }
}

export function borrarBorradoresDe(modulo) {
  if (modulo != null) cerrados.add(modulo);
  const inicio = `${PREFIJO}${usuarioActual()}:${modulo ?? ''}`;
  try {
    Object.keys(localStorage).filter((k) => (modulo == null ? k.startsWith(`${PREFIJO}${usuarioActual()}:`) : k.startsWith(`${inicio}:`)))
      .forEach((k) => localStorage.removeItem(k));
  } catch { }
}

export function useBorrador(clave, objeto) {
  const route = useRoute();
  const modulo = route.query.m || 'inicio';
  cerrados.delete(modulo);
  const recuperado = ref(false);
  let base = null;
  let temporizador = null;

  const llave = () => `${PREFIJO}${usuarioActual()}:${modulo}:${typeof clave === 'function' ? clave() : clave}`;
  const foto = () => JSON.stringify(objeto);

  function restaurar() {
    base = foto();
    const guardado = leer(llave());
    if (guardado && JSON.stringify(guardado) !== base) {
      Object.assign(objeto, guardado);
      recuperado.value = true;
    } else {
      recuperado.value = false;
    }
  }

  function descartar() {
    escribir(llave(), null);
    recuperado.value = false;
    base = foto();
  }

  function guardarYa() {
    if (base == null || cerrados.has(modulo)) return;
    const actual = foto();
    escribir(llave(), actual === base ? null : JSON.parse(actual));
  }

  watch(() => foto(), () => {
    clearTimeout(temporizador);
    temporizador = setTimeout(guardarYa, 500);
  });
  window.addEventListener('pagehide', guardarYa);
  onUnmounted(() => {
    clearTimeout(temporizador);
    guardarYa();
    window.removeEventListener('pagehide', guardarYa);
  });

  return { recuperado, restaurar, descartar };
}
