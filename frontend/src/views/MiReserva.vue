<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import QRCode from 'qrcode';
import { url as urlApi } from '../config.js';

const route = useRoute();
const reserva = ref(null);
const error = ref('');
const segundos = ref(0);
const subiendo = ref(false);
const mensaje = ref('');
const archivo = ref(null);
const qr = ref('');
const copiado = ref(false);
let reloj = null;
let sondeo = null;

const ESTADOS = {
  BLOQUEADA: { txt: 'Mesa bloqueada', clase: 'espera' },
  EN_REVISION: { txt: 'Validando tu pago', clase: 'revision' },
  CONFIRMADA: { txt: 'Reserva confirmada', clase: 'ok' },
  RECHAZADA: { txt: 'Pago rechazado', clase: 'mal' },
  VENCIDA: { txt: 'Tiempo vencido', clase: 'mal' },
  CANCELADA: { txt: 'Reserva cancelada', clase: 'mal' },
};

const evento = computed(() => reserva.value?.evento ?? null);
const estado = computed(() => ESTADOS[reserva.value?.estado] ?? { txt: '', clase: '' });
const reloj_ = computed(() => {
  const s = Math.max(0, segundos.value);
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
});
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO')}`;

async function cargar() {
  try {
    const r = await fetch(urlApi(`/api/publico/mesas/reservas/${encodeURIComponent(route.query.reserva)}`));
    if (!r.ok) { error.value = 'No encontramos esta reserva. Revisa el enlace.'; return; }
    reserva.value = await r.json();
    segundos.value = reserva.value.segundosRestantes || 0;
  } catch {
    error.value = 'Sin conexión. Vuelve a intentar.';
  }
}

watch(() => reserva.value?.estado, async (e) => {
  clearInterval(sondeo);
  if (e === 'EN_REVISION' || e === 'BLOQUEADA') sondeo = setInterval(cargar, e === 'BLOQUEADA' ? 30000 : 15000);
  if (e === 'CONFIRMADA') {
    qr.value = await QRCode.toDataURL(reserva.value.codigo, { width: 320, margin: 2, errorCorrectionLevel: 'M' });
  }
});

async function enviarComprobante() {
  if (!archivo.value) return;
  subiendo.value = true;
  mensaje.value = '';
  try {
    const datos = new FormData();
    datos.append('archivo', archivo.value);
    const r = await fetch(urlApi(`/api/publico/mesas/reservas/${encodeURIComponent(reserva.value.codigo)}/comprobante`), {
      method: 'POST', body: datos,
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) mensaje.value = d.mensaje || 'No se pudo enviar el comprobante.';
    await cargar();
  } catch {
    mensaje.value = 'Sin conexión. Intenta de nuevo antes de que venza el tiempo.';
  } finally {
    subiendo.value = false;
  }
}

async function cancelar() {
  const r = await fetch(urlApi(`/api/publico/mesas/reservas/${encodeURIComponent(reserva.value.codigo)}/cancelar`), { method: 'POST' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) mensaje.value = d.mensaje || 'No se pudo cancelar.';
  await cargar();
}

async function copiar(texto) {
  try {
    await navigator.clipboard.writeText(texto);
    copiado.value = true;
    setTimeout(() => { copiado.value = false; }, 1500);
  } catch {
    copiado.value = false;
  }
}

onMounted(async () => {
  await cargar();
  reloj = setInterval(() => {
    if (reserva.value?.estado !== 'BLOQUEADA') return;
    segundos.value -= 1;
    if (segundos.value === 0) cargar();
  }, 1000);
});
onUnmounted(() => { clearInterval(reloj); clearInterval(sondeo); });
</script>

<template>
  <div class="noche">
    <main class="contenedor">
      <p v-if="error" class="vacio">{{ error }}</p>
      <template v-else-if="reserva && evento">
        <header class="cab">
          <span class="local">{{ evento.localNombre }}</span>
          <h1>{{ evento.nombre }}</h1>
          <p class="cuando">{{ evento.fechaTexto }}</p>
        </header>

        <section class="estado" :class="estado.clase">
          <span class="etq">{{ estado.txt }}</span>
          <div class="resumen">
            <div><span>Mesa</span><strong>{{ reserva.mesa }}<small v-if="reserva.zona"> · {{ reserva.zona }}</small></strong></div>
            <div><span>Total</span><strong>{{ bs(reserva.monto) }}</strong></div>
            <div><span>Titular</span><strong>{{ reserva.nombre }} {{ reserva.apellidos }}</strong></div>
            <div><span>Carnet</span><strong>{{ reserva.ci }}</strong></div>
          </div>
          <p class="codigo">Código de reserva: <b>{{ reserva.codigo }}</b></p>
        </section>

        <template v-if="reserva.estado === 'BLOQUEADA'">
          <section class="tiempo" :class="{ urgente: segundos < 60 }">
            <span>Tiempo para adjuntar el comprobante</span>
            <strong>{{ reloj_ }}</strong>
          </section>
          <section class="pago">
            <h2>1. Paga {{ bs(reserva.monto) }}</h2>
            <img v-if="evento.pagoQrUrl" :src="urlApi(evento.pagoQrUrl)" alt="QR de pago del local" class="qr-pago" />
            <dl v-if="evento.pagoCuenta" class="cuenta">
              <div v-if="evento.pagoBanco"><dt>Banco</dt><dd>{{ evento.pagoBanco }}</dd></div>
              <div><dt>Cuenta</dt><dd>{{ evento.pagoCuenta }} <button type="button" class="mini" @click="copiar(evento.pagoCuenta)">{{ copiado ? 'Copiado' : 'Copiar' }}</button></dd></div>
              <div v-if="evento.pagoTitular"><dt>Titular</dt><dd>{{ evento.pagoTitular }}</dd></div>
            </dl>
            <p v-if="evento.pagoInstrucciones" class="nota">{{ evento.pagoInstrucciones }}</p>
            <p class="nota">Pon el código <b>{{ reserva.codigo }}</b> en la glosa o referencia del pago.</p>
          </section>
          <section class="pago">
            <h2>2. Adjunta tu comprobante</h2>
            <label class="archivo">
              <input id="m-comprobante" type="file" accept="image/png,image/jpeg,image/webp,application/pdf" @change="archivo = $event.target.files?.[0] || null" />
              <span>{{ archivo ? archivo.name : 'Elegir foto o PDF del comprobante' }}</span>
            </label>
            <button class="boton" :disabled="!archivo || subiendo" @click="enviarComprobante">{{ subiendo ? 'Enviando…' : 'Enviar comprobante' }}</button>
            <button class="enlace" @click="cancelar">Cancelar y liberar la mesa</button>
          </section>
        </template>

        <section v-else-if="reserva.estado === 'EN_REVISION'" class="info">
          <p>Recibimos tu comprobante. El local está validando el pago; esta página se actualiza sola. Cuando se confirme, aquí aparecerá tu código QR de ingreso.</p>
          <p class="nota">Guarda este enlace para volver a ver tu reserva.</p>
        </section>

        <section v-else-if="reserva.estado === 'CONFIRMADA'" class="confirmada">
          <img v-if="qr" :src="qr" alt="Código QR de ingreso" class="qr" />
          <p>Muestra este QR y tu carnet en el ingreso.</p>
          <p v-if="evento.horaLimiteTexto" class="limite">Llega antes de las <b>{{ evento.horaLimiteTexto }}</b>.</p>
          <p v-if="reserva.ingresoEn" class="nota">Ingreso registrado.</p>
          <p v-if="reserva.beneficios" class="nota">Incluye: {{ reserva.beneficios }}</p>
        </section>

        <section v-else class="info">
          <p v-if="reserva.motivo">{{ reserva.motivo }}</p>
          <router-link :to="{ path: '/', query: { evento: evento.id } }" class="boton">Elegir otra mesa</router-link>
        </section>

        <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>

        <details class="politicas">
          <summary>Condiciones de la reserva</summary>
          <ul><li v-for="(p, i) in evento.politicas.split('\n').filter(Boolean)" :key="i">{{ p }}</li></ul>
        </details>
        <p v-if="evento.celularContacto" class="contacto">Consultas: {{ evento.celularContacto }}</p>
      </template>
      <p v-else class="vacio">Cargando…</p>
    </main>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,700;12..96,800&family=Public+Sans:wght@400;600&family=JetBrains+Mono:wght@500&display=swap');
.noche {
  --fondo: #0d0f1c; --panel: #161a2e; --linea: #2a2f4d; --texto: #eef0fb; --suave: #a3a9c7;
  --acento: #b49bff; --acento-fuerte: #8f6dff; --ok: #3fd18f; --alerta: #ffb347; --error: #ff6b8b; --revision: #9d86ff;
  min-height: 100vh; background: radial-gradient(ellipse at top, #20184a 0%, var(--fondo) 55%); color: var(--texto);
  font-family: 'Public Sans', system-ui, sans-serif; color-scheme: dark;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px));
}
.contenedor { max-width: 560px; margin: 0 auto; padding-block: 28px 56px; display: flex; flex-direction: column; gap: 16px; }
.cab { display: flex; flex-direction: column; gap: 4px; }
.local { font-size: 0.78rem; letter-spacing: 0.14em; text-transform: uppercase; color: var(--acento); font-weight: 600; }
h1 { margin: 0; font: 800 clamp(1.7rem, 6vw, 2.4rem)/1.05 'Bricolage Grotesque', system-ui, sans-serif; text-wrap: balance; }
h2 { margin: 0; font: 700 1.15rem 'Bricolage Grotesque', system-ui, sans-serif; }
.cuando { margin: 0; color: var(--suave); text-transform: capitalize; }
section { background: var(--panel); border: 1px solid var(--linea); border-radius: 16px; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.estado { border-left: 4px solid var(--linea); }
.estado.espera { border-left-color: var(--alerta); } .estado.revision { border-left-color: var(--revision); }
.estado.ok { border-left-color: var(--ok); } .estado.mal { border-left-color: var(--error); }
.etq { font-weight: 700; font-size: 0.95rem; }
.espera .etq { color: var(--alerta); } .revision .etq { color: var(--revision); } .ok .etq { color: var(--ok); } .mal .etq { color: var(--error); }
.resumen { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.resumen span { display: block; font-size: 0.75rem; color: var(--suave); text-transform: uppercase; letter-spacing: 0.06em; }
.resumen strong { font-size: 1rem; }
.resumen small { color: var(--suave); font-weight: 400; }
.codigo { margin: 0; font-size: 0.85rem; color: var(--suave); }
.codigo b { font-family: 'JetBrains Mono', monospace; color: var(--texto); }
.tiempo { flex-direction: row; justify-content: space-between; align-items: center; }
.tiempo strong { font: 500 1.8rem 'JetBrains Mono', monospace; color: var(--alerta); font-variant-numeric: tabular-nums; }
.tiempo.urgente strong { color: var(--error); }
.qr-pago { width: min(260px, 100%); align-self: center; background: #fff; border-radius: 12px; padding: 8px; }
.cuenta { margin: 0; display: flex; flex-direction: column; gap: 8px; }
.cuenta div { display: flex; justify-content: space-between; gap: 12px; }
.cuenta dt { color: var(--suave); }
.cuenta dd { margin: 0; font-family: 'JetBrains Mono', monospace; text-align: right; }
.mini { margin-left: 8px; border: 1px solid var(--linea); background: transparent; color: var(--acento); border-radius: 8px; padding: 2px 8px; font: 600 0.75rem 'Public Sans', sans-serif; cursor: pointer; }
.nota { margin: 0; font-size: 0.88rem; color: var(--suave); }
.archivo { position: relative; border: 1.5px dashed var(--linea); border-radius: 12px; padding: 16px; text-align: center; color: var(--suave); cursor: pointer; }
.archivo input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.boton {
  display: block; text-align: center; text-decoration: none; border: none; border-radius: 12px; padding: 14px 18px;
  font: 700 1rem 'Public Sans', sans-serif; cursor: pointer; background: var(--acento-fuerte); color: #fff; min-height: 48px;
}
.boton:disabled { opacity: 0.45; cursor: default; }
.enlace { border: none; background: none; color: var(--suave); text-decoration: underline; cursor: pointer; font: inherit; font-size: 0.88rem; }
.info p { margin: 0; }
.confirmada { align-items: center; text-align: center; }
.confirmada p { margin: 0; }
.qr { width: min(280px, 100%); background: #fff; border-radius: 14px; padding: 8px; }
.limite b { color: var(--alerta); }
.aviso-error { margin: 0; padding: 12px 14px; border-radius: 12px; background: color-mix(in srgb, var(--error) 14%, transparent); color: var(--error); }
.politicas { background: var(--panel); border: 1px solid var(--linea); border-radius: 14px; padding: 12px 16px; }
.politicas summary { cursor: pointer; font-weight: 600; }
.politicas ul { margin: 10px 0 0; padding-left: 18px; display: flex; flex-direction: column; gap: 6px; color: var(--suave); font-size: 0.9rem; }
.contacto { margin: 0; text-align: center; color: var(--suave); font-size: 0.88rem; }
.vacio { text-align: center; color: var(--suave); padding: 60px 0; }
</style>
