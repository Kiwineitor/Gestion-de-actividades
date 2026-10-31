<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from './UiModal.vue';
import EntregaVenta from './EntregaVenta.vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';

const props = defineProps({
  solicitud: { type: Object, required: true },
  actividad: { type: String, default: '' },
});
const emit = defineEmits(['cerrar', 'aprobada']);

const tipos = ref([]);
const libres = ref([]);
const enviando = ref(false);
const error = ref('');
const entrega = ref(null);
const cargandoEntrega = ref(false);

const s = props.solicitud;
const cupo = Math.max(1, Number(s.personasPorPuesto) || 2);
const form = reactive({
  ci: s.ci || '',
  tipoEntidadId: null,
  nit: '',
  puestoId: s.puestoId || null,
  pagadoEnOficina: false,
  personas: [{ nombre: s.nombreCompleto || '', ci: s.ci || '', celular: s.celular || '', _ciPrevio: s.ci || '' }],
});

const yaVendida = computed(() => Boolean(s.inscripcionId));
const titular = computed(() => ({ nombre: s.nombreCompleto, celular: s.celular, empresa: s.empresa }));
const oficina = computed(() => s.modoPago === 'OFICINA');
const listo = computed(() => form.ci.trim() && form.tipoEntidadId && form.puestoId
  && form.personas.every((p) => !p.nombre.trim() || p.ci.trim()));

function sincronizarCi() {
  if (!form.personas[0].ci || form.personas[0].ci === form.personas[0]._ciPrevio) form.personas[0].ci = form.ci;
  form.personas[0]._ciPrevio = form.ci;
}

function agregarPersona() {
  if (form.personas.length < cupo) form.personas.push({ nombre: '', ci: '', celular: '' });
}

async function cargarEntrega() {
  cargandoEntrega.value = true;
  try {
    const r = await apiFetch(`/api/app/interesados-stand/${s.id}/entrega`);
    if (r.ok) entrega.value = await r.json();
  } finally {
    cargandoEntrega.value = false;
  }
}

async function aprobar() {
  if (!listo.value || enviando.value) return;
  enviando.value = true;
  error.value = '';
  try {
    const cuerpo = {
      puestoId: form.puestoId, ciRepresentante: form.ci.trim(), tipoEntidadId: form.tipoEntidadId, nit: form.nit.trim(),
      pagadoEnOficina: form.pagadoEnOficina,
      personas: form.personas.filter((p) => p.nombre.trim()).map((p) => ({ nombre: p.nombre.trim(), ci: p.ci.trim(), celular: p.celular.trim() })),
    };
    const r = await apiFetch(`/api/app/interesados-stand/${s.id}/aprobar-venta`, { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) {
      error.value = d.mensaje || 'No se pudo registrar la venta.';
      if (r.status === 409) cargarLibres();
      return;
    }
    entrega.value = d.entrega;
    toast(d.mensaje, 'ok', 3000);
    emit('aprobada', d.interesado);
  } finally {
    enviando.value = false;
  }
}

async function cargarLibres() {
  const r = await fetch(urlApi('/api/publico/feria/plano')).catch(() => null);
  if (!r?.ok) return;
  const d = await r.json().catch(() => null);
  libres.value = (d?.puestos || []).filter((p) => p.estado === 'L' || p.id === s.puestoId)
    .sort((a, b) => (a.categoriaId === s.categoriaId ? 0 : 1) - (b.categoriaId === s.categoriaId ? 0 : 1)
      || String(a.categoria).localeCompare(String(b.categoria)) || String(a.codigo).localeCompare(String(b.codigo), 'es', { numeric: true }));
}

onMounted(async () => {
  if (yaVendida.value) { cargarEntrega(); return; }
  const [rt] = await Promise.all([apiFetch('/api/app/catalogos/tipos-entidad'), cargarLibres()]);
  tipos.value = rt.ok ? await rt.json() : [];
  const preferido = tipos.value.find((t) => /emprend|empresa|privad|negocio/i.test(t.nombre)) || tipos.value[0];
  form.tipoEntidadId = preferido?.id ?? null;
});
</script>

<template>
  <UiModal :titulo="entrega ? 'Entrega al expositor' : 'Aprobar y registrar la venta'" ancho="640px" @cerrar="emit('cerrar')">
    <p v-if="cargandoEntrega" class="muted">Cargando…</p>

    <EntregaVenta v-else-if="entrega" :entrega="entrega" :titular="titular" :actividad="actividad" @actualizar="cargarEntrega" />

    <template v-else>
      <div class="resumen">
        <span class="color" :style="{ background: s.categoriaColor || 'var(--muted)' }"></span>
        <div>
          <b>{{ s.empresa }}</b>
          <small>{{ s.rubro }} · {{ s.nombreCompleto }} · {{ s.celular }}</small>
        </div>
        <span class="pago" :class="{ ok: s.comprobanteUrl }">{{ s.comprobanteUrl ? '📎 Con comprobante' : '🏢 Paga en oficina' }}</span>
      </div>

      <div class="rejilla">
        <label>Espacio asignado
          <select v-model.number="form.puestoId" class="control">
            <option :value="null" disabled>Elige un espacio libre</option>
            <option v-for="p in libres" :key="p.id" :value="p.id">{{ p.categoria }} · {{ p.codigo }} — Bs {{ Number(p.precio || 0).toLocaleString('es-BO') }}</option>
          </select>
        </label>
        <label>Tipo de expositor
          <select v-model.number="form.tipoEntidadId" class="control">
            <option v-for="t in tipos" :key="t.id" :value="t.id">{{ t.nombre }}</option>
          </select>
        </label>
        <label>C.I. de {{ s.nombreCompleto?.split(' ')[0] || 'quien solicitó' }}
          <input v-model="form.ci" class="control" maxlength="20" placeholder="Número de carnet" @input="sincronizarCi" />
        </label>
        <label><span>NIT <small class="muted">(opcional)</small></span>
          <input v-model="form.nit" class="control" maxlength="30" placeholder="Si emite factura" />
        </label>
      </div>

      <section class="personas">
        <header>
          <b>Personas del puesto</b>
          <small>Cada una recibe su credencial · incluye {{ cupo }}</small>
        </header>
        <div v-for="(p, n) in form.personas" :key="n" class="persona">
          <span class="num">{{ n + 1 }}</span>
          <input v-model="p.nombre" class="control" placeholder="Nombre completo" maxlength="150" />
          <input v-model="p.ci" class="control ci" placeholder="C.I." maxlength="20" />
          <button v-if="n > 0" type="button" class="btn btn-sm btn-fantasma" aria-label="Quitar" @click="form.personas.splice(n, 1)">✕</button>
          <span v-else class="hueco" aria-hidden="true"></span>
        </div>
        <button v-if="form.personas.length < cupo" type="button" class="btn btn-sm btn-fantasma agregar" @click="agregarPersona">＋ Agregar persona ({{ form.personas.length }} de {{ cupo }})</button>
        <p class="muted chico">Las personas adicionales se agregan luego desde Inscripciones, con su costo extra.</p>
      </section>

      <label v-if="oficina" class="check-oficina">
        <input v-model="form.pagadoEnOficina" type="checkbox" />
        <span>Ya pagó en oficina <small class="muted">— si no tiene comprobante, la venta queda «pendiente de cobro» hasta adjuntarlo.</small></span>
      </label>

      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </template>

    <template #pie>
      <template v-if="entrega">
        <button type="button" class="btn btn-primario" @click="emit('cerrar')">Listo</button>
      </template>
      <template v-else-if="!cargandoEntrega">
        <button type="button" class="btn btn-fantasma" @click="emit('cerrar')">Cancelar</button>
        <button type="button" class="btn btn-primario" :disabled="!listo || enviando" @click="aprobar">{{ enviando ? 'Registrando…' : '✓ Aprobar y registrar venta' }}</button>
      </template>
    </template>
  </UiModal>
</template>

<style scoped>
.resumen { display: flex; align-items: center; gap: 0.6rem; padding: 0.6rem 0.75rem; background: var(--panel-2); border-radius: var(--radio-sm); }
.resumen .color { width: 10px; align-self: stretch; border-radius: 4px; flex: none; }
.resumen div { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.resumen b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resumen small { color: var(--muted); font-size: 0.8rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pago { font-size: 0.78rem; font-weight: 700; color: var(--tramite); white-space: nowrap; }
.pago.ok { color: var(--ok); }
.rejilla { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.6rem 0.75rem; }
.rejilla label { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; font-weight: 600; }
.personas { display: flex; flex-direction: column; gap: 0.45rem; border-top: 1px solid var(--border); padding-top: 0.7rem; }
.personas header { display: flex; justify-content: space-between; align-items: baseline; gap: 0.5rem; }
.personas header small { color: var(--muted); font-size: 0.8rem; }
.persona { display: grid; grid-template-columns: auto minmax(0, 1fr) 130px 34px; gap: 0.45rem; align-items: center; }
.num { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; background: var(--acento-suave); color: var(--acento); font-weight: 800; font-size: 0.8rem; }
.agregar { align-self: flex-start; }
.chico { font-size: 0.78rem; margin: 0; }
.check-oficina { display: flex; gap: 0.5rem; align-items: flex-start; font-size: 0.88rem; }
.error { margin: 0; padding: 0.55rem 0.75rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--danger) 12%, transparent); color: var(--danger); font-size: 0.88rem; }
@media (max-width: 560px) {
  .rejilla { grid-template-columns: minmax(0, 1fr); }
  .persona { grid-template-columns: auto minmax(0, 1fr) 96px 34px; }
}
</style>
