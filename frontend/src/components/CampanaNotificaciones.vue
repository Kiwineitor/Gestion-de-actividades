<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { useNotificacionesStore } from '../stores/notificaciones';
import { aModulo } from '../modulos';
import NotificacionItem from './NotificacionItem.vue';
import UiModal from './UiModal.vue';

const props = defineProps({
  pendientes: { type: Array, default: () => [] },
});

const router = useRouter();
const auth = useAuthStore();
const tienda = usePuestosStore();
const notif = useNotificacionesStore();

const abierto = ref(false);
const modal = ref(false);
const raiz = ref(null);

const ICONOS = {
  SOLICITUD_CANCELACION: '📋', APROBACION_CANCELACION: '✅', RECHAZO_CANCELACION: '❌',
  OBSERVACION_ADMIN: '💬', VENTA_REGISTRADA: '💰', SISTEMA: '⚙️',
};

const recientes = computed(() => notif.lista.slice(0, 6));
const pendientesVisibles = computed(() => props.pendientes.filter((p) => p.cantidad > 0));
const total = computed(() => notif.countNoLeidas + pendientesVisibles.value.reduce((s, p) => s + p.cantidad, 0));

function hace(iso) {
  if (!iso) return '';
  const min = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (min < 1) return 'ahora';
  if (min < 60) return `hace ${min} min`;
  const h = Math.round(min / 60);
  if (h < 24) return `hace ${h} h`;
  const d = Math.round(h / 24);
  return d === 1 ? 'ayer' : `hace ${d} días`;
}

function alternar() {
  abierto.value = !abierto.value;
  if (abierto.value && !notif.lista.length && !notif.cargando) notif.cargar().catch(() => {});
}

function abrirNotificacion(n) {
  notif.marcarLeida(n.id);
  abierto.value = false;
  if (n.inscripcionId) router.push(aModulo('mis-ventas', { inscripcion: n.inscripcionId }));
  else modal.value = true;
}

function irPendiente(p) {
  abierto.value = false;
  router.push(aModulo(p.modulo, p.query || {}));
}

function verTodas() {
  abierto.value = false;
  modal.value = true;
}

function fuera(e) {
  if (abierto.value && raiz.value && !raiz.value.contains(e.target)) abierto.value = false;
}

function tecla(e) {
  if (e.key === 'Escape') abierto.value = false;
}

function alLlegar(n) {
  if (!n?.asunto || n.tipo === 'PERMISOS_CAMBIADOS' || n.tipo === 'MANTENIMIENTO_ACTIVO') return;
  notif.aplicarWS(n);
}

let quitar = null;
onMounted(() => {
  notif.cargar().catch(() => {});
  quitar = tienda.registrarNotificaciones(alLlegar);
  document.addEventListener('pointerdown', fuera);
  window.addEventListener('keydown', tecla);
});
onUnmounted(() => {
  if (quitar) quitar();
  document.removeEventListener('pointerdown', fuera);
  window.removeEventListener('keydown', tecla);
});

</script>

<template>
  <div ref="raiz" class="campana">
    <button type="button" class="btn btn-fantasma btn-icono boton" :class="{ activo: abierto }"
            :aria-expanded="abierto" aria-label="Notificaciones" title="Notificaciones" @click="alternar">
      <span class="ico" :class="{ suena: total > 0 }">🔔</span>
      <span v-if="total" class="globo">{{ total > 99 ? '99+' : total }}</span>
    </button>

    <Transition name="caer">
      <div v-if="abierto" class="panel" role="dialog" aria-label="Notificaciones">
        <header class="panel-cab">
          <strong>Notificaciones</strong>
          <button v-if="notif.countNoLeidas" type="button" class="enlace" @click="notif.marcarTodasLeidas()">Marcar todas leídas</button>
        </header>

        <div v-if="pendientesVisibles.length" class="pendientes">
          <button v-for="p in pendientesVisibles" :key="p.clave" type="button" class="pendiente" @click="irPendiente(p)">
            <span class="p-ico">{{ p.icono }}</span>
            <span class="p-txt">{{ p.texto }}</span>
            <span class="p-num">{{ p.cantidad }}</span>
          </button>
        </div>

        <div class="lista">
          <p v-if="notif.cargando && !notif.lista.length" class="nada">Cargando…</p>
          <p v-else-if="!recientes.length" class="nada">No tienes notificaciones.</p>
          <button v-for="n in recientes" :key="n.id" type="button" class="item" :class="{ nueva: !n.leida }" @click="abrirNotificacion(n)">
            <span class="i-ico">{{ ICONOS[n.tipo] || '🔔' }}</span>
            <span class="i-txt">
              <b>{{ n.asunto }}</b>
              <small>{{ n.cuerpo }}</small>
            </span>
            <span class="i-hora">{{ hace(n.fecha) }}</span>
          </button>
        </div>

        <footer class="panel-pie">
          <button type="button" class="btn btn-sm btn-primario" @click="verTodas">Ver todas las notificaciones</button>
        </footer>
      </div>
    </Transition>

    <UiModal v-if="modal" titulo="Notificaciones" ancho="720px" @cerrar="modal = false">
      <div class="filtros">
        <div class="segmento">
          <button v-for="o in [['todas', 'Todas'], ['no-leidas', 'No leídas'], ['leidas', 'Leídas']]" :key="o[0]" type="button"
                  :class="{ on: notif.filtroLeida === o[0] }" @click="notif.filtroLeida = o[0]">{{ o[1] }}</button>
        </div>
        <select v-if="notif.tiposDisponibles.length > 1" v-model="notif.filtroTipo" class="tipo">
          <option value="">Todos los tipos</option>
          <option v-for="t in notif.tiposDisponibles" :key="t" :value="t">{{ t.replaceAll('_', ' ').toLowerCase() }}</option>
        </select>
        <button v-if="notif.countNoLeidas" type="button" class="btn btn-fantasma btn-sm" @click="notif.marcarTodasLeidas()">Marcar todas leídas</button>
      </div>
      <p v-if="!notif.filtradas.length" class="nada">No hay notificaciones{{ notif.filtroLeida !== 'todas' || notif.filtroTipo ? ' con esos filtros' : '' }}.</p>
      <div v-else class="items">
        <NotificacionItem v-for="n in notif.filtradas" :key="n.id" :notificacion="n"
                          :es-admin="auth.puedeEditarPlano" :usuario-id="auth.id || 0" />
      </div>
    </UiModal>
  </div>
</template>

<style scoped>
.campana { position: relative; }
.boton { position: relative; }
.boton.activo { background: var(--panel-2); }
.ico { display: inline-block; transform-origin: 50% 10%; }
.ico.suena { animation: sonar 2.6s ease-in-out 1; }
@keyframes sonar {
  0%, 60%, 100% { transform: rotate(0); }
  10% { transform: rotate(14deg); } 20% { transform: rotate(-12deg); }
  30% { transform: rotate(9deg); } 40% { transform: rotate(-6deg); } 50% { transform: rotate(3deg); }
}
.globo {
  position: absolute; top: 2px; right: 0; min-width: 17px; height: 17px; padding: 0 4px; box-sizing: border-box;
  border-radius: 999px; background: var(--danger, #dc2626); color: #fff; font-size: 0.65rem; font-weight: 800;
  display: grid; place-items: center; line-height: 1; border: 2px solid var(--panel);
}
.panel {
  position: absolute; right: 0; top: calc(100% + 8px); z-index: 70; width: min(380px, calc(100vw - 24px));
  background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio, 12px);
  box-shadow: var(--sombra-md); display: flex; flex-direction: column; max-height: min(520px, 75vh); overflow: hidden;
}
.panel-cab { display: flex; align-items: center; justify-content: space-between; padding: 0.75rem 0.9rem 0.5rem; }
.enlace { border: 0; background: none; color: var(--acento); font: inherit; font-size: 0.8rem; font-weight: 700; cursor: pointer; padding: 0; }
.pendientes { display: flex; flex-direction: column; gap: 4px; padding: 0 0.6rem 0.5rem; }
.pendiente {
  display: flex; align-items: center; gap: 0.55rem; padding: 0.5rem 0.6rem; border-radius: var(--radio-sm);
  border: 1px solid color-mix(in srgb, var(--tramite) 35%, transparent);
  background: color-mix(in srgb, var(--tramite) 10%, transparent); color: var(--text);
  font: inherit; font-size: 0.85rem; text-align: left; cursor: pointer;
}
.p-txt { flex: 1; font-weight: 600; }
.p-num { background: var(--tramite); color: #fff; border-radius: 999px; padding: 0 0.45rem; font-size: 0.75rem; font-weight: 800; }
.lista { flex: 1; min-height: 0; overflow: auto; border-top: 1px solid var(--border); }
.item {
  display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 0.6rem; align-items: start; width: 100%;
  padding: 0.65rem 0.9rem; border: 0; border-bottom: 1px solid var(--border); background: none;
  color: var(--text); font: inherit; text-align: left; cursor: pointer;
}
.item:hover { background: var(--panel-2); }
.item.nueva { background: var(--acento-suave); }
.item.nueva b::after { content: ''; display: inline-block; width: 7px; height: 7px; margin-left: 6px; border-radius: 50%; background: var(--acento); vertical-align: middle; }
.i-ico { font-size: 1.1rem; }
.i-txt { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.i-txt b { font-size: 0.86rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.i-txt small { color: var(--muted); font-size: 0.78rem; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.i-hora { font-size: 0.72rem; color: var(--muted); white-space: nowrap; }
.nada { margin: 0; padding: 1.4rem 1rem; text-align: center; color: var(--muted); font-size: 0.88rem; }
.panel-pie { padding: 0.6rem; border-top: 1px solid var(--border); display: flex; }
.panel-pie .btn { flex: 1; }
.filtros { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
.segmento { display: inline-flex; border: 1px solid var(--border); border-radius: 999px; padding: 2px; }
.segmento button { border: 0; background: none; color: var(--muted); font: inherit; font-size: 0.82rem; font-weight: 700; padding: 0.3rem 0.75rem; border-radius: 999px; cursor: pointer; }
.segmento button.on { background: var(--acento); color: var(--acento-texto, #fff); }
.filtros .tipo { max-width: 220px; }
.items { display: flex; flex-direction: column; gap: 0.5rem; }
.caer-enter-active, .caer-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.caer-enter-from, .caer-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
