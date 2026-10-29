import { defineStore } from 'pinia';
import { ref } from 'vue';
import { apiFetch } from '../api';
import { escucharTopic } from '../ws';

export const usePlanoTextosStore = defineStore('planoTextos', () => {
  const textos = ref([]);
  const cargando = ref(false);
  const enVivo = ref(false);

  let cerrarCanal = null;
  let promesa = null;

  function aplicar(dto) {
    if (!dto || dto.id == null) return;
    const i = textos.value.findIndex((t) => t.id === dto.id);
    if (dto.activo === false) {
      if (i >= 0) textos.value.splice(i, 1);
      return;
    }
    if (i < 0) textos.value.push(dto);
    else textos.value[i] = dto;
  }

  async function cargar() {
    cargando.value = true;
    try {
      const r = await apiFetch('/api/app/plano-textos');
      if (r.ok) textos.value = await r.json();
    } catch {
    } finally {
      cargando.value = false;
    }
  }

  function asegurar() {
    if (promesa) return promesa;
    promesa = cargar();
    if (!cerrarCanal) {
      cerrarCanal = escucharTopic('/topic/plano-textos', {
        onMensaje: aplicar,
        onConectado: () => { enVivo.value = true; cargar(); },
        onCerrado: () => { enVivo.value = false; },
      });
    }
    return promesa;
  }

  function desconectar() {
    if (cerrarCanal) cerrarCanal();
    cerrarCanal = null;
    promesa = null;
    enVivo.value = false;
    textos.value = [];
  }

  return { textos, cargando, enVivo, asegurar, cargar, aplicar, desconectar };
});
