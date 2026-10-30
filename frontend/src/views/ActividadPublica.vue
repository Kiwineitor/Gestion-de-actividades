<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import ModalPublico from '../components/ModalPublico.vue';
import { url as urlApi } from '../config.js';
import { marca, cargarMarca } from '../ui/marca';
import { alCambiarPublico } from '../ui/canalPublico';
import { avisoPublico } from '../ui/avisosPublicos';
import { enlaceWhatsApp } from '../ui/tokenPublico';

const datos = ref(null);
const error = ref('');
const planoAbierto = ref(false);
const formAbierto = ref(false);
const descripcionAbierta = ref(false);
const form = reactive({ nombreCompleto: '', celular: '', empresa: '', rubro: '', categoriaId: '' });
const enviando = ref(false);
const respuesta = ref(null);
let soltarCanal = null;
let sondeo = null;

const actividad = computed(() => datos.value?.edicion ?? null);
const categorias = computed(() => {
  const stats = new Map((datos.value?.estadisticasPorCategoria ?? []).map((s) => [s.categoriaId, s]));
  return (datos.value?.categorias ?? []).map((c) => ({ ...c, stats: stats.get(c.id) }));
});
const stats = computed(() => datos.value?.estadisticas ?? null);
const planoUrl = computed(() => {
  const p = datos.value?.plano;
  return p?.propio && p.url ? urlApi(p.url) : null;
});
const portada = computed(() => (actividad.value?.portadaUrl ? urlApi(actividad.value.portadaUrl) : null));
const recibe = computed(() => actividad.value?.recibeInteresados && categorias.value.length);
const contacto = computed(() => actividad.value?.contacto || marca.contacto || '');
const whatsapp = computed(() => enlaceWhatsApp(contacto.value, actividad.value ? `Hola, consulto por ${actividad.value.nombre}` : ''));
const formListo = computed(() => form.nombreCompleto.trim() && form.empresa.trim() && form.rubro.trim()
  && /^\+?\d{7,15}$/.test(form.celular.trim()) && form.categoriaId);
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;

async function cargar() {
  try {
    const r = await fetch(urlApi('/api/publico/feria'));
    if (!r.ok) throw new Error();
    datos.value = await r.json();
    error.value = datos.value?.edicion ? '' : 'No hay una actividad publicada en este momento.';
    if (datos.value?.edicion) document.title = `${datos.value.edicion.nombre} · ${marca.nombre}`;
  } catch {
    if (!datos.value) error.value = 'No se pudo cargar la información. Revisa tu conexión.';
  }
}

function participar(categoriaId = '') {
  respuesta.value = null;
  form.categoriaId = categoriaId;
  formAbierto.value = true;
}

async function enviar() {
  if (!formListo.value || enviando.value) return;
  enviando.value = true;
  respuesta.value = null;
  try {
    const r = await fetch(urlApi('/api/publico/interesados-stand'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nombreCompleto: form.nombreCompleto.trim(),
        celular: form.celular.trim(),
        empresa: form.empresa.trim(),
        rubro: form.rubro.trim(),
        categoriaId: Number(form.categoriaId),
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.mensaje || 'No se pudo enviar tu solicitud.');
    formAbierto.value = false;
    avisoPublico('Recibimos tu solicitud. Te escribiremos por WhatsApp.', 'libre', 6000);
    Object.assign(form, { nombreCompleto: '', celular: '', empresa: '', rubro: '', categoriaId: '' });
  } catch (e) {
    respuesta.value = e.message || 'No se pudo enviar tu solicitud.';
  } finally {
    enviando.value = false;
  }
}

onMounted(() => {
  cargarMarca();
  cargar();
  soltarCanal = alCambiarPublico((m) => { if (m?.tipo === 'actividad') cargar(); }, { reconectado: cargar });
  sondeo = setInterval(cargar, 60000);
});
onUnmounted(() => { soltarCanal?.(); clearInterval(sondeo); });
</script>

<template>
  <div class="pagina">
    <header class="barra">
      <router-link to="/" class="pub-volver">← Inicio</router-link>
      <span class="marca">{{ marca.nombre }}</span>
      <a v-if="whatsapp" :href="whatsapp" target="_blank" rel="noopener" class="pub-boton whatsapp chico">Consultas</a>
    </header>

    <p v-if="error" class="estado">{{ error }} <router-link to="/">Volver al inicio</router-link></p>
    <p v-else-if="!datos" class="estado">Cargando…</p>

    <main v-else-if="actividad" class="contenido">
      <section class="hero" :style="portada ? { backgroundImage: `linear-gradient(90deg, rgba(11,13,26,.92) 0%, rgba(11,13,26,.55) 60%, rgba(11,13,26,.25)), url(${portada})` } : null">
        <div class="hero-txt">
          <span v-if="actividad.lugar" class="lugar">{{ actividad.lugar }}</span>
          <h1>{{ actividad.nombre }}</h1>
          <p v-if="actividad.fechasTexto" class="fechas">{{ actividad.fechasTexto }}</p>
          <p v-if="actividad.descripcion" class="descripcion">{{ actividad.descripcion }}</p>
          <button v-if="actividad.descripcion && actividad.descripcion.length > 160" type="button" class="enlace" @click="descripcionAbierta = true">Leer más</button>
          <div class="acciones">
            <button v-if="recibe" type="button" class="pub-boton" @click="participar()">Quiero participar</button>
            <button v-if="planoUrl" type="button" class="pub-boton secundario" @click="planoAbierto = true">Ver plano</button>
          </div>
        </div>
        <div v-if="stats && stats.total" class="cifras">
          <div><strong>{{ stats.libres }}</strong><span>disponibles</span></div>
          <div><strong>{{ stats.total }}</strong><span>espacios</span></div>
          <div><strong>{{ stats.porcentajeOcupacion }}%</strong><span>ocupado</span></div>
        </div>
      </section>

      <section v-if="categorias.length" class="categorias">
        <div class="cab">
          <h2>Espacios</h2>
          <span class="pub-vivo">Actualizado en vivo</span>
        </div>
        <div class="rejilla">
          <article v-for="c in categorias" :key="c.id" class="tarjeta" :style="{ '--c': c.color || '#8f6dff' }">
            <div class="tarjeta-cab"><span class="punto"></span><strong>{{ c.nombre }}</strong></div>
            <p v-if="c.descripcion" class="tenue">{{ c.descripcion }}</p>
            <div class="tarjeta-pie">
              <span v-if="c.precioBase" class="precio">Desde {{ bs(c.precioBase) }}</span>
              <span v-if="c.stats" class="tenue">{{ c.stats.libres }} de {{ c.stats.total }} libres</span>
            </div>
            <button v-if="recibe" type="button" class="pub-boton secundario chico" @click="participar(c.id)">Me interesa</button>
          </article>
        </div>
      </section>
    </main>

    <ModalPublico v-if="formAbierto" titulo="Quiero participar" @cerrar="formAbierto = false">
      <p class="tenue">Déjanos tus datos y te escribiremos por WhatsApp para reservar tu espacio.</p>
      <form id="form-interes" class="formulario" @submit.prevent="enviar">
        <label class="pub-campo ancho"><span>Nombre completo</span><input v-model="form.nombreCompleto" autocomplete="name" /></label>
        <label class="pub-campo"><span>Celular (WhatsApp)</span><input v-model="form.celular" inputmode="tel" autocomplete="tel" /></label>
        <label class="pub-campo"><span>Rubro</span><input v-model="form.rubro" placeholder="Comida, artesanía…" /></label>
        <label class="pub-campo ancho"><span>Empresa o emprendimiento</span><input v-model="form.empresa" /></label>
        <label class="pub-campo ancho"><span>Espacio que te interesa</span>
          <select v-model="form.categoriaId">
            <option value="" disabled>Elige una opción</option>
            <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
          </select>
        </label>
      </form>
      <p v-if="respuesta" class="mal">{{ respuesta }}</p>
      <template #pie>
        <button type="button" class="pub-boton secundario" @click="formAbierto = false">Cancelar</button>
        <button type="submit" form="form-interes" class="pub-boton" :disabled="!formListo || enviando">{{ enviando ? 'Enviando…' : 'Enviar solicitud' }}</button>
      </template>
    </ModalPublico>

    <ModalPublico v-if="descripcionAbierta && actividad" :titulo="actividad.nombre" @cerrar="descripcionAbierta = false">
      <p class="texto-largo">{{ actividad.descripcion }}</p>
    </ModalPublico>

    <div v-if="planoAbierto" class="visor" role="dialog" aria-modal="true" @click.self="planoAbierto = false">
      <button type="button" class="cerrar" aria-label="Cerrar" @click="planoAbierto = false">✕</button>
      <img :src="planoUrl" :alt="`Plano de ${actividad?.nombre}`" />
    </div>
  </div>
</template>

<style scoped>
.pagina {
  height: 100vh; height: 100dvh; display: grid; grid-template-rows: auto minmax(0, 1fr); max-width: 1180px; margin: 0 auto;
  padding: 0 max(16px, env(safe-area-inset-right, 0px)) 0 max(16px, env(safe-area-inset-left, 0px)); box-sizing: border-box;
}
.barra { display: flex; align-items: center; gap: 12px; padding: calc(12px + env(safe-area-inset-top, 0px)) 0 10px; }
.marca { flex: 1; font-weight: 700; color: var(--pub-suave); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.estado { text-align: center; color: var(--pub-suave); margin: 4rem 0; }
.estado a { color: var(--pub-acento-claro); }
.contenido { display: grid; grid-template-rows: auto minmax(0, 1fr); gap: 16px; min-height: 0; padding-bottom: calc(14px + env(safe-area-inset-bottom, 0px)); }
.hero {
  border-radius: 20px; overflow: hidden; padding: 22px; display: flex; align-items: flex-end; justify-content: space-between; gap: 18px; flex-wrap: wrap;
  background: radial-gradient(900px 400px at 15% 0%, rgba(143, 109, 255, 0.4), transparent 70%), var(--pub-panel);
  background-size: cover; background-position: center; border: 1px solid var(--pub-linea); animation: pub-entrar 0.35s ease-out both;
}
.hero-txt { max-width: 40rem; display: flex; flex-direction: column; gap: 6px; }
.lugar { text-transform: uppercase; letter-spacing: 0.14em; font-size: 0.72rem; color: var(--pub-acento-claro); font-weight: 700; }
.hero h1 { font-size: clamp(1.6rem, 4vw, 2.6rem); margin: 0; line-height: 1.05; }
.fechas { color: #d7d4ff; margin: 0; font-weight: 600; }
.descripcion { color: #c5c7dc; margin: 0; display: -webkit-box; -webkit-line-clamp: 3; line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; white-space: pre-line; }
.enlace { border: 0; background: none; padding: 0; color: var(--pub-acento-claro); font: inherit; font-weight: 600; cursor: pointer; align-self: flex-start; }
.acciones { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 6px; }
.cifras { display: flex; gap: 8px; }
.cifras div { background: rgba(11, 13, 26, 0.7); border: 1px solid var(--pub-linea); border-radius: 14px; padding: 10px 14px; text-align: center; display: flex; flex-direction: column; backdrop-filter: blur(6px); min-width: 78px; }
.cifras strong { font: 800 1.4rem var(--pub-titulo); }
.cifras span { color: var(--pub-suave); font-size: 0.75rem; }
.categorias { display: flex; flex-direction: column; gap: 10px; min-height: 0; }
.cab { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.cab h2 { margin: 0; font-size: 1.1rem; }
.rejilla { min-height: 0; overflow-y: auto; overscroll-behavior: contain; display: grid; gap: 12px; align-content: start; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); padding: 2px; }
.tarjeta {
  background: var(--pub-panel); border: 1px solid var(--pub-linea); border-top: 4px solid var(--c); border-radius: 16px; padding: 14px;
  display: flex; flex-direction: column; gap: 8px; animation: pub-entrar 0.35s ease-out both;
}
.tarjeta-cab { display: flex; align-items: center; gap: 8px; }
.punto { width: 10px; height: 10px; border-radius: 50%; background: var(--c); flex: none; }
.tarjeta p { margin: 0; }
.tarjeta-pie { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; margin-top: auto; }
.tarjeta .pub-boton { align-self: flex-start; }
.precio { font-weight: 700; }
.tenue { color: var(--pub-suave); font-size: 0.88rem; margin: 0; }
.formulario { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.formulario .ancho { grid-column: 1 / -1; }
.mal { color: var(--pub-alerta); margin: 0; }
.texto-largo { margin: 0; white-space: pre-line; color: var(--pub-suave); }
.visor { position: fixed; inset: 0; z-index: 80; background: rgba(5, 6, 12, 0.92); display: grid; place-items: center; padding: 1rem; }
.visor img { max-width: 100%; max-height: 92dvh; border-radius: 0.6rem; background: #fff; }
.cerrar { position: absolute; top: calc(0.8rem + env(safe-area-inset-top, 0px)); right: 0.8rem; width: 2.6rem; height: 2.6rem; border-radius: 50%; border: 0; background: #262b48; color: #fff; font-size: 1.1rem; cursor: pointer; }
@media (max-width: 640px) {
  .hero { padding: 16px; }
  .cifras { width: 100%; }
  .cifras div { flex: 1; min-width: 0; padding: 8px; }
  .cifras strong { font-size: 1.15rem; }
  .descripcion { -webkit-line-clamp: 2; line-clamp: 2; }
  .formulario { grid-template-columns: 1fr; }
  .rejilla { grid-template-columns: 1fr; }
}
</style>
