<script setup>
import { ref, computed, watch, onMounted, onActivated, onBeforeUnmount } from 'vue';
import { apiFetch, jsonOError } from '../api';
import { toast } from '../ui/toast';
import { confirmar } from '../ui/alerta';
import { edicionElegida } from '../ui/actividadElegida';
import SelectorActividad from '../components/SelectorActividad.vue';

const datos = ref({ ventas: 0, llegaron: 0, lista: [] });
const q = ref('');
const filtro = ref('pendientes');
const cargando = ref(false);
const ocupado = ref(0);
const buscador = ref(null);
let espera = 0;

async function cargar() {
  cargando.value = true;
  try {
    const p = new URLSearchParams();
    if (edicionElegida.value) p.set('edicion', edicionElegida.value);
    if (q.value.trim()) p.set('q', q.value.trim());
    datos.value = await jsonOError(await apiFetch(`/api/app/llegadas?${p}`), 'No se pudieron cargar las llegadas');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

watch(q, () => {
  clearTimeout(espera);
  espera = setTimeout(cargar, 300);
});
watch(edicionElegida, cargar);
onMounted(() => { cargar(); buscador.value?.focus(); });
onActivated(cargar);
onBeforeUnmount(() => clearTimeout(espera));

const visibles = computed(() => {
  const l = datos.value.lista || [];
  if (q.value.trim()) return l;
  if (filtro.value === 'pendientes') return l.filter((v) => !v.llegoEn);
  if (filtro.value === 'llegaron') return l.filter((v) => v.llegoEn);
  return l;
});

const porcentaje = computed(() => (datos.value.ventas ? Math.round((datos.value.llegaron * 100) / datos.value.ventas) : 0));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;
const hora = (iso) => (iso ? new Date(iso).toLocaleTimeString('es-BO', { hour: '2-digit', minute: '2-digit' }) : '');

async function confirmarLlegada(v) {
  if (!v.cobrada && !(await confirmar(`Esta venta tiene el pago pendiente (${bs(v.total)}). Cobra o verifica el pago antes de ubicarlo. ¿Confirmar su llegada igual?`,
    { titulo: '⚠️ Pago pendiente', aceptar: 'Sí, confirmar llegada' }))) return;
  ocupado.value = v.id;
  try {
    const r = await apiFetch(`/api/app/llegadas/${v.id}`, { method: 'POST', body: JSON.stringify({}) });
    const d = await r.json().catch(() => ({}));
    if (d.repetido) toast(d.mensaje, 'advertencia', 5000);
    else if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo confirmar la llegada', 'error'); return; }
    else toast(`✅ ${d.mensaje}`, 'ok', 2500);
    if (q.value) q.value = '';
    else await cargar();
    buscador.value?.focus();
  } finally {
    ocupado.value = 0;
  }
}

async function deshacer(v) {
  if (!(await confirmar(`Se quitará la llegada de «${v.cliente}» de hoy. Úsalo solo si se confirmó por error.`, { titulo: '¿Quitar la llegada?', aceptar: 'Sí, quitar' }))) return;
  ocupado.value = v.id;
  try {
    const r = await apiFetch(`/api/app/llegadas/${v.id}`, { method: 'DELETE' });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo quitar', 'error'); return; }
    toast('Llegada quitada', 'ok', 1800);
    await cargar();
  } finally {
    ocupado.value = 0;
  }
}
</script>

<template>
  <div class="llegadas">
    <header class="cabecera">
      <SelectorActividad />
      <div class="avance" :title="`${datos.llegaron} de ${datos.ventas} ventas ya llegaron hoy`">
        <div class="cifras"><b>{{ datos.llegaron }}</b> de {{ datos.ventas }} llegaron hoy</div>
        <div class="barra"><span :style="{ width: `${porcentaje}%` }"></span></div>
      </div>
    </header>

    <div class="buscar">
      <span aria-hidden="true">🔎</span>
      <input ref="buscador" v-model="q" class="control" type="search" placeholder="Nombre, C.I., mesa (ej. 4) o n.º de venta" aria-label="Buscar cliente" />
    </div>

    <div v-if="!q.trim()" class="chips" role="tablist">
      <button type="button" class="chip" :class="{ on: filtro === 'pendientes' }" @click="filtro = 'pendientes'">⏳ Por llegar ({{ datos.ventas - datos.llegaron }})</button>
      <button type="button" class="chip" :class="{ on: filtro === 'llegaron' }" @click="filtro = 'llegaron'">✅ Llegaron ({{ datos.llegaron }})</button>
      <button type="button" class="chip" :class="{ on: filtro === 'todas' }" @click="filtro = 'todas'">Todas ({{ datos.ventas }})</button>
    </div>

    <p v-if="cargando && !datos.lista.length" class="muted">Cargando…</p>
    <div v-else-if="!visibles.length" class="vacio">
      <span class="grande">{{ q.trim() ? '🔍' : filtro === 'pendientes' && datos.ventas ? '🎉' : '🗒️' }}</span>
      <p>{{ q.trim() ? 'No hay ventas que coincidan. Revisa el nombre o el número.' : filtro === 'pendientes' && datos.ventas ? '¡Ya llegaron todos!' : 'Aún no hay ventas en esta actividad.' }}</p>
    </div>

    <ul v-else class="lista">
      <li v-for="v in visibles" :key="v.id" class="tarjeta" :class="{ llego: v.llegoEn }">
        <div class="lugar">
          <span class="etiqueta">Lugar</span>
          <strong>{{ v.espacios || '—' }}</strong>
        </div>
        <div class="quien">
          <b>{{ v.representante || v.cliente }}</b>
          <small v-if="v.representante && v.cliente && v.cliente !== v.representante">{{ v.cliente }}</small>
          <small>C.I. {{ v.ci || '—' }}<template v-if="v.celular"> · 📱 {{ v.celular }}</template> · venta #{{ v.id }}</small>
          <div class="marcas">
            <span class="marca" :class="v.cobrada ? 'pagado' : 'pendiente'">{{ v.cobrada ? '✓ Pagado' : `⚠️ Pago pendiente · ${bs(v.total)}` }}</span>
            <span v-if="v.conCredenciales" class="marca neutra">👥 {{ v.incluidas }} persona(s) · {{ v.registradas }} con credencial</span>
          </div>
        </div>
        <div class="accion">
          <template v-if="v.llegoEn">
            <span class="hecho">✅ Llegó {{ hora(v.llegoEn) }}</span>
            <small v-if="v.recibio" class="muted">Lo recibió {{ v.recibio }}</small>
            <button type="button" class="btn btn-sm btn-fantasma" :disabled="ocupado === v.id" @click="deshacer(v)">Deshacer</button>
          </template>
          <button v-else type="button" class="btn btn-primario confirmar" :disabled="ocupado === v.id" @click="confirmarLlegada(v)">
            {{ ocupado === v.id ? 'Confirmando…' : '✅ Confirmar llegada' }}
          </button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.llegadas { display: flex; flex-direction: column; gap: 0.75rem; max-width: 900px; }
.cabecera { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.75rem; }
.avance { min-width: 200px; flex: 1 1 220px; max-width: 320px; display: flex; flex-direction: column; gap: 0.3rem; }
.cifras { font-size: 0.9rem; color: var(--muted); }
.cifras b { color: var(--text); font-size: 1.15rem; }
.barra { height: 8px; border-radius: 999px; background: var(--panel-2); overflow: hidden; }
.barra span { display: block; height: 100%; background: var(--ok); transition: width 0.3s ease; }
.buscar { display: flex; align-items: center; gap: 0.5rem; }
.buscar .control { font-size: 1.05rem; padding: 0.7rem 0.85rem; }
.chips { display: flex; flex-wrap: wrap; gap: 0.4rem; }
.chip { border: 1px solid var(--border); background: var(--panel); color: var(--text); border-radius: 999px; padding: 0.35rem 0.8rem; font: inherit; font-size: 0.85rem; font-weight: 600; cursor: pointer; }
.chip.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.tarjeta { display: grid; grid-template-columns: minmax(110px, 160px) minmax(0, 1fr) auto; gap: 0.75rem; align-items: center; padding: 0.7rem 0.85rem; border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel); }
.tarjeta.llego { border-color: color-mix(in srgb, var(--ok) 45%, var(--border)); background: color-mix(in srgb, var(--ok) 7%, var(--panel)); }
.lugar { display: flex; flex-direction: column; gap: 0.1rem; padding: 0.45rem 0.6rem; border-radius: var(--radio-sm); background: var(--acento-suave); }
.lugar .etiqueta { font-size: 0.68rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); font-weight: 700; }
.lugar strong { font-size: 1rem; line-height: 1.25; }
.quien { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; }
.quien b { font-size: 1rem; overflow-wrap: anywhere; }
.quien small { color: var(--muted); font-size: 0.8rem; }
.marcas { display: flex; flex-wrap: wrap; gap: 0.3rem; margin-top: 0.2rem; }
.marca { font-size: 0.74rem; font-weight: 700; padding: 0.12rem 0.5rem; border-radius: 999px; }
.marca.pagado { background: color-mix(in srgb, var(--ok) 15%, transparent); color: var(--ok); }
.marca.pendiente { background: color-mix(in srgb, var(--tramite) 18%, transparent); color: var(--text); }
.marca.neutra { background: var(--panel-2); color: var(--muted); }
.accion { display: flex; flex-direction: column; align-items: flex-end; gap: 0.25rem; text-align: right; }
.hecho { font-weight: 800; color: var(--ok); }
.confirmar { padding: 0.7rem 1rem; font-size: 0.95rem; }
.vacio { text-align: center; padding: 1.5rem 0.5rem; color: var(--muted); }
.vacio .grande { font-size: 2rem; }
@media (max-width: 640px) {
  .tarjeta { grid-template-columns: 1fr; }
  .accion { align-items: stretch; text-align: left; }
  .confirmar { width: 100%; }
}
</style>
