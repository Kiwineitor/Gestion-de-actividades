<script setup>
import { ref, computed, watch, onMounted, onUnmounted, onActivated } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AprobarSolicitud from '../components/AprobarSolicitud.vue';
import EntregaEntradas from '../components/EntregaEntradas.vue';
import { apiFetch, jsonOError } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { confirmar, pedirTexto } from '../ui/alerta';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { alCambiarPublico } from '../ui/canalPublico';
import { nombreEspacio, mayuscula } from '../ui/espacios';
import { canalDe } from '../ui/canal';
import { aModulo } from '../modulos';
import { useSolicitudesStore } from '../stores/solicitudes';

const route = useRoute();
const router = useRouter();
const solicitudesStore = useSolicitudesStore();
const CLAVE = 'cobros.actividad';

const actividades = ref([]);
const edicion = ref(Number(route.query.edicion) || Number(sessionStorage.getItem(CLAVE)) || null);
const pestana = ref(['resumen', 'validar', 'ventas'].includes(route.query.tab) ? route.query.tab : 'resumen');
const resumen = ref(null);
const solicitudes = ref([]);
const compras = ref([]);
const ventas = ref([]);
const cargando = ref(false);
const ocupado = ref(null);
const aprobando = ref(null);
const entregandoEntradas = ref(null);
const filtroTipo = ref('');
const filtroPago = ref('');
const filtroCanal = ref('');
const busqueda = ref('');
let soltar = null;
let pendiente = null;

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 0 })}`;
const hora = (s) => (s ? new Date(s).toLocaleString('es-BO', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—');
const esPdf = (u) => /\.pdf($|\?)/i.test(u || '');
const actual = computed(() => actividades.value.find((a) => a.id === edicion.value) || null);
const espacios = computed(() => mayuscula(nombreEspacio(actual.value || {}, true)));
const uno = computed(() => nombreEspacio(actual.value || {}));
const avance = computed(() => Math.min(100, Number(resumen.value?.avance || 0)));

const porValidar = computed(() => {
  const s = solicitudes.value.map((i) => ({
    clave: `s${i.id}`, tipo: 'ESPACIO', id: i.id, nombre: i.empresa || i.nombreCompleto, persona: i.nombreCompleto,
    celular: i.celular, detalle: `${i.categoriaNombre || ''}${i.puestoCodigo ? ` · ${mayuscula(uno.value)} ${i.puestoCodigo}` : ''}`,
    monto: i.precio, comprobanteUrl: i.comprobanteUrl, modo: i.modoPago, fecha: i.fechaRegistro, original: i,
  }));
  const c = compras.value.map((x) => ({
    clave: `c${x.id}`, tipo: 'ENTRADA', id: x.id, nombre: x.nombre, persona: x.ci ? `C.I. ${x.ci}` : '', celular: x.celular,
    detalle: x.resumen, monto: x.total, comprobanteUrl: x.comprobanteUrl, modo: x.modoPago, fecha: x.creadoEn, codigo: x.codigo, original: x,
  }));
  return [...s, ...c].sort((a, b) => String(a.fecha).localeCompare(String(b.fecha)));
});

const ventasFiltradas = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  return ventas.value
    .filter((v) => !filtroTipo.value || v.tipo === filtroTipo.value)
    .filter((v) => !filtroCanal.value || v.canal === filtroCanal.value)
    .filter((v) => !filtroPago.value || (filtroPago.value === 'COBRAR' ? Number(v.porCobrar) > 0 : Number(v.porCobrar) <= 0))
    .filter((v) => !q || `${v.comprador} ${v.codigo} ${v.detalle} ${v.celular} ${v.vendedor}`.toLowerCase().includes(q));
});
const totales = computed(() => ventasFiltradas.value.reduce((t, v) => ({
  total: t.total + Number(v.total || 0), cobrado: t.cobrado + Number(v.recaudado || 0), porCobrar: t.porCobrar + Number(v.porCobrar || 0),
}), { total: 0, cobrado: 0, porCobrar: 0 }));
const maxDia = computed(() => Math.max(1, ...(resumen.value?.porDia || []).map((d) => Number(d.monto))));
const maxMedio = computed(() => Math.max(1, ...(resumen.value?.porMedio || []).map((d) => Number(d.monto))));
const diasRecientes = computed(() => (resumen.value?.porDia || []).slice(-21));

async function cargarActividades() {
  try {
    const l = await jsonOError(await apiFetch('/api/app/ediciones/resumen'), 'No se pudieron cargar las actividades');
    actividades.value = l.filter((a) => a.vendeEntradas || a.vendeEspacios);
    if (!actividades.value.some((a) => a.id === edicion.value)) {
      edicion.value = (actividades.value.find((a) => a.publicada && !a.finalizada) || actividades.value.find((a) => a.activa) || actividades.value[0])?.id ?? null;
    }
  } catch (e) { toast(e.message, 'error'); }
}

async function cargar(silencioso = false) {
  if (!edicion.value) return;
  if (!silencioso) cargando.value = true;
  try {
    const peticiones = [apiFetch(`/api/app/cobros/${edicion.value}/resumen`)];
    if (pestana.value === 'validar') {
      peticiones.push(apiFetch('/api/app/interesados-stand'), apiFetch(`/api/app/entradas?edicion=${edicion.value}&estado=REVISION`));
    } else if (pestana.value === 'ventas') {
      peticiones.push(apiFetch(`/api/app/cobros/${edicion.value}/ventas`));
    }
    const [r, a, b] = await Promise.all(peticiones);
    resumen.value = await jsonOError(r, 'No se pudo cargar el resumen');
    if (pestana.value === 'validar') {
      solicitudes.value = a.ok ? (await a.json()).filter((i) => i.edicionId === edicion.value && i.estado === 'NUEVO') : [];
      compras.value = b.ok ? await b.json() : [];
    } else if (pestana.value === 'ventas') {
      ventas.value = await jsonOError(a, 'No se pudieron cargar las ventas');
    }
  } catch (e) {
    if (!silencioso) toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function aprobar(p) {
  if (p.tipo === 'ESPACIO') { aprobando.value = p.original; return; }
  if (!(await confirmar(`Se aprobará la compra de ${p.nombre} (${p.detalle}, ${bs(p.monto)}) y se generarán sus entradas con QR.`, { titulo: '¿Aprobar el pago?', aceptar: 'Sí, aprobar' }))) return;
  await accionEntrada(p, 'aprobar', null);
}

async function rechazar(p) {
  if (p.tipo === 'ESPACIO') {
    const motivoEspacio = await pedirTexto(`Escribe por qué se rechaza. ${mayuscula(uno.value.endsWith('a') ? 'la' : 'el')} ${uno.value} vuelve a quedar libre y la solicitud pasa a Anulaciones con este motivo. Antes de rechazar, puedes abrirla con «Revisar» y reemplazar el comprobante si el cliente te envió el correcto.`, { titulo: 'Rechazar la solicitud', aceptar: 'Rechazar', marcador: 'Ej. El comprobante no corresponde al monto' });
    if (motivoEspacio == null) return;
    if (motivoEspacio.trim().length < 4) { toast('Escribe el motivo del rechazo', 'advertencia'); return; }
    ocupado.value = p.clave;
    try {
      const r = await apiFetch(`/api/app/interesados-stand/${p.id}/estado`, { method: 'PATCH', body: JSON.stringify({ estado: 'DESCARTADO', motivo: motivoEspacio.trim() }) });
      const d = await r.json().catch(() => ({}));
      if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo rechazar', 'error'); return; }
      toast(d.mensaje || 'Solicitud rechazada', 'ok');
      await cargar(true);
    } finally { ocupado.value = null; }
    return;
  }
  const motivo = await pedirTexto('Escribe el motivo. El cliente lo verá en su compra.', { titulo: 'Rechazar pago', aceptar: 'Rechazar', marcador: 'Ej. El monto no coincide' });
  if (motivo) await accionEntrada(p, 'rechazar', JSON.stringify({ motivo }));
}

async function accionEntrada(p, tipo, cuerpo) {
  ocupado.value = p.clave;
  try {
    const r = await apiFetch(`/api/app/entradas/${p.id}/${tipo}`, { method: 'POST', body: cuerpo });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo completar', 'error'); return; }
    toast(d.mensaje, 'ok');
    if (tipo === 'aprobar') entregandoEntradas.value = { ...p.original };
    await cargar(true);
    solicitudesStore.cargar();
    cargarActividades();
  } finally { ocupado.value = null; }
}

function mensaje(p) {
  const base = `Hola ${String(p.persona || p.nombre).replace(/^C\.I\..*/, p.nombre).split(' ')[0]}, te escribimos de ${actual.value?.nombre || 'la actividad'}`;
  return p.tipo === 'ENTRADA' ? `${base} por tu compra de entradas ${p.codigo}.` : `${base} por tu solicitud (${p.detalle}).`;
}

function aprobada() {
  cargar(true);
  solicitudesStore.cargar();
}

function irPestana(t) {
  pestana.value = t;
}

watch(edicion, (v) => {
  try { if (v) sessionStorage.setItem(CLAVE, String(v)); } catch { }
  cargar();
});
watch(pestana, (t) => {
  if (route.query.m === 'cobros' && route.query.tab !== t) router.replace({ query: { ...route.query, tab: t } });
  cargar();
});
watch(() => route.query, (q) => {
  if (q.m !== 'cobros') return;
  if (q.tab && q.tab !== pestana.value && ['resumen', 'validar', 'ventas'].includes(q.tab)) pestana.value = q.tab;
  if (q.edicion && Number(q.edicion) !== edicion.value) edicion.value = Number(q.edicion);
});

onMounted(async () => {
  await cargarActividades();
  await cargar();
  soltar = alCambiarPublico((m) => {
    if (!['entradas', 'feria', 'actividad'].includes(m?.tipo)) return;
    clearTimeout(pendiente);
    pendiente = setTimeout(async () => { await cargarActividades(); cargar(true); }, 600);
  });
});
onActivated(async () => { await cargarActividades(); cargar(true); });
watch(() => [solicitudesStore.nuevosFeria, solicitudesStore.nuevasEntradas], () => { cargarActividades(); });
onUnmounted(() => { soltar?.(); clearTimeout(pendiente); });
</script>

<template>
  <div class="cobros">
    <header class="cab">
      <select v-model.number="edicion" class="control actividad" aria-label="Actividad">
        <option v-for="a in actividades" :key="a.id" :value="a.id">{{ a.nombre }}{{ a.finalizada ? ' (finalizada)' : !a.publicada ? ' (borrador)' : '' }}{{ a.porValidar ? ` · 🔔 ${a.porValidar} por validar` : '' }}</option>
      </select>
      <nav class="pestanas" role="tablist">
        <button role="tab" :class="{ on: pestana === 'resumen' }" @click="irPestana('resumen')">📊 Resumen</button>
        <button role="tab" :class="{ on: pestana === 'validar' }" @click="irPestana('validar')">
          📥 Por validar <span v-if="resumen?.porValidar" class="contador">{{ resumen.porValidar }}</span>
        </button>
        <button role="tab" :class="{ on: pestana === 'ventas' }" @click="irPestana('ventas')">🧾 Ventas</button>
      </nav>
    </header>

    <p v-if="!actividades.length" class="vacio card">Todavía no hay actividades que vendan. Créala en <router-link :to="aModulo('actividades')">Mis actividades</router-link>.</p>

    <template v-else-if="resumen">
      <section v-if="pestana === 'resumen'" class="resumen">
        <div class="card meta">
          <div class="meta-cab">
            <div>
              <span class="rotulo">Recaudado</span>
              <strong class="grande">{{ bs(resumen.recaudado) }}</strong>
              <span class="muted">de la meta {{ bs(resumen.meta) }}{{ resumen.metaAutomatica ? ' (si vendes todo)' : '' }}</span>
            </div>
            <span class="porcentaje">{{ avance }}%</span>
          </div>
          <div class="barra" role="progressbar" :aria-valuenow="avance" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: `${avance}%` }"></span></div>
        </div>

        <div class="kpis">
          <button type="button" class="card kpi" :class="{ alerta: resumen.porValidar }" @click="irPestana('validar')">
            <span class="rotulo">⏳ Por validar</span><strong>{{ resumen.porValidar }}</strong><small>{{ bs(resumen.porValidarMonto) }} esperando revisión</small>
          </button>
          <div class="card kpi">
            <span class="rotulo">🧾 Vendido</span><strong>{{ bs(resumen.vendido) }}</strong><small>ventas confirmadas</small>
          </div>
          <button type="button" class="card kpi" :class="{ aviso: Number(resumen.porCobrar) > 0 }" @click="filtroPago = 'COBRAR'; irPestana('ventas')">
            <span class="rotulo">💰 Por cobrar</span><strong>{{ bs(resumen.porCobrar) }}</strong><small>vendido sin comprobante</small>
          </button>
          <div class="card kpi">
            <span class="rotulo">🛒 Por vender</span><strong>{{ bs(resumen.porVender) }}</strong><small>lo que queda disponible</small>
          </div>
        </div>

        <div class="paneles">
          <div v-if="resumen.espacios" class="card panel">
            <h3>{{ espacios }}: {{ resumen.espacios.vendidos }} de {{ resumen.espacios.total }} vendidos</h3>
            <p class="muted chico">{{ resumen.espacios.libres }} libres · {{ resumen.espacios.enProceso }} en proceso o en revisión</p>
            <div v-for="c in resumen.espacios.categorias" :key="c.categoria" class="fila-barra">
              <span class="nombre">{{ c.categoria }}</span>
              <div class="mini"><span :style="{ width: `${c.puestosTotales ? (c.puestosVendidos / c.puestosTotales) * 100 : 0}%` }"></span></div>
              <span class="valor">{{ c.puestosVendidos }}/{{ c.puestosTotales }} · {{ bs(c.vendidoBs) }}</span>
            </div>
          </div>

          <div v-if="resumen.entradas" class="card panel">
            <h3>Entradas: {{ resumen.entradas.vendidas }} vendidas</h3>
            <p class="muted chico">{{ bs(resumen.entradas.recaudado) }} recaudado · las barras incluyen compras en revisión</p>
            <div v-for="t in resumen.entradas.tipos" :key="t.id" class="fila-barra">
              <span class="nombre">{{ t.nombre }}</span>
              <div class="mini"><span :style="{ width: `${t.cupo ? ((t.cupo - t.quedan) / t.cupo) * 100 : 0}%` }"></span></div>
              <span class="valor">{{ t.cupo == null ? 'sin límite' : `${t.cupo - t.quedan} de ${t.cupo}` }}</span>
            </div>
          </div>

          <div class="card panel">
            <h3>Cómo te pagaron</h3>
            <p v-if="!resumen.porMedio.length" class="muted chico">Todavía no hay cobros.</p>
            <div v-for="m in resumen.porMedio" :key="m.medio" class="fila-barra" :class="{ efectivo: m.medio === 'Efectivo' || m.medio === 'Al contado' }">
              <span class="nombre">{{ m.medio === 'Efectivo' || m.medio === 'Al contado' ? '💵 ' : '' }}{{ m.medio }}</span>
              <div class="mini"><span :style="{ width: `${(Number(m.monto) / maxMedio) * 100}%` }"></span></div>
              <span class="valor">{{ bs(m.monto) }}</span>
            </div>
          </div>

          <div class="card panel">
            <h3>Quién vendió</h3>
            <p v-if="!resumen.porVendedor.length" class="muted chico">Todavía no hay ventas.</p>
            <div v-for="v in resumen.porVendedor" :key="v.vendedor" class="fila-barra">
              <span class="nombre">{{ v.vendedor }}</span>
              <span class="muted chico">{{ v.operaciones }} venta{{ v.operaciones === 1 ? '' : 's' }}</span>
              <span class="valor">{{ bs(v.monto) }}</span>
            </div>
          </div>

          <div v-if="diasRecientes.length" class="card panel ancho">
            <h3>Ventas por día</h3>
            <div class="dias">
              <div v-for="d in diasRecientes" :key="d.fecha" class="dia" :title="`${d.fecha}: ${bs(d.monto)} (acumulado ${bs(d.acumulado)})`">
                <span class="col" :style="{ height: `${Math.max(4, (Number(d.monto) / maxDia) * 100)}%` }"></span>
                <small>{{ d.fecha.slice(8, 10) }}/{{ d.fecha.slice(5, 7) }}</small>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-else-if="pestana === 'validar'" class="validar">
        <p v-if="cargando" class="vacio">Cargando…</p>
        <p v-else-if="!porValidar.length" class="vacio card">✓ No hay pagos esperando revisión en esta actividad.</p>
        <article v-for="p in porValidar" :key="p.clave" class="card pendiente" :class="{ oficina: p.modo === 'OFICINA' }">
          <a v-if="p.comprobanteUrl" class="comprobante" :href="urlApi(p.comprobanteUrl)" target="_blank" rel="noopener" title="Ver comprobante en grande">
            <img v-if="!esPdf(p.comprobanteUrl)" :src="urlApi(p.comprobanteUrl)" alt="Comprobante" />
            <span v-else>📄 PDF</span>
          </a>
          <div v-else class="comprobante sin" :title="p.modo === 'OFICINA' ? 'Pagará en persona' : 'Sin comprobante'">{{ p.modo === 'OFICINA' ? '🏢' : '—' }}</div>
          <div class="info">
            <div class="fila1">
              <span class="badge" :class="p.tipo === 'ENTRADA' ? 'badge-ok' : 'badge-parcial'">{{ p.tipo === 'ENTRADA' ? '🎟️ Entradas' : `🗺️ ${mayuscula(uno)}` }}</span>
              <strong>{{ p.nombre }}</strong>
            </div>
            <span class="detalle">{{ p.detalle }} · <b>{{ bs(p.monto) }}</b></span>
            <span class="muted chico">{{ p.persona }}{{ p.persona ? ' · ' : '' }}{{ p.modo === 'OFICINA' ? 'Pagará en persona' : p.comprobanteUrl ? 'Adjuntó comprobante' : 'Sin comprobante' }} · {{ hora(p.fecha) }}</span>
          </div>
          <div class="acciones">
            <a class="btn btn-sm" :href="enlaceWhatsApp(p.celular, mensaje(p))" target="_blank" rel="noopener" title="Escribir por WhatsApp">💬 {{ p.celular }}</a>
            <button class="btn btn-sm btn-primario" :disabled="ocupado === p.clave" @click="aprobar(p)">{{ p.tipo === 'ESPACIO' ? '🔍 Revisar y aprobar' : '✓ Aprobar' }}</button>
            <button class="btn btn-sm btn-fantasma" :disabled="ocupado === p.clave" @click="rechazar(p)">Rechazar</button>
          </div>
        </article>
      </section>

      <section v-else class="ventas">
        <div class="filtros">
          <input v-model="busqueda" type="search" class="control buscar" placeholder="Buscar comprador, código, celular…" aria-label="Buscar venta" />
          <select v-model="filtroTipo" class="control" aria-label="Tipo"><option value="">Todo</option><option value="ESPACIO">{{ espacios }}</option><option value="ENTRADA">Entradas</option></select>
          <select v-model="filtroCanal" class="control" aria-label="Canal"><option value="">En línea y en persona</option><option value="EN_LINEA">🌐 Solo en línea</option><option value="EN_PERSONA">🏢 Solo en persona</option></select>
          <select v-model="filtroPago" class="control" aria-label="Cobro"><option value="">Cobradas y por cobrar</option><option value="COBRAR">Solo por cobrar</option><option value="COBRADO">Solo cobradas</option></select>
        </div>
        <p v-if="cargando" class="vacio">Cargando…</p>
        <p v-else-if="!ventasFiltradas.length" class="vacio card">No hay ventas con estos filtros.</p>
        <div v-else class="tabla-scroll card">
          <table class="tabla">
            <thead><tr><th>Fecha</th><th>Comprador</th><th>Detalle</th><th class="num">Total</th><th class="num">Cobrado</th><th>Pago</th><th title="En línea: quién aprobó la solicitud · En persona: quién registró la venta">Atendió</th><th></th></tr></thead>
            <tbody>
              <tr v-for="v in ventasFiltradas" :key="`${v.tipo}${v.id}`" :class="{ cobrar: Number(v.porCobrar) > 0 }">
                <td>{{ hora(v.fecha) }}</td>
                <td><strong>{{ v.comprador }}</strong><small class="muted bloque">{{ v.codigo }}</small></td>
                <td>{{ v.tipo === 'ENTRADA' ? '🎟️ ' : '🗺️ ' }}{{ v.detalle }}</td>
                <td class="num">{{ bs(v.total) }}</td>
                <td class="num">{{ bs(v.recaudado) }}<small v-if="Number(v.porCobrar) > 0" class="falta bloque">falta {{ bs(v.porCobrar) }}</small></td>
                <td><span :class="{ efectivo: v.medio === 'Efectivo' || v.medio === 'Al contado' }">{{ v.medio || '—' }}</span></td>
                <td class="atendio"><span class="canal" :class="canalDe(v.canal).clase">{{ canalDe(v.canal).icono }} {{ canalDe(v.canal).texto }}</span><small class="bloque">{{ canalDe(v.canal).rol }}: {{ v.atendio || '—' }}</small></td>
                <td class="acciones-fila"><button v-if="v.tipo === 'ENTRADA'" type="button" class="btn btn-sm" title="Ver, descargar y enviar sus entradas" @click="entregandoEntradas = { id: v.id, codigo: v.codigo, nombre: v.comprador, celular: v.celular, celularExtra: v.celularExtra, total: v.total, cantidad: v.cantidad, resumen: v.detalle }">🎟️ Entregar</button><a v-if="v.celular" class="btn btn-sm btn-fantasma" :href="enlaceWhatsApp(v.celular, `Hola, te escribimos de ${actual?.nombre || 'la actividad'}`)" target="_blank" rel="noopener" title="WhatsApp">💬</a></td>
              </tr>
            </tbody>
            <tfoot><tr><td colspan="3">{{ ventasFiltradas.length }} venta(s)</td><td class="num">{{ bs(totales.total) }}</td><td class="num">{{ bs(totales.cobrado) }}</td><td colspan="3">{{ totales.porCobrar ? `Por cobrar ${bs(totales.porCobrar)}` : '' }}</td></tr></tfoot>
          </table>
        </div>
        <p class="muted chico">Para corregir una venta, adjuntar un comprobante o anularla usa <router-link :to="aModulo('inscripciones')">Detalle de ventas</router-link>.</p>
      </section>
    </template>
    <p v-else-if="cargando" class="vacio">Cargando…</p>

    <EntregaEntradas v-if="entregandoEntradas" :compra="entregandoEntradas" :actividad="actual?.nombre || ''" @cerrar="entregandoEntradas = null" />
    <AprobarSolicitud v-if="aprobando" :solicitud="aprobando" :actividad="actual?.nombre || ''" @cerrar="aprobando = null" @aprobada="aprobada" @rechazada="aprobando = null; cargar(true)" @actualizada="cargar(true)" />
  </div>
</template>

<style scoped>
.cobros { display: flex; flex-direction: column; gap: 1rem; }
.cab { display: flex; gap: 0.8rem; align-items: center; flex-wrap: wrap; }
.acciones-fila { white-space: nowrap; }
.actividad { max-width: 340px; font-weight: 700; }
.pestanas { display: flex; gap: 0.3rem; flex-wrap: wrap; }
.pestanas button { border: 1px solid var(--border); background: var(--panel); color: var(--muted); border-radius: 999px; padding: 0.45rem 0.9rem; font: inherit; font-weight: 700; cursor: pointer; display: inline-flex; gap: 0.4rem; align-items: center; }
.pestanas button.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.contador { background: var(--danger); color: #fff; border-radius: 999px; font-size: 0.72rem; padding: 0 0.45rem; }
.resumen { display: flex; flex-direction: column; gap: 1rem; }
.meta { padding: 1.1rem 1.2rem; display: flex; flex-direction: column; gap: 0.7rem; background: linear-gradient(135deg, var(--acento-suave), var(--panel)); }
.meta-cab { display: flex; justify-content: space-between; align-items: flex-end; gap: 1rem; }
.meta-cab > div { display: flex; flex-direction: column; gap: 0.1rem; }
.rotulo { font-size: 0.75rem; font-weight: 700; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.grande { font-size: clamp(1.6rem, 4vw, 2.3rem); font-variant-numeric: tabular-nums; }
.porcentaje { font-size: clamp(1.4rem, 3.5vw, 2rem); font-weight: 800; color: var(--acento); }
.barra { height: 14px; border-radius: 999px; background: var(--border); overflow: hidden; }
.barra span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--acento), var(--ok)); transition: width 0.4s; }
.kpis { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0.8rem; }
.kpi { padding: 0.9rem 1rem; display: flex; flex-direction: column; gap: 0.15rem; text-align: left; font: inherit; color: var(--text); border: 1px solid var(--border); }
button.kpi { cursor: pointer; }
button.kpi:hover { border-color: var(--acento); }
.kpi strong { font-size: 1.45rem; font-variant-numeric: tabular-nums; }
.kpi small { color: var(--muted); font-size: 0.78rem; }
.kpi.alerta { border-color: var(--tramite); box-shadow: inset 4px 0 0 var(--tramite); }
.kpi.aviso strong { color: var(--tramite); }
.paneles { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.8rem; }
.panel { padding: 1rem; display: flex; flex-direction: column; gap: 0.5rem; }
.panel.ancho { grid-column: 1 / -1; }
.panel h3 { margin: 0; font-size: 1rem; }
.chico { font-size: 0.82rem; margin: 0; }
.fila-barra { display: grid; grid-template-columns: minmax(90px, 1fr) minmax(60px, 1.4fr) auto; gap: 0.6rem; align-items: center; font-size: 0.88rem; }
.fila-barra .nombre { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fila-barra .valor { font-variant-numeric: tabular-nums; font-weight: 700; text-align: right; }
.fila-barra.efectivo .nombre { color: #d97706; font-weight: 800; }
.mini { height: 8px; border-radius: 999px; background: var(--border); overflow: hidden; }
.mini span { display: block; height: 100%; background: var(--acento); border-radius: inherit; }
.dias { display: flex; align-items: flex-end; gap: 4px; height: 140px; overflow-x: auto; padding-top: 8px; }
.dia { flex: 1 0 22px; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; gap: 4px; }
.dia .col { width: 100%; max-width: 30px; background: var(--acento); border-radius: 6px 6px 2px 2px; }
.dia small { font-size: 0.65rem; color: var(--muted); }
.validar { display: flex; flex-direction: column; gap: 0.6rem; }
.pendiente { display: grid; grid-template-columns: 76px minmax(0, 1fr) auto; gap: 0.8rem; align-items: center; padding: 0.7rem 0.9rem; }
.pendiente.oficina { border-left: 4px solid #f59e0b; }
.comprobante { width: 76px; height: 76px; border-radius: var(--radio-sm); overflow: hidden; display: grid; place-items: center; background: var(--panel-2); border: 1px solid var(--border); color: var(--text); text-decoration: none; font-weight: 700; font-size: 0.8rem; }
.comprobante img { width: 100%; height: 100%; object-fit: cover; }
.comprobante.sin { font-size: 1.6rem; }
.info { display: flex; flex-direction: column; gap: 0.2rem; min-width: 0; }
.fila1 { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
.detalle { font-size: 0.9rem; }
.acciones { display: flex; gap: 0.35rem; flex-wrap: wrap; justify-content: flex-end; }
.ventas { display: flex; flex-direction: column; gap: 0.7rem; }
.filtros { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.buscar { flex: 1; min-width: 200px; }
.filtros select { width: auto; }
.num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
.bloque { display: block; }
.falta { color: var(--tramite); font-weight: 700; }
tr.cobrar td:first-child { box-shadow: inset 4px 0 0 var(--tramite); }
.efectivo { color: #d97706; font-weight: 800; }
tfoot td { font-weight: 800; }
.vacio { text-align: center; color: var(--muted); padding: 1.5rem; }
@media (max-width: 900px) { .kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); } .paneles { grid-template-columns: 1fr; } }
@media (max-width: 640px) {
  .pestanas { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .pestanas button { justify-content: center; padding: 0.5rem 0.4rem; font-size: 0.88rem; }
  .actividad { max-width: none; width: 100%; }
  .pendiente { grid-template-columns: 60px minmax(0, 1fr); }
  .comprobante { width: 60px; height: 60px; }
  .acciones { grid-column: 1 / -1; justify-content: flex-start; }
  .kpi strong { font-size: 1.2rem; }
}
</style>
