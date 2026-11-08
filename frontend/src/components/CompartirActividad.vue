<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import QRCode from 'qrcode';
import UiModal from './UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { marca, cargarMarca } from '../ui/marca';
import { url as urlApi } from '../config.js';
import { guardarBlob } from '../ui/descargas';

const props = defineProps({
  actividad: { type: Object, required: true },
  bienvenida: { type: Boolean, default: false },
});
const emit = defineEmits(['cerrar']);
const router = useRouter();

const PALETAS = [
  { principal: '#6d28d9', fondo: '#f5f3ff' },
  { principal: '#0f766e', fondo: '#ecfdf5' },
  { principal: '#b91c1c', fondo: '#fff1f2' },
  { principal: '#1d4ed8', fondo: '#eff6ff' },
  { principal: '#111827', fondo: '#f8fafc' },
  { principal: '#c2410c', fondo: '#fff7ed' },
];

const estilo = reactive({
  modo: 'tarjeta', principal: '#6d28d9', fondo: '#f5f3ff', qrColor: '#111827', logo: 'marca',
  llamado: 'Escanea y reserva tu lugar', redondo: true,
});
const lienzo = ref(null);
const guardando = ref(false);
const dibujando = ref(false);

const urlPublica = computed(() => {
  const ruta = router.resolve({ path: '/', query: { v: 'actividad', edicion: props.actividad.id } }).href;
  return new URL(ruta, window.location.origin).href;
});
const logoUrl = computed(() => {
  if (estilo.logo === 'marca' && marca.logoUrl) return marca.logoUrl;
  if (estilo.logo === 'portada' && props.actividad.portadaUrl) return urlApi(props.actividad.portadaUrl);
  return null;
});
const textoWhatsApp = computed(() => `${props.actividad.nombre}${props.actividad.fechasTexto ? ` · ${props.actividad.fechasTexto}` : ''}\n${urlPublica.value}`);

function cargarEstilo() {
  try {
    const guardado = props.actividad.qrEstilo ? JSON.parse(props.actividad.qrEstilo) : null;
    if (guardado && typeof guardado === 'object') Object.assign(estilo, guardado);
  } catch { }
}

function imagen(src) {
  return new Promise((resolve) => {
    if (!src) { resolve(null); return; }
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

function rectRedondo(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function lineas(ctx, texto, ancho) {
  const palabras = String(texto || '').split(/\s+/);
  const salida = [];
  let actual = '';
  for (const p of palabras) {
    const prueba = actual ? `${actual} ${p}` : p;
    if (ctx.measureText(prueba).width > ancho && actual) { salida.push(actual); actual = p; } else actual = prueba;
  }
  if (actual) salida.push(actual);
  return salida;
}

async function qrConLogo(tam) {
  const c = document.createElement('canvas');
  await QRCode.toCanvas(c, urlPublica.value, {
    width: tam, margin: 1, errorCorrectionLevel: 'H', color: { dark: estilo.qrColor, light: '#ffffff' },
  });
  const logo = await imagen(logoUrl.value);
  if (logo) {
    const ctx = c.getContext('2d');
    const l = Math.round(tam * 0.22);
    const x = (tam - l) / 2;
    ctx.fillStyle = '#ffffff';
    rectRedondo(ctx, x - 8, x - 8, l + 16, l + 16, 14);
    ctx.fill();
    const escala = Math.min(l / logo.width, l / logo.height);
    const w = logo.width * escala;
    const h = logo.height * escala;
    ctx.drawImage(logo, (tam - w) / 2, (tam - h) / 2, w, h);
  }
  return c;
}

async function dibujar() {
  const c = lienzo.value;
  if (!c) return;
  dibujando.value = true;
  try {
    const ctx = c.getContext('2d');
    if (estilo.modo === 'qr') {
      c.width = 900; c.height = 900;
      const qr = await qrConLogo(900);
      ctx.clearRect(0, 0, 900, 900);
      ctx.drawImage(qr, 0, 0);
      return;
    }
    const W = 1080;
    const H = 1350;
    c.width = W; c.height = H;
    const g = ctx.createLinearGradient(0, 0, W, H);
    g.addColorStop(0, estilo.principal);
    g.addColorStop(1, `${estilo.principal}cc`);
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = 'rgba(255,255,255,0.08)';
    ctx.beginPath(); ctx.arc(W * 0.92, H * 0.06, 260, 0, Math.PI * 2); ctx.fill();
    ctx.beginPath(); ctx.arc(W * 0.05, H * 0.98, 300, 0, Math.PI * 2); ctx.fill();

    ctx.fillStyle = '#ffffff';
    ctx.textAlign = 'center';
    ctx.font = '700 34px system-ui, sans-serif';
    ctx.globalAlpha = 0.85;
    ctx.fillText((marca.nombre || '').toUpperCase(), W / 2, 110);
    ctx.globalAlpha = 1;
    ctx.font = '800 76px system-ui, sans-serif';
    let y = 210;
    for (const l of lineas(ctx, props.actividad.nombre, W - 160).slice(0, 3)) { ctx.fillText(l, W / 2, y); y += 86; }
    ctx.font = '500 36px system-ui, sans-serif';
    const datos = [props.actividad.fechasTexto, props.actividad.lugar].filter(Boolean).join('  ·  ');
    for (const l of lineas(ctx, datos, W - 180).slice(0, 2)) { ctx.fillText(l, W / 2, y + 6); y += 46; }

    const caja = 620;
    const cx = (W - caja) / 2;
    const cy = Math.max(y + 40, 470);
    ctx.fillStyle = estilo.fondo;
    rectRedondo(ctx, cx - 30, cy - 30, caja + 60, caja + 60, estilo.redondo ? 48 : 8);
    ctx.fill();
    ctx.fillStyle = '#ffffff';
    rectRedondo(ctx, cx, cy, caja, caja, estilo.redondo ? 28 : 4);
    ctx.fill();
    const qr = await qrConLogo(caja - 40);
    ctx.drawImage(qr, cx + 20, cy + 20);

    ctx.fillStyle = '#ffffff';
    ctx.font = '800 50px system-ui, sans-serif';
    ctx.fillText(estilo.llamado || '', W / 2, cy + caja + 120);
    ctx.font = '500 28px system-ui, sans-serif';
    ctx.globalAlpha = 0.85;
    ctx.fillText(urlPublica.value.replace(/^https?:\/\//, '').slice(0, 60), W / 2, cy + caja + 170);
    ctx.globalAlpha = 1;
  } catch (e) {
    toast(`No se pudo dibujar el QR: ${e.message}`, 'error');
  } finally {
    dibujando.value = false;
  }
}

let pendiente = null;
watch(() => ({ ...estilo }), () => { clearTimeout(pendiente); pendiente = setTimeout(dibujar, 120); }, { deep: true });

async function descargar() {
  let blob;
  try {
    blob = await new Promise((r) => lienzo.value.toBlob(r, 'image/png'));
  } catch {
    toast('No se pudo generar la imagen con ese logo; prueba con «Nada» al centro', 'error');
    return;
  }
  const nombre = `${(props.actividad.nombre || 'actividad').toLowerCase().normalize('NFD').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}-${estilo.modo === 'qr' ? 'qr' : 'tarjeta'}.png`;
  const r = await guardarBlob(blob, nombre);
  toast(r?.destino === 'telefono' ? 'Imagen guardada en Documentos' : 'Imagen descargada', 'ok', 1800);
}

async function compartir() {
  try {
    const blob = await new Promise((r) => lienzo.value.toBlob(r, 'image/png'));
    const archivo = new File([blob], 'actividad.png', { type: 'image/png' });
    if (navigator.canShare?.({ files: [archivo] })) {
      await navigator.share({ files: [archivo], title: props.actividad.nombre, text: textoWhatsApp.value });
    } else if (navigator.share) {
      await navigator.share({ title: props.actividad.nombre, text: textoWhatsApp.value, url: urlPublica.value });
    } else {
      await copiar();
    }
  } catch { }
}

async function copiar() {
  try {
    await navigator.clipboard.writeText(urlPublica.value);
    toast('Enlace copiado', 'ok', 1500);
  } catch { toast('No se pudo copiar; selecciónalo a mano', 'advertencia'); }
}

async function guardarEstilo() {
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/ediciones/${props.actividad.id}/qr-estilo`, { method: 'PUT', body: JSON.stringify({ ...estilo }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) throw new Error(d.mensaje || 'No se pudo guardar el diseño');
    toast('Diseño guardado para esta actividad', 'ok', 1600);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

onMounted(async () => {
  cargarEstilo();
  await cargarMarca();
  if (estilo.logo === 'marca' && !marca.logoUrl) estilo.logo = props.actividad.portadaUrl ? 'portada' : 'ninguno';
  dibujar();
});
</script>

<template>
  <UiModal :titulo="bienvenida ? '🎉 ¡Tu actividad está publicada!' : `Compartir «${actividad.nombre}»`" ancho="960px" @cerrar="emit('cerrar')">
    <p v-if="bienvenida" class="bienvenida">Ya aparece en la página pública y puede recibir compras. Comparte el enlace o descarga la imagen con el QR para tus redes, afiches o mesas de informes.</p>
    <div class="compartir">
      <section class="lado-control">
        <div class="bloque">
          <h4>🔗 Enlace de la actividad</h4>
          <div class="copiar">
            <input class="control" :value="urlPublica" readonly @focus="$event.target.select()" />
            <button type="button" class="btn btn-primario" @click="copiar">Copiar</button>
          </div>
          <div class="botones">
            <a class="btn btn-sm wa" :href="`https://wa.me/?text=${encodeURIComponent(textoWhatsApp)}`" target="_blank" rel="noopener">WhatsApp</a>
            <button type="button" class="btn btn-sm" @click="compartir">📤 Compartir…</button>
            <a class="btn btn-sm btn-fantasma" :href="urlPublica" target="_blank" rel="noopener">👁 Ver como cliente</a>
          </div>
        </div>

        <div class="bloque">
          <h4>🎨 Diseña tu QR</h4>
          <div class="segmento" role="radiogroup" aria-label="Formato">
            <button type="button" :class="{ on: estilo.modo === 'tarjeta' }" @click="estilo.modo = 'tarjeta'">Tarjeta para redes</button>
            <button type="button" :class="{ on: estilo.modo === 'qr' }" @click="estilo.modo = 'qr'">Solo el QR</button>
          </div>
          <div v-if="estilo.modo === 'tarjeta'" class="campo">
            <span>Colores</span>
            <div class="paletas">
              <button v-for="p in PALETAS" :key="p.principal" type="button" class="paleta" :class="{ on: estilo.principal === p.principal }"
                      :style="{ background: `linear-gradient(135deg, ${p.principal} 55%, ${p.fondo} 55%)` }" :aria-label="`Color ${p.principal}`"
                      @click="estilo.principal = p.principal; estilo.fondo = p.fondo"></button>
              <label class="paleta propia" title="Color propio"><input v-model="estilo.principal" type="color" /></label>
            </div>
          </div>
          <div class="dos">
            <label class="campo"><span>Color del QR</span><input v-model="estilo.qrColor" type="color" class="control color" /></label>
            <label class="campo"><span>Al centro del QR</span>
              <select v-model="estilo.logo" class="control">
                <option value="marca" :disabled="!marca.logoUrl">Logo del negocio{{ marca.logoUrl ? '' : ' (sin logo)' }}</option>
                <option value="portada" :disabled="!actividad.portadaUrl">Portada de la actividad{{ actividad.portadaUrl ? '' : ' (sin portada)' }}</option>
                <option value="ninguno">Nada</option>
              </select>
            </label>
          </div>
          <label v-if="estilo.modo === 'tarjeta'" class="campo"><span>Texto de invitación</span>
            <input v-model="estilo.llamado" class="control" maxlength="40" placeholder="Escanea y reserva tu lugar" />
          </label>
          <label v-if="estilo.modo === 'tarjeta'" class="check"><input v-model="estilo.redondo" type="checkbox" /> Bordes redondeados</label>
        </div>
      </section>

      <section class="lado-vista">
        <div class="marco" :class="estilo.modo"><canvas ref="lienzo" :aria-label="`Vista previa: ${estilo.modo === 'qr' ? 'código QR' : 'tarjeta con QR'}`"></canvas></div>
        <div class="botones">
          <button type="button" class="btn btn-primario" :disabled="dibujando" @click="descargar">⬇ Descargar imagen</button>
          <button type="button" class="btn" :disabled="guardando" @click="guardarEstilo">💾 Guardar diseño</button>
        </div>
      </section>
    </div>
    <template #pie>
      <button type="button" class="btn" @click="emit('cerrar')">Listo</button>
    </template>
  </UiModal>
</template>

<style scoped>
.bienvenida { margin: 0 0 0.4rem; padding: 0.7rem 0.9rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--ok) 12%, transparent); color: var(--text); line-height: 1.45; }
.compartir { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 0.9fr); gap: 1rem; align-items: start; }
.lado-control { display: flex; flex-direction: column; gap: 0.9rem; }
.bloque { display: flex; flex-direction: column; gap: 0.6rem; padding: 0.9rem; border: 1px solid var(--border); border-radius: var(--radio); background: var(--panel-2); }
.bloque h4 { margin: 0; font-size: 0.95rem; }
.copiar { display: flex; gap: 0.4rem; }
.copiar .control { flex: 1; min-width: 0; font-size: 0.85rem; }
.botones { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.wa { background: #1fa855; border-color: #1fa855; color: #fff; text-decoration: none; }
.segmento { display: grid; grid-template-columns: 1fr 1fr; gap: 0.3rem; padding: 3px; border-radius: 10px; background: var(--bg); border: 1px solid var(--border); }
.segmento button { border: 0; border-radius: 8px; padding: 0.45rem; font: inherit; font-weight: 700; font-size: 0.85rem; background: transparent; color: var(--muted); cursor: pointer; }
.segmento button.on { background: var(--acento); color: var(--acento-texto); }
.campo { display: flex; flex-direction: column; gap: 0.3rem; font-size: 0.85rem; }
.campo > span { color: var(--muted); font-weight: 600; }
.dos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; }
.color { height: 42px; padding: 4px; background: #fff; border-color: #cbd5e1; cursor: pointer; }
.paletas { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.paleta { width: 38px; height: 38px; border-radius: 50%; border: 2px solid var(--border); cursor: pointer; padding: 0; position: relative; overflow: hidden; }
.paleta.on { outline: 3px solid var(--acento); outline-offset: 2px; }
.paleta.propia { display: grid; place-items: center; background: conic-gradient(red, orange, yellow, lime, cyan, blue, magenta, red); }
.paleta.propia input { opacity: 0; position: absolute; inset: 0; width: 100%; height: 100%; cursor: pointer; }
.check { display: flex; align-items: center; gap: 0.45rem; font-size: 0.85rem; }
.lado-vista { display: flex; flex-direction: column; gap: 0.6rem; align-items: center; position: sticky; top: 0; }
.marco { width: 100%; max-width: 360px; border-radius: 16px; overflow: hidden; box-shadow: var(--sombra-md); background: #fff; }
.marco canvas { display: block; width: 100%; height: auto; }
.marco.qr { max-width: 300px; padding: 10px; }
@media (max-width: 760px) {
  .compartir { grid-template-columns: minmax(0, 1fr); }
  .lado-vista { order: -1; position: static; }
  .marco { max-width: 260px; }
}
</style>
