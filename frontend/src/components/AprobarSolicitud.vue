<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from './UiModal.vue';
import EntregaVenta from './EntregaVenta.vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { pedirTexto } from '../ui/alerta';
import { enlaceWhatsApp } from '../ui/tokenPublico';

const props = defineProps({
  solicitud: { type: Object, required: true },
  actividad: { type: String, default: '' },
});
const emit = defineEmits(['cerrar', 'aprobada', 'rechazada', 'actualizada']);

const tipos = ref([]);
const libres = ref([]);
const enviando = ref(false);
const error = ref('');
const entrega = ref(null);
const cargandoEntrega = ref(false);
const cambiandoEspacio = ref(false);
const subiendo = ref(false);
const comprobanteUrl = ref(props.solicitud.comprobanteUrl || null);

const s = props.solicitud;
const ciDelCliente = Boolean(String(s.ci || '').trim());
const form = reactive({
  ci: s.ci || '',
  tipoEntidadId: null,
  nit: '',
  puestoId: s.puestoId || null,
  pagadoEnOficina: false,
});

const yaVendida = computed(() => Boolean(s.inscripcionId));
const titular = computed(() => ({ nombre: s.nombreCompleto, celular: s.celular, empresa: s.empresa }));
const oficina = computed(() => s.modoPago === 'OFICINA');
const espacioElegido = computed(() => libres.value.find((p) => p.id === form.puestoId) || null);
const esImagen = computed(() => /\.(png|jpe?g|webp|gif)(\?|$)/i.test(comprobanteUrl.value || ''));
const listo = computed(() => form.ci.trim() && form.tipoEntidadId && form.puestoId);
const mensajeCorreccion = computed(() => `Hola ${String(s.nombreCompleto || '').split(' ')[0]}, te escribimos de ${props.actividad || 'la actividad'} por tu solicitud de ${s.categoriaNombre || 'espacio'}${s.puestoCodigo ? ` ${s.puestoCodigo}` : ''}. Revisamos tu comprobante y necesitamos que nos envíes uno correcto por aquí, por favor.`);

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
    };
    const r = await apiFetch(`/api/app/interesados-stand/${s.id}/aprobar-venta`, { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) {
      error.value = d.mensaje || 'No se pudo registrar la venta.';
      if (r.status === 409) { await cargarLibres(); cambiandoEspacio.value = true; }
      return;
    }
    entrega.value = d.entrega;
    toast(d.mensaje, 'ok', 3000);
    emit('aprobada', d.interesado);
  } finally {
    enviando.value = false;
  }
}

async function reemplazarComprobante(e) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (!archivo) return;
  subiendo.value = true;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/interesados-stand/${s.id}/comprobante`, { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo reemplazar el comprobante', 'error'); return; }
    comprobanteUrl.value = d.interesado?.comprobanteUrl || comprobanteUrl.value;
    toast(d.mensaje, 'ok', 2500);
    emit('actualizada', d.interesado);
  } finally {
    subiendo.value = false;
  }
}

async function rechazar() {
  const motivo = await pedirTexto('Escribe por qué se rechaza. El espacio vuelve a quedar libre y la solicitud pasa a Anulaciones con este motivo.', {
    titulo: 'Rechazar la solicitud', aceptar: 'Rechazar', marcador: 'Ej. El comprobante no corresponde al monto',
  });
  if (motivo == null) return;
  if (motivo.trim().length < 4) { toast('Escribe el motivo del rechazo', 'advertencia'); return; }
  enviando.value = true;
  try {
    const r = await apiFetch(`/api/app/interesados-stand/${s.id}/estado`, { method: 'PATCH', body: JSON.stringify({ estado: 'DESCARTADO', motivo: motivo.trim() }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo rechazar', 'error'); return; }
    toast(d.mensaje || 'Solicitud rechazada', 'ok');
    emit('rechazada', d.interesado);
  } finally {
    enviando.value = false;
  }
}

async function cargarLibres() {
  const r = await fetch(urlApi(s.edicionId ? `/api/publico/feria/plano?edicion=${s.edicionId}` : '/api/publico/feria/plano')).catch(() => null);
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
  const particular = String(s.rubro || '').toUpperCase() === 'PARTICULAR';
  const preferido = (particular && tipos.value.find((t) => /particular/i.test(t.nombre)))
    || tipos.value.find((t) => /emprend|empresa|privad|negocio/i.test(t.nombre)) || tipos.value[0];
  form.tipoEntidadId = preferido?.id ?? null;
  if (!form.puestoId) cambiandoEspacio.value = true;
});
</script>

<template>
  <UiModal :titulo="entrega ? 'Entrega al cliente' : 'Revisar y aprobar la solicitud'" ancho="680px" @cerrar="emit('cerrar')">
    <p v-if="cargandoEntrega" class="muted">Cargando…</p>

    <EntregaVenta v-else-if="entrega" :entrega="entrega" :titular="titular" :actividad="actividad" @actualizar="cargarEntrega" />

    <template v-else>
      <section class="datos">
        <header>
          <span class="color" :style="{ background: s.categoriaColor || 'var(--muted)' }"></span>
          <strong>Datos que envió el cliente</strong>
          <span class="candado" title="Estos datos no se pueden cambiar: son los que el cliente registró">🔒 Solo lectura</span>
        </header>
        <dl>
          <div><dt>Nombre</dt><dd>{{ s.nombreCompleto || '—' }}</dd></div>
          <div><dt>C.I.</dt><dd>{{ s.ci || '—' }}</dd></div>
          <div><dt>Celular</dt><dd>{{ s.celular || '—' }}</dd></div>
          <div v-if="s.empresa && s.empresa !== s.nombreCompleto"><dt>Negocio</dt><dd>{{ s.empresa }}</dd></div>
          <div v-if="s.rubro && s.rubro !== 'PARTICULAR'"><dt>Rubro</dt><dd>{{ s.rubro }}</dd></div>
          <div><dt>Pidió</dt><dd>{{ s.categoriaNombre || '—' }}{{ s.puestoCodigo ? ` · ${s.puestoCodigo}` : '' }}</dd></div>
          <div><dt>Pago</dt><dd>{{ oficina ? '🏢 Pagará en persona' : '📎 QR o transferencia' }}</dd></div>
        </dl>
      </section>

      <section class="comprobante">
        <div class="vista">
          <a v-if="comprobanteUrl" :href="urlApi(comprobanteUrl)" target="_blank" rel="noopener" title="Abrir el comprobante en grande">
            <img v-if="esImagen" :src="urlApi(comprobanteUrl)" alt="Comprobante enviado" />
            <span v-else class="pdf">📄 Ver comprobante</span>
          </a>
          <span v-else class="sin">{{ oficina ? 'Paga en persona: sin comprobante' : 'No adjuntó comprobante' }}</span>
        </div>
        <div class="correccion">
          <strong>¿Algo no cuadra?</strong>
          <small class="muted">Antes de rechazar, comunícate con el cliente. Si te envía el comprobante correcto, reemplázalo aquí y aprueba.</small>
          <div class="botones">
            <a v-if="s.celular" class="btn btn-sm wa" :href="enlaceWhatsApp(s.celular, mensajeCorreccion)" target="_blank" rel="noopener">💬 Pedir corrección</a>
            <label class="btn btn-sm" :class="{ deshabilitado: subiendo }">
              {{ subiendo ? 'Subiendo…' : comprobanteUrl ? '📎 Reemplazar comprobante' : '📎 Adjuntar comprobante' }}
              <input type="file" accept="image/*,application/pdf" hidden :disabled="subiendo" @change="reemplazarComprobante" />
            </label>
          </div>
        </div>
      </section>

      <section class="registro">
        <div class="campo">
          <span>Espacio que se le asigna</span>
          <template v-if="!cambiandoEspacio">
            <div class="fijo">
              <strong>{{ espacioElegido ? `${espacioElegido.categoria} · ${espacioElegido.codigo}` : `${s.categoriaNombre || ''} ${s.puestoCodigo || ''}` }}</strong>
              <small v-if="espacioElegido" class="muted">Bs {{ Number(espacioElegido.precio || 0).toLocaleString('es-BO') }}</small>
              <button type="button" class="enlace" @click="cambiandoEspacio = true">Asignar otro</button>
            </div>
          </template>
          <select v-else v-model.number="form.puestoId" class="control">
            <option :value="null" disabled>Elige un espacio libre</option>
            <option v-for="p in libres" :key="p.id" :value="p.id">{{ p.categoria }} · {{ p.codigo }} — Bs {{ Number(p.precio || 0).toLocaleString('es-BO') }}</option>
          </select>
        </div>
        <label class="campo"><span>Tipo de cliente</span>
          <select v-model.number="form.tipoEntidadId" class="control">
            <option v-for="t in tipos" :key="t.id" :value="t.id">{{ t.nombre }}</option>
          </select>
        </label>
        <label v-if="!ciDelCliente" class="campo"><span>C.I. <small class="muted">(el cliente no lo envió)</small></span>
          <input v-model="form.ci" class="control" maxlength="20" placeholder="Pídeselo y escríbelo aquí" />
        </label>
        <label class="campo"><span>NIT <small class="muted">(opcional, si pide factura)</small></span>
          <input v-model="form.nit" class="control" maxlength="30" placeholder="Solo si el cliente lo pidió" />
        </label>
      </section>

      <p class="muted chico">Los pases de sus acompañantes se crean solos y sus datos se completan después, en la entrega.</p>

      <label v-if="oficina" class="check-oficina">
        <input v-model="form.pagadoEnOficina" type="checkbox" />
        <span>Ya pagó en persona <small class="muted">— si no, la venta queda «pendiente de cobro» hasta adjuntar el comprobante.</small></span>
      </label>

      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </template>

    <template #pie>
      <template v-if="entrega">
        <button type="button" class="btn btn-primario" @click="emit('cerrar')">Listo</button>
      </template>
      <template v-else-if="!cargandoEntrega">
        <button type="button" class="btn btn-peligro rechazar" :disabled="enviando" @click="rechazar">Rechazar…</button>
        <span class="crecer"></span>
        <button type="button" class="btn btn-fantasma" @click="emit('cerrar')">Cerrar</button>
        <button type="button" class="btn btn-primario" :disabled="!listo || enviando" @click="aprobar">{{ enviando ? 'Registrando…' : '✓ Aprobar y registrar venta' }}</button>
      </template>
    </template>
  </UiModal>
</template>

<style scoped>
.datos { border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel-2); padding: 0.7rem 0.85rem; display: flex; flex-direction: column; gap: 0.5rem; }
.datos header { display: flex; align-items: center; gap: 0.5rem; }
.datos .color { width: 12px; height: 12px; border-radius: 3px; flex: none; }
.candado { margin-left: auto; font-size: 0.75rem; font-weight: 700; color: var(--muted); background: var(--bg); padding: 0.1rem 0.5rem; border-radius: 999px; }
.datos dl { margin: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 0.45rem 0.9rem; }
.datos dt { font-size: 0.72rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; font-weight: 700; }
.datos dd { margin: 0; font-weight: 600; overflow-wrap: anywhere; }
.comprobante { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 0.8rem; align-items: start; }
.vista { border: 1px dashed var(--border); border-radius: var(--radio-sm); min-height: 110px; display: grid; place-items: center; overflow: hidden; background: var(--bg); }
.vista img { display: block; width: 100%; max-height: 200px; object-fit: contain; }
.vista .pdf, .vista .sin { font-size: 0.82rem; color: var(--muted); text-align: center; padding: 0.5rem; }
.correccion { display: flex; flex-direction: column; gap: 0.35rem; }
.botones { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.wa { background: #1fa855; border-color: #1fa855; color: #fff; text-decoration: none; }
.deshabilitado { opacity: 0.5; pointer-events: none; }
.registro { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.6rem 0.75rem; }
.campo { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; font-weight: 600; }
.fijo { display: flex; align-items: baseline; gap: 0.45rem; flex-wrap: wrap; padding: 0.55rem 0.7rem; border: 1px solid var(--border); border-radius: var(--radio-sm); }
.enlace { border: 0; background: none; color: var(--acento); font: inherit; font-size: 0.8rem; font-weight: 700; cursor: pointer; margin-left: auto; }
.chico { font-size: 0.8rem; margin: 0; }
.check-oficina { display: flex; gap: 0.5rem; align-items: flex-start; font-size: 0.88rem; }
.error { margin: 0; padding: 0.55rem 0.75rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--danger) 12%, transparent); color: var(--danger); font-size: 0.88rem; }
.crecer { flex: 1; }
@media (max-width: 560px) {
  .registro, .comprobante { grid-template-columns: minmax(0, 1fr); }
  .rechazar { order: 3; }
}
</style>
