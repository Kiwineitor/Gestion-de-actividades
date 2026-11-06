<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PlanoMesas from '../components/PlanoMesas.vue';
import ModalPublico from '../components/ModalPublico.vue';
import { url as urlApi } from '../config.js';
import { escucharPublico, alCambiarPublico } from '../ui/canalPublico';
import { avisoPublico } from '../ui/avisosPublicos';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { marca } from '../ui/marca';
import ReservaPendiente from '../components/ReservaPendiente.vue';

const route = useRoute();
const router = useRouter();

const evento = ref(null);
const error = ref('');
const seleccion = ref([]);
const ficha = ref(false);
const enviando = ref(false);
const mensaje = ref('');
const confirmoDatos = ref(false);
const condiciones = ref(false);
const info = ref(false);
const ampliado = ref(false);
const resaltadas = ref(new Set());
const cliente = reactive({ nombre: '', ci: '', celular: '', correo: '' });
const soltar = [];
const mesas = computed(() => evento.value?.mesas ?? []);
const elegidas = computed(() => seleccion.value.map((id) => mesas.value.find((m) => m.id === id)).filter(Boolean));
const total = computed(() => elegidas.value.reduce((monto, m) => monto + Number(m.precio), 0));
const manillas = computed(() => elegidas.value.reduce((cantidad, m) => cantidad + m.capacidad, 0));
const aspecto = computed(() => {
  const e = evento.value;
  return e?.planoAncho && e?.planoAlto ? e.planoAlto / e.planoAncho : 0.62;
});
const libres = computed(() => mesas.value.filter((m) => m.estado === 'L').length);
const zonas = computed(() => [...new Map((evento.value?.mesas ?? []).filter((m) => m.zona).map((m) => [m.zona, m.color])).entries()]);
const politicas = computed(() => (evento.value?.politicas || '').split('\n').filter(Boolean));
const contacto = computed(() => evento.value?.celularContacto || marca.contacto || '');
const whatsapp = computed(() => enlaceWhatsApp(contacto.value, evento.value ? `Hola, tengo una consulta sobre ${evento.value.nombre}` : ''));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 0 })}`;
const datosCompletos = computed(() => cliente.nombre.trim() && cliente.ci.trim()
  && cliente.celular.replace(/\D/g, '').length >= 7 && confirmoDatos.value);

async function cargar() {
  try {
    const r = await fetch(urlApi(`/api/publico/mesas/eventos/${route.query.evento}`));
    if (!r.ok) { error.value = 'Este evento no está disponible.'; return; }
    evento.value = await r.json();
    error.value = '';
    document.title = `${evento.value.nombre} · Reserva de mesas`;
  } catch {
    error.value = 'No se pudo cargar el evento. Revisa tu conexión.';
  }
}

function resaltar(id) {
  resaltadas.value = new Set([...resaltadas.value, id]);
  setTimeout(() => {
    const s = new Set(resaltadas.value);
    s.delete(id);
    resaltadas.value = s;
  }, 6000);
}

function aplicar(cambios) {
  if (!evento.value || !Array.isArray(cambios)) return;
  for (const c of cambios) {
    const i = evento.value.mesas.findIndex((m) => m.id === c.id);
    const antes = i >= 0 ? evento.value.mesas[i] : null;
    if (c.activa === false) {
      if (i >= 0) evento.value.mesas.splice(i, 1);
    } else if (i >= 0) {
      evento.value.mesas[i] = { ...antes, ...c };
    } else {
      evento.value.mesas.push(c);
    }
    if (antes && antes.estado !== 'L' && c.estado === 'L' && c.activa !== false) {
      avisoPublico(`La mesa ${c.codigo} quedó libre`, 'libre');
      resaltar(c.id);
    }
    if (!enviando.value && seleccion.value.includes(c.id) && (c.estado !== 'L' || c.activa === false)) {
      seleccion.value = seleccion.value.filter((id) => id !== c.id);
      ficha.value = false;
      mensaje.value = `La mesa ${c.codigo} ya no está disponible. Revisa tu selección.`;
    }
  }
}

function elegir(m) {
  mensaje.value = '';
  if (seleccion.value.includes(m.id)) {
    seleccion.value = seleccion.value.filter((id) => id !== m.id);
    return;
  }
  if (m.estado !== 'L') { avisoPublico(`La mesa ${m.codigo} no está disponible.`); return; }
  if (seleccion.value.length === 2) { avisoPublico('Puedes seleccionar hasta dos mesas.'); return; }
  seleccion.value = [...seleccion.value, m.id];
}

async function reservar() {
  if (!datosCompletos.value || !elegidas.value.length) return;
  enviando.value = true;
  mensaje.value = '';
  try {
    const imagen = new Image();
    imagen.src = urlApi(evento.value.pagoQrUrl);
    await imagen.decode();
    const r = await fetch(urlApi(`/api/publico/mesas/eventos/${evento.value.id}/reservas`), {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mesaIds: seleccion.value, cliente }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { mensaje.value = d.mensaje || 'No se pudo reservar. Intenta de nuevo.'; return; }
    ficha.value = false;
    router.push({ path: '/', query: { reserva: d.codigo } });
  } catch {
    mensaje.value = 'Sin conexión. Intenta de nuevo.';
  } finally {
    enviando.value = false;
  }
}

onMounted(async () => {
  await cargar();
  if (!evento.value) return;
  soltar.push(escucharPublico(`/topic/publico/mesas/${evento.value.id}`, aplicar, { reconectado: cargar }));
  soltar.push(alCambiarPublico((m) => { if (m?.tipo === 'evento' && m.id === evento.value?.id) cargar(); }));
});
onUnmounted(() => {
  soltar.forEach((f) => f());
});
</script>

<template>
  <div class="evento">
    <header class="barra">
      <router-link to="/" class="pub-volver" @click="alSalir">← Inicio</router-link>
      <div v-if="evento" class="titulo">
        <h1>{{ evento.nombre }}</h1>
        <span class="cuando">{{ evento.localNombre }} · {{ evento.fechaTexto }}</span>
      </div>
      <div v-if="evento" class="acciones-barra">
        <button type="button" class="pub-boton secundario chico" @click="info = true">Información</button>
        <a v-if="whatsapp" :href="whatsapp" target="_blank" rel="noopener" class="pub-boton whatsapp chico" aria-label="Consultas por WhatsApp">Consultas</a>
      </div>
      <ReservaPendiente class="pendiente-barra" :evento-id="route.query.evento" />
    </header>

    <p v-if="error" class="vacio">{{ error }} <router-link to="/">Volver al inicio</router-link></p>
    <p v-else-if="!evento" class="vacio">Cargando…</p>

    <main v-else class="contenido">
      <section class="plano">
        <div class="estado-plano">
          <span class="pub-vivo"><b>{{ libres }}</b>&nbsp;libre{{ libres === 1 ? '' : 's' }}</span>
          <button type="button" class="ampliar" @click="ampliado = !ampliado">{{ ampliado ? 'Ajustar plano' : 'Ampliar plano' }}</button>
          <span class="leyenda"><i class="l"></i>Libre <i class="r"></i>Apartada <i class="o"></i>Vendida</span>
        </div>
        <div class="lienzo" :class="{ ampliado }" :style="{ '--aspecto': aspecto }">
          <div class="ajuste">
            <PlanoMesas :mesas="mesas" :elementos="evento.elementos || []" :plano-url="evento.planoUrl" :aspecto="aspecto" :seleccionada="seleccion"
                        :resaltadas="resaltadas" @elegir="elegir" />
          </div>
        </div>
        <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>
        <p v-else class="pista">Selecciona hasta dos mesas libres. Toca una mesa seleccionada para quitarla.</p>
        <p v-if="!evento.pagoQrUrl" class="pista">La reserva en línea estará disponible cuando el local configure el QR de pago.</p>
        <div v-if="elegidas.length" class="resumen">
          <div v-for="m in elegidas" :key="m.id"><span>Mesa {{ m.codigo }}</span><strong>{{ bs(m.precio) }}</strong></div>
          <button type="button" class="pub-boton" :disabled="!evento.pagoQrUrl" @click="mensaje = ''; ficha = true">Continuar · {{ bs(total) }}</button>
        </div>
      </section>

      <aside class="lateral">
        <p v-if="evento.descripcion" class="desc">{{ evento.descripcion }}</p>
        <dl class="datos">
          <div><dt>Lugar</dt><dd>{{ evento.localNombre }}<template v-if="evento.localDireccion"><br /><small>{{ evento.localDireccion }}</small></template></dd></div>
          <div><dt>Fecha</dt><dd class="cap">{{ evento.fechaTexto }}</dd></div>
          <div v-if="evento.horaLimiteTexto"><dt>Llegada hasta</dt><dd>{{ evento.horaLimiteTexto }}</dd></div>
          <div><dt>Bloqueo para pagar</dt><dd>{{ evento.minutosBloqueo }} minutos</dd></div>
        </dl>
        <div v-if="zonas.length" class="zonas">
          <span v-for="[z, c] in zonas" :key="z"><i :style="{ background: c || '#6d5bd0' }"></i>{{ z }}</span>
        </div>
        <button type="button" class="pub-boton secundario" @click="condiciones = true">Condiciones de la reserva</button>
      </aside>
    </main>

    <ModalPublico v-if="ficha && elegidas.length" titulo="Datos de reserva" ancho="620px" :cerrable="!enviando" @cerrar="ficha = false">
      <div class="resumen">
        <div v-for="m in elegidas" :key="m.id">
          <span>Mesa {{ m.codigo }}<template v-if="m.zona"> · {{ m.zona }}</template></span>
          <strong>{{ bs(m.precio) }} · {{ m.capacidad }} manillas</strong>
          <small v-if="m.beneficios">{{ m.beneficios }}</small>
        </div>
        <div><span>Total</span><strong class="precio">{{ bs(total) }} · {{ manillas }} manillas</strong></div>
      </div>

      <form id="form-reserva" class="formulario" @submit.prevent="reservar">
        <div class="campos">
          <label class="pub-campo"><span>Nombre completo</span><input id="r-nombre" v-model="cliente.nombre" autocomplete="name" maxlength="80" required /></label>
          <label class="pub-campo"><span>Número de carnet</span><input id="r-ci" v-model="cliente.ci" maxlength="20" required /></label>
          <label class="pub-campo"><span>Celular (WhatsApp)</span><input id="r-celular" v-model="cliente.celular" inputmode="tel" autocomplete="tel" maxlength="20" required /></label>
          <label class="pub-campo"><span>Correo (opcional)</span><input id="r-correo" v-model="cliente.correo" type="email" autocomplete="email" maxlength="120" /></label>
        </div>
        <label class="check">
          <input id="r-confirmo" v-model="confirmoDatos" type="checkbox" />
          <span>Revisé mis datos: no podrán cambiarse y el titular presentará este carnet para retirar el vale y las manillas.</span>
        </label>
        <p class="nota">Al abrir el QR de pago tendrás <b>{{ evento.minutosBloqueo }} min</b> para pagar y adjuntar el comprobante. Te avisaremos por WhatsApp.</p>
        <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>
      </form>

      <template #pie>
        <button type="button" class="pub-boton secundario" :disabled="enviando" @click="ficha = false">Cambiar selección</button>
        <button type="submit" form="form-reserva" class="pub-boton" :disabled="!datosCompletos || enviando">
          {{ enviando ? 'Bloqueando…' : `Ver QR de pago · ${bs(total)}` }}
        </button>
      </template>
    </ModalPublico>

    <ModalPublico v-if="condiciones" titulo="Condiciones de la reserva" @cerrar="condiciones = false">
      <ul class="politicas"><li v-for="(p, i) in politicas" :key="i">{{ p }}</li></ul>
    </ModalPublico>

    <ModalPublico v-if="info && evento" :titulo="evento.nombre" @cerrar="info = false">
      <p v-if="evento.descripcion" class="desc">{{ evento.descripcion }}</p>
      <dl class="datos">
        <div><dt>Lugar</dt><dd>{{ evento.localNombre }}<template v-if="evento.localDireccion"> · {{ evento.localDireccion }}</template></dd></div>
        <div><dt>Fecha</dt><dd class="cap">{{ evento.fechaTexto }}</dd></div>
        <div v-if="evento.horaLimiteTexto"><dt>Llegada hasta</dt><dd>{{ evento.horaLimiteTexto }}</dd></div>
        <div v-if="contacto"><dt>Consultas</dt><dd>{{ contacto }}</dd></div>
      </dl>
      <div v-if="zonas.length" class="zonas">
        <span v-for="[z, c] in zonas" :key="z"><i :style="{ background: c || '#6d5bd0' }"></i>{{ z }}</span>
      </div>
      <button type="button" class="pub-boton secundario" @click="info = false; condiciones = true">Ver condiciones</button>
    </ModalPublico>
  </div>
</template>

<style scoped>
.evento {
  height: 100vh; height: 100dvh; display: grid; grid-template-rows: auto minmax(0, 1fr); max-width: 1280px; margin: 0 auto;
  padding: 0 max(16px, env(safe-area-inset-right, 0px)) 0 max(16px, env(safe-area-inset-left, 0px)); box-sizing: border-box;
}
.barra { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: calc(12px + env(safe-area-inset-top, 0px)) 0 10px; }
.pendiente-barra { flex-basis: 100%; }
.titulo { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.titulo h1 { margin: 0; font-size: clamp(1.15rem, 2.6vw, 1.7rem); line-height: 1.1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cuando { color: var(--pub-suave); font-size: 0.85rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; text-transform: capitalize; }
.acciones-barra { display: flex; gap: 8px; }
.contenido { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; min-height: 0; padding-bottom: calc(14px + env(safe-area-inset-bottom, 0px)); }
.plano { display: flex; flex-direction: column; gap: 10px; min-height: 0; }
.estado-plano { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 0.85rem; }
.estado-plano b { color: var(--pub-ok); }
.leyenda { display: inline-flex; align-items: center; gap: 6px; color: var(--pub-suave); font-size: 0.78rem; flex-wrap: wrap; }
.leyenda i, .zonas i { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.leyenda i:not(:first-child) { margin-left: 6px; }
.leyenda .l { background: #1f9d63; } .leyenda .p { background: #2b8fd6; } .leyenda .r { background: #d98b0b; } .leyenda .o { background: #d23b5a; }
.lienzo { flex: 1; min-height: 0; container-type: size; display: grid; place-items: center; }
.ajuste { width: min(100cqw, calc(100cqh / var(--aspecto))); }
.ampliar { display: none; border: 1px solid var(--pub-linea); background: var(--pub-panel); color: var(--pub-texto); border-radius: 999px; padding: 5px 12px; font: 600 0.8rem 'Public Sans', sans-serif; cursor: pointer; }
.lienzo.ampliado { overflow: auto; place-items: start; overscroll-behavior: contain; border-radius: 14px; }
.lienzo.ampliado .ajuste { width: max(220cqw, calc(100cqh / var(--aspecto))); }
.pista { margin: 0; text-align: center; color: var(--pub-suave); font-size: 0.85rem; }
.aviso-error { margin: 0; padding: 10px 14px; border-radius: 12px; background: color-mix(in srgb, var(--pub-error) 14%, transparent); color: var(--pub-error); font-size: 0.9rem; }
.lateral {
  display: flex; flex-direction: column; gap: 14px; min-height: 0; overflow-y: auto; padding: 16px; border-radius: 18px;
  background: rgba(21, 26, 46, 0.78); border: 1px solid var(--pub-linea);
}
.desc { margin: 0; color: var(--pub-suave); white-space: pre-line; }
.datos { margin: 0; display: flex; flex-direction: column; gap: 10px; }
.datos dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--pub-suave); }
.datos dd { margin: 2px 0 0; font-weight: 600; }
.datos small { color: var(--pub-suave); font-weight: 400; }
.cap { text-transform: capitalize; }
.zonas { display: flex; flex-wrap: wrap; gap: 10px; font-size: 0.82rem; color: var(--pub-suave); }
.zonas span { display: inline-flex; align-items: center; gap: 6px; }
.lateral .pub-boton { margin-top: auto; }
.vacio { text-align: center; color: var(--pub-suave); padding: 60px 0; }
.vacio a { color: var(--pub-acento-claro); }
.ficha-cab { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.ficha-cab h2 { margin: 0; font-size: 1.7rem; line-height: 1; }
.resumen { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 12px 14px; border-radius: 14px; background: var(--pub-panel-2); }
.resumen span { display: block; font-size: 0.72rem; color: var(--pub-suave); text-transform: uppercase; letter-spacing: 0.06em; }
.resumen strong { font-size: 1rem; }
.resumen .ancho { grid-column: 1 / -1; }
.precio { font: 800 1.35rem var(--pub-titulo); color: var(--pub-acento-claro); }
.apartada { margin: 0; font-size: 0.82rem; color: var(--pub-proceso); }
.formulario { display: flex; flex-direction: column; gap: 12px; }
.campos { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.campos .ancho { grid-column: 1 / -1; }
.check { display: flex; align-items: flex-start; gap: 10px; font-size: 0.88rem; cursor: pointer; padding: 10px 12px; border-radius: 12px; border: 1px solid color-mix(in srgb, var(--pub-alerta) 45%, transparent); background: color-mix(in srgb, var(--pub-alerta) 8%, transparent); }
.check input { width: 18px; height: 18px; margin-top: 2px; accent-color: var(--pub-acento); flex: none; }
.nota { margin: 0; font-size: 0.84rem; color: var(--pub-suave); }
.politicas { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 8px; color: var(--pub-suave); font-size: 0.92rem; }
@media (max-width: 900px) {
  .contenido { grid-template-columns: 1fr; }
  .lateral { display: none; }
}
@media (max-width: 560px) {
  .barra { flex-wrap: wrap; row-gap: 6px; }
  .titulo { order: 3; flex-basis: 100%; }
  .acciones-barra { margin-left: auto; }
  .leyenda { display: none; }
  .ampliar { display: inline-block; }
  .campos { grid-template-columns: 1fr; gap: 8px; }
}
</style>
