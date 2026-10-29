<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import UiModal from '../components/UiModal.vue';
import PermisosUsuario from '../components/PermisosUsuario.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { alertaConAccion } from '../ui/alerta';
import { usePermisosStore } from '../stores/permisos';

const route = useRoute();
const propio = usePermisosStore();

const vista = ref(route.query.m === 'permisos' ? 'usuarios' : 'roles');
const cargando = ref(true);
const roles = ref([]);
const catalogo = ref([]);
const matriz = ref(new Map());
const usuarios = ref([]);
const filtro = ref('');
const selId = ref(null);
const pestana = ref('info');

const info = reactive({ nombre: '', descripcion: '' });
const marcadas = ref(new Set());
const guardando = ref(false);
const abiertos = ref(new Set());
const nuevo = reactive({ abierto: false, nombre: '', descripcion: '', guardando: false });

watch(() => route.query.m, (m) => { vista.value = m === 'permisos' ? 'usuarios' : 'roles'; });

const sel = computed(() => roles.value.find((r) => r.id === selId.value) || null);
const filaMatriz = computed(() => (sel.value ? matriz.value.get(sel.value.id) : null));
const loVeTodo = computed(() => Boolean(filaMatriz.value?.loVeTodo));
const filtrados = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  return roles.value.filter((r) => !q || `${r.nombre} ${r.descripcion || ''}`.toLowerCase().includes(q));
});
const grupos = computed(() => {
  const g = new Map();
  for (const p of catalogo.value) {
    if (!g.has(p.grupo)) g.set(p.grupo, []);
    g.get(p.grupo).push(p);
  }
  return [...g.entries()].map(([nombre, pantallas]) => ({ nombre, pantallas }));
});
const usuariosDelRol = computed(() => usuarios.value.filter((u) => u.rolId === selId.value));
const otrosUsuarios = computed(() => usuarios.value.filter((u) => u.rolId !== selId.value && u.estado !== 'ELIMINADO'));
const cuentaPermisos = (r) => {
  const f = matriz.value.get(r.id);
  if (!f) return 0;
  return f.loVeTodo ? catalogo.value.length : f.pantallas.length;
};

const permisosCambiados = computed(() => {
  const f = filaMatriz.value;
  if (!f || f.loVeTodo) return false;
  const a = new Set(f.pantallas);
  return a.size !== marcadas.value.size || [...marcadas.value].some((x) => !a.has(x));
});
const infoCambiada = computed(() => sel.value != null
  && (info.nombre.trim() !== sel.value.nombre || info.descripcion.trim() !== (sel.value.descripcion || '')));

async function cargar(conservar = selId.value) {
  cargando.value = true;
  try {
    const [rr, rc, rm, ru] = await Promise.all([
      apiFetch('/api/app/roles'), apiFetch('/api/app/permisos/catalogo'),
      apiFetch('/api/app/permisos'), apiFetch('/api/app/usuarios'),
    ]);
    if (!rr.ok || !rc.ok || !rm.ok) throw new Error('No se pudieron cargar los roles');
    roles.value = (await rr.json()).filter((r) => r.estado !== 'ELIMINADO');
    catalogo.value = await rc.json();
    matriz.value = new Map((await rm.json()).map((f) => [f.rolId, f]));
    usuarios.value = ru.ok ? await ru.json() : [];
    const id = roles.value.some((r) => r.id === conservar) ? conservar : roles.value[0]?.id ?? null;
    elegir(id, true);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function elegir(id, forzar = false) {
  if (!forzar && (permisosCambiados.value || infoCambiada.value)
      && !confirm('Hay cambios sin guardar en este rol. ¿Descartarlos?')) return;
  selId.value = id;
  const r = roles.value.find((x) => x.id === id);
  info.nombre = r?.nombre ?? '';
  info.descripcion = r?.descripcion ?? '';
  marcadas.value = new Set(matriz.value.get(id)?.pantallas ?? []);
  abiertos.value = new Set();
}

function alternarPantalla(clave) {
  if (loVeTodo.value) return;
  const s = new Set(marcadas.value);
  if (s.has(clave)) s.delete(clave);
  else s.add(clave);
  marcadas.value = s;
}

function marcarGrupo(grupo, valor) {
  if (loVeTodo.value) return;
  const s = new Set(marcadas.value);
  for (const p of grupo.pantallas) {
    if (valor) s.add(p.clave);
    else s.delete(p.clave);
  }
  marcadas.value = s;
}

const marcadasDe = (grupo) => grupo.pantallas.filter((p) => loVeTodo.value || marcadas.value.has(p.clave)).length;

function alternarGrupo(nombre) {
  const s = new Set(abiertos.value);
  if (s.has(nombre)) s.delete(nombre);
  else s.add(nombre);
  abiertos.value = s;
}

async function guardarInfo() {
  if (!sel.value || guardando.value) return;
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/roles/${sel.value.id}`, {
      method: 'PATCH', body: JSON.stringify({ nombre: info.nombre.trim(), descripcion: info.descripcion.trim() }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    toast('Rol actualizado ✓', 'ok', 1600);
    await cargar(sel.value.id);
  } finally {
    guardando.value = false;
  }
}

async function guardarPermisos() {
  if (!sel.value || guardando.value) return;
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/permisos/${sel.value.id}`, {
      method: 'PUT', body: JSON.stringify({ rol: sel.value.nombre, pantallas: [...marcadas.value] }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudieron guardar los permisos', 'error'); return; }
    toast('Permisos guardados ✓', 'ok', 1600);
    await cargar(sel.value.id);
    propio.limpiar();
    await propio.asegurar();
  } finally {
    guardando.value = false;
  }
}

async function eliminar() {
  const r = sel.value;
  if (!r || r.sistema) return;
  const si = await alertaConAccion(`¿Eliminar el rol "${r.nombre}"? Solo se puede si no tiene usuarios.`, 'advertencia', () => {});
  if (!si) return;
  const res = await apiFetch(`/api/app/roles/${r.id}`, { method: 'DELETE' });
  const d = await res.json().catch(() => ({}));
  if (!res.ok || !d.ok) { toast(d.mensaje || 'No se pudo eliminar', 'error'); return; }
  toast('Rol eliminado', 'ok');
  await cargar(null);
}

async function crear() {
  if (!nuevo.nombre.trim()) { toast('El rol necesita un nombre', 'advertencia'); return; }
  nuevo.guardando = true;
  try {
    const r = await apiFetch('/api/app/roles', {
      method: 'POST', body: JSON.stringify({ nombre: nuevo.nombre.trim(), descripcion: nuevo.descripcion.trim() }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo crear', 'error'); return; }
    Object.assign(nuevo, { abierto: false, nombre: '', descripcion: '' });
    await cargar(d.rol?.id);
    pestana.value = 'permisos';
  } finally {
    nuevo.guardando = false;
  }
}

async function asignarRol(u, rolId) {
  if (!rolId || rolId === u.rolId) return;
  const destino = roles.value.find((r) => r.id === rolId);
  const si = await alertaConAccion(`¿Cambiar a ${u.username} al rol "${destino?.nombre}"?`, 'advertencia', () => {});
  if (!si) return;
  const r = await apiFetch(`/api/app/usuarios/${u.id}`, {
    method: 'PATCH', body: JSON.stringify({ username: u.username, personaId: u.personaId, rolId }),
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo cambiar el rol', 'error'); return; }
  toast(`${u.username} ahora es ${destino?.nombre}`, 'ok', 1800);
  await cargar(selId.value);
}

const agregar = ref('');

async function agregarUsuario() {
  const u = usuarios.value.find((x) => String(x.id) === String(agregar.value));
  agregar.value = '';
  if (u) await asignarRol(u, selId.value);
}

onMounted(() => cargar());
</script>

<template>
  <div class="rp">
    <div class="vistas">
      <button :class="{ on: vista === 'roles' }" @click="vista = 'roles'">Roles</button>
      <button :class="{ on: vista === 'usuarios' }" @click="vista = 'usuarios'">Permisos por usuario</button>
    </div>

    <PermisosUsuario v-if="vista === 'usuarios'" :catalogo="catalogo" />

    <div v-else class="cuerpo">
      <aside class="card lista">
        <div class="lista-cab">
          <input v-model="filtro" class="control" placeholder="Buscar rol…" />
          <button class="btn btn-primario btn-sm" @click="nuevo.abierto = true">＋ Nuevo</button>
        </div>
        <p v-if="cargando" class="muted">Cargando…</p>
        <ul>
          <li v-for="r in filtrados" :key="r.id">
            <button :class="{ on: r.id === selId }" @click="elegir(r.id)">
              <span class="fila-rol">
                <strong>{{ r.nombre }}</strong>
                <span v-if="r.sistema" class="badge badge-muted">sistema</span>
              </span>
              <span class="muted chico">{{ r.usuarios }} usuario(s) · {{ cuentaPermisos(r) }} pantalla(s)</span>
            </button>
          </li>
        </ul>
      </aside>

      <section v-if="sel" class="card detalle">
        <header class="det-cab">
          <div>
            <h3>{{ sel.nombre }}</h3>
            <span class="muted chico">{{ sel.autoridad }}</span>
          </div>
          <span class="badge" :class="sel.estado === 'ACTIVO' ? 'badge-ok' : 'badge-muted'">{{ sel.estado }}</span>
        </header>
        <div class="pestanas">
          <button :class="{ on: pestana === 'info' }" @click="pestana = 'info'">Información</button>
          <button :class="{ on: pestana === 'permisos' }" @click="pestana = 'permisos'">Permisos</button>
          <button :class="{ on: pestana === 'usuarios' }" @click="pestana = 'usuarios'">Usuarios ({{ usuariosDelRol.length }})</button>
        </div>

        <div v-if="pestana === 'info'" class="formulario">
          <label class="campo"><span>Nombre</span>
            <input v-model="info.nombre" class="control" maxlength="60" :disabled="sel.sistema" />
          </label>
          <p v-if="sel.sistema" class="muted chico">Rol del sistema: su nombre e identificador no se pueden cambiar ni eliminar.</p>
          <label class="campo"><span>Descripción</span>
            <textarea v-model="info.descripcion" class="control" rows="3" maxlength="200"></textarea>
          </label>
          <div class="acciones">
            <button v-if="!sel.sistema" class="btn btn-peligro btn-sm" @click="eliminar">Eliminar rol</button>
            <span class="crecer"></span>
            <button class="btn btn-primario btn-sm" :disabled="!infoCambiada || guardando" @click="guardarInfo">Guardar</button>
          </div>
        </div>

        <div v-else-if="pestana === 'permisos'" class="permisos">
          <p v-if="loVeTodo" class="aviso">Este rol ve todas las pantallas siempre.</p>
          <div v-for="g in grupos" :key="g.nombre" class="grupo">
            <div class="grupo-cab" @click="alternarGrupo(g.nombre)">
              <span class="chevron">{{ abiertos.has(g.nombre) ? '▾' : '▸' }}</span>
              <strong>{{ g.nombre }}</strong>
              <span class="cuenta" :class="{ completo: marcadasDe(g) === g.pantallas.length }">{{ marcadasDe(g) }}/{{ g.pantallas.length }}</span>
              <span class="crecer"></span>
              <template v-if="!loVeTodo">
                <button class="btn btn-fantasma btn-sm" @click.stop="marcarGrupo(g, true)">Todos</button>
                <button class="btn btn-fantasma btn-sm" @click.stop="marcarGrupo(g, false)">Ninguno</button>
              </template>
            </div>
            <div v-if="abiertos.has(g.nombre)" class="pantallas">
              <label v-for="p in g.pantallas" :key="p.clave" class="check">
                <input type="checkbox" :checked="loVeTodo || marcadas.has(p.clave)" :disabled="loVeTodo"
                       @change="alternarPantalla(p.clave)" />
                {{ p.titulo }}
              </label>
            </div>
          </div>
          <div v-if="!loVeTodo" class="acciones">
            <button class="btn btn-sm" :disabled="!permisosCambiados" @click="elegir(sel.id, true)">Descartar</button>
            <span class="crecer"></span>
            <button class="btn btn-primario btn-sm" :disabled="!permisosCambiados || guardando" @click="guardarPermisos">Guardar permisos</button>
          </div>
        </div>

        <div v-else class="usuarios">
          <div class="agregar">
            <select v-model="agregar" class="control">
              <option value="">Asignar este rol a un usuario…</option>
              <option v-for="u in otrosUsuarios" :key="u.id" :value="u.id">{{ u.username }} · {{ u.persona || 'sin persona' }} ({{ u.rol }})</option>
            </select>
            <button class="btn btn-sm" :disabled="!agregar" @click="agregarUsuario">Asignar</button>
          </div>
          <p v-if="!usuariosDelRol.length" class="muted">Nadie tiene este rol.</p>
          <table v-else class="tabla">
            <thead><tr><th>Usuario</th><th>Persona</th><th>Estado</th><th>Cambiar a</th></tr></thead>
            <tbody>
              <tr v-for="u in usuariosDelRol" :key="u.id">
                <td><strong>{{ u.username }}</strong></td>
                <td>{{ u.persona || '—' }}</td>
                <td><span class="badge" :class="u.estado === 'ACTIVO' ? 'badge-ok' : 'badge-muted'">{{ u.estado }}</span></td>
                <td>
                  <select class="control control-sm" :value="u.rolId" @change="asignarRol(u, Number($event.target.value)); $event.target.value = u.rolId">
                    <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.nombre }}</option>
                  </select>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <UiModal v-if="nuevo.abierto" titulo="Nuevo rol" @cerrar="!nuevo.guardando && (nuevo.abierto = false)">
      <div class="formulario">
        <label class="campo"><span>Nombre</span><input v-model="nuevo.nombre" class="control" maxlength="60" placeholder="Ej. COORDINADOR" /></label>
        <label class="campo"><span>Descripción</span><textarea v-model="nuevo.descripcion" class="control" rows="2" maxlength="200"></textarea></label>
        <p class="muted chico">Después eliges sus pantallas en la pestaña Permisos.</p>
      </div>
      <template #pie>
        <button class="btn btn-fantasma" :disabled="nuevo.guardando" @click="nuevo.abierto = false">Cancelar</button>
        <button class="btn btn-primario" :disabled="nuevo.guardando" @click="crear">Crear</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.rp { display: flex; flex-direction: column; gap: 1rem; }
.vistas { display: inline-flex; padding: 2px; border-radius: 9px; background: var(--panel); border: 1px solid var(--border); align-self: flex-start; }
.vistas button {
  border: none; background: transparent; color: var(--text); font: inherit; font-size: 0.88rem; font-weight: 600;
  padding: 0.4rem 0.9rem; border-radius: 7px; cursor: pointer;
}
.vistas button.on { background: var(--acento); color: var(--acento-texto); }
.cuerpo { display: grid; grid-template-columns: 300px 1fr; gap: 1rem; align-items: start; }
.card { padding: 0.9rem; }
.lista-cab { display: flex; gap: 0.5rem; margin-bottom: 0.6rem; }
.lista ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
.lista li button {
  width: 100%; display: flex; flex-direction: column; gap: 0.15rem; text-align: left;
  border: 1px solid transparent; background: none; color: var(--text); font: inherit;
  padding: 0.5rem 0.6rem; border-radius: var(--radio-sm); cursor: pointer;
}
.lista li button:hover { background: var(--bg); }
.lista li button.on { background: var(--acento-suave); border-color: var(--acento); }
.fila-rol { display: flex; align-items: center; gap: 0.4rem; }
.chico { font-size: 0.8rem; margin: 0; }
.det-cab { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.5rem; }
.det-cab h3 { margin: 0; }
.pestanas { display: flex; gap: 0.3rem; margin: 0.8rem 0 1rem; border-bottom: 1px solid var(--border); }
.pestanas button {
  border: none; background: none; color: var(--muted); font: inherit; font-weight: 600;
  padding: 0.5rem 0.75rem; border-bottom: 2px solid transparent; cursor: pointer;
}
.pestanas button.on { color: var(--text); border-bottom-color: var(--acento); }
.formulario { display: flex; flex-direction: column; gap: 0.8rem; }
.acciones { display: flex; align-items: center; gap: 0.5rem; margin-top: 0.4rem; }
.crecer { flex: 1; }
.aviso { margin: 0 0 0.6rem; padding: 0.5rem 0.7rem; border-radius: var(--radio-sm); background: var(--acento-suave); font-size: 0.88rem; }
.grupo { border: 1px solid var(--border); border-radius: var(--radio-sm); margin-bottom: 0.5rem; }
.grupo-cab { display: flex; align-items: center; gap: 0.5rem; padding: 0.45rem 0.6rem; cursor: pointer; user-select: none; }
.chevron { width: 1rem; color: var(--muted); }
.cuenta { font-size: 0.78rem; color: var(--muted); font-variant-numeric: tabular-nums; }
.cuenta.completo { color: var(--ok); font-weight: 700; }
.pantallas { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.35rem 1rem; padding: 0.5rem 0.8rem 0.8rem 2.1rem; border-top: 1px solid var(--border); }
.check { display: flex; align-items: center; gap: 0.45rem; font-size: 0.88rem; cursor: pointer; }
.agregar { display: flex; gap: 0.5rem; margin-bottom: 0.8rem; }
.control-sm { padding: 0.3rem 0.4rem; font-size: 0.85rem; }
@media (max-width: 900px) { .cuerpo { grid-template-columns: 1fr; } }
</style>
