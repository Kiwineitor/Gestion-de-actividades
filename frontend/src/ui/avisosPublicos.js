import { ref } from 'vue';

export const avisosPublicos = ref([]);
let secuencia = 0;

export function avisoPublico(texto, tipo = 'info', ms = 4200) {
  const id = ++secuencia;
  avisosPublicos.value = [...avisosPublicos.value.slice(-2), { id, texto, tipo }];
  setTimeout(() => { avisosPublicos.value = avisosPublicos.value.filter((a) => a.id !== id); }, ms);
}
