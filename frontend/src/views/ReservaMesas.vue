<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PlanoMesas from '../components/PlanoMesas.vue';
import ModalPublico from '../components/ModalPublico.vue';
import { url as urlApi } from '../config.js';
import { escucharPublico, alCambiarPublico } from '../ui/canalPublico';
import { avisoPublico } from '../ui/avisosPublicos';
import { tokenPublico, enlaceWhatsApp } from '../ui/tokenPublico';
import { marca } from '../ui/marca';

const route = useRoute();
const router = useRouter();
const token = tokenPublico();

const evento = ref(null);
const error = ref('');
const elegidaId = ref(null);
const tomando = ref(null);
const enviando = ref(false);
const mensaje = ref('');
const confirmoDatos = ref(false);
const condiciones = ref(false);
const info = ref(false);
const ampliado = ref(false);
const resaltadas = ref(new Set());
const cliente = reactive({ nombre: '', apellidos: '', ci: '', celular: '', correo: '' });
const soltar = [];
let latido = null;
let propia = null;

const mesas = computed(() => (evento.value?.mesas ?? []).map((m) => (
  m.estado === 'L' && m.enProceso && m.id !== elegidaId.value ? { ...m, estado: 'P' } : m
)));
const elegida = computed(() => mesas.value.find((m) => m.id === elegidaId.value) || null);
const aspecto = computed(() => {
  const e = evento.value;
  return e?.planoAncho && e?.planoAlto ? e.planoAlto / e.planoAncho : 0.62;
});
const libres = computed(() => mesas.value.filter((m) => m.estado === 'L').length);
const enProceso = computed(() => mesas.value.filter((m) => m.estado === 'P').length);
const zonas = computed(() => [...new Map((evento.value?.mesas ?? []).filter((m) => m.zona).map((m) => [m.zona, m.color])).entries()]);
const politicas = computed(() => (evento.value?.politicas || '').split('\n').filter(Boolean));
const contacto = computed(() => evento.value?.celularContacto || marca.contacto || '');
const whatsapp = computed(() => enlaceWhatsApp(contacto.value, evento.value ? `Hola, tengo una consulta sobre ${evento.value.nombre}` : ''));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 0 })}`;
const datosCompletos = computed(() => cliente.nombre.trim() && cliente.apellidos.trim() && cliente.ci.trim()
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
    if (c.activa === false) { if (i >= 0) evento.value.mesas.splice(i, 1); continue; }
    const antes = i >= 0 ? evento.value.mesas[i] : null;
    if (i >= 0) evento.value.mesas[i] = { ...antes, ...c };
    else evento.value.mesas.push(c);
    const ocupadaAntes = antes && (antes.estado !== 'L' || antes.enProceso);
    if (ocupadaAntes && c.estado === 'L' && !c.enProceso && c.id !== elegidaId.value && c.id !== propia) {
      avisoPublico(`La mesa ${c.codigo} quedó libre`, 'libre');
      resaltar(c.id);
    }
    if (c.id === elegidaId.value && c.estado !== 'L' && !enviando.value) {
      cerrarFicha(false);
      mensaje.value = `La mesa ${c.codigo} acaba de ser reservada por otra persona. Elige otra.`;
    }
  }
}

async function apartar(id) {
  const r = await fetch(urlApi(`/api/publico/mesas/eventos/${evento.value.id}/mesas/${id}/atencion`), {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token }),
  });
  return r.json().catch(() => ({ ok: false }));
}

function liberar(id) {
  if (!id || !evento.value) return;
  const destino = urlApi(`/api/publico/mesas/eventos/${evento.value.id}/mesas/${id}/soltar`);
  try {
    if (navigator.sendBeacon?.(destino, new Blob([token], { type: 'text/plain' }))) return;
  } catch (_) {
    return;
  }
  fetch(destino, { method: 'POST', body: token, headers: { 'Content-Type': 'text/plain' }, keepalive: true }).catch(() => {});
}

async function elegir(m) {
  mensaje.value = '';
  if (m.estado === 'P') { avisoPublico(`Alguien está comprando la mesa ${m.codigo}. Elige otra o espera un momento.`); return; }
  if (m.estado !== 'L') { avisoPublico(`La mesa ${m.codigo} no está disponible.`); return; }
  if (tomando.value) return;
  tomando.value = m.id;
  try {
    const d = await apartar(m.id);
    if (!d.ok) { avisoPublico(d.mensaje || 'No se pudo apartar la mesa.', 'error'); return; }
    elegidaId.value = m.id;
    confirmoDatos.value = false;
    clearInterval(latido);
    latido = setInterval(async () => {
      if (!elegidaId.value) return;
      const res = await apartar(elegidaId.value).catch(() => null);
      if (res && !res.ok) { cerrarFicha(false); mensaje.value = res.mensaje; }
    }, 45000);
  } catch {
    avisoPublico('Sin conexión. Intenta de nuevo.', 'error');
  } finally {
    tomando.value = null;
  }
}

function cerrarFicha(soltarMesa = true) {
  clearInterval(latido);
  propia = elegidaId.value;
  if (soltarMesa) liberar(elegidaId.value);
  elegidaId.value = null;
}

async function reservar() {
  if (!datosCompletos.value || !elegida.value) return;
  enviando.value = true;
  mensaje.value = '';
  try {
    const r = await fetch(urlApi(`/api/publico/mesas/eventos/${evento.value.id}/reservas`), {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mesaId: elegida.value.id, cliente, token }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { mensaje.value = d.mensaje || 'No se pudo reservar. Intenta de nuevo.'; return; }
    clearInterval(latido);
    elegidaId.value = null;
    router.push({ path: '/', query: { reserva: d.codigo } });
  } catch {
    mensaje.value = 'Sin conexión. Intenta de nuevo.';
  } finally {
    enviando.value = false;
  }
}

function alSalir() {
  if (elegidaId.value) liberar(elegidaId.value);
}

onMounted(async () => {
  await cargar();
  if (!evento.value) return;
  soltar.push(escucharPublico(`/topic/publico/mesas/${evento.value.id}`, aplicar, { reconectado: cargar }));
  soltar.push(alCambiarPublico((m) => { if (m?.tipo === 'evento' && m.id === evento.value?.id) cargar(); }));
  window.addEventListener('pagehide', alSalir);
});
onUnmounted(() => {
  alSalir();
  clearInterval(latido);
  soltar.forEach((f) => f());
  window.removeEventListener('pagehide', alSalir);
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
    </header>

    <p v-if="error" class="vacio">{{ error }} <router-link to="/">Volver al inicio</router-link></p>
    <p v-else-if="!evento" class="vacio">Cargando…</p>

    <main v-else class="contenido">
      <section class="plano">
        <div class="estado-plano">
          <span class="pub-vivo"><b>{{ libres }}</b>&nbsp;libre{{ libres === 1 ? '' : 's' }}<template v-if="enProceso"> · {{ enProceso }} en proceso</template></span>
          <button type="button" class="ampliar" @click="ampliado = !ampliado">{{ ampliado ? 'Ajustar plano' : 'Ampliar plano' }}</button>
          <span class="leyenda"><i class="l"></i>Libre <i class="p"></i>En proceso <i class="r"></i>Reservada <i class="o"></i>Vendida</span>
        </div>
        <div class="lienzo" :class="{ ampliado }" :style="{ '--aspecto': aspecto }">
          <div class="ajuste">
            <PlanoMesas :mesas="mesas" :plano-url="evento.planoUrl" :aspecto="aspecto" :seleccionada="elegidaId"
                        :resaltadas="resaltadas" :ocupada="tomando" @elegir="elegir" />
          </div>
        </div>
        <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>
        <p v-else class="pista">Toca una mesa libre para ver su precio y reservarla.</p>
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

    <ModalPublico v-if="elegida" ancho="620px" :cerrable="!enviando" @cerrar="cerrarFicha()">
      <template #cabecera>
        <div class="ficha-cab">
          <span class="pub-chip" :style="{ color: elegida.color || undefined }">{{ elegida.zona || 'Mesa' }}</span>
          <h2>Mesa {{ elegida.codigo }}</h2>
        </div>
      </template>

      <div class="resumen">
        <div><span>Precio</span><strong class="precio">{{ bs(elegida.precio) }}</strong></div>
        <div><span>Capacidad</span><strong>{{ elegida.capacidad }} personas</strong></div>
        <div v-if="elegida.beneficios" class="ancho"><span>Incluye</span><strong>{{ elegida.beneficios }}</strong></div>
      </div>
      <p class="apartada">Esta mesa está apartada para ti mientras completas tus datos.</p>

      <form id="form-reserva" class="formulario" @submit.prevent="reservar">
        <div class="campos">
          <label class="pub-campo"><span>Nombre</span><input id="r-nombre" v-model="cliente.nombre" autocomplete="given-name" maxlength="80" required /></label>
          <label class="pub-campo"><span>Apellidos</span><input id="r-apellidos" v-model="cliente.apellidos" autocomplete="family-name" maxlength="80" required /></label>
          <label class="pub-campo"><span>Número de carnet</span><input id="r-ci" v-model="cliente.ci" maxlength="20" required /></label>
          <label class="pub-campo"><span>Celular (WhatsApp)</span><input id="r-celular" v-model="cliente.celular" inputmode="tel" autocomplete="tel" maxlength="20" required /></label>
          <label class="pub-campo ancho"><span>Correo (opcional)</span><input id="r-correo" v-model="cliente.correo" type="email" autocomplete="email" maxlength="120" /></label>
        </div>
        <label class="check">
          <input id="r-confirmo" v-model="confirmoDatos" type="checkbox" />
          <span>Revisé mis datos: no podrán cambiarse y el titular presentará este carnet en el ingreso.</span>
        </label>
        <p class="nota">Al continuar, la mesa queda bloqueada <b>{{ evento.minutosBloqueo }} min</b> para que pagues y adjuntes tu comprobante. Te avisaremos por WhatsApp.</p>
        <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>
      </form>

      <template #pie>
        <button type="button" class="pub-boton secundario" :disabled="enviando" @click="cerrarFicha()">Elegir otra</button>
        <button type="submit" form="form-reserva" class="pub-boton" :disabled="!datosCompletos || enviando">
          {{ enviando ? 'Bloqueando…' : `Reservar y pagar ${bs(elegida.precio)}` }}
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
.barra { display: flex; align-items: center; gap: 12px; padding: calc(12px + env(safe-area-inset-top, 0px)) 0 10px; }
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
