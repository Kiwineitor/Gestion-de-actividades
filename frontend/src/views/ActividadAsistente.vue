<script setup>
import { ref, reactive, computed, watch, onMounted, onActivated } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import CampoCelular from '../components/CampoCelular.vue';
import CompartirActividad from '../components/CompartirActividad.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { confirmar } from '../ui/alerta';
import { url as urlApi } from '../config.js';
import { aModulo } from '../modulos';
import DisenoEntrada from '../components/DisenoEntrada.vue';
import { cargarActividades } from '../ui/actividadElegida';
import { TIPOS_ESPACIO, tipoEspacio, nombreEspacio, mayuscula } from '../ui/espacios';

const route = useRoute();
const router = useRouter();

const TIPOS_ACTIVIDAD = ['Feria', 'Evento', 'Concierto', 'Fiesta', 'Exposición', 'Congreso', 'Festival'];
const CONDICIONES_SUGERIDAS = [
  'No se aceptan devoluciones una vez aprobado el pago.',
  'Presentar el C.I. y el QR al ingresar.',
  'Menores de edad solo con un adulto responsable.',
  'Prohibido ingresar bebidas y comida externas.',
  'El espacio debe dejarse limpio al terminar.',
  'La reserva no aprobada en el tiempo indicado se libera.',
];
const MINUTOS = [5, 10, 15, 30, 60];

const id = ref(null);
const cargando = ref(false);
const guardando = ref(false);
const datos = ref(null);
const pasoActual = ref('datos');
const compartirAbierto = ref(false);

const f = reactive({
  nombre: '', tipo: 'Evento', descripcion: '', lugar: '', contacto: '', dias: [],
  vendeEntradas: false, vendeEspacios: true, tipoEspacio: 'MESA', etiquetaEspacio: '', comprador: 'PERSONA', entradas: [],
  condiciones: '', minutosBloqueo: 10, ventaHasta: '', ventaDesde: '',
  pagoBanco: '', pagoCuenta: '', pagoTitular: '', pagoInstrucciones: '', pagoOficina: '',
  usaCredenciales: false, credencialEntrada: false, credencialRepresentante: false, credencialAcompanantes: false,
  metaAutomatica: true, metaRecaudacion: null,
});
const cupos = reactive({});

const PASOS = [
  { k: 'datos', n: 1, t: 'Datos', i: '📝' },
  { k: 'venta', n: 2, t: 'Qué vendes', i: '🛒' },
  { k: 'condiciones', n: 3, t: 'Condiciones', i: '📜' },
  { k: 'cobro', n: 4, t: 'Cobro', i: '💳' },
  { k: 'plano', n: 5, t: 'Plano', i: '🗺️' },
  { k: 'credenciales', n: 6, t: 'Credenciales', i: '🪪' },
  { k: 'publicar', n: 7, t: 'Publicar', i: '🚀' },
];
const pasos = computed(() => PASOS.filter((p) => p.k !== 'plano' || f.vendeEspacios)
  .map((p) => (p.k === 'credenciales' && f.vendeEntradas ? { ...p, t: 'Entradas y credenciales', i: '🎟️' } : p)));
const indice = computed(() => Math.max(0, pasos.value.findIndex((p) => p.k === pasoActual.value)));
const paso = computed(() => pasos.value[indice.value]);
const alcanzado = computed(() => Number(datos.value?.configuracionPaso || 0));
const espacio = computed(() => nombreEspacio(f));
const espacios = computed(() => nombreEspacio(f, true));
const tipoElegido = computed(() => tipoEspacio(f.tipoEspacio));
const pendientes = computed(() => datos.value?.pendientes || []);
const faltanObligatorios = computed(() => pendientes.value.filter((p) => p.obligatorio && !p.ok));
const urlPublica = computed(() => (id.value
  ? `${window.location.origin}${router.resolve({ path: '/', query: { v: 'actividad', edicion: id.value } }).href}`
  : ''));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
const tipoLibre = computed(() => (TIPOS_ACTIVIDAD.includes(f.tipo) ? '' : f.tipo));
const lineasCondiciones = computed(() => f.condiciones.split('\n').map((l) => l.trim()).filter(Boolean));
const vistaFechas = computed(() => textoFechas(f.dias.map((d) => d.fecha).filter(Boolean)));

function textoFechas(fechas) {
  const lista = [...new Set(fechas)].sort().map((s) => new Date(`${s}T12:00:00`));
  if (!lista.length) return '';
  const fmt = (d, mes = true) => d.toLocaleDateString('es-BO', mes ? { day: 'numeric', month: 'long' } : { day: 'numeric' });
  const anio = lista[lista.length - 1].getFullYear();
  if (lista.length === 1) return `${lista[0].toLocaleDateString('es-BO', { weekday: 'long', day: 'numeric', month: 'long' })} de ${anio}`;
  const seguidas = lista.every((d, i) => i === 0 || (d - lista[i - 1]) === 86400000);
  if (seguidas) return `${fmt(lista[0], lista[0].getMonth() !== lista[lista.length - 1].getMonth())} al ${fmt(lista[lista.length - 1])} de ${anio}`;
  return `${lista.map((d) => fmt(d)).join(', ')} de ${anio}`;
}

function aplicar(d) {
  datos.value = d;
  Object.assign(f, {
    nombre: d.nombre || '', tipo: d.tipo || '', descripcion: d.descripcion || '', lugar: d.lugar || '', contacto: d.contacto || '',
    dias: (d.dias || []).map((x) => ({ fecha: x.fecha, horaInicio: x.horaInicio || '', horaFin: x.horaFin || '' })),
    vendeEntradas: Boolean(d.vendeEntradas), vendeEspacios: Boolean(d.vendeEspacios),
    tipoEspacio: d.tipoEspacio || 'PUESTO', etiquetaEspacio: d.etiquetaEspacio || '', comprador: d.comprador || 'NEGOCIO',
    entradas: (d.entradas || []).map((e) => ({ ...e, precio: Number(e.precio), cupo: e.cupo ?? null, descripcion: e.descripcion || '' })),
    condiciones: d.condiciones || '', minutosBloqueo: d.minutosBloqueo || 10, ventaHasta: d.ventaHasta || '', ventaDesde: d.ventaDesde || '',
    pagoBanco: d.pagoBanco || '', pagoCuenta: d.pagoCuenta || '', pagoTitular: d.pagoTitular || '',
    pagoInstrucciones: d.pagoInstrucciones || '', pagoOficina: d.pagoOficina || '',
    usaCredenciales: Boolean(d.usaCredenciales), credencialEntrada: Boolean(d.credencialEntrada),
    credencialRepresentante: Boolean(d.credencialRepresentante), credencialAcompanantes: Boolean(d.credencialAcompanantes),
    metaAutomatica: d.metaAutomatica !== false, metaRecaudacion: d.metaRecaudacion == null ? null : Number(d.metaRecaudacion),
  });
  for (const c of d.categorias || []) {
    const costo = Number(c.costoPersonaExtra || 0);
    cupos[c.id] = { personas: Math.max(1, Number(c.personasPorPuesto || 1)), extras: costo > 0, costo: costo > 0 ? costo : 20 };
  }
}

function reiniciar() {
  datos.value = null;
  Object.assign(f, {
    nombre: '', tipo: 'Evento', descripcion: '', lugar: '', contacto: '', dias: [{ fecha: '', horaInicio: '', horaFin: '' }],
    vendeEntradas: false, vendeEspacios: true, tipoEspacio: 'MESA', etiquetaEspacio: '', comprador: 'PERSONA', entradas: [],
    condiciones: '', minutosBloqueo: 10, ventaHasta: '', ventaDesde: '',
    pagoBanco: '', pagoCuenta: '', pagoTitular: '', pagoInstrucciones: '', pagoOficina: '',
    usaCredenciales: false, credencialEntrada: false, credencialRepresentante: false, credencialAcompanantes: false,
    metaAutomatica: true, metaRecaudacion: null,
  });
}

async function cargar() {
  if (!id.value) return;
  cargando.value = true;
  try {
    const r = await apiFetch(`/api/app/ediciones/${id.value}/configuracion`);
    if (!r.ok) { toast('No se encontró la actividad', 'error'); router.replace(aModulo('actividades')); return; }
    aplicar(await r.json());
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function desdeRuta() {
  if (route.query.m !== 'actividad') return;
  const nuevoId = Number(route.query.id || route.query.edicion) || null;
  const pedido = PASOS.some((p) => p.k === route.query.paso) ? route.query.paso : 'datos';
  if (nuevoId !== id.value || !nuevoId) {
    id.value = nuevoId;
    if (nuevoId) cargar(); else reiniciar();
  } else if (pedido === 'plano' || pedido === 'publicar' || pedido === 'credenciales') {
    cargar();
  }
  pasoActual.value = nuevoId ? pedido : 'datos';
}

function irPaso(k) {
  if (!id.value && k !== 'datos') { toast('Primero guarda los datos de la actividad', 'advertencia'); return; }
  router.replace(aModulo('actividad', { id: id.value, paso: k }));
}

function cuerpoDelPaso(k) {
  const n = PASOS.find((p) => p.k === k)?.n;
  switch (k) {
    case 'datos':
      return {
        paso: n, nombre: f.nombre, tipo: f.tipo || '', descripcion: f.descripcion || '', lugar: f.lugar || '', contacto: f.contacto || '',
        dias: f.dias.filter((d) => d.fecha).map((d) => ({ fecha: d.fecha, horaInicio: d.horaInicio || null, horaFin: d.horaFin || null })),
      };
    case 'venta':
      return {
        paso: n, vendeEntradas: f.vendeEntradas, vendeEspacios: f.vendeEspacios, tipoEspacio: f.tipoEspacio,
        etiquetaEspacio: f.tipoEspacio === 'OTRO' ? f.etiquetaEspacio : '', comprador: f.comprador,
        entradas: f.vendeEntradas ? f.entradas.map((e) => ({
          id: e.id || null, nombre: e.nombre, descripcion: e.descripcion || '', precio: Number(e.precio) || 0,
          cupo: e.cupo === '' || e.cupo == null ? null : Number(e.cupo),
        })) : null,
      };
    case 'condiciones':
      return { paso: n, condiciones: f.condiciones || '', minutosBloqueo: f.minutosBloqueo, ventaHasta: f.ventaHasta || '', ventaDesde: f.ventaDesde || '' };
    case 'cobro':
      return {
        paso: n, pagoBanco: f.pagoBanco || '', pagoCuenta: f.pagoCuenta || '', pagoTitular: f.pagoTitular || '',
        pagoInstrucciones: f.pagoInstrucciones || '', pagoOficina: f.pagoOficina || '',
      };
    case 'plano':
      return { paso: n };
    case 'credenciales':
      return {
        paso: n, usaCredenciales: f.usaCredenciales,
        credencialEntrada: f.usaCredenciales && f.vendeEntradas && f.credencialEntrada,
        credencialRepresentante: f.usaCredenciales && f.vendeEspacios && f.credencialRepresentante,
        credencialAcompanantes: f.usaCredenciales && f.vendeEspacios && f.credencialRepresentante && f.credencialAcompanantes,
      };
    case 'publicar':
      return { metaAutomatica: f.metaAutomatica, metaRecaudacion: f.metaAutomatica ? null : Number(f.metaRecaudacion) || 0 };
    default:
      return {};
  }
}

function validarPaso(k) {
  if (k === 'datos') {
    if (!f.nombre.trim()) return 'Escribe el nombre de la actividad';
    if (!f.dias.some((d) => d.fecha)) return 'Agrega al menos un día';
    if (!f.lugar.trim()) return 'Escribe la dirección o el lugar';
    if (String(f.contacto || '').replace(/\D/g, '').length < 7) return 'Escribe un celular de contacto';
  }
  if (k === 'venta') {
    if (!f.vendeEntradas && !f.vendeEspacios) return 'Elige qué vas a vender: entradas, espacios o ambos';
    if (f.vendeEntradas && !f.entradas.length) return 'Agrega al menos un tipo de entrada';
    if (f.vendeEntradas && f.entradas.some((e) => !String(e.nombre || '').trim())) return 'Cada entrada necesita un nombre';
    if (f.vendeEspacios && f.tipoEspacio === 'OTRO' && !f.etiquetaEspacio.trim()) return 'Escribe cómo se llama lo que vendes en el plano';
  }
  return null;
}

async function guardarPaso(k = pasoActual.value) {
  const error = validarPaso(k);
  if (error) { toast(error, 'advertencia'); return false; }
  guardando.value = true;
  try {
    if (k === 'credenciales' && f.usaCredenciales && f.credencialRepresentante) await guardarCupos();
    const cuerpo = cuerpoDelPaso(k);
    const r = id.value
      ? await apiFetch(`/api/app/ediciones/${id.value}/configuracion`, { method: 'PATCH', body: JSON.stringify(cuerpo) })
      : await apiFetch('/api/app/ediciones/asistente', { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return false; }
    if (!id.value) {
      id.value = d.id;
      cargarActividades(true);
    }
    return true;
  } catch (e) {
    toast(e.message, 'error');
    return false;
  } finally {
    guardando.value = false;
  }
}

async function guardarCupos() {
  for (const c of datos.value?.categorias || []) {
    const q = cupos[c.id];
    if (!q) continue;
    const personas = Math.max(1, Math.min(50, Number(q.personas) || 1));
    const costo = q.extras ? Math.max(1, Number(q.costo) || 1) : 0;
    if (personas === Number(c.personasPorPuesto) && costo === Number(c.costoPersonaExtra || 0)) continue;
    const r = await apiFetch(`/api/app/categorias/${c.id}`, { method: 'PATCH', body: JSON.stringify({ personasPorPuesto: personas, costoPersonaExtra: costo }) });
    if (!r.ok) toast(`No se pudo guardar las personas de ${c.nombre}`, 'error');
  }
}

function resumenCupo(id) {
  const q = cupos[id];
  if (!q) return '';
  const n = Math.max(1, Number(q.personas) || 1);
  const base = `${n} persona${n === 1 ? '' : 's'} con credencial`;
  return q.extras ? `${base}; desde la ${n + 1}.ª pagan ${bs(q.costo)} c/u` : `${base}; no se aceptan más`;
}

async function siguiente() {
  const actual = pasoActual.value;
  if (!(await guardarPaso(actual))) return;
  const lista = pasos.value;
  const i = lista.findIndex((p) => p.k === actual);
  const proximo = lista[Math.min(i + 1, lista.length - 1)].k;
  if (actual === 'publicar') { await cargar(); return; }
  await router.replace(aModulo('actividad', { id: id.value, paso: proximo }));
  await cargar();
}

async function anterior() {
  const i = indice.value;
  if (i === 0) { router.push(aModulo('actividades')); return; }
  if (id.value && pasoActual.value !== 'publicar' && pasoActual.value !== 'plano') await guardarPaso();
  irPaso(pasos.value[i - 1].k);
}

function agregarDia() {
  const ultimo = f.dias[f.dias.length - 1];
  let fecha = '';
  if (ultimo?.fecha) {
    const d = new Date(`${ultimo.fecha}T12:00:00`);
    d.setDate(d.getDate() + 1);
    fecha = d.toISOString().slice(0, 10);
  }
  f.dias.push({ fecha, horaInicio: ultimo?.horaInicio || '', horaFin: ultimo?.horaFin || '' });
}

function agregarEntrada() {
  f.entradas.push({ id: null, nombre: f.entradas.length ? '' : 'Entrada general', precio: 0, cupo: null, descripcion: '' });
}

watch(() => f.vendeEntradas, (v) => { if (v && !f.entradas.length) agregarEntrada(); });

function elegirTipo(t) {
  f.tipoEspacio = t.id;
  f.comprador = t.comprador;
}

function agregarCondicion(texto) {
  const lineas = lineasCondiciones.value;
  if (lineas.includes(texto)) return;
  f.condiciones = [...lineas, texto].join('\n');
}

async function subir(ruta, e, exito) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (!archivo || !id.value) return;
  const cuerpo = new FormData();
  cuerpo.append('archivo', archivo);
  const r = await apiFetch(`/api/app/ediciones/${id.value}/${ruta}`, { method: 'POST', body: cuerpo });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir la imagen', 'error'); return; }
  toast(exito, 'ok', 1500);
  await cargar();
}

async function quitar(ruta) {
  const r = await apiFetch(`/api/app/ediciones/${id.value}/${ruta}`, { method: 'DELETE' });
  if (!r.ok) { toast('No se pudo quitar', 'error'); return; }
  await cargar();
}

function abrirEditor() {
  router.push(aModulo('editor', { edicion: id.value, asistente: '1' }));
}

const ROLES_CREDENCIAL = computed(() => [
  f.vendeEspacios && f.credencialRepresentante && { rol: 'REPRESENTANTE', icono: '👤', titulo: `Credencial de quien compra ${espacio.value.endsWith('a') ? 'la' : 'el'} ${espacio.value}`, detalle: `Nombre, ${espacio.value} y QR de ingreso.` },
  f.vendeEspacios && f.credencialRepresentante && f.credencialAcompanantes && { rol: 'ACOMPANANTE', icono: '👥', titulo: 'Pase de acompañante', detalle: `Sin datos personales: dice «Acompañante 2 de Juan Pérez» y ${espacio.value.endsWith('a') ? 'la' : 'el'} ${espacio.value} comprad${espacio.value.endsWith('a') ? 'a' : 'o'}.` },
].filter(Boolean));
const disenoDe = (rol) => (datos.value?.credencialRoles || []).find((x) => x.rol === rol);

async function abrirDisenoCredencial(rol) {
  if (!(await guardarPaso('credenciales'))) return;
  const r = await apiFetch(`/api/app/ediciones/${id.value}/credencial/${rol}`, { method: 'POST' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo preparar el diseño', 'error'); return; }
  router.push(aModulo('credenciales', { tab: 'diseno', plantilla: d.id }));
}

async function publicar(valor) {
  if (!valor && !(await confirmar('La actividad deja de verse en la página pública y no recibe más compras hasta que la vuelvas a publicar.', { titulo: '¿Ocultar la actividad?', aceptar: 'Sí, ocultar' }))) return;
  if (valor && !(await guardarPaso('publicar'))) return;
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/ediciones/${id.value}/publicar`, { method: 'POST', body: JSON.stringify({ valor }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo publicar', 'error'); return; }
    await cargar();
    cargarActividades(true);
    if (valor) router.push(aModulo('actividades', { compartir: id.value, bienvenida: '1' }));
    else toast(d.mensaje, 'ok');
  } finally {
    guardando.value = false;
  }
}

async function terminar() {
  if (!(await guardarPaso('publicar'))) return;
  toast('Cambios guardados', 'ok', 1500);
  router.push(aModulo('actividades'));
}


function verComoCliente() {
  window.open(router.resolve({ path: '/', query: { v: 'actividad', edicion: id.value, vista: 'previa' } }).href, '_blank', 'noopener');
}

const estado = computed(() => {
  if (!datos.value) return { t: 'Nueva', c: 'badge-muted' };
  if (datos.value.finalizada) return { t: 'Finalizada', c: 'badge-muted' };
  if (datos.value.publicada) return { t: 'Publicada', c: 'badge-ok' };
  return { t: 'Borrador', c: 'badge-parcial' };
});

watch(() => route.query, desdeRuta, { immediate: true });
onMounted(() => { if (!route.query.id) reiniciar(); });
onActivated(() => { if (id.value && route.query.m === 'actividad') cargar(); });
</script>

<template>
  <div class="asistente">
    <header class="cabecera card">
      <div class="titulo">
        <button class="btn btn-fantasma btn-sm" @click="router.push(aModulo('actividades'))">← Actividades</button>
        <h2>{{ f.nombre || 'Nueva actividad' }}</h2>
        <span class="badge" :class="estado.c">{{ estado.t }}</span>
      </div>
      <nav class="pasos" aria-label="Pasos de la configuración">
        <button v-for="(p, i) in pasos" :key="p.k" type="button" class="paso"
                :class="{ on: p.k === pasoActual, hecho: id && alcanzado >= p.n && p.k !== pasoActual }"
                :aria-current="p.k === pasoActual ? 'step' : null" :disabled="!id && p.k !== 'datos'" @click="irPaso(p.k)">
          <span class="num">{{ id && alcanzado > p.n && p.k !== pasoActual ? '✓' : i + 1 }}</span>
          <span class="txt">{{ p.t }}</span>
        </button>
      </nav>
    </header>

    <p v-if="cargando && !datos" class="vacio">Cargando…</p>

    <section v-else class="cuerpo card">
      <template v-if="pasoActual === 'datos'">
        <div class="intro"><span class="icono">📝</span><div><h3>¿De qué trata tu actividad?</h3><p class="muted">Esto es lo primero que verán tus clientes en la página pública.</p></div></div>
        <label class="campo"><span>Nombre de la actividad *</span>
          <input v-model="f.nombre" class="control grande" maxlength="120" placeholder="Ej. Noche de Gala 2026" autofocus />
        </label>
        <div class="campo"><span>Tipo</span>
          <div class="chips">
            <button v-for="t in TIPOS_ACTIVIDAD" :key="t" type="button" class="chip" :class="{ on: f.tipo === t }" @click="f.tipo = t">{{ t }}</button>
            <input :value="tipoLibre" class="control chip-libre" :class="{ on: tipoLibre }" maxlength="40" placeholder="Otro…" aria-label="Otro tipo" @input="f.tipo = $event.target.value" />
          </div>
        </div>
        <label class="campo"><span>Descripción</span>
          <textarea v-model="f.descripcion" class="control" rows="4" maxlength="3000" placeholder="Qué habrá, para quién es, artistas invitados, qué incluye…"></textarea>
        </label>
        <div class="dos">
          <label class="campo"><span>Dirección o lugar *</span>
            <input v-model="f.lugar" class="control" maxlength="200" placeholder="Salón Los Pinos, Av. Banzer 4to anillo" />
          </label>
          <label class="campo"><span>Celular de contacto *</span>
            <CampoCelular v-model="f.contacto" />
            <small class="muted">Para que los clientes pregunten. Aparece con botón de WhatsApp.</small>
          </label>
        </div>
        <div class="campo">
          <span>Días de la actividad *</span>
          <div class="dias">
            <div v-for="(d, i) in f.dias" :key="i" class="dia">
              <label><small>Fecha</small><input v-model="d.fecha" type="date" class="control" /></label>
              <label><small>Desde</small><input v-model="d.horaInicio" type="time" class="control" /></label>
              <label><small>Hasta</small><input v-model="d.horaFin" type="time" class="control" /></label>
              <button type="button" class="btn btn-fantasma btn-icono" :disabled="f.dias.length === 1" aria-label="Quitar día" @click="f.dias.splice(i, 1)">✕</button>
            </div>
          </div>
          <div class="fila-acciones">
            <button type="button" class="btn btn-sm" @click="agregarDia">＋ Agregar otro día</button>
            <span v-if="vistaFechas" class="muted chico">Se mostrará: <b>{{ vistaFechas }}</b></span>
          </div>
        </div>
        <div v-if="id" class="campo">
          <span>Imagen de portada</span>
          <div class="portada">
            <img v-if="datos?.portadaUrl" :src="urlApi(datos.portadaUrl)" alt="Portada de la actividad" />
            <div v-else class="portada-vacia">Sin portada: se usará un fondo de color.</div>
            <div class="fila-acciones">
              <label class="btn btn-sm">{{ datos?.portadaUrl ? 'Cambiar' : 'Subir portada' }}
                <input type="file" accept="image/png,image/jpeg,image/webp" hidden @change="subir('portada', $event, 'Portada actualizada')" />
              </label>
              <button v-if="datos?.portadaUrl" type="button" class="btn btn-sm btn-fantasma" @click="quitar('portada')">Quitar</button>
            </div>
          </div>
        </div>
        <p v-else class="muted chico">Después de guardar podrás subir una imagen de portada.</p>
      </template>

      <template v-else-if="pasoActual === 'venta'">
        <div class="intro"><span class="icono">🛒</span><div><h3>¿Qué vas a vender?</h3><p class="muted">Puedes activar uno o los dos.</p></div></div>
        <div class="opciones">
          <button type="button" class="opcion" :class="{ on: f.vendeEntradas }" :aria-pressed="f.vendeEntradas" @click="f.vendeEntradas = !f.vendeEntradas">
            <span class="check">{{ f.vendeEntradas ? '✓' : '' }}</span>
            <span class="grande">🎟️</span><strong>Entradas</strong>
            <span class="muted">El público paga por ingresar. Ej. General Bs 50, VIP Bs 120.</span>
          </button>
          <button type="button" class="opcion" :class="{ on: f.vendeEspacios }" :aria-pressed="f.vendeEspacios" @click="f.vendeEspacios = !f.vendeEspacios">
            <span class="check">{{ f.vendeEspacios ? '✓' : '' }}</span>
            <span class="grande">🗺️</span><strong>Espacios en un plano</strong>
            <span class="muted">Mesas, puestos o stands que el cliente elige en un mapa.</span>
          </button>
        </div>

        <div v-if="f.vendeEntradas" class="bloque">
          <h4>🎟️ Tipos de entrada</h4>
          <div class="entradas">
            <div v-for="(e, i) in f.entradas" :key="e.id || `n${i}`" class="entrada">
              <label class="campo"><span>Nombre</span><input v-model="e.nombre" class="control" maxlength="80" placeholder="Preventa, VIP, Estudiante…" /></label>
              <label class="campo"><span>Precio (Bs)</span><input v-model.number="e.precio" class="control" type="number" min="0" step="0.5" /></label>
              <label class="campo"><span>Cupo</span><input v-model.number="e.cupo" class="control" type="number" min="1" placeholder="Sin límite" /></label>
              <label class="campo ancho"><span>Qué incluye (opcional)</span><input v-model="e.descripcion" class="control" maxlength="300" placeholder="Ej. Incluye un trago de bienvenida" /></label>
              <button type="button" class="btn btn-fantasma btn-icono" aria-label="Quitar entrada" @click="f.entradas.splice(i, 1)">✕</button>
            </div>
          </div>
          <button type="button" class="btn btn-sm" @click="agregarEntrada">＋ Agregar tipo de entrada</button>
        </div>

        <div v-if="f.vendeEspacios" class="bloque">
          <h4>🗺️ ¿Qué hay en el plano?</h4>
          <div class="tipos">
            <button v-for="t in TIPOS_ESPACIO" :key="t.id" type="button" class="tipo" :class="{ on: f.tipoEspacio === t.id }" @click="elegirTipo(t)">
              <span class="grande">{{ t.icono }}</span><span>{{ t.id === 'OTRO' ? 'Otro' : mayuscula(t.varios) }}</span>
            </button>
          </div>
          <label v-if="f.tipoEspacio === 'OTRO'" class="campo"><span>¿Cómo se llama cada espacio?</span>
            <input v-model="f.etiquetaEspacio" class="control" maxlength="40" placeholder="Ej. Lote, Box, Parcela" />
          </label>
          <div class="campo"><span>¿Quién compra cada {{ espacio }}?</span>
            <div class="segmentado">
              <button type="button" :class="{ on: f.comprador === 'PERSONA' }" @click="f.comprador = 'PERSONA'">👤 Una persona<small>Nombre, C.I. y celular</small></button>
              <button type="button" :class="{ on: f.comprador === 'NEGOCIO' }" @click="f.comprador = 'NEGOCIO'">🏢 Un negocio o expositor<small>Nombre del negocio, NIT y representante</small></button>
            </div>
          </div>
          <p class="nota">Las categorías ({{ tipoElegido.ejemplo }}), su precio, forma y tamaño se crean en el paso <b>Plano</b>, directamente sobre el mapa.</p>
        </div>
      </template>

      <template v-else-if="pasoActual === 'condiciones'">
        <div class="intro"><span class="icono">📜</span><div><h3>Condiciones para el cliente</h3><p class="muted">El cliente las lee y acepta antes de comprar.</p></div></div>
        <label class="campo"><span>Una condición por línea</span>
          <textarea v-model="f.condiciones" class="control" rows="6" maxlength="5000" placeholder="Ej. No se aceptan devoluciones."></textarea>
        </label>
        <div class="chips">
          <span class="muted chico">Sugerencias:</span>
          <button v-for="c in CONDICIONES_SUGERIDAS" :key="c" type="button" class="chip chip-sm" :disabled="lineasCondiciones.includes(c)" @click="agregarCondicion(c)">＋ {{ c }}</button>
        </div>
        <div class="dos">
          <div class="campo"><span>Tiempo para pagar</span>
            <div class="chips">
              <button v-for="m in MINUTOS" :key="m" type="button" class="chip" :class="{ on: f.minutosBloqueo === m }" @click="f.minutosBloqueo = m">{{ m }} min</button>
            </div>
            <small class="muted">Al elegir {{ f.vendeEspacios ? `${espacio.endsWith('a') ? 'una' : 'un'} ${espacio}` : 'sus entradas' }}, se le guarda al cliente este tiempo para pagar y subir el comprobante. Si no lo hace, se libera solo.</small>
          </div>
        </div>
        <div class="dos">
          <label class="campo"><span>⏳ La venta abre el (opcional)</span>
            <input v-model="f.ventaDesde" type="datetime-local" class="control" />
            <small class="muted">Como un estreno: antes de esa fecha la página muestra la actividad con una <b>cuenta regresiva</b> y nadie puede comprar en línea. Al llegar la hora, la venta se abre sola. Vacío = abre al publicar.</small>
          </label>
          <label class="campo"><span>🔒 La venta cierra el (opcional)</span>
            <input v-model="f.ventaHasta" type="datetime-local" class="control" :min="f.ventaDesde || undefined" />
            <small class="muted">Después de esta fecha ya no se puede comprar en línea. Vacío = sigue abierta hasta que la cierres.</small>
          </label>
        </div>
        <div v-if="lineasCondiciones.length" class="previa">
          <span class="muted chico">Así lo verá el cliente</span>
          <ul><li v-for="l in lineasCondiciones" :key="l">{{ l }}</li></ul>
          <label class="check"><input type="checkbox" disabled /> He leído y acepto las condiciones</label>
        </div>
      </template>

      <template v-else-if="pasoActual === 'cobro'">
        <div class="intro"><span class="icono">💳</span><div><h3>¿Cómo te pagan?</h3><p class="muted">El cliente paga con QR o transferencia y sube su comprobante. Tú lo revisas y apruebas en <b>Ventas y cobros</b>.</p></div></div>
        <div class="cobro">
          <div class="qr">
            <img v-if="datos?.pagoQrUrl" :src="urlApi(datos.pagoQrUrl)" alt="QR de pago" />
            <span v-else class="muted chico">Sin QR</span>
            <label class="btn btn-sm">{{ datos?.pagoQrUrl ? 'Cambiar QR' : 'Subir QR' }}
              <input type="file" accept="image/png,image/jpeg,image/webp" hidden @change="subir('qr-pago', $event, 'QR actualizado')" />
            </label>
            <button v-if="datos?.pagoQrUrl" type="button" class="btn btn-sm btn-fantasma" @click="quitar('qr-pago')">Quitar</button>
          </div>
          <div class="formulario">
            <div class="dos">
              <label class="campo"><span>Banco</span><input v-model="f.pagoBanco" class="control" maxlength="80" placeholder="Banco Unión" /></label>
              <label class="campo"><span>Número de cuenta</span><input v-model="f.pagoCuenta" class="control" maxlength="60" inputmode="numeric" /></label>
            </div>
            <label class="campo"><span>Titular de la cuenta</span><input v-model="f.pagoTitular" class="control" maxlength="120" /></label>
            <label class="campo"><span>Instrucciones (opcional)</span><input v-model="f.pagoInstrucciones" class="control" maxlength="500" placeholder="Ej. Pon tu nombre en la glosa" /></label>
          </div>
        </div>
        <label class="campo"><span>Pago en persona (opcional)</span>
          <textarea v-model="f.pagoOficina" class="control" rows="2" maxlength="300" placeholder="Dirección y horario del punto de venta, ej. Av. Principal 123, de 9:00 a 18:00"></textarea>
          <small class="muted">Si lo llenas, el cliente también puede elegir pagar en efectivo en ese lugar.</small>
        </label>
      </template>

      <template v-else-if="pasoActual === 'plano'">
        <div class="intro"><span class="icono">🗺️</span><div><h3>Arma el plano de {{ espacios }}</h3><p class="muted">Crea las categorías con su precio, forma y tamaño, y coloca cada {{ espacio }} con un clic.</p></div></div>
        <div v-if="datos?.categorias?.length" class="tabla-scroll">
          <table class="tabla">
            <thead><tr><th>Categoría</th><th class="num">Precio</th><th class="num">Cantidad</th><th class="num">En el plano</th><th class="num">Personas</th></tr></thead>
            <tbody>
              <tr v-for="c in datos.categorias" :key="c.id">
                <td><span class="punto" :style="{ background: c.color || '#888' }"></span>{{ c.nombre }}</td>
                <td class="num">{{ bs(c.precio) }}</td>
                <td class="num">{{ c.cantidad }}</td>
                <td class="num" :class="{ alerta: c.ubicados < c.cantidad }">{{ c.ubicados }}</td>
                <td class="num">{{ c.personasPorPuesto }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="vacio-grande">
          <span class="grande">🧩</span>
          <p>Aún no hay categorías. Ej.: <b>{{ tipoElegido.ejemplo }}</b>.</p>
        </div>
        <button type="button" class="btn btn-primario cta" @click="abrirEditor">🎨 Abrir el diseñador del plano</button>
        <ol class="ayuda">
          <li>Sube una imagen o PDF del lugar como fondo (opcional).</li>
          <li>En <b>🏷️ Categorías y precios</b> crea cada categoría: nombre, precio, cantidad, forma, ancho y alto.</li>
          <li>Elige la categoría y toca el plano para colocar cada {{ espacio }}. Arrástralos para ordenarlos.</li>
          <li>Agrega textos y zonas (Escenario, Baños, Entrada…) para orientar al cliente.</li>
        </ol>
      </template>

      <template v-else-if="pasoActual === 'credenciales'">
        <div class="intro"><span class="icono">🪪</span><div><h3>{{ f.vendeEntradas ? 'Entradas digitales y credenciales' : 'Credenciales y pases' }}</h3><p class="muted">Llevan un código QR para controlar la entrada y la salida.</p></div></div>
        <div v-if="f.vendeEntradas" class="bloque entrada-digital">
          <h4>🎟️ Diseño de la entrada digital</h4>
          <DisenoEntrada v-if="id" :edicion="id" @personalizar="abrirDisenoCredencial('ENTRADA')" />
          <p v-else class="muted chico">Guarda la actividad para diseñar sus entradas.</p>
        </div>
        <h4 v-if="f.vendeEspacios && f.vendeEntradas" class="subtitulo-cred">🪪 Credenciales para quienes compran {{ espacios }}</h4>
        <div v-if="f.vendeEspacios" class="opciones">
          <button type="button" class="opcion" :class="{ on: f.usaCredenciales }" @click="f.usaCredenciales = true">
            <span class="grande">✅</span><strong>Sí, usa credenciales</strong><span class="muted">Se generan al aprobar cada compra.</span>
          </button>
          <button type="button" class="opcion" :class="{ on: !f.usaCredenciales }" @click="f.usaCredenciales = false">
            <span class="grande">🚫</span><strong>No hace falta</strong><span class="muted">Solo se entrega el recibo y se vende el espacio, sin registrar personas.</span>
          </button>
        </div>
        <template v-if="f.usaCredenciales && f.vendeEspacios">
          <template v-if="f.vendeEspacios">
            <label class="pregunta">
              <input v-model="f.credencialRepresentante" type="checkbox" />
              <span><strong>👤 Credencial para quien compra {{ espacio.endsWith('a') ? 'la' : 'el' }} {{ espacio }}</strong>
                <small>Con su nombre, {{ espacio.endsWith('a') ? 'la' : 'el' }} {{ espacio }} que compró y el QR de ingreso.</small></span>
            </label>
            <label v-if="f.credencialRepresentante" class="pregunta">
              <input v-model="f.credencialAcompanantes" type="checkbox" />
              <span><strong>👥 Pases para acompañantes</strong>
                <small>Las personas incluidas, además de quien compra, no dan sus datos: cada pase dice «Acompañante de {representante} · {{ mayuscula(espacio) }} {código}». Si lo dejas apagado, se registra el nombre y el C.I. de cada una.</small></span>
            </label>
            <div v-if="f.credencialRepresentante" class="bloque">
              <h4>👥 Personas por {{ espacio }} y personas extra</h4>
              <p class="muted chico">Cuántas personas incluye cada {{ espacio }} en su precio (cuenta a quien compra) y si se pueden sumar más pagando. Cada persona recibe su credencial; la extra, además, su recibo.</p>
              <p v-if="!datos?.categorias?.length" class="muted chico">Crea las categorías en el paso Plano y vuelve aquí para definirlo.</p>
              <div v-else class="cupos">
                <div v-for="c in datos.categorias" :key="c.id" class="cupo-cat">
                  <span class="nombre-cat"><span class="punto" :style="{ background: c.color || '#888' }"></span>{{ c.nombre }}</span>
                  <template v-if="cupos[c.id]">
                    <label class="campo"><span>Incluye (personas)</span><input v-model.number="cupos[c.id].personas" class="control" type="number" min="1" max="50" /></label>
                    <label class="campo"><span>Personas extra</span>
                      <select v-model="cupos[c.id].extras" class="control"><option :value="false">No se venden</option><option :value="true">Sí, pagando</option></select>
                    </label>
                    <label v-if="cupos[c.id].extras" class="campo"><span>Bs por persona extra</span><input v-model.number="cupos[c.id].costo" class="control" type="number" min="1" step="1" /></label>
                    <small class="resumen-cat">{{ resumenCupo(c.id) }}</small>
                  </template>
                </div>
              </div>
            </div>
          </template>
          <div v-if="ROLES_CREDENCIAL.length" class="bloque">
            <h4>🎨 Diseño de cada credencial</h4>
            <p class="muted chico">Cada una parte de una plantilla por defecto. Personalízala: fondo, tamaño, con o sin foto, dónde va el nombre, los datos de la actividad y el QR.</p>
            <div class="disenos">
              <div v-for="r in ROLES_CREDENCIAL" :key="r.rol" class="diseno-rol">
                <span class="icono-rol">{{ r.icono }}</span>
                <div class="crecer"><strong>{{ r.titulo }}</strong><small class="muted">{{ r.detalle }}</small>
                  <small v-if="disenoDe(r.rol)" class="listo">✓ Diseño propio: {{ disenoDe(r.rol).etiqueta }}</small>
                  <small v-else class="muted">Usa la plantilla por defecto</small></div>
                <button type="button" class="btn btn-sm" :disabled="!id" @click="abrirDisenoCredencial(r.rol)">{{ disenoDe(r.rol) ? 'Editar diseño' : 'Personalizar' }}</button>
              </div>
            </div>
          </div>
        </template>
      </template>

      <template v-else-if="pasoActual === 'publicar'">
        <div class="intro"><span class="icono">🚀</span><div><h3>Revisar y publicar</h3><p class="muted">Cuando todo esté en verde, publica y comparte el enlace.</p></div></div>
        <ul class="lista-revision">
          <li v-for="p in pendientes" :key="p.texto" :class="{ ok: p.ok, falta: !p.ok && p.obligatorio }">
            <span class="marca">{{ p.ok ? '✓' : p.obligatorio ? '✕' : '!' }}</span>
            <span class="crecer">{{ p.texto }}<small v-if="!p.obligatorio && !p.ok" class="muted"> · recomendado</small></span>
            <button v-if="!p.ok" type="button" class="btn btn-sm btn-fantasma" @click="irPaso(p.paso)">Completar</button>
          </li>
        </ul>

        <div class="bloque meta">
          <h4>🎯 Meta de recaudación <small class="muted">(opcional)</small></h4>
          <p class="muted chico">Lo vendido, lo cobrado y lo que falta <b>el sistema lo calcula solo y en vivo</b> en <b>Ventas y cobros</b>, con cada venta en oficina o compra en línea aprobada. La meta es solo tu objetivo, para ver qué tan cerca estás.</p>
          <div class="meta-filas">
            <div v-if="f.vendeEspacios"><span class="muted">Si vendes todos los {{ espacios }}</span><b>{{ bs(datos?.metaEspacios) }}</b></div>
            <div v-if="f.vendeEntradas"><span class="muted">Si vendes todas las entradas con cupo</span><b>{{ bs(datos?.metaEntradas) }}</b></div>
            <div class="total"><span>Lo máximo que podrías recaudar</span><b>{{ bs(datos?.metaCalculada) }}</b></div>
          </div>
          <p v-if="datos?.entradasSinCupo" class="muted chico">{{ datos.entradasSinCupo }} tipo(s) de entrada no tienen cupo, así que no se pueden sumar a ese máximo.</p>
          <div class="opciones-meta" role="radiogroup">
            <label class="check"><input v-model="f.metaAutomatica" type="radio" :value="true" /> Mi meta es vender todo ({{ bs(datos?.metaCalculada) }})</label>
            <label class="check"><input v-model="f.metaAutomatica" type="radio" :value="false" /> Prefiero poner otra cifra</label>
          </div>
          <label v-if="!f.metaAutomatica" class="campo"><span>Mi meta (Bs)</span><input v-model.number="f.metaRecaudacion" class="control" type="number" min="0" step="1" /></label>
        </div>

        <div class="publicacion" :class="{ publicada: datos?.publicada }">
          <template v-if="datos?.publicada">
            <div class="enlace">
              <strong>✅ Tu actividad está publicada</strong>
              <span class="muted chico">Comparte el enlace o descarga una imagen con el QR para tus redes. Para ocultarla, posponerla, cerrar las ventas o duplicarla usa el botón <b>Opciones</b> en <b>Mis actividades</b>.</span>
            </div>
            <div class="fila-acciones">
              <button type="button" class="btn btn-primario" @click="compartirAbierto = true">📣 Compartir: enlace y QR</button>
              <button type="button" class="btn" @click="verComoCliente">👁 Ver como cliente</button>
            </div>
          </template>
          <template v-else>
            <div class="enlace">
              <strong>{{ faltanObligatorios.length ? `Faltan ${faltanObligatorios.length} punto(s) para publicar` : 'Todo listo para publicar' }}</strong>
              <span class="muted chico">{{ f.ventaDesde ? 'Al publicarla aparece en la página pública con la cuenta regresiva; la venta se abre sola en la fecha que elegiste.' : 'Al publicarla aparece en la página pública y empieza a recibir compras.' }}</span>
            </div>
            <button type="button" class="btn btn-primario cta" :disabled="guardando || faltanObligatorios.length > 0" @click="publicar(true)">🚀 Publicar actividad</button>
          </template>
        </div>
        <CompartirActividad v-if="compartirAbierto && datos" :actividad="{ ...datos, id }" @cerrar="compartirAbierto = false" />
      </template>
    </section>

    <footer class="pie card">
      <button type="button" class="btn btn-fantasma" :disabled="guardando" @click="anterior">{{ indice === 0 ? 'Salir' : '← Anterior' }}</button>
      <span class="muted chico paso-txt">Paso {{ indice + 1 }} de {{ pasos.length }}</span>
      <button v-if="pasoActual !== 'publicar'" type="button" class="btn btn-primario" :disabled="guardando" @click="siguiente">
        {{ guardando ? 'Guardando…' : !id ? 'Crear y seguir →' : 'Guardar y seguir →' }}
      </button>
      <button v-else type="button" class="btn btn-primario" :disabled="guardando" @click="terminar">Guardar e ir a Mis actividades →</button>
    </footer>
  </div>
</template>

<style scoped>
.asistente { display: flex; flex-direction: column; gap: 0.9rem; max-width: 980px; margin: 0 auto; width: 100%; }
.cabecera { padding: 0.8rem 1rem; display: flex; flex-direction: column; gap: 0.8rem; }
.titulo { display: flex; align-items: center; gap: 0.6rem; min-width: 0; }
.titulo h2 { margin: 0; font-size: 1.15rem; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pasos { display: flex; gap: 0.3rem; overflow-x: auto; scrollbar-width: none; padding-bottom: 2px; }
.pasos::-webkit-scrollbar { display: none; }
.paso {
  flex: 1 0 auto; display: flex; align-items: center; gap: 0.45rem; padding: 0.45rem 0.7rem; border-radius: 999px;
  border: 1px solid var(--border); background: var(--panel-2); color: var(--muted); font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer;
}
.paso:disabled { cursor: not-allowed; opacity: 0.55; }
.paso .num { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 50%; background: var(--border); color: var(--text); font-size: 0.75rem; flex: none; }
.paso.hecho { color: var(--text); }
.paso.hecho .num { background: var(--ok); color: #fff; }
.paso.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.paso.on .num { background: rgba(255, 255, 255, 0.25); color: inherit; }
.cuerpo { padding: 1.2rem; display: flex; flex-direction: column; gap: 1rem; }
.intro { display: flex; gap: 0.8rem; align-items: center; }
.intro .icono { font-size: 2rem; }
.intro h3 { margin: 0; font-size: 1.15rem; }
.intro p { margin: 0.15rem 0 0; }
.grande { font-size: 1.6rem; line-height: 1; }
.control.grande { font-size: 1.1rem; padding: 0.7rem 0.8rem; }
.dos { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.9rem; }
.chips { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; }
.chip { border: 1px solid var(--border); background: var(--panel); color: var(--text); border-radius: 999px; padding: 0.4rem 0.85rem; font: inherit; font-size: 0.88rem; font-weight: 600; cursor: pointer; }
.chip.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.chip:disabled { opacity: 0.45; cursor: default; }
.chip-sm { font-size: 0.8rem; padding: 0.3rem 0.65rem; font-weight: 500; }
.chip-libre { width: 130px; border-radius: 999px; padding: 0.4rem 0.8rem; }
.chip-libre.on { border-color: var(--acento); box-shadow: 0 0 0 2px var(--acento-suave); }
.dias { display: flex; flex-direction: column; gap: 0.5rem; }
.dia { display: grid; grid-template-columns: minmax(140px, 1.4fr) 1fr 1fr auto; gap: 0.5rem; align-items: end; }
.dia label { display: flex; flex-direction: column; gap: 0.15rem; }
.dia small { color: var(--muted); font-size: 0.72rem; }
.fila-acciones { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; margin-top: 0.4rem; }
.chico { font-size: 0.82rem; margin: 0; }
.portada { display: flex; flex-direction: column; gap: 0.4rem; }
.portada img { width: 100%; max-height: 200px; object-fit: cover; border-radius: var(--radio); border: 1px solid var(--border); }
.portada-vacia { border: 1px dashed var(--border); border-radius: var(--radio); padding: 1.2rem; text-align: center; color: var(--muted); }
.opciones { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.8rem; }
.opcion {
  position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 0.35rem; text-align: left;
  border: 2px solid var(--border); background: var(--panel); color: var(--text); border-radius: var(--radio); padding: 1rem; font: inherit; cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.opcion:hover { border-color: var(--acento); }
.opcion.on { border-color: var(--acento); box-shadow: 0 0 0 3px var(--acento-suave); }
.opcion .check { position: absolute; top: 0.6rem; right: 0.6rem; width: 22px; height: 22px; border-radius: 6px; border: 2px solid var(--border); display: grid; place-items: center; font-size: 0.8rem; font-weight: 800; }
.opcion.on .check { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.opcion .muted { font-size: 0.85rem; }
.subtitulo-cred { margin: 0.4rem 0 0; }
.bloque { border: 1px solid var(--border); border-radius: var(--radio); padding: 0.9rem; display: flex; flex-direction: column; gap: 0.7rem; background: var(--panel-2); }
.bloque h4 { margin: 0; font-size: 0.98rem; }
.entradas { display: flex; flex-direction: column; gap: 0.6rem; }
.entrada { display: grid; grid-template-columns: 1.4fr 0.8fr 0.8fr 2fr auto; gap: 0.5rem; align-items: end; }
.tipos { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 0.5rem; }
.tipo { display: flex; flex-direction: column; align-items: center; gap: 0.3rem; padding: 0.7rem 0.3rem; border: 2px solid var(--border); background: var(--panel); color: var(--text); border-radius: var(--radio); font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; }
.tipo.on { border-color: var(--acento); background: var(--acento-suave); }
.segmentado { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.5rem; }
.segmentado button { display: flex; flex-direction: column; gap: 0.15rem; align-items: flex-start; text-align: left; padding: 0.6rem 0.8rem; border: 2px solid var(--border); background: var(--panel); color: var(--text); border-radius: var(--radio-sm); font: inherit; font-weight: 700; cursor: pointer; }
.segmentado button small { font-weight: 400; color: var(--muted); font-size: 0.78rem; }
.segmentado button.on { border-color: var(--acento); background: var(--acento-suave); }
.nota { margin: 0; padding: 0.6rem 0.8rem; border-radius: var(--radio-sm); background: var(--acento-suave); font-size: 0.88rem; }
.previa { border: 1px dashed var(--border); border-radius: var(--radio); padding: 0.8rem 1rem; }
.previa ul { margin: 0.4rem 0 0.6rem; padding-left: 1.2rem; }
.check { display: flex; gap: 0.45rem; align-items: center; }
.cobro { display: grid; grid-template-columns: 170px minmax(0, 1fr); gap: 1rem; align-items: start; }
.qr { display: flex; flex-direction: column; align-items: center; gap: 0.45rem; border: 1px dashed var(--border); border-radius: var(--radio); padding: 0.7rem; }
.qr img { width: 140px; height: 140px; object-fit: contain; background: #fff; border-radius: 6px; }
.formulario { display: flex; flex-direction: column; gap: 0.8rem; }
.punto { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 0.45rem; vertical-align: middle; }
.num { text-align: right; font-variant-numeric: tabular-nums; }
.alerta { color: var(--tramite); font-weight: 700; }
.vacio-grande { text-align: center; padding: 1.4rem; border: 1px dashed var(--border); border-radius: var(--radio); }
.vacio-grande p { margin: 0.4rem 0 0; }
.cta { align-self: flex-start; padding: 0.75rem 1.2rem; font-size: 1rem; }
.ayuda { margin: 0; padding-left: 1.2rem; color: var(--muted); display: flex; flex-direction: column; gap: 0.3rem; font-size: 0.9rem; }
.pregunta { display: flex; gap: 0.7rem; align-items: flex-start; padding: 0.8rem; border: 1px solid var(--border); border-radius: var(--radio); cursor: pointer; }
.pregunta input { width: 20px; height: 20px; margin-top: 0.1rem; flex: none; accent-color: var(--acento); }
.pregunta span { display: flex; flex-direction: column; gap: 0.2rem; }
.pregunta small { color: var(--muted); }
.cupos { display: flex; flex-direction: column; gap: 0.5rem; }
.cupo-cat { display: grid; grid-template-columns: minmax(120px, 1fr) repeat(3, minmax(110px, 150px)); gap: 0.4rem 0.6rem; align-items: end; background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.55rem 0.7rem; }
.nombre-cat { font-weight: 700; align-self: center; }
.resumen-cat { grid-column: 1 / -1; color: var(--muted); font-size: 0.78rem; }
@media (max-width: 640px) { .cupo-cat { grid-template-columns: 1fr 1fr; } .nombre-cat { grid-column: 1 / -1; } }
.disenos { display: flex; flex-direction: column; gap: 0.5rem; }
.diseno-rol { display: flex; gap: 0.7rem; align-items: center; padding: 0.6rem 0.7rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel); }
.diseno-rol .crecer { display: flex; flex-direction: column; gap: 0.1rem; min-width: 0; }
.diseno-rol small { font-size: 0.78rem; }
.diseno-rol .listo { color: var(--ok); font-weight: 700; }
.icono-rol { font-size: 1.5rem; }
.lista-revision { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.35rem; }
.lista-revision li { display: flex; align-items: center; gap: 0.6rem; padding: 0.5rem 0.7rem; border-radius: var(--radio-sm); background: var(--panel-2); }
.lista-revision .marca { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: var(--tramite); color: #fff; font-weight: 800; font-size: 0.8rem; flex: none; }
.lista-revision li.ok .marca { background: var(--ok); }
.lista-revision li.falta .marca { background: var(--danger); }
.crecer { flex: 1; }
.meta-filas { display: flex; flex-direction: column; gap: 0.3rem; }
.meta-filas div { display: flex; justify-content: space-between; gap: 1rem; font-variant-numeric: tabular-nums; }
.meta-filas .total { border-top: 1px solid var(--border); padding-top: 0.35rem; font-size: 1.05rem; }
.opciones-meta { display: flex; flex-direction: column; gap: 0.35rem; }
.publicacion { display: flex; gap: 1rem; align-items: center; justify-content: space-between; flex-wrap: wrap; padding: 1rem; border-radius: var(--radio); border: 2px dashed var(--border); }
.publicacion.publicada { border-style: solid; border-color: var(--ok); }
.enlace { display: flex; flex-direction: column; gap: 0.5rem; flex: 1; min-width: 240px; }
.copiar { display: flex; gap: 0.4rem; }
.copiar .control { flex: 1; min-width: 0; }
.qr-publico { display: flex; flex-direction: column; align-items: center; gap: 0.3rem; }
.qr-publico img { width: 150px; height: 150px; background: #fff; border-radius: 8px; padding: 4px; }
.pie { position: sticky; bottom: calc(var(--safe-bottom) + var(--tabbar-h)); z-index: 5; display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; padding: 0.7rem 1rem; box-shadow: var(--sombra-md); }
.vacio { text-align: center; color: var(--muted); padding: 2rem; }
@media (max-width: 760px) {
  .dos, .opciones, .segmentado, .cobro { grid-template-columns: 1fr; }
  .tipos { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .entrada { grid-template-columns: 1fr 1fr; }
  .entrada .ancho { grid-column: 1 / -1; }
  .dia { grid-template-columns: 1fr 1fr auto; }
  .dia label:first-child { grid-column: 1 / -1; }
  .paso .txt { display: none; }
  .paso.on .txt { display: inline; }
  .cuerpo { padding: 1rem; }
  .diseno-rol { flex-wrap: wrap; }
  .paso-txt { display: none; }
}
</style>
