<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import ModalPublico from './ModalPublico.vue';
import ConfirmarCelular from './ConfirmarCelular.vue';
import { useRouter } from 'vue-router';
import { url as urlApi } from '../config.js';

const props = defineProps({
  actividad: { type: Object, required: true },
  tipos: { type: Array, default: () => [] },
  inicial: { type: Object, default: () => ({}) },
});
const emit = defineEmits(['cerrar', 'comprada']);

const MAXIMO = 20;
const router = useRouter();
const enlaceLegal = (v) => router.resolve({ path: '/', query: { v } }).href;
const cantidades = reactive({});
const form = reactive({ nombre: '', ci: '', celular: '', celularExtra: '', conExtra: false, modoPago: '', comprobante: null, acepta: false });
const confirmando = ref(false);
const numeroConfirmado = ref('');
const extraValido = computed(() => !form.conExtra || !form.celularExtra.trim() || /^\+?\d{7,15}$/.test(form.celularExtra.replace(/\s/g, '')));
const enviando = ref(false);
const mensaje = ref('');

const pago = computed(() => props.actividad.pago || {});
const hayComprobante = computed(() => Boolean(pago.value.qrUrl || pago.value.banco || pago.value.cuenta));
const hayOficina = computed(() => Boolean(pago.value.oficina));
const condiciones = computed(() => String(props.actividad.condiciones || '').split('\n').map((l) => l.trim()).filter(Boolean));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;
const cantidad = computed(() => Object.values(cantidades).reduce((s, n) => s + (Number(n) || 0), 0));
const total = computed(() => props.tipos.reduce((s, t) => s + (Number(cantidades[t.id]) || 0) * Number(t.precio || 0), 0));
const gratis = computed(() => cantidad.value > 0 && total.value === 0);
const celularValido = computed(() => /^\+?\d{7,15}$/.test(form.celular.replace(/\s/g, '')));
const listo = computed(() => cantidad.value > 0 && form.nombre.trim().length >= 3 && form.ci.trim().length >= 4 && celularValido.value && extraValido.value
  && (gratis.value || form.modoPago) && (form.modoPago !== 'COMPROBANTE' || gratis.value || form.comprobante)
  && form.acepta);

function maximoDe(t) {
  const otros = cantidad.value - (Number(cantidades[t.id]) || 0);
  const tope = MAXIMO - otros;
  return t.quedan == null ? tope : Math.min(tope, t.quedan);
}

function cambiar(t, delta) {
  const actual = Number(cantidades[t.id]) || 0;
  cantidades[t.id] = Math.max(0, Math.min(maximoDe(t), actual + delta));
}

function pedirEnvio() {
  if (!listo.value || enviando.value) return;
  const numero = form.celular.replace(/\s/g, '');
  if (numeroConfirmado.value !== numero) { confirmando.value = true; return; }
  enviar();
}

function confirmado() {
  numeroConfirmado.value = form.celular.replace(/\s/g, '');
  confirmando.value = false;
  enviar();
}

async function enviar() {
  if (!listo.value || enviando.value) return;
  enviando.value = true;
  mensaje.value = '';
  try {
    const datos = {
      nombre: form.nombre.trim(), ci: form.ci.trim(), celular: form.celular.replace(/\s/g, ''),
      celularExtra: form.conExtra && form.celularExtra.trim() ? form.celularExtra.replace(/\s/g, '') : null,
      modoPago: gratis.value ? null : form.modoPago, acepta: form.acepta,
      items: props.tipos.filter((t) => cantidades[t.id] > 0).map((t) => ({ tipoId: t.id, cantidad: Number(cantidades[t.id]) })),
    };
    const cuerpo = new FormData();
    cuerpo.append('datos', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
    if (form.modoPago === 'COMPROBANTE' && form.comprobante) cuerpo.append('comprobante', form.comprobante);
    const r = await fetch(urlApi(`/api/publico/entradas/${props.actividad.id}`), { method: 'POST', body: cuerpo });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) throw new Error(d.mensaje || 'No se pudo enviar tu compra.');
    emit('comprada', d.codigo);
  } catch (e) {
    mensaje.value = e.message;
  } finally {
    enviando.value = false;
  }
}

onMounted(() => {
  for (const t of props.tipos) cantidades[t.id] = 0;
  for (const [k, v] of Object.entries(props.inicial || {})) cantidades[k] = v;
  if (!hayComprobante.value && hayOficina.value) form.modoPago = 'OFICINA';
  else if (hayComprobante.value) form.modoPago = 'COMPROBANTE';
});
</script>

<template>
  <ModalPublico ancho="600px" :cerrable="!enviando" @cerrar="emit('cerrar')">
    <template #cabecera>
      <div class="ficha-cab">
        <span class="pub-chip">🎟️ Entradas</span>
        <h2>{{ actividad.nombre }}</h2>
      </div>
    </template>

    <form id="form-entradas" class="formulario" @submit.prevent="pedirEnvio">
      <fieldset>
        <legend>¿Cuántas entradas?</legend>
        <div class="tipos">
          <div v-for="t in tipos" :key="t.id" class="tipo" :class="{ agotado: t.quedan === 0 }">
            <div class="tipo-info">
              <strong>{{ t.nombre }}</strong>
              <small v-if="t.descripcion">{{ t.descripcion }}</small>
              <small v-if="t.quedan === 0" class="agotada">Agotada</small>
              <small v-else-if="t.quedan != null && t.quedan <= 20" class="pocas">Quedan {{ t.quedan }}</small>
            </div>
            <b class="precio">{{ Number(t.precio) ? bs(t.precio) : 'Gratis' }}</b>
            <div class="contador" role="group" :aria-label="`Cantidad de ${t.nombre}`">
              <button type="button" :disabled="!cantidades[t.id]" aria-label="Menos" @click="cambiar(t, -1)">−</button>
              <span>{{ cantidades[t.id] || 0 }}</span>
              <button type="button" :disabled="maximoDe(t) <= (cantidades[t.id] || 0)" aria-label="Más" @click="cambiar(t, 1)">+</button>
            </div>
          </div>
        </div>
        <div class="total"><span>{{ cantidad }} entrada{{ cantidad === 1 ? '' : 's' }}</span><b>{{ bs(total) }}</b></div>
      </fieldset>

      <fieldset>
        <legend>Tus datos</legend>
        <div class="campos">
          <label class="pub-campo ancho"><span>Nombre completo</span>
            <input v-model="form.nombre" autocomplete="name" maxlength="160" /><small>Las entradas salen a este nombre.</small></label>
          <label class="pub-campo"><span>Carnet (C.I.)</span><input v-model="form.ci" inputmode="numeric" maxlength="20" /></label>
          <label class="pub-campo"><span>Celular con WhatsApp</span>
            <input v-model="form.celular" inputmode="tel" autocomplete="tel" maxlength="16" placeholder="70000000" />
            <small>Aquí te enviamos tus entradas.</small></label>
          <button v-if="!form.conExtra" type="button" class="agregar-extra ancho" @click="form.conExtra = true">＋ Agregar otro número de WhatsApp (opcional)</button>
          <label v-else class="pub-campo ancho"><span>Otro WhatsApp (opcional)</span>
            <input v-model="form.celularExtra" inputmode="tel" maxlength="16" placeholder="Ej. el de un familiar" />
            <small :class="{ error: !extraValido }">{{ extraValido ? 'Por si no te encontramos en el primero.' : 'Revisa este número.' }}</small></label>
        </div>
      </fieldset>

      <fieldset v-if="!gratis && (hayComprobante || hayOficina)">
        <legend>Pago</legend>
        <div class="modos">
          <label v-if="hayComprobante" class="modo" :class="{ on: form.modoPago === 'COMPROBANTE' }">
            <input v-model="form.modoPago" type="radio" value="COMPROBANTE" />
            <span><b>{{ pago.qrUrl ? '📱 Pago con QR' : '🏦 Transferencia' }}</b><small>Paga y adjunta el comprobante.</small></span>
          </label>
          <label v-if="hayOficina" class="modo" :class="{ on: form.modoPago === 'OFICINA' }">
            <input v-model="form.modoPago" type="radio" value="OFICINA" />
            <span><b>🏢 Pago en persona</b><small>{{ pago.oficina }}</small></span>
          </label>
        </div>
        <div v-if="form.modoPago === 'COMPROBANTE'" class="datos-pago">
          <div v-if="pago.qrUrl" class="qr-caja">
            <img :src="urlApi(pago.qrUrl)" alt="QR de pago" class="qr" />
            <a class="guardar-qr" :href="urlApi(pago.qrUrl)" download="qr-de-pago" target="_blank" rel="noopener">⬇ Guardar QR</a>
          </div>
          <dl>
            <div><dt>Monto</dt><dd class="precio">{{ bs(total) }}</dd></div>
            <div v-if="pago.banco"><dt>Banco</dt><dd>{{ pago.banco }}</dd></div>
            <div v-if="pago.cuenta"><dt>Cuenta</dt><dd class="mono">{{ pago.cuenta }}</dd></div>
            <div v-if="pago.titular"><dt>Titular</dt><dd>{{ pago.titular }}</dd></div>
            <div v-if="pago.instrucciones" class="ancho"><dt>Indicaciones</dt><dd>{{ pago.instrucciones }}</dd></div>
          </dl>
          <label class="archivo" :class="{ listo: form.comprobante }">
            <input type="file" accept="image/png,image/jpeg,image/webp,application/pdf" @change="form.comprobante = $event.target.files?.[0] || null" />
            <span>{{ form.comprobante ? `✓ ${form.comprobante.name}` : '📷 Adjuntar comprobante (foto o PDF)' }}</span>
          </label>
        </div>
      </fieldset>

      <fieldset>
        <legend>{{ condiciones.length ? 'Condiciones' : 'Antes de enviar' }}</legend>
        <ul v-if="condiciones.length" class="condiciones"><li v-for="c in condiciones" :key="c">{{ c }}</li></ul>
        <label class="acepto"><input v-model="form.acepta" type="checkbox" />
          <span>He leído y acepto {{ condiciones.length ? 'las condiciones de la actividad, ' : '' }}los
            <a :href="enlaceLegal('terminos')" target="_blank" rel="noopener">Términos y condiciones de compra</a> y la
            <a :href="enlaceLegal('privacidad')" target="_blank" rel="noopener">Política de privacidad</a>.</span></label>
      </fieldset>

      <p class="nota">{{ gratis ? 'Tus entradas se generan al instante.' : 'Revisamos tu pago y te enviamos tus entradas con QR. Cada QR permite un ingreso.' }}</p>
      <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>
    </form>

    <template #pie>
      <button type="button" class="pub-boton secundario" :disabled="enviando" @click="emit('cerrar')">Cancelar</button>
      <button type="submit" form="form-entradas" class="pub-boton" :disabled="!listo || enviando">
        {{ enviando ? 'Enviando…' : gratis ? 'Obtener entradas' : `Comprar · ${bs(total)}` }}
      </button>
    </template>
  </ModalPublico>
  <ConfirmarCelular v-if="confirmando" :numero="form.celular" :extra="form.conExtra ? form.celularExtra : ''" @si="confirmado" @no="confirmando = false" />
</template>

<style scoped>
.ficha-cab { display: flex; flex-direction: column; gap: 4px; }
.ficha-cab h2 { margin: 0; }
.formulario { display: flex; flex-direction: column; gap: 14px; }
fieldset { border: 0; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
legend { font-weight: 800; margin-bottom: 8px; color: var(--pub-acento-claro, #a78bfa); }
.tipos { display: flex; flex-direction: column; gap: 8px; }
.tipo { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 12px; align-items: center; padding: 12px; border-radius: 14px; background: rgba(255, 255, 255, 0.05); border: 1px solid rgba(255, 255, 255, 0.08); }
.tipo.agotado { opacity: 0.5; }
.tipo-info { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.tipo-info small { color: var(--pub-suave); font-size: 0.8rem; }
.agotada { color: #f87171 !important; font-weight: 700; }
.pocas { color: #fbbf24 !important; font-weight: 700; }
.precio { font-variant-numeric: tabular-nums; }
.contador { display: flex; align-items: center; gap: 8px; }
.contador button { width: 36px; height: 36px; border-radius: 50%; border: 1px solid rgba(255, 255, 255, 0.2); background: rgba(255, 255, 255, 0.08); color: inherit; font-size: 1.2rem; font-weight: 800; cursor: pointer; }
.contador button:disabled { opacity: 0.35; cursor: default; }
.contador span { min-width: 22px; text-align: center; font-weight: 800; font-variant-numeric: tabular-nums; }
.total { display: flex; justify-content: space-between; padding: 10px 12px; border-radius: 12px; background: rgba(143, 109, 255, 0.14); font-size: 1.05rem; }
.campos { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.campos .ancho { grid-column: 1 / -1; }
.agregar-extra { justify-self: start; border: 0; background: none; color: var(--pub-acento-claro, #a78bfa); font: inherit; font-weight: 700; font-size: 0.88rem; cursor: pointer; padding: 2px 0; }
small.error { color: #f87171 !important; }
.condiciones { margin: 0; padding-left: 1.2rem; display: flex; flex-direction: column; gap: 4px; font-size: 0.9rem; }
.acepto { display: flex; gap: 8px; align-items: flex-start; font-weight: 600; cursor: pointer; line-height: 1.4; }
.acepto a { color: var(--pub-acento-claro, #a78bfa); }
.acepto input { width: 18px; height: 18px; }
.datos-pago dt { display: block; font-size: 0.72rem; color: var(--pub-suave); text-transform: uppercase; letter-spacing: 0.06em; }
.modos { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.modo { display: flex; gap: 10px; align-items: flex-start; padding: 12px; border-radius: 12px; border: 1px solid var(--pub-linea); cursor: pointer; }
.modo.on { border-color: var(--pub-acento); background: color-mix(in srgb, var(--pub-acento) 12%, transparent); }
.modo input { accent-color: var(--pub-acento); margin-top: 3px; }
.modo span { display: flex; flex-direction: column; gap: 2px; }
.modo small { color: var(--pub-suave); font-size: 0.78rem; }
.datos-pago { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 12px; padding: 12px; border-radius: 14px; background: var(--pub-panel-2); align-items: start; }
.qr-caja { grid-row: span 2; display: flex; flex-direction: column; align-items: center; gap: 6px; }
.datos-pago .qr { width: 170px; height: 170px; object-fit: contain; background: #fff; border-radius: 10px; padding: 6px; box-sizing: border-box; }
.guardar-qr { color: var(--pub-acento-claro); font-weight: 700; font-size: 0.85rem; text-decoration: none; }
.datos-pago dl { margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.datos-pago dd, .info dd { margin: 2px 0 0; font-weight: 600; overflow-wrap: anywhere; }
.datos-pago .ancho { grid-column: 1 / -1; }
.mono { font-family: var(--pub-mono); }
.archivo { grid-column: 1 / -1; display: flex; align-items: center; justify-content: center; min-height: 48px; border: 2px dashed var(--pub-linea); border-radius: 12px; cursor: pointer; font-weight: 700; padding: 0 12px; text-align: center; }
.archivo input { display: none; }
.archivo.listo { border-color: var(--pub-ok); color: var(--pub-ok); border-style: solid; }
.nota { margin: 0; font-size: 0.84rem; color: var(--pub-suave); }
.aviso-error { margin: 0; padding: 10px 14px; border-radius: 12px; background: color-mix(in srgb, var(--pub-error) 14%, transparent); color: var(--pub-error); font-size: 0.9rem; }
@media (max-width: 640px) {
  .modos { grid-template-columns: 1fr; }
  .datos-pago { grid-template-columns: 1fr; justify-items: center; }
  .qr-caja { grid-row: auto; }
  .datos-pago dl { width: 100%; }
}
@media (max-width: 520px) {
  .campos { grid-template-columns: 1fr; }
  .tipo { grid-template-columns: minmax(0, 1fr) auto; }
  .tipo .contador { grid-column: 1 / -1; justify-content: flex-end; }
}
</style>
