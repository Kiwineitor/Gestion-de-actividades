<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { descargarArchivo } from '../ui/descargas';
import { eventoElegido } from '../ui/actividadElegida';
import BarrasHorizontales from '../components/BarrasHorizontales.vue';
import ModalEntregaReserva from '../components/ModalEntregaReserva.vue';

const datos = ref(null);
const cargando = ref(true);
const descargando = ref('');
const busqueda = ref('');
const filtroEstado = ref('');
const abierta = ref(null);

const ETIQUETAS = {
  CONFIRMADA: 'Confirmada', EN_REVISION: 'Pago por validar', BLOQUEADA: 'Apartada, sin pago',
  RECHAZADA: 'Rechazada', VENCIDA: 'Vencida', CANCELADA: 'Cancelada',
};

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const ent = (n) => Number(n || 0).toLocaleString('es-BO');
const pct = (a, b) => (b ? Math.round((Number(a) / Number(b)) * 100) : 0);
const cuando = (t) => {
  if (!t) return '—';
  try { return new Date(t).toLocaleString('es-BO', { dateStyle: 'short', timeStyle: 'short' }); } catch { return '—'; }
};

async function cargar() {
  if (!eventoElegido.value) return;
  cargando.value = true;
  try {
    const r = await apiFetch(`/api/app/reportes-evento/${eventoElegido.value}`);
    if (!r.ok) { datos.value = null; toast(r.status === 403 ? 'No tienes acceso a este reporte' : 'No se pudo cargar el reporte', 'error'); return; }
    datos.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function descargar(formato) {
  descargando.value = formato;
  try {
    const nombre = `reporte-${(datos.value?.evento?.nombre || 'evento').replace(/[^\w\-]+/g, '-').toLowerCase()}.${formato === 'pdf' ? 'pdf' : 'xlsx'}`;
    await descargarArchivo(`/api/app/reportes-evento/${eventoElegido.value}/${formato}`, nombre);
    toast(formato === 'pdf' ? 'PDF descargado' : 'Excel descargado', 'ok');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    descargando.value = '';
  }
}

const t = computed(() => datos.value?.totales || {});
const estados = computed(() => Object.entries(datos.value?.porEstado || {}).map(([k, v]) => ({ k, t: ETIQUETAS[k] || k, n: v })));

const reservas = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  return (datos.value?.reservas || []).filter((r) => (!filtroEstado.value || r.estado === filtroEstado.value)
    && (!q || `${r.codigo} ${r.cliente} ${r.ci} ${r.celular} ${r.mesa} ${r.zona || ''} ${r.atendio || ''}`.toLowerCase().includes(q)));
});

function claseEstado(e) {
  if (e === 'CONFIRMADA') return 'badge-ok';
  if (e === 'EN_REVISION' || e === 'BLOQUEADA') return 'badge-parcial';
  return 'badge-danger';
}

watch(eventoElegido, cargar);
onMounted(cargar);
</script>

<template>
  <div class="reporte-evento">
    <div v-if="cargando && !datos" class="vacio">Cargando…</div>
    <div v-else-if="!datos" class="vacio card">No se encontró el evento.</div>
    <template v-else>
      <header class="cabecera">
        <div>
          <h2>{{ datos.evento.nombre }}</h2>
          <p class="muted">{{ datos.evento.local }} · {{ cuando(datos.evento.fecha) }}</p>
        </div>
        <div class="botones">
          <button type="button" class="btn btn-sm" :disabled="Boolean(descargando)" @click="descargar('excel')">
            {{ descargando === 'excel' ? 'Preparando…' : '⬇️ Excel' }}
          </button>
          <button type="button" class="btn btn-sm" :disabled="Boolean(descargando)" @click="descargar('pdf')">
            {{ descargando === 'pdf' ? 'Preparando…' : '⬇️ PDF' }}
          </button>
          <button type="button" class="btn btn-fantasma btn-sm" :disabled="cargando" @click="cargar">🔄</button>
        </div>
      </header>

      <div class="kpis">
        <div class="card kpi"><span>Recaudado</span><strong>{{ bs(t.recaudado) }}</strong><small class="muted">reservas confirmadas</small></div>
        <div class="card kpi"><span>Mesas vendidas</span><strong>{{ ent(t.vendidas) }} / {{ ent(t.mesas) }}</strong><small class="muted">{{ pct(t.vendidas, t.mesas) }} % · {{ ent(t.libres) }} libres</small></div>
        <div class="card kpi" :class="{ ojo: t.porValidar }"><span>Pagos por validar</span><strong>{{ ent(t.porValidar) }}</strong><small class="muted">{{ bs(t.montoPorValidar) }}</small></div>
        <div class="card kpi"><span>Personas</span><strong>{{ ent(t.personas) }}</strong><small class="muted">{{ ent(t.pases) }} pases emitidos</small></div>
        <div class="card kpi"><span>Ingresaron</span><strong>{{ ent(t.pasesUsados) }}</strong><small class="muted">{{ pct(t.pasesUsados, t.pases) }} % de los pases · {{ ent(t.reservasConIngreso) }} mesas</small></div>
        <div class="card kpi"><span>Apartadas</span><strong>{{ ent(t.apartadas) }}</strong><small class="muted">{{ ent(t.fueraDeVenta) }} fuera de venta</small></div>
      </div>

      <div class="graficos">
        <section class="card bloque">
          <h3>Reservas por estado</h3>
          <BarrasHorizontales v-if="estados.length" :items="estados.map((e) => ({ label: e.t, valor: Number(e.n) }))" :formato="ent" />
          <p v-else class="muted">Aún no hay reservas.</p>
        </section>
        <section class="card bloque">
          <h3>Por zona</h3>
          <table v-if="datos.porZona.length" class="tabla mini">
            <thead><tr><th>Zona</th><th class="num">Vendidas</th><th class="num">Personas</th><th class="num">Recaudado</th></tr></thead>
            <tbody>
              <tr v-for="z in datos.porZona" :key="z.zona">
                <td>{{ z.zona }}</td>
                <td class="num">{{ ent(z.vendidas) }} / {{ ent(z.mesas) }}</td>
                <td class="num">{{ ent(z.personas) }}</td>
                <td class="num">{{ bs(z.recaudado) }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else class="muted">Sin mesas.</p>
        </section>
        <section class="card bloque">
          <h3>Por tipo de pase</h3>
          <table v-if="datos.porTipoPase.length" class="tabla mini">
            <thead><tr><th>Tipo</th><th class="num">Pases</th><th class="num">Usados</th></tr></thead>
            <tbody>
              <tr v-for="p in datos.porTipoPase" :key="p.tipo">
                <td>{{ p.tipo }}</td><td class="num">{{ ent(p.pases) }}</td><td class="num">{{ ent(p.usados) }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else class="muted">Aún no hay pases emitidos.</p>
        </section>
        <section class="card bloque">
          <h3>Por usuario (registró o validó)</h3>
          <table v-if="datos.porUsuario.length" class="tabla mini">
            <thead><tr><th>Usuario</th><th class="num">Registró</th><th class="num">Validó</th><th class="num">Rechazó</th><th class="num">Recaudado</th></tr></thead>
            <tbody>
              <tr v-for="u in datos.porUsuario" :key="u.id">
                <td>{{ u.nombre }}</td>
                <td class="num">{{ ent(u.registradas) }}</td>
                <td class="num">{{ ent(u.validadas) }}</td>
                <td class="num">{{ ent(u.rechazadas) }}</td>
                <td class="num">{{ bs(u.recaudado) }}</td>
              </tr>
            </tbody>
          </table>
          <p v-else class="muted">Nadie registró ni validó reservas todavía.</p>
        </section>
      </div>

      <section class="card bloque">
        <div class="fila-titulo">
          <h3>Reservas ({{ reservas.length }})</h3>
          <select v-model="filtroEstado" class="control chico">
            <option value="">Todos los estados</option>
            <option v-for="e in estados" :key="e.k" :value="e.k">{{ e.t }}</option>
          </select>
          <input v-model="busqueda" class="control chico busca" type="search" placeholder="Buscar cliente, C.I., mesa…" />
        </div>
        <div class="tabla-scroll">
          <table class="tabla">
            <thead>
              <tr>
                <th>Cliente</th><th>Mesa</th><th class="num">Personas</th><th class="num">Monto</th>
                <th>Estado</th><th>Atendió</th><th class="num">Ingresos</th><th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in reservas" :key="r.id">
                <td><strong>{{ r.cliente }}</strong><small class="muted bloque-txt">C.I. {{ r.ci }} · {{ r.celular }} · {{ r.codigo }}</small></td>
                <td>{{ r.mesa }}<small v-if="r.zona" class="muted bloque-txt">{{ r.zona }}<template v-if="r.tipoPase"> · {{ r.tipoPase }}</template></small></td>
                <td class="num">{{ r.personas ?? '—' }}</td>
                <td class="num">{{ bs(r.monto) }}</td>
                <td><span class="badge" :class="claseEstado(r.estado)">{{ ETIQUETAS[r.estado] || r.estado }}</span><small v-if="r.enOficina" class="muted bloque-txt">en oficina</small></td>
                <td>{{ r.atendio || '—' }}</td>
                <td class="num">{{ r.pases ? `${r.pasesUsados} / ${r.pases}` : '—' }}</td>
                <td class="acciones">
                  <button v-if="r.estado === 'CONFIRMADA'" type="button" class="btn btn-sm" @click="abierta = r.id">📲 Pases</button>
                </td>
              </tr>
              <tr v-if="!reservas.length"><td colspan="8" class="muted centro">Sin reservas con estos filtros.</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <ModalEntregaReserva v-if="abierta" :reserva-id="abierta" @cerrar="abierta = null" />
  </div>
</template>

<style scoped>
.reporte-evento { display: flex; flex-direction: column; gap: 0.9rem; }
.cabecera { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.75rem; flex-wrap: wrap; }
.cabecera h2 { margin: 0; font-size: 1.15rem; }
.cabecera p { margin: 0.15rem 0 0; font-size: 0.88rem; }
.botones { display: flex; gap: 0.4rem; flex-wrap: wrap; }

.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 0.75rem; }
.kpi { padding: 0.75rem 0.85rem; display: flex; flex-direction: column; gap: 0.1rem; }
.kpi span { font-size: 0.78rem; color: var(--muted); font-weight: 700; }
.kpi strong { font-size: 1.35rem; line-height: 1.15; font-variant-numeric: tabular-nums; }
.kpi small { font-size: 0.78rem; }
.kpi.ojo strong { color: var(--tramite); }

.graficos { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.75rem; }
.bloque { padding: 0.85rem; min-width: 0; }
.bloque h3 { margin: 0 0 0.6rem; font-size: 0.95rem; }
.mini th, .mini td { padding: 0.45rem 0.5rem; }
.num { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.bloque-txt { display: block; font-size: 0.78rem; }
.centro { text-align: center; }
.fila-titulo { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 0.5rem; }
.fila-titulo h3 { margin: 0; flex: 1 1 auto; }
.chico { padding: 0.35rem 0.5rem; font-size: 0.85rem; }
.busca { flex: 1 1 200px; min-width: 0; }

@media (max-width: 760px) {
  .graficos { grid-template-columns: 1fr; }
}
</style>
