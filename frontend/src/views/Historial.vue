<script setup>
import { ref, reactive, computed, watch, onMounted, onActivated } from 'vue';
import { useRoute } from 'vue-router';
import { apiFetch, jsonOError } from '../api';
import { toast } from '../ui/toast';

const route = useRoute();
const LIMITE = 100;
const ICONOS = {
  Actividades: '🗓️', 'Plano y precios': '🗺️', 'Ventas y cobros': '💰', 'Punto de venta': '🧾', 'Recepción e ingreso': '🛎️', Credenciales: '🪪',
  'Usuarios y permisos': '👥', Comunicación: '📣', Sistema: '⚙️', Sesiones: '🔑', Otros: '•',
};

const filtros = reactive({ q: '', usuario: route.query.usuario ? Number(route.query.usuario) : '', modulo: '', desde: '', hasta: '', errores: false });
const lista = ref([]);
const opciones = ref({ modulos: [], usuarios: [] });
const cargando = ref(false);
const hayMas = ref(false);
const abiertos = ref(new Set());
let pendiente = null;

const grupos = computed(() => {
  const g = new Map();
  for (const h of lista.value) {
    const dia = h.cuando.slice(0, 10);
    if (!g.has(dia)) g.set(dia, []);
    g.get(dia).push(h);
  }
  return [...g.entries()].map(([dia, items]) => ({ dia, titulo: tituloDia(dia), items }));
});

function tituloDia(dia) {
  const hoy = new Date();
  const ayer = new Date(Date.now() - 86400000);
  const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  if (dia === iso(hoy)) return 'Hoy';
  if (dia === iso(ayer)) return 'Ayer';
  const t = new Date(`${dia}T12:00:00`).toLocaleDateString('es-BO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  return t.charAt(0).toUpperCase() + t.slice(1);
}

const hora = (s) => s.slice(11, 16);
const iniciales = (n) => String(n || '?').split(/\s+/).filter(Boolean).slice(0, 2).map((x) => x[0]).join('').toUpperCase();
const valor = (v) => (v === null || v === undefined || v === '' ? '—' : v === true ? 'Sí' : v === false ? 'No' : String(v));
const cambiosDe = (h) => Object.entries(h.detalle?.cambios || {});

function consulta(extra = {}) {
  const p = new URLSearchParams({ limite: String(LIMITE) });
  if (filtros.q.trim()) p.set('q', filtros.q.trim());
  if (filtros.usuario) p.set('usuario', filtros.usuario);
  if (filtros.modulo) p.set('modulo', filtros.modulo);
  if (filtros.desde) p.set('desde', filtros.desde);
  if (filtros.hasta) p.set('hasta', filtros.hasta);
  if (filtros.errores) p.set('errores', 'true');
  for (const [k, v] of Object.entries(extra)) p.set(k, v);
  return p.toString();
}

async function cargar(mas = false) {
  cargando.value = true;
  try {
    const extra = mas && lista.value.length ? { antesDe: lista.value[lista.value.length - 1].id } : {};
    const nuevos = await jsonOError(await apiFetch(`/api/app/historial?${consulta(extra)}`), 'No se pudo cargar el historial');
    lista.value = mas ? [...lista.value, ...nuevos] : nuevos;
    hayMas.value = nuevos.length === LIMITE;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function cargarOpciones() {
  const r = await apiFetch('/api/app/historial/opciones').catch(() => null);
  if (r?.ok) opciones.value = await r.json();
}

function alternar(id) {
  const s = new Set(abiertos.value);
  if (s.has(id)) s.delete(id); else s.add(id);
  abiertos.value = s;
}

function limpiar() {
  Object.assign(filtros, { q: '', usuario: '', modulo: '', desde: '', hasta: '', errores: false });
}

watch(() => ({ ...filtros }), () => {
  clearTimeout(pendiente);
  pendiente = setTimeout(() => cargar(), 350);
}, { deep: true });

onMounted(() => { cargarOpciones(); cargar(); });
onActivated(() => cargar());
</script>

<template>
  <div class="historial">
    <section class="card filtros">
      <input v-model="filtros.q" type="search" class="control buscar" placeholder="Buscar en el historial…" aria-label="Buscar en el historial" />
      <select v-model="filtros.usuario" class="control" aria-label="Usuario">
        <option value="">Todos los usuarios</option>
        <option v-for="u in opciones.usuarios" :key="u.id" :value="u.id">{{ u.nombre }}</option>
      </select>
      <select v-model="filtros.modulo" class="control" aria-label="Módulo">
        <option value="">Todos los módulos</option>
        <option v-for="m in opciones.modulos" :key="m" :value="m">{{ ICONOS[m] || '•' }} {{ m }}</option>
      </select>
      <label class="fecha"><span>Desde</span><input v-model="filtros.desde" type="date" class="control" /></label>
      <label class="fecha"><span>Hasta</span><input v-model="filtros.hasta" type="date" class="control" /></label>
      <label class="check"><input v-model="filtros.errores" type="checkbox" /> Solo con error</label>
      <button type="button" class="btn btn-sm btn-fantasma" @click="limpiar">Limpiar</button>
      <button type="button" class="btn btn-sm" :disabled="cargando" @click="cargar()">↻ Actualizar</button>
    </section>

    <p v-if="cargando && !lista.length" class="vacio">Cargando…</p>
    <p v-else-if="!lista.length" class="vacio card">No hay acciones con estos filtros.</p>

    <section v-for="g in grupos" :key="g.dia" class="dia">
      <h3>{{ g.titulo }}</h3>
      <ol class="linea">
        <li v-for="h in g.items" :key="h.id" class="accion" :class="{ error: !h.ok }">
          <span class="hora">{{ hora(h.cuando) }}</span>
          <span class="avatar" :title="h.rol || ''">{{ iniciales(h.usuario) }}</span>
          <div class="cuerpo">
            <div class="fila1">
              <strong>{{ h.usuario || 'Sin usuario' }}</strong>
              <span class="texto">{{ h.accion }}</span>
            </div>
            <div class="fila2">
              <span class="modulo">{{ ICONOS[h.modulo] || '•' }} {{ h.modulo }}</span>
              <span v-if="h.detalle?.resumen" class="resumen">{{ h.detalle.resumen }}</span>
              <span v-if="!h.ok" class="badge badge-danger">No se completó{{ h.detalle?.error ? ` · ${h.detalle.error}` : '' }}</span>
              <button v-if="cambiosDe(h).length" type="button" class="enlace" @click="alternar(h.id)">
                {{ abiertos.has(h.id) ? 'Ocultar cambios' : `Ver ${cambiosDe(h).length} cambio${cambiosDe(h).length === 1 ? '' : 's'}` }}
              </button>
            </div>
            <table v-if="abiertos.has(h.id)" class="cambios">
              <thead><tr><th>Dato</th><th>Antes</th><th>Después</th></tr></thead>
              <tbody>
                <tr v-for="[campo, c] in cambiosDe(h)" :key="campo">
                  <td>{{ campo }}</td><td class="antes">{{ valor(c.antes) }}</td><td class="despues">{{ valor(c.despues) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </li>
      </ol>
    </section>

    <button v-if="hayMas" type="button" class="btn mas" :disabled="cargando" @click="cargar(true)">{{ cargando ? 'Cargando…' : 'Ver acciones anteriores' }}</button>
  </div>
</template>

<style scoped>
.historial { display: flex; flex-direction: column; gap: 1rem; }
.filtros { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; padding: 0.8rem; }
.buscar { flex: 1 1 240px; }
.filtros select { width: auto; max-width: 220px; }
.fecha { display: flex; align-items: center; gap: 0.3rem; font-size: 0.8rem; color: var(--muted); }
.fecha .control { width: auto; }
.check { display: flex; align-items: center; gap: 0.35rem; font-size: 0.85rem; }
.dia h3 { margin: 0 0 0.5rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
.linea { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.4rem; }
.accion { display: grid; grid-template-columns: 46px 34px minmax(0, 1fr); gap: 0.6rem; align-items: start; padding: 0.6rem 0.8rem; background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio); }
.accion.error { border-left: 4px solid var(--danger); }
.hora { font-variant-numeric: tabular-nums; color: var(--muted); font-size: 0.85rem; padding-top: 0.45rem; }
.avatar { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 50%; background: var(--acento-suave); color: var(--acento); font-weight: 800; font-size: 0.8rem; }
.cuerpo { display: flex; flex-direction: column; gap: 0.25rem; min-width: 0; }
.fila1 { display: flex; flex-wrap: wrap; gap: 0.35rem; align-items: baseline; }
.texto { color: var(--text); }
.fila2 { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; font-size: 0.8rem; }
.modulo { background: var(--panel-2); border: 1px solid var(--border); border-radius: 999px; padding: 0.05rem 0.55rem; color: var(--muted); font-weight: 600; }
.resumen { color: var(--muted); overflow-wrap: anywhere; }
.enlace { border: 0; background: none; color: var(--acento); font: inherit; font-weight: 700; cursor: pointer; padding: 0; }
.cambios { width: 100%; border-collapse: collapse; margin-top: 0.4rem; font-size: 0.84rem; }
.cambios th { text-align: left; color: var(--muted); font-weight: 600; font-size: 0.75rem; text-transform: uppercase; padding: 0.3rem 0.4rem; border-bottom: 1px solid var(--border); }
.cambios td { padding: 0.35rem 0.4rem; border-bottom: 1px dashed var(--border); vertical-align: top; overflow-wrap: anywhere; }
.antes { color: var(--danger); text-decoration: line-through; text-decoration-thickness: 1px; }
.despues { color: var(--ok); font-weight: 600; }
.mas { align-self: center; }
.vacio { text-align: center; color: var(--muted); padding: 1.5rem; }
@media (max-width: 640px) {
  .accion { grid-template-columns: 40px minmax(0, 1fr); }
  .avatar { display: none; }
  .filtros select, .buscar { max-width: none; width: 100%; min-width: 0; flex: 1 1 100%; }
  .fecha { flex: 1 1 45%; }
  .fecha .control { width: 100%; min-width: 0; }
}
</style>
