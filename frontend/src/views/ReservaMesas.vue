<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import PlanoMesas from '../components/PlanoMesas.vue';
import { url as urlApi } from '../config.js';
import { escucharTopicPublico } from '../ws.js';

const route = useRoute();
const router = useRouter();

const evento = ref(null);
const error = ref('');
const elegidaId = ref(null);
const paso = ref('mapa');
const enviando = ref(false);
const mensaje = ref('');
const confirmoDatos = ref(false);
const cliente = reactive({ nombre: '', apellidos: '', ci: '', celular: '', correo: '' });
let cerrarCanal = null;

const mesas = computed(() => evento.value?.mesas ?? []);
const elegida = computed(() => mesas.value.find((m) => m.id === elegidaId.value) || null);
const aspecto = computed(() => {
  const e = evento.value;
  return e?.planoAncho && e?.planoAlto ? e.planoAlto / e.planoAncho : 0.62;
});
const libres = computed(() => mesas.value.filter((m) => m.estado === 'L').length);
const zonas = computed(() => [...new Map(mesas.value.filter((m) => m.zona).map((m) => [m.zona, m.color])).entries()]);
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 0 })}`;
const datosCompletos = computed(() => cliente.nombre.trim() && cliente.apellidos.trim() && cliente.ci.trim()
  && cliente.celular.replace(/\D/g, '').length >= 7 && confirmoDatos.value);

async function cargar() {
  try {
    const r = await fetch(urlApi(`/api/publico/mesas/eventos/${route.query.evento}`));
    if (!r.ok) { error.value = 'Este evento no está disponible.'; return; }
    evento.value = await r.json();
    document.title = `${evento.value.nombre} · Reserva de mesas`;
  } catch {
    error.value = 'No se pudo cargar el evento. Revisa tu conexión.';
  }
}

function aplicar(cambios) {
  if (!evento.value || !Array.isArray(cambios)) return;
  for (const c of cambios) {
    const i = evento.value.mesas.findIndex((m) => m.id === c.id);
    if (c.activa === false) { if (i >= 0) evento.value.mesas.splice(i, 1); continue; }
    if (i >= 0) evento.value.mesas[i] = { ...evento.value.mesas[i], ...c };
    else evento.value.mesas.push(c);
    if (c.id === elegidaId.value && c.estado !== 'L' && !enviando.value) {
      mensaje.value = `La mesa ${c.codigo} acaba de ser reservada por otra persona. Elige otra.`;
      paso.value = 'mapa';
      elegidaId.value = null;
    }
  }
}

function elegir(m) {
  mensaje.value = '';
  if (m.estado !== 'L') { mensaje.value = `La mesa ${m.codigo} no está disponible.`; return; }
  elegidaId.value = m.id;
  paso.value = 'mapa';
}

async function reservar() {
  if (!datosCompletos.value || !elegida.value) return;
  enviando.value = true;
  mensaje.value = '';
  try {
    const r = await fetch(urlApi(`/api/publico/mesas/eventos/${evento.value.id}/reservas`), {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mesaId: elegida.value.id, cliente }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { mensaje.value = d.mensaje || 'No se pudo reservar. Intenta de nuevo.'; return; }
    router.push({ path: '/', query: { reserva: d.codigo } });
  } catch {
    mensaje.value = 'Sin conexión. Intenta de nuevo.';
  } finally {
    enviando.value = false;
  }
}

onMounted(async () => {
  await cargar();
  if (evento.value) cerrarCanal = escucharTopicPublico(`/topic/publico/mesas/${evento.value.id}`, { onMensaje: aplicar, onConectado: cargar });
});
onUnmounted(() => cerrarCanal?.());
</script>

<template>
  <div class="noche">
    <main class="contenedor">
      <p v-if="error" class="vacio">{{ error }}</p>
      <template v-else-if="evento">
        <header class="cab">
          <span class="local">{{ evento.localNombre }}</span>
          <h1>{{ evento.nombre }}</h1>
          <p class="cuando">{{ evento.fechaTexto }}<template v-if="evento.localDireccion"> · {{ evento.localDireccion }}</template></p>
          <p v-if="evento.descripcion" class="desc">{{ evento.descripcion }}</p>
        </header>

        <section class="mapa">
          <div class="mapa-cab">
            <strong>{{ libres }} mesa{{ libres === 1 ? '' : 's' }} libre{{ libres === 1 ? '' : 's' }}</strong>
            <span class="leyenda"><i class="l"></i>Libre <i class="r"></i>Reservada <i class="o"></i>Vendida</span>
          </div>
          <PlanoMesas :mesas="mesas" :plano-url="evento.planoUrl" :aspecto="aspecto" :seleccionada="elegidaId" @elegir="elegir" />
          <div v-if="zonas.length" class="zonas">
            <span v-for="[z, c] in zonas" :key="z"><i :style="{ background: c || '#6d5bd0' }"></i>{{ z }}</span>
          </div>
        </section>

        <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>

        <section v-if="elegida" class="ficha">
          <div class="ficha-cab">
            <div>
              <span class="etq">Mesa</span>
              <h2>{{ elegida.codigo }}<small v-if="elegida.zona"> · {{ elegida.zona }}</small></h2>
            </div>
            <strong class="precio">{{ bs(elegida.precio) }}</strong>
          </div>
          <p class="detalle">Hasta {{ elegida.capacidad }} personas<template v-if="elegida.beneficios"> · {{ elegida.beneficios }}</template></p>
          <button v-if="paso === 'mapa'" class="boton" @click="paso = 'datos'">Reservar esta mesa</button>

          <form v-else class="formulario" @submit.prevent="reservar">
            <div class="campos">
              <label><span>Nombre</span><input id="r-nombre" v-model="cliente.nombre" autocomplete="given-name" maxlength="80" required /></label>
              <label><span>Apellidos</span><input id="r-apellidos" v-model="cliente.apellidos" autocomplete="family-name" maxlength="80" required /></label>
              <label><span>Número de carnet</span><input id="r-ci" v-model="cliente.ci" maxlength="20" required /></label>
              <label><span>Número de celular</span><input id="r-celular" v-model="cliente.celular" inputmode="tel" autocomplete="tel" maxlength="20" required /></label>
              <label class="ancho"><span>Correo</span><input id="r-correo" v-model="cliente.correo" type="email" autocomplete="email" maxlength="120" /></label>
            </div>
            <div class="importante">
              <strong>Revisa tus datos</strong>
              <p>Son importantes para tu reserva y no podrán modificarse después. El titular debe presentar este carnet en el ingreso.</p>
              <label class="check"><input id="r-confirmo" v-model="confirmoDatos" type="checkbox" /> Revisé mis datos y están correctos</label>
            </div>
            <p class="nota">Al continuar, la mesa queda bloqueada <b>{{ evento.minutosBloqueo }} minutos</b> para que pagues y adjuntes tu comprobante.</p>
            <div class="acciones">
              <button type="button" class="boton secundario" :disabled="enviando" @click="paso = 'mapa'">Volver</button>
              <button type="submit" class="boton" :disabled="!datosCompletos || enviando">{{ enviando ? 'Bloqueando…' : 'Bloquear mesa y pagar' }}</button>
            </div>
          </form>
        </section>
        <p v-else class="pista">Toca una mesa libre en el plano para ver su precio y reservarla.</p>

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
@import url('https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,700;12..96,800&family=Public+Sans:wght@400;600&display=swap');
.noche {
  --fondo: #0d0f1c; --panel: #161a2e; --linea: #2a2f4d; --texto: #eef0fb; --suave: #a3a9c7;
  --acento: #b49bff; --acento-fuerte: #8f6dff; --ok: #3fd18f; --alerta: #ffb347; --error: #ff6b8b;
  min-height: 100vh; background: radial-gradient(ellipse at top, #20184a 0%, var(--fondo) 55%); color: var(--texto);
  font-family: 'Public Sans', system-ui, sans-serif; color-scheme: dark;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px));
}
.contenedor { max-width: 760px; margin: 0 auto; padding-block: 28px 56px; display: flex; flex-direction: column; gap: 20px; }
.cab { display: flex; flex-direction: column; gap: 6px; }
.local { font-size: 0.78rem; letter-spacing: 0.14em; text-transform: uppercase; color: var(--acento); font-weight: 600; }
h1 { margin: 0; font: 800 clamp(1.9rem, 6vw, 2.8rem)/1.05 'Bricolage Grotesque', system-ui, sans-serif; text-wrap: balance; }
.cuando { margin: 0; color: var(--suave); text-transform: capitalize; }
.desc { margin: 0; color: var(--suave); }
.mapa { display: flex; flex-direction: column; gap: 10px; }
.mapa-cab { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 0.9rem; }
.leyenda { display: inline-flex; align-items: center; gap: 6px; color: var(--suave); font-size: 0.8rem; }
.leyenda i, .zonas i { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.leyenda .l { background: #1f9d63; } .leyenda .r { background: #d98b0b; margin-left: 6px; } .leyenda .o { background: #d23b5a; margin-left: 6px; }
.zonas { display: flex; flex-wrap: wrap; gap: 12px; font-size: 0.8rem; color: var(--suave); }
.zonas span { display: inline-flex; align-items: center; gap: 6px; }
.ficha { background: var(--panel); border: 1px solid var(--linea); border-radius: 16px; padding: 18px; display: flex; flex-direction: column; gap: 12px; }
.ficha-cab { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.etq { font-size: 0.75rem; letter-spacing: 0.1em; text-transform: uppercase; color: var(--suave); }
h2 { margin: 0; font: 800 1.8rem/1 'Bricolage Grotesque', system-ui, sans-serif; }
h2 small { font: 600 1rem 'Public Sans', sans-serif; color: var(--suave); }
.precio { font: 800 1.5rem 'Bricolage Grotesque', sans-serif; color: var(--acento); white-space: nowrap; }
.detalle { margin: 0; color: var(--suave); }
.boton {
  border: none; border-radius: 12px; padding: 14px 18px; font: 700 1rem 'Public Sans', sans-serif; cursor: pointer;
  background: var(--acento-fuerte); color: #fff; min-height: 48px;
}
.boton:disabled { opacity: 0.45; cursor: default; }
.boton.secundario { background: transparent; border: 1px solid var(--linea); color: var(--texto); }
.formulario { display: flex; flex-direction: column; gap: 14px; }
.campos { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.campos label { display: flex; flex-direction: column; gap: 5px; font-size: 0.82rem; color: var(--suave); }
.campos .ancho { grid-column: 1 / -1; }
.campos input {
  background: var(--fondo); border: 1px solid var(--linea); border-radius: 10px; color: var(--texto);
  padding: 12px; font: 400 1rem 'Public Sans', sans-serif; min-width: 0;
}
.campos input:focus { outline: 2px solid var(--acento); border-color: var(--acento); }
.importante { border: 1px solid color-mix(in srgb, var(--alerta) 50%, transparent); background: color-mix(in srgb, var(--alerta) 10%, transparent); border-radius: 12px; padding: 12px 14px; display: flex; flex-direction: column; gap: 6px; }
.importante strong { color: var(--alerta); }
.importante p { margin: 0; font-size: 0.9rem; }
.check { display: flex; align-items: center; gap: 8px; font-size: 0.9rem; cursor: pointer; }
.check input { width: 18px; height: 18px; accent-color: var(--acento-fuerte); }
.nota { margin: 0; font-size: 0.88rem; color: var(--suave); }
.acciones { display: flex; gap: 10px; }
.acciones .boton:last-child { flex: 1; }
.aviso-error { margin: 0; padding: 12px 14px; border-radius: 12px; background: color-mix(in srgb, var(--error) 14%, transparent); color: var(--error); }
.pista { margin: 0; text-align: center; color: var(--suave); }
.politicas { background: var(--panel); border: 1px solid var(--linea); border-radius: 14px; padding: 12px 16px; }
.politicas summary { cursor: pointer; font-weight: 600; }
.politicas ul { margin: 10px 0 0; padding-left: 18px; display: flex; flex-direction: column; gap: 6px; color: var(--suave); font-size: 0.9rem; }
.contacto { margin: 0; text-align: center; color: var(--suave); font-size: 0.88rem; }
.vacio { text-align: center; color: var(--suave); padding: 60px 0; }
@media (max-width: 520px) { .campos { grid-template-columns: 1fr; } }
</style>
