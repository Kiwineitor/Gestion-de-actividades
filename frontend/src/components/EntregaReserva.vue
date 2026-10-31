<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { descargarArchivo, guardarBlob, nombreSeguro } from '../ui/descargas';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { confirmar } from '../ui/alerta';
import { url as urlArchivo } from '../config.js';

const props = defineProps({
  reservaId: { type: Number, required: true },
});
const emit = defineEmits(['cerrar', 'cambio']);

const router = useRouter();
const entrega = ref(null);
const error = ref('');
const ocupado = ref('');
const imagenes = ref({});
const nueva = reactive({ abierta: false, personas: 1, tipoPase: '' });
const persona = reactive({ numero: null, nombre: '', ci: '', fotoUrl: null, guardando: false });

function editarPersona(p) {
  Object.assign(persona, { numero: p.numero, nombre: p.nombre || '', ci: p.ci || '', fotoUrl: p.fotoUrl || null, guardando: false });
}

async function guardarPersona(quitarFoto = false) {
  if (persona.numero == null || persona.guardando) return;
  persona.guardando = true;
  try {
    const r = await apiFetch(`/api/app/mesas/reservas/${props.reservaId}/pases/${persona.numero}`, {
      method: 'PATCH', body: JSON.stringify({ nombre: persona.nombre, ci: persona.ci, quitarFoto }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudieron guardar los datos', 'error'); return; }
    toast(quitarFoto ? 'Foto quitada: el pase lleva el logo del local' : 'Datos del pase guardados', 'ok', 1800);
    if (quitarFoto) persona.fotoUrl = null;
    else persona.numero = null;
    await cargar();
  } finally {
    persona.guardando = false;
  }
}

async function subirFotoPase(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo || persona.numero == null) return;
  persona.guardando = true;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/mesas/reservas/${props.reservaId}/pases/${persona.numero}/foto`, { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo subir la foto', 'error'); return; }
    persona.fotoUrl = d.url || persona.fotoUrl;
    toast('Foto guardada', 'ok', 1500);
    await cargar();
  } finally {
    persona.guardando = false;
  }
}

const confirmada = computed(() => entrega.value?.estado === 'CONFIRMADA');
const pases = computed(() => entrega.value?.pases || []);
const usados = computed(() => pases.value.filter((p) => p.ingresoEn).length);

function liberarImagenes() {
  Object.values(imagenes.value).forEach((u) => URL.revokeObjectURL(u));
  imagenes.value = {};
}

async function cargarImagenes() {
  liberarImagenes();
  const nuevas = {};
  await Promise.all(pases.value.map(async (p) => {
    const r = await apiFetch(`/api/app/mesas/reservas/${props.reservaId}/pases/${p.numero}/imagen`);
    if (r.ok) nuevas[p.numero] = URL.createObjectURL(await r.blob());
  }));
  imagenes.value = nuevas;
}

async function cargar() {
  error.value = '';
  const r = await apiFetch(`/api/app/mesas/reservas/${props.reservaId}/entrega`);
  if (!r.ok) { error.value = r.status === 403 ? 'No tienes permiso para ver esta reserva.' : 'No se pudo cargar la reserva.'; return; }
  entrega.value = await r.json();
  nueva.personas = entrega.value.personas || 1;
  nueva.tipoPase = entrega.value.tipoPase || '';
  await cargarImagenes();
}

onMounted(cargar);
onUnmounted(liberarImagenes);
watch(() => props.reservaId, cargar);

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
const enlacePublico = computed(() => (entrega.value ? `${location.origin}${router.resolve({ path: '/', query: { reserva: entrega.value.codigo } }).href}` : ''));

const mensaje = computed(() => {
  const e = entrega.value;
  if (!e) return '';
  return [
    `Hola ${e.titular || ''} 👋`.trim(),
    '',
    `Tu reserva para *${e.evento}* está *confirmada* ✅`,
    e.fechaTexto ? `📅 ${e.fechaTexto}${e.local ? ` · ${e.local}` : ''}` : null,
    `🍸 Mesa *${e.mesa}*${e.zona ? ` (${e.zona})` : ''}`,
    `👥 Personas: *${e.personas}*${e.tipoPase ? ` · pase ${e.tipoPase}` : ''}`,
    `💵 Total: ${bs(e.monto)}`,
    `🔑 Código: *${e.codigo}*`,
    e.horaLimiteTexto ? `⏰ Llega hasta las ${e.horaLimiteTexto}` : null,
    '',
    '🧾 Te enviamos tu recibo y tus pases en este chat.',
    `🎟️ También puedes verlos aquí: ${enlacePublico.value}`,
    '',
    'Cada pase tiene su QR y permite un ingreso. ¡Te esperamos!',
  ].filter((l) => l !== null).join('\n');
});

const whatsapp = computed(() => enlaceWhatsApp(entrega.value?.celular, mensaje.value));

async function recibo() {
  ocupado.value = 'recibo';
  try {
    await descargarArchivo(`/api/app/mesas/reservas/${props.reservaId}/recibo`, `recibo-${entrega.value.codigo}.pdf`);
    toast('Recibo descargado', 'ok', 1500);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = '';
  }
}

async function bajarPase(p) {
  const url = imagenes.value[p.numero];
  if (!url) { toast('El pase todavía no está listo', 'error'); return; }
  const blob = await (await fetch(url)).blob();
  await guardarBlob(blob, `pase-${nombreSeguro(entrega.value.titular)}-${p.numero}.png`);
}

async function bajarTodos() {
  ocupado.value = 'todos';
  try {
    await descargarArchivo('/api/app/mesas/pases/pdf', `pases-${entrega.value.codigo}.pdf`, {
      method: 'POST', body: JSON.stringify({ reservas: [props.reservaId] }),
    });
    toast('Pases descargados', 'ok', 1500);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = '';
  }
}

async function regenerar() {
  const n = Number(nueva.personas);
  if (!(await confirmar(`¿Generar de nuevo ${n} pase${n === 1 ? '' : 's'}? Los que aún no ingresaron se vuelven a emitir con el mismo código.`, { titulo: 'Generar pases', aceptar: 'Sí, generar' }))) return;
  ocupado.value = 'regenerar';
  try {
    const r = await apiFetch(`/api/app/mesas/reservas/${props.reservaId}/pases`, {
      method: 'POST', body: JSON.stringify({ personas: n, tipoPase: nueva.tipoPase || null }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudieron generar los pases', 'error', 4500); return; }
    toast(d.mensaje || 'Pases generados', 'ok');
    nueva.abierta = false;
    await cargar();
    emit('cambio');
  } finally {
    ocupado.value = '';
  }
}

async function archivosParaCompartir() {
  const archivos = [];
  const r = await apiFetch(`/api/app/mesas/reservas/${props.reservaId}/recibo`);
  if (r.ok) archivos.push(new File([await r.blob()], `recibo-${entrega.value.codigo}.pdf`, { type: 'application/pdf' }));
  for (const p of pases.value) {
    const url = imagenes.value[p.numero];
    if (url) archivos.push(new File([await (await fetch(url)).blob()], `pase-${p.numero}.png`, { type: 'image/png' }));
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
    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="!entrega" class="muted">Cargando…</p>
    <template v-else>
      <div class="sello-venta" :class="{ pendiente: !confirmada }">
        <span class="check">{{ confirmada ? '✓' : '…' }}</span>
        <div>
          <strong>{{ confirmada ? 'Reserva confirmada' : `Reserva ${String(entrega.estado).toLowerCase().replace('_', ' ')}` }} · {{ entrega.codigo }}</strong>
          <small>{{ entrega.evento }} · Mesa {{ entrega.mesa }}{{ entrega.zona ? ` (${entrega.zona})` : '' }} · {{ bs(entrega.monto) }}
            · <b>{{ entrega.enOficina ? 'pagado en oficina' : entrega.conComprobante ? 'pago con comprobante' : 'sin comprobante' }}</b></small>
        </div>
      </div>

      <section class="bloque">
        <header><b>Titular</b><small>{{ entrega.validadoPor ? `validó ${entrega.validadoPor}` : '' }}</small></header>
        <p class="titular"><b>{{ entrega.titular }}</b> · C.I. {{ entrega.ci || '—' }} · 📱 {{ entrega.celular || '—' }}<template v-if="entrega.correo"> · {{ entrega.correo }}</template></p>
      </section>

      <section v-if="confirmada" class="bloque">
        <header>
          <b>Pases</b>
          <small>{{ pases.length }} pase(s){{ entrega.tipoPase ? ` · ${entrega.tipoPase}` : '' }}{{ usados ? ` · ${usados} ya ingresaron` : '' }}</small>
        </header>
        <ul class="pases">
          <li v-for="p in pases" :key="p.numero">
            <img v-if="imagenes[p.numero]" :src="imagenes[p.numero]" :alt="`Pase ${p.numero}`" />
            <span v-else class="sin-imagen">Generando…</span>
            <small>Pase {{ p.numero }}<template v-if="p.nombre"> · {{ p.nombre }}</template>{{ p.dentro ? ' · adentro' : p.ingresoEn ? ' · ya ingresó' : '' }}</small>
            <button type="button" class="btn btn-sm" :disabled="!imagenes[p.numero]" @click="bajarPase(p)">⬇ Descargar</button>
            <button type="button" class="btn btn-sm btn-fantasma" @click="editarPersona(p)">✏️ Datos</button>
          </li>
        </ul>
        <div v-if="persona.numero != null" class="persona-pase">
          <strong>Pase {{ persona.numero }}: datos de la persona</strong>
          <p class="muted chico">Opcional. Si no los cargas, el pase lleva {{ persona.numero === 1 ? 'el nombre del titular' : 'el nombre del local' }} y el logo del local.</p>
          <div class="fila-persona">
            <label class="campo"><span>Nombre completo</span><input v-model="persona.nombre" class="control" maxlength="160" /></label>
            <label class="campo"><span>C.I.</span><input v-model="persona.ci" class="control" maxlength="20" /></label>
          </div>
          <div class="fila-acciones">
            <img v-if="persona.fotoUrl" :src="urlArchivo(persona.fotoUrl)" alt="Foto" class="mini-foto" />
            <label class="btn btn-sm">{{ persona.fotoUrl ? 'Cambiar foto' : '📷 Subir foto' }}
              <input type="file" accept="image/png,image/jpeg" capture="user" class="oculto" :disabled="persona.guardando" @change="subirFotoPase" />
            </label>
            <button v-if="persona.fotoUrl" type="button" class="btn btn-sm btn-fantasma" :disabled="persona.guardando" @click="guardarPersona(true)">Quitar foto</button>
            <span class="crecer"></span>
            <button type="button" class="btn btn-sm btn-fantasma" @click="persona.numero = null">Cancelar</button>
            <button type="button" class="btn btn-sm btn-primario" :disabled="persona.guardando" @click="guardarPersona()">{{ persona.guardando ? 'Guardando…' : 'Guardar' }}</button>
          </div>
        </div>
        <div class="fila-acciones">
          <button type="button" class="btn btn-sm" :disabled="Boolean(ocupado) || !pases.length" @click="bajarTodos">{{ ocupado === 'todos' ? 'Preparando…' : '⬇ Todos los pases (PDF)' }}</button>
          <button v-if="!nueva.abierta" type="button" class="btn btn-sm btn-fantasma" @click="nueva.abierta = true">🔄 Generar de nuevo</button>
        </div>
        <div v-if="nueva.abierta" class="nueva">
          <label class="campo"><span>Cantidad de pases</span>
            <input v-model.number="nueva.personas" type="number" class="control" :min="Math.max(1, usados)" :max="entrega.capacidad" />
          </label>
          <label class="campo"><span>Tipo de pase</span>
            <select v-if="entrega.tiposPase?.length" v-model="nueva.tipoPase" class="control">
              <option value="">{{ entrega.zona || 'General' }}</option>
              <option v-for="t in entrega.tiposPase" :key="t" :value="t">{{ t }}</option>
            </select>
            <input v-else v-model="nueva.tipoPase" class="control" maxlength="60" :placeholder="entrega.zona || 'General'" />
          </label>
          <p class="nota">La mesa admite hasta {{ entrega.capacidad }} persona(s).</p>
          <div class="nueva-acciones">
            <button type="button" class="btn btn-sm btn-fantasma" @click="nueva.abierta = false">Cancelar</button>
            <button type="button" class="btn btn-sm btn-primario" :disabled="ocupado === 'regenerar'" @click="regenerar">{{ ocupado === 'regenerar' ? 'Generando…' : 'Generar' }}</button>
          </div>
        </div>
      </section>

      <section class="bloque">
        <header><b>Enviar al cliente</b><small>{{ entrega.celular || 'sin celular' }}</small></header>
        <div class="acciones">
          <button type="button" class="btn" :disabled="Boolean(ocupado)" @click="recibo">{{ ocupado === 'recibo' ? 'Descargando…' : '🧾 Recibo PDF' }}</button>
          <a v-if="whatsapp && confirmada" class="btn btn-whatsapp" :href="whatsapp" target="_blank" rel="noopener">💬 Abrir WhatsApp con el mensaje</a>
          <button v-if="puedeCompartirArchivos && confirmada" type="button" class="btn" :disabled="Boolean(ocupado)" @click="compartir">{{ ocupado === 'compartir' ? 'Preparando…' : '📤 Compartir recibo y pases' }}</button>
        </div>
        <details v-if="confirmada" class="vista-mensaje">
          <summary>Ver el mensaje que se envía</summary>
          <pre>{{ mensaje }}</pre>
          <button type="button" class="btn btn-sm btn-fantasma" @click="copiarMensaje">Copiar mensaje</button>
        </details>
        <p class="nota">«Abrir WhatsApp» lleva el texto con el enlace de la reserva; el recibo y los pases se adjuntan arrastrándolos al chat o con «Compartir».</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.entrega { display: flex; flex-direction: column; gap: 0.8rem; }
.error { color: var(--danger); margin: 0; }
.muted { color: var(--muted); margin: 0; }
.sello-venta { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem 0.9rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--ok) 12%, transparent); border: 1px solid color-mix(in srgb, var(--ok) 40%, transparent); }
.sello-venta.pendiente { background: color-mix(in srgb, var(--tramite) 12%, transparent); border-color: color-mix(in srgb, var(--tramite) 40%, transparent); }
.check { width: 36px; height: 36px; flex: none; border-radius: 50%; display: grid; place-items: center; background: var(--ok); color: #fff; font-weight: 800; font-size: 1.1rem; }
.pendiente .check { background: var(--tramite); }
.sello-venta div { display: flex; flex-direction: column; min-width: 0; }
.sello-venta small { color: var(--muted); }
.bloque { border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.7rem 0.8rem; display: flex; flex-direction: column; gap: 0.55rem; }
.bloque header { display: flex; align-items: baseline; justify-content: space-between; gap: 0.5rem; }
.bloque header small { color: var(--muted); font-size: 0.8rem; }
.titular { margin: 0; font-size: 0.88rem; overflow-wrap: anywhere; }
.pases { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 0.6rem; }
.pases li { display: flex; flex-direction: column; gap: 0.3rem; align-items: stretch; text-align: center; }
.pases img { width: 100%; border-radius: var(--radio-sm); border: 1px solid var(--border); background: var(--panel-2); }
.pases small { color: var(--muted); font-size: 0.78rem; }
.persona-pase { display: grid; gap: 0.45rem; margin-top: 0.7rem; padding: 0.7rem; border: 1px solid var(--acento); border-radius: var(--radio-sm); background: var(--acento-suave); }
.persona-pase .chico { margin: 0; font-size: 0.8rem; }
.fila-persona { display: grid; grid-template-columns: 2fr 1fr; gap: 0.5rem; }
.persona-pase .campo { display: grid; gap: 0.2rem; font-size: 0.82rem; }
.mini-foto { width: 42px; height: 42px; object-fit: cover; border-radius: 8px; border: 1px solid var(--border); }
.crecer { flex: 1; }
.oculto { display: none; }
@media (max-width: 520px) { .fila-persona { grid-template-columns: 1fr; } }
.sin-imagen { display: grid; place-items: center; aspect-ratio: 3 / 4; border-radius: var(--radio-sm); background: var(--panel-2); color: var(--muted); font-size: 0.8rem; }
.fila-acciones { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.acciones { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr)); gap: 0.5rem; }
.acciones .btn { justify-content: center; text-align: center; }
.btn-whatsapp { background: #1fa855; border-color: #1fa855; color: #fff; text-decoration: none; }
.vista-mensaje summary { cursor: pointer; font-size: 0.85rem; color: var(--acento); font-weight: 600; }
.vista-mensaje pre { white-space: pre-wrap; font: inherit; font-size: 0.82rem; background: var(--panel-2); border-radius: var(--radio-sm); padding: 0.6rem; margin: 0.4rem 0; max-height: 200px; overflow: auto; }
.nota { margin: 0; font-size: 0.8rem; color: var(--muted); }
.nueva { display: grid; grid-template-columns: 1fr 1fr; gap: 0.45rem; border-top: 1px dashed var(--border); padding-top: 0.5rem; }
.nueva .nota, .nueva-acciones { grid-column: 1 / -1; }
.nueva-acciones { display: flex; justify-content: flex-end; gap: 0.4rem; }
.campo { display: flex; flex-direction: column; gap: 0.2rem; font-size: 0.82rem; color: var(--muted); }
@media (max-width: 520px) {
  .nueva { grid-template-columns: 1fr; }
}
</style>
