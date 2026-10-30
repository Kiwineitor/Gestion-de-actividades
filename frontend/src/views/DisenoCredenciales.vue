<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import MenuAcciones from '../components/MenuAcciones.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { alertaConAccion } from '../ui/alerta';
import { primeraPaginaEnCanvas } from '../ui/pdf';

const CAMPOS = [
  { id: 'qr', txt: 'Código QR', muestra: 'QR', obligatorio: true },
  { id: 'foto', txt: 'Fotografía', muestra: 'FOTO' },
  { id: 'nombre', txt: 'Nombre completo', muestra: 'NOMBRE APELLIDO EJEMPLO' },
  { id: 'empresa', txt: 'Empresa / actividad', muestra: 'EMPRESA DE EJEMPLO' },
  { id: 'ci', txt: 'C.I.', muestra: '1234567' },
  { id: 'tipo', txt: 'Cargo o tipo', muestra: 'EXPOSITOR' },
  { id: 'codigo', txt: 'Número de puesto', muestra: '12' },
  { id: 'zona', txt: 'Zona / categoría', muestra: 'CATEGORÍA' },
];
const OPCIONES = [
  { id: 'requiereFoto', txt: 'Exigir foto para emitir' },
  { id: 'mayusculas', txt: 'Texto en mayúsculas' },
  { id: 'centrado', txt: 'Texto centrado' },
  { id: 'multilinea', txt: 'Permitir dos líneas' },
  { id: 'fotoCircular', txt: 'Foto redonda' },
];

const ANCHO_CM = 10;
const ALTO_MASIVA_CM = 13;
const PROPORCION_MASIVA = ALTO_MASIVA_CM / ANCHO_CM;
const PX_POR_CM_CSS = 96 / 2.54;
const TARJETA_CM = { ancho: 8.56, alto: 5.398 };
const CLAVE_CALIBRACION = 'credenciales.pxPorCm';

const plantillas = ref([]);
const ediciones = ref([]);
const dependencias = ref([]);
const cargando = ref(true);
const grupos = computed(() => [
  { uso: 'EXPOSITOR', titulo: 'Expositores', items: plantillas.value.filter((p) => p.uso !== 'APOYO') },
  { uso: 'APOYO', titulo: 'Personal de apoyo', items: plantillas.value.filter((p) => p.uso === 'APOYO') },
]);
const nombreEdicion = (id) => ediciones.value.find((e) => e.id === id)?.nombre;

async function cargarCatalogos() {
  try {
    const [e, d] = await Promise.all([apiFetch('/api/app/ediciones'), apiFetch('/api/app/personal-apoyo/dependencias')]);
    if (e.ok) ediciones.value = await e.json();
    if (d.ok) dependencias.value = await d.json();
  } catch { }
}
const selId = ref(null);
const borrador = ref(null);
const original = ref('');
const guardando = ref(false);
const campoSel = ref(null);
const fondos = reactive({});
const lienzo = ref(null);
const escenario = ref(null);
const zoom = ref('ajustar');
const anchoLienzoPx = ref(0);
const pxPorCm = ref(leerCalibracion());
let observador = null;

const seleccionada = computed(() => plantillas.value.find((p) => p.id === selId.value) || null);
const modificado = computed(() => borrador.value != null && JSON.stringify(borrador.value) !== original.value);
const proporcion = computed(() => borrador.value?.proporcion || PROPORCION_MASIVA);
const altoCm = computed(() => ANCHO_CM * proporcion.value);
const deformaMasiva = computed(() => Math.abs(proporcion.value - PROPORCION_MASIVA) > 0.01);
const cajaSel = computed(() => (campoSel.value && borrador.value?.cajas[campoSel.value]) || null);
const etiquetaCampo = (id) => CAMPOS.find((c) => c.id === id)?.txt ?? id;
const cm = (v) => v.toLocaleString('es-BO', { maximumFractionDigits: 1 });
const escalaReal = computed(() => (anchoLienzoPx.value ? Math.round((anchoLienzoPx.value / (ANCHO_CM * pxPorCm.value)) * 100) : 0));
const marcasAncho = computed(() => Array.from({ length: ANCHO_CM + 1 }, (_, i) => i));
const marcasAlto = computed(() => Array.from({ length: Math.floor(altoCm.value) + 1 }, (_, i) => i));
const estiloLienzo = computed(() => (zoom.value === 'real'
  ? { '--prop': proporcion.value, width: `${ANCHO_CM * pxPorCm.value}px` }
  : { '--prop': proporcion.value }));

function leerCalibracion() {
  try {
    const v = Number(localStorage.getItem(CLAVE_CALIBRACION));
    return v > 10 && v < 120 ? v : PX_POR_CM_CSS;
  } catch { return PX_POR_CM_CSS; }
}

function guardarCalibracion(v) {
  pxPorCm.value = v;
  try { localStorage.setItem(CLAVE_CALIBRACION, String(v)); } catch { }
}

function copia(p) {
  return JSON.parse(JSON.stringify({ ...p, cajas: p.cajas || {} }));
}

async function cargar(conservar = selId.value) {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/plantillas-credencial');
    if (!r.ok) { toast('No se pudieron cargar las plantillas', 'error'); return; }
    plantillas.value = await r.json();
    const id = plantillas.value.some((p) => p.id === conservar) ? conservar : plantillas.value[0]?.id ?? null;
    elegir(id, true);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function elegir(id, forzar = false) {
  if (!forzar && modificado.value && !confirm('Hay cambios sin guardar en esta plantilla. ¿Descartarlos?')) return;
  selId.value = id;
  const p = plantillas.value.find((x) => x.id === id);
  borrador.value = p ? copia(p) : null;
  original.value = borrador.value ? JSON.stringify(borrador.value) : '';
  campoSel.value = null;
  if (p) {
    asegurarFondo(p.imagen);
    asegurarFondo(p.imagenReverso);
  }
}

async function asegurarFondo(imagen) {
  if (!imagen || fondos[imagen]) return;
  try {
    const r = await apiFetch(`/api/app/plantillas-credencial/fondo?imagen=${encodeURIComponent(imagen)}`);
    if (!r.ok) return;
    fondos[imagen] = URL.createObjectURL(await r.blob());
  } catch {
    fondos[imagen] = null;
  }
}

function estiloCaja(c) {
  return {
    left: `${c.x * 100}%`,
    top: `${c.y * 100}%`,
    width: `${c.ancho * 100}%`,
    height: `${c.alto * 100}%`,
  };
}

function medidaMm(c) {
  return `${Math.round(c.ancho * ANCHO_CM * 10)} × ${Math.round(c.alto * altoCm.value * 10)} mm`;
}

function cajaPorDefecto(id, indice) {
  const p = proporcion.value;
  if (id === 'qr') return { x: 0.6, y: 0.05, ancho: 0.3, alto: 0.3 / p };
  if (id === 'foto') return { x: 0.1, y: 0.1, ancho: 0.32, alto: 0.32 / p };
  return { x: 0.1, y: Math.min(0.9, 0.5 + indice * 0.07), ancho: 0.75, alto: 0.05 };
}

function alternarCampo(id) {
  const cajas = borrador.value.cajas;
  if (cajas[id]) {
    if (id === 'qr') return;
    delete cajas[id];
    if (campoSel.value === id) campoSel.value = null;
  } else {
    cajas[id] = cajaPorDefecto(id, Object.keys(cajas).length);
    campoSel.value = id;
  }
}

const acotar = (v, min, max) => Math.min(max, Math.max(min, v));
let gesto = null;

function onCajaDown(e, id, modoGesto) {
  e.preventDefault();
  e.stopPropagation();
  campoSel.value = id;
  const r = lienzo.value.getBoundingClientRect();
  e.currentTarget.setPointerCapture(e.pointerId);
  gesto = { id, modo: modoGesto, x0: e.clientX, y0: e.clientY, w: r.width, h: r.height, base: { ...borrador.value.cajas[id] } };
}

function onCajaMove(e) {
  if (!gesto) return;
  const c = borrador.value.cajas[gesto.id];
  const dx = (e.clientX - gesto.x0) / gesto.w;
  const dy = (e.clientY - gesto.y0) / gesto.h;
  const b = gesto.base;
  if (gesto.modo === 'mover') {
    c.x = acotar(b.x + dx, 0, 1 - b.ancho);
    c.y = acotar(b.y + dy, 0, 1 - b.alto);
    return;
  }
  c.ancho = acotar(b.ancho + dx, 0.02, 1 - b.x);
  c.alto = gesto.id === 'qr'
    ? acotar(c.ancho / proporcion.value, 0.02, 1 - b.y)
    : acotar(b.alto + dy, 0.015, 1 - b.y);
  if (gesto.id === 'qr') c.ancho = Math.min(c.ancho, c.alto * proporcion.value);
}

function onCajaUp() {
  if (!gesto) return;
  const c = borrador.value.cajas[gesto.id];
  for (const k of ['x', 'y', 'ancho', 'alto']) c[k] = Math.round(c[k] * 10000) / 10000;
  gesto = null;
}

function enMm(clave) {
  const c = cajaSel.value;
  if (!c) return 0;
  const total = clave === 'x' || clave === 'ancho' ? ANCHO_CM * 10 : altoCm.value * 10;
  return Math.round(c[clave] * total * 10) / 10;
}

function fijarMm(clave, valor) {
  const c = cajaSel.value;
  const mm = Number(String(valor).replace(',', '.'));
  if (!c || !Number.isFinite(mm)) return;
  const total = clave === 'x' || clave === 'ancho' ? ANCHO_CM * 10 : altoCm.value * 10;
  c[clave] = Math.round(acotar(mm / total, 0, 1) * 10000) / 10000;
  if (campoSel.value === 'qr' && clave === 'ancho') c.alto = Math.round((c.ancho / proporcion.value) * 10000) / 10000;
}

function datosDe(b) {
  return {
    codigo: b.codigo, etiqueta: b.etiqueta, detalle: b.detalle, imagen: b.imagen,
    requiereFoto: b.requiereFoto, centrado: b.centrado, mayusculas: b.mayusculas,
    fotoCircular: b.fotoCircular, multilinea: b.multilinea,
    colorTexto: b.colorTexto || null, tipoTexto: b.tipoTexto || null,
    cajas: b.cajas, orden: b.orden, predeterminada: b.predeterminada,
    uso: b.uso || 'EXPOSITOR', idEdicion: b.idEdicion ?? null,
    idDependencia: b.uso === 'APOYO' ? b.idDependencia ?? null : null,
    imagenReverso: b.imagenReverso || null,
  };
}

const datosParaGuardar = () => datosDe(borrador.value);

async function enviarPlantilla(id, datos) {
  const r = await apiFetch(`/api/app/plantillas-credencial/${id}`, { method: 'PATCH', body: JSON.stringify(datos) });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(d.mensaje || 'No se pudo guardar');
  const i = plantillas.value.findIndex((p) => p.id === d.id);
  if (i >= 0) plantillas.value[i] = d;
  if (d.predeterminada) plantillas.value.forEach((p) => { if (p.id !== d.id) p.predeterminada = false; });
  return d;
}

async function guardar() {
  if (!borrador.value || guardando.value) return;
  guardando.value = true;
  try {
    const d = await enviarPlantilla(borrador.value.id, datosParaGuardar());
    elegir(d.id, true);
    toast('Plantilla guardada ✓', 'ok', 1800);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

function descartar() {
  if (seleccionada.value) elegir(seleccionada.value.id, true);
}

async function duplicar(p = seleccionada.value) {
  if (!p) return;
  if (p.id === selId.value && modificado.value && !confirm('Se duplicará la versión guardada; los cambios sin guardar no se copian. ¿Continuar?')) return;
  const r = await apiFetch(`/api/app/plantillas-credencial/${p.id}/duplicar`, { method: 'POST' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) { toast(d.mensaje || 'No se pudo duplicar', 'error'); return; }
  plantillas.value.push(d);
  elegir(d.id, true);
  toast('Copia creada', 'ok', 1600);
}

async function hacerPredeterminada(p) {
  try {
    const d = await enviarPlantilla(p.id, { ...datosDe(p), predeterminada: true, idEdicion: null });
    if (d.id === selId.value) elegir(d.id, true);
    toast(`«${d.etiqueta}» es ahora la plantilla por defecto`, 'ok');
  } catch (e) {
    toast(e.message, 'error');
  }
}

const reemplazo = reactive({ abierto: false, plantilla: null, nuevaId: null, trabajando: false });
const candidatas = computed(() => plantillas.value.filter((x) => x.uso !== 'APOYO' && x.id !== reemplazo.plantilla?.id));

async function eliminar(p = seleccionada.value) {
  if (!p) return;
  if (p.predeterminada) {
    if (!candidatas.value.length && plantillas.value.filter((x) => x.uso !== 'APOYO').length <= 1) {
      toast('Es la única plantilla de expositores. Crea o duplica otra antes de eliminarla.', 'advertencia');
      return;
    }
    Object.assign(reemplazo, { abierto: true, plantilla: p, nuevaId: null, trabajando: false });
    reemplazo.nuevaId = candidatas.value[0]?.id ?? null;
    return;
  }
  const si = await alertaConAccion(`¿Eliminar la plantilla «${p.etiqueta}»? Las credenciales ya impresas no cambian.`, 'advertencia', () => {});
  if (!si) return;
  await borrar(p);
}

async function borrar(p) {
  const r = await apiFetch(`/api/app/plantillas-credencial/${p.id}`, { method: 'DELETE' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) { toast(d.mensaje || 'No se pudo eliminar', 'error'); return false; }
  toast('Plantilla eliminada', 'ok', 1600);
  await cargar(selId.value === p.id ? null : selId.value);
  return true;
}

async function confirmarReemplazo() {
  const nueva = plantillas.value.find((x) => x.id === reemplazo.nuevaId);
  if (!nueva || !reemplazo.plantilla) return;
  reemplazo.trabajando = true;
  try {
    await enviarPlantilla(nueva.id, { ...datosDe(nueva), predeterminada: true, idEdicion: null });
    if (await borrar(reemplazo.plantilla)) reemplazo.abierto = false;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    reemplazo.trabajando = false;
  }
}

function accionesDe(p) {
  return [
    { txt: 'Usar por defecto', deshabilitado: p.uso === 'APOYO' || p.predeterminada, titulo: p.uso === 'APOYO' ? 'Las de personal de apoyo no son predeterminadas' : '', accion: () => hacerPredeterminada(p) },
    { txt: 'Duplicar', accion: () => duplicar(p) },
    { separador: true },
    { txt: p.predeterminada ? 'Eliminar (elegir otra por defecto)' : 'Eliminar', peligro: true, accion: () => eliminar(p) },
  ];
}

async function subirImagen(archivo) {
  const fd = new FormData();
  fd.append('archivo', archivo);
  const r = await apiFetch('/api/app/plantillas-credencial/fondo', { method: 'POST', body: fd });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) { toast(d.mensaje || 'No se pudo subir la imagen', 'error'); return null; }
  return d.imagen;
}

async function proporcionDe(archivo) {
  const url = URL.createObjectURL(archivo);
  try {
    const img = new Image();
    await new Promise((ok, mal) => { img.onload = ok; img.onerror = mal; img.src = url; });
    return img.naturalHeight / img.naturalWidth;
  } finally {
    URL.revokeObjectURL(url);
  }
}

async function cambiarFondo(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo || !borrador.value) return;
  const imagen = await subirImagen(archivo);
  if (!imagen) return;
  fondos[imagen] = URL.createObjectURL(archivo);
  borrador.value.imagen = imagen;
  borrador.value.proporcion = await proporcionDe(archivo);
  toast('Fondo cargado. Revisa la posición de los campos y guarda.', 'info');
}

async function cambiarReverso(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo || !borrador.value) return;
  const imagen = await subirImagen(archivo);
  if (!imagen) return;
  fondos[imagen] = URL.createObjectURL(archivo);
  borrador.value.imagenReverso = imagen;
}

const nueva = reactive({ abierta: false, etiqueta: '', archivo: null, creando: false, uso: 'EXPOSITOR' });

async function crear() {
  if (!nueva.etiqueta.trim() || !nueva.archivo) { toast('Pon un nombre y elige la imagen de fondo', 'advertencia'); return; }
  nueva.creando = true;
  try {
    const imagen = await subirImagen(nueva.archivo);
    if (!imagen) return;
    const prop = await proporcionDe(nueva.archivo);
    const codigo = nueva.etiqueta.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 30);
    const r = await apiFetch('/api/app/plantillas-credencial', {
      method: 'POST',
      body: JSON.stringify({
        codigo, etiqueta: nueva.etiqueta.trim(), imagen, requiereFoto: false, mayusculas: true, uso: nueva.uso,
        cajas: { qr: { x: 0.35, y: 0.05, ancho: 0.3, alto: Math.round((0.3 / prop) * 10000) / 10000 } },
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { toast(d.mensaje || 'No se pudo crear', 'error'); return; }
    fondos[imagen] = URL.createObjectURL(nueva.archivo);
    plantillas.value.push(d);
    Object.assign(nueva, { abierta: false, etiqueta: '', archivo: null, uso: 'EXPOSITOR' });
    elegir(d.id, true);
  } finally {
    nueva.creando = false;
  }
}

const calibrar = reactive({ abierto: false, valor: PX_POR_CM_CSS });

function abrirCalibrar() {
  calibrar.valor = pxPorCm.value;
  calibrar.abierto = true;
}

const previa = reactive({ abierta: false, cargando: false, formato: 'pdf', imagen: null, persona: '', personas: [], real: false });
const lienzoPrevia = ref(null);
const personaId = computed(() => previa.personas.find((p) => p.etiqueta === previa.persona)?.id ?? null);

async function abrirPrevia() {
  previa.abierta = true;
  if (!previa.personas.length) {
    try {
      const r = await apiFetch('/api/app/credenciales');
      if (r.ok) {
        previa.personas = (await r.json()).map((c) => ({ id: c.responsableId, etiqueta: `${c.nombre} · ${c.entidad || ''}` }));
      }
    } catch { }
  }
  await generarPrevia();
}

async function generarPrevia() {
  if (!borrador.value) return;
  previa.cargando = true;
  if (previa.imagen) URL.revokeObjectURL(previa.imagen);
  previa.imagen = null;
  try {
    const r = await apiFetch('/api/app/plantillas-credencial/vista-previa', {
      method: 'POST',
      body: JSON.stringify({ plantilla: datosParaGuardar(), responsableId: personaId.value, formato: previa.formato, anchoCm: ANCHO_CM }),
    });
    if (!r.ok) {
      const d = await r.json().catch(() => ({}));
      toast(d.mensaje || 'No se pudo generar la vista previa', 'error');
      return;
    }
    const blob = await r.blob();
    if (previa.formato === 'png') {
      previa.imagen = URL.createObjectURL(blob);
      return;
    }
    const escala = 3;
    const hoja = await primeraPaginaEnCanvas(await blob.arrayBuffer(), document.createElement('canvas'), escala);
    const ancho = (ANCHO_CM * 72) / 2.54;
    const alto = ancho * proporcion.value;
    const x0 = (hoja.width / escala - ancho) / 2;
    const y0 = (hoja.height / escala - alto) / 2;
    await nextTick();
    const canvas = lienzoPrevia.value;
    if (!canvas) return;
    canvas.width = Math.round(ancho * escala);
    canvas.height = Math.round(alto * escala);
    canvas.getContext('2d').drawImage(hoja, x0 * escala, y0 * escala, ancho * escala, alto * escala,
      0, 0, canvas.width, canvas.height);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    previa.cargando = false;
  }
}

const estiloPrevia = computed(() => (previa.real
  ? { width: `${ANCHO_CM * pxPorCm.value}px`, maxWidth: 'none', maxHeight: 'none' }
  : {}));

watch(() => previa.formato, () => { if (previa.abierta) generarPrevia(); });

function onTecla(e) {
  if (!cajaSel.value || ['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target?.tagName)) return;
  const paso = e.shiftKey ? 0.01 : 0.002;
  const c = cajaSel.value;
  const mover = { ArrowLeft: ['x', -paso], ArrowRight: ['x', paso], ArrowUp: ['y', -paso], ArrowDown: ['y', paso] }[e.key];
  if (mover) {
    e.preventDefault();
    const [k, d] = mover;
    const max = k === 'x' ? 1 - c.ancho : 1 - c.alto;
    c[k] = Math.round(acotar(c[k] + d, 0, max) * 10000) / 10000;
  } else if (e.key === 'Escape') {
    campoSel.value = null;
  }
}

function avisar(e) {
  if (!modificado.value) return;
  e.preventDefault();
  e.returnValue = '';
}

watch(lienzo, (el, antes) => {
  if (antes) observador?.unobserve(antes);
  if (el) observador?.observe(el);
});

onMounted(() => {
  observador = new ResizeObserver(([e]) => { anchoLienzoPx.value = e.contentRect.width; });
  if (lienzo.value) observador.observe(lienzo.value);
  cargarCatalogos();
  cargar();
});
onActivated(() => {
  window.addEventListener('keydown', onTecla);
  window.addEventListener('beforeunload', avisar);
});
onDeactivated(() => {
  window.removeEventListener('keydown', onTecla);
  window.removeEventListener('beforeunload', avisar);
});
onUnmounted(() => {
  observador?.disconnect();
  window.removeEventListener('keydown', onTecla);
  window.removeEventListener('beforeunload', avisar);
  Object.values(fondos).forEach((u) => u && URL.revokeObjectURL(u));
});
</script>

<template>
  <div class="diseno">
    <aside class="lista card">
      <div class="lista-cab">
        <strong>Plantillas</strong>
        <button class="btn btn-sm btn-primario" @click="nueva.abierta = true">＋ Nueva</button>
      </div>
      <div class="lista-cuerpo">
        <p v-if="cargando" class="muted">Cargando…</p>
        <template v-for="g in grupos" :key="g.uso">
          <h5 class="grupo-tit">{{ g.titulo }}</h5>
          <p v-if="!g.items.length" class="muted chico">{{ g.uso === 'APOYO' ? 'Sin plantillas: se usa el diseño de siempre.' : 'Sin plantillas.' }}</p>
          <ul>
            <li v-for="p in g.items" :key="p.id" :class="{ on: p.id === selId }">
              <button class="elegir" @click="elegir(p.id)">
                <span class="nom">{{ p.etiqueta }}</span>
                <span v-if="p.predeterminada" class="badge badge-ok">★ por defecto</span>
                <span v-else-if="p.idEdicion" class="badge badge-muted">{{ nombreEdicion(p.idEdicion) || 'actividad' }}</span>
              </button>
              <MenuAcciones :acciones="accionesDe(p)" :etiqueta="`Acciones de ${p.etiqueta}`" />
            </li>
          </ul>
        </template>
      </div>
    </aside>

    <section v-if="borrador" class="centro">
      <div class="barra">
        <strong class="titulo">{{ borrador.etiqueta }}</strong>
        <span class="medida" :title="`Ancho fijo de ${ANCHO_CM} cm; el alto sale de la proporción del fondo`">📐 {{ cm(ANCHO_CM) }} × {{ cm(altoCm) }} cm</span>
        <span v-if="modificado" class="pendiente">Cambios sin guardar</span>
        <span class="crecer"></span>
        <div class="segmentado" role="group" aria-label="Zoom">
          <button :class="{ on: zoom === 'ajustar' }" @click="zoom = 'ajustar'">Ajustar</button>
          <button :class="{ on: zoom === 'real' }" @click="zoom = 'real'">Tamaño real</button>
        </div>
        <button class="btn btn-sm btn-fantasma" title="Ajustar la regla a tu pantalla" @click="abrirCalibrar">Calibrar</button>
        <button class="btn btn-sm" @click="abrirPrevia">👁 Vista previa</button>
        <button class="btn btn-sm" :disabled="!modificado || guardando" @click="descartar">Descartar</button>
        <button class="btn btn-sm btn-primario" :disabled="!modificado || guardando" @click="guardar">
          {{ guardando ? 'Guardando…' : 'Guardar' }}
        </button>
      </div>
      <p v-if="deformaMasiva" class="aviso-medida">
        La impresión masiva (4 por hoja A4) usa {{ ANCHO_CM }} × {{ ALTO_MASIVA_CM }} cm y este fondo mide {{ cm(ANCHO_CM) }} × {{ cm(altoCm) }} cm: ahí se estiraría.
        Usa una imagen de proporción 10:13 (por ejemplo 1181 × 1535 px a 300 ppp).
      </p>
      <div ref="escenario" class="escenario" :class="{ real: zoom === 'real' }" @pointerdown.self="campoSel = null">
        <div class="marco" :style="{ '--prop': proporcion }">
          <div class="esquina">cm</div>
          <div class="regla horizontal" :style="{ '--div': ANCHO_CM }">
            <span v-for="i in marcasAncho" :key="i" :style="{ left: `${(i / ANCHO_CM) * 100}%` }">{{ i }}</span>
          </div>
          <div class="regla vertical" :style="{ '--div': altoCm }">
            <span v-for="i in marcasAlto" :key="i" :style="{ top: `${(i / altoCm) * 100}%` }">{{ i }}</span>
          </div>
          <div ref="lienzo" class="lienzo" :style="estiloLienzo">
            <img v-if="fondos[borrador.imagen]" :src="fondos[borrador.imagen]" alt="" draggable="false" />
            <div v-for="(c, id) in borrador.cajas" :key="id" class="caja"
                 :class="[`c-${id}`, { sel: campoSel === id, circular: id === 'foto' && borrador.fotoCircular }]"
                 :style="estiloCaja(c)"
                 @pointerdown="onCajaDown($event, id, 'mover')" @pointermove="onCajaMove"
                 @pointerup="onCajaUp" @pointercancel="onCajaUp">
              <span class="rotulo">{{ etiquetaCampo(id) }}</span>
              <span v-if="campoSel === id" class="dim">{{ medidaMm(c) }}</span>
              <span v-if="id !== 'qr' && id !== 'foto'" class="muestra-texto"
                    :style="{ color: borrador.colorTexto || '#111827', justifyContent: borrador.centrado ? 'center' : 'flex-start' }">
                {{ id === 'tipo' && borrador.tipoTexto ? borrador.tipoTexto : CAMPOS.find((x) => x.id === id)?.muestra }}
              </span>
              <span class="asa" @pointerdown="onCajaDown($event, id, 'tamano')" @pointermove="onCajaMove"
                    @pointerup="onCajaUp" @pointercancel="onCajaUp"></span>
            </div>
          </div>
        </div>
      </div>
      <p class="pie-escala muted chico">
        {{ zoom === 'real' ? 'Tamaño real según tu calibración: pon una credencial impresa sobre la pantalla para comparar.' : `Vista al ${escalaReal}% del tamaño real.` }}
        Flechas: mover el campo seleccionado (Shift = más rápido).
      </p>
    </section>

    <aside v-if="borrador" class="props card">
      <div class="props-cuerpo">
        <h4>Plantilla</h4>
        <label class="campo"><span>Nombre</span><input v-model="borrador.etiqueta" class="control" maxlength="80" /></label>
        <label class="campo"><span>Descripción</span><input v-model="borrador.detalle" class="control" maxlength="200" /></label>
        <div class="dos">
          <label class="campo"><span>Cargo por defecto</span><input v-model="borrador.tipoTexto" class="control" maxlength="40" placeholder="EXPOSITOR" /></label>
          <label class="campo"><span>Color del texto</span><input :value="borrador.colorTexto || '#000000'" type="color" class="control control-color"
                   @input="borrador.colorTexto = $event.target.value" /></label>
        </div>
        <div class="dos">
          <label class="campo"><span>Uso</span>
            <select v-model="borrador.uso" class="control" :disabled="seleccionada?.predeterminada">
              <option value="EXPOSITOR">Expositores</option>
              <option value="APOYO">Personal de apoyo</option>
            </select>
          </label>
          <label class="campo"><span>Actividad</span>
            <select v-model="borrador.idEdicion" class="control" :disabled="borrador.predeterminada">
              <option :value="null">Todas</option>
              <option v-for="e in ediciones" :key="e.id" :value="e.id">{{ e.nombre }}{{ e.activa ? ' (actual)' : '' }}</option>
            </select>
          </label>
        </div>
        <label v-if="borrador.uso === 'APOYO'" class="campo"><span>Dependencia</span>
          <select v-model="borrador.idDependencia" class="control">
            <option :value="null">Todas las dependencias</option>
            <option v-for="d in dependencias" :key="d.id" :value="d.id">{{ d.nombre }}</option>
          </select>
        </label>
        <div class="opciones">
          <label v-for="o in OPCIONES" :key="o.id" class="check"><input v-model="borrador[o.id]" type="checkbox" /> {{ o.txt }}</label>
        </div>
        <div class="fondos">
          <label class="btn btn-sm">Cambiar fondo
            <input type="file" accept="image/png,image/jpeg" class="oculto" @change="cambiarFondo" />
          </label>
          <img v-if="borrador.imagenReverso && fondos[borrador.imagenReverso]" :src="fondos[borrador.imagenReverso]" alt="Reverso" class="mini-reverso" />
          <label class="btn btn-sm">{{ borrador.imagenReverso ? 'Cambiar reverso' : 'Subir reverso' }}
            <input type="file" accept="image/png,image/jpeg" class="oculto" @change="cambiarReverso" />
          </label>
          <button v-if="borrador.imagenReverso" class="btn btn-sm btn-fantasma" @click="borrador.imagenReverso = null">Quitar reverso</button>
        </div>

        <h4>Campos</h4>
        <div class="campos">
          <label v-for="c in CAMPOS" :key="c.id" class="chip-campo" :class="{ sel: campoSel === c.id, activo: !!borrador.cajas[c.id] }">
            <input type="checkbox" :checked="!!borrador.cajas[c.id]" :disabled="c.obligatorio" @change="alternarCampo(c.id)" />
            <span @click.prevent="borrador.cajas[c.id] ? (campoSel = c.id) : alternarCampo(c.id)">{{ c.txt }}</span>
          </label>
        </div>

        <div v-if="cajaSel" class="medidas">
          <strong>{{ etiquetaCampo(campoSel) }} <span class="muted">· en milímetros</span></strong>
          <div class="cuatro">
            <label>Desde la izquierda <input type="number" step="0.5" :value="enMm('x')" @change="fijarMm('x', $event.target.value)" /></label>
            <label>Desde arriba <input type="number" step="0.5" :value="enMm('y')" @change="fijarMm('y', $event.target.value)" /></label>
            <label>Ancho <input type="number" step="0.5" :value="enMm('ancho')" @change="fijarMm('ancho', $event.target.value)" /></label>
            <label>Alto <input type="number" step="0.5" :value="enMm('alto')" :disabled="campoSel === 'qr'" @change="fijarMm('alto', $event.target.value)" /></label>
          </div>
        </div>
        <p v-else class="muted chico">Toca un campo en la credencial para moverlo, cambiar su tamaño o escribir sus medidas exactas.</p>
      </div>
    </aside>

    <UiModal v-if="nueva.abierta" titulo="Nueva plantilla" @cerrar="!nueva.creando && (nueva.abierta = false)">
      <div class="formulario">
        <label class="campo"><span>Nombre</span><input v-model="nueva.etiqueta" class="control" maxlength="80" placeholder="Credencial staff" /></label>
        <label class="campo"><span>Uso</span>
          <select v-model="nueva.uso" class="control">
            <option value="EXPOSITOR">Expositores</option>
            <option value="APOYO">Personal de apoyo</option>
          </select>
        </label>
        <label class="campo"><span>Imagen de fondo (PNG o JPG)</span>
          <input type="file" accept="image/png,image/jpeg" class="control" @change="nueva.archivo = $event.target.files?.[0] || null" />
        </label>
        <p class="muted chico">La credencial mide {{ ANCHO_CM }} cm de ancho. Para que coincida con la impresión masiva usa un fondo de proporción 10:13 (por ejemplo 1181 × 1535 px a 300 ppp).</p>
      </div>
      <template #pie>
        <button class="btn btn-fantasma" :disabled="nueva.creando" @click="nueva.abierta = false">Cancelar</button>
        <button class="btn btn-primario" :disabled="nueva.creando" @click="crear">{{ nueva.creando ? 'Creando…' : 'Crear' }}</button>
      </template>
    </UiModal>

    <UiModal v-if="reemplazo.abierto" :titulo="`Eliminar «${reemplazo.plantilla?.etiqueta}»`" @cerrar="!reemplazo.trabajando && (reemplazo.abierto = false)">
      <p>Es la plantilla por defecto. Elige cuál la reemplaza y después se elimina.</p>
      <label class="campo"><span>Nueva plantilla por defecto</span>
        <select v-model="reemplazo.nuevaId" class="control">
          <option v-for="c in candidatas" :key="c.id" :value="c.id">{{ c.etiqueta }}</option>
        </select>
      </label>
      <p class="muted chico">Las credenciales ya impresas no cambian.</p>
      <template #pie>
        <button class="btn btn-fantasma" :disabled="reemplazo.trabajando" @click="reemplazo.abierto = false">Cancelar</button>
        <button class="btn btn-peligro" :disabled="!reemplazo.nuevaId || reemplazo.trabajando" @click="confirmarReemplazo">
          {{ reemplazo.trabajando ? 'Eliminando…' : 'Cambiar y eliminar' }}
        </button>
      </template>
    </UiModal>

    <UiModal v-if="calibrar.abierto" titulo="Calibrar tamaño real" ancho="640px" @cerrar="calibrar.abierto = false">
      <p>Pon una tarjeta bancaria o un carnet ({{ cm(TARJETA_CM.ancho) }} × {{ cm(TARJETA_CM.alto) }} cm) sobre la pantalla y mueve el control hasta que el rectángulo tenga su mismo tamaño.</p>
      <div class="zona-tarjeta">
        <div class="tarjeta" :style="{ width: `${TARJETA_CM.ancho * calibrar.valor}px`, height: `${TARJETA_CM.alto * calibrar.valor}px` }">
          <span>{{ cm(TARJETA_CM.ancho) }} cm</span>
        </div>
      </div>
      <input v-model.number="calibrar.valor" type="range" min="20" max="80" step="0.1" class="deslizador" aria-label="Tamaño" />
      <template #pie>
        <button class="btn btn-fantasma" @click="calibrar.valor = PX_POR_CM_CSS">Restablecer</button>
        <button class="btn btn-primario" @click="guardarCalibracion(calibrar.valor); calibrar.abierto = false; zoom = 'real'">Guardar y ver tamaño real</button>
      </template>
    </UiModal>

    <UiModal v-if="previa.abierta" titulo="Vista previa" ancho="980px" @cerrar="previa.abierta = false">
      <div class="previa">
        <div class="previa-hoja" :class="{ real: previa.real }">
          <p v-if="previa.cargando" class="muted generando">Generando…</p>
          <canvas v-show="previa.formato === 'pdf'" ref="lienzoPrevia" :style="estiloPrevia"></canvas>
          <img v-if="previa.formato === 'png' && previa.imagen" :src="previa.imagen" alt="Vista previa" :style="estiloPrevia" />
        </div>
        <div class="previa-lado">
          <div class="segmentado">
            <button :class="{ on: previa.formato === 'pdf' }" @click="previa.formato = 'pdf'">Impresión</button>
            <button :class="{ on: previa.formato === 'png' }" @click="previa.formato = 'png'">Virtual</button>
          </div>
          <label class="check"><input v-model="previa.real" type="checkbox" /> Tamaño real</label>
          <label class="campo"><span>Persona de ejemplo</span>
            <input v-model="previa.persona" class="control" list="personas-previa" placeholder="Opcional" @change="generarPrevia" />
          </label>
          <datalist id="personas-previa">
            <option v-for="p in previa.personas" :key="p.id" :value="p.etiqueta"></option>
          </datalist>
          <dl class="ficha-medida">
            <div><dt>Credencial suelta</dt><dd>{{ cm(ANCHO_CM) }} × {{ cm(altoCm) }} cm</dd></div>
            <div><dt>Impresión masiva</dt><dd>{{ ANCHO_CM }} × {{ ALTO_MASIVA_CM }} cm · 4 por A4</dd></div>
          </dl>
          <p class="muted chico">Generada por el mismo motor que imprime, con los cambios aún sin guardar.</p>
        </div>
      </div>
    </UiModal>
  </div>
</template>

<style scoped>
.diseno { display: grid; grid-template-columns: 230px minmax(0, 1fr) 300px; gap: 0.8rem; height: 100%; min-height: 0; }
.card { padding: 0.8rem; }
.lista, .props { display: flex; flex-direction: column; min-height: 0; }
.lista-cab { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.4rem; }
.lista-cuerpo, .props-cuerpo { overflow-y: auto; min-height: 0; flex: 1; }
.lista ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
.lista li { display: flex; align-items: center; gap: 2px; border-radius: var(--radio-sm); border: 1px solid transparent; }
.lista li:hover { background: var(--bg); }
.lista li.on { background: var(--acento-suave); border-color: var(--acento); }
.elegir {
  flex: 1; min-width: 0; display: flex; flex-direction: column; align-items: flex-start; gap: 0.2rem;
  border: 0; background: none; color: var(--text); font: inherit; font-size: 0.88rem; padding: 0.45rem 0.5rem; cursor: pointer; text-align: left;
}
.nom { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; font-weight: 600; }
.grupo-tit { margin: 0.6rem 0 0.25rem; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }

.centro { display: flex; flex-direction: column; gap: 0.5rem; min-width: 0; min-height: 0; }
.barra { display: flex; align-items: center; gap: 0.45rem; flex-wrap: wrap; }
.titulo { font-size: 1rem; }
.medida { font-size: 0.8rem; font-weight: 700; padding: 0.2rem 0.55rem; border-radius: 999px; background: var(--acento-suave); color: var(--acento); }
.pendiente { font-size: 0.8rem; color: var(--tramite); font-weight: 600; }
.crecer { flex: 1; }
.aviso-medida { margin: 0; padding: 0.5rem 0.7rem; border-radius: var(--radio-sm); font-size: 0.82rem; background: color-mix(in srgb, var(--tramite) 14%, transparent); color: var(--text); border: 1px solid var(--tramite); }
.escenario {
  flex: 1; min-height: 0; display: grid; place-items: center; padding: 0.6rem; border-radius: var(--radio);
  background: var(--bg); border: 1px solid var(--border); overflow: hidden; container-type: size;
}
.escenario.real { overflow: auto; place-items: start center; }
.marco { --regla: 22px; display: grid; grid-template-columns: var(--regla) auto; grid-template-rows: var(--regla) auto; }
.esquina { font-size: 9px; color: var(--muted); display: grid; place-items: center; }
.regla { position: relative; color: var(--muted); font-size: 9px; }
.regla.horizontal {
  border-bottom: 1px solid var(--muted);
  background:
    repeating-linear-gradient(90deg, var(--muted) 0 1px, transparent 1px calc(100% / var(--div))) bottom / 100% 9px no-repeat,
    repeating-linear-gradient(90deg, var(--border) 0 1px, transparent 1px calc(100% / var(--div) / 10)) bottom / 100% 4px no-repeat;
}
.regla.vertical {
  border-right: 1px solid var(--muted);
  background:
    repeating-linear-gradient(180deg, var(--muted) 0 1px, transparent 1px calc(100% / var(--div))) right / 9px 100% no-repeat,
    repeating-linear-gradient(180deg, var(--border) 0 1px, transparent 1px calc(100% / var(--div) / 10)) right / 4px 100% no-repeat;
}
.regla.horizontal span { position: absolute; top: 1px; transform: translateX(-50%); }
.regla.vertical span { position: absolute; left: 2px; transform: translateY(-50%); }
.regla span:first-child { transform: none; }
.lienzo {
  position: relative; aspect-ratio: calc(1 / var(--prop));
  width: min(calc(100cqw - 22px - 1.2rem), calc((100cqh - 22px - 1.2rem) / var(--prop)));
  box-shadow: var(--sombra-md); background: #fff; user-select: none; touch-action: none;
}
.lienzo img { position: absolute; inset: 0; width: 100%; height: 100%; display: block; pointer-events: none; }
.caja {
  position: absolute; border: 1.5px dashed rgba(79, 70, 229, 0.9); background: rgba(79, 70, 229, 0.08);
  cursor: move; display: flex; align-items: center; overflow: visible;
}
.caja.sel { border-style: solid; border-color: var(--acento); background: rgba(79, 70, 229, 0.16); z-index: 2; }
.caja.c-qr { background: repeating-linear-gradient(45deg, rgba(17, 24, 39, 0.25) 0 6px, rgba(255, 255, 255, 0.5) 6px 12px); }
.caja.c-foto { background: rgba(148, 163, 184, 0.45); }
.caja.circular { border-radius: 50%; }
.rotulo {
  position: absolute; top: 0; left: 0; font-size: 10px; line-height: 1; padding: 2px 4px;
  background: var(--acento); color: #fff; border-bottom-right-radius: 4px; pointer-events: none; white-space: nowrap;
}
.dim {
  position: absolute; bottom: -18px; left: 50%; transform: translateX(-50%); font-size: 10px; font-weight: 700;
  background: #111827; color: #fff; padding: 1px 6px; border-radius: 999px; white-space: nowrap; pointer-events: none;
}
.caja.circular .rotulo { left: 50%; transform: translateX(-50%); border-radius: 0 0 4px 4px; }
.muestra-texto {
  display: flex; width: 100%; padding: 0 4px; font-weight: 800; font-size: clamp(8px, 1.2vw, 15px);
  white-space: nowrap; overflow: hidden; pointer-events: none;
}
.asa {
  position: absolute; right: -4px; bottom: -4px; width: 12px; height: 12px;
  background: var(--acento); cursor: nwse-resize; border-radius: 3px; border: 2px solid #fff;
}
.pie-escala { margin: 0; }

.props-cuerpo { display: flex; flex-direction: column; gap: 0.55rem; }
.props h4 { margin: 0.2rem 0 0; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); }
.dos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; }
.control-color { height: 40px; padding: 2px; }
.opciones { display: grid; grid-template-columns: 1fr 1fr; gap: 0.3rem 0.5rem; }
.check { display: flex; align-items: center; gap: 0.4rem; font-size: 0.84rem; cursor: pointer; }
.fondos { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.mini-reverso { width: 30px; height: 38px; object-fit: cover; border-radius: 4px; border: 1px solid var(--border); }
.oculto { display: none; }
.campos { display: flex; flex-wrap: wrap; gap: 0.3rem; }
.chip-campo {
  display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.25rem 0.55rem; border-radius: 999px;
  border: 1px solid var(--border); font-size: 0.8rem; cursor: pointer;
}
.chip-campo input { margin: 0; }
.chip-campo.activo { border-color: var(--acento); }
.chip-campo.sel { background: var(--acento-suave); color: var(--acento); font-weight: 700; }
.medidas { display: flex; flex-direction: column; gap: 0.4rem; border-top: 1px solid var(--border); padding-top: 0.6rem; }
.cuatro { display: grid; grid-template-columns: 1fr 1fr; gap: 0.4rem; }
.cuatro label { display: flex; flex-direction: column; gap: 0.15rem; font-size: 0.74rem; color: var(--muted); }
.cuatro input {
  padding: 0.3rem 0.4rem; border: 1px solid var(--border); border-radius: 6px;
  background: var(--panel); color: var(--text); font: inherit; font-size: 0.85rem;
}
.chico { font-size: 0.8rem; margin: 0; }
.formulario { display: flex; flex-direction: column; gap: 0.8rem; }

.segmentado { display: inline-flex; padding: 2px; border-radius: 9px; background: var(--bg); border: 1px solid var(--border); }
.segmentado button {
  border: none; background: transparent; color: var(--text); font: inherit; font-size: 0.8rem; font-weight: 600;
  padding: 0.25rem 0.7rem; border-radius: 7px; cursor: pointer;
}
.segmentado button.on { background: var(--acento); color: var(--acento-texto); }

.zona-tarjeta { display: grid; place-items: center; padding: 1rem; background: var(--bg); border-radius: var(--radio-sm); overflow: auto; }
.tarjeta { border: 2px solid var(--acento); border-radius: 10px; background: var(--acento-suave); display: grid; place-items: center; font-weight: 700; color: var(--acento); }
.deslizador { width: 100%; }

.previa { display: grid; grid-template-columns: minmax(0, 1fr) 240px; gap: 1rem; min-height: 0; }
.previa-hoja {
  --alto-hoja: min(72vh, 760px);
  position: relative; display: flex; align-items: center; justify-content: center; background: var(--bg); border-radius: var(--radio-sm);
  padding: 0.8rem; height: var(--alto-hoja); box-sizing: border-box; overflow: hidden;
}
.previa-hoja.real { overflow: auto; align-items: flex-start; }
.previa-hoja canvas, .previa-hoja img {
  max-width: 100%; max-height: calc(var(--alto-hoja) - 1.6rem); width: auto; height: auto;
  box-shadow: var(--sombra-md); background: #fff; flex: none;
}
.generando { position: absolute; top: 0.6rem; left: 0.8rem; margin: 0; }
.previa-lado { display: flex; flex-direction: column; gap: 0.7rem; }
.ficha-medida { margin: 0; display: flex; flex-direction: column; gap: 0.4rem; font-size: 0.84rem; }
.ficha-medida dt { color: var(--muted); font-size: 0.74rem; text-transform: uppercase; letter-spacing: 0.04em; }
.ficha-medida dd { margin: 0; font-weight: 700; }

@media (max-width: 1100px) {
  .diseno { grid-template-columns: 1fr; height: auto; }
  .lista-cuerpo { max-height: 220px; }
  .escenario { height: 70vh; flex: none; }
  .previa { grid-template-columns: 1fr; }
  .previa-hoja { --alto-hoja: 60vh; }
}
</style>
