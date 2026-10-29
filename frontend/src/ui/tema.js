import { ref } from 'vue';

const GUARDADO = localStorage.getItem('tema');
export const tema = ref(GUARDADO || 'auto');

function aplicar() {
  const raiz = document.documentElement;
  if (tema.value === 'auto') raiz.removeAttribute('data-theme');
  else raiz.setAttribute('data-theme', tema.value);
}

export function alternarTema() {
  tema.value = tema.value === 'auto' ? 'dark' : tema.value === 'dark' ? 'light' : 'auto';
  if (tema.value === 'auto') localStorage.removeItem('tema');
  else localStorage.setItem('tema', tema.value);
  aplicar();
}

export function iniciarTema() {
  aplicar();
}
