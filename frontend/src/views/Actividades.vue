<script setup>
import { ref, computed, onMounted, onUnmounted, onActivated } from 'vue';
import { useRouter } from 'vue-router';
import MenuAcciones from '../components/MenuAcciones.vue';
import { apiFetch, jsonOError } from '../api';
import { toast } from '../ui/toast';
import { alertaConAccion, pedirTexto } from '../ui/alerta';
import { cargarActividades } from '../ui/actividadElegida';
import { useAuthStore } from '../stores/auth';
import { useActividadStore } from '../stores/actividad';
import { usePermisosStore } from '../stores/permisos';
import { aModulo } from '../modulos';
import { alCambiarPublico } from '../ui/canalPublico';
import { url as urlApi } from '../config.js';
import { tipoEspacio, nombreEspacio, mayuscula } from '../ui/espacios';

const auth = useAuthStore();
const permisos = usePermisosStore();
const router = useRouter();
const actividad = useActividadStore();
const esSuper = computed(() => (auth.rol || '').toUpperCase().replace(/ /g, '_') === 'SUPER_USUARIO');

const lista = ref([]);
const eventos = ref([]);
const cargando = ref(true);
const filtro = ref('todas');
const busqueda = ref('');
let soltarCanal = null;
let pendiente = null;

const admin = computed(() => auth.puedeEditarPlano);
const puede = (p) => admin.value || permisos.puedeVer(p);
const veActividades = computed(() => puede('actividades'));
const veEventos = computed(() => puede('mesas') || puede('mesas-diseno'));

const estadoDe = (a) => {
  if (a.clase === 'evento') return a.publicado ? 'publicada' : 'borrador';
  if (a.finalizada) return 'finalizada';
  if (a.publicada) return 'publicada';
  return 'borrador';
};
const ESTADOS = {
  borrador: { t: 'Borrador', c: 'badge-parcial' },
  publicada: { t: 'Publicada', c: 'badge-ok' },
  finalizada: { t: 'Finalizada', c: 'badge-muted' },
};
const FILTROS = [['todas', 'Todas'], ['publicada', 'Publicadas'], ['borrador', 'Borradores'], ['finalizada', 'Finalizadas']];

const tarjetas = computed(() => {
  const propias = lista.value.map((a) => ({ ...a, clase: 'actividad', clave: `f${a.id}` }));
  const evs = eventos.value.map((e) => ({ ...e, clase: 'evento', clave: `e${e.id}` }));
  const q = busqueda.value.trim().toLowerCase();
  return [...propias, ...evs]
    .map((t) => ({ ...t, estado: estadoDe(t) }))
    .filter((t) => filtro.value === 'todas' || t.estado === filtro.value)
    .filter((t) => !q || `${t.nombre} ${t.localNombre || ''} ${t.lugar || ''} ${t.tipo || ''}`.toLowerCase().includes(q))
    .sort((a, b) => Number(Boolean(b.activa)) - Number(Boolean(a.activa)) || (b.id - a.id));
});
const conteo = computed(() => {
  const c = { todas: 0, publicada: 0, borrador: 0, finalizada: 0 };
  for (const a of lista.value) { c.todas++; c[estadoDe({ ...a, clase: 'actividad' })]++; }
  for (const e of eventos.value) { c.todas++; c[estadoDe({ ...e, clase: 'evento' })]++; }
  return c;
});
const porValidar = computed(() => eventos.value.reduce((s, e) => s + Number(e.porValidar || 0), 0));

async function cargar(silencioso = false) {
  if (!silencioso) cargando.value = true;
  try {
    const [f, e] = await Promise.all([
      veActividades.value ? jsonOError(await apiFetch('/api/app/ediciones/resumen'), 'No se pudieron cargar las actividades') : [],
      veEventos.value ? jsonOError(await apiFetch('/api/app/mesas/eventos'), 'No se pudieron cargar los eventos') : [],
    ]);
    lista.value = f;
    eventos.value = e;
  } catch (e) {
    if (!silencioso) toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function ir(m, query = {}) {
  router.push(aModulo(m, query));
}

function configurar(a, paso) {
  ir('actividad', paso ? { id: a.id, paso } : { id: a.id });
}

function abrirPublica(query) {
  window.open(router.resolve({ path: '/', query }).href, '_blank', 'noopener');
}

const hora = (s) => (s ? s.replace('T', ' ').slice(0, 16) : '—');
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO')}`;
const progreso = (a) => Math.min(100, Math.round((Math.min(Number(a.configuracionPaso || 1), 7) / 7) * 100));
const icono = (a) => (a.vendeEspacios ? tipoEspacio(a.tipoEspacio).icono : '🎟️');
const queVende = (a) => [a.vendeEntradas && 'Entradas', a.vendeEspacios && mayuscula(nombreEspacio(a, true))].filter(Boolean).join(' + ');

async function activar(a) {
  const si = await alertaConAccion(
    `¿Poner «${a.nombre}» en curso? El mapa de ventas, los reportes y las credenciales pasarán a usar esta actividad.`,
    'advertencia', () => {});
  if (!si) return;
  const r = await apiFetch(`/api/app/ediciones/${a.id}/activar`, { method: 'POST' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo activar', 'error'); return; }
  toast(d.mensaje, 'ok');
  await cargar();
  await actividad.cargar();
}

async function eliminar(t) {
  const propia = t.clase === 'actividad';
  const escrito = await pedirTexto(
    `Se borrará «${t.nombre}» con su plano y sus ${propia ? 'categorías y espacios' : 'mesas'}. No se puede deshacer. `
    + 'Si ya tiene ventas, el sistema no lo permitirá para conservar el historial. '
    + 'Escribe el nombre de la actividad para confirmar.',
    { titulo: 'Eliminar actividad', aceptar: 'Eliminar', marcador: t.nombre },
  );
  if (escrito == null) return;
  if (escrito.trim().toLowerCase() !== t.nombre.trim().toLowerCase()) { toast('El nombre no coincide: no se eliminó nada', 'advertencia'); return; }
  const r = await apiFetch(propia ? `/api/app/ediciones/${t.id}` : `/api/app/mesas/eventos/${t.id}`, { method: 'DELETE' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo eliminar', 'error'); return; }
  toast(d.mensaje || 'Actividad eliminada', 'ok');
  await cargar();
  await Promise.all([actividad.cargar(), cargarActividades(true)]);
}

function acciones(a) {
  return [
    { txt: '⚙️ Configurar', accion: () => configurar(a) },
    { txt: '👁 Ver página pública', deshabilitado: !a.publicada, titulo: a.publicada ? '' : 'Publícala primero', accion: () => abrirPublica({ v: 'actividad', edicion: a.id }) },
    {
      txt: '📌 Poner en curso', deshabilitado: a.activa || !esSuper.value,
      titulo: !esSuper.value ? 'Solo el Superadministrador cambia la actividad en curso' : '', accion: () => activar(a),
    },
    {
      txt: '🗑 Eliminar', peligro: true, deshabilitado: a.activa || !puede('actividades'),
      titulo: a.activa ? 'Pon otra actividad en curso antes de eliminar esta' : '', accion: () => eliminar(a),
    },
  ];
}

function accionesEvento(e) {
  return [
    { txt: '👁 Ver página pública', deshabilitado: !e.publicado, accion: () => abrirPublica({ evento: e.id }) },
    { txt: '⚙️ Organizar', deshabilitado: !puede('mesas-diseno'), accion: () => ir('mesas', { evento: e.id, tab: 'evento' }) },
    { txt: '🗑 Eliminar', peligro: true, deshabilitado: !puede('mesas-diseno'), accion: () => eliminar(e) },
  ];
}

onMounted(() => {
  cargar();
  soltarCanal = alCambiarPublico(() => {
    clearTimeout(pendiente);
    pendiente = setTimeout(() => cargar(true), 700);
  });
});
onActivated(() => cargar(true));
onUnmounted(() => { soltarCanal?.(); clearTimeout(pendiente); });
</script>

<template>
  <div class="actividades">
    <div class="cab">
      <div class="filtros" role="tablist" aria-label="Filtrar actividades">
        <button v-for="[k, t] in FILTROS" :key="k" type="button" role="tab" class="chip" :class="{ on: filtro === k }"
                :aria-selected="filtro === k" @click="filtro = k">{{ t }} <span class="cuenta">{{ conteo[k] }}</span></button>
      </div>
      <div class="derecha">
        <input v-model="busqueda" type="search" class="control buscar" placeholder="Buscar…" aria-label="Buscar actividad" />
        <button v-if="veActividades" class="btn btn-primario" @click="ir('actividad', { nuevo: '1' })">＋ Nueva actividad</button>
      </div>
    </div>
    <p v-if="porValidar" class="alerta-validar" role="status">
      Hay <b>{{ porValidar }}</b> pago{{ porValidar === 1 ? '' : 's' }} de mesas esperando revisión.
      <button class="btn btn-sm" @click="ir('solicitudes', { tab: 'mesas' })">Revisar</button>
    </p>

    <p v-if="cargando" class="vacio">Cargando…</p>
    <div v-else-if="!tarjetas.length" class="vacio-grande card">
      <span class="grande">🎉</span>
      <h3>{{ lista.length || eventos.length ? 'Nada coincide con el filtro' : 'Crea tu primera actividad' }}</h3>
      <p class="muted">Un asistente te guía paso a paso: datos, qué vendes, condiciones, cobro, plano y credenciales.</p>
      <button v-if="veActividades && !lista.length" class="btn btn-primario" @click="ir('actividad', { nuevo: '1' })">＋ Nueva actividad</button>
    </div>
    <div v-else class="rejilla">
      <template v-for="t in tarjetas" :key="t.clave">
        <article v-if="t.clase === 'actividad'" class="card tarjeta" :class="[t.estado, { activa: t.activa }]">
          <div class="portada" :style="t.portadaUrl ? { backgroundImage: `url(${urlApi(t.portadaUrl)})` } : null">
            <span class="icono-grande">{{ icono(t) }}</span>
            <span class="badge" :class="ESTADOS[t.estado].c">{{ ESTADOS[t.estado].t }}</span>
            <span v-if="t.activa" class="badge en-curso" title="La usan el mapa de ventas, los reportes y las credenciales">📌 En curso</span>
            <span class="menu"><MenuAcciones :acciones="acciones(t)" :etiqueta="`Acciones de ${t.nombre}`" /></span>
          </div>
          <div class="info">
            <span class="tipo">{{ t.tipo || 'Actividad' }} · {{ queVende(t) || 'Sin definir' }}</span>
            <h3>{{ t.nombre }}</h3>
            <span class="muted chico">📅 {{ t.fechasTexto || 'Sin fechas' }}<template v-if="t.lugar"> · 📍 {{ t.lugar }}</template></span>
          </div>
          <template v-if="t.estado === 'borrador' && t.configuracionPaso < 99">
            <div class="progreso" :title="`Configuración ${progreso(t)}%`"><span :style="{ width: `${progreso(t)}%` }"></span></div>
            <button class="btn btn-primario" @click="configurar(t)">Continuar configuración →</button>
          </template>
          <template v-else>
            <dl>
              <div v-if="t.vendeEspacios"><dt>{{ mayuscula(nombreEspacio(t, true)) }}</dt><dd>{{ t.vendidos }}<small>/{{ t.puestos }}</small></dd></div>
              <div v-if="t.vendeEntradas"><dt>Tipos de entrada</dt><dd>{{ t.tiposEntrada }}</dd></div>
              <div><dt>Ventas</dt><dd>{{ t.ventas }}</dd></div>
            </dl>
            <div class="atajos">
              <button class="btn btn-sm" @click="configurar(t)">⚙️ Configurar</button>
              <button v-if="t.vendeEspacios && puede('editor')" class="btn btn-sm" @click="ir('editor', { edicion: t.id })">🗺️ Plano</button>
              <button v-if="t.activa && puede('mapa')" class="btn btn-sm" @click="ir('mapa')">🧾 Vender</button>
              <button v-if="puede('interesados') || puede('inscripciones')" class="btn btn-sm" @click="ir('solicitudes', { tab: 'ferias' })">💰 Cobros</button>
            </div>
          </template>
        </article>

        <article v-else class="card tarjeta evento" :class="t.estado">
          <div class="portada">
            <span class="icono-grande">🍸</span>
            <span class="badge" :class="ESTADOS[t.estado].c">{{ ESTADOS[t.estado].t }}</span>
            <span class="menu"><MenuAcciones :acciones="accionesEvento(t)" :etiqueta="`Acciones de ${t.nombre}`" /></span>
          </div>
          <div class="info">
            <span class="tipo">Evento con mesas · versión anterior</span>
            <h3>{{ t.nombre }}</h3>
            <span class="muted chico">📅 {{ hora(t.fechaInicio) }} · 📍 {{ t.localNombre }}</span>
          </div>
          <dl>
            <div><dt>Mesas</dt><dd>{{ t.vendidas }}<small>/{{ t.mesas }}</small></dd></div>
            <div><dt>Por validar</dt><dd :class="{ urgente: t.porValidar }">{{ t.porValidar }}</dd></div>
            <div><dt>Cobrado</dt><dd class="monto">{{ bs(t.confirmado) }}</dd></div>
          </dl>
          <div class="atajos">
            <button v-if="puede('mapa')" class="btn btn-sm" @click="ir('mapa', { evento: t.id })">🧾 Vender</button>
            <button v-if="puede('mesas')" class="btn btn-sm" :class="{ 'btn-primario': t.porValidar }" @click="ir('solicitudes', { tab: 'mesas', evento: t.id })">💰 Cobros</button>
            <button v-if="puede('mesas-diseno')" class="btn btn-sm" @click="ir('mesas', { evento: t.id, tab: 'plano' })">🗺️ Plano</button>
          </div>
        </article>
      </template>
    </div>
  </div>
</template>

<style scoped>
.actividades { display: flex; flex-direction: column; gap: 1rem; }
.cab { display: flex; justify-content: space-between; align-items: center; gap: 0.8rem; flex-wrap: wrap; }
.filtros { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.derecha { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
.chip { border: 1px solid var(--border); background: var(--panel); color: var(--muted); border-radius: 999px; padding: 0.35rem 0.8rem; font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; display: inline-flex; gap: 0.35rem; align-items: center; }
.chip.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.cuenta { font-size: 0.72rem; background: rgba(127, 127, 127, 0.18); border-radius: 999px; padding: 0 0.4rem; }
.buscar { width: 200px; }
.alerta-validar { margin: 0; padding: 0.6rem 0.9rem; border-radius: var(--radio-sm); background: var(--acento-suave); color: var(--acento); font-weight: 600; display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.rejilla { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 1rem; }
.tarjeta { padding: 0; overflow: hidden; display: flex; flex-direction: column; gap: 0.7rem; transition: transform 0.15s, box-shadow 0.15s; }
.tarjeta:hover { transform: translateY(-2px); box-shadow: var(--sombra-md); }
.tarjeta.activa { box-shadow: 0 0 0 2px var(--acento); }
.tarjeta > :not(.portada) { margin: 0 1rem; }
.tarjeta > :last-child { margin-bottom: 1rem; }
.portada {
  position: relative; height: 96px; display: flex; align-items: flex-start; gap: 0.4rem; padding: 0.6rem;
  background: linear-gradient(135deg, var(--acento), #a855f7) center / cover no-repeat;
}
.tarjeta.borrador .portada { background-image: linear-gradient(135deg, #94a3b8, #64748b); }
.tarjeta.finalizada .portada { filter: grayscale(0.7); }
.tarjeta.evento .portada { background-image: linear-gradient(135deg, #0ea5e9, #6366f1); }
.portada[style] { background-size: cover; }
.icono-grande { position: absolute; left: 0.8rem; bottom: -18px; width: 44px; height: 44px; display: grid; place-items: center; font-size: 1.5rem; background: var(--panel); border-radius: 12px; box-shadow: var(--sombra-md); }
.portada .menu { margin-left: auto; background: rgba(255, 255, 255, 0.9); border-radius: 8px; color: #0f172a; }
.portada .badge { background: rgba(255, 255, 255, 0.92); color: #0f172a; font-weight: 800; }
.portada .badge-ok { color: #15803d; }
.portada .badge-parcial { color: #b45309; }
.en-curso { background: #fff; color: var(--acento); }
.info { display: flex; flex-direction: column; gap: 0.2rem; padding-top: 0.6rem; }
.tipo { font-size: 0.72rem; font-weight: 700; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.info h3 { margin: 0; font-size: 1.1rem; }
.chico { font-size: 0.82rem; }
.progreso { height: 8px; border-radius: 999px; background: var(--border); overflow: hidden; }
.progreso span { display: block; height: 100%; background: var(--acento); border-radius: inherit; }
.tarjeta dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(80px, 1fr)); gap: 0.4rem; margin: 0 1rem; }
.tarjeta dt { font-size: 0.7rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.tarjeta dd { margin: 0; font-size: 1.15rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.tarjeta dd small { font-size: 0.75rem; color: var(--muted); font-weight: 600; }
.urgente { color: var(--acento); }
.monto { font-size: 0.95rem !important; }
.atajos { display: flex; gap: 0.4rem; flex-wrap: wrap; margin-top: auto !important; padding-top: 0.6rem; border-top: 1px dashed var(--border); }
.vacio { text-align: center; color: var(--muted); padding: 2rem; }
.vacio-grande { text-align: center; padding: 2rem 1rem; display: flex; flex-direction: column; align-items: center; gap: 0.4rem; }
.vacio-grande h3 { margin: 0; }
.vacio-grande p { margin: 0 0 0.6rem; max-width: 420px; }
.grande { font-size: 2.4rem; }
@media (max-width: 600px) {
  .portada { height: 72px; }
  .buscar { flex: 1; width: auto; }
  .derecha { width: 100%; }
  .filtros { overflow-x: auto; flex-wrap: nowrap; width: 100%; scrollbar-width: none; }
}
</style>
