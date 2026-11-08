<script setup>
import { ref, computed, watch, onMounted, onActivated, defineAsyncComponent } from 'vue';
import { apiFetch, jsonOError } from '../api';
import { toast } from '../ui/toast';
import { useAuthStore } from '../stores/auth';
import { edicionElegida } from '../ui/actividadElegida';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { canalDe } from '../ui/canal';

const PedidosAnulacion = defineAsyncComponent(() => import('./Inscripciones.vue'));

const auth = useAuthStore();
const esAdmin = computed(() => auth.puedeEditarPlano);
const vista = ref('anuladas');
const lista = ref([]);
const cargando = ref(true);
const busqueda = ref('');
const tipo = ref('');

const TIPOS = {
  VENTA: { icono: '🧾', texto: 'Ventas anuladas' },
  SOLICITUD: { icono: '🗺️', texto: 'Solicitudes rechazadas' },
  ENTRADAS: { icono: '🎟️', texto: 'Entradas rechazadas o anuladas' },
};

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const cuando = (t) => {
  if (!t) return '—';
  try { return new Date(t).toLocaleString('es-BO', { dateStyle: 'medium', timeStyle: 'short' }); } catch { return '—'; }
};

const visibles = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  return lista.value
    .filter((a) => !tipo.value || a.tipo === tipo.value)
    .filter((a) => !q || `${a.cliente} ${a.detalle} ${a.motivo} ${a.anulo} ${a.atendio} ${a.celular}`.toLowerCase().includes(q));
});
const conteo = computed(() => lista.value.reduce((c, a) => { c[a.tipo] = (c[a.tipo] || 0) + 1; return c; }, {}));
const montoTotal = computed(() => visibles.value.reduce((t, a) => t + Number(a.monto || 0), 0));

async function cargar() {
  cargando.value = true;
  try {
    lista.value = await jsonOError(await apiFetch(`/api/app/anulaciones${edicionElegida.value ? `?edicion=${edicionElegida.value}` : ''}`), 'No se pudieron cargar las anulaciones');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

watch(edicionElegida, cargar);
onMounted(cargar);
onActivated(cargar);
</script>

<template>
  <div class="anulaciones">
    <div v-if="esAdmin" class="vistas" role="tablist">
      <button type="button" role="tab" :class="{ on: vista === 'anuladas' }" :aria-selected="vista === 'anuladas'" @click="vista = 'anuladas'">🗂️ Anuladas y rechazadas</button>
      <button type="button" role="tab" :class="{ on: vista === 'pedidos' }" :aria-selected="vista === 'pedidos'" @click="vista = 'pedidos'">✋ Pedidos de anulación de vendedores</button>
    </div>

    <PedidosAnulacion v-if="vista === 'pedidos'" solo-solicitudes />

    <template v-else>
      <p class="muted nota">
        Aquí solo aparece lo que <b>se anuló de verdad</b>: ventas anuladas, solicitudes en línea rechazadas y compras de entradas rechazadas o anuladas,
        con <b>quién lo hizo, cuándo y por qué</b>. Para anular una venta vigente, ábrela en <b>Todas las ventas</b>.
      </p>

      <div class="resumen">
        <button type="button" class="chip" :class="{ on: !tipo }" @click="tipo = ''">Todas <b>{{ lista.length }}</b></button>
        <button v-for="(t, k) in TIPOS" :key="k" type="button" class="chip" :class="{ on: tipo === k }" @click="tipo = tipo === k ? '' : k">
          {{ t.icono }} {{ t.texto }} <b>{{ conteo[k] || 0 }}</b>
        </button>
        <input v-model="busqueda" type="search" class="control buscar" placeholder="Buscar cliente, motivo, quién anuló…" aria-label="Buscar anulación" />
      </div>

      <p v-if="cargando" class="vacio">Cargando…</p>
      <div v-else-if="!visibles.length" class="vacio card">
        <span class="grande">✅</span>
        <strong>{{ lista.length ? 'Nada coincide con el filtro' : 'No hay anulaciones en esta actividad' }}</strong>
      </div>
      <ul v-else class="lista">
        <li v-for="a in visibles" :key="`${a.tipo}-${a.id}`" class="card item" :class="a.tipo.toLowerCase()">
          <div class="cab">
            <span class="badge" :class="a.tipo === 'VENTA' ? 'badge-danger' : 'badge-muted'">{{ TIPOS[a.tipo].icono }} {{ a.etiqueta }}</span>
            <span class="muted chico">{{ cuando(a.cuando) }}</span>
            <strong class="monto">{{ bs(a.monto) }}</strong>
          </div>
          <div class="cuerpo">
            <div>
              <strong>{{ a.cliente }}</strong>
              <span class="muted chico">{{ a.detalle || '—' }}</span>
            </div>
            <a v-if="a.celular" class="btn btn-sm btn-fantasma" :href="enlaceWhatsApp(a.celular)" target="_blank" rel="noopener" title="Escribir por WhatsApp">💬 {{ a.celular }}</a>
          </div>
          <p class="motivo"><span>Motivo</span>{{ a.motivo || 'No se registró el motivo (anulación anterior a este cambio)' }}</p>
          <div class="quienes">
            <span><b>Anuló:</b> {{ a.anulo || '—' }}</span>
            <span><span class="canal" :class="canalDe(a.canal).clase">{{ canalDe(a.canal).icono }} {{ canalDe(a.canal).texto }}</span>
              <template v-if="a.tipo === 'VENTA' || a.atendio !== 'Página pública'"> · {{ canalDe(a.canal).rol }}: {{ a.atendio || '—' }}</template></span>
          </div>
        </li>
      </ul>
      <p v-if="visibles.length" class="muted chico total">{{ visibles.length }} anulación(es) · {{ bs(montoTotal) }} que no se cobraron</p>
    </template>
  </div>
</template>

<style scoped>
.anulaciones { display: flex; flex-direction: column; gap: 0.8rem; }
.vistas { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.vistas button { border: 1px solid var(--border); background: var(--panel); color: var(--muted); border-radius: 999px; padding: 0.4rem 0.9rem; font: inherit; font-weight: 700; cursor: pointer; }
.vistas button.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.nota { margin: 0; line-height: 1.5; font-size: 0.9rem; }
.resumen { display: flex; gap: 0.4rem; flex-wrap: wrap; align-items: center; }
.chip { border: 1px solid var(--border); background: var(--panel); color: var(--text); border-radius: 999px; padding: 0.35rem 0.75rem; font: inherit; font-size: 0.84rem; cursor: pointer; display: inline-flex; gap: 0.35rem; align-items: center; }
.chip b { font-size: 0.75rem; background: rgba(127, 127, 127, 0.18); border-radius: 999px; padding: 0 0.4rem; }
.chip.on { border-color: var(--acento); background: var(--acento-suave); color: var(--acento); }
.buscar { flex: 1 1 220px; margin-left: auto; max-width: 340px; }
.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.6rem; }
.item { padding: 0.8rem 0.95rem; display: flex; flex-direction: column; gap: 0.5rem; border-left: 4px solid var(--danger); }
.item.solicitud, .item.entradas { border-left-color: var(--tramite, #d97706); }
.cab { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.monto { margin-left: auto; font-variant-numeric: tabular-nums; }
.cuerpo { display: flex; justify-content: space-between; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.cuerpo > div { display: flex; flex-direction: column; gap: 0.1rem; min-width: 0; }
.motivo { margin: 0; padding: 0.5rem 0.7rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--danger) 8%, transparent); line-height: 1.4; overflow-wrap: anywhere; }
.motivo span { display: block; font-size: 0.7rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
.quienes { display: flex; gap: 0.4rem 1rem; flex-wrap: wrap; font-size: 0.85rem; color: var(--muted); }
.quienes b { color: var(--text); }
.chico { font-size: 0.82rem; }
.vacio { text-align: center; color: var(--muted); padding: 2rem 1rem; display: flex; flex-direction: column; align-items: center; gap: 0.3rem; }
.grande { font-size: 2rem; }
.total { margin: 0; text-align: right; }
@media (max-width: 600px) {
  .buscar { max-width: none; margin-left: 0; }
}
</style>
