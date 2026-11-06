<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import UiModal from '../components/UiModal.vue';
import { apiFetch, jsonOError } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { alertaConAccion } from '../ui/alerta';
import ModalEntregaReserva from '../components/ModalEntregaReserva.vue';
import { useSolicitudesStore } from '../stores/solicitudes';
import { usePermisosStore } from '../stores/permisos';
import { useAuthStore } from '../stores/auth';

const Interesados = defineAsyncComponent(() => import('./Interesados.vue'));

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const permisos = usePermisosStore();
const solicitudes = useSolicitudesStore();

const veMesas = computed(() => auth.puedeEditarPlano || permisos.puedeVer('mesas'));
const veFerias = computed(() => auth.puedeEditarPlano || permisos.puedeVer('interesados'));
const pestana = ref(route.query.tab || (veMesas.value ? 'mesas' : 'ferias'));
const evento = ref(route.query.evento ? Number(route.query.evento) : null);
const ahora = ref(Date.now());
const aprobadas = ref([]);
const cargandoAprobadas = ref(false);
let reloj = null;

const eventos = computed(() => [...new Map(solicitudes.pendientes.map((r) => [r.eventoId, r.evento])).entries()]);
const filtrar = (lista) => lista.filter((r) => !evento.value || r.eventoId === evento.value);
const porValidar = computed(() => filtrar(solicitudes.porValidar));
const esperando = computed(() => filtrar(solicitudes.esperandoPago));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO')}`;

function espera(desde) {
  if (!desde) return '';
  const min = Math.max(0, Math.round((ahora.value - new Date(desde).getTime()) / 60000));
  if (min < 1) return 'recién';
  if (min < 60) return `hace ${min} min`;
  return `hace ${Math.floor(min / 60)} h ${min % 60} min`;
}

function urgencia(r) {
  const min = (ahora.value - new Date(r.comprobanteSubidoEn || r.creadoEn).getTime()) / 60000;
  return min > 15 ? 'alta' : min > 5 ? 'media' : 'baja';
}

function restante(r) {
  const s = Math.max(0, Math.round((new Date(r.bloqueoExpira).getTime() - ahora.value) / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

const esPdf = (u) => /\.pdf($|\?)/i.test(u || '');

const aprobar = reactive({ abierto: false, r: null, tipo: '', otro: '', personas: 1, enviando: false });
const entregaId = ref(null);
const tiposDe = (r) => [...new Set([...(r.tiposPase || []), r.zona, 'General'].filter(Boolean))];

function abrirAprobar(r) {
  const tipos = tiposDe(r);
  Object.assign(aprobar, { abierto: true, r, tipo: tipos[0], otro: '', personas: r.personas || r.capacidad, enviando: false });
}

async function confirmarAprobacion() {
  const tipo = aprobar.tipo === '__otro' ? aprobar.otro.trim() : aprobar.tipo;
  if (!aprobar.r.retiroFisico && !tipo) { toast('Escribe el tipo de pase', 'advertencia'); return; }
  aprobar.enviando = true;
  try {
    const res = await apiFetch(`/api/app/mesas/reservas/${aprobar.r.id}/confirmar`, {
      method: 'POST', body: JSON.stringify({ tipoPase: tipo, personas: Number(aprobar.personas) }),
    });
    const d = await res.json().catch(() => ({}));
    if (!res.ok || !d.ok) { toast(d.mensaje || 'No se pudo aprobar', 'error'); return; }
    toast(d.mensaje || 'Reserva aprobada', 'ok');
    aprobar.abierto = false;
    entregaId.value = aprobar.r.id;
    solicitudes.cargar();
    if (pestana.value === 'aprobadas') cargarAprobadas();
  } finally {
    aprobar.enviando = false;
  }
}

const rechazo = reactive({ abierto: false, r: null, motivo: '' });

async function enviarRechazo() {
  const res = await apiFetch(`/api/app/mesas/reservas/${rechazo.r.id}/rechazar`, { method: 'POST', body: JSON.stringify({ motivo: rechazo.motivo }) });
  const d = await res.json().catch(() => ({}));
  if (!res.ok || !d.ok) { toast(d.mensaje || 'No se pudo rechazar', 'error'); return; }
  toast(d.mensaje, 'ok');
  rechazo.abierto = false;
  solicitudes.cargar();
}

async function noPresentado(r) {
  const si = await alertaConAccion(`¿Liberar la mesa ${r.mesa} de ${r.nombre} ${r.apellidos} porque no se presentó? Podrá venderse otra vez.`, 'advertencia', () => {});
  if (!si) return;
  const res = await apiFetch(`/api/app/mesas/reservas/${r.id}/no-presentado`, { method: 'POST' });
  const d = await res.json().catch(() => ({}));
  toast(d.mensaje || 'No se pudo liberar', res.ok && d.ok ? 'ok' : 'error');
  if (res.ok && d.ok) cargarAprobadas();
}

async function cargarAprobadas() {
  cargandoAprobadas.value = true;
  try {
    const evs = await jsonOError(await apiFetch('/api/app/mesas/eventos'), 'No se pudieron cargar los eventos');
    const listas = await Promise.all(evs.filter((e) => !evento.value || e.id === evento.value).map(async (e) => {
      const rs = await jsonOError(await apiFetch(`/api/app/mesas/eventos/${e.id}/reservas?estado=CONFIRMADA`), '');
      return rs.map((r) => ({ ...r, evento: e.nombre, eventoId: e.id }));
    }));
    aprobadas.value = listas.flat().sort((a, b) => (b.validadoEn || '').localeCompare(a.validadoEn || ''));
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargandoAprobadas.value = false;
  }
}

const aqui = () => route.query.m === 'solicitudes';

watch(pestana, (p) => {
  if (aqui()) router.replace({ query: { ...route.query, tab: p } });
  if (p === 'aprobadas') cargarAprobadas();
});
watch(() => route.query.evento, (e) => { if (aqui()) evento.value = e ? Number(e) : null; });
watch(evento, (e) => {
  if (aqui()) router.replace({ query: { ...route.query, evento: e || undefined } });
  if (pestana.value === 'aprobadas') cargarAprobadas();
});

onMounted(() => {
  solicitudes.cargar();
  if (pestana.value === 'aprobadas') cargarAprobadas();
  reloj = setInterval(() => { ahora.value = Date.now(); }, 1000);
});
onUnmounted(() => clearInterval(reloj));
</script>

<template>
  <div class="solicitudes">
    <nav class="pestanas">
      <button v-if="veMesas" :class="{ on: pestana === 'mesas' }" @click="pestana = 'mesas'">
        Pagos por revisar <span v-if="solicitudes.porValidar.length" class="contador">{{ solicitudes.porValidar.length }}</span>
      </button>
      <button v-if="veMesas" :class="{ on: pestana === 'aprobadas' }" @click="pestana = 'aprobadas'">Aprobadas y pases</button>
      <button v-if="veFerias" :class="{ on: pestana === 'ferias' }" @click="pestana = 'ferias'">Participación en ferias</button>
      <span class="crecer"></span>
      <select v-if="veMesas && pestana !== 'ferias' && eventos.length > 1" v-model="evento" class="control filtro">
        <option :value="null">Todos los eventos</option>
        <option v-for="[id, nombre] in eventos" :key="id" :value="id">{{ nombre }}</option>
      </select>
    </nav>

    <template v-if="pestana === 'mesas'">
      <p v-if="!porValidar.length" class="vacio">✓ No hay pagos esperando revisión. Te avisaremos con un sonido cuando llegue uno.</p>
      <div class="rejilla">
        <article v-for="r in porValidar" :key="r.id" class="card solicitud" :class="`u-${urgencia(r)}`">
          <a v-if="r.comprobanteUrl" class="comprobante" :href="urlApi(r.comprobanteUrl)" target="_blank" rel="noopener" title="Ver comprobante completo">
            <span v-if="esPdf(r.comprobanteUrl)" class="pdf">📄 PDF</span>
            <img v-else :src="urlApi(r.comprobanteUrl)" alt="Comprobante" loading="lazy" />
          </a>
          <div class="datos">
            <div class="fila-cab">
              <strong>Mesa {{ r.mesa }}<small v-if="r.zona"> · {{ r.zona }}</small></strong>
              <span class="espera">{{ espera(r.comprobanteSubidoEn || r.creadoEn) }}</span>
            </div>
            <span class="muted chico">{{ r.evento }}</span>
            <span>{{ r.nombre }} {{ r.apellidos }} · C.I. {{ r.ci }}</span>
            <span class="muted chico">Cel. {{ r.celular }} · {{ r.personas }} persona{{ r.personas === 1 ? '' : 's' }} · {{ r.codigo }}</span>
            <strong class="monto">{{ bs(r.monto) }}</strong>
            <div class="acciones">
              <button class="btn btn-primario btn-sm" @click="abrirAprobar(r)">✓ Aprobar</button>
              <button class="btn btn-peligro btn-sm" @click="Object.assign(rechazo, { abierto: true, r, motivo: '' })">Rechazar</button>
            </div>
          </div>
        </article>
      </div>

      <section v-if="esperando.length" class="esperando">
        <h4>Esperando que el cliente pague ({{ esperando.length }})</h4>
        <ul>
          <li v-for="r in esperando" :key="r.id">
            <span>Mesa {{ r.mesa }} · {{ r.evento }}</span>
            <span>{{ r.nombre }} {{ r.apellidos }}</span>
            <span class="reloj">{{ restante(r) }}</span>
          </li>
        </ul>
      </section>
    </template>

    <template v-else-if="pestana === 'aprobadas'">
      <p v-if="cargandoAprobadas" class="vacio">Cargando…</p>
      <p v-else-if="!aprobadas.length" class="vacio">Aún no hay reservas aprobadas.</p>
      <table v-else class="tabla">
        <thead><tr><th>Mesa</th><th>Titular</th><th>Pases</th><th>Ingreso</th><th></th></tr></thead>
        <tbody>
          <tr v-for="r in aprobadas" :key="r.id">
            <td><strong>{{ r.mesa }}</strong><br /><span class="muted chico">{{ r.evento }}</span></td>
            <td>{{ r.nombre }} {{ r.apellidos }}<br /><span class="muted chico">{{ r.celular }}</span></td>
            <td>{{ r.personas }} · {{ r.tipoPase || '—' }}</td>
            <td>{{ r.retiroFisico ? (r.ingresoEn ? 'Retirado' : 'Retiro pendiente') : r.ingresados + ' / ' + r.personas }}</td>
            <td class="acciones">
              <button class="btn btn-sm btn-primario" title="Recibo, pases y WhatsApp" @click="entregaId = r.id">📲 Entregar</button>
              <button v-if="!r.ingresados" class="btn btn-sm btn-fantasma" title="Solo después de la hora límite" @click="noPresentado(r)">No vino</button>
            </td>
          </tr>
        </tbody>
      </table>
    </template>

    <Interesados v-else-if="pestana === 'ferias'" />

    <UiModal v-if="aprobar.abierto" :titulo="`Aprobar mesa ${aprobar.r?.mesa}`" ancho="560px"
             @cerrar="!aprobar.enviando && (aprobar.abierto = false)">
      <template v-if="aprobar.r">
        <p class="muted">{{ aprobar.r.nombre }} {{ aprobar.r.apellidos }} pagó <b>{{ bs(aprobar.r.monto) }}</b>. Confirma el abono en el banco antes de aprobar.</p>
        <p v-if="aprobar.r.retiroFisico">Al aprobar se habilita el QR para retirar el vale de consumo y {{ aprobar.r.personas }} manillas.</p>
        <template v-else>
        <label class="campo"><span>Tipo de pase</span>
          <select v-model="aprobar.tipo" class="control">
            <option v-for="t in tiposDe(aprobar.r)" :key="t" :value="t">{{ t }}</option>
            <option value="__otro">Otro…</option>
          </select>
        </label>
        <label v-if="aprobar.tipo === '__otro'" class="campo"><span>Nombre del pase</span><input v-model="aprobar.otro" class="control" maxlength="60" /></label>
        <label class="campo"><span>Personas en la mesa (máximo {{ aprobar.r.capacidad }})</span>
          <input v-model.number="aprobar.personas" type="number" min="1" :max="aprobar.r.capacidad" class="control" />
        </label>
        <p class="muted chico">Se generará un pase con QR por persona, con el diseño de credencial vinculado al evento. Al aprobar se abre el recibo y los pases para enviarlos al cliente.</p>
        </template>
      </template>
      <template #pie>
        <button class="btn btn-fantasma" :disabled="aprobar.enviando" @click="aprobar.abierto = false">Cancelar</button>
        <button class="btn btn-primario" :disabled="aprobar.enviando" @click="confirmarAprobacion">{{ aprobar.enviando ? 'Aprobando…' : 'Confirmar pago' }}</button>
      </template>
    </UiModal>

    <ModalEntregaReserva v-if="entregaId" :reserva-id="entregaId" @cerrar="entregaId = null" @cambio="pestana === 'aprobadas' && cargarAprobadas()" />

    <UiModal v-if="rechazo.abierto" :titulo="`Rechazar pago · mesa ${rechazo.r?.mesa}`" @cerrar="rechazo.abierto = false">
      <label class="campo"><span>Motivo (lo verá el cliente)</span>
        <textarea v-model="rechazo.motivo" class="control" rows="3" maxlength="300" placeholder="El monto no coincide con el precio de la mesa"></textarea>
      </label>
      <template #pie>
        <button class="btn btn-fantasma" @click="rechazo.abierto = false">Cancelar</button>
        <button class="btn btn-peligro" :disabled="!rechazo.motivo.trim()" @click="enviarRechazo">Rechazar y liberar la mesa</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.solicitudes { display: flex; flex-direction: column; gap: 1rem; }
.pestanas { display: flex; align-items: center; gap: 0.3rem; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.pestanas button { border: none; background: none; color: var(--muted); font: inherit; font-weight: 700; padding: 0.55rem 0.8rem; border-bottom: 2px solid transparent; cursor: pointer; display: inline-flex; gap: 0.4rem; align-items: center; }
.pestanas button.on { color: var(--text); border-bottom-color: var(--acento); }
.contador { background: var(--danger, #dc2626); color: #fff; border-radius: 999px; font-size: 0.72rem; padding: 0 0.45rem; min-width: 1.2rem; text-align: center; }
.crecer { flex: 1; }
.filtro { width: auto; }
.vacio { text-align: center; color: var(--muted); padding: 2rem; margin: 0; }
.rejilla { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 0.9rem; }
.solicitud { padding: 0.8rem; display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 0.8rem; border-left: 4px solid var(--ok); }
.solicitud.u-media { border-left-color: var(--tramite); }
.solicitud.u-alta { border-left-color: var(--danger); animation: latir 2s ease-in-out infinite; }
@keyframes latir { 50% { box-shadow: 0 0 0 3px color-mix(in srgb, var(--danger) 25%, transparent); } }
.comprobante { display: grid; place-items: center; border-radius: var(--radio-sm); overflow: hidden; background: var(--bg); border: 1px solid var(--border); height: 140px; text-decoration: none; }
.comprobante img { width: 100%; height: 100%; object-fit: cover; }
.pdf { font-weight: 700; color: var(--muted); }
.datos { display: flex; flex-direction: column; gap: 0.2rem; min-width: 0; font-size: 0.9rem; }
.fila-cab { display: flex; justify-content: space-between; gap: 0.5rem; }
.fila-cab small { color: var(--muted); font-weight: 400; }
.espera { font-size: 0.75rem; font-weight: 700; color: var(--muted); white-space: nowrap; }
.u-alta .espera { color: var(--danger); }
.monto { font-size: 1.05rem; }
.acciones { display: flex; gap: 0.4rem; flex-wrap: wrap; margin-top: 0.3rem; }
.esperando h4 { margin: 0 0 0.4rem; font-size: 0.85rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.esperando ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.3rem; }
.esperando li { display: grid; grid-template-columns: 1.2fr 1fr auto; gap: 0.6rem; padding: 0.45rem 0.7rem; border-radius: var(--radio-sm); background: var(--panel); border: 1px solid var(--border); font-size: 0.88rem; }
.reloj { font-family: ui-monospace, monospace; color: var(--tramite); font-weight: 700; }
.tabla { width: 100%; border-collapse: collapse; font-size: 0.9rem; }
.tabla th, .tabla td { text-align: left; padding: 0.5rem; border-bottom: 1px solid var(--border); vertical-align: middle; }
.tabla th { font-size: 0.75rem; color: var(--muted); text-transform: uppercase; }
.chico { font-size: 0.8rem; margin: 0; }
@media (max-width: 600px) { .solicitud { grid-template-columns: 1fr; } .comprobante { height: 160px; } }
</style>
