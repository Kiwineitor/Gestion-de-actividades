const espacios = (v) => v.replace(/ {2,}/g, ' ').replace(/^ /, '');

const FILTROS = {
  letras: (v) => espacios(v.toLocaleUpperCase('es-BO').replace(/[^\p{L} ]/gu, '')),
  nombre: (v) => espacios(v.toLocaleUpperCase('es-BO').replace(/[\p{N}\p{Cc}]/gu, '')),
  telefono: (v) => (v.includes('+') ? '+' : '') + v.replace(/\D/g, ''),
};

export const vFiltro = {
  mounted(el, binding) {
    const limpiar = FILTROS[binding.value];
    if (!limpiar) return;
    const aplicar = (e) => {
      if (e?.isComposing) return;
      const antes = el.value;
      const despues = limpiar(antes);
      if (despues === antes) return;
      const pos = el.selectionStart ?? antes.length;
      const nuevaPos = limpiar(antes.slice(0, pos)).length;
      el.value = despues;
      try { el.setSelectionRange(nuevaPos, nuevaPos); } catch { }
      el.dispatchEvent(new Event('input', { bubbles: true }));
    };
    el._filtroEntrada = aplicar;
    el.addEventListener('input', aplicar);
    el.addEventListener('compositionend', aplicar);
  },
  unmounted(el) {
    el.removeEventListener('input', el._filtroEntrada);
    el.removeEventListener('compositionend', el._filtroEntrada);
  },
};
