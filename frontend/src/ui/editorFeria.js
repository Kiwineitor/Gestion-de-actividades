import { reactive } from 'vue';
import { apiFetch, jsonOError } from '../api';
import { url } from '../config';
import { escucharTopic } from '../ws';
import { toast } from './toast';

export function apiDeFeria(ruta, edicion, opciones) {
  return apiFetch(edicion ? `${ruta}?${new URLSearchParams({ edicion })}` : ruta, opciones);
}

export function editorDeFeria(edicion) {
  const cerrar = [];
  const lista = (ruta, campo, topic) => {
    let proteger = null;
    const estado = reactive({
      [campo]: [],
      protegerLocales(fn) {
        proteger = fn;
        return () => { if (proteger === fn) proteger = null; };
      },
      aplicar(dto) {
        const filas = estado[campo];
        const i = filas.findIndex((p) => p.id === dto.id);
        if (dto.activo === false) {
          if (i >= 0) filas.splice(i, 1);
        } else if (i === -1) {
          filas.push(dto);
        } else {
          const local = proteger?.(dto.id);
          const geometria = local === true ? {
            mapaX: filas[i].mapaX, mapaY: filas[i].mapaY,
            mapaEscala: filas[i].mapaEscala, mapaRotacion: filas[i].mapaRotacion,
          } : local || {};
          Object.assign(filas[i], dto, geometria);
        }
      },
      async cargar() {
        try {
          const filas = await jsonOError(await apiDeFeria(ruta, edicion), 'No se pudo cargar el plano');
          for (const dto of filas) estado.aplicar(dto);
          const ids = new Set(filas.map((p) => p.id));
          estado[campo] = estado[campo].filter((p) => ids.has(p.id));
        } catch (e) {
          toast(e.message, 'error');
        }
      },
      asegurar() {
        cerrar.push(escucharTopic(`${topic}/ediciones/${edicion}`, {
          onMensaje: estado.aplicar, onConectado: estado.cargar,
        }));
        return estado.cargar();
      },
      recargar() { return estado.cargar(); },
    });
    return estado;
  };
  const plano = reactive({
    plano: { ancho: 1600, alto: 1000, version: 0, sinFondo: true },
    get src() {
      if (!this.plano.url) return null;
      return this.plano.propio ? url(this.plano.url) : `${import.meta.env.BASE_URL}mapa.png`;
    },
    get aspecto() { return this.plano.alto / this.plano.ancho; },
    aplicar(datos) { if (datos) this.plano = datos; },
    async asegurar() {
      try {
        this.aplicar(await jsonOError(await apiDeFeria('/api/app/plano', edicion), 'No se pudo cargar el fondo'));
      } catch (e) {
        toast(e.message, 'error');
      }
    },
  });
  return {
    plano,
    puestos: lista('/api/app/puestos', 'puestos', '/topic/puestos'),
    textos: lista('/api/app/plano-textos', 'textos', '/topic/plano-textos'),
    cerrar: () => cerrar.forEach((fn) => fn()),
  };
}
