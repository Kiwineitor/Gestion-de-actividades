<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import QRCode from 'qrcode';
import ModalPublico from '../components/ModalPublico.vue';
import { url as urlApi } from '../config.js';
import { escucharPublico } from '../ui/canalPublico';
import { avisoPublico } from '../ui/avisosPublicos';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { marca } from '../ui/marca';
import { recordarReserva, olvidarReserva } from '../ui/reservasGuardadas';
import { guardarBlob } from '../ui/descargas';

const route = useRoute();
const reserva = ref(null);
const error = ref('');
const segundos = ref(0);
const subiendo = ref(false);
const mensaje = ref('');
const archivo = ref(null);
const vistaPrevia = ref('');
const qr = ref('');
const paseActual = ref(0);
const qrPases = ref([]);
const condiciones = ref(false);
const confirmarCancelar = ref(false);
const guardandoQr = ref(false);
let reloj = null;
let sondeo = null;
let soltarCanal = null;

const ESTADOS = {
  BLOQUEADA: { txt: 'Esperando tu pago', clase: 'espera' },
  EN_REVISION: { txt: 'Pago en revisión', clase: 'revision' },
  CONFIRMADA: { txt: 'Reserva confirmada', clase: 'ok' },
  RECHAZADA: { txt: 'Pago rechazado', clase: 'mal' },
  VENCIDA: { txt: 'Tiempo vencido', clase: 'mal' },
  CANCELADA: { txt: 'Reserva cancelada', clase: 'mal' },
};

const evento = computed(() => reserva.value?.evento ?? null);
const estado = computed(() => ESTADOS[reserva.value?.estado] ?? { txt: '', clase: '' });
const minutos = computed(() => {
  const s = Math.max(0, segundos.value);
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
});
const avance = computed(() => {
  const total = (evento.value?.minutosBloqueo || 5) * 60;
  return Math.max(0, Math.min(1, segundos.value / total));
});
const politicas = computed(() => (evento.value?.politicas || '').split('\n').filter(Boolean));
const contacto = computed(() => evento.value?.celularContacto || marca.contacto || '');
const whatsapp = computed(() => enlaceWhatsApp(contacto.value, reserva.value ? `Hola, consulto por mi reserva ${reserva.value.codigo}` : ''));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO')}`;

async function cargar() {
  try {
    const r = await fetch(urlApi(`/api/publico/mesas/reservas/${encodeURIComponent(route.query.reserva)}`));
    if (r.status === 404) olvidarReserva(String(route.query.reserva));
    if (!r.ok) { error.value = 'No encontramos esta reserva. Revisa el código.'; return; }
    const antes = reserva.value?.estado;
    reserva.value = await r.json();
    segundos.value = reserva.value.segundosRestantes || 0;
    recordarReserva(reserva.value);
    if (antes && antes !== reserva.value.estado) avisoPublico(ESTADOS[reserva.value.estado]?.txt || 'Tu reserva cambió');
  } catch {
    error.value = 'Sin conexión. Vuelve a intentar.';
  }
}

watch(() => reserva.value?.estado, async (e) => {
  clearInterval(sondeo);
  if (e === 'EN_REVISION' || e === 'BLOQUEADA') sondeo = setInterval(cargar, 30000);
  if (e === 'CONFIRMADA') await prepararPases();
});

const pases = computed(() => reserva.value?.pases ?? []);
const pase = computed(() => pases.value[paseActual.value] || null);
const urlPdf = computed(() => (reserva.value ? urlApi(`/api/publico/mesas/reservas/${encodeURIComponent(reserva.value.codigo)}/pases.pdf`) : ''));

async function prepararPases() {
  const lista = reserva.value?.pases ?? [];
  if (!lista.length) {
    qr.value = await QRCode.toDataURL(reserva.value.codigo, { width: 320, margin: 1, errorCorrectionLevel: 'M' });
    return;
  }
  qrPases.value = await Promise.all(lista.map((p) => QRCode.toDataURL(p.codigo, { width: 320, margin: 1, errorCorrectionLevel: 'M' })));
  paseActual.value = Math.min(paseActual.value, lista.length - 1);
}

watch(() => JSON.stringify(reserva.value?.pases ?? []), () => { if (reserva.value?.estado === 'CONFIRMADA') prepararPases(); });

watch(() => evento.value?.id, (id) => {
  soltarCanal?.();
  soltarCanal = id ? escucharPublico(`/topic/publico/mesas/${id}`, (cambios) => {
    if (Array.isArray(cambios) && cambios.some((c) => c.codigo === reserva.value?.mesa)) cargar();
  }, { reconectado: cargar }) : null;
});

function elegirArchivo(e) {
  const f = e.target.files?.[0] || null;
  archivo.value = f;
  if (vistaPrevia.value) URL.revokeObjectURL(vistaPrevia.value);
  vistaPrevia.value = f && f.type.startsWith('image/') ? URL.createObjectURL(f) : '';
}

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
  confirmarCancelar.value = false;
  const r = await fetch(urlApi(`/api/publico/mesas/reservas/${encodeURIComponent(reserva.value.codigo)}/cancelar`), { method: 'POST' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) mensaje.value = d.mensaje || 'No se pudo cancelar.';
  await cargar();
}

async function guardarQr() {
  if (!evento.value?.pagoQrUrl) return;
  guardandoQr.value = true;
  try {
    const r = await fetch(urlApi(evento.value.pagoQrUrl));
    if (!r.ok) throw new Error();
    const blob = await r.blob();
    const ext = (blob.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
    await guardarBlob(blob, `qr-pago-${reserva.value.codigo}.${ext}`);
    avisoPublico('QR guardado. Ábrelo desde la app de tu banco y vuelve aquí.');
  } catch {
    window.open(urlApi(evento.value.pagoQrUrl), '_blank', 'noopener');
  } finally {
    guardandoQr.value = false;
  }
}

function alVolver() {
  if (document.visibilityState === 'visible' && reserva.value) cargar();
}

async function copiar(texto, que) {
  try {
    await navigator.clipboard.writeText(texto);
    avisoPublico(`${que} copiado`);
  } catch {
    avisoPublico(texto);
  }
}

onMounted(async () => {
  document.addEventListener('visibilitychange', alVolver);
  window.addEventListener('pageshow', alVolver);
  await cargar();
  reloj = setInterval(() => {
    if (reserva.value?.estado !== 'BLOQUEADA') return;
    segundos.value -= 1;
    if (segundos.value === 0) cargar();
  }, 1000);
});
onUnmounted(() => {
  document.removeEventListener('visibilitychange', alVolver);
  window.removeEventListener('pageshow', alVolver);
  clearInterval(reloj);
  clearInterval(sondeo);
  soltarCanal?.();
  if (vistaPrevia.value) URL.revokeObjectURL(vistaPrevia.value);
});
</script>

<template>
  <div class="mi-reserva">
    <header class="barra">
      <router-link to="/" class="pub-volver">← Inicio</router-link>
      <div v-if="evento" class="titulo">
        <h1>{{ evento.nombre }}</h1>
        <span class="cuando">{{ evento.localNombre }} · {{ evento.fechaTexto }}</span>
      </div>
      <span v-if="reserva" class="estado-chip" :class="estado.clase">{{ estado.txt }}</span>
    </header>

    <p v-if="error" class="vacio">{{ error }} <router-link to="/">Volver al inicio</router-link></p>
    <p v-else-if="!reserva || !evento" class="vacio">Cargando…</p>

    <main v-else class="contenido">
      <section class="tarjeta resumen" :class="estado.clase">
        <div class="codigo">
          <span>Código de reserva</span>
          <button type="button" class="copiable" @click="copiar(reserva.codigo, 'Código')">{{ reserva.codigo }}</button>
        </div>
        <dl class="datos">
          <div><dt>Mesa</dt><dd>{{ reserva.mesa }}<small v-if="reserva.zona"> · {{ reserva.zona }}</small></dd></div>
          <div><dt>Total</dt><dd>{{ bs(reserva.monto) }}</dd></div>
          <div><dt>Titular</dt><dd>{{ reserva.nombre }} {{ reserva.apellidos }}</dd></div>
          <div><dt>Carnet</dt><dd>{{ reserva.ci }}</dd></div>
          <div><dt>Personas</dt><dd>{{ reserva.personas }}<small v-if="reserva.tipoPase"> · {{ reserva.tipoPase }}</small></dd></div>
        </dl>
        <div v-if="reserva.estado === 'BLOQUEADA'" class="tiempo" :class="{ urgente: segundos < 60 }">
          <div class="anillo" :style="{ '--avance': avance }"><strong>{{ minutos }}</strong></div>
          <span>para pagar y adjuntar tu comprobante</span>
        </div>
        <p class="whatsapp-nota">
          <span class="icono" aria-hidden="true">💬</span>
          <span v-if="reserva.estado === 'CONFIRMADA'">Te enviamos tus pases por WhatsApp<template v-if="reserva.celular"> al <b>{{ reserva.celular }}</b></template>; también están aquí abajo.</span>
          <span v-else>Te escribiremos por WhatsApp<template v-if="reserva.celular"> al <b>{{ reserva.celular }}</b></template> cuando el encargado revise tu pago.</span>
        </p>
      </section>

      <section class="tarjeta accion">
        <template v-if="reserva.estado === 'BLOQUEADA'">
          <h2><span class="paso">1</span> Paga {{ bs(reserva.monto) }}</h2>
          <div class="pago">
            <div v-if="evento.pagoQrUrl" class="qr-caja">
              <img :src="urlApi(evento.pagoQrUrl)" alt="QR de pago" class="qr-pago" />
              <button type="button" class="pub-boton chico guardar-qr" :disabled="guardandoQr" @click="guardarQr">{{ guardandoQr ? 'Guardando…' : '⬇ Guardar QR' }}</button>
            </div>
            <dl v-if="evento.pagoCuenta || evento.pagoBanco" class="cuenta">
              <div v-if="evento.pagoBanco"><dt>Banco</dt><dd>{{ evento.pagoBanco }}</dd></div>
              <div v-if="evento.pagoCuenta"><dt>Cuenta</dt><dd><button type="button" class="copiable" @click="copiar(evento.pagoCuenta, 'Número de cuenta')">{{ evento.pagoCuenta }}</button></dd></div>
              <div v-if="evento.pagoTitular"><dt>Titular</dt><dd>{{ evento.pagoTitular }}</dd></div>
              <div><dt>Glosa</dt><dd class="mono">{{ reserva.codigo }}</dd></div>
            </dl>
          </div>
          <p v-if="evento.pagoInstrucciones" class="nota">{{ evento.pagoInstrucciones }}</p>
          <p class="salir-volver">
            <span aria-hidden="true">📱</span>
            <span>¿Pagas desde este celular? Guarda el QR, ábrelo en la app de tu banco y <b>vuelve a esta página</b>: tu reserva queda guardada aquí y también te la enviamos por WhatsApp.</span>
          </p>

          <h2><span class="paso">2</span> Adjunta tu comprobante</h2>
          <label class="archivo" :class="{ lleno: archivo }">
            <input id="m-comprobante" type="file" accept="image/png,image/jpeg,image/webp,application/pdf" @change="elegirArchivo" />
            <img v-if="vistaPrevia" :src="vistaPrevia" alt="" class="miniatura" />
            <span>{{ archivo ? archivo.name : 'Toca para elegir la foto o PDF del pago' }}</span>
          </label>
          <div class="botones">
            <button type="button" class="pub-boton secundario" @click="confirmarCancelar = true">Cancelar reserva</button>
            <button type="button" class="pub-boton" :disabled="!archivo || subiendo" @click="enviarComprobante">{{ subiendo ? 'Enviando…' : 'Enviar comprobante' }}</button>
          </div>
        </template>

        <div v-else-if="reserva.estado === 'EN_REVISION'" class="mensaje-estado">
          <span class="grande" aria-hidden="true">⏳</span>
          <h2>Recibimos tu comprobante</h2>
          <p>El encargado está revisando tu pago. Te escribirá por WhatsApp<template v-if="reserva.celular"> al <b>{{ reserva.celular }}</b></template> y esta página se actualizará sola con tu QR de ingreso.</p>
        </div>

        <div v-else-if="reserva.estado === 'CONFIRMADA'" class="mensaje-estado confirmada">
          <template v-if="pases.length">
            <div class="pase-cab">
              <button type="button" class="flecha" :disabled="paseActual === 0" aria-label="Pase anterior" @click="paseActual--">‹</button>
              <span>Pase <b>{{ paseActual + 1 }}</b> de {{ pases.length }}<template v-if="pase?.tipo"> · {{ pase.tipo }}</template></span>
              <button type="button" class="flecha" :disabled="paseActual >= pases.length - 1" aria-label="Pase siguiente" @click="paseActual++">›</button>
            </div>
            <img v-if="qrPases[paseActual]" :src="qrPases[paseActual]" :alt="`QR del pase ${paseActual + 1}`" class="qr" :class="{ usado: pase?.ingresoEn }" />
            <p v-if="pase?.ingresoEn" class="nota ok">Este pase ya ingresó ✓</p>
            <p>Cada persona muestra su propio pase en la puerta.</p>
            <a :href="urlPdf" target="_blank" rel="noopener" class="pub-boton chico">⬇ Descargar los {{ pases.length }} pases (PDF)</a>
          </template>
          <template v-else>
            <img v-if="qr" :src="qr" alt="Código QR de ingreso" class="qr" />
            <p>Muestra este QR y tu carnet en el ingreso.</p>
          </template>
          <p v-if="evento.horaLimiteTexto" class="nota">Llega antes de las <b>{{ evento.horaLimiteTexto }}</b>.</p>
          <p v-if="reserva.beneficios" class="nota">Incluye: {{ reserva.beneficios }}</p>
          <p v-if="reserva.ingresoEn" class="nota ok">Ingreso registrado ✓</p>
        </div>

        <div v-else class="mensaje-estado">
          <span class="grande" aria-hidden="true">⚠️</span>
          <h2>{{ estado.txt }}</h2>
          <p v-if="reserva.motivo">{{ reserva.motivo }}</p>
          <router-link :to="{ path: '/', query: { evento: evento.id } }" class="pub-boton">Elegir otra mesa</router-link>
        </div>

        <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>
      </section>
    </main>

    <footer v-if="reserva && evento" class="pie">
      <button type="button" class="enlace" @click="condiciones = true">Condiciones de la reserva</button>
      <a v-if="whatsapp" :href="whatsapp" target="_blank" rel="noopener" class="pub-boton whatsapp chico">Consultas por WhatsApp</a>
    </footer>

    <ModalPublico v-if="condiciones" titulo="Condiciones de la reserva" @cerrar="condiciones = false">
      <ul class="politicas"><li v-for="(p, i) in politicas" :key="i">{{ p }}</li></ul>
    </ModalPublico>

    <ModalPublico v-if="confirmarCancelar" titulo="¿Cancelar tu reserva?" ancho="420px" @cerrar="confirmarCancelar = false">
      <p class="nota">La mesa {{ reserva?.mesa }} quedará libre para otra persona.</p>
      <template #pie>
        <button type="button" class="pub-boton secundario" @click="confirmarCancelar = false">No, volver</button>
        <button type="button" class="pub-boton peligro" @click="cancelar">Sí, cancelar</button>
      </template>
    </ModalPublico>
  </div>
</template>

<style scoped>
.mi-reserva {
  height: 100vh; height: 100dvh; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; max-width: 1040px; margin: 0 auto;
  padding: 0 max(16px, env(safe-area-inset-right, 0px)) 0 max(16px, env(safe-area-inset-left, 0px)); box-sizing: border-box;
}
.barra { display: flex; align-items: center; gap: 12px; padding: calc(12px + env(safe-area-inset-top, 0px)) 0 10px; }
.titulo { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.titulo h1 { margin: 0; font-size: clamp(1.05rem, 2.4vw, 1.5rem); line-height: 1.1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cuando { color: var(--pub-suave); font-size: 0.82rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-transform: capitalize; }
.estado-chip { padding: 6px 12px; border-radius: 999px; font-weight: 700; font-size: 0.8rem; white-space: nowrap; background: var(--pub-panel-2); }
.estado-chip.espera { color: var(--pub-alerta); } .estado-chip.revision { color: var(--pub-acento-claro); }
.estado-chip.ok { color: var(--pub-ok); } .estado-chip.mal { color: var(--pub-error); }
.contenido { display: grid; grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr); gap: 14px; min-height: 0; align-content: start; }
.tarjeta { background: rgba(21, 26, 46, 0.82); border: 1px solid var(--pub-linea); border-radius: 18px; padding: 16px; display: flex; flex-direction: column; gap: 12px; min-height: 0; overflow-y: auto; }
.resumen { border-top: 4px solid var(--pub-linea); }
.resumen.espera { border-top-color: var(--pub-alerta); } .resumen.revision { border-top-color: var(--pub-acento); }
.resumen.ok { border-top-color: var(--pub-ok); } .resumen.mal { border-top-color: var(--pub-error); }
.codigo span { display: block; font-size: 0.72rem; color: var(--pub-suave); text-transform: uppercase; letter-spacing: 0.08em; }
.copiable { border: 0; background: none; padding: 0; color: var(--pub-texto); font: 500 1.1rem var(--pub-mono); cursor: copy; text-align: left; }
.copiable:hover { color: var(--pub-acento-claro); }
.datos { margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.datos dt { font-size: 0.7rem; color: var(--pub-suave); text-transform: uppercase; letter-spacing: 0.06em; }
.datos dd { margin: 2px 0 0; font-weight: 700; }
.datos small { color: var(--pub-suave); font-weight: 400; }
.tiempo { display: flex; align-items: center; gap: 12px; color: var(--pub-suave); font-size: 0.86rem; }
.anillo {
  --c: var(--pub-alerta); width: 74px; height: 74px; flex: none; border-radius: 50%; display: grid; place-items: center;
  background: conic-gradient(var(--c) calc(var(--avance) * 360deg), var(--pub-panel-2) 0);
}
.anillo strong { width: 60px; height: 60px; border-radius: 50%; display: grid; place-items: center; background: var(--pub-panel); font: 500 1rem var(--pub-mono); color: var(--c); }
.tiempo.urgente .anillo { --c: var(--pub-error); }
.whatsapp-nota { margin: 0; display: flex; gap: 8px; align-items: flex-start; font-size: 0.84rem; color: var(--pub-suave); padding: 10px 12px; border-radius: 12px; background: color-mix(in srgb, #1fa855 12%, transparent); }
.whatsapp-nota b { color: var(--pub-texto); }
.accion h2 { margin: 0; font-size: 1.05rem; display: flex; align-items: center; gap: 8px; }
.paso { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; background: var(--pub-acento); font: 700 0.8rem 'Public Sans', sans-serif; color: #fff; }
.pago { display: flex; gap: 14px; align-items: flex-start; }
.qr-caja { display: flex; flex-direction: column; align-items: stretch; gap: 6px; flex: none; }
.qr-pago { width: clamp(110px, 20vh, 170px); aspect-ratio: 1; object-fit: contain; background: #fff; border-radius: 12px; padding: 6px; flex: none; box-sizing: border-box; }
.guardar-qr { justify-content: center; }
.salir-volver { margin: 0; display: flex; gap: 8px; align-items: flex-start; font-size: 0.82rem; color: var(--pub-suave); padding: 8px 10px; border-radius: 10px; background: var(--pub-panel-2); }
.salir-volver b { color: var(--pub-texto); }
.cuenta { margin: 0; display: flex; flex-direction: column; gap: 6px; flex: 1; min-width: 0; font-size: 0.9rem; }
.cuenta div { display: flex; justify-content: space-between; gap: 10px; border-bottom: 1px dashed var(--pub-linea); padding-bottom: 4px; }
.cuenta dt { color: var(--pub-suave); }
.cuenta dd { margin: 0; text-align: right; font-weight: 600; word-break: break-all; }
.cuenta .copiable { font-size: 0.95rem; }
.mono { font-family: var(--pub-mono); }
.nota { margin: 0; font-size: 0.84rem; color: var(--pub-suave); }
.nota.ok { color: var(--pub-ok); }
.archivo { position: relative; display: flex; align-items: center; gap: 10px; border: 1.5px dashed var(--pub-linea); border-radius: 12px; padding: 12px; color: var(--pub-suave); cursor: pointer; font-size: 0.9rem; }
.archivo.lleno { border-color: var(--pub-acento-claro); color: var(--pub-texto); }
.archivo input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.miniatura { width: 44px; height: 44px; object-fit: cover; border-radius: 8px; flex: none; }
.botones { display: flex; gap: 10px; }
.botones .pub-boton:last-child { flex: 1; }
.pub-boton.peligro { background: var(--pub-error); }
.mensaje-estado { display: flex; flex-direction: column; align-items: center; text-align: center; gap: 10px; margin: auto 0; }
.mensaje-estado p { margin: 0; color: var(--pub-suave); max-width: 30rem; }
.mensaje-estado b { color: var(--pub-texto); }
.grande { font-size: 2.4rem; }
.qr { width: clamp(140px, 26vh, 230px); background: #fff; border-radius: 14px; padding: 8px; }
.qr.usado { opacity: 0.35; }
.pase-cab { display: flex; align-items: center; gap: 12px; }
.flecha { width: 34px; height: 34px; border-radius: 50%; border: 1px solid var(--pub-linea); background: var(--pub-panel-2); color: var(--pub-texto); font-size: 1.2rem; cursor: pointer; }
.flecha:disabled { opacity: 0.35; cursor: default; }
.aviso-error { margin: 0; padding: 10px 14px; border-radius: 12px; background: color-mix(in srgb, var(--pub-error) 14%, transparent); color: var(--pub-error); font-size: 0.9rem; }
.pie { display: flex; justify-content: space-between; align-items: center; gap: 10px; padding: 10px 0 calc(12px + env(safe-area-inset-bottom, 0px)); }
.enlace { border: 0; background: none; color: var(--pub-suave); text-decoration: underline; cursor: pointer; font: inherit; font-size: 0.86rem; padding: 0; }
.politicas { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 8px; color: var(--pub-suave); font-size: 0.92rem; }
.vacio { text-align: center; color: var(--pub-suave); padding: 60px 0; }
.vacio a { color: var(--pub-acento-claro); }
@media (max-width: 760px) {
  .contenido { grid-template-columns: 1fr; overflow-y: auto; gap: 10px; }
  .tarjeta { overflow: visible; padding: 12px 14px; gap: 10px; }
  .estado-chip { display: none; }
  .datos { grid-template-columns: 1fr 1fr; gap: 6px 10px; }
  .tiempo .anillo { width: 58px; height: 58px; }
  .tiempo .anillo strong { width: 46px; height: 46px; font-size: 0.85rem; }
  .pago { flex-direction: column; align-items: center; }
  .qr-pago { width: min(62vw, 220px); }
  .cuenta { width: 100%; }
  .whatsapp-nota { font-size: 0.8rem; padding: 8px 10px; }
}
</style>
