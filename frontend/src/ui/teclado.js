const MINIMO_TECLADO = 80;

export function observarTeclado() {
  const vv = typeof window !== 'undefined' ? window.visualViewport : null;
  if (!vv) return () => {};

  const aplicar = () => {
    const alto = Math.max(0, Math.round(window.innerHeight - vv.height - vv.offsetTop));
    const raiz = document.documentElement;
    raiz.style.setProperty('--kb', alto > MINIMO_TECLADO ? `${alto}px` : '0px');
    raiz.classList.toggle('con-teclado', alto > MINIMO_TECLADO);
  };

  vv.addEventListener('resize', aplicar);
  vv.addEventListener('scroll', aplicar);
  aplicar();

  return () => {
    vv.removeEventListener('resize', aplicar);
    vv.removeEventListener('scroll', aplicar);
  };
}
