<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { apiFetch, jsonOError } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { confirmar, pedirTexto } from '../ui/alerta';
import { descargarPdf } from '../ui/descargas';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { alCambiarPublico } from '../ui/canalPublico';

const actividades = ref([]);
const edicion = ref(null);
const estado = ref('REVISION');
const lista = ref([]);
const resumen = ref(null);
const cargando = ref(false);
const ocupado = ref(null);
let soltar = null;
let pendiente = null;

const FILTROS = [['REVISION', 'Por validar'], ['APROBADA', 'Aprobadas'], ['RECHAZADA', 'Rechazadas'], ['', 'Todas']];
const ESTADO = { REVISION: ['Por validar', 'badge-parcial'], APROBADA: ['Aprobada', 'badge-ok'], RECHAZADA: ['Rechazada', 'badge-danger'], ANULADA: ['Anulada', 'badge-muted'] };
const MODO = { COMPROBANTE: '📎 Comprobante', OFICINA: '🏢 Pagará en persona', EFECTIVO: '💵 EFECTIVO', QR: '📱 QR en punto de venta', GRATIS: '🎁 Gratis' };
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;
const hora = (s) => (s ? new Date(s).toLocaleString('es-BO', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '');
const esPdf = (u) => /\.pdf($|\?)/i.test(u || '');

async function cargarActividades() {
  try {
    const l = await jsonOError(await apiFetch('/api/app/ediciones/resumen'), 'No se pudieron cargar las actividades');
    actividades.value = l.filter((a) => a.vendeEntradas);
    if (!edicion.value || !actividades.value.some((a) => a.id === edicion.value)) edicion.value = actividades.value[0]?.id ?? null;
  } catch (e) { toast(e.message, 'error'); }
}

async function cargar(silencioso = false) {
  if (!edicion.value) { lista.value = []; resumen.value = null; return; }
  if (!silencioso) cargando.value = true;
  try {
    const q = new URLSearchParams({ edicion: edicion.value, ...(estado.value ? { estado: estado.value } : {}) });
    const [l, r] = await Promise.all([
      jsonOError(await apiFetch(`/api/app/entradas?${q}`), 'No se pudieron cargar las compras'),
      jsonOError(await apiFetch(`/api/app/entradas/resumen?edicion=${edicion.value}`), 'No se pudo cargar el resumen'),
    ]);
    lista.value = l;
    resumen.value = r;
  } catch (e) {
    if (!silencioso) toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function accion(c, tipo) {
  let cuerpo = null;
  if (tipo === 'aprobar') {
    if (!(await confirmar(`Se aprobará la compra de ${c.nombre} (${c.resumen}, ${bs(c.total)}) y se generarán sus pases con QR.`, { titulo: '¿Aprobar la compra?', aceptar: 'Sí, aprobar' }))) return;
  } else {
    const motivo = await pedirTexto(tipo === 'rechazar' ? 'Escribe el motivo. El cliente lo verá en su compra.' : 'Escribe el motivo de la anulación.',
      { titulo: tipo === 'rechazar' ? 'Rechazar compra' : 'Anular compra', aceptar: tipo === 'rechazar' ? 'Rechazar' : 'Anular', marcador: 'Ej. El monto del comprobante no coincide' });
    if (!motivo) return;
    cuerpo = JSON.stringify({ motivo });
  }
  ocupado.value = c.id;
  try {
    const r = await apiFetch(`/api/app/entradas/${c.id}/${tipo}`, { method: 'POST', body: cuerpo });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo completar', 'error'); return; }
    toast(d.mensaje, 'ok');
    await cargar(true);
  } finally {
    ocupado.value = null;
  }
}

function mensajeCliente(c) {
  const base = `Hola ${c.nombre.split(' ')[0]}, te escribimos por tu compra de entradas ${c.codigo}`;
  const enlace = `${window.location.origin}/?compra=${c.codigo}`;
  if (c.estado === 'APROBADA') return `${base}: ¡ya está aprobada! Descarga tus entradas aquí: ${enlace}`;
  if (c.estado === 'RECHAZADA') return `${base}: no pudimos aprobarla. Motivo: ${c.motivo}. ${enlace}`;
  return `${base}.`;
}

const actual = computed(() => actividades.value.find((a) => a.id === edicion.value));

watch([edicion, estado], () => cargar());
onMounted(async () => {
  await cargarActividades();
  await cargar();
  soltar = alCambiarPublico((m) => {
    if (m?.tipo !== 'entradas') return;
    clearTimeout(pendiente);
    pendiente = setTimeout(() => cargar(true), 500);
  });
});
onUnmounted(() => { soltar?.(); clearTimeout(pendiente); });
</script>

<template>
  <div class="entradas">
    <p v-if="!actividades.length" class="vacio">Ninguna actividad vende entradas. Actívalo en el paso «Qué vendes» de la actividad.</p>
    <template v-else>
      <div class="cab">
        <select v-model.number="edicion" class="control" aria-label="Actividad">
          <option v-for="a in actividades" :key="a.id" :value="a.id">{{ a.nombre }}</option>
        </select>
        <div class="filtros">
          <button v-for="[k, t] in FILTROS" :key="k" type="button" class="chip" :class="{ on: estado === k }" @click="estado = k">{{ t }}</button>
        </div>
      </div>

      <div v-if="resumen" class="kpis">
        <div><span>Por validar</span><b :class="{ alerta: resumen.por_validar }">{{ resumen.por_validar }}</b><small>{{ bs(resumen.por_validar_monto) }}</small></div>
        <div><span>Entradas vendidas</span><b>{{ resumen.vendidas }}</b></div>
        <div><span>Recaudado</span><b>{{ bs(resumen.recaudado) }}</b></div>
        <div v-for="t in resumen.tipos" :key="t.id"><span>{{ t.nombre }}</span><b>{{ t.cupo == null ? '∞' : t.quedan }}</b><small>{{ t.cupo == null ? 'sin límite' : `quedan de ${t.cupo}` }}</small></div>
      </div>

      <p v-if="cargando" class="vacio">Cargando…</p>
      <p v-else-if="!lista.length" class="vacio">{{ estado === 'REVISION' ? '✓ No hay compras esperando revisión.' : 'No hay compras en este filtro.' }}</p>
      <div v-else class="lista">
        <article v-for="c in lista" :key="c.id" class="card compra" :class="{ efectivo: c.modoPago === 'EFECTIVO' }">
          <a v-if="c.comprobanteUrl" class="comprobante" :href="urlApi(c.comprobanteUrl)" target="_blank" rel="noopener" title="Ver comprobante en grande">
            <img v-if="!esPdf(c.comprobanteUrl)" :src="urlApi(c.comprobanteUrl)" alt="Comprobante" />
            <span v-else>📄 PDF</span>
          </a>
          <div v-else class="comprobante sin">{{ c.modoPago === 'EFECTIVO' ? '💵' : '—' }}</div>
          <div class="info">
            <div class="fila1">
              <strong>{{ c.nombre }}</strong>
              <span class="badge" :class="ESTADO[c.estado][1]">{{ ESTADO[c.estado][0] }}</span>
            </div>
            <span class="muted chico">{{ c.resumen }} · <b>{{ bs(c.total) }}</b></span>
            <span class="muted chico">C.I. {{ c.ci || '—' }} · {{ MODO[c.modoPago] || '—' }} · {{ hora(c.creadoEn) }}<template v-if="c.vendedor"> · vendió {{ c.vendedor }}</template></span>
            <span v-if="c.motivo" class="motivo">Motivo: {{ c.motivo }}</span>
          </div>
          <div class="acciones">
            <a class="btn btn-sm" :href="enlaceWhatsApp(c.celular, mensajeCliente(c))" target="_blank" rel="noopener" title="Escribir al cliente">💬 {{ c.celular }}</a>
            <template v-if="c.estado === 'REVISION'">
              <button class="btn btn-sm btn-primario" :disabled="ocupado === c.id" @click="accion(c, 'aprobar')">✓ Aprobar</button>
              <button class="btn btn-sm btn-fantasma" :disabled="ocupado === c.id" @click="accion(c, 'rechazar')">Rechazar</button>
            </template>
            <template v-else-if="c.estado === 'APROBADA'">
              <button class="btn btn-sm" @click="descargarPdf(`/api/app/entradas/compra/${c.codigo}/pases.pdf`, `entradas-${c.codigo}.pdf`)">🎟️ Pases</button>
              <button class="btn btn-sm" @click="descargarPdf(`/api/app/entradas/compra/${c.codigo}/recibo.pdf`, `recibo-${c.codigo}.pdf`)">🧾 Recibo</button>
              <button v-if="!c.ingresaron" class="btn btn-sm btn-fantasma" :disabled="ocupado === c.id" @click="accion(c, 'anular')">Anular</button>
            </template>
          </div>
        </article>
      </div>
      <p v-if="actual && !actual.publicada" class="muted chico">Esta actividad aún no está publicada: el público todavía no puede comprar.</p>
    </template>
  </div>
</template>

<style scoped>
.entradas { display: flex; flex-direction: column; gap: 0.9rem; }
.cab { display: flex; gap: 0.6rem; flex-wrap: wrap; align-items: center; }
.cab select { max-width: 320px; }
.filtros { display: flex; gap: 0.35rem; flex-wrap: wrap; }
.chip { border: 1px solid var(--border); background: var(--panel); color: var(--muted); border-radius: 999px; padding: 0.35rem 0.8rem; font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; }
.chip.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.kpis { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 0.6rem; }
.kpis div { background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio); padding: 0.7rem 0.8rem; display: flex; flex-direction: column; gap: 0.1rem; }
.kpis span { font-size: 0.72rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.kpis b { font-size: 1.3rem; font-variant-numeric: tabular-nums; }
.kpis b.alerta { color: var(--tramite); }
.kpis small { color: var(--muted); font-size: 0.75rem; }
.lista { display: flex; flex-direction: column; gap: 0.6rem; }
.compra { display: grid; grid-template-columns: 72px minmax(0, 1fr) auto; gap: 0.8rem; align-items: center; padding: 0.7rem 0.8rem; }
.compra.efectivo { border-left: 4px solid #f59e0b; }
.comprobante { width: 72px; height: 72px; border-radius: var(--radio-sm); overflow: hidden; display: grid; place-items: center; background: var(--panel-2); border: 1px solid var(--border); text-decoration: none; color: var(--text); font-weight: 700; font-size: 0.8rem; }
.comprobante img { width: 100%; height: 100%; object-fit: cover; }
.comprobante.sin { font-size: 1.6rem; }
.info { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }
.fila1 { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
.chico { font-size: 0.82rem; }
.motivo { font-size: 0.8rem; color: var(--danger); }
.acciones { display: flex; gap: 0.35rem; flex-wrap: wrap; justify-content: flex-end; }
.vacio { text-align: center; color: var(--muted); padding: 1.5rem; }
@media (max-width: 700px) {
  .compra { grid-template-columns: 56px minmax(0, 1fr); }
  .comprobante { width: 56px; height: 56px; }
  .acciones { grid-column: 1 / -1; justify-content: flex-start; }
}
</style>
