import { apiFetch } from '../api.js';
import { toast } from './toast.js';

export async function descargarCredencialVirtual(responsableId, nombre, carpeta = null) {
  const archivo = `credencial-${nombreSeguro(nombre || responsableId)}.png`;
  try {
    const res = await descargarArchivo(
      `/api/app/credenciales/${responsableId}/virtual`, archivo, { carpeta });
    toast(res.destino === 'telefono'
      ? (res.carpeta ? `Guardada en Documentos › ${res.carpeta}` : `Credencial guardada en Documentos`)
      : 'Credencial descargada', 'ok');
    return true;
  } catch (e) {
    toast(`No se pudo generar la credencial: ${e.message}`, 'error');
    return false;
  }
}

export async function descargarPdf(ruta, nombreArchivo, opciones = {}) {
  return descargarArchivo(ruta, nombreArchivo, opciones);
}

export async function descargarArchivo(ruta, nombreArchivo, opciones = {}) {
  const { carpeta, ...peticion } = opciones;
  const r = await apiFetch(ruta, peticion);
  if (!r.ok) {
    const motivo = await r.text().catch(() => '');
    throw new Error(motivo && motivo.length < 200 ? motivo : 'El servidor no devolvió el documento');
  }
  const blob = await r.blob();

  if (esNativo()) {
    return guardarEnElTelefono(blob, nombreArchivo, carpeta);
  }
  guardarEnElNavegador(blob, carpeta ? `${nombreSeguro(carpeta)} - ${nombreArchivo}` : nombreArchivo);
  return { destino: 'navegador' };
}

function esNativo() {
  return typeof window !== 'undefined'
      && window.Capacitor
      && typeof window.Capacitor.isNativePlatform === 'function'
      && window.Capacitor.isNativePlatform();
}

function guardarEnElNavegador(blob, nombreArchivo) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombreArchivo;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

export function nombreSeguro(v) {
  return String(v || '')
    .replace(/[^\p{L}\p{N} .\-_]/gu, '-')
    .replace(/-{2,}/g, '-')
    .replace(/\s{2,}/g, ' ')
    .trim()
    .slice(0, 60) || 'Sin nombre';
}

async function guardarEnElTelefono(blob, nombreArchivo, carpeta) {
  const { Filesystem, Directory } = await import('@capacitor/filesystem');
  const datos = await aBase64(blob);
  const ruta = carpeta ? `${nombreSeguro(carpeta)}/${nombreArchivo}` : nombreArchivo;
  const { uri } = await Filesystem.writeFile({
    path: ruta,
    data: datos,
    directory: Directory.Documents,
    recursive: true,
  });
  return { destino: 'telefono', uri, carpeta: carpeta ? nombreSeguro(carpeta) : null };
}

function aBase64(blob) {
  return new Promise((resolve, reject) => {
    const lector = new FileReader();
    lector.onerror = () => reject(new Error('No se pudo leer el documento'));
    lector.onloadend = () => resolve(String(lector.result).split(',')[1] ?? '');
    lector.readAsDataURL(blob);
  });
}

export async function compartirRecibo(inscripcionId) {
  const nombre = `nota-venta-${inscripcionId}.pdf`;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${inscripcionId}/recibo`);
    if (!r.ok) throw new Error('El servidor no devolvió el recibo');
    const blob = await r.blob();

    if (esNativo()) {
      const { Filesystem, Directory } = await import('@capacitor/filesystem');
      const { Share } = await import('@capacitor/share');
      const { uri } = await Filesystem.writeFile({
        path: nombre, data: await aBase64(blob), directory: Directory.Cache, recursive: true,
      });
      await Share.share({ title: 'Recibo de la venta', url: uri });
      return true;
    }

    const archivo = new File([blob], nombre, { type: 'application/pdf' });
    if (navigator.canShare?.({ files: [archivo] })) {
      await navigator.share({ files: [archivo], title: 'Recibo de la venta' });
      return true;
    }
    guardarEnElNavegador(blob, nombre);
    toast('Tu navegador no puede compartir archivos: el recibo se descargó', 'info');
    return true;
  } catch (e) {
    if (e?.name === 'AbortError') return false;
    toast(`No se pudo compartir el recibo: ${e.message}`, 'error');
    return false;
  }
}

export async function descargarRecibo(inscripcionId, carpeta = null) {
  try {
    const res = await descargarPdf(`/api/app/inscripciones/${inscripcionId}/recibo`,
                                   `nota-venta-${inscripcionId}.pdf`, { carpeta });
    toast(res.destino === 'telefono'
      ? (res.carpeta ? `Recibo guardado en Documentos › ${res.carpeta}` : 'Recibo guardado en Documentos')
      : 'Recibo descargado', 'ok');
    return true;
  } catch (e) {
    toast(`No se pudo descargar el recibo: ${e.message}. Puedes bajarlo desde Mis ventas.`, 'error');
    return false;
  }
}

export async function descargarReciboExtra(inscripcionId, responsableId, entidadNombre = null) {
  try {
    const carpeta = entidadNombre ? nombreSeguro(entidadNombre) : null;
    const res = await descargarPdf(`/api/app/inscripciones/${inscripcionId}/responsables/${responsableId}/recibo-extra`,
                                   `recibo-extra-${responsableId}.pdf`, { carpeta });
    toast(res.destino === 'telefono'
      ? (res.carpeta ? `Recibo extra guardado en Documentos › ${res.carpeta}` : 'Recibo extra guardado en Documentos')
      : 'Recibo extra descargado', 'ok');
    return true;
  } catch (e) {
    toast(`No se pudo descargar el recibo extra: ${e.message}`, 'error');
    return false;
  }
}
