<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import AutocompleteSelect from '../components/AutocompleteSelect.vue';
import MenuAcciones from '../components/MenuAcciones.vue';
import { apiFetch } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const esSuper = computed(() => (auth.rol || '').toUpperCase().replace(/ /g, '_') === 'SUPER_USUARIO');

const miDependencia = ref({ id: null, nombre: '' });

const avisoSinDependencia = ref(false);

const ROLES_PREDEFINIDOS = [
  { value: 'fotógrafo', label: '📸 Fotógrafo' },
  { value: 'azafata', label: '👩‍✈️ Azafata' },
  { value: 'logística', label: '📦 Logística' },
  { value: 'seguridad', label: '🛡️ Seguridad' },
  { value: 'limpieza', label: '🧹 Limpieza' },
  { value: 'montaje', label: '🔧 Montaje' },
  { value: 'coordinador', label: '📋 Coordinador' },
  { value: 'soporte técnico', label: '💻 Soporte técnico' },
  { value: 'atención al público', label: '🤝 Atención al público' },
  { value: 'otro', label: '➕ Otro' },
];

const rolesParaSelect = computed(() => {
  const actual = (modalPersonal.rol || '').trim();
  if (!actual) return ROLES_PREDEFINIDOS;
  if (ROLES_PREDEFINIDOS.some((r) => r.value === actual)) return ROLES_PREDEFINIDOS;
  return [{ value: actual, label: actual }, ...ROLES_PREDEFINIDOS];
});

const modo = ref('personal');
const cargando = ref(true);
const filtro = ref('');
const guardando = ref(false);
const guardandoDep = ref(false);
const ocupado = ref(false);

const dependencias = ref([]);

const personal = ref([]);

const modalPersonal = reactive({
  abierto: false,
  editando: null,
  idDependencia: '',
  nombre: '',
  paterno: '',
  materno: '',
  ci: '',
  rol: '',
  descripcionTarea: '',
  turno: '',
  celular: '',
  foto: ''
});

const TURNOS = ['Mañana', 'Tarde', 'Noche', 'Completo'];
const filtroDependencia = ref('');
const filtroEstado = ref('A');
const filtroTurno = ref('');
const detalle = ref(null);
const cambioDep = reactive({ abierto: false, persona: null, idDependencia: '', guardando: false });
const turnosUsados = computed(() => [...new Set([...TURNOS, ...personal.value.map((p) => p.turno).filter(Boolean)])]);
const nombreDependencia = (id) => dependencias.value.find((d) => String(d.id) === String(id))?.nombre ?? '';
const archivoFoto = ref(null);
const vistaPreviaFoto = ref('');

function elegirFoto(e) {
  const f = e.target.files?.[0] || null;
  if (vistaPreviaFoto.value.startsWith('blob:')) URL.revokeObjectURL(vistaPreviaFoto.value);
  archivoFoto.value = f;
  vistaPreviaFoto.value = f ? URL.createObjectURL(f) : '';
}

function limpiarFotoPendiente() {
  if (vistaPreviaFoto.value.startsWith('blob:')) URL.revokeObjectURL(vistaPreviaFoto.value);
  archivoFoto.value = null;
  vistaPreviaFoto.value = '';
}

const modalDependencia = reactive({
  abierto: false,
  editando: null,
  nombre: '',
  descripcion: ''
});

const personalFiltrado = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  return personal.value.filter((p) => {
    if (filtroDependencia.value && String(p.idDependencia) !== String(filtroDependencia.value)) return false;
    if (filtroEstado.value && (p.estado || 'A') !== filtroEstado.value) return false;
    if (filtroTurno.value === '-' && p.turno) return false;
    if (filtroTurno.value && filtroTurno.value !== '-' && p.turno !== filtroTurno.value) return false;
    return !q || [p.nombreCompleto, p.ci, p.celular, p.rol, p.dependenciaNombre, p.descripcionTarea, p.turno]
      .some((c) => (c || '').toLowerCase().includes(q));
  });
});

function accionesPersonal(p) {
  const activo = (p.estado || 'A') === 'A';
  return [
    { txt: 'Ver detalle', accion: () => { detalle.value = p; } },
    { txt: 'Editar', accion: () => abrirEditarPersonal(p) },
    ...(esSuper.value ? [{ txt: 'Cambiar dependencia', accion: () => abrirCambioDependencia(p) }] : []),
    { txt: activo ? 'Desactivar' : 'Activar', accion: () => cambiarEstado(p) },
    { separador: true },
    { txt: 'Eliminar', peligro: true, accion: () => eliminarPersonal(p) },
  ];
}

function accionesDependencia(d) {
  return [
    { txt: 'Ver personal', accion: () => verPersonalDe(d) },
    { txt: 'Editar', accion: () => abrirEditarDependencia(d) },
    { separador: true },
    {
      txt: 'Eliminar', peligro: true, deshabilitado: d.personas > 0,
      titulo: d.personas > 0 ? 'Tiene personal asignado' : '', accion: () => eliminarDependencia(d),
    },
  ];
}

function verPersonalDe(d) {
  modo.value = 'personal';
  filtro.value = '';
  filtroEstado.value = '';
  filtroDependencia.value = String(d.id);
}

function abrirCambioDependencia(p) {
  Object.assign(cambioDep, { abierto: true, persona: p, idDependencia: String(p.idDependencia || ''), guardando: false });
}

async function guardarCambioDependencia() {
  const p = cambioDep.persona;
  if (!p || !cambioDep.idDependencia || String(p.idDependencia) === String(cambioDep.idDependencia)) {
    cambioDep.abierto = false;
    return;
  }
  cambioDep.guardando = true;
  try {
    const r = await apiFetch(`/api/app/personal-apoyo/${p.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        idDependencia: Number(cambioDep.idDependencia), nombre: p.nombre, paterno: p.paterno, materno: p.materno,
        ci: p.ci, rol: p.rol, descripcionTarea: p.descripcionTarea, turno: p.turno || '',
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!d.ok) { toast(d.mensaje || 'No se pudo cambiar la dependencia.', 'error'); return; }
    toast(`${p.nombreCompleto} pasó a ${nombreDependencia(cambioDep.idDependencia)}`, 'ok');
    cambioDep.abierto = false;
    await Promise.all([cargarPersonal(), cargarDependencias()]);
  } finally {
    cambioDep.guardando = false;
  }
}

async function cambiarEstado(p) {
  const activar = (p.estado || 'A') !== 'A';
  if (!activar && !confirm(`¿Desactivar a "${p.nombreCompleto}"? Su credencial dejará de permitir el ingreso hasta que se active.`)) return;
  const r = await apiFetch(`/api/app/personal-apoyo/${p.id}/estado`, { method: 'PATCH', body: JSON.stringify({ activo: activar }) });
  const d = await r.json().catch(() => ({}));
  if (!d.ok) { toast(d.mensaje || 'No se pudo cambiar el estado.', 'error'); return; }
  toast(d.mensaje, 'ok');
  await cargarPersonal();
}

const dependenciasFiltradas = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return dependencias.value;
  return dependencias.value.filter((d) =>
    [d.nombre, d.descripcion].some((c) => (c || '').toLowerCase().includes(q)));
});

async function cargarDependencias() {
  try {
    const r = await apiFetch('/api/app/personal-apoyo/dependencias');
    dependencias.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  }
}

async function cargarPersonal() {
  cargando.value = true;
  try {
    const qs = (!esSuper.value && miDependencia.value.id) ? `?dependencia=${miDependencia.value.id}` : '';
    const r = await apiFetch(`/api/app/personal-apoyo${qs}`);
    personal.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function cargarTodo() {
  try {
    const r = await apiFetch('/api/app/personal-apoyo/mi-dependencia');
    const d = await r.json();
    if (!d.esSuper && d.idDependencia) {
      miDependencia.value = { id: d.idDependencia, nombre: d.dependenciaNombre || '' };
    }
  } catch (e) {
    toast(e.message, 'error');
  }
  if (esSuper.value) await cargarDependencias();
  await cargarPersonal();
  if (!esSuper.value && !miDependencia.value.id) avisoSinDependencia.value = true;
}

function cambiarModo(nuevo) {
  if (modo.value === nuevo) return;
  if (nuevo === 'dependencias' && !esSuper.value) return;
  modo.value = nuevo;
  filtro.value = '';
}

function abrirCrearPersonal() {
  limpiarFotoPendiente();
  Object.assign(modalPersonal, {
    abierto: true, editando: null,
    idDependencia: (!esSuper.value && miDependencia.value.id) ? String(miDependencia.value.id) : '',
    nombre: '', paterno: '', materno: '',
    ci: '', rol: '', descripcionTarea: '', turno: '', celular: '', foto: ''
  });
}

function abrirEditarPersonal(p) {
  limpiarFotoPendiente();
  Object.assign(modalPersonal, {
    abierto: true, editando: p.id,
    idDependencia: String(p.idDependencia || ''),
    nombre: p.nombre || '', paterno: p.paterno || '', materno: p.materno || '',
    ci: p.ci || '', rol: p.rol || '', descripcionTarea: p.descripcionTarea || '',
    turno: p.turno || '', celular: p.celular || '', foto: p.foto || ''
  });
}

function abrirCrearDependencia() {
  if (!esSuper.value) return;
  Object.assign(modalDependencia, { abierto: true, editando: null, nombre: '', descripcion: '' });
}

function abrirEditarDependencia(d) {
  Object.assign(modalDependencia, { abierto: true, editando: d.id, nombre: d.nombre || '', descripcion: d.descripcion || '' });
}

async function guardarPersonal() {
  if (!modalPersonal.idDependencia) return toast('La dependencia es obligatoria.', 'error');
  if (!modalPersonal.nombre.trim()) return toast('El nombre es obligatorio.', 'error');
  if (!modalPersonal.paterno.trim()) return toast('El apellido paterno es obligatorio.', 'error');
  if (!modalPersonal.ci.trim()) return toast('El C.I. es obligatorio.', 'error');
  if (!modalPersonal.rol.trim()) return toast('El rol es obligatorio (ej. fotógrafo, azafata, logística).', 'error');

  guardando.value = true;
  try {
    const url = modalPersonal.editando ? `/api/app/personal-apoyo/${modalPersonal.editando}` : '/api/app/personal-apoyo';
    const metodo = modalPersonal.editando ? 'PATCH' : 'POST';
    const body = {
      idDependencia: Number(modalPersonal.idDependencia),
      nombre: modalPersonal.nombre, paterno: modalPersonal.paterno, materno: modalPersonal.materno,
      ci: modalPersonal.ci, rol: modalPersonal.rol,
      descripcionTarea: modalPersonal.descripcionTarea || null,
      turno: modalPersonal.turno || '',
      celular: modalPersonal.celular.trim() || null
    };
    const r = await apiFetch(url, { method: metodo, body: JSON.stringify(body) });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'No se pudo guardar.', 'error');
    if (archivoFoto.value && d.personal?.id) {
      console.log('[APOYO-FOTO] subiendo', archivoFoto.value.name,
        archivoFoto.value.size, 'bytes a ficha', d.personal.id);
      const forma = new FormData();
      forma.append('foto', archivoFoto.value);
      const rf = await apiFetch(`/api/app/personal-apoyo/${d.personal.id}/foto`,
        { method: 'POST', body: forma });
      console.log('[APOYO-FOTO] respuesta foto: HTTP', rf.status);
      const df = await rf.json().catch(() => ({}));
      console.log('[APOYO-FOTO] cuerpo foto:', df);
      if (!df.ok) return toast(`Se guardó la ficha, pero la foto no: ${df.mensaje || 'error'}.`, 'error');
    } else if (!archivoFoto.value) {
      console.log('[APOYO-FOTO] sin archivo elegido, no se sube nada');
    }
    toast(d.mensaje, 'ok');
    modalPersonal.abierto = false;
    await cargarPersonal();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

async function guardarDependencia() {
  if (!modalDependencia.nombre.trim()) return toast('El nombre de la dependencia es obligatorio.', 'error');

  guardandoDep.value = true;
  try {
    const url = modalDependencia.editando ? `/api/app/personal-apoyo/dependencias/${modalDependencia.editando}` : '/api/app/personal-apoyo/dependencias';
    const metodo = modalDependencia.editando ? 'PATCH' : 'POST';
    const body = { nombre: modalDependencia.nombre, descripcion: modalDependencia.descripcion };
    const r = await apiFetch(url, { method: metodo, body: JSON.stringify(body) });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'No se pudo guardar.', 'error');
    toast(d.mensaje, 'ok');
    modalDependencia.abierto = false;
    await cargarDependencias();
    await cargarPersonal();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardandoDep.value = false;
  }
}

async function quitarFoto() {
  if (!modalPersonal.editando || ocupado.value) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/personal-apoyo/${modalPersonal.editando}/foto`,
      { method: 'DELETE' });
    const d = await r.json().catch(() => ({}));
    if (!d.ok) return toast(d.mensaje || 'No se pudo quitar.', 'error');
    modalPersonal.foto = '';
    limpiarFotoPendiente();
    toast('Foto quitada.', 'ok');
    await cargarPersonal();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}
async function eliminarPersonal(p) {
  if (ocupado.value) return;
  if (!confirm(`¿Eliminar a "${p.nombreCompleto}"?`)) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/personal-apoyo/${p.id}`, { method: 'DELETE' });
    const d = await r.json();
    if (!d.ok) return toast(d.mensaje || 'Error', 'error');
    toast(d.mensaje, 'ok');
    await cargarPersonal();
  } finally {
    ocupado.value = false;
  }
}

async function eliminarDependencia(d) {
  if (ocupado.value) return;
  if (!confirm(`¿Eliminar la dependencia "${d.nombre}"? Se perderán sus asignaciones.`)) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/personal-apoyo/dependencias/${d.id}`, { method: 'DELETE' });
    const res = await r.json();
    if (!res.ok) return toast(res.mensaje || 'Error', 'error');
    toast(res.mensaje, 'ok');
    await cargarDependencias();
    await cargarPersonal();
  } finally {
    ocupado.value = false;
  }
}

onMounted(cargarTodo);
</script>

<template>
  <div class="fila entre encabezado">
    <div class="tabs" role="tablist">
      <button
        role="tab"
        :aria-selected="modo === 'personal'"
        :class="{ activo: modo === 'personal' }"
        @click="cambiarModo('personal')"
        class="btn btn-fantasma"
      >
        👥 Personal de apoyo
      </button>
      <button
        v-if="esSuper"
        role="tab"
        :aria-selected="modo === 'dependencias'"
        :class="{ activo: modo === 'dependencias' }"
        @click="cambiarModo('dependencias')"
        class="btn btn-fantasma"
      >
        🏢 Dependencias
      </button>
    </div>
    <div class="acciones">
      <input v-model="filtro" class="control busca" :placeholder="modo === 'personal' ? 'Buscar por nombre, C.I., rol, dependencia…' : 'Buscar por nombre o descripción…'" />
      <button class="btn btn-primario" :disabled="guardando || guardandoDep" @click="modo === 'personal' ? abrirCrearPersonal() : abrirCrearDependencia()">
        {{ modo === 'personal' ? '＋ Nuevo personal' : '＋ Nueva dependencia' }}
      </button>
    </div>
  </div>

  <p class="muted nota">
    {{ modo === 'personal'
      ? (!esSuper && miDependencia.nombre
        ? `Personal de tu dependencia (${miDependencia.nombre}). No afecta a la tabla de personas del sistema.`
        : 'Gestión de personal de apoyo: fotógrafos, azafatas, logística, etc. No afecta a la tabla de personas del sistema.')
      : 'Catálogo de dependencias/unidades a las que se asigna el personal de apoyo.' }}
  </p>
  <p v-if="modo === 'personal' && !esSuper && !miDependencia.id" class="muted nota">
    Tu usuario no tiene ficha de apoyo registrada (se enlaza por C.I.): no hay personal que mostrar.
    Pide al super usuario que te registre en tu dependencia.
  </p>

  <div v-if="modo === 'personal'" class="filtros">
    <select v-if="esSuper" v-model="filtroDependencia" class="control">
      <option value="">Todas las dependencias</option>
      <option v-for="d in dependencias" :key="d.id" :value="String(d.id)">{{ d.nombre }}</option>
    </select>
    <select v-model="filtroEstado" class="control">
      <option value="">Todos los estados</option>
      <option value="A">Activos</option>
      <option value="I">Inactivos</option>
    </select>
    <select v-model="filtroTurno" class="control">
      <option value="">Todos los turnos</option>
      <option v-for="t in turnosUsados" :key="t" :value="t">{{ t }}</option>
      <option value="-">Sin turno</option>
    </select>
    <span class="muted cuenta">{{ personalFiltrado.length }} de {{ personal.length }}</span>
  </div>

  <div class="card">
    <div v-if="cargando" class="vacio">Cargando…</div>
    <div v-else-if="modo === 'personal' && personalFiltrado.length === 0" class="vacio">No hay personal que mostrar.</div>
    <div v-else-if="modo === 'dependencias' && dependenciasFiltradas.length === 0" class="vacio">No hay dependencias que mostrar.</div>

    <div v-else-if="modo === 'personal'" class="tabla-scroll">
      <table class="tabla">
        <thead>
          <tr><th></th><th>Nombre</th><th>C.I.</th><th>Celular</th><th>Dependencia</th><th>Función</th><th>Turno</th><th>Estado</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="p in personalFiltrado" :key="p.id" :class="{ inactivo: (p.estado || 'A') !== 'A' }">
            <td><img v-if="p.foto" :src="urlApi(p.foto)" :alt="p.nombreCompleto" class="mini" />
              <span v-else class="mini sinfoto">—</span></td>
            <td><strong>{{ p.nombreCompleto }}</strong></td>
            <td>{{ p.ci }}</td>
            <td>{{ p.celular || '—' }}</td>
            <td>{{ p.dependenciaNombre || '—' }}</td>
            <td>
              <span class="badge">{{ p.rol }}</span>
              <span v-if="p.descripcionTarea" class="muted tarea">{{ p.descripcionTarea }}</span>
            </td>
            <td>{{ p.turno || '—' }}</td>
            <td><span class="badge" :class="(p.estado || 'A') === 'A' ? 'badge-ok' : 'badge-muted'">{{ (p.estado || 'A') === 'A' ? 'Activo' : 'Inactivo' }}</span></td>
            <td class="acciones"><MenuAcciones :acciones="accionesPersonal(p)" :deshabilitado="ocupado" :etiqueta="`Acciones de ${p.nombreCompleto}`" /></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-else class="tabla-scroll">
      <table class="tabla">
        <thead>
          <tr><th>Nombre</th><th>Descripción</th><th>Personas</th><th>Responsable</th><th>Estado</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="d in dependenciasFiltradas" :key="d.id" class="clicable" @click="verPersonalDe(d)">
            <td><strong>{{ d.nombre }}</strong></td>
            <td>{{ d.descripcion || '—' }}</td>
            <td>{{ d.activos ?? 0 }}<span class="muted"> / {{ d.personas ?? 0 }}</span></td>
            <td>{{ d.responsable || '—' }}</td>
            <td><span class="badge badge-ok">Activa</span></td>
            <td class="acciones" @click.stop><MenuAcciones :acciones="accionesDependencia(d)" :deshabilitado="ocupado" :etiqueta="`Acciones de ${d.nombre}`" /></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>

  <UiModal v-if="detalle" :titulo="detalle.nombreCompleto" @cerrar="detalle = null" ancho="520px">
    <div class="detalle">
      <img v-if="detalle.foto" :src="urlApi(detalle.foto)" alt="" class="foto-prev" />
      <dl>
        <dt>C.I.</dt><dd>{{ detalle.ci }}</dd>
        <dt>Celular</dt><dd>{{ detalle.celular || '—' }}</dd>
        <dt>Dependencia</dt><dd>{{ detalle.dependenciaNombre || '—' }}</dd>
        <dt>Función</dt><dd>{{ detalle.rol }}</dd>
        <dt>Tarea</dt><dd>{{ detalle.descripcionTarea || '—' }}</dd>
        <dt>Turno</dt><dd>{{ detalle.turno || '—' }}</dd>
        <dt>Estado</dt><dd>{{ (detalle.estado || 'A') === 'A' ? 'Activo' : 'Inactivo' }}</dd>
      </dl>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="detalle = null">Cerrar</button>
      <button class="btn btn-primario" @click="abrirEditarPersonal(detalle); detalle = null">Editar</button>
    </template>
  </UiModal>

  <UiModal v-if="cambioDep.abierto" :titulo="`Cambiar dependencia de ${cambioDep.persona?.nombreCompleto}`"
           @cerrar="!cambioDep.guardando && (cambioDep.abierto = false)" ancho="460px">
    <label class="campo"><span>Nueva dependencia</span>
      <select v-model="cambioDep.idDependencia" class="control">
        <option v-for="d in dependencias" :key="d.id" :value="String(d.id)">{{ d.nombre }}</option>
      </select>
    </label>
    <template #pie>
      <button class="btn btn-fantasma" :disabled="cambioDep.guardando" @click="cambioDep.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="cambioDep.guardando" @click="guardarCambioDependencia">Cambiar</button>
    </template>
  </UiModal>

  <UiModal v-if="modalPersonal.abierto" :titulo="modalPersonal.editando ? 'Editar personal' : 'Nuevo personal de apoyo'"
           @cerrar="modalPersonal.abierto = false" ancho="600px">
    <div class="grid2">
      <AutocompleteSelect
        v-if="esSuper"
        v-model="modalPersonal.idDependencia"
        :options="dependencias.map(d => ({ value: d.id, label: d.nombre }))"
        label="Dependencia *"
        placeholder="Seleccione dependencia…"
        required
        searchKey="label"
        valueKey="value"
        labelKey="label"
      />
      <p v-else class="campo"><span>Dependencia</span><strong>{{ miDependencia.nombre || '—' }}</strong></p>
      <AutocompleteSelect
        v-model="modalPersonal.rol"
        :options="rolesParaSelect"
        label="Rol *"
        placeholder="Seleccione rol…"
        required
        searchKey="label"
        valueKey="value"
        labelKey="label"
      />
      <label class="campo"><span>Nombre *</span><input v-model="modalPersonal.nombre" class="control" required /></label>
      <label class="campo"><span>C.I. *</span><input v-model="modalPersonal.ci" class="control" required /></label>
      <label class="campo"><span>Apellido paterno *</span><input v-model="modalPersonal.paterno" class="control" required /></label>
      <label class="campo"><span>Apellido materno</span><input v-model="modalPersonal.materno" class="control" /></label>
      <label class="campo"><span>Turno</span>
        <input v-model="modalPersonal.turno" class="control" list="turnos-apoyo" maxlength="30" placeholder="Mañana, Tarde…" />
        <datalist id="turnos-apoyo"><option v-for="t in TURNOS" :key="t" :value="t"></option></datalist>
      </label>
      <label class="campo"><span>Celular</span><input v-model="modalPersonal.celular" class="control" maxlength="20" inputmode="tel" /></label>
      <label class="campo campo-ancho"><span>Descripción de la tarea ({{ (modalPersonal.descripcionTarea || '').length }}/200)</span><input v-model="modalPersonal.descripcionTarea" class="control" maxlength="200" placeholder="Brevemente: ¿qué va a realizar?" /></label>
      <div class="campo campo-ancho">
        <span>Foto (va en el círculo de la credencial)</span>
        <div class="foto-fila">
          <img v-if="vistaPreviaFoto" :src="vistaPreviaFoto" alt="Nueva foto" class="foto-prev" />
          <img v-else-if="modalPersonal.foto" :src="urlApi(modalPersonal.foto)" alt="Foto actual" class="foto-prev" />
          <div v-else class="foto-prev sinfoto">Sin foto</div>
          <div class="foto-acciones">
            <label class="btn btn-sm btn-fantasma">Elegir…
              <input type="file" accept="image/*" class="oculto" @change="elegirFoto" />
            </label>
            <button v-if="modalPersonal.editando && (modalPersonal.foto || archivoFoto)"
                    class="btn btn-sm btn-peligro" :disabled="ocupado" @click="quitarFoto">Quitar</button>
          </div>
        </div>
      </div>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalPersonal.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardarPersonal">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>

  <UiModal v-if="avisoSinDependencia" titulo="⚠️ Sin dependencia asignada"
           @cerrar="avisoSinDependencia = false" ancho="480px">
    <p>No tienes ninguna dependencia asignada.</p>
    <p class="muted">Comunícate con soporte técnico para que te asignen a tu dependencia
      y puedas ver y registrar tu personal de apoyo.</p>
    <template #pie>
      <button class="btn btn-primario" @click="avisoSinDependencia = false">Entendido</button>
    </template>
  </UiModal>

  <UiModal v-if="modalDependencia.abierto" :titulo="modalDependencia.editando ? 'Editar dependencia' : 'Nueva dependencia'"
           @cerrar="modalDependencia.abierto = false" ancho="540px">
    <div class="grid2">
      <label class="campo"><span>Nombre *</span><input v-model="modalDependencia.nombre" class="control" required /></label>
      <label class="campo"><span>Descripción</span><input v-model="modalDependencia.descripcion" class="control" /></label>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalDependencia.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardandoDep" @click="guardarDependencia">
        {{ guardandoDep ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.encabezado { margin-bottom: 0.6rem; gap: 0.8rem; flex-wrap: wrap; }
.tabs { display: flex; gap: 0.4rem; }
.tabs .btn { padding: 0.5rem 1rem; font-weight: 600; }
.tabs .btn.activo { background: var(--acento-suave); color: var(--acento); }
.busca { max-width: 360px; flex: 1; min-width: 200px; }
.nota { margin: 0 0 1rem; font-size: 0.85rem; }
.acciones { display: flex; gap: 0.4rem; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
.campo-ancho { grid-column: 1 / -1; }
.foto-fila { display: flex; align-items: center; gap: 0.8rem; }
.foto-prev { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; background: var(--border); }
.foto-prev.sinfoto { display: flex; align-items: center; justify-content: center; font-size: 0.7rem; color: var(--muted); }
.foto-acciones { display: flex; gap: 0.4rem; }
.mini { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.mini.sinfoto { display: inline-flex; align-items: center; justify-content: center; background: var(--border); color: var(--muted); }
.oculto { display: none; }
.filtros { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; margin-bottom: 0.8rem; }
.filtros .control { width: auto; min-width: 170px; }
.cuenta { font-size: 0.85rem; }
.tarea { display: block; font-size: 0.78rem; margin-top: 0.15rem; }
tr.inactivo td { opacity: 0.6; }
tr.clicable { cursor: pointer; }
.detalle { display: flex; gap: 1rem; align-items: flex-start; }
.detalle dl { display: grid; grid-template-columns: auto 1fr; gap: 0.35rem 0.9rem; margin: 0; }
.detalle dt { color: var(--muted); font-size: 0.85rem; }
.detalle dd { margin: 0; }
@media (max-width: 600px) { .grid2 { grid-template-columns: 1fr; } }
</style>