<script setup>
import { ref, computed, watch, onMounted, onUnmounted, defineAsyncComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { url as urlApi } from '../config.js';
import { marca, iniciales, cargarMarca } from '../ui/marca';
import { alCambiarPublico } from '../ui/canalPublico';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import AvisosPublicos from '../components/AvisosPublicos.vue';
import '../publico.css';

const SECCIONES = {
  credencial: { vista: defineAsyncComponent(() => import('./CredencialPublica.vue')), titulo: 'Credencial', propia: true },
  reserva: { vista: defineAsyncComponent(() => import('./MiReserva.vue')), titulo: 'Mi reserva' },
  evento: { vista: defineAsyncComponent(() => import('./ReservaMesas.vue')), titulo: 'Reserva de mesas' },
  actividad: { vista: defineAsyncComponent(() => import('./ActividadPublica.vue')), titulo: 'Actividad' },
};

const route = useRoute();
const router = useRouter();

const seccion = computed(() => {
  const q = route.query;
  if (q.credencial) return SECCIONES.credencial;
  if (q.reserva) return SECCIONES.reserva;
  if (q.evento) return SECCIONES.evento;
  if (q.v === 'actividad' || q.v === 'feria') return SECCIONES.actividad;
  return null;
});

const eventos = ref([]);
const feria = ref(null);
const cargando = ref(true);
const codigo = ref('');
const aviso = ref('');
const filtro = ref('todas');
const busqueda = ref('');
let pendiente = null;
let soltarCanal = null;

watch([seccion, () => marca.nombre], ([s]) => {
  document.title = s ? `${s.titulo} · ${marca.nombre}` : marca.nombre;
}, { immediate: true });
cargarMarca();

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 0 })}`;

const actividades = computed(() => {
  const lista = eventos.value.map((e) => ({
    clave: `e${e.id}`, tipo: 'mesas', etiqueta: 'Reserva de mesas', nombre: e.nombre, lugar: e.localNombre,
    fecha: e.fechaTexto, imagen: e.planoUrl ? urlApi(e.planoUrl) : null,
    libres: e.libres, total: e.mesas, unidad: 'mesas', desde: e.desde,
    accion: 'Elegir mi mesa', destino: { path: '/', query: { evento: e.id } },
  }));
  if (feria.value?.edicion) {
    const f = feria.value.edicion;
    const s = feria.value.estadisticas;
    lista.push({
      clave: `a${f.id ?? 'activa'}`, tipo: 'actividad', etiqueta: 'Feria · espacios', nombre: f.nombre, lugar: f.lugar,
      fecha: f.fechasTexto, imagen: f.portadaUrl ? urlApi(f.portadaUrl) : null,
      libres: s?.libres, total: s?.total, unidad: 'espacios', desde: null,
      accion: 'Ver información', destino: { path: '/', query: { v: 'actividad' } },
    });
  }
  return lista;
});

const hayFiltros = computed(() => new Set(actividades.value.map((a) => a.tipo)).size > 1 || actividades.value.length > 6);
const visibles = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  return actividades.value.filter((a) => (filtro.value === 'todas' || a.tipo === filtro.value)
    && (!q || `${a.nombre} ${a.lugar || ''}`.toLowerCase().includes(q)));
});
const contacto = computed(() => (marca.contacto || '').trim());
const enlaceContacto = computed(() => enlaceWhatsApp(contacto.value));

async function cargar(silencioso = false) {
  if (!silencioso) cargando.value = true;
  const [ev, fe] = await Promise.allSettled([
    fetch(urlApi('/api/publico/mesas')).then((r) => (r.ok ? r.json() : [])),
    fetch(urlApi('/api/publico/feria')).then((r) => (r.ok ? r.json() : null)),
  ]);
  if (ev.status === 'fulfilled' && Array.isArray(ev.value)) eventos.value = ev.value;
  if (fe.status === 'fulfilled') feria.value = fe.value;
  cargando.value = false;
}

function programar() {
  clearTimeout(pendiente);
  pendiente = setTimeout(() => cargar(true), 600);
}

function normalizar(texto) {
  const c = texto.trim().toUpperCase().replace(/\s+/g, '');
  const m = c.match(/MSA-?([A-Z0-9]{10})/);
  if (m) return `MSA-${m[1]}`;
  if (/^[A-Z0-9]{10}$/.test(c)) return `MSA-${c}`;
  return null;
}

function consultar() {
  const c = normalizar(codigo.value);
  if (!c) { aviso.value = 'Escribe el código que recibiste al reservar, por ejemplo MSA-AB12CD34EF.'; return; }
  aviso.value = '';
  router.push({ path: '/', query: { reserva: c } });
}

onMounted(() => {
  if (!seccion.value) cargar();
  soltarCanal = alCambiarPublico((m) => {
    if (m?.tipo === 'sistema') cargarMarca(true);
    else if (!seccion.value) programar();
  });
});
onUnmounted(() => { soltarCanal?.(); clearTimeout(pendiente); });
watch(seccion, (s) => { if (!s) cargar(!!actividades.value.length); });
</script>

<template>
  <component :is="seccion.vista" v-if="seccion && seccion.propia" :key="route.fullPath" />

  <div v-else class="pub raiz">
    <component :is="seccion.vista" v-if="seccion" :key="route.fullPath" />

    <div v-else class="portal">
      <header class="cabecera">
        <div class="marca">
          <img v-if="marca.logoUrl" :src="marca.logoUrl" :alt="marca.nombre" class="logo-img" />
          <span v-else class="logo">{{ iniciales }}</span>
          <span class="nombre">{{ marca.nombre }}</span>
        </div>
        <router-link to="/admin" class="pub-volver personal">Ingreso del personal</router-link>
      </header>

      <main class="cuerpo">
        <section class="intro">
          <span class="pub-vivo">Disponibilidad en tiempo real</span>
          <h1>{{ marca.lema || 'Reserva tu lugar en nuestras actividades' }}</h1>
          <p class="sub">Elige una actividad, aparta tu lugar y sigue tu reserva desde el celular.</p>

          <form class="consulta" @submit.prevent="consultar">
            <label for="codigo-reserva">¿Ya reservaste? Consulta tu reserva</label>
            <div class="fila">
              <input id="codigo-reserva" v-model="codigo" placeholder="MSA-XXXXXXXXXX" autocomplete="off"
                     autocapitalize="characters" spellcheck="false" enterkeyhint="search" />
              <button type="submit" class="pub-boton">Consultar</button>
            </div>
            <p v-if="aviso" class="aviso">{{ aviso }}</p>
          </form>
        </section>

        <section class="catalogo">
          <div class="catalogo-cab">
            <h2>Actividades <span class="cuenta">{{ actividades.length }}</span></h2>
            <div v-if="hayFiltros" class="filtros">
              <button v-for="[k, t] in [['todas', 'Todas'], ['mesas', 'Mesas'], ['actividad', 'Ferias']]" :key="k"
                      type="button" :class="{ on: filtro === k }" @click="filtro = k">{{ t }}</button>
              <input v-if="actividades.length > 6" v-model="busqueda" type="search" placeholder="Buscar…" aria-label="Buscar actividad" />
            </div>
          </div>

          <div class="lista">
            <p v-if="cargando" class="vacio">Cargando actividades…</p>
            <p v-else-if="!visibles.length" class="vacio">
              {{ actividades.length ? 'Ninguna actividad coincide con tu búsqueda.' : 'No hay actividades abiertas en este momento. Vuelve pronto.' }}
            </p>
            <router-link v-for="(a, i) in visibles" v-else :key="a.clave" :to="a.destino" class="tarjeta"
                         :class="a.tipo" :style="{ animationDelay: `${i * 60}ms` }">
              <div class="imagen" :style="a.imagen ? { backgroundImage: `url(${a.imagen})` } : null">
                <span class="pub-chip">{{ a.etiqueta }}</span>
              </div>
              <div class="datos">
                <span class="etq-movil">{{ a.etiqueta }}</span>
                <strong>{{ a.nombre }}</strong>
                <span v-if="a.lugar || a.fecha" class="meta">{{ [a.lugar, a.fecha].filter(Boolean).join(' · ') }}</span>
                <div v-if="a.total" class="disp">
                  <div class="barra"><i :style="{ width: `${Math.round(((a.total - a.libres) / a.total) * 100)}%` }"></i></div>
                  <span><b>{{ a.libres }}</b> de {{ a.total }} {{ a.unidad }} libres</span>
                </div>
                <div class="pie-tarjeta">
                  <span v-if="a.desde" class="desde">Desde {{ bs(a.desde) }}</span>
                  <span class="accion">{{ a.accion }} →</span>
                </div>
              </div>
            </router-link>
          </div>
        </section>
      </main>

      <footer class="pie">
        <span>© {{ new Date().getFullYear() }} {{ marca.nombre }}</span>
        <a v-if="enlaceContacto" :href="enlaceContacto" target="_blank" rel="noopener">Consultas por WhatsApp: {{ contacto }}</a>
        <span v-else-if="contacto">Consultas: {{ contacto }}</span>
      </footer>
    </div>

    <AvisosPublicos />
  </div>
</template>

<style scoped>
.raiz { min-height: 100dvh; }
.portal {
  height: 100vh; height: 100dvh; display: grid; grid-template-rows: auto minmax(0, 1fr) auto;
  max-width: 1180px; margin: 0 auto; padding: 0 max(16px, env(safe-area-inset-right, 0px)) 0 max(16px, env(safe-area-inset-left, 0px));
  box-sizing: border-box;
}
.cabecera { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: calc(14px + env(safe-area-inset-top, 0px)) 0 10px; }
.marca { display: flex; align-items: center; gap: 10px; font-weight: 700; min-width: 0; }
.nombre { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.logo { width: 36px; height: 36px; border-radius: 10px; display: grid; place-items: center; background: var(--pub-acento); color: #fff; font-size: 0.85rem; flex: none; }
.logo-img { width: 36px; height: 36px; object-fit: contain; flex: none; }
.cuerpo { display: grid; grid-template-columns: minmax(300px, 0.85fr) minmax(0, 1.4fr); gap: 28px; min-height: 0; padding: 12px 0; }
.intro { display: flex; flex-direction: column; justify-content: center; gap: 14px; min-height: 0; animation: pub-entrar 0.4s ease-out both; }
.intro h1 { margin: 0; font-size: clamp(1.8rem, 3.6vw, 3rem); line-height: 1.04; letter-spacing: -0.01em; }
.sub { margin: 0; color: var(--pub-suave); max-width: 32rem; }
.consulta {
  margin-top: 6px; padding: 16px; border-radius: 18px; background: rgba(21, 26, 46, 0.78);
  border: 1px solid var(--pub-linea); display: flex; flex-direction: column; gap: 10px; backdrop-filter: blur(6px);
}
.consulta label { font-weight: 700; font-size: 0.95rem; }
.fila { display: flex; gap: 8px; }
.fila input {
  flex: 1; min-width: 0; min-height: 46px; padding: 0 14px; border-radius: 12px; border: 1px solid var(--pub-linea);
  background: var(--pub-fondo); color: var(--pub-texto); font: 500 1.05rem var(--pub-mono); text-transform: uppercase; letter-spacing: 0.04em;
}
.fila input:focus { outline: 2px solid var(--pub-acento-claro); border-color: transparent; }
.aviso { margin: 0; color: var(--pub-alerta); font-size: 0.86rem; }
.catalogo { display: flex; flex-direction: column; justify-content: center; gap: 12px; min-height: 0; }
.catalogo-cab { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.catalogo-cab h2 { margin: 0; font-size: 1.15rem; display: flex; align-items: center; gap: 8px; }
.cuenta { font: 700 0.75rem 'Public Sans', sans-serif; background: var(--pub-panel-2); color: var(--pub-acento-claro); padding: 2px 8px; border-radius: 999px; }
.filtros { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.filtros button {
  border: 1px solid var(--pub-linea); background: transparent; color: var(--pub-suave); border-radius: 999px;
  padding: 6px 12px; font: 600 0.82rem 'Public Sans', sans-serif; cursor: pointer;
}
.filtros button.on { background: var(--pub-acento); border-color: var(--pub-acento); color: #fff; }
.filtros input { min-height: 32px; border-radius: 999px; border: 1px solid var(--pub-linea); background: var(--pub-fondo); color: var(--pub-texto); padding: 0 12px; font: inherit; font-size: 0.85rem; width: 150px; }
.lista {
  flex: 0 1 auto; min-height: 0; overflow-y: auto; overscroll-behavior: contain; display: grid; gap: 14px; align-content: start;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); padding: 2px 4px 4px 2px; scrollbar-width: thin;
}
.vacio { grid-column: 1 / -1; color: var(--pub-suave); text-align: center; padding: 48px 12px; border: 1px dashed var(--pub-linea); border-radius: 18px; margin: 0; }
.tarjeta {
  display: flex; flex-direction: column; border-radius: 18px; overflow: hidden; text-decoration: none; color: inherit;
  background: var(--pub-panel); border: 1px solid var(--pub-linea); animation: pub-entrar 0.35s ease-out both;
  transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}
.tarjeta:hover { transform: translateY(-3px); border-color: var(--pub-acento-claro); box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35); }
.imagen {
  height: 110px; padding: 10px; display: flex; align-items: flex-start; background-size: cover; background-position: center;
  background-color: var(--pub-panel-2);
  background-image: linear-gradient(135deg, rgba(143, 109, 255, 0.45), rgba(76, 195, 255, 0.18));
}
.actividad .imagen:not([style]) { background-image: linear-gradient(135deg, rgba(255, 179, 71, 0.35), rgba(143, 109, 255, 0.3)); }
.imagen .pub-chip { background: rgba(11, 13, 26, 0.75); backdrop-filter: blur(4px); }
.datos { padding: 12px 14px 14px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.datos strong { font: 800 1.12rem var(--pub-titulo); line-height: 1.15; }
.meta { color: var(--pub-suave); font-size: 0.85rem; text-transform: capitalize; }
.disp { display: flex; flex-direction: column; gap: 5px; font-size: 0.82rem; color: var(--pub-suave); }
.disp b { color: var(--pub-ok); }
.barra { height: 6px; border-radius: 999px; background: var(--pub-panel-2); overflow: hidden; }
.barra i { display: block; height: 100%; background: linear-gradient(90deg, var(--pub-acento), var(--pub-proceso)); border-radius: inherit; transition: width 0.4s ease; }
.pie-tarjeta { margin-top: auto; padding-top: 4px; display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.etq-movil { display: none; }
.desde { font-weight: 700; font-size: 0.9rem; }
.accion { color: var(--pub-acento-claro); font-weight: 700; font-size: 0.9rem; margin-left: auto; }
.pie { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 10px 0 calc(12px + env(safe-area-inset-bottom, 0px)); color: #6f7193; font-size: 0.8rem; }
.pie a { color: var(--pub-suave); }
@media (max-width: 860px) {
  .cuerpo { grid-template-columns: 1fr; grid-template-rows: auto minmax(0, 1fr); gap: 14px; padding-top: 4px; }
  .intro { justify-content: flex-start; gap: 8px; }
  .intro h1 { font-size: clamp(1.45rem, 6.5vw, 2rem); }
  .sub, .intro .pub-vivo { display: none; }
  .consulta { padding: 12px; margin-top: 2px; }
  .catalogo { justify-content: flex-start; }
  .lista { grid-template-columns: 1fr; gap: 10px; }
  .imagen { display: none; }
  .tarjeta { border-left: 3px solid var(--pub-acento); }
  .tarjeta.actividad { border-left-color: var(--pub-alerta); }
  .etq-movil { display: block; font-size: 0.68rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: var(--pub-acento-claro); }
  .datos { padding: 12px 14px; }
  .personal { font-size: 0.8rem; min-height: 34px; }
}
@media (max-height: 560px) and (min-width: 861px) {
  .intro h1 { font-size: 1.8rem; }
  .sub { display: none; }
}
</style>
