<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { ediciones, cargarActividades } from '../ui/actividadElegida';
import ModalEntrega from '../components/ModalEntrega.vue';
import { pedirTexto } from '../ui/alerta';
import { useAuthStore } from '../stores/auth';
import { canalDe } from '../ui/canal';

const ESTADOS = [
  { v: '', t: 'Vigentes y por validar' },
  { v: 'VIGENTE', t: 'Vigentes / confirmadas' },
  { v: 'POR_VALIDAR', t: 'Pago por validar' },
  { v: 'APARTADA', t: 'Apartadas sin pago' },
  { v: 'ANULADA', t: 'Anuladas' },
  { v: 'RECHAZADA', t: 'Rechazadas' },
  { v: 'CANCELADA', t: 'Canceladas' },
  { v: 'VENCIDA', t: 'Vencidas' },
  { v: 'TODAS', t: 'Todas' },
];

const route = useRoute();
const actividad = ref(/^[fe]-\d+$/.test(route.query.actividad) ? route.query.actividad : '');
watch(() => route.query.actividad, (valor) => {
  if (route.query.m === 'inscripciones' && /^[fe]-\d+$/.test(valor)) actividad.value = valor;
});
const categoria = ref('');
const vendedor = ref('');
const estado = ref('');
const busqueda = ref('');

const filas = ref([]);
const opciones = ref({ categorias: [], vendedores: [], categoriaEs: 'categoría' });
const cargando = ref(true);
const abierta = ref(null);

const especifica = computed(() => /^[fe]-\d+$/.test(actividad.value));
const conCategorias = computed(() => especifica.value && opciones.value.categorias.length > 0);
const etiquetaCategoria = computed(() => (actividad.value.startsWith('e-') ? 'Zona' : 'Categoría'));

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const cuando = (t) => {
  if (!t) return '—';
  try { return new Date(t).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' }); } catch { return '—'; }
};
const sinAcentos = (s) => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

function consulta() {
  const p = new URLSearchParams();
  if (actividad.value) p.set('actividad', actividad.value);
  if (categoria.value && conCategorias.value) p.set('categoria', categoria.value);
  if (vendedor.value) p.set('vendedor', vendedor.value);
  if (estado.value) p.set('estado', estado.value);
  const q = p.toString();
  return q ? `?${q}` : '';
}

async function cargarOpciones() {
  try {
    const r = await apiFetch(`/api/app/inscripciones/general/opciones${actividad.value ? `?actividad=${encodeURIComponent(actividad.value)}` : ''}`);
    if (r.ok) opciones.value = await r.json();
  } catch { }
  if (!opciones.value.categorias.some((c) => String(c.id) === categoria.value)) categoria.value = '';
  if (vendedor.value && !opciones.value.vendedores.some((v) => String(v.id) === vendedor.value)) vendedor.value = '';
}

let pedido = 0;
async function cargar() {
  const mio = ++pedido;
  cargando.value = true;
  try {
    const r = await apiFetch(`/api/app/inscripciones/general${consulta()}`);
    if (mio !== pedido) return;
    if (!r.ok) { toast(r.status === 403 ? 'No tienes acceso a las inscripciones' : 'No se pudo cargar el listado', 'error'); filas.value = []; return; }
    filas.value = await r.json();
  } catch (e) {
    if (mio === pedido) toast(e.message, 'error');
  } finally {
    if (mio === pedido) cargando.value = false;
  }
}

const visibles = computed(() => {
  const q = sinAcentos(busqueda.value.trim());
  if (!q) return filas.value;
  return filas.value.filter((f) => sinAcentos(
    `${f.codigo} ${f.cliente} ${f.representante || ''} ${f.ci || ''} ${f.celular || ''} ${f.espacios || ''} ${f.categoria || ''} ${f.vendedor || ''} ${f.actividad || ''}`,
  ).includes(q));
});

const totales = computed(() => {
  const vivas = visibles.value.filter((f) => f.estadoGeneral === 'VIGENTE' || f.estadoGeneral === 'POR_VALIDAR');
  const vendido = vivas.reduce((s, f) => s + Number(f.monto || 0), 0);
  const cobrado = vivas.filter((f) => f.pagado).reduce((s, f) => s + Number(f.monto || 0), 0);
  return {
    cantidad: visibles.value.length,
    ferias: visibles.value.filter((f) => f.tipo === 'FERIA').length,
    vendido, cobrado, porCobrar: vendido - cobrado,
  };
});

function claseEstado(f) {
  if (f.estadoGeneral === 'VIGENTE') return 'badge-ok';
  if (f.estadoGeneral === 'POR_VALIDAR' || f.estadoGeneral === 'APARTADA') return 'badge-parcial';
  return 'badge-danger';
}

const puedeEntregar = (f) => (f.tipo === 'FERIA' ? f.estadoGeneral !== 'ANULADA' : f.estadoGeneral === 'VIGENTE');
const auth = useAuthStore();
const anulando = ref(null);

async function anular(f) {
  const motivo = await pedirTexto(`Se anulará la venta de «${f.cliente}» (${f.espacios || 'sin espacios'}) y sus espacios vuelven a quedar libres. Quedará en Anulaciones con este motivo.`, {
    titulo: 'Anular la venta', aceptar: 'Anular venta', marcador: 'Ej. El cliente desistió y se le devolvió el dinero',
  });
  if (motivo == null) return;
  if (motivo.trim().length < 4) { toast('Escribe el motivo de la anulación', 'advertencia'); return; }
  anulando.value = f.id;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${f.id}/cancelar`, { method: 'POST', body: JSON.stringify({ motivo: motivo.trim() }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo anular la venta', 'error'); return; }
    toast(d.mensaje || 'Venta anulada', 'ok');
    await cargar();
  } finally {
    anulando.value = null;
  }
}

function limpiar() {
  actividad.value = '';
  categoria.value = '';
  vendedor.value = '';
  estado.value = '';
  busqueda.value = '';
}

watch(actividad, async () => {
  categoria.value = '';
  await cargarOpciones();
  cargar();
});
watch([categoria, vendedor, estado], cargar);

onMounted(async () => {
  cargarActividades();
  await cargarOpciones();
  cargar();
});
</script>

<template>
  <div class="general">
    <section class="card filtros">
      <label class="campo ancho">
        <span>Actividad</span>
        <select v-model="actividad" class="control">
          <option value="">Todas las actividades</option>
          <optgroup v-if="ediciones.length" label="Actividades">
            <option v-for="a in ediciones" :key="a.clave" :value="a.clave">{{ a.nombre }}{{ a.activa ? ' (en curso)' : '' }}</option>
          </optgroup>
        </select>
      </label>
      <label class="campo" :class="{ apagado: !conCategorias }"
             :title="conCategorias ? '' : 'Elige primero una actividad que tenga categorías o zonas'">
        <span>{{ etiquetaCategoria }}</span>
        <select v-model="categoria" class="control" :disabled="!conCategorias">
          <option value="">{{ conCategorias ? 'Todas' : 'Elige una actividad' }}</option>
          <option v-for="c in opciones.categorias" :key="c.id" :value="String(c.id)">{{ c.nombre }}</option>
        </select>
      </label>
      <label class="campo">
        <span>Atendió</span>
        <select v-model="vendedor" class="control">
          <option value="">Todos</option>
          <option v-for="v in opciones.vendedores" :key="v.id" :value="String(v.id)">{{ v.nombre }}</option>
        </select>
      </label>
      <label class="campo">
        <span>Estado</span>
        <select v-model="estado" class="control">
          <option v-for="e in ESTADOS" :key="e.v" :value="e.v">{{ e.t }}</option>
        </select>
      </label>
      <label class="campo ancho busca">
        <span>Buscar</span>
        <input v-model="busqueda" class="control" type="search" placeholder="Cliente, C.I., celular, código o espacio…" />
      </label>
      <button type="button" class="btn btn-fantasma btn-sm limpiar" @click="limpiar">Limpiar filtros</button>
    </section>

    <div class="resumen">
      <div class="card kpi"><strong>{{ totales.cantidad }}</strong><span>ventas</span></div>
      <div class="card kpi"><strong>{{ bs(totales.vendido) }}</strong><span>vendido (vigentes y por validar)</span></div>
      <div class="card kpi"><strong>{{ bs(totales.cobrado) }}</strong><span>cobrado</span></div>
      <div class="card kpi" :class="{ ojo: totales.porCobrar > 0 }"><strong>{{ bs(totales.porCobrar) }}</strong><span>por cobrar o validar</span></div>
    </div>

    <div v-if="cargando && !filas.length" class="vacio">Cargando…</div>
    <div v-else-if="!visibles.length" class="vacio card">No hay inscripciones con estos filtros.</div>
    <div v-else class="card tabla-scroll">
      <table class="tabla lista">
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Actividad</th>
            <th>Cliente</th>
            <th>Espacio</th>
            <th class="num">Monto</th>
            <th>Estado</th>
            <th title="En línea: quién aprobó · En persona: quién registró">Atendió</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="f in visibles" :key="`${f.tipo}-${f.id}`">
            <td data-t="Fecha" class="nowrap">{{ cuando(f.fecha) }}<small class="muted bloque">{{ f.codigo }}</small></td>
            <td data-t="Actividad">
              {{ f.actividad || '—' }}
            </td>
            <td data-t="Cliente">
              <strong>{{ f.cliente }}</strong>
              <small class="muted bloque">
                <template v-if="f.representante">{{ f.representante }} · </template>C.I. {{ f.ci || '—' }}<template v-if="f.celular"> · {{ f.celular }}</template>
              </small>
            </td>
            <td data-t="Espacio">
              {{ f.espacios || '—' }}
              <small v-if="f.categoria || f.personas" class="muted bloque">
                {{ f.categoria || '' }}<template v-if="f.personas"> · {{ f.personas }} persona{{ f.personas === 1 ? '' : 's' }}</template>
              </small>
            </td>
            <td data-t="Monto" class="num">
              {{ bs(f.monto) }}
              <small class="bloque" :class="f.pagado ? 'pagado' : 'muted'">{{ f.pagado ? (f.formaPago || 'cobrado') : (f.formaPago || 'sin comprobante') }}</small>
            </td>
            <td data-t="Estado"><span class="badge" :class="claseEstado(f)">{{ f.estado }}</span></td>
            <td data-t="Atendió" class="atendio"><span class="canal" :class="canalDe(f.canal).clase">{{ canalDe(f.canal).icono }} {{ canalDe(f.canal).texto }}</span><small class="bloque">{{ canalDe(f.canal).rol }}: {{ f.vendedor || '—' }}</small></td>
            <td class="acciones">
              <button v-if="puedeEntregar(f)" type="button" class="btn btn-sm" @click="abierta = f">📲 Entregar al cliente</button>
              <button v-if="auth.puedeEditarPlano && f.tipo === 'FERIA' && f.estadoGeneral !== 'ANULADA'" type="button" class="btn btn-sm btn-fantasma peligro" :disabled="anulando === f.id" title="Anular la venta indicando el motivo" @click="anular(f)">Anular…</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <ModalEntrega v-if="abierta" :inscripcion-id="abierta.id" @cerrar="abierta = null" />
  </div>
</template>

<style scoped>
.general { display: flex; flex-direction: column; gap: 0.9rem; }
.filtros { display: flex; flex-wrap: wrap; gap: 0.6rem 0.75rem; align-items: flex-end; padding: 0.85rem; }
.campo { display: flex; flex-direction: column; gap: 0.25rem; flex: 1 1 160px; min-width: 0; }
.campo.ancho { flex: 2 1 240px; }
.campo > span { font-size: 0.78rem; font-weight: 700; color: var(--muted); }
.campo.apagado > span { opacity: 0.6; }
.limpiar { flex: none; }

.resumen { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.75rem; }
.kpi { padding: 0.75rem 0.85rem; display: flex; flex-direction: column; gap: 0.15rem; }
.kpi strong { font-size: 1.3rem; line-height: 1.1; font-variant-numeric: tabular-nums; }
.kpi span { font-size: 0.8rem; color: var(--muted); }
.kpi.ojo strong { color: var(--tramite); }

.lista td { vertical-align: top; }
.num { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.nowrap { white-space: nowrap; }
.bloque { display: block; font-size: 0.78rem; margin-top: 0.1rem; }
.peligro { color: var(--danger); }
td.acciones { white-space: nowrap; }
td.acciones .btn + .btn { margin-left: 0.3rem; }
.pagado { color: var(--ok); font-weight: 600; }
.tipo { display: inline-grid; place-items: center; width: 1.6rem; height: 1.6rem; border-radius: 50%; margin-right: 0.25rem; background: var(--panel-2); }

@media (max-width: 900px) {
  .resumen { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 720px) {
  .tabla-scroll { overflow: visible; background: transparent; border: 0; box-shadow: none; }
  .lista thead { display: none; }
  .lista, .lista tbody, .lista tr, .lista td { display: block; width: 100%; }
  .lista tr {
    background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio);
    padding: 0.6rem 0.75rem; margin-bottom: 0.6rem;
  }
  .lista td { border: 0; padding: 0.25rem 0; text-align: left; }
  .lista td[data-t]::before {
    content: attr(data-t); display: block; font-size: 0.7rem; font-weight: 700; text-transform: uppercase;
    letter-spacing: 0.04em; color: var(--muted);
  }
  .lista td.acciones { text-align: left; padding-top: 0.45rem; }
  .lista td.acciones .btn { width: 100%; }
  .num { text-align: left; }
}

@media (max-width: 480px) {
  .resumen { grid-template-columns: 1fr; }
}
</style>
