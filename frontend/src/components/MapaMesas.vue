<script setup>
import { ref, reactive, computed, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import PlanoMesas from './PlanoMesas.vue';
import UiModal from './UiModal.vue';
import ModalEntregaReserva from './ModalEntregaReserva.vue';
import CampoCelular from './CampoCelular.vue';
import { apiFetch, jsonOError } from '../api';
import { escucharTopicPublico } from '../ws';
import { toast } from '../ui/toast';
import { LEYENDA_MESAS } from '../ui/imagenPlano';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import { aModulo } from '../modulos';

const props = defineProps({
  eventoId: { type: [Number, String], required: true },
});

const auth = useAuthStore();
const permisos = usePermisosStore();
const router = useRouter();

const detalle = ref(null);
const cargando = ref(true);
const selId = ref(null);
const entregaId = ref(null);
const puedeVender = computed(() => auth.puedeEditarPlano || permisos.puedeVer('mesas') || permisos.puedeVer('mapa'));

const mesas = computed(() => detalle.value?.mesas || []);
const sel = computed(() => mesas.value.find((m) => m.id === selId.value) || null);
const aspecto = computed(() => (detalle.value?.planoAncho && detalle.value?.planoAlto ? detalle.value.planoAlto / detalle.value.planoAncho : 0.62));
const tiposPase = computed(() => (detalle.value?.tiposPase || []).filter(Boolean));
const conteo = computed(() => {
  const c = { L: 0, R: 0, O: 0 };
  for (const m of mesas.value) {
    if (m.estado === 'L') c.L += 1;
    else if (m.estado === 'O') c.O += 1;
    else if (m.estado === 'T' || m.estado === 'R') c.R += 1;
  }
  return c;
});

const vacio = () => ({ nombre: '', apellidos: '', ci: '', celular: '', correo: '', personas: 1, tipoPase: '', enviando: false });
const venta = reactive(vacio());

let cerrarCanal = null;
let pendiente = null;

async function cargar(silencioso = false) {
  if (!silencioso) cargando.value = true;
  try {
    detalle.value = await jsonOError(await apiFetch(`/api/app/mesas/eventos/${props.eventoId}`), 'No se pudo cargar el evento');
  } catch (e) {
    if (!silencioso) toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function programarRecarga() {
  clearTimeout(pendiente);
  pendiente = setTimeout(() => cargar(true), 400);
}

function escuchar() {
  cerrarCanal?.();
  cerrarCanal = escucharTopicPublico(`/topic/publico/mesas/${props.eventoId}`, { onMensaje: programarRecarga });
}

watch(() => props.eventoId, () => {
  selId.value = null;
  cargar();
  escuchar();
}, { immediate: true });

onUnmounted(() => {
  cerrarCanal?.();
  clearTimeout(pendiente);
});

function elegir(m) {
  selId.value = m.id;
  Object.assign(venta, vacio(), { personas: m.capacidad || 1, tipoPase: tiposPase.value[0] || m.zona || '' });
}

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO')}`;
const hora = (s) => (s ? s.replace('T', ' ').slice(11, 16) : '');

const ESTADO = {
  L: 'Libre', T: 'Apartada por un cliente (esperando su comprobante)', R: 'Pago en revisión', O: 'Vendida', X: 'Fuera de venta',
};

async function vender() {
  const m = sel.value;
  if (!m || venta.enviando) return;
  if (!venta.nombre.trim() || !venta.apellidos.trim() || !venta.ci.trim() || !venta.celular.trim()) {
    toast('Completa nombre, apellidos, carnet y celular del cliente', 'advertencia');
    return;
  }
  venta.enviando = true;
  try {
    const r = await apiFetch(`/api/app/mesas/eventos/${props.eventoId}/mesas/${m.id}/venta`, {
      method: 'POST',
      body: JSON.stringify({
        nombre: venta.nombre.trim(), apellidos: venta.apellidos.trim(), ci: venta.ci.trim(), celular: venta.celular,
        correo: venta.correo.trim() || null, personas: Number(venta.personas) || null, tipoPase: venta.tipoPase || null,
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo registrar la venta', 'error'); await cargar(true); return; }
    toast(d.mensaje || `Mesa ${m.codigo} vendida`, 'ok');
    selId.value = null;
    entregaId.value = d.reservaId ?? d.datos?.reservaId ?? null;
    await cargar(true);
  } finally {
    venta.enviando = false;
  }
}

function abrirEntrega(m) {
  if (!m.reservaId) { toast('No se encontró la reserva de esta mesa', 'advertencia'); return; }
  selId.value = null;
  entregaId.value = m.reservaId;
}

function irASolicitudes() {
  router.push(aModulo('solicitudes', { tab: 'mesas', evento: props.eventoId }));
}
</script>

<template>
  <div class="mapa-mesas">
    <div class="leyenda-mesas">
      <span v-for="l in LEYENDA_MESAS" :key="l.texto" class="chip-mesa"><i :style="{ background: l.color }"></i>{{ l.texto }}</span>
      <span class="resumen muted">{{ conteo.L }} libres · {{ conteo.R }} en proceso · {{ conteo.O }} vendidas</span>
    </div>
    <p v-if="cargando" class="vacio">Cargando el plano…</p>
    <p v-else-if="!mesas.length" class="vacio">Este evento todavía no tiene mesas. Ármalo en el editor del plano.</p>
    <div v-else class="contenedor" :style="{ '--aspecto': aspecto }">
      <div class="lienzo">
        <PlanoMesas :mesas="mesas" :elementos="detalle?.elementos || []" :plano-url="detalle?.planoUrl" :aspecto="aspecto"
                    gestion :seleccionada="selId" @elegir="elegir" @vacio="selId = null" />
      </div>
    </div>

    <UiModal v-if="sel" :titulo="`Mesa ${sel.codigo}${sel.zona ? ` · ${sel.zona}` : ''}`" ancho="520px" @cerrar="selId = null">
      <dl class="datos">
        <div><dt>Estado</dt><dd>{{ ESTADO[sel.estado] || sel.estado }}</dd></div>
        <div><dt>Capacidad</dt><dd>{{ sel.capacidad }} personas</dd></div>
        <div><dt>Precio</dt><dd>{{ bs(sel.precio) }}</dd></div>
        <div v-if="sel.reservadoPor"><dt>Titular</dt><dd>{{ sel.reservadoPor }}</dd></div>
        <div v-if="sel.reservaCodigo"><dt>Código</dt><dd class="mono">{{ sel.reservaCodigo }}</dd></div>
      </dl>
      <p v-if="sel.beneficios" class="muted chico">Incluye: {{ sel.beneficios }}</p>

      <form v-if="sel.estado === 'L' && puedeVender" class="form-venta" @submit.prevent="vender">
        <h3>Registrar venta en oficina</h3>
        <p class="muted chico">El cliente pagó aquí: la mesa queda vendida al instante y se generan sus pases con el diseño del evento.</p>
        <div class="fila">
          <label class="campo"><span>Nombre</span><input v-model="venta.nombre" class="control" autocomplete="off" required /></label>
          <label class="campo"><span>Apellidos</span><input v-model="venta.apellidos" class="control" autocomplete="off" required /></label>
        </div>
        <div class="fila">
          <label class="campo"><span>Carnet (C.I.)</span><input v-model="venta.ci" class="control" autocomplete="off" required /></label>
          <label class="campo"><span>Celular (WhatsApp)</span><CampoCelular v-model="venta.celular" /></label>
        </div>
        <label class="campo"><span>Correo (opcional)</span><input v-model="venta.correo" type="email" class="control" autocomplete="off" /></label>
        <div class="fila">
          <label class="campo"><span>Personas (pases)</span>
            <input v-model.number="venta.personas" type="number" min="1" :max="sel.capacidad" class="control" />
          </label>
          <label class="campo"><span>Tipo de pase</span>
            <select v-if="tiposPase.length" v-model="venta.tipoPase" class="control">
              <option v-for="t in tiposPase" :key="t" :value="t">{{ t }}</option>
            </select>
            <input v-else v-model="venta.tipoPase" class="control" :placeholder="sel.zona || 'General'" />
          </label>
        </div>
      </form>
      <p v-else-if="sel.estado === 'L'" class="muted">Libre. Tu usuario no puede registrar ventas de mesas.</p>
      <p v-else-if="sel.estado === 'T'" class="muted">Un cliente la está pagando{{ sel.bloqueoExpira ? ` (hasta las ${hora(sel.bloqueoExpira)})` : '' }}. Si no adjunta su comprobante a tiempo, se libera sola.</p>
      <p v-else-if="sel.estado === 'R'" class="muted">El cliente ya adjuntó su comprobante: valídalo en Solicitudes para generar sus pases.</p>
      <p v-else-if="sel.estado === 'X'" class="muted">Esta mesa está fuera de venta. Se vuelve a habilitar en el editor del plano.</p>

      <template #pie>
        <button type="button" class="btn btn-fantasma" @click="selId = null">Cerrar</button>
        <button v-if="sel.estado === 'L' && puedeVender" type="button" class="btn btn-primario" :disabled="venta.enviando" @click="vender">
          {{ venta.enviando ? 'Registrando…' : `Vender · ${bs(sel.precio)}` }}
        </button>
        <button v-else-if="sel.estado === 'O' && sel.reservaId" type="button" class="btn btn-primario" @click="abrirEntrega(sel)">📲 Recibo y pases</button>
        <button v-else-if="sel.estado === 'R'" type="button" class="btn btn-primario" @click="irASolicitudes">Ir a validar</button>
      </template>
    </UiModal>

    <ModalEntregaReserva v-if="entregaId" :reserva-id="entregaId" @cerrar="entregaId = null" />
  </div>
</template>

<style scoped>
.mapa-mesas { flex: 1; display: flex; flex-direction: column; min-height: 0; padding: 0 1rem 1rem; gap: 0.6rem; }
.leyenda-mesas { display: flex; flex-wrap: wrap; gap: 0.45rem; align-items: center; }
.chip-mesa { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.8rem; font-weight: 600; }
.chip-mesa i { width: 0.75rem; height: 0.75rem; border-radius: 50%; display: inline-block; }
.resumen { font-size: 0.82rem; margin-left: auto; }
.contenedor { flex: 1; min-height: 0; display: flex; justify-content: center; align-items: flex-start; overflow: auto; }
.lienzo { width: min(100%, calc((100dvh - 190px) / var(--aspecto))); min-width: 280px; }
.vacio { text-align: center; color: var(--muted); padding: 2rem 1rem; }
.datos { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 0.5rem; margin: 0 0 0.6rem; }
.datos dt { font-size: 0.75rem; color: var(--muted); }
.datos dd { margin: 0; font-weight: 700; }
.mono { font-family: ui-monospace, monospace; font-size: 0.85rem; }
.form-venta { display: grid; gap: 0.55rem; border-top: 1px solid var(--border); padding-top: 0.7rem; }
.form-venta h3 { margin: 0; font-size: 1rem; }
.fila { display: grid; grid-template-columns: 1fr 1fr; gap: 0.55rem; }
.campo { display: grid; gap: 0.2rem; font-size: 0.85rem; }
.campo > span { color: var(--muted); font-weight: 600; }
.muted { color: var(--muted); }
.chico { font-size: 0.82rem; margin: 0; }
@media (max-width: 560px) { .fila { grid-template-columns: 1fr; } .resumen { margin-left: 0; } }
</style>
