<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import UiModal from './UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { enlaceWhatsApp } from '../ui/tokenPublico';

const props = defineProps({
  compra: { type: Object, required: true },
  actividad: { type: String, default: '' },
});
const emit = defineEmits(['cerrar']);

const imagenes = ref([]);
const ocupado = ref('');
const cantidad = computed(() => Number(props.compra.cantidad) || imagenes.value.length || 1);
const enlace = computed(() => `${window.location.origin}/?compra=${props.compra.codigo}`);
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;

const mensaje = computed(() => [
  `Hola ${String(props.compra.nombre || '').split(' ')[0]},`,
  '',
  `Tu compra para *${props.actividad || 'la actividad'}* fue *aprobada*.`,
  `- ${cantidad.value === 1 ? '1 entrada' : `${cantidad.value} entradas`} · ${bs(props.compra.total)}`,
  `- Código de compra: ${props.compra.codigo}`,
  '',
  'Descarga tus entradas con QR aquí:',
  enlace.value,
  '',
  'Muestra el QR de cada entrada en el ingreso: cada QR vale por una persona.',
  '¡Te esperamos!',
].join('\n'));

const alPrincipal = computed(() => enlaceWhatsApp(props.compra.celular, mensaje.value));
const alExtra = computed(() => (props.compra.celularExtra ? enlaceWhatsApp(props.compra.celularExtra, mensaje.value) : null));

async function cargarImagenes() {
  const lista = [];
  for (let n = 1; n <= cantidad.value; n++) {
    const r = await apiFetch(`/api/app/entradas/compra/${props.compra.codigo}/pase/${n}.png`);
    if (!r.ok) break;
    lista.push({ n, url: URL.createObjectURL(await r.blob()) });
  }
  imagenes.value = lista;
}

async function descargar(ruta, nombre) {
  ocupado.value = nombre;
  try {
    const r = await apiFetch(ruta);
    if (!r.ok) { toast('No se pudo descargar', 'error'); return; }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = nombre;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  } finally {
    ocupado.value = '';
  }
}

function guardarImagen(i) {
  const a = document.createElement('a');
  a.href = i.url;
  a.download = `entrada-${props.compra.codigo}-${i.n}.png`;
  a.click();
}

async function automatico(extra) {
  ocupado.value = extra ? 'auto-extra' : 'auto';
  try {
    const r = await apiFetch(`/api/app/entradas/${props.compra.id}/whatsapp`, { method: 'POST', body: JSON.stringify({ alExtra: extra }) });
    const d = await r.json().catch(() => ({}));
    toast(d.mensaje || (r.ok ? 'Enviado' : 'No se pudo enviar'), r.ok && d.ok ? 'ok' : 'error', 5000);
  } finally {
    ocupado.value = '';
  }
}

async function copiar() {
  try { await navigator.clipboard.writeText(mensaje.value); toast('Mensaje copiado', 'ok', 1500); } catch { toast('No se pudo copiar', 'error'); }
}

onMounted(cargarImagenes);
onBeforeUnmount(() => imagenes.value.forEach((i) => URL.revokeObjectURL(i.url)));
</script>

<template>
  <UiModal :titulo="`Entregar entradas · ${compra.codigo}`" ancho="720px" @cerrar="emit('cerrar')">
    <div class="entrega-ent">
      <div class="sello">
        <span class="check">✓</span>
        <div><strong>Compra aprobada · {{ compra.nombre }}</strong>
          <small>{{ cantidad === 1 ? '1 entrada' : `${cantidad} entradas` }} · {{ bs(compra.total) }}<template v-if="compra.resumen"> · {{ compra.resumen }}</template></small></div>
      </div>

      <div class="tira">
        <figure v-for="i in imagenes" :key="i.n" class="mini">
          <img :src="i.url" :alt="`Entrada ${i.n}`" />
          <button type="button" class="btn btn-sm" @click="guardarImagen(i)">⬇ {{ i.n }}</button>
        </figure>
        <p v-if="!imagenes.length" class="muted chico">Preparando las entradas…</p>
      </div>

      <section class="bloque">
        <header><b>Enviar al cliente</b><small>📱 {{ compra.celular }}<template v-if="compra.celularExtra"> · otro: {{ compra.celularExtra }}</template></small></header>
        <div class="principales">
          <a v-if="alPrincipal" class="btn btn-whatsapp" :href="alPrincipal" target="_blank" rel="noopener">
            <span class="ico">💬</span><span class="txt"><b>Enviar por WhatsApp</b><small>Mensaje con el enlace a sus entradas</small></span>
          </a>
          <button type="button" class="btn" :disabled="Boolean(ocupado)" @click="automatico(false)">
            <span class="ico">🤖</span><span class="txt"><b>{{ ocupado === 'auto' ? 'Enviando…' : 'Envío automático' }}</b><small>Manda las imágenes y el recibo</small></span>
          </button>
        </div>
        <div v-if="compra.celularExtra" class="principales">
          <a v-if="alExtra" class="btn" :href="alExtra" target="_blank" rel="noopener">
            <span class="ico">💬</span><span class="txt"><b>Al otro WhatsApp</b><small>{{ compra.celularExtra }}</small></span>
          </a>
          <button type="button" class="btn" :disabled="Boolean(ocupado)" @click="automatico(true)">
            <span class="ico">🤖</span><span class="txt"><b>{{ ocupado === 'auto-extra' ? 'Enviando…' : 'Automático al otro' }}</b><small>{{ compra.celularExtra }}</small></span>
          </button>
        </div>
        <div class="secundarios">
          <button type="button" class="btn btn-sm" :disabled="Boolean(ocupado)" @click="descargar(`/api/app/entradas/compra/${compra.codigo}/pases.pdf`, `entradas-${compra.codigo}.pdf`)">🎟️ Entradas PDF</button>
          <button type="button" class="btn btn-sm" :disabled="Boolean(ocupado)" @click="descargar(`/api/app/entradas/compra/${compra.codigo}/recibo.pdf`, `recibo-${compra.codigo}.pdf`)">🧾 Recibo</button>
          <button type="button" class="btn btn-sm" @click="copiar">📋 Copiar mensaje</button>
        </div>
        <details class="vista-mensaje"><summary>Ver el mensaje</summary><pre>{{ mensaje }}</pre></details>
      </section>
    </div>
    <template #pie>
      <button type="button" class="btn btn-primario" @click="emit('cerrar')">Listo</button>
    </template>
  </UiModal>
</template>

<style scoped>
.entrega-ent { display: flex; flex-direction: column; gap: 0.8rem; }
.sello { display: flex; align-items: center; gap: 0.75rem; padding: 0.7rem 0.9rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--ok) 12%, transparent); border: 1px solid color-mix(in srgb, var(--ok) 40%, transparent); }
.sello div { display: flex; flex-direction: column; min-width: 0; }
.sello small { color: var(--muted); }
.check { width: 34px; height: 34px; flex: none; border-radius: 50%; display: grid; place-items: center; background: var(--ok); color: #fff; font-weight: 800; }
.tira { display: flex; gap: 0.6rem; overflow-x: auto; padding-bottom: 0.3rem; }
.mini { margin: 0; flex: none; width: 120px; display: flex; flex-direction: column; gap: 0.3rem; align-items: stretch; }
.mini img { width: 100%; aspect-ratio: 9 / 16; object-fit: cover; border-radius: 10px; box-shadow: 0 6px 16px rgba(0, 0, 0, 0.25); }
.bloque { border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.7rem 0.8rem; display: flex; flex-direction: column; gap: 0.55rem; }
.bloque header { display: flex; align-items: baseline; justify-content: space-between; gap: 0.5rem; flex-wrap: wrap; }
.bloque header small { color: var(--muted); font-size: 0.8rem; }
.principales { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 210px), 1fr)); gap: 0.5rem; }
.principales .btn { justify-content: flex-start; gap: 0.6rem; padding: 0.6rem 0.75rem; white-space: normal; text-align: left; min-height: 54px; text-decoration: none; }
.ico { font-size: 1.3rem; flex: none; }
.txt { display: flex; flex-direction: column; min-width: 0; line-height: 1.25; }
.txt small { font-weight: 500; font-size: 0.76rem; opacity: 0.85; }
.btn-whatsapp { background: #1fa855; border-color: #1fa855; color: #fff; }
.secundarios { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.secundarios .btn { flex: 1 1 auto; }
.vista-mensaje summary { cursor: pointer; font-size: 0.85rem; color: var(--acento); font-weight: 600; }
.vista-mensaje pre { white-space: pre-wrap; font: inherit; font-size: 0.82rem; background: var(--panel-2); border-radius: var(--radio-sm); padding: 0.6rem; margin: 0.4rem 0 0; }
</style>
