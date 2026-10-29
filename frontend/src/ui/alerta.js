import { ref, reactive } from 'vue';
import { registrarError } from './registroErrores.js';

export const alertaVisible = ref(false);
export const alertaConfig = reactive({
  titulo: '',
  mensaje: '',
  tipo: 'info',
  ms: 4000,
  mostrarConfirmar: false,
  onConfirmar: null,
  onCerrar: null,
  serie: 0,
});

const TITULOS = { ok: 'Éxito', error: 'Error', advertencia: 'Atención', info: 'Información' };

let resolvePromesa = null;

function mostrar(opciones, resolve) {
  if (opciones.tipo === 'error') registrarError(opciones.mensaje);
  if (resolvePromesa) resolvePromesa(false);
  resolvePromesa = resolve;
  Object.assign(alertaConfig, {
    titulo: opciones.titulo || TITULOS[opciones.tipo] || TITULOS.info,
    mensaje: opciones.mensaje,
    tipo: opciones.tipo,
    ms: opciones.ms,
    mostrarConfirmar: Boolean(opciones.onConfirmar),
    onConfirmar: opciones.onConfirmar || null,
    onCerrar: null,
    serie: alertaConfig.serie + 1,
  });
  alertaVisible.value = true;
}

export function alerta(mensaje, tipo = 'info', ms = 4000) {
  return new Promise((resolve) => mostrar({ mensaje, tipo, ms }, resolve));
}

export function aviso(mensaje, tipo = 'ok', ms = 2000, titulo = null) {
  const porDefecto = tipo === 'ok' ? '¡Listo!' : tipo === 'error' ? 'No se pudo completar' : null;
  return new Promise((resolve) => mostrar({ mensaje, tipo, ms, titulo: titulo || porDefecto }, resolve));
}

export function alertaConAccion(mensaje, tipo, onConfirmar, ms = 0) {
  return new Promise((resolve) =>
    mostrar({ mensaje, tipo, ms, onConfirmar: () => onConfirmar?.() }, resolve));
}

export function cerrar(confirmado = false) {
  alertaVisible.value = false;
  if (resolvePromesa) {
    resolvePromesa(confirmado);
    resolvePromesa = null;
  }
  alertaConfig.onConfirmar = null;
  alertaConfig.onCerrar = null;
}
