<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, onActivated } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MenuAcciones from '../components/MenuAcciones.vue';
import UiModal from '../components/UiModal.vue';
import CompartirActividad from '../components/CompartirActividad.vue';
import { apiFetch, jsonOError } from '../api';
import { toast } from '../ui/toast';
import { alertaConAccion, confirmar, pedirTexto } from '../ui/alerta';
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
const route = useRoute();
const router = useRouter();
const actividad = useActividadStore();
const esSuper = computed(() => (auth.rol || '').toUpperCase().replace(/ /g, '_') === 'SUPER_USUARIO');

const lista = ref([]);
const papelera = ref([]);
const cargando = ref(true);
const filtro = ref('todas');
const busqueda = ref('');
const compartiendo = ref(null);
const bienvenida = ref(false);
const resaltada = ref(null);
let soltarCanal = null;
let pendiente = null;

const admin = computed(() => auth.puedeEditarPlano);
const puede = (p) => admin.value || permisos.puedeVer(p);
const veActividades = computed(() => puede('actividades'));

const estadoDe = (a) => {
  if (a.estadoEvento === 'CANCELADA') return 'cancelada';
  if (a.finalizada) return 'finalizada';
  if (a.publicada) return 'publicada';
  return 'borrador';
};
const ESTADOS = {
  borrador: { t: 'Borrador', c: 'badge-parcial' },
  publicada: { t: 'Publicada', c: 'badge-ok' },
  finalizada: { t: 'Finalizada', c: 'badge-muted' },
  cancelada: { t: 'Cancelada', c: 'badge-danger' },
};
const FILTROS = [['todas', 'Todas'], ['publicada', 'Publicadas'], ['borrador', 'Borradores'], ['finalizada', 'Finalizadas'], ['papelera', '🗑 Papelera']];

const tarjetas = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  return lista.value
    .map((a) => ({ ...a, clave: `f${a.id}`, estado: estadoDe(a) }))
    .filter((t) => filtro.value === 'todas' || t.estado === filtro.value || (filtro.value === 'finalizada' && t.estado === 'cancelada'))
    .filter((t) => !q || `${t.nombre} ${t.lugar || ''} ${t.tipo || ''}`.toLowerCase().includes(q))
    .sort((a, b) => Number(Boolean(b.activa)) - Number(Boolean(a.activa)) || (b.id - a.id));
});
const conteo = computed(() => {
  const c = { todas: 0, publicada: 0, borrador: 0, finalizada: 0, papelera: papelera.value.length };
  for (const a of lista.value) {
    c.todas++;
    const e = estadoDe(a);
    c[e === 'cancelada' ? 'finalizada' : e]++;
  }
  return c;
});

async function cargar(silencioso = false) {
  if (!silencioso) cargando.value = true;
  try {
    if (!veActividades.value) { lista.value = []; return; }
    const [l, p] = await Promise.all([
      jsonOError(await apiFetch('/api/app/ediciones/resumen'), 'No se pudieron cargar las actividades'),
      apiFetch('/api/app/ediciones/papelera').then((r) => (r.ok ? r.json() : [])).catch(() => []),
    ]);
    lista.value = l;
    papelera.value = p;
    abrirCompartirPedido();
  } catch (e) {
    if (!silencioso) toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function abrirCompartirPedido() {
  const id = Number(route.query.compartir);
  if (!id) return;
  const a = lista.value.find((x) => x.id === id);
  if (!a) return;
  bienvenida.value = route.query.bienvenida === '1';
  compartiendo.value = a;
  resaltada.value = id;
  router.replace(aModulo('actividades'));
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

const progreso = (a) => Math.min(100, Math.round((Math.min(Number(a.configuracionPaso || 1), 7) / 7) * 100));
const icono = (a) => (a.vendeEspacios ? tipoEspacio(a.tipoEspacio).icono : '🎟️');
const queVende = (a) => [a.vendeEntradas && 'Entradas', a.vendeEspacios && mayuscula(nombreEspacio(a, true))].filter(Boolean).join(' + ');
const fechaCorta = (s) => {
  if (!s) return '';
  try { return new Date(s).toLocaleString('es-BO', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }); } catch { return s; }
};

async function llamar(ruta, opciones, ok) {
  const r = await apiFetch(ruta, opciones);
  const d = await r.json().catch(() => ({}));
  if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo completar', 'error'); return null; }
  if (ok !== false) toast(d.mensaje || ok || 'Listo', 'ok');
  await cargar(true);
  cargarActividades(true);
  return d;
}

async function activar(a) {
  const si = await alertaConAccion(
    `¿Poner «${a.nombre}» en curso? El mapa de ventas, los reportes y las credenciales pasarán a usar esta actividad.`,
    'advertencia', () => {});
  if (!si) return;
  if (await llamar(`/api/app/ediciones/${a.id}/activar`, { method: 'POST' })) await actividad.cargar();
}

async function publicarONo(a, valor) {
  if (!valor && !(await confirmar('Deja de verse en la página pública y no recibe compras en línea hasta que la vuelvas a publicar. Nada se borra.', { titulo: '¿Ocultar de la página pública?', aceptar: 'Sí, ocultar' }))) return;
  await llamar(`/api/app/ediciones/${a.id}/publicar`, { method: 'POST', body: JSON.stringify({ valor }) });
}

async function finalizar(a, valor) {
  if (valor && !(await confirmar('Ya no se podrá comprar nada. Las ventas, reportes y el control de ingreso siguen funcionando. Puedes reabrirla después.', { titulo: '¿Cerrar las ventas?', aceptar: 'Sí, cerrar ventas' }))) return;
  await llamar(`/api/app/ediciones/${a.id}/finalizar`, { method: 'POST', body: JSON.stringify({ valor }) });
}

async function duplicar(a) {
  if (!(await confirmar(`Se crea un borrador nuevo con la misma configuración, categorías, precios, plano y entradas de «${a.nombre}». Los espacios quedan libres y sin ventas.`, { titulo: '¿Duplicar la actividad?', aceptar: 'Duplicar', tipo: 'info' }))) return;
  const d = await llamar(`/api/app/ediciones/${a.id}/duplicar`, { method: 'POST' });
  if (d?.id) ir('actividad', { id: d.id });
}

async function eliminar(t) {
  const escrito = await pedirTexto(
    `Se quitará «${t.nombre}» con su plano, categorías y espacios. Quedará ${7} días en la papelera por si quieres recuperarla. `
    + 'Escribe el nombre de la actividad para confirmar.',
    { titulo: 'Eliminar actividad', aceptar: 'Eliminar', marcador: t.nombre },
  );
  if (escrito == null) return;
  if (escrito.trim().toLowerCase() !== t.nombre.trim().toLowerCase()) { toast('El nombre no coincide: no se eliminó nada', 'advertencia'); return; }
  if (await llamar(`/api/app/ediciones/${t.id}`, { method: 'DELETE' })) await actividad.cargar();
}

async function restaurar(p) {
  const d = await llamar(`/api/app/ediciones/papelera/${p.id}/restaurar`, { method: 'POST' });
  if (d?.id) {
    filtro.value = 'borrador';
    resaltada.value = d.id;
  }
}

const dialogoEstado = reactive({ abierto: false, actividad: null, estado: 'POSPUESTA', motivo: '', nuevaFecha: '', publico: true, guardando: false });

function abrirEstado(a, estado) {
  Object.assign(dialogoEstado, {
    abierto: true, actividad: a, estado: estado || (a.estadoEvento !== 'NORMAL' ? a.estadoEvento : 'POSPUESTA'),
    motivo: a.estadoMotivo || '', nuevaFecha: a.estadoNuevaFecha || '', publico: a.estadoPublico !== false, guardando: false,
  });
}

async function guardarEstado(estado = dialogoEstado.estado) {
  dialogoEstado.guardando = true;
  try {
    const d = await llamar(`/api/app/ediciones/${dialogoEstado.actividad.id}/estado`, {
      method: 'POST',
      body: JSON.stringify({ estado, motivo: dialogoEstado.motivo, nuevaFecha: dialogoEstado.nuevaFecha, publico: dialogoEstado.publico }),
    });
    if (d) dialogoEstado.abierto = false;
  } finally {
    dialogoEstado.guardando = false;
  }
}

function acciones(a) {
  const conRegistros = Number(a.registros || 0) > 0;
  const lista = [
    { txt: '⚙️ Configurar', accion: () => configurar(a) },
    { txt: '📣 Compartir: enlace y QR', deshabilitado: !a.publicada, titulo: a.publicada ? '' : 'Publícala primero', accion: () => { bienvenida.value = false; compartiendo.value = a; } },
    { txt: '👁 Ver página pública', deshabilitado: !a.publicada, titulo: a.publicada ? '' : 'Publícala primero', accion: () => abrirPublica({ v: 'actividad', edicion: a.id }) },
  ];
  if (a.publicada) lista.push({ txt: '🙈 Ocultar de la página pública', accion: () => publicarONo(a, false) });
  else if (Number(a.configuracionPaso || 0) >= 99) lista.push({ txt: '🚀 Publicar de nuevo', accion: () => publicarONo(a, true) });
  lista.push(
    { txt: '📌 Poner en curso', deshabilitado: a.activa || !esSuper.value, titulo: !esSuper.value ? 'Solo el Superadministrador cambia la actividad en curso' : '', accion: () => activar(a) },
    { txt: '📄 Duplicar como nueva', accion: () => duplicar(a) },
    { separador: true },
  );
  if (a.estadoEvento && a.estadoEvento !== 'NORMAL') {
    lista.push({ txt: '✅ Quitar aviso y volver a la normalidad', accion: () => guardarEstadoDirecto(a) });
  } else {
    lista.push({ txt: '⏸ Posponer o cancelar…', accion: () => abrirEstado(a) });
  }
  if (a.estadoEvento !== 'CANCELADA') {
    lista.push(a.finalizada
      ? { txt: '🔓 Reabrir ventas', accion: () => finalizar(a, false) }
      : { txt: '🔒 Cerrar ventas (terminó)', deshabilitado: !a.publicada, accion: () => finalizar(a, true) });
  }
  lista.push({
    txt: '🗑 Eliminar', peligro: true, deshabilitado: a.activa || conRegistros || !puede('actividades'),
    titulo: a.activa ? 'Pon otra actividad en curso antes de eliminar esta'
      : conRegistros ? `Tiene ${a.registros} venta(s) o solicitud(es): usa «Posponer o cancelar»` : '',
    accion: () => eliminar(a),
  });
  return lista;
}

async function guardarEstadoDirecto(a) {
  Object.assign(dialogoEstado, { actividad: a, motivo: '', nuevaFecha: '', publico: true });
  await guardarEstado('NORMAL');
}

watch(() => route.query.compartir, () => abrirCompartirPedido());

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
      <div class="filtros desliza-h" role="tablist" aria-label="Filtrar actividades">
        <button v-for="[k, t] in FILTROS" :key="k" type="button" role="tab" class="chip" :class="{ on: filtro === k, papelera: k === 'papelera' }"
                :aria-selected="filtro === k" @click="filtro = k">{{ t }} <span class="cuenta">{{ conteo[k] }}</span></button>
      </div>
      <div class="derecha">
        <input v-model="busqueda" type="search" class="control buscar" placeholder="Buscar…" aria-label="Buscar actividad" />
        <button v-if="veActividades" class="btn btn-primario" @click="ir('actividad', { nuevo: '1' })">＋ Nueva actividad</button>
      </div>
    </div>

    <p v-if="cargando" class="vacio">Cargando…</p>

    <template v-else-if="filtro === 'papelera'">
      <p class="muted nota">Las actividades eliminadas se guardan aquí {{ 7 }} días. Al restaurar vuelve como <b>borrador</b>, con su configuración, categorías, precios, plano y entradas, lista para revisar y publicar.</p>
      <div v-if="!papelera.length" class="vacio-grande card"><span class="grande">🗑</span><h3>La papelera está vacía</h3></div>
      <ul v-else class="lista-papelera">
        <li v-for="p in papelera" :key="p.id" class="card">
          <div>
            <strong>{{ p.nombre }}</strong>
            <span class="muted chico">Eliminada el {{ fechaCorta(p.eliminadaEn) }} · se borra definitivamente en {{ p.diasRestantes }} día{{ p.diasRestantes === 1 ? '' : 's' }}</span>
          </div>
          <button class="btn btn-primario btn-sm" @click="restaurar(p)">↩ Restaurar</button>
        </li>
      </ul>
    </template>

    <div v-else-if="!tarjetas.length" class="vacio-grande card">
      <span class="grande">🎉</span>
      <h3>{{ lista.length ? 'Nada coincide con el filtro' : 'Crea tu primera actividad' }}</h3>
      <p class="muted">Un asistente te guía paso a paso: datos, qué vendes, condiciones, cobro, plano y credenciales.</p>
      <button v-if="veActividades && !lista.length" class="btn btn-primario" @click="ir('actividad', { nuevo: '1' })">＋ Nueva actividad</button>
    </div>
    <div v-else class="rejilla">
      <article v-for="t in tarjetas" :key="t.clave" class="card tarjeta" :class="[t.estado, { activa: t.activa, resaltada: resaltada === t.id }]">
        <div class="portada" :style="t.portadaUrl ? { backgroundImage: `url(${urlApi(t.portadaUrl)})` } : null">
          <span class="icono-grande">{{ icono(t) }}</span>
          <div class="chips">
            <span class="badge" :class="ESTADOS[t.estado].c">{{ ESTADOS[t.estado].t }}</span>
            <span v-if="t.activa" class="badge en-curso" title="La usan el mapa de ventas, los reportes y las credenciales">📌 En curso</span>
            <span v-if="t.estadoEvento === 'POSPUESTA'" class="badge pospuesta" :title="t.estadoMotivo">⏸ Pospuesta</span>
            <span v-if="t.antesDeAbrir" class="badge abre" title="La venta en línea abre automáticamente en esa fecha">⏳ Abre {{ fechaCorta(t.ventaDesde) }}</span>
          </div>
          <span class="menu"><MenuAcciones :acciones="acciones(t)" texto="Opciones" :etiqueta="`Opciones de ${t.nombre}`" /></span>
        </div>
        <div class="info">
          <span class="tipo">{{ t.tipo || 'Actividad' }} · {{ queVende(t) || 'Sin definir' }}</span>
          <h3>{{ t.nombre }}</h3>
          <span class="muted chico">📅 {{ t.fechasTexto || 'Sin fechas' }}<template v-if="t.lugar"> · 📍 {{ t.lugar }}</template></span>
          <span v-if="t.estadoEvento !== 'NORMAL' && t.estadoMotivo" class="aviso-estado" :class="t.estadoEvento.toLowerCase()">
            {{ t.estadoEvento === 'POSPUESTA' ? `Nueva fecha: ${t.estadoNuevaFecha}` : 'Cancelada' }} · {{ t.estadoMotivo }}
            <small v-if="!t.estadoPublico">(solo interno)</small>
          </span>
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
            <button v-if="t.publicada" class="btn btn-sm" @click="bienvenida = false; compartiendo = t">📣 Compartir</button>
            <button class="btn btn-sm" @click="ir('cobros', { edicion: t.id })">💰 Cobros</button>
          </div>
        </template>
      </article>
    </div>

    <CompartirActividad v-if="compartiendo" :actividad="compartiendo" :bienvenida="bienvenida" @cerrar="compartiendo = null; bienvenida = false" />

    <UiModal v-if="dialogoEstado.abierto" :titulo="`¿Qué pasa con «${dialogoEstado.actividad.nombre}»?`" ancho="560px" @cerrar="dialogoEstado.abierto = false">
      <div class="estado-form">
        <div class="opciones-estado" role="radiogroup">
          <button type="button" class="opcion" :class="{ on: dialogoEstado.estado === 'POSPUESTA' }" @click="dialogoEstado.estado = 'POSPUESTA'">
            <strong>⏸ Posponer</strong>
            <span class="muted">Cambia de fecha. Las ventas siguen y se conservan; avisa a todos la nueva fecha.</span>
          </button>
          <button type="button" class="opcion peligro" :class="{ on: dialogoEstado.estado === 'CANCELADA' }" @click="dialogoEstado.estado = 'CANCELADA'">
            <strong>✖ Cancelar</strong>
            <span class="muted">No se realizará. Se cierran las ventas; lo vendido se conserva para devoluciones y reportes.</span>
          </button>
        </div>
        <label class="campo"><span>Motivo *</span>
          <textarea v-model="dialogoEstado.motivo" class="control" rows="2" maxlength="500" placeholder="Ej.: Por lluvia intensa prevista en la ciudad"></textarea>
        </label>
        <label v-if="dialogoEstado.estado === 'POSPUESTA'" class="campo"><span>Nueva fecha *</span>
          <input v-model="dialogoEstado.nuevaFecha" class="control" maxlength="160" placeholder="Ej.: sábado 20 de diciembre, 20:00 — o «por definir»" />
          <small class="muted">Cuando la tengas confirmada, actualiza también los días en «Configurar».</small>
        </label>
        <label class="check"><input v-model="dialogoEstado.publico" type="checkbox" /> Mostrar el aviso en la página pública</label>
        <p class="muted chico">{{ dialogoEstado.publico
          ? 'Los visitantes verán un aviso destacado con el motivo' + (dialogoEstado.estado === 'POSPUESTA' ? ' y la nueva fecha.' : '.')
          : dialogoEstado.estado === 'CANCELADA' ? 'Solo lo verá tu equipo; la actividad se ocultará de la página pública.' : 'Solo lo verá tu equipo.' }}</p>
      </div>
      <template #pie>
        <button class="btn btn-fantasma" @click="dialogoEstado.abierto = false">Volver</button>
        <button class="btn" :class="dialogoEstado.estado === 'CANCELADA' ? 'btn-peligro' : 'btn-primario'"
                :disabled="dialogoEstado.guardando || !dialogoEstado.motivo.trim() || (dialogoEstado.estado === 'POSPUESTA' && !dialogoEstado.nuevaFecha.trim())"
                @click="guardarEstado()">{{ dialogoEstado.estado === 'CANCELADA' ? 'Cancelar la actividad' : 'Posponer la actividad' }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.actividades { display: flex; flex-direction: column; gap: 1rem; }
.cab { display: flex; justify-content: space-between; align-items: center; gap: 0.8rem; flex-wrap: wrap; }
.filtros { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.derecha { display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap; }
.chip { border: 1px solid var(--border); background: var(--panel); color: var(--muted); border-radius: 999px; padding: 0.35rem 0.8rem; font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; display: inline-flex; gap: 0.35rem; align-items: center; white-space: nowrap; }
.chip.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.chip.papelera:not(.on) { border-style: dashed; }
.cuenta { font-size: 0.72rem; background: rgba(127, 127, 127, 0.18); border-radius: 999px; padding: 0 0.4rem; }
.buscar { width: 200px; }
.nota { margin: 0; line-height: 1.5; }
.rejilla { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 1rem; }
.tarjeta { padding: 0; overflow: hidden; display: flex; flex-direction: column; gap: 0.7rem; transition: transform 0.15s, box-shadow 0.15s; }
.tarjeta:hover { transform: translateY(-2px); box-shadow: var(--sombra-md); }
.tarjeta.activa { box-shadow: 0 0 0 2px var(--acento); }
.tarjeta.resaltada { animation: resaltar 1.6s ease-out 2; }
@keyframes resaltar { 0% { box-shadow: 0 0 0 0 var(--acento); } 50% { box-shadow: 0 0 0 6px color-mix(in srgb, var(--acento) 35%, transparent); } 100% { box-shadow: 0 0 0 0 transparent; } }
.tarjeta > :not(.portada) { margin: 0 1rem; }
.tarjeta > :last-child { margin-bottom: 1rem; }
.portada {
  position: relative; height: 96px; display: flex; align-items: flex-start; gap: 0.4rem; padding: 0.6rem;
  background: linear-gradient(135deg, var(--acento), #a855f7) center / cover no-repeat;
}
.tarjeta.borrador .portada { background-image: linear-gradient(135deg, #94a3b8, #64748b); }
.tarjeta.finalizada .portada, .tarjeta.cancelada .portada { filter: grayscale(0.7); }
.portada[style] { background-size: cover; }
.chips { display: flex; gap: 0.3rem; flex-wrap: wrap; align-items: flex-start; min-width: 0; }
.icono-grande { position: absolute; left: 0.8rem; bottom: -18px; width: 44px; height: 44px; display: grid; place-items: center; font-size: 1.5rem; background: var(--panel); border-radius: 12px; box-shadow: var(--sombra-md); }
.portada .menu { margin-left: auto; flex: none; }
.portada .menu :deep(.puntos) { background: #fff; color: #0f172a; border: 1px solid rgba(15, 23, 42, 0.15); box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18); }
.portada .menu :deep(.puntos:hover) { background: #eef2ff; color: #3730a3; }
.portada .badge { background: rgba(255, 255, 255, 0.92); color: #0f172a; font-weight: 800; }
.portada .badge-ok { color: #15803d; }
.portada .badge-parcial { color: #b45309; }
.portada .badge-danger { color: #b91c1c; }
.portada .pospuesta { color: #b45309; }
.portada .abre { color: #1d4ed8; }
.en-curso { background: #fff; color: var(--acento); }
.info { display: flex; flex-direction: column; gap: 0.2rem; padding-top: 0.6rem; }
.tipo { font-size: 0.72rem; font-weight: 700; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.info h3 { margin: 0; font-size: 1.1rem; }
.chico { font-size: 0.82rem; }
.aviso-estado { margin-top: 0.3rem; font-size: 0.8rem; font-weight: 600; padding: 0.3rem 0.55rem; border-radius: 8px; background: color-mix(in srgb, var(--tramite, #d97706) 14%, transparent); }
.aviso-estado.cancelada { background: color-mix(in srgb, var(--danger) 14%, transparent); }
.aviso-estado small { font-weight: 500; color: var(--muted); }
.progreso { height: 8px; border-radius: 999px; background: var(--border); overflow: hidden; }
.progreso span { display: block; height: 100%; background: var(--acento); border-radius: inherit; }
.tarjeta dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(80px, 1fr)); gap: 0.4rem; margin: 0 1rem; }
.tarjeta dt { font-size: 0.7rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.tarjeta dd { margin: 0; font-size: 1.15rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.tarjeta dd small { font-size: 0.75rem; color: var(--muted); font-weight: 600; }
.atajos { display: flex; gap: 0.4rem; flex-wrap: wrap; margin-top: auto !important; padding-top: 0.6rem; border-top: 1px dashed var(--border); }
.vacio { text-align: center; color: var(--muted); padding: 2rem; }
.vacio-grande { text-align: center; padding: 2rem 1rem; display: flex; flex-direction: column; align-items: center; gap: 0.4rem; }
.vacio-grande h3 { margin: 0; }
.vacio-grande p { margin: 0 0 0.6rem; max-width: 420px; }
.grande { font-size: 2.4rem; }
.lista-papelera { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.lista-papelera li { display: flex; align-items: center; justify-content: space-between; gap: 0.8rem; padding: 0.8rem 1rem; flex-wrap: wrap; }
.lista-papelera li > div { display: flex; flex-direction: column; gap: 0.15rem; }
.estado-form { display: flex; flex-direction: column; gap: 0.8rem; }
.opciones-estado { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; }
.opcion { display: flex; flex-direction: column; gap: 0.25rem; text-align: left; padding: 0.7rem; border-radius: 10px; border: 2px solid var(--border); background: var(--panel); color: inherit; font: inherit; cursor: pointer; }
.opcion .muted { font-size: 0.8rem; line-height: 1.35; }
.opcion.on { border-color: var(--acento); background: color-mix(in srgb, var(--acento) 10%, var(--panel)); }
.opcion.peligro.on { border-color: var(--danger); background: color-mix(in srgb, var(--danger) 10%, var(--panel)); }
.campo { display: flex; flex-direction: column; gap: 0.3rem; }
.campo > span { font-size: 0.85rem; font-weight: 600; color: var(--muted); }
.check { display: flex; align-items: center; gap: 0.45rem; font-weight: 600; }
@media (max-width: 600px) {
  .portada { height: 84px; }
  .buscar { flex: 1; width: auto; }
  .derecha { width: 100%; }
  .filtros { overflow-x: auto; flex-wrap: nowrap; width: 100%; scrollbar-width: none; }
  .opciones-estado { grid-template-columns: 1fr; }
}
</style>
