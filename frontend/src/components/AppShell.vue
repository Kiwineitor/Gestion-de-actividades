<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import { usePuestosStore } from '../stores/puestos';
import { toast } from '../ui/toast';
import { alerta } from '../ui/alerta';
import { tema, alternarTema } from '../ui/tema';
import { iniciarPresencia, marcarPantalla, detenerPresencia } from '../ui/presencia';
import AvisoAnuncios from './AvisoAnuncios.vue';
import { useAnunciosStore } from '../stores/anuncios';
import { marca, iniciales, cargarMarca } from '../ui/marca';
import { claveModulo, moduloActual, aModulo, puedeAbrir, MODULOS } from '../modulos';
import { useActividadStore } from '../stores/actividad';
import { etiquetaRol } from '../ui/roles';

const auth = useAuthStore();
const permisos = usePermisosStore();
const tienda = usePuestosStore();
permisos.asegurar();
cargarMarca();
const router = useRouter();
const route = useRoute();
const abierto = ref(false);

const ESPERA_OCULTAR_MS = 6000;
const contraido = ref(leerSesion('menu.contraido') === '1');
const ocultoAuto = ref(false);
const asomado = ref(false);
let temporizadorMenu = null;

function leerSesion(clave) {
  try { return sessionStorage.getItem(clave); } catch { return null; }
}

function guardarSesion(clave, valor) {
  try { sessionStorage.setItem(clave, valor); } catch { }
}

function esMovil() {
  return typeof window !== 'undefined' && window.matchMedia('(max-width: 820px)').matches;
}

function alternarMenu() {
  if (esMovil()) {
    abierto.value = !abierto.value;
    return;
  }
  const plegado = contraido.value || ocultoAuto.value;
  contraido.value = !plegado;
  ocultoAuto.value = false;
  asomado.value = false;
  guardarSesion('menu.contraido', contraido.value ? '1' : '0');
  programarOcultar();
}

function programarOcultar() {
  clearTimeout(temporizadorMenu);
  if (!modulo.value.inmersivo || contraido.value || ocultoAuto.value || asomado.value) return;
  temporizadorMenu = setTimeout(() => { ocultoAuto.value = true; }, ESPERA_OCULTAR_MS);
}

function entrarMenu() {
  clearTimeout(temporizadorMenu);
  asomado.value = true;
}

function salirMenu() {
  asomado.value = false;
  programarOcultar();
}

const modulo = computed(() => moduloActual(route));
const clave = computed(() => claveModulo(route));
const titulo = computed(() => modulo.value.titulo || '');
const inmersivo = computed(() => Boolean(modulo.value.inmersivo));
const plegado = computed(() => contraido.value || ocultoAuto.value);

watch(clave, () => {
  ocultoAuto.value = false;
  programarOcultar();
}, { immediate: true });

const GRUPOS = [
  'Mi trabajo', 'Actividades', 'Ventas', 'Planos y precios', 'Credenciales y acceso',
  'Reportes', 'Personas y permisos', 'Comunicación', 'Sistema',
];

const TODOS = [
  { m: 'inicio', g: 'Mi trabajo', icono: '🏠', txt: 'Inicio' },
  { m: 'notificaciones', g: 'Mi trabajo', icono: '🔔', txt: 'Notificaciones' },

  { m: 'actividades', g: 'Actividades', icono: '🗓️', txt: 'Centro de actividades' },
  { m: 'mesas', g: 'Actividades', icono: '🍸', txt: 'Eventos con mesas' },
  { m: 'interesados', g: 'Actividades', icono: '🙋', txt: 'Solicitudes de participación' },
  { m: 'tablero', g: 'Actividades', icono: '📌', txt: 'Tablero' },
  { m: 'catalogo', g: 'Actividades', icono: '📚', txt: 'Catálogo' },

  { m: 'mapa', g: 'Ventas', icono: '🗺️', txt: 'Mapa de ventas' },
  { m: 'venta', g: 'Ventas', icono: '🛒', txt: 'Registrar venta' },
  { m: 'mis-ventas', g: 'Ventas', icono: '🧾', txt: 'Mis ventas' },
  { m: 'inscripciones', g: 'Ventas', icono: '📋', txt: 'Inscripciones' },
  { m: 'control-ventas', g: 'Ventas', icono: '💰', txt: 'Control de ventas' },
  { m: 'vendedores', g: 'Ventas', icono: '👥', txt: 'Vendedores' },

  { m: 'editor', g: 'Planos y precios', icono: '✏️', txt: 'Editor del plano' },
  { m: 'categorias', g: 'Planos y precios', icono: '🏷️', txt: 'Categorías y precios' },
  { m: 'puestos', g: 'Planos y precios', icono: '🔢', txt: 'Puestos' },

  { m: 'credenciales', g: 'Credenciales y acceso', icono: '🪪', txt: 'Credenciales' },
  { m: 'credenciales-diseno', g: 'Credenciales y acceso', icono: '🎨', txt: 'Diseño de credenciales' },
  { m: 'credenciales-apoyo', g: 'Credenciales y acceso', icono: '🎫', txt: 'Credenciales de apoyo' },
  { m: 'impresion-masiva', g: 'Credenciales y acceso', icono: '🖨️', txt: 'Impresión masiva' },
  { m: 'responsables-extra', g: 'Credenciales y acceso', icono: '➕', txt: 'Responsables extra' },
  { m: 'escaner', g: 'Credenciales y acceso', icono: '📷', txt: 'Escanear credencial' },
  { m: 'control-acceso', g: 'Credenciales y acceso', icono: '🚪', txt: 'Control de acceso' },

  { m: 'dashboard', g: 'Reportes', icono: '📊', txt: 'Dashboard' },
  { m: 'reporte-responsables', g: 'Reportes', icono: '📑', txt: 'Reporte de responsables' },

  { m: 'usuarios', g: 'Personas y permisos', icono: '👤', txt: 'Usuarios' },
  { m: 'roles', g: 'Personas y permisos', icono: '🛡️', txt: 'Roles y permisos' },
  { m: 'permisos', siNo: 'roles', g: 'Personas y permisos', icono: '🔐', txt: 'Roles y permisos' },
  { m: 'personas', g: 'Personas y permisos', icono: '🧑', txt: 'Personas' },
  { m: 'personal-apoyo', g: 'Personas y permisos', icono: '🤝', txt: 'Personal de apoyo' },

  { m: 'anuncios', g: 'Comunicación', icono: '📣', txt: 'Comunicados' },
  { m: 'whatsapp', g: 'Comunicación', icono: '💬', txt: 'WhatsApp' },

  { m: 'sistema', g: 'Sistema', icono: '🏷️', txt: 'Datos del sistema' },
  { m: 'mantenimiento', g: 'Sistema', icono: '⚙️', txt: 'Mantenimiento' },
  { m: 'errores', g: 'Sistema', icono: '⚠️', txt: 'Registro de errores' },
];

const PREFERIDOS = ['inicio', 'mapa', 'venta', 'mis-ventas', 'actividades', 'mesas', 'escaner', 'credenciales', 'notificaciones'];
const CLAVE_GRUPOS = 'menu.grupos.v2';

const busqueda = ref('');
const buscador = ref(null);
const abiertos = ref(new Set(leerGrupos()));

function leerGrupos() {
  try {
    const g = JSON.parse(localStorage.getItem(CLAVE_GRUPOS) || 'null');
    return Array.isArray(g) ? g : ['Mi trabajo'];
  } catch { return ['Mi trabajo']; }
}

function alternarGrupo(g) {
  const s = new Set(abiertos.value);
  if (s.has(g)) s.delete(g); else s.add(g);
  abiertos.value = s;
  try { localStorage.setItem(CLAVE_GRUPOS, JSON.stringify([...s])); } catch { }
}

const sinAcentos = (s) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

const anuncios = useAnunciosStore();
const actividad = useActividadStore();

function puedeVerEnlace(e) {
  if (e.siNo && permisos.puedeVer(e.siNo)) return false;
  return puedeAbrir(MODULOS[e.m], auth, permisos);
}

const enlaces = computed(() => TODOS.filter(puedeVerEnlace));

const grupos = computed(() => {
  const q = sinAcentos(busqueda.value.trim());
  return GRUPOS
    .map((g) => ({
      clave: g,
      titulo: g,
      enlaces: enlaces.value.filter((e) => e.g === g && (!q || sinAcentos(`${e.txt} ${g}`).includes(q))),
    }))
    .filter((g) => g.enlaces.length > 0)
    .map((g) => ({ ...g, abierto: Boolean(q) || abiertos.value.has(g.clave) || g.enlaces.some((e) => e.m === clave.value) }));
});

const sinResultados = computed(() => busqueda.value.trim() && !grupos.value.length);

function irPrimero() {
  const primero = grupos.value[0]?.enlaces[0];
  if (!primero) return;
  router.push(aModulo(primero.m));
  busqueda.value = '';
  abierto.value = false;
}

function atajoBuscar(e) {
  const escribiendo = /^(INPUT|TEXTAREA|SELECT)$/.test(e.target?.tagName || '') || e.target?.isContentEditable;
  if ((e.key === 'k' && (e.ctrlKey || e.metaKey)) || (e.key === '/' && !escribiendo)) {
    e.preventDefault();
    if (plegado.value) { contraido.value = false; ocultoAuto.value = false; }
    if (esMovil()) abierto.value = true;
    setTimeout(() => buscador.value?.focus(), 30);
  }
}

const enlacesPrincipales = computed(() => {
  const vistos = PREFERIDOS.map((m) => enlaces.value.find((e) => e.m === m)).filter(Boolean);
  return (vistos.length ? vistos : enlaces.value).slice(0, 4);
});

const iconoTema = computed(() => (tema.value === 'dark' ? '🌙' : tema.value === 'light' ? '☀️' : '🌗'));

function alCambiarPermisos(n) {
  if (n?.tipo === 'MANTENIMIENTO_ACTIVO') {
    expulsarPorMantenimiento(n.cuerpo || n.asunto || 'Sistema en mantenimiento');
    return;
  }
  if (n?.tipo !== 'PERMISOS_CAMBIADOS') return;
  permisos.recargar().then(() => {
    toast('Cambiaron tus opciones del menú', 'info');
    if (!puedeAbrir(modulo.value, auth, permisos)) router.push('/admin');
  });
}

function expulsarPorMantenimiento(mensaje) {
  if (mantenimientoMostrado) return;
  mantenimientoMostrado = true;
  permisos.limpiar();
  auth.logout();
  router.push('/admin');
  alerta(mensaje || 'Sistema en mantenimiento', 'advertencia', 0);
}

function alMantenimientoHttp(e) {
  expulsarPorMantenimiento(e.detail?.mensaje);
}

function alVolverAlFrente() {
  if (typeof document !== 'undefined' && document.visibilityState !== 'visible') return;
  permisos.recargar();
}

let quitarOyente = null;
let mantenimientoMostrado = false;
onMounted(() => {
  tienda.conectar();
  quitarOyente = tienda.registrarNotificaciones(alCambiarPermisos);
  window.addEventListener('unifex:mantenimiento', alMantenimientoHttp);
  window.addEventListener('keydown', atajoBuscar);
  document.addEventListener('visibilitychange', alVolverAlFrente);
  window.addEventListener('online', alVolverAlFrente);
  iniciarPresencia();
  anuncios.asegurar();
  actividad.asegurar();
  marcarPantalla(modulo.value.pantalla, modulo.value.titulo);
});

watch(() => route.fullPath, () => marcarPantalla(modulo.value.pantalla, modulo.value.titulo));
onUnmounted(() => {
  clearTimeout(temporizadorMenu);
  if (quitarOyente) quitarOyente();
  window.removeEventListener('unifex:mantenimiento', alMantenimientoHttp);
  window.removeEventListener('keydown', atajoBuscar);
  document.removeEventListener('visibilitychange', alVolverAlFrente);
  window.removeEventListener('online', alVolverAlFrente);
});

function salir() {
  detenerPresencia();
  anuncios.desconectar();
  actividad.desconectar();
  permisos.limpiar();
  auth.logout();
  router.push('/admin');
}
</script>

<template>
  <div class="shell">
    <AvisoAnuncios />
    <div v-if="plegado && asomado" class="hueco-menu"></div>
    <aside class="sidebar" :class="{ abierto, plegado, asomado: plegado && asomado }"
           @mouseenter="entrarMenu" @mouseleave="salirMenu">
      <div class="marca">
        <img v-if="marca.logoUrl" :src="marca.logoUrl" :alt="marca.nombre" class="logo-img" />
        <span v-else class="logo">{{ iniciales }}</span>
        <strong class="txt-menu">{{ marca.nombre }}</strong>
      </div>
      <div class="buscador txt-menu">
        <input ref="buscador" v-model="busqueda" type="search" placeholder="Buscar opción…  ( / )" aria-label="Buscar en el menú"
               @keydown.enter.prevent="irPrimero" @keydown.esc="busqueda = ''" />
      </div>
      <nav>
        <p v-if="sinResultados" class="sin-resultados txt-menu">Nada coincide con «{{ busqueda }}».</p>
        <div v-for="g in grupos" :key="g.clave" class="grupo" :class="{ cerrado: !g.abierto }">
          <button type="button" class="grupo-titulo" :aria-expanded="g.abierto" @click="alternarGrupo(g.clave)">
            <span>{{ g.titulo }}</span><span class="cuenta">{{ g.enlaces.length }}</span><span class="flecha" aria-hidden="true">▾</span>
          </button>
          <div class="grupo-enlaces">
            <router-link v-for="e in g.enlaces" :key="e.m" :to="aModulo(e.m)" class="enlace" :class="{ activo: e.m === clave }" :title="e.txt" @click="abierto = false; busqueda = ''">
              <span class="ico">{{ e.icono }}</span><span class="txt-menu">{{ e.txt }}</span>
            </router-link>
          </div>
        </div>
      </nav>
      <div class="pie-side">
        <div class="quien">
          <span class="avatar">{{ (auth.usuario || '?').charAt(0).toUpperCase() }}</span>
          <div class="datos txt-menu">
            <span class="u">{{ auth.usuario }}</span>
            <span class="r">{{ etiquetaRol(auth.rol) }}</span>
          </div>
        </div>
        <button class="btn btn-fantasma btn-sm txt-menu" @click="salir">Salir</button>
      </div>
    </aside>

    <div v-if="abierto" class="velo" @click="abierto = false"></div>

    <div class="area">
      <header class="topbar">
        <button class="btn btn-fantasma btn-icono menu" :aria-expanded="!plegado" @click="alternarMenu"
                :title="plegado ? 'Mostrar menú' : 'Ocultar menú'" aria-label="Menú">☰</button>
        <h1>{{ titulo }}</h1>
        <span v-if="actividad.actual" class="chip-actividad" :title="`Actividad activa: ${actividad.actual.nombre}`">{{ actividad.actual.nombre }}</span>
        <div class="crecer"></div>
        <button class="btn btn-fantasma btn-icono" :title="`Tema: ${tema}`" @click="alternarTema">{{ iconoTema }}</button>
      </header>
      <main class="contenido" :class="{ inmersivo, completo: modulo.completo }">
        <slot />
      </main>
    </div>

    <nav class="tabbar">
      <router-link v-for="e in enlacesPrincipales" :key="e.m" :to="aModulo(e.m)" class="tab" :class="{ activo: e.m === clave }" @click="abierto = false">
        <span class="ico">{{ e.icono }}</span>
        <span class="txt">{{ e.txt }}</span>
      </router-link>
      <button type="button" class="tab tab-mas" :class="{ activo: abierto }" @click="abierto = !abierto">
        <span class="ico">☰</span>
        <span class="txt">Más</span>
      </button>
    </nav>
  </div>
</template>

<style scoped>
.shell { display: flex; min-height: 100vh; min-height: 100dvh; }

.sidebar {
  width: var(--sidebar-w); flex: none; background: var(--panel);
  border-right: 1px solid var(--border); display: flex; flex-direction: column;
  padding-top: var(--safe-top); padding-left: var(--safe-left);
  position: sticky; top: 0; height: 100vh;
}
.marca { display: flex; align-items: center; gap: 0.6rem; padding: 1.1rem 1rem; font-size: 1.1rem; }
.logo-img { width: 2rem; height: 2rem; object-fit: contain; flex: none; }
.logo {
  display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px;
  background: var(--acento); color: var(--acento-texto); font-weight: 800; font-size: 0.8rem;
}
nav { display: flex; flex-direction: column; gap: 2px; padding: 0.4rem 0.6rem; flex: 1; overflow-y: auto; }
.grupo { display: flex; flex-direction: column; gap: 2px; }
.buscador { padding: 0 0.8rem 0.4rem; }
.buscador input {
  width: 100%; box-sizing: border-box; height: 36px; border-radius: var(--radio-sm); border: 1px solid var(--border);
  background: var(--bg); color: var(--text); padding: 0 0.7rem; font: inherit; font-size: 0.86rem;
}
.buscador input:focus { outline: 2px solid var(--acento); border-color: transparent; }
.sin-resultados { margin: 0.6rem 0.7rem; font-size: 0.85rem; color: var(--muted); }
.grupo-titulo {
  display: flex; align-items: center; gap: 0.4rem; width: 100%; margin: 0.35rem 0 0; padding: 0.45rem 0.7rem;
  border: 0; background: none; border-radius: var(--radio-sm); cursor: pointer; text-align: left;
  font: inherit; font-size: 0.68rem; font-weight: 800; letter-spacing: 0.08em;
  text-transform: uppercase; color: var(--muted);
}
.grupo-titulo:hover { background: var(--panel-2); color: var(--text); }
.grupo-titulo span:first-child { flex: 1; }
.grupo-titulo .cuenta { font-size: 0.65rem; background: var(--panel-2); border-radius: 999px; padding: 0 0.4rem; letter-spacing: 0; }
.grupo-titulo .flecha { transition: transform 0.15s ease; font-size: 0.8rem; }
.grupo.cerrado .flecha { transform: rotate(-90deg); }
.grupo-enlaces { display: flex; flex-direction: column; gap: 2px; }
.grupo.cerrado .grupo-enlaces { display: none; }
.sidebar.plegado:not(.asomado) .grupo.cerrado .grupo-enlaces { display: flex; }
.sidebar.plegado:not(.asomado) .buscador { display: none; }
.enlace {
  display: flex; align-items: center; gap: 0.6rem; padding: 0.6rem 0.7rem; border-radius: var(--radio-sm);
  color: var(--text); text-decoration: none; font-weight: 600; font-size: 0.92rem;
}
.enlace:hover { background: var(--panel-2); }
.enlace.activo { background: var(--acento-suave); color: var(--acento); }
.ico { width: 1.3rem; text-align: center; }

.pie-side { border-top: 1px solid var(--border); padding: 0.8rem; display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; }
.quien { display: flex; align-items: center; gap: 0.55rem; min-width: 0; }
.avatar { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; background: var(--acento-suave); color: var(--acento); font-weight: 700; flex: none; }
.datos { display: flex; flex-direction: column; min-width: 0; }
.datos .u { font-weight: 600; font-size: 0.85rem; overflow: hidden; text-overflow: ellipsis; }
.datos .r { font-size: 0.7rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; }

.area { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  display: flex; align-items: center; gap: 0.6rem;
  padding: calc(0.7rem + var(--safe-top)) calc(1.2rem + var(--safe-right)) 0.7rem calc(1.2rem + var(--safe-left));
  border-bottom: 1px solid var(--border); background: var(--panel); position: sticky; top: 0; z-index: 10;
}
.topbar::before {
  content: ''; position: absolute; top: 0; left: 0; right: 0;
  height: var(--safe-top); background: var(--franja-estado);
}
.topbar h1 { margin: 0; font-size: 1.15rem; }
.chip-actividad {
  font-size: 0.75rem; font-weight: 700; padding: 0.2rem 0.6rem; border-radius: 999px;
  background: var(--acento-suave); color: var(--acento); white-space: nowrap;
  max-width: 40vw; overflow: hidden; text-overflow: ellipsis;
}
.menu { display: inline-flex; }
.hueco-menu { width: 64px; flex: none; }
.sidebar { transition: width 0.18s ease; }
.sidebar.plegado { width: 64px; overflow: hidden; }
.sidebar.plegado .txt-menu, .sidebar.plegado .grupo-titulo { display: none; }
.sidebar.plegado .enlace { justify-content: center; padding: 0.6rem 0; }
.sidebar.plegado .marca { justify-content: center; padding: 1.1rem 0; }
.sidebar.plegado .pie-side { justify-content: center; padding: 0.8rem 0; }
.sidebar.plegado nav { padding: 0.4rem 0.4rem; }
.sidebar.plegado.asomado {
  width: var(--sidebar-w); position: fixed; left: 0; top: 0; z-index: 45; box-shadow: var(--sombra-md);
}
.sidebar.plegado.asomado .txt-menu { display: inline; }
.sidebar.plegado.asomado .datos { display: flex; }
.sidebar.plegado.asomado .grupo-titulo { display: flex; }
.sidebar.plegado.asomado .enlace { justify-content: flex-start; padding: 0.6rem 0.7rem; }
.sidebar.plegado.asomado .marca { justify-content: flex-start; padding: 1.1rem 1rem; }
.sidebar.plegado.asomado .pie-side { justify-content: space-between; padding: 0.8rem; }
.sidebar.plegado:not(.asomado) .quien .avatar { margin: 0; }
.contenido {
  flex: 1 0 auto; max-width: 1200px; width: 100%; margin: 0 auto;
  padding: 1.4rem calc(1.4rem + var(--safe-right))
           calc(1.4rem + var(--tabbar-h) + var(--kb)) calc(1.4rem + var(--safe-left));
}
.contenido.completo {
  max-width: none; height: calc(100dvh - 58px - var(--safe-top)); box-sizing: border-box;
  display: flex; flex-direction: column; min-height: 0; overflow: hidden; padding-bottom: 1rem;
}
.contenido.completo > * { flex: 1; min-height: 0; }
.contenido.inmersivo { display: flex; flex-direction: column; padding: 0; padding-bottom: var(--tabbar-h); max-width: none; width: 100%; }

.velo { display: none; }
.tabbar { display: none; }

@media (max-width: 820px) {
  .sidebar, .sidebar.plegado, .sidebar.plegado.asomado {
    position: fixed; z-index: 60; transform: translateX(-100%); transition: transform 0.2s ease;
    width: var(--sidebar-w); top: 0; left: 0;
  }
  .hueco-menu { display: none; }
  .sidebar.plegado .txt-menu, .sidebar.plegado .grupo-titulo { display: revert; }
  .sidebar.plegado .enlace { justify-content: flex-start; padding: 0.9rem; }
  .sidebar.abierto, .sidebar.plegado.abierto, .sidebar.plegado.asomado.abierto { transform: translateX(0); box-shadow: var(--sombra-md); }
  .velo { display: block; position: fixed; inset: 0; z-index: 50; background: rgba(2, 6, 23, 0.4); }

  .menu { display: none; }

  .enlace { padding: 0.9rem 0.9rem; font-size: 1.02rem; gap: 0.8rem; }
  .ico { font-size: 1.15rem; }

  .contenido.completo { height: auto; overflow: visible; padding-bottom: calc(1.4rem + var(--tabbar-h)); }
  .con-teclado .tabbar { display: none; }
  .tabbar {
    display: flex; flex-direction: row; gap: 0; overflow: visible; flex: none;
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 55;
    height: var(--tabbar-h);
    padding: 0 var(--safe-right) var(--safe-bottom) var(--safe-left);
    background: var(--panel); border-top: 1px solid var(--border); box-shadow: 0 -2px 10px rgba(2, 6, 23, 0.06);
  }
  .tab {
    flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
    gap: 0.15rem; color: var(--muted); text-decoration: none; font: inherit;
    background: none; border: none; padding: 0.3rem 0.2rem;
  }
  .tab .ico { font-size: 1.35rem; line-height: 1; }
  .tab .txt { font-size: 0.66rem; font-weight: 700; }
  .tab.activo, .tab-mas.activo { color: var(--acento); }
}
</style>
