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

const conCredenciales = computed(() => props.entrega.registraPersonas !== false);
const cobraExtra = computed(() => cupo.value && !cupo.value.dentroDelDerecho && Number(cupo.value.costoSiguiente) > 0);
const puedeSumar = computed(() => Boolean(cupo.value) && (cupo.value.dentroDelDerecho || cupo.value.admiteExtras));

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
const sinFoto = computed(() => props.entrega.personas.filter((p) => !p.tieneFoto && !p.sinDatos).length);

const mensaje = computed(() => {
  const e = props.entrega;
  const pagado = e.conComprobante || e.cobroEfectivo;
  const lineas = [
    `Hola ${props.titular.nombre || ''}`.trim() + ',',
    '',
    `Tu compra${props.actividad ? ` para *${props.actividad}*` : ''} quedó *confirmada*.`,
    `- Espacio: ${e.espacios.join(', ')}`,
    `- Total: ${bs(e.total)} (${pagado ? 'pago recibido' : 'pendiente de pago'})`,
    '- Te enviamos tu recibo en este chat.',
    '',
  ];
  if (conCredenciales.value && e.personas.length) {
    lineas.push(`Credenciales de acceso (${e.personas.length}):`);
    lineas.push(...e.personas.map((p) => `• ${p.nombre}: ${enlaceCredencial(p.codigo)}`));
    lineas.push('', 'Presenta tu credencial con el QR en el ingreso.');
  } else {
    lineas.push('Al llegar, da tu nombre o tu C.I. en la recepción y te ubicamos en tu lugar.');
  }
  lineas.push('¡Te esperamos!');
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
  for (const p of conCredenciales.value ? props.entrega.personas : []) {
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
          · <b :class="entrega.conComprobante || entrega.cobroEfectivo ? 'ok' : 'pend'">{{ entrega.cobroEfectivo ? 'cobrado en efectivo' : entrega.conComprobante ? 'pago con comprobante' : 'pago pendiente' }}</b></small>
      </div>
    </div>

    <section v-if="conCredenciales" class="bloque">
      <header><b>Personas y credenciales</b><small>{{ entrega.personas.length }} registrada(s)</small></header>
      <ul class="personas">
        <li v-for="p in entrega.personas" :key="p.id">
          <span class="avatar" :class="{ con: p.tieneFoto }">{{ p.tieneFoto ? '📷' : p.sinDatos ? '👥' : (p.nombre || '?').charAt(0) }}</span>
          <span class="quien"><b>{{ p.nombre }}</b><small>{{ p.sinDatos ? 'pase de acompañante sin datos' : `C.I. ${p.ci || '—'}${p.titular ? ' · titular' : p.extra ? ' · extra pagada' : ''}` }}</small></span>
          <label v-if="!p.sinDatos" class="btn btn-sm btn-fantasma" :class="{ deshabilitado: ocupado }" :title="p.tieneFoto ? 'Cambiar foto' : 'Subir foto'">
            <input type="file" accept="image/*" hidden :disabled="Boolean(ocupado)" @change="subirFoto(p, $event)" />
            {{ ocupado === `foto-${p.id}` ? '…' : p.tieneFoto ? 'Cambiar foto' : '📷 Foto' }}
          </label>
          <button type="button" class="btn btn-sm" :disabled="Boolean(ocupado)" @click="credencial(p)">{{ ocupado === `cred-${p.id}` ? '…' : '⬇ Credencial' }}</button>
        </li>
      </ul>
      <p v-if="sinFoto" class="nota">Si la credencial lleva foto, súbela antes de descargarla o enviarla ({{ sinFoto }} sin foto).</p>
      <div v-if="cupo && !entrega.anulada" class="cupo">
        <span>Incluye <b>{{ cupo.derecho }}</b> persona(s) · registradas <b>{{ cupo.registrados }}</b><template v-if="cupo.extras"> · {{ cupo.extras }} extra</template></span>
        <button v-if="!nueva.abierta && puedeSumar" type="button" class="btn btn-sm" @click="nueva.abierta = true">
          ＋ {{ cupo.dentroDelDerecho ? 'Agregar persona incluida' : `Persona extra · ${bs(cupo.costoSiguiente)}` }}
        </button>
        <small v-else-if="!nueva.abierta" class="lleno">Cupo completo · esta categoría no vende personas extra</small>
      </div>
      <div v-if="nueva.abierta" class="nueva">
        <p v-if="cobraExtra" class="aviso-extra">Pasa de las {{ cupo.derecho }} incluidas: se cobra <b>{{ bs(cupo.costoSiguiente) }}</b> y se genera su credencial.</p>
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
    <p v-else class="sin-credenciales">🧾 Esta actividad no usa credenciales: al cliente solo se le entrega el recibo. El día del evento se le ubica por su nombre o C.I. en <b>Recepción e ingreso › Llegadas</b>.</p>

    <section class="bloque">
      <header><b>Enviar al cliente</b><small>{{ titular.celular ? `📱 ${titular.celular}` : 'sin celular' }}</small></header>
      <div class="envio-principal">
        <a v-if="whatsapp" class="btn btn-whatsapp" :href="whatsapp" target="_blank" rel="noopener">
          <span class="ico">💬</span><span class="txt"><b>Enviar por WhatsApp</b><small>Abre el chat con el mensaje listo</small></span>
        </a>
        <button type="button" class="btn" :disabled="Boolean(ocupado)" @click="enviarAutomatico">
          <span class="ico">🤖</span><span class="txt"><b>{{ ocupado === 'auto' ? 'Enviando…' : 'Envío automático' }}</b><small>{{ conCredenciales ? 'Manda mensaje, recibo y credenciales' : 'Manda el mensaje y el recibo' }}</small></span>
        </button>
      </div>
      <div class="envio-secundario">
        <button type="button" class="btn btn-sm" :disabled="Boolean(ocupado)" @click="recibo">{{ ocupado === 'recibo' ? 'Descargando…' : '🧾 Descargar recibo' }}</button>
        <button v-if="puedeCompartirArchivos" type="button" class="btn btn-sm" :disabled="Boolean(ocupado)" @click="compartir">{{ ocupado === 'compartir' ? 'Preparando…' : conCredenciales ? '📤 Compartir archivos' : '📤 Compartir recibo' }}</button>
        <button type="button" class="btn btn-sm" @click="copiarMensaje">📋 Copiar mensaje</button>
      </div>
      <details class="vista-mensaje">
        <summary>Ver el mensaje</summary>
        <pre>{{ mensaje }}</pre>
      </details>
      <p class="nota">{{ conCredenciales
        ? '«Enviar por WhatsApp» lleva el texto con el enlace de cada credencial; el recibo se adjunta en el chat. «Envío automático» usa la instancia de WhatsApp configurada y manda todo solo.'
        : '«Enviar por WhatsApp» lleva el texto; el recibo se adjunta en el chat. «Envío automático» usa la instancia de WhatsApp configurada y manda el mensaje con el recibo.' }}</p>
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
.envio-principal { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 210px), 1fr)); gap: 0.5rem; }
.envio-principal .btn { justify-content: flex-start; gap: 0.6rem; padding: 0.6rem 0.75rem; white-space: normal; text-align: left; min-height: 56px; }
.envio-principal .ico { font-size: 1.35rem; flex: none; }
.envio-principal .txt { display: flex; flex-direction: column; min-width: 0; line-height: 1.25; }
.envio-principal .txt small { font-weight: 500; font-size: 0.76rem; opacity: 0.85; }
.envio-secundario { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.envio-secundario .btn { flex: 1 1 auto; white-space: normal; }
.btn-whatsapp { background: #1fa855; border-color: #1fa855; color: #fff; text-decoration: none; }
.sin-credenciales { margin: 0; padding: 0.6rem 0.75rem; border-radius: var(--radio-sm); background: var(--panel-2); font-size: 0.85rem; color: var(--muted); }
.sin-credenciales b { color: var(--text); }
.lleno { font-size: 0.78rem; }
.aviso-extra { grid-column: 1 / -1; margin: 0; font-size: 0.8rem; padding: 0.45rem 0.6rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--tramite) 12%, transparent); color: var(--text); }
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
