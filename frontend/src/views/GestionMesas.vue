<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PlanoMesas from '../components/PlanoMesas.vue';
import ModalFondo from '../components/ModalFondo.vue';
import ModalForma from '../components/ModalForma.vue';
import UiModal from '../components/UiModal.vue';
import { imagenPlanoMesas, LEYENDA_MESAS } from '../ui/imagenPlano';
import { guardarBlob, nombreSeguro } from '../ui/descargas';
import { apiFetch, jsonOError } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { confirmar } from '../ui/alerta';
import { escucharTopicPublico } from '../ws.js';
import { aModulo } from '../modulos';
import { useBorrador } from '../ui/borradores';
import CambiarPlano from '../components/CambiarPlano.vue';
import ModalEntregaReserva from '../components/ModalEntregaReserva.vue';

const route = useRoute();
const router = useRouter();
const entregaId = ref(null);

const ESTADO_MESA = { L: 'Libre', T: 'Bloqueada', R: 'En revisión', O: 'Vendida', X: 'Fuera de venta' };

const selId = ref(null);
const detalle = ref(null);
const pestana = ref('evento');
const cargando = ref(false);
const guardando = ref(false);

const form = reactive({});
const mesasEdit = ref([]);
const elementos = ref([]);
const mesaSel = ref(null);
const elementoSel = ref(null);
const cambiosMesas = ref(false);
const completo = ref(false);
const rejilla = ref(false);
const ayuda = ref(false);
const modalFondo = ref(false);
const subiendoFondo = ref(false);
const vistaPrevia = ref(false);
const modalForma = ref(false);
const aplicandoForma = ref(false);
const generandoImagen = ref(false);
const nuevoPase = ref('');
const original = ref('');
const plano = reactive({ mesas: mesasEdit, elementos });
const CLAVE_AYUDA = 'ayuda.plano-mesas.vista';
let cerrarCanal = null;
let pendiente = null;

const borrador = useBorrador(() => `evento-${selId.value ?? 'nuevo'}`, form);
const borradorPlano = useBorrador(() => `plano-${selId.value ?? 'nuevo'}`, plano);

const aspecto = computed(() => (detalle.value?.planoAncho && detalle.value?.planoAlto ? detalle.value.planoAlto / detalle.value.planoAncho : 0.62));
const enlacePublico = computed(() => (detalle.value ? `${location.origin}${router.resolve({ path: '/', query: { evento: detalle.value.id } }).href}` : ''));
const mesaElegida = computed(() => mesasEdit.value.find((m) => (m.id ?? m.clave) === mesaSel.value) || null);
const elementoElegido = computed(() => elementos.value.find((e) => e.id === elementoSel.value) || null);
const pista = computed(() => {
  if (elementoElegido.value) return 'Arrastra el texto para moverlo · la esquina blanca cambia su tamaño · flechas para ajuste fino · Supr para borrarlo';
  if (mesaElegida.value) return 'Arrastra la mesa para moverla · edita sus datos en el panel · flechas para ajuste fino';
  return rejilla.value
    ? 'Rejilla e imán activos: al mover, todo se acomoda a la cuadrícula para quedar alineado'
    : 'Arrastra mesas y textos para ubicarlos · toca uno para editarlo · toca un espacio vacío para soltar la selección';
});
const tiposPase = computed(() => String(form.tiposPase || '').split('\n').map((t) => t.trim()).filter(Boolean));
const cambiosEvento = computed(() => original.value !== '' && JSON.stringify(form) !== original.value);

function fijarOriginal() {
  nextTick(() => { original.value = JSON.stringify(form); });
}

function agregarPase() {
  const t = nuevoPase.value.trim().replace(/\s+/g, ' ').slice(0, 40);
  if (!t) return;
  if (!tiposPase.value.some((x) => x.toLowerCase() === t.toLowerCase())) form.tiposPase = [...tiposPase.value, t].join('\n');
  nuevoPase.value = '';
}

function quitarPase(t) {
  form.tiposPase = tiposPase.value.filter((x) => x !== t).join('\n');
}
const totales = computed(() => ({
  mesas: mesasEdit.value.length,
  personas: mesasEdit.value.reduce((s, m) => s + (Number(m.capacidad) || 0), 0),
  vendidas: mesasEdit.value.filter((m) => m.estado === 'O').length,
}));

function vacio() {
  const manana = new Date(Date.now() + 7 * 864e5);
  const fecha = manana.toISOString().slice(0, 10);
  return {
    nombre: '', localNombre: '', localDireccion: '', descripcion: '', fechaInicio: `${fecha}T22:00`, horaLimite: `${fecha}T23:30`,
    minutosBloqueo: 10, pagoBanco: '', pagoCuenta: '', pagoTitular: '', pagoInstrucciones: '', celularContacto: '',
    politicas: '', publicado: false, tiposPase: 'General\nVIP',
  };
}

async function abrir(id) {
  if (cambiosMesas.value && !(await confirmar('Hay cambios sin guardar en el plano. ¿Descartarlos?', { titulo: 'Cambios sin guardar', aceptar: 'Descartar cambios' }))) return;
  selId.value = id;
  cargando.value = true;
  try {
    await cargarDetalle();
    borrador.restaurar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
  cerrarCanal?.();
  cerrarCanal = escucharTopicPublico(`/topic/publico/mesas/${id}`, { onMensaje: programarRecarga });
}

async function cargarDetalle() {
  const d = await jsonOError(await apiFetch(`/api/app/mesas/eventos/${selId.value}`), 'No se pudo cargar el evento');
  detalle.value = d;
  Object.assign(form, vacio(), {
    ...d, fechaInicio: d.fechaInicio?.slice(0, 16) || '', horaLimite: d.horaLimite?.slice(0, 16) || '',
    politicas: d.politicasPropias || '', tiposPase: (d.tiposPase || []).join('\n'),
  });
  delete form.mesas;
  original.value = JSON.stringify(form);
  mesasEdit.value = d.mesas.map((m) => ({ ...m }));
  elementos.value = (d.elementos || []).map((e) => ({ ...e }));
  cambiosMesas.value = false;
  borradorPlano.restaurar();
  if (borradorPlano.recuperado.value) cambiosMesas.value = true;
}

function programarRecarga() {
  clearTimeout(pendiente);
  pendiente = setTimeout(async () => {
    const d = await jsonOError(await apiFetch(`/api/app/mesas/eventos/${selId.value}`), '').catch(() => null);
    if (!d) return;
    detalle.value = { ...detalle.value, mesas: d.mesas };
    if (!cambiosMesas.value) mesasEdit.value = d.mesas.map((m) => ({ ...m }));
    else for (const m of mesasEdit.value) {
      const actual = d.mesas.find((x) => x.id === m.id);
      if (actual) Object.assign(m, { estado: actual.estado, reservadoPor: actual.reservadoPor, reservaId: actual.reservaId });
    }
  }, 400);
}

function nuevoEvento() {
  cerrarCanal?.();
  selId.value = null;
  detalle.value = null;
  mesasEdit.value = [];
  Object.assign(form, vacio());
  original.value = JSON.stringify(form);
  borrador.restaurar();
  pestana.value = 'evento';
}

async function guardarEvento() {
  guardando.value = true;
  try {
    const cuerpo = { ...form, minutosBloqueo: Number(form.minutosBloqueo) || 10 };
    const nuevo = !selId.value;
    const r = nuevo
      ? await apiFetch('/api/app/mesas/eventos', { method: 'POST', body: JSON.stringify(cuerpo) })
      : await apiFetch(`/api/app/mesas/eventos/${selId.value}`, { method: 'PATCH', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    borrador.descartar();
    fijarOriginal();
    toast(nuevo ? 'Evento creado. Ahora arma su plano.' : d.mensaje, 'ok', 2200);
    router.replace({ query: { m: 'mesas', evento: d.id, tab: nuevo ? 'plano' : pestana.value } });
    if (!nuevo) await cargarDetalle();
  } finally {
    guardando.value = false;
  }
}

async function alternarPublicado() {
  const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/publicar`, { method: 'POST', body: JSON.stringify({ publicado: !detalle.value.publicado }) });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo cambiar', 'error'); return; }
  toast(d.mensaje, 'ok');
  detalle.value.publicado = !detalle.value.publicado;
  form.publicado = detalle.value.publicado;
}

async function subirImagen(evento, tipo) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (archivo) await subirArchivo(archivo, tipo);
}

async function subirArchivo(archivo, tipo) {
  if (!archivo || !selId.value) return false;
  const datos = new FormData();
  datos.append('archivo', archivo);
  const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/${tipo}`, { method: 'POST', body: datos });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir la imagen', 'error'); return false; }
  const d2 = await jsonOError(await apiFetch(`/api/app/mesas/eventos/${selId.value}`), '').catch(() => null);
  if (d2) detalle.value = { ...detalle.value, planoUrl: d2.planoUrl, planoAncho: d2.planoAncho, planoAlto: d2.planoAlto, pagoQrUrl: d2.pagoQrUrl, logoUrl: d2.logoUrl };
  toast(d.mensaje, 'ok', 1600);
  return true;
}

async function elegirFondo(archivo) {
  subiendoFondo.value = true;
  try {
    if (await subirArchivo(archivo, 'plano')) modalFondo.value = false;
  } finally {
    subiendoFondo.value = false;
  }
}

async function quitarFondo() {
  const si = await confirmar('Las mesas, los textos y las zonas se quedan donde están; solo desaparece la imagen de atrás.', { titulo: '¿Quitar el fondo?', aceptar: 'Sí, quitar fondo' });
  if (!si) return;
  const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/plano`, { method: 'DELETE' });
  if (!r.ok) { toast('No se pudo quitar el fondo', 'error'); return; }
  detalle.value = { ...detalle.value, planoUrl: null };
  modalFondo.value = false;
  toast('Fondo quitado', 'ok', 1500);
}

async function aplicarForma(forma) {
  aplicandoForma.value = true;
  try {
    if (cambiosMesas.value) await guardarMesas();
    if (cambiosMesas.value) return;
    const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/forma`, { method: 'PATCH', body: JSON.stringify(forma) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo cambiar la forma', 'error'); return; }
    await cargarDetalle();
    modalForma.value = false;
    toast('Forma del plano actualizada', 'ok', 1600);
  } finally {
    aplicandoForma.value = false;
  }
}

async function descargarImagen() {
  generandoImagen.value = true;
  try {
    const blob = await imagenPlanoMesas({
      titulo: detalle.value?.nombre, subtitulo: [detalle.value?.localNombre, detalle.value?.fechaTexto].filter(Boolean).join(' · '),
      aspecto: aspecto.value, fondoUrl: detalle.value?.planoUrl ? urlApi(detalle.value.planoUrl) : null,
      mesas: mesasEdit.value, elementos: elementos.value,
    });
    const r = await guardarBlob(blob, `plano-${nombreSeguro(detalle.value?.nombre || 'evento')}.png`);
    toast(r.destino === 'telefono' ? 'Imagen guardada en Documentos' : 'Imagen descargada', 'ok', 1800);
  } catch (e) {
    toast(`No se pudo generar la imagen: ${e.message}`, 'error');
  } finally {
    generandoImagen.value = false;
  }
}

async function quitarLogo() {
  const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/logo`, { method: 'DELETE' });
  if (r.ok) detalle.value = { ...detalle.value, logoUrl: null };
}

async function quitarQr() {
  const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/qr-pago`, { method: 'DELETE' });
  if (r.ok) detalle.value = { ...detalle.value, pagoQrUrl: null };
}

function agregarMesa() {
  const numeros = mesasEdit.value.map((m) => parseInt(m.codigo, 10)).filter(Number.isFinite);
  const ultima = mesasEdit.value[mesasEdit.value.length - 1];
  const nueva = {
    clave: `n${Date.now()}`, codigo: String((numeros.length ? Math.max(...numeros) : 0) + 1),
    zona: ultima?.zona || 'General', color: ultima?.color || '#6d5bd0', capacidad: ultima?.capacidad || 4,
    precio: ultima?.precio || 0, beneficios: ultima?.beneficios || '', forma: ultima?.forma || 'circulo',
    tamano: ultima?.tamano || 0.06, mapaX: 0.5, mapaY: 0.5, estado: 'L',
  };
  mesasEdit.value.push(nueva);
  elementoSel.value = null;
  mesaSel.value = nueva.clave;
  cambiosMesas.value = true;
}

function moverMesa(m, x, y) {
  m.mapaX = Math.round(x * 1000) / 1000;
  m.mapaY = Math.round(y * 1000) / 1000;
  cambiosMesas.value = true;
}

function elegirMesa(m) {
  elementoSel.value = null;
  mesaSel.value = m.id ?? m.clave;
}

function elegirElemento(e) {
  mesaSel.value = null;
  elementoSel.value = e.id;
}

function soltarSeleccion() {
  mesaSel.value = null;
  elementoSel.value = null;
}

function nuevoId() {
  return `e${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

function agregarElemento(tipo) {
  const e = tipo === 'zona'
    ? { id: nuevoId(), tipo: 'zona', texto: 'ZONA', x: 0.35, y: 0.35, ancho: 0.3, alto: 0.2, color: '#ffffff', fondo: '#7c5cff', tamano: 0.025, rotacion: 0 }
    : { id: nuevoId(), tipo: 'texto', texto: 'TEXTO', x: 0.4, y: 0.45, ancho: 0.2, alto: 0.07, color: '#ffffff', fondo: null, tamano: 0.03, rotacion: 0 };
  elementos.value.push(e);
  elegirElemento(e);
  cambiosMesas.value = true;
}

function cambiarElemento(e, cambios) {
  Object.assign(e, cambios);
  cambiosMesas.value = true;
}

function duplicarElemento(e) {
  const copia = { ...e, id: nuevoId(), x: Math.min(1 - e.ancho, e.x + 0.03), y: Math.min(1 - e.alto, e.y + 0.03) };
  elementos.value.push(copia);
  elegirElemento(copia);
  cambiosMesas.value = true;
}

function quitarElemento(e) {
  elementos.value = elementos.value.filter((x) => x.id !== e.id);
  elementoSel.value = null;
  cambiosMesas.value = true;
}

function alternarCompleto() {
  completo.value = !completo.value;
  try {
    if (completo.value && !document.fullscreenElement) document.documentElement.requestFullscreen?.().catch(() => {});
    if (!completo.value && document.fullscreenElement) document.exitFullscreen?.().catch(() => {});
  } catch { }
}

function abrirEnPestana() {
  const destino = router.resolve({ path: '/admin', query: { m: 'mesas', evento: selId.value, tab: 'plano', completo: '1' } }).href;
  window.open(destino, '_blank', 'noopener');
}

function cerrarAyuda() {
  ayuda.value = false;
  try { localStorage.setItem(CLAVE_AYUDA, '1'); } catch { }
}

const acotar = (v, min, max) => Math.min(max, Math.max(min, v));

function tecla(e) {
  if (route.query.m !== 'mesas' || pestana.value !== 'plano') return;
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault();
    if (cambiosMesas.value) guardarMesas();
    return;
  }
  if (/^(INPUT|TEXTAREA|SELECT)$/.test(e.target?.tagName || '')) return;
  if (e.key === 'Escape') { if (ayuda.value) cerrarAyuda(); else soltarSeleccion(); return; }
  const paso = e.shiftKey ? 0.02 : 0.004;
  const d = { ArrowLeft: [-paso, 0], ArrowRight: [paso, 0], ArrowUp: [0, -paso], ArrowDown: [0, paso] }[e.key];
  if (d) {
    e.preventDefault();
    if (elementoElegido.value) {
      const el = elementoElegido.value;
      cambiarElemento(el, { x: acotar(el.x + d[0], 0, 1 - el.ancho), y: acotar(el.y + d[1], 0, 1 - el.alto) });
    } else if (mesaElegida.value) {
      const m = mesaElegida.value;
      moverMesa(m, acotar(m.mapaX + d[0], 0, 1), acotar(m.mapaY + d[1], 0, 1));
    }
    return;
  }
  if ((e.key === 'Delete' || e.key === 'Backspace') && elementoElegido.value) {
    e.preventDefault();
    quitarElemento(elementoElegido.value);
  }
}

function avisarSalida(e) {
  if (!cambiosMesas.value) return;
  e.preventDefault();
  e.returnValue = '';
}

async function guardarMesas() {
  guardando.value = true;
  try {
    const lista = mesasEdit.value.map((m) => ({
      id: m.id ?? null, codigo: m.codigo, zona: m.zona, capacidad: Number(m.capacidad) || 1, precio: Number(m.precio) || 0,
      beneficios: m.beneficios, forma: m.forma, color: m.color, mapaX: m.mapaX, mapaY: m.mapaY, tamano: Number(m.tamano) || 0.06,
    }));
    const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/mesas`, { method: 'PUT', body: JSON.stringify(lista) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar el plano', 'error'); return; }
    const re = await apiFetch(`/api/app/mesas/eventos/${selId.value}/elementos`, { method: 'PUT', body: JSON.stringify(elementos.value) });
    const de = await re.json().catch(() => ({}));
    if (!re.ok || !de.ok) { toast(de.mensaje || 'Las mesas se guardaron, pero no los textos', 'error'); return; }
    toast('Plano guardado ✓', 'ok', 1600);
    mesasEdit.value = d.mesas.map((m) => ({ ...m }));
    elementos.value = de.elementos.map((e) => ({ ...e }));
    cambiosMesas.value = false;
    borradorPlano.descartar();
  } finally {
    guardando.value = false;
  }
}

async function quitarMesa(m) {
  if (!m.id) {
    mesasEdit.value = mesasEdit.value.filter((x) => x !== m);
    mesaSel.value = null;
    return;
  }
  const si = await confirmar(`La mesa ${m.codigo} se quitará del plano.`, { titulo: '¿Eliminar la mesa?', aceptar: 'Sí, eliminar' });
  if (!si) return;
  const r = await apiFetch(`/api/app/mesas/${m.id}`, { method: 'DELETE' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo eliminar', 'error'); return; }
  mesasEdit.value = mesasEdit.value.filter((x) => x.id !== m.id);
  mesaSel.value = null;
}

async function alternarVenta(m) {
  const r = await apiFetch(`/api/app/mesas/${m.id}/venta`, { method: 'PATCH', body: JSON.stringify({ fueraDeVenta: m.estado === 'L' }) });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo cambiar', 'error'); return; }
  m.estado = m.estado === 'L' ? 'X' : 'L';
}

async function copiarEnlace() {
  try {
    await navigator.clipboard.writeText(enlacePublico.value);
    toast('Enlace copiado', 'ok', 1500);
  } catch {
    toast(enlacePublico.value, 'info', 6000);
  }
}

function desdeRuta() {
  if (route.query.m !== 'mesas') return;
  const q = route.query;
  if (q.tab && ['evento', 'plano'].includes(q.tab)) pestana.value = q.tab;
  if (q.completo === '1' && q.tab === 'plano') completo.value = true;
  if (q.nuevo === '1') { if (selId.value || !detalle.value) nuevoEvento(); return; }
  const id = Number(q.evento);
  if (id && id !== selId.value) abrir(id);
  if (!id && !q.nuevo) router.replace(aModulo('actividades'));
}

watch(pestana, (p) => {
  if (p !== 'plano') { soltarSeleccion(); completo.value = false; }
  if (route.query.m === 'mesas' && route.query.tab !== p && selId.value) router.replace({ query: { ...route.query, tab: p } });
});
watch(() => [route.query.evento, route.query.tab, route.query.nuevo], desdeRuta);
watch(pestana, (p) => {
  if (p !== 'plano') return;
  try { if (!localStorage.getItem(CLAVE_AYUDA)) ayuda.value = true; } catch { }
});
onMounted(() => {
  desdeRuta();
  window.addEventListener('keydown', tecla);
  window.addEventListener('beforeunload', avisarSalida);
});
onUnmounted(() => {
  cerrarCanal?.();
  clearTimeout(pendiente);
  window.removeEventListener('keydown', tecla);
  window.removeEventListener('beforeunload', avisarSalida);
});
</script>

<template>
  <div class="organizar">
    <header class="cab card">
      <button class="btn btn-fantasma btn-sm" @click="router.push(aModulo('actividades'))">← Centro de actividades</button>
      <CambiarPlano v-if="detalle && pestana === 'plano'" :actual="`e-${detalle.id}`" />
      <div class="titulo">
        <h3>{{ detalle ? detalle.nombre : 'Nuevo evento con mesas' }}</h3>
        <span v-if="detalle" class="muted chico">{{ detalle.localNombre }} · {{ detalle.fechaTexto }} · {{ totales.mesas }} mesas para {{ totales.personas }} personas · {{ totales.vendidas }} vendidas</span>
      </div>
      <template v-if="detalle">
        <label class="interruptor" :title="detalle.publicado ? 'Visible y recibiendo reservas' : 'Oculto al público'">
          <input type="checkbox" :checked="detalle.publicado" @change="alternarPublicado" />
          <span class="pista"></span>
          <span>{{ detalle.publicado ? 'Habilitado' : 'Deshabilitado' }}</span>
        </label>
        <button v-if="detalle.publicado" class="btn btn-sm" @click="copiarEnlace">Copiar enlace</button>
        <a v-if="detalle.publicado" class="btn btn-sm" :href="enlacePublico" target="_blank" rel="noopener">Ver público</a>
      </template>
    </header>

    <nav class="pestanas">
      <button :class="{ on: pestana === 'evento' }" @click="pestana = 'evento'">1. Datos, pago y pases</button>
      <button :class="{ on: pestana === 'plano' }" :disabled="!detalle" :title="detalle ? '' : 'Guarda el evento primero'" @click="pestana = 'plano'">2. Plano y mesas</button>
      <span class="crecer"></span>
      <span v-if="borrador.recuperado.value" class="borrador">Recuperamos lo que estabas escribiendo · <button class="enlace" @click="borrador.descartar(); detalle ? cargarDetalle() : nuevoEvento()">descartar</button></span>
    </nav>

    <p v-if="cargando" class="muted">Cargando…</p>

    <div v-else-if="pestana === 'evento'" class="datos">
      <div class="columnas">
        <section class="card seccion">
          <header class="sec-cab"><span class="sec-icono">🎉</span>
            <div><h4>El evento</h4><p class="muted chico">Lo que el público ve en la portada.</p></div></header>
          <div class="campos">
            <label class="campo ancho"><span>Nombre del evento</span>
              <input id="ev-nombre" v-model="form.nombre" class="control" maxlength="120" placeholder="Ej. Noche Neón · Fiesta de fin de año" /></label>
            <label class="campo ancho"><span>Local o salón</span>
              <input id="ev-local" v-model="form.localNombre" class="control" maxlength="120" placeholder="Ej. Salón de eventos Los Jardines" /></label>
            <label class="campo ancho"><span>Dirección</span>
              <textarea id="ev-dir" v-model="form.localDireccion" class="control" rows="2" maxlength="200" placeholder="Calle, número, zona y una referencia"></textarea></label>
            <label class="campo"><span>Fecha y hora de inicio</span>
              <input id="ev-fecha" v-model="form.fechaInicio" type="datetime-local" class="control" /></label>
            <label class="campo"><span>Llegada hasta</span>
              <input id="ev-limite" v-model="form.horaLimite" type="datetime-local" class="control" />
              <small class="muted">Después de esta hora el ingreso ya no se garantiza.</small></label>
            <label class="campo ancho"><span>Descripción</span>
              <textarea id="ev-desc" v-model="form.descripcion" class="control" rows="3" maxlength="500" placeholder="Qué incluye, artistas, código de vestimenta…"></textarea>
              <small class="muted contador">{{ (form.descripcion || '').length }}/500</small></label>
          </div>
        </section>

        <section class="card seccion">
          <header class="sec-cab"><span class="sec-icono">🏷️</span>
            <div><h4>Identidad del local</h4><p class="muted chico">El logo va en los pases que no tienen foto de la persona, junto al nombre del local.</p></div></header>
          <div class="pago">
            <div class="qr-caja">
              <img v-if="detalle?.logoUrl" :src="urlApi(detalle.logoUrl)" alt="Logo del local" />
              <span v-else class="qr-vacio">Sin logo</span>
              <label v-if="selId" class="btn btn-sm">{{ detalle?.logoUrl ? 'Cambiar logo' : 'Subir logo' }}
                <input type="file" accept="image/png,image/jpeg" class="oculto" @change="subirImagen($event, 'logo')" />
              </label>
              <button v-if="detalle?.logoUrl" class="btn btn-sm btn-fantasma" @click="quitarLogo">Quitar</button>
              <small v-if="!selId" class="muted">Guarda el evento para subir el logo.</small>
            </div>
            <div class="campos">
              <p class="muted chico ancho">Nombre del local: <b>{{ form.localNombre || '—' }}</b> (se cambia en los datos del evento). Usa un logo PNG con fondo transparente para que se vea mejor dentro del rombo del pase.</p>
            </div>
          </div>
        </section>

        <section class="card seccion">
          <header class="sec-cab"><span class="sec-icono">💳</span>
            <div><h4>Cómo paga el cliente</h4><p class="muted chico">Se muestra al reservar una mesa.</p></div></header>
          <div class="pago">
            <div class="qr-caja">
              <img v-if="detalle?.pagoQrUrl" :src="urlApi(detalle.pagoQrUrl)" alt="QR de pago" />
              <span v-else class="qr-vacio">Sin QR</span>
              <label v-if="selId" class="btn btn-sm">{{ detalle?.pagoQrUrl ? 'Cambiar QR' : 'Subir QR' }}
                <input type="file" accept="image/png,image/jpeg,image/webp" class="oculto" @change="subirImagen($event, 'qr-pago')" />
              </label>
              <button v-if="detalle?.pagoQrUrl" class="btn btn-sm btn-fantasma" @click="quitarQr">Quitar</button>
              <small v-if="!selId" class="muted">Guarda el evento para subir el QR.</small>
            </div>
            <div class="campos">
              <label class="campo ancho"><span>Banco</span><input id="ev-banco" v-model="form.pagoBanco" class="control" maxlength="80" placeholder="Ej. Banco Unión" /></label>
              <label class="campo ancho"><span>Titular de la cuenta</span><input id="ev-titular" v-model="form.pagoTitular" class="control" maxlength="120" /></label>
              <label class="campo ancho"><span>Número de cuenta</span><input id="ev-cuenta" v-model="form.pagoCuenta" class="control" maxlength="60" inputmode="numeric" /></label>
              <label class="campo"><span>WhatsApp de consultas</span><input id="ev-cel" v-model="form.celularContacto" class="control" maxlength="30" inputmode="tel" placeholder="70000000" /></label>
              <label class="campo"><span>Tiempo para pagar</span>
                <span class="con-sufijo"><select id="ev-min" v-model.number="form.minutosBloqueo" class="control"><option :value="5">5 minutos</option><option :value="10">10 minutos</option></select></span>
              </label>
            </div>
          </div>
          <label class="campo"><span>Instrucciones de pago</span>
            <textarea id="ev-instr" v-model="form.pagoInstrucciones" class="control" rows="2" maxlength="500" placeholder="Ej. transfiere el monto exacto y adjunta la captura"></textarea></label>
          <p class="nota-sec">La mesa queda apartada esos minutos mientras el cliente paga y adjunta su comprobante.</p>
        </section>

        <section class="card seccion">
          <header class="sec-cab"><span class="sec-icono">🎟️</span>
            <div><h4>Pases de ingreso</h4><p class="muted chico">Al aprobar un pago eliges uno y se genera un pase con QR por persona.</p></div></header>
          <div class="chips-pase">
            <span v-for="t in tiposPase" :key="t" class="chip-pase">{{ t }}
              <button type="button" :aria-label="`Quitar ${t}`" @click="quitarPase(t)">✕</button></span>
            <span v-if="!tiposPase.length" class="muted chico">Aún no hay tipos de pase.</span>
          </div>
          <form class="agregar-pase" @submit.prevent="agregarPase">
            <input id="ev-pase" v-model="nuevoPase" class="control" maxlength="40" placeholder="Nuevo tipo, ej. VIP o Cortesía" />
            <button type="submit" class="btn btn-sm" :disabled="!nuevoPase.trim()">＋ Agregar</button>
          </form>
        </section>

        <section class="card seccion">
          <header class="sec-cab"><span class="sec-icono">📋</span>
            <div><h4>Condiciones de la reserva</h4><p class="muted chico">Una por línea. El cliente las acepta al reservar.</p></div></header>
          <textarea id="ev-pol" v-model="form.politicas" class="control" rows="6" :placeholder="detalle?.politicas || 'Si lo dejas vacío se usan las condiciones básicas del sistema.'"></textarea>
        </section>
      </div>

      <div class="barra-guardar" :class="{ sucio: cambiosEvento || !selId }">
        <span class="estado-form">
          <template v-if="!selId">Completa los datos y crea el evento para armar su plano.</template>
          <template v-else-if="cambiosEvento">● Tienes cambios sin guardar</template>
          <template v-else>✓ Todo guardado</template>
        </span>
        <button class="btn btn-primario" :disabled="guardando || (selId && !cambiosEvento)" @click="guardarEvento">
          {{ guardando ? 'Guardando…' : selId ? 'Guardar cambios' : 'Crear evento y seguir con el plano' }}
        </button>
      </div>
    </div>

    <div v-else-if="pestana === 'plano'" class="card plano-tab" :class="{ completo }">
      <div class="barra">
        <strong v-if="completo" class="titulo-completo">{{ detalle?.nombre }}</strong>
        <button class="btn btn-sm" title="Agregar una mesa en el centro del plano" @click="agregarMesa">＋ Mesa</button>
        <button class="btn btn-sm" title="Agregar un texto suelto, por ejemplo BARRA o INGRESO" @click="agregarElemento('texto')">＋ Texto</button>
        <button class="btn btn-sm" title="Agregar un área con nombre, por ejemplo ZONA VIP o PISTA" @click="agregarElemento('zona')">＋ Zona</button>
        <button class="btn btn-sm" title="Poner, cambiar o quitar una imagen del local debajo del plano" @click="modalFondo = true">🖼 Fondo{{ detalle?.planoUrl ? ' ✓' : '' }}</button>
        <button class="btn btn-sm" title="Elegir si el plano es horizontal, cuadrado o vertical" @click="modalForma = true">📐 Forma</button>
        <label class="check-barra" title="Muestra una cuadrícula y hace que mesas, textos y zonas se acomoden a ella al moverlos, para que queden alineados">
          <input v-model="rejilla" type="checkbox" /> Rejilla e imán</label>
        <button class="btn btn-sm" title="Ver el plano como lo verá el público y descargarlo como imagen" @click="vistaPrevia = true">👁 Vista previa</button>
        <span class="crecer"></span>
        <button class="btn btn-sm btn-fantasma" title="Cómo se usa el editor" @click="ayuda = true">? Ayuda</button>
        <button v-if="!completo" class="btn btn-sm btn-fantasma" title="Abrir el editor solo, en otra pestaña del navegador" @click="abrirEnPestana">↗ Otra pestaña</button>
        <button class="btn btn-sm" :title="completo ? 'Volver a la vista normal' : 'Usar toda la pantalla'" @click="alternarCompleto">{{ completo ? '✕ Salir de pantalla completa' : '⛶ Pantalla completa' }}</button>
        <button class="btn btn-primario btn-sm" :class="{ latir: cambiosMesas }" :disabled="!cambiosMesas || guardando" title="Ctrl + S" @click="guardarMesas">
          {{ guardando ? 'Guardando…' : cambiosMesas ? '💾 Guardar plano' : '✓ Guardado' }}
        </button>
      </div>
      <p class="linea-ayuda" :class="{ alerta: cambiosMesas }">
        <template v-if="cambiosMesas">● Tienes cambios sin guardar. Pulsa «Guardar plano» o Ctrl + S para que el público los vea. </template>
        {{ pista }}
      </p>
      <div class="plano-cuerpo">
        <div class="lienzo-plano" :style="{ '--aspecto': aspecto }">
          <div class="ajuste">
            <PlanoMesas :mesas="mesasEdit" :elementos="elementos" :plano-url="detalle?.planoUrl" :aspecto="aspecto" editable gestion
                        :rejilla="rejilla" :seleccionada="mesaSel" :elemento-sel="elementoSel"
                        @elegir="elegirMesa" @mover="moverMesa" @elegir-elemento="elegirElemento"
                        @cambiar-elemento="cambiarElemento" @vacio="soltarSeleccion" />
          </div>
        </div>
        <aside class="panel-lateral">
          <template v-if="elementoElegido">
            <div class="ficha-cab"><strong>{{ elementoElegido.tipo === 'zona' ? 'Zona' : 'Texto' }}</strong>
              <button class="btn btn-fantasma btn-icono" aria-label="Cerrar" @click="soltarSeleccion">✕</button></div>
            <label class="campo"><span>Texto que se ve</span>
              <input :value="elementoElegido.texto" class="control" maxlength="80" @input="cambiarElemento(elementoElegido, { texto: $event.target.value })" />
            </label>
            <div class="dos">
              <label class="campo"><span>Tipo</span>
                <select :value="elementoElegido.tipo" class="control" @change="cambiarElemento(elementoElegido, { tipo: $event.target.value, fondo: $event.target.value === 'zona' ? (elementoElegido.fondo || '#7c5cff') : null })">
                  <option value="texto">Texto suelto</option><option value="zona">Zona con fondo</option>
                </select>
              </label>
              <label class="campo"><span>Color del texto</span>
                <input :value="elementoElegido.color || '#ffffff'" type="color" class="control control-color" @input="cambiarElemento(elementoElegido, { color: $event.target.value })" />
              </label>
              <label v-if="elementoElegido.tipo === 'zona'" class="campo"><span>Color de la zona</span>
                <input :value="elementoElegido.fondo || '#7c5cff'" type="color" class="control control-color" @input="cambiarElemento(elementoElegido, { fondo: $event.target.value })" />
              </label>
            </div>
            <label class="campo"><span>Tamaño de letra</span>
              <input :value="elementoElegido.tamano" type="range" min="0.008" max="0.08" step="0.001" @input="cambiarElemento(elementoElegido, { tamano: Number($event.target.value) })" />
            </label>
            <label class="campo"><span>Giro · {{ elementoElegido.rotacion || 0 }}°</span>
              <input :value="elementoElegido.rotacion || 0" type="range" min="-90" max="90" step="5" @input="cambiarElemento(elementoElegido, { rotacion: Number($event.target.value) })" />
            </label>
            <div class="acciones">
              <button class="btn btn-sm" @click="duplicarElemento(elementoElegido)">Duplicar</button>
              <button class="btn btn-sm btn-peligro" @click="quitarElemento(elementoElegido)">Eliminar</button>
            </div>
          </template>
          <template v-else-if="mesaElegida">
            <div class="ficha-cab"><strong>Mesa {{ mesaElegida.codigo }}</strong>
              <span class="muted chico">{{ ESTADO_MESA[mesaElegida.estado] }}</span>
              <button class="btn btn-fantasma btn-icono" aria-label="Cerrar" @click="soltarSeleccion">✕</button></div>
            <div class="dos">
              <label class="campo"><span>Número</span><input v-model="mesaElegida.codigo" class="control" maxlength="20" @input="cambiosMesas = true" /></label>
              <label class="campo"><span>Zona</span><input v-model="mesaElegida.zona" class="control" maxlength="60" @input="cambiosMesas = true" /></label>
              <label class="campo"><span>Precio (Bs)</span><input v-model.number="mesaElegida.precio" type="number" min="0" class="control" @input="cambiosMesas = true" /></label>
              <label class="campo"><span>Capacidad (personas)</span><input v-model.number="mesaElegida.capacidad" type="number" min="1" max="50" class="control" @input="cambiosMesas = true" /></label>
              <label class="campo"><span>Forma</span>
                <select v-model="mesaElegida.forma" class="control" @change="cambiosMesas = true">
                  <option value="circulo">Redonda</option><option value="cuadrado">Cuadrada</option><option value="rectangulo">Rectangular</option>
                </select>
              </label>
              <label class="campo"><span>Color de zona</span><input :value="mesaElegida.color || '#6d5bd0'" type="color" class="control control-color" @input="mesaElegida.color = $event.target.value; cambiosMesas = true" /></label>
            </div>
            <label class="campo"><span>Tamaño en el plano</span>
              <input v-model.number="mesaElegida.tamano" type="range" min="0.02" max="0.15" step="0.005" @input="cambiosMesas = true" />
            </label>
            <label class="campo"><span>Incluye</span><input v-model="mesaElegida.beneficios" class="control" maxlength="300" placeholder="1 botella + 4 mixers" @input="cambiosMesas = true" /></label>
            <p v-if="mesaElegida.reservadoPor" class="muted chico">Reserva de {{ mesaElegida.reservadoPor }}</p>
            <div class="acciones">
              <button v-if="mesaElegida.id && (mesaElegida.estado === 'L' || mesaElegida.estado === 'X')" class="btn btn-sm" @click="alternarVenta(mesaElegida)">
                {{ mesaElegida.estado === 'X' ? 'Liberar para venta pública' : 'Apartar para VIP / fuera de venta' }}
              </button>
              <button v-if="!mesaElegida.id || mesaElegida.estado === 'L' || mesaElegida.estado === 'X'" class="btn btn-sm btn-peligro" @click="quitarMesa(mesaElegida)">Eliminar</button>
              <button v-if="mesaElegida.estado === 'O' && mesaElegida.reservaId" class="btn btn-sm btn-primario" @click="entregaId = mesaElegida.reservaId">📲 Recibo y pases</button>
            </div>
          </template>
          <div v-else class="sin-seleccion">
            <strong>Nada seleccionado</strong>
            <p class="muted chico">Toca una mesa, un texto o una zona del plano para editarla aquí.</p>
            <dl class="resumen-plano">
              <div><dt>Mesas</dt><dd>{{ totales.mesas }}</dd></div>
              <div><dt>Personas</dt><dd>{{ totales.personas }}</dd></div>
              <div><dt>Textos y zonas</dt><dd>{{ elementos.length }}</dd></div>
            </dl>
          </div>
        </aside>
      </div>

      <ModalFondo v-if="modalFondo" :fondo-url="detalle?.planoUrl ? urlApi(detalle.planoUrl) : null" :ocupado="subiendoFondo"
                  @elegir="elegirFondo" @quitar="quitarFondo" @cerrar="modalFondo = false" />

      <ModalForma v-if="modalForma" :aspecto="aspecto" :con-fondo="Boolean(detalle?.planoUrl)" :ocupado="aplicandoForma"
                  @aplicar="aplicarForma" @quitar-fondo="quitarFondo" @cerrar="modalForma = false" />

      <ModalEntregaReserva v-if="entregaId" :reserva-id="entregaId" @cerrar="entregaId = null" />
      <UiModal v-if="vistaPrevia" titulo="Así lo verá el público" ancho="1100px" @cerrar="vistaPrevia = false">
        <p class="muted chico">Incluye los cambios que aún no guardaste. Las mesas se ven con su estado actual.</p>
        <div class="previa-publico">
          <div class="previa-cab"><strong>{{ detalle?.nombre }}</strong><span>{{ detalle?.localNombre }} · {{ detalle?.fechaTexto }}</span></div>
          <div class="previa-lienzo" :style="{ '--aspecto': aspecto }">
            <div class="ajuste"><PlanoMesas :mesas="mesasEdit" :elementos="elementos" :plano-url="detalle?.planoUrl" :aspecto="aspecto" /></div>
          </div>
          <div class="previa-leyenda"><span v-for="l in LEYENDA_MESAS" :key="l.texto"><i :style="{ background: l.color }"></i>{{ l.texto }}</span></div>
        </div>
        <template #pie>
          <button class="btn btn-fantasma" @click="vistaPrevia = false">Cerrar</button>
          <button class="btn" @click="vistaPrevia = false; modalForma = true">📐 Cambiar forma</button>
          <a v-if="detalle?.publicado" class="btn" :href="enlacePublico" target="_blank" rel="noopener">Abrir página pública ↗</a>
          <button class="btn btn-primario" :disabled="generandoImagen" @click="descargarImagen">{{ generandoImagen ? 'Generando…' : '⬇ Descargar imagen' }}</button>
        </template>
      </UiModal>

      <div v-if="ayuda" class="ayuda-velo" @click.self="cerrarAyuda">
        <section class="ayuda-caja" role="dialog" aria-modal="true" aria-label="Cómo se usa el editor del plano">
          <h3>Cómo armar el plano</h3>
          <ol>
            <li><b>Agrega mesas</b> con «＋ Mesa» y arrástralas a su lugar. Al tocar una, a la derecha cambias número, zona, precio, capacidad, forma y tamaño.</li>
            <li><b>Dibuja el local</b> con «＋ Zona» (Pista, Zona VIP, Barra…) y «＋ Texto» (Ingreso, Escenario · DJ…). Arrástralos para moverlos y usa la esquina blanca para cambiar su tamaño.</li>
            <li><b>Si tienes un dibujo o foto del local</b>, ponlo con «🖼 Fondo»: queda debajo y las mesas y textos encima. Desde ahí también lo quitas.</li>
            <li><b>Rejilla e imán:</b> muestra una cuadrícula y, al mover, todo se acomoda a ella para que las filas queden rectas.</li>
            <li><b>📐 Forma:</b> elige si el local es horizontal, cuadrado, vertical o escribe sus medidas en metros.</li>
            <li><b>Vista previa:</b> mira el plano como lo verá el público y descárgalo como imagen para compartirlo.</li>
            <li><b>Ajuste fino:</b> con algo seleccionado, las flechas del teclado lo mueven (Shift = más rápido). Supr borra un texto o zona. Esc suelta la selección.</li>
            <li><b>Guarda</b> con «Guardar plano» o Ctrl + S. Hasta que guardes, el público sigue viendo la versión anterior. Si cierras sin guardar, lo recuperamos la próxima vez.</li>
            <li><b>Más espacio:</b> «Pantalla completa» o «Otra pestaña» abren solo el editor.</li>
          </ol>
          <div class="acciones"><button class="btn btn-primario" @click="cerrarAyuda">Entendido</button></div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.organizar { display: flex; flex-direction: column; gap: 0.9rem; }
.card { padding: 1rem; }
.cab { display: flex; align-items: center; gap: 0.7rem; flex-wrap: wrap; }
.titulo { flex: 1; min-width: 220px; display: flex; flex-direction: column; }
.titulo h3 { margin: 0; }
.chico { font-size: 0.8rem; margin: 0; }
.interruptor { display: inline-flex; align-items: center; gap: 0.5rem; cursor: pointer; font-weight: 700; font-size: 0.88rem; }
.interruptor input { position: absolute; opacity: 0; width: 0; height: 0; }
.interruptor .pista { width: 40px; height: 22px; border-radius: 999px; background: var(--border); position: relative; transition: background 0.15s; }
.interruptor .pista::after { content: ''; position: absolute; top: 3px; left: 3px; width: 16px; height: 16px; border-radius: 50%; background: #fff; transition: transform 0.15s; }
.interruptor input:checked + .pista { background: var(--ok); }
.interruptor input:checked + .pista::after { transform: translateX(18px); }
.interruptor input:focus-visible + .pista { outline: 2px solid var(--acento); outline-offset: 2px; }
.pestanas { display: flex; align-items: center; gap: 0.3rem; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.pestanas button { border: none; background: none; color: var(--muted); font: inherit; font-weight: 700; padding: 0.55rem 0.8rem; border-bottom: 2px solid transparent; cursor: pointer; }
.pestanas button.on { color: var(--text); border-bottom-color: var(--acento); }
.pestanas button:disabled { opacity: 0.5; cursor: not-allowed; }
.borrador { font-size: 0.8rem; color: var(--tramite); }
.enlace { border: 0; background: none; padding: 0; color: inherit; text-decoration: underline; cursor: pointer; font: inherit; }
.crecer { flex: 1; }
.dos { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.7rem; }
.datos { display: flex; flex-direction: column; gap: 0.9rem; }
.columnas { column-width: 430px; column-gap: 0.9rem; }
.seccion { break-inside: avoid; margin: 0 0 0.9rem; display: flex; flex-direction: column; gap: 0.75rem; container-type: inline-size; }
.sec-cab { display: flex; gap: 0.7rem; align-items: flex-start; }
.sec-icono { font-size: 1.3rem; width: 2.3rem; height: 2.3rem; flex: none; display: grid; place-items: center; border-radius: 10px; background: var(--acento-suave); }
.sec-cab h4 { margin: 0; font-size: 1rem; }
.sec-cab p { margin: 0.1rem 0 0; }
.campos { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.7rem; align-items: start; }
.campos .ancho { grid-column: 1 / -1; }
.campo small { font-size: 0.75rem; }
.contador { align-self: flex-end; }
textarea.control { resize: vertical; min-height: 2.6rem; field-sizing: content; max-height: 14rem; }
.con-sufijo { display: flex; align-items: center; gap: 0.4rem; }
.con-sufijo .control { flex: 1; min-width: 0; }
.con-sufijo em { font-style: normal; color: var(--muted); font-size: 0.85rem; }
.pago { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 0.9rem; align-items: start; }
.qr-caja { display: flex; flex-direction: column; gap: 0.4rem; align-items: center; border: 1px dashed var(--border); border-radius: var(--radio-sm); padding: 0.6rem; text-align: center; }
.qr-caja img { width: 120px; height: 120px; object-fit: contain; background: #fff; border-radius: 8px; }
.qr-vacio { width: 120px; height: 120px; display: grid; place-items: center; border-radius: 8px; background: var(--bg); color: var(--muted); font-size: 0.8rem; }
.nota-sec { margin: 0; font-size: 0.78rem; color: var(--muted); }
.chips-pase { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.chip-pase { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.3rem 0.4rem 0.3rem 0.75rem; border-radius: 999px; background: var(--acento-suave); border: 1px solid var(--acento); font-weight: 600; font-size: 0.88rem; }
.chip-pase button { border: 0; background: none; color: inherit; cursor: pointer; width: 1.4rem; height: 1.4rem; border-radius: 50%; font-size: 0.75rem; }
.chip-pase button:hover { background: color-mix(in srgb, var(--danger) 18%, transparent); }
.agregar-pase { display: flex; gap: 0.5rem; }
.agregar-pase .control { flex: 1; min-width: 0; }
.barra-guardar {
  position: sticky; bottom: calc(0.5rem + var(--tabbar-h, 0px)); z-index: 5; display: flex; align-items: center; justify-content: space-between; gap: 0.8rem; flex-wrap: wrap;
  padding: 0.7rem 1rem; border-radius: var(--radio); background: var(--panel); border: 1px solid var(--border); box-shadow: var(--sombra-md);
}
.barra-guardar.sucio { border-color: var(--tramite); }
.estado-form { font-size: 0.88rem; font-weight: 600; color: var(--muted); }
.sucio .estado-form { color: var(--tramite); }
@container (max-width: 400px) {
  .campos, .pago { grid-template-columns: 1fr; }
  .qr-caja { flex-direction: row; flex-wrap: wrap; justify-content: center; }
}
.previa-publico { background: #0b0d1a; color: #fff; border-radius: var(--radio); padding: 0.8rem; display: flex; flex-direction: column; gap: 0.6rem; }
.previa-cab { display: flex; flex-direction: column; }
.previa-cab span { color: #b9b6e6; font-size: 0.85rem; }
.previa-lienzo { container-type: size; height: min(62vh, 640px); display: grid; place-items: center; }
.previa-leyenda { display: flex; gap: 0.9rem; flex-wrap: wrap; font-size: 0.82rem; color: #d7d4ff; }
.previa-leyenda i { display: inline-block; width: 0.75rem; height: 0.75rem; border-radius: 50%; margin-right: 0.35rem; vertical-align: -1px; }
.acciones { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; }
.oculto { display: none; }
.control-color { height: 40px; padding: 2px; }
.plano-tab { display: flex; flex-direction: column; gap: 0.6rem; }
.plano-tab.completo {
  position: fixed; inset: 0; z-index: 80; border-radius: 0; margin: 0; background: var(--bg);
  padding: calc(0.7rem + var(--safe-top, 0px)) calc(0.9rem + var(--safe-right, 0px)) calc(0.7rem + var(--safe-bottom, 0px)) calc(0.9rem + var(--safe-left, 0px));
}
.barra { display: flex; align-items: center; gap: 0.45rem; flex-wrap: wrap; }
.titulo-completo { margin-right: 0.4rem; }
.check-barra { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.85rem; font-weight: 600; cursor: pointer; }
.latir { animation: latir-guardar 1.8s ease-in-out infinite; }
@keyframes latir-guardar { 50% { box-shadow: 0 0 0 4px color-mix(in srgb, var(--acento) 30%, transparent); } }
.linea-ayuda { margin: 0; font-size: 0.82rem; color: var(--muted); padding: 0.4rem 0.7rem; border-radius: var(--radio-sm); background: var(--bg); }
.linea-ayuda.alerta { color: var(--text); background: color-mix(in srgb, var(--tramite) 16%, transparent); }
.plano-cuerpo { display: grid; grid-template-columns: minmax(0, 1fr) 290px; gap: 0.9rem; align-items: stretch; }
.completo .plano-cuerpo { flex: 1; min-height: 0; }
.lienzo-plano { container-type: size; display: grid; place-items: center; height: min(72vh, 760px); min-width: 0; }
.completo .lienzo-plano { height: auto; min-height: 0; }
.ajuste { width: min(100cqw, calc(100cqh / var(--aspecto))); }
.panel-lateral {
  border: 1px solid var(--border); border-radius: var(--radio); padding: 0.8rem; display: flex; flex-direction: column; gap: 0.6rem;
  overflow-y: auto; min-height: 0; max-height: min(72vh, 760px);
}
.completo .panel-lateral { max-height: none; }
.ficha-cab { display: flex; justify-content: space-between; align-items: center; gap: 0.4rem; }
.ficha-cab strong { flex: 1; }
.sin-seleccion { display: flex; flex-direction: column; gap: 0.5rem; }
.resumen-plano { margin: 0.4rem 0 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.4rem; }
.resumen-plano dt { font-size: 0.7rem; color: var(--muted); text-transform: uppercase; }
.resumen-plano dd { margin: 0; font-size: 1.2rem; font-weight: 800; }
.ayuda-velo { position: fixed; inset: 0; z-index: 90; background: rgba(2, 6, 23, 0.55); display: grid; place-items: center; padding: 1rem; }
.ayuda-caja { background: var(--panel); border-radius: var(--radio); box-shadow: var(--sombra-md); padding: 1.2rem 1.4rem; max-width: 560px; width: 100%; display: flex; flex-direction: column; gap: 0.7rem; }
.ayuda-caja h3 { margin: 0; }
.ayuda-caja ol { margin: 0; padding-left: 1.2rem; display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.92rem; }
@media (max-width: 980px) {
  .plano-cuerpo, .pago { grid-template-columns: 1fr; }
  .lienzo-plano { height: min(60vh, 520px); }
  .completo .plano-cuerpo { grid-template-rows: minmax(0, 1fr) auto; }
  .completo .panel-lateral { max-height: 38vh; }
}
</style>
