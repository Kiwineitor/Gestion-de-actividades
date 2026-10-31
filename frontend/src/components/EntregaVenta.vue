<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { descargarRecibo, descargarCredencialVirtual, nombreSeguro } from '../ui/descargas';
import { enlaceWhatsApp } from '../ui/tokenPublico';

const props = defineProps({
  entrega: { type: Object, required: true },
  titular: { type: Object, default: () => ({}) },
  actividad: { type: String, default: '' },
});
const emit = defineEmits(['actualizar']);

const router = useRouter();
const ocupado = ref('');
const cupo = ref(null);
const nueva = reactive({ abierta: false, nombre: '', ci: '', comprobante: null });

async function cargarCupo() {
  const r = await apiFetch(`/api/app/inscripciones/${props.entrega.inscripcionId}/responsables/cupo`);
  cupo.value = r.ok ? await r.json() : null;
}

onMounted(cargarCupo);
watch(() => props.entrega.personas.length, cargarCupo);

const cobraExtra = computed(() => cupo.value && !cupo.value.dentroDelDerecho && Number(cupo.value.costoSiguiente) > 0);

async function agregarPersona() {
  if (!nueva.nombre.trim() || !nueva.ci.trim()) { toast('Nombre y C.I. son obligatorios', 'error'); return; }
  if (cobraExtra.value && !nueva.comprobante) { toast(`Adjunta el comprobante del pago extra (Bs ${cupo.value.costoSiguiente})`, 'error'); return; }
  ocupado.value = 'nueva';
  try {
    const fd = new FormData();
    fd.append('nombre', nueva.nombre.trim().toUpperCase());
    fd.append('paterno', '');
    fd.append('materno', '');
    fd.append('ci', nueva.ci.trim());
    fd.append('celular', '');
    if (nueva.comprobante) fd.append('comprobante', nueva.comprobante);
    const r = await apiFetch(`/api/app/inscripciones/${props.entrega.inscripcionId}/responsables`, { method: 'POST', body: fd });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo agregar', 'error', 4000); return; }
    toast(d.cobrado ? `Persona agregada · cobro extra de Bs ${d.monto}` : 'Persona agregada', 'ok');
    Object.assign(nueva, { abierta: false, nombre: '', ci: '', comprobante: null });
    emit('actualizar');
  } finally {
    ocupado.value = '';
  }
}

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
const enlaceCredencial = (codigo) => `${location.origin}${router.resolve({ path: '/', query: { credencial: codigo } }).href}`;
const sinFoto = computed(() => props.entrega.personas.filter((p) => !p.tieneFoto).length);

const mensaje = computed(() => {
  const e = props.entrega;
  const lineas = [
    `Hola ${props.titular.nombre || ''} 👋`.trim(),
    '',
    `Tu solicitud${props.actividad ? ` para *${props.actividad}*` : ''} fue *aprobada* ✅`,
    `🏷️ Espacio: ${e.espacios.join(', ')}`,
    `💵 Total: ${bs(e.total)} — ${e.conComprobante ? 'pago recibido' : 'pendiente de pago'}`,
    '🧾 Te enviamos tu recibo en este chat.',
    '',
    `🪪 Credenciales de acceso (${e.personas.length}):`,
    ...e.personas.map((p) => `• ${p.nombre}: ${enlaceCredencial(p.codigo)}`),
    '',
    'Presenta tu credencial con el QR en el ingreso. ¡Te esperamos!',
  ];
  return lineas.join('\n');
});

const whatsapp = computed(() => enlaceWhatsApp(props.titular.celular, mensaje.value));

async function subirFoto(p, ev) {
  const archivo = ev.target.files?.[0];
  ev.target.value = '';
  if (!archivo) return;
  ocupado.value = `foto-${p.id}`;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/inscripciones/${props.entrega.inscripcionId}/responsables/${p.id}/foto`, { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo subir la foto', 'error'); return; }
    toast(`Foto de ${p.nombre} guardada`, 'ok', 1800);
    emit('actualizar');
  } finally {
    ocupado.value = '';
  }
}

async function recibo() {
  ocupado.value = 'recibo';
  try { await descargarRecibo(props.entrega.inscripcionId, props.titular.empresa || null); } finally { ocupado.value = ''; }
}

async function credencial(p) {
  ocupado.value = `cred-${p.id}`;
  try { await descargarCredencialVirtual(p.id, p.nombre); } finally { ocupado.value = ''; }
}

async function enviarAutomatico() {
  ocupado.value = 'auto';
  try {
    const r = await apiFetch('/api/app/credenciales/whatsapp', {
      method: 'POST',
      body: JSON.stringify({ inscripcionId: props.entrega.inscripcionId, responsables: props.entrega.personas.map((p) => p.id), incluirRecibo: true }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo enviar por WhatsApp', 'error', 5000); return; }
    toast(d.mensaje || 'Enviado por WhatsApp', 'ok');
  } finally {
    ocupado.value = '';
  }
}

async function archivosParaCompartir() {
  const archivos = [];
  const r = await apiFetch(`/api/app/inscripciones/${props.entrega.inscripcionId}/recibo`);
  if (r.ok) archivos.push(new File([await r.blob()], `recibo-${props.entrega.inscripcionId}.pdf`, { type: 'application/pdf' }));
  for (const p of props.entrega.personas) {
    const c = await apiFetch(`/api/app/credenciales/${p.id}/virtual`);
    if (c.ok) archivos.push(new File([await c.blob()], `credencial-${nombreSeguro(p.nombre)}.png`, { type: 'image/png' }));
  }
  return archivos;
}

async function compartir() {
  ocupado.value = 'compartir';
  try {
    const archivos = await archivosParaCompartir();
    if (archivos.length && navigator.canShare?.({ files: archivos })) {
      await navigator.share({ files: archivos, text: mensaje.value });
      return;
    }
    await copiarMensaje();
    toast('Este navegador no comparte archivos: copia el mensaje y descarga los archivos para adjuntarlos.', 'info', 5000);
  } catch (e) {
    if (e?.name !== 'AbortError') toast('No se pudo compartir', 'error');
  } finally {
    ocupado.value = '';
  }
}

async function copiarMensaje() {
  try {
    await navigator.clipboard.writeText(mensaje.value);
    toast('Mensaje copiado', 'ok', 1500);
  } catch {
    toast('No se pudo copiar el mensaje', 'error');
  }
}

const puedeCompartirArchivos = typeof navigator !== 'undefined' && typeof navigator.canShare === 'function';
</script>

<template>
  <div class="entrega">
    <div class="sello-venta" :class="{ anulada: entrega.anulada }">
      <span class="check">{{ entrega.anulada ? '✕' : '✓' }}</span>
      <div>
        <strong>{{ entrega.anulada ? 'Venta anulada' : 'Venta registrada' }} #{{ entrega.inscripcionId }}</strong>
        <small>{{ entrega.espacios.join(' · ') }} · {{ bs(entrega.total) }}
          · <b :class="entrega.conComprobante ? 'ok' : 'pend'">{{ entrega.conComprobante ? 'pago con comprobante' : 'pago pendiente' }}</b></small>
      </div>
    </div>

    <section class="bloque">
      <header><b>Credenciales</b><small>{{ entrega.personas.length }} persona(s) del puesto</small></header>
      <ul class="personas">
        <li v-for="p in entrega.personas" :key="p.id">
          <span class="avatar" :class="{ con: p.tieneFoto }">{{ p.tieneFoto ? '📷' : (p.nombre || '?').charAt(0) }}</span>
          <span class="quien"><b>{{ p.nombre }}</b><small>C.I. {{ p.ci || '—' }}{{ p.titular ? ' · titular' : '' }}</small></span>
          <label class="btn btn-sm btn-fantasma" :class="{ deshabilitado: ocupado }" :title="p.tieneFoto ? 'Cambiar foto' : 'Subir foto'">
            <input type="file" accept="image/*" hidden :disabled="Boolean(ocupado)" @change="subirFoto(p, $event)" />
            {{ ocupado === `foto-${p.id}` ? '…' : p.tieneFoto ? 'Cambiar foto' : '📷 Foto' }}
          </label>
          <button type="button" class="btn btn-sm" :disabled="Boolean(ocupado)" @click="credencial(p)">{{ ocupado === `cred-${p.id}` ? '…' : '⬇ Credencial' }}</button>
        </li>
      </ul>
      <p v-if="sinFoto" class="nota">Si la credencial lleva foto, súbela antes de descargarla o enviarla ({{ sinFoto }} sin foto).</p>
      <div v-if="cupo && !entrega.anulada" class="cupo">
        <span>Incluye <b>{{ cupo.derecho }}</b> · registradas <b>{{ cupo.registrados }}</b><template v-if="cupo.extras"> · {{ cupo.extras }} extra</template></span>
        <button v-if="!nueva.abierta" type="button" class="btn btn-sm" @click="nueva.abierta = true">
          ＋ {{ cupo.dentroDelDerecho ? 'Agregar persona' : `Persona extra (Bs ${cupo.costoSiguiente})` }}
        </button>
      </div>
      <div v-if="nueva.abierta" class="nueva">
        <input v-model="nueva.nombre" class="control" placeholder="Nombre completo" maxlength="150" />
        <input v-model="nueva.ci" class="control" placeholder="C.I." maxlength="20" />
        <label v-if="cobraExtra" class="btn btn-sm btn-fantasma archivo">
          <input type="file" accept="image/*,application/pdf" hidden @change="nueva.comprobante = $event.target.files?.[0] || null" />
          {{ nueva.comprobante ? '📎 ' + nueva.comprobante.name : '📎 Comprobante del pago extra' }}
        </label>
        <div class="nueva-acciones">
          <button type="button" class="btn btn-sm btn-fantasma" @click="nueva.abierta = false">Cancelar</button>
          <button type="button" class="btn btn-sm btn-primario" :disabled="ocupado === 'nueva'" @click="agregarPersona">{{ ocupado === 'nueva' ? 'Agregando…' : 'Agregar' }}</button>
        </div>
      </div>
    </section>

    <section class="bloque">
      <header><b>Enviar al expositor</b><small>{{ titular.celular || 'sin celular' }}</small></header>
      <div class="acciones">
        <button type="button" class="btn" :disabled="Boolean(ocupado)" @click="recibo">{{ ocupado === 'recibo' ? 'Descargando…' : '🧾 Recibo PDF' }}</button>
        <a v-if="whatsapp" class="btn btn-whatsapp" :href="whatsapp" target="_blank" rel="noopener">💬 Abrir WhatsApp con el mensaje</a>
        <button v-if="puedeCompartirArchivos" type="button" class="btn" :disabled="Boolean(ocupado)" @click="compartir">{{ ocupado === 'compartir' ? 'Preparando…' : '📤 Compartir recibo y credenciales' }}</button>
        <button type="button" class="btn" :disabled="Boolean(ocupado)" @click="enviarAutomatico">{{ ocupado === 'auto' ? 'Enviando…' : '🤖 Envío automático' }}</button>
      </div>
      <details class="vista-mensaje">
        <summary>Ver el mensaje que se envía</summary>
        <pre>{{ mensaje }}</pre>
        <button type="button" class="btn btn-sm btn-fantasma" @click="copiarMensaje">Copiar mensaje</button>
      </details>
      <p class="nota">«Abrir WhatsApp» lleva el texto con el enlace de cada credencial; el recibo y las imágenes se adjuntan arrastrándolos al chat. «Envío automático» usa la instancia de WhatsApp configurada y manda todo solo.</p>
    </section>
  </div>
</template>

<style scoped>
.entrega { display: flex; flex-direction: column; gap: 0.8rem; }
.sello-venta { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem 0.9rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--ok) 12%, transparent); border: 1px solid color-mix(in srgb, var(--ok) 40%, transparent); animation: aparecer 0.3s ease; }
.sello-venta.anulada { background: color-mix(in srgb, var(--danger) 10%, transparent); border-color: color-mix(in srgb, var(--danger) 40%, transparent); }
.check { width: 36px; height: 36px; flex: none; border-radius: 50%; display: grid; place-items: center; background: var(--ok); color: #fff; font-weight: 800; font-size: 1.1rem; }
.anulada .check { background: var(--danger); }
.sello-venta div { display: flex; flex-direction: column; min-width: 0; }
.sello-venta small { color: var(--muted); }
.ok { color: var(--ok); } .pend { color: var(--tramite); }
.bloque { border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.7rem 0.8rem; display: flex; flex-direction: column; gap: 0.55rem; }
.bloque header { display: flex; align-items: baseline; justify-content: space-between; gap: 0.5rem; }
.bloque header small { color: var(--muted); font-size: 0.8rem; }
.personas { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.4rem; }
.personas li { display: grid; grid-template-columns: auto minmax(0, 1fr) auto auto; gap: 0.5rem; align-items: center; }
.avatar { width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; background: var(--panel-2); color: var(--muted); font-weight: 700; }
.avatar.con { background: var(--acento-suave); }
.quien { display: flex; flex-direction: column; min-width: 0; }
.quien b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.9rem; }
.quien small { color: var(--muted); font-size: 0.78rem; }
.deshabilitado { opacity: 0.6; pointer-events: none; }
.acciones { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr)); gap: 0.5rem; }
.acciones .btn { justify-content: center; text-align: center; }
.btn-whatsapp { background: #1fa855; border-color: #1fa855; color: #fff; text-decoration: none; }
.vista-mensaje summary { cursor: pointer; font-size: 0.85rem; color: var(--acento); font-weight: 600; }
.vista-mensaje pre { white-space: pre-wrap; font: inherit; font-size: 0.82rem; background: var(--panel-2); border-radius: var(--radio-sm); padding: 0.6rem; margin: 0.4rem 0; max-height: 200px; overflow: auto; }
.nota { margin: 0; font-size: 0.8rem; color: var(--muted); }
.cupo { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; flex-wrap: wrap; font-size: 0.82rem; color: var(--muted); border-top: 1px dashed var(--border); padding-top: 0.5rem; }
.cupo b { color: var(--text); }
.nueva { display: grid; grid-template-columns: minmax(0, 1fr) 130px; gap: 0.45rem; }
.nueva .archivo { grid-column: 1 / -1; justify-content: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nueva-acciones { grid-column: 1 / -1; display: flex; justify-content: flex-end; gap: 0.4rem; }
@keyframes aparecer { from { opacity: 0; transform: scale(0.97); } to { opacity: 1; transform: none; } }
@media (max-width: 520px) {
  .personas li { grid-template-columns: auto minmax(0, 1fr); }
  .personas li .btn, .personas li label { grid-column: span 1; }
}
</style>
