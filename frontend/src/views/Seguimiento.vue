<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { apiFetch } from '../api';

const REFRESCO_MS = 5000;

const gente = ref([]);
const cargando = ref(true);
const error = ref('');
const actualizado = ref(0);
let temporizador = null;

const enLinea = computed(() => gente.value.filter((g) => g.enLinea));
const ausentes = computed(() => gente.value.filter((g) => !g.enLinea));
const vendiendo = computed(() => enLinea.value.filter((g) => g.registrando));

const casetasEnTramite = computed(() =>
  gente.value.reduce((s, g) => s + (Number(g.casetasEnCarrito) || 0), 0));

const vendidasTotal = computed(() =>
  gente.value.reduce((s, g) => s + (Number(g.vendidas) || 0), 0));

async function cargar() {
  try {
    const r = await apiFetch('/api/app/presencia');
    if (!r.ok) { error.value = 'No se pudo leer el seguimiento'; return; }
    gente.value = await r.json();
    error.value = '';
    actualizado.value = Date.now();
  } catch (e) {
    error.value = e.message;
  } finally {
    cargando.value = false;
  }
}

onMounted(() => {
  cargar();
  temporizador = setInterval(cargar, REFRESCO_MS);
});
onUnmounted(() => clearInterval(temporizador));

function hace(segundos) {
  const s = Number(segundos) || 0;
  if (s < 10) return 'ahora mismo';
  if (s < 60) return `hace ${Math.round(s)} s`;
  const m = Math.round(s / 60);
  if (m < 60) return `hace ${m} min`;
  return `hace ${Math.round(m / 60)} h`;
}

const iniciales = (n) => (n || '?').split(/\s+/).slice(0, 2).map((x) => x[0]).join('').toUpperCase();
</script>

<template>
  <p class="muted nota">
    Quién tiene la aplicación abierta en este momento y en qué pantalla está.
    <strong>Casetas en carrito</strong> son ventas empezadas y sin cerrar: esas casetas están
    bloqueadas para el resto de vendedores mientras dure la reserva.
  </p>

  <div class="tarjetas-resumen">
    <div class="card resumen"><strong>{{ enLinea.length }}</strong><span>en línea</span></div>
    <div class="card resumen"><strong>{{ vendiendo.length }}</strong><span>registrando una venta</span></div>
    <div class="card resumen"><strong>{{ ausentes.length }}</strong><span>sin actividad reciente</span></div>
    <div class="card resumen"><strong>{{ casetasEnTramite }}</strong><span>casetas en trámite</span></div>
    <div class="card resumen"><strong>{{ vendidasTotal }}</strong><span>casetas vendidas (de los listados)</span></div>
  </div>

  <p v-if="error" class="card aviso-error">{{ error }}</p>
  <div v-else-if="cargando" class="vacio">Cargando…</div>
  <div v-else-if="!gente.length" class="vacio card">
    No hay nadie usando la aplicación en este momento.
  </div>

  <template v-else>
    <h3 class="titulo-seccion">En línea ({{ enLinea.length }})</h3>
    <p v-if="!enLinea.length" class="muted">Nadie con la aplicación abierta ahora mismo.</p>
    <ul v-else class="lista">
      <li v-for="g in enLinea" :key="g.usuarioId" class="card persona" :class="{ vendiendo: g.registrando }">
        <span class="avatar" :class="{ activo: true }">{{ iniciales(g.nombre || g.usuario) }}</span>
        <div class="datos">
          <div class="linea1">
            <strong>{{ g.nombre || g.usuario }}</strong>
            <span class="badge badge-muted">{{ g.rol }}</span>
            <span v-if="g.origen" class="badge badge-muted">{{ g.origen }}</span>
          </div>
          <div class="linea2">
            <span class="pantalla">{{ g.titulo || g.pantalla || 'sin pantalla' }}</span>
            <span class="muted">· {{ hace(g.inactivoSegundos) }}</span>
          </div>
          <div v-if="g.registrando" class="linea3">
            <span class="badge badge-ok">registrando</span>
            <span>{{ g.casetasEnCarrito }} caseta{{ g.casetasEnCarrito === 1 ? '' : 's' }}
              en el carrito: {{ g.casetas.join(', ') }}</span>
          </div>
          <div v-if="g.registrando && g.avance != null" class="avance">
            <div class="avance-cab">
              <span class="badge badge-muted">{{ g.avancePaso || 'formulario' }}</span>
              <strong>{{ g.avance }}%</strong>
              <span v-if="g.avance >= 100" class="muted">listo para registrar</span>
              <span v-else-if="g.avanceFaltan" class="muted">falta: {{ g.avanceFaltan }}</span>
            </div>
            <div class="barra-avance" role="progressbar" :aria-valuenow="g.avance"
                 aria-valuemin="0" aria-valuemax="100">
              <span :style="{ width: g.avance + '%' }" :class="{ completo: g.avance >= 100 }"></span>
            </div>
          </div>
          <div class="linea4">
            <span class="muted">{{ g.vendidas }} caseta{{ g.vendidas === 1 ? '' : 's' }} vendida{{ g.vendidas === 1 ? '' : 's' }} en esta edición</span>
          </div>
        </div>
      </li>
    </ul>

    <template v-if="ausentes.length">
      <h3 class="titulo-seccion">Sin actividad reciente ({{ ausentes.length }})</h3>
      <p class="muted chico">
        Estuvieron dentro hace poco y dejaron de dar señales: cerraron la aplicación, se quedaron
        sin señal o la tienen en segundo plano. Si alguno aparece con casetas en el carrito, esa
        venta quedó a medias.
      </p>
      <ul class="lista">
        <li v-for="g in ausentes" :key="g.usuarioId" class="card persona apagada">
          <span class="avatar">{{ iniciales(g.nombre || g.usuario) }}</span>
          <div class="datos">
            <div class="linea1">
              <strong>{{ g.nombre || g.usuario }}</strong>
              <span class="badge badge-muted">{{ g.rol }}</span>
            </div>
            <div class="linea2">
              <span class="pantalla">{{ g.titulo || g.pantalla || 'sin pantalla' }}</span>
              <span class="muted">· {{ hace(g.inactivoSegundos) }}</span>
            </div>
            <div v-if="g.registrando" class="linea3">
              <span class="badge badge-aviso">venta a medias</span>
              <span>{{ g.casetas.join(', ') }}</span>
              <span v-if="g.avance != null" class="muted">· iba por el {{ g.avance }}%</span>
            </div>
          </div>
        </li>
      </ul>
    </template>
  </template>
</template>

<style scoped>
.nota { margin: 0 0 1rem; line-height: 1.5; }
.chico { font-size: 0.85rem; }

.tarjetas-resumen {
  display: grid; gap: 0.75rem; margin-bottom: 1.25rem;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}
.resumen { padding: 0.8rem; display: flex; flex-direction: column; align-items: center; gap: 0.15rem; text-align: center; }
.resumen strong { font-size: 1.8rem; line-height: 1; }
.resumen span { font-size: 0.85rem; opacity: 0.75; }

.titulo-seccion { margin: 1.25rem 0 0.5rem; font-size: 1.05rem; }
.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.6rem; }

.persona { display: flex; align-items: flex-start; gap: 0.75rem; padding: 0.75rem; }
.persona.vendiendo { outline: 1px solid var(--ok, #16a34a); }
.persona.apagada { opacity: 0.6; }

.avatar {
  flex: none; width: 40px; height: 40px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 0.9rem;
  background: var(--panel-2, rgba(128,128,128,0.15));
}
.avatar.activo { box-shadow: 0 0 0 2px var(--ok, #16a34a); }

.datos { display: flex; flex-direction: column; gap: 0.2rem; min-width: 0; }
.linea1, .linea2, .linea3, .linea4 { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.linea4 { font-size: 0.85rem; }

.avance { display: flex; flex-direction: column; gap: 0.3rem; margin-top: 0.15rem; }
.avance-cab { display: flex; align-items: baseline; gap: 0.4rem; flex-wrap: wrap; font-size: 0.85rem; }
.barra-avance {
  height: 6px; border-radius: 999px; overflow: hidden;
  background: var(--panel-2, rgba(128,128,128,0.2));
}
.barra-avance span {
  display: block; height: 100%; border-radius: 999px;
  background: var(--tramite, #d97706); transition: width 0.3s ease;
}
.barra-avance span.completo { background: var(--libre, #16a34a); }
.pantalla { font-weight: 500; }
.aviso-error { padding: 0.8rem; }

@media (max-width: 480px) {
  .tarjetas-resumen { grid-template-columns: 1fr; }
  .resumen { flex-direction: row; justify-content: flex-start; gap: 0.5rem; }
  .resumen strong { font-size: 1.3rem; }
}
</style>
