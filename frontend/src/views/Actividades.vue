<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import UiModal from '../components/UiModal.vue';
import MenuAcciones from '../components/MenuAcciones.vue';
import { apiFetch, jsonOError } from '../api';
import { toast } from '../ui/toast';
import { alertaConAccion, pedirTexto } from '../ui/alerta';
import { cargarActividades } from '../ui/actividadElegida';
import { useAuthStore } from '../stores/auth';
import { useActividadStore } from '../stores/actividad';
import { url as urlApi } from '../config.js';
import { usePermisosStore } from '../stores/permisos';
import { aModulo } from '../modulos';
import { alCambiarPublico } from '../ui/canalPublico';
import { useBorrador } from '../ui/borradores';

const TIPOS = ['Feria', 'Exposición', 'Evento', 'Congreso', 'Festival', 'Otro'];

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
const nuevoAbierto = ref(false);
let soltarCanal = null;
let pendiente = null;

const admin = computed(() => auth.puedeEditarPlano);
const puede = (p) => admin.value || permisos.puedeVer(p);
const veFerias = computed(() => puede('actividades'));
const veEventos = computed(() => puede('mesas') || puede('mesas-diseno'));

const tarjetas = computed(() => {
  const ferias = lista.value.map((a) => ({ ...a, clase: 'feria', clave: `f${a.id}` }));
  const evs = eventos.value.map((e) => ({ ...e, clase: 'evento', clave: `e${e.id}` }));
  const q = busqueda.value.trim().toLowerCase();
  return [...ferias, ...evs]
    .filter((t) => filtro.value === 'todas' || t.clase === filtro.value)
    .filter((t) => !q || `${t.nombre} ${t.localNombre || ''} ${t.lugar || ''} ${t.tipo || ''}`.toLowerCase().includes(q))
    .sort((a, b) => Number(Boolean(b.activa)) - Number(Boolean(a.activa)));
});
const porValidar = computed(() => eventos.value.reduce((s, e) => s + Number(e.porValidar || 0), 0));
const form = reactive({
  abierto: false, editando: null, nombre: '', anio: new Date().getFullYear(), tipo: 'Feria',
  copiarDe: '', copiarPlano: true, copiarTextos: true, guardando: false,
  lugar: '', fechasTexto: '', descripcionPublica: '', contacto: '', publica: true, recibeInteresados: true,
  portadaUrl: null, subiendo: false,
  pagoBanco: '', pagoCuenta: '', pagoTitular: '', pagoInstrucciones: '', pagoOficina: '', pagoQrUrl: null,
});

const borradorFeria = useBorrador('feria', form);

const PUBLICO_VACIO = {
  lugar: '', fechasTexto: '', descripcionPublica: '', contacto: '', publica: true, recibeInteresados: true, portadaUrl: null,
  pagoBanco: '', pagoCuenta: '', pagoTitular: '', pagoInstrucciones: '', pagoOficina: '', pagoQrUrl: null,
};

const activa = computed(() => lista.value.find((a) => a.activa) || null);

async function cargar(silencioso = false) {
  if (!silencioso) cargando.value = true;
  try {
    const [f, e] = await Promise.all([
      veFerias.value ? jsonOError(await apiFetch('/api/app/ediciones/resumen'), 'No se pudieron cargar las ferias') : [],
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

function abrirPublica(query) {
  window.open(router.resolve({ path: '/', query }).href, '_blank', 'noopener');
}

function nuevo(tipo) {
  nuevoAbierto.value = false;
  if (tipo === 'evento') ir('mesas', { nuevo: '1' });
  else abrirCrear();
}

const hora = (s) => (s ? s.replace('T', ' ').slice(0, 16) : '—');
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO')}`;

function abrirCrear() {
  Object.assign(form, {
    abierto: true, editando: null, nombre: '', anio: new Date().getFullYear(), tipo: 'Feria',
    copiarDe: activa.value ? String(activa.value.id) : '', copiarPlano: true, copiarTextos: true, guardando: false,
    ...PUBLICO_VACIO,
  });
}

function abrirEditar(a) {
  Object.assign(form, {
    abierto: true, editando: a, nombre: a.nombre, anio: a.anio, tipo: a.tipo || '', copiarDe: '', guardando: false,
    lugar: a.lugar || '', fechasTexto: a.fechasTexto || '', descripcionPublica: a.descripcionPublica || '',
    contacto: a.contacto || '', publica: a.publica !== false, recibeInteresados: a.recibeInteresados !== false,
    portadaUrl: a.portadaUrl || null,
    pagoBanco: a.pagoBanco || '', pagoCuenta: a.pagoCuenta || '', pagoTitular: a.pagoTitular || '',
    pagoInstrucciones: a.pagoInstrucciones || '', pagoOficina: a.pagoOficina || '', pagoQrUrl: a.pagoQrUrl || null,
  });
}

async function guardar() {
  if (!form.nombre.trim()) { toast('La actividad necesita un nombre', 'advertencia'); return; }
  form.guardando = true;
  try {
    const cuerpo = {
      nombre: form.nombre.trim(), anio: Number(form.anio) || null, tipo: form.tipo || null,
      copiarDe: form.editando || !form.copiarDe ? null : Number(form.copiarDe),
      copiarPlano: form.copiarPlano, copiarTextos: form.copiarTextos,
      lugar: form.lugar, fechasTexto: form.fechasTexto, descripcionPublica: form.descripcionPublica,
      contacto: form.contacto, publica: form.publica, recibeInteresados: form.recibeInteresados,
      pagoBanco: form.pagoBanco, pagoCuenta: form.pagoCuenta, pagoTitular: form.pagoTitular,
      pagoInstrucciones: form.pagoInstrucciones, pagoOficina: form.pagoOficina,
    };
    const r = form.editando
      ? await apiFetch(`/api/app/ediciones/${form.editando.id}`, { method: 'PATCH', body: JSON.stringify(cuerpo) })
      : await apiFetch('/api/app/ediciones', { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    toast(d.mensaje, 'ok');
    form.abierto = false;
    borradorFeria.descartar();
    await cargar();
    await actividad.cargar();
  } finally {
    form.guardando = false;
  }
}

async function subirPortada(e) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (!archivo || !form.editando) return;
  form.subiendo = true;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/ediciones/${form.editando.id}/portada`, { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir la portada', 'error'); return; }
    toast(d.mensaje, 'ok');
    await cargar();
    form.portadaUrl = lista.value.find((x) => x.id === form.editando.id)?.portadaUrl || null;
  } finally {
    form.subiendo = false;
  }
}

async function subirQrPago(e) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (!archivo || !form.editando) return;
  const datos = new FormData();
  datos.append('archivo', archivo);
  const r = await apiFetch(`/api/app/ediciones/${form.editando.id}/qr-pago`, { method: 'POST', body: datos });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir el QR', 'error'); return; }
  toast(d.mensaje, 'ok', 1500);
  await cargar();
  form.pagoQrUrl = lista.value.find((x) => x.id === form.editando.id)?.pagoQrUrl || null;
}

async function quitarQrPago() {
  if (!form.editando) return;
  const r = await apiFetch(`/api/app/ediciones/${form.editando.id}/qr-pago`, { method: 'DELETE' });
  if (!r.ok) { toast('No se pudo quitar el QR', 'error'); return; }
  form.pagoQrUrl = null;
  await cargar();
}

async function quitarPortada() {
  if (!form.editando) return;
  const r = await apiFetch(`/api/app/ediciones/${form.editando.id}/portada`, { method: 'DELETE' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo quitar la portada', 'error'); return; }
  form.portadaUrl = null;
  await cargar();
}

async function activar(a) {
  const si = await alertaConAccion(
    `¿Activar "${a.nombre}"? Todos los usuarios pasarán a ver su plano, sus puestos y sus ventas. `
    + `"${activa.value?.nombre ?? 'La actividad actual'}" queda guardada y se puede volver a activar.`,
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
  const esFeria = t.clase === 'feria';
  const escrito = await pedirTexto(
    `Se borrará «${t.nombre}» con su plano, ${esFeria ? 'categorías y puestos' : 'mesas'}. No se puede deshacer. `
    + `Si tiene ${esFeria ? 'ventas' : 'reservas pagadas o en revisión'}, el sistema no lo permitirá para conservar el historial. `
    + 'Escribe el nombre de la actividad para confirmar.',
    { titulo: 'Eliminar actividad', aceptar: 'Eliminar', marcador: t.nombre },
  );
  if (escrito == null) return;
  if (escrito.trim().toLowerCase() !== t.nombre.trim().toLowerCase()) { toast('El nombre no coincide: no se eliminó nada', 'advertencia'); return; }
  const r = await apiFetch(esFeria ? `/api/app/ediciones/${t.id}` : `/api/app/mesas/eventos/${t.id}`, { method: 'DELETE' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo eliminar', 'error'); return; }
  toast(d.mensaje || 'Actividad eliminada', 'ok');
  await cargar();
  await Promise.all([actividad.cargar(), cargarActividades(true)]);
}

function acciones(a) {
  return [
    { txt: 'Editar datos y página pública', accion: () => abrirEditar(a) },
    { txt: 'Ver página pública', deshabilitado: !a.activa || !a.publica, titulo: !a.activa ? 'Solo la actividad activa se muestra al público' : '', accion: () => abrirPublica({ v: 'actividad' }) },
    {
      txt: 'Poner en curso', deshabilitado: a.activa || !esSuper.value,
      titulo: !esSuper.value ? 'Solo el Superadministrador cambia la feria en curso' : '', accion: () => activar(a),
    },
    {
      txt: 'Eliminar', peligro: true, deshabilitado: a.activa || !puede('actividades'),
      titulo: a.activa ? 'Pon otra feria en curso antes de eliminar esta' : '', accion: () => eliminar(a),
    },
  ];
}

function accionesEvento(e) {
  return [
    { txt: 'Ver página pública', deshabilitado: !e.publicado, titulo: e.publicado ? '' : 'Habilita el evento para verlo', accion: () => abrirPublica({ evento: e.id }) },
    { txt: 'Eliminar', peligro: true, deshabilitado: !puede('mesas-diseno'), accion: () => eliminar(e) },
  ];
}

async function habilitar(t) {
  const encender = t.clase === 'feria' ? !t.publica : !t.publicado;
  const r = t.clase === 'feria'
    ? await apiFetch(`/api/app/ediciones/${t.id}/habilitar`, { method: 'POST', body: JSON.stringify({ habilitada: encender }) })
    : await apiFetch(`/api/app/mesas/eventos/${t.id}/publicar`, { method: 'POST', body: JSON.stringify({ publicado: encender }) });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo cambiar', 'error'); return; }
  toast(d.mensaje, 'ok');
  await cargar(true);
}

const puedeHabilitar = (t) => (t.clase === 'feria' ? puede('actividades') : puede('mesas-diseno'));
const habilitada = (t) => (t.clase === 'feria' ? t.publica !== false : t.publicado);

onMounted(() => {
  borradorFeria.restaurar();
  Object.assign(form, { guardando: false, subiendo: false });
  cargar();
  soltarCanal = alCambiarPublico(() => {
    clearTimeout(pendiente);
    pendiente = setTimeout(() => cargar(true), 700);
  });
});
onUnmounted(() => { soltarCanal?.(); clearTimeout(pendiente); });
</script>

<template>
  <div class="actividades">
    <div class="cab">
      <div class="filtros">
        <button v-for="[k, t] in [['todas', 'Todas'], ['feria', 'Ferias con puestos'], ['evento', 'Eventos con mesas']]" :key="k"
                type="button" class="chip" :class="{ on: filtro === k }" @click="filtro = k">{{ t }}</button>
        <input v-model="busqueda" type="search" class="control buscar" placeholder="Buscar actividad…" aria-label="Buscar actividad" />
      </div>
      <div class="nuevo">
        <button class="btn btn-primario" @click="nuevoAbierto = !nuevoAbierto">＋ Nueva</button>
        <div v-if="nuevoAbierto" class="menu-nuevo">
          <button v-if="admin || puede('actividades')" type="button" @click="nuevo('feria')">
            <strong>🗺️ Feria con puestos</strong><span>Plano con espacios para vender a expositores.</span>
          </button>
          <button v-if="puede('mesas-diseno')" type="button" @click="nuevo('evento')">
            <strong>🍸 Evento con mesas</strong><span>Reserva pública de mesas con pago y comprobante.</span>
          </button>
        </div>
      </div>
    </div>
    <p v-if="porValidar" class="alerta-validar" role="status">
      Hay <b>{{ porValidar }}</b> pago{{ porValidar === 1 ? '' : 's' }} de mesas esperando revisión.
    </p>

    <p v-if="cargando" class="vacio">Cargando…</p>
    <p v-else-if="!tarjetas.length" class="vacio">No hay actividades que coincidan.</p>
    <div v-else class="rejilla">
      <template v-for="t in tarjetas" :key="t.clave">
        <article v-if="t.clase === 'feria'" class="card tarjeta feria" :class="{ activa: t.activa }">
          <header>
            <span class="tipo">🗺️ {{ t.tipo || 'Feria' }} · {{ t.anio }}</span>
            <span v-if="t.activa" class="badge badge-ok" title="Es la feria en curso: la que usan el mapa de ventas y los reportes">En curso</span>
            <MenuAcciones :acciones="acciones(t)" :etiqueta="`Acciones de ${t.nombre}`" />
          </header>
          <h3>{{ t.nombre }}</h3>
          <dl>
            <div><dt>Categorías</dt><dd>{{ t.categorias }}</dd></div>
            <div><dt>Puestos</dt><dd>{{ t.puestos }}</dd></div>
            <div><dt>Vendidos</dt><dd>{{ t.vendidos }}</dd></div>
            <div><dt>Ventas</dt><dd>{{ t.ventas }}</dd></div>
          </dl>
          <label class="interruptor" :class="{ deshabilitado: !puedeHabilitar(t) }">
            <input type="checkbox" :checked="habilitada(t)" :disabled="!puedeHabilitar(t)" @change="habilitar(t)" />
            <span class="pista"></span>
            <span>{{ habilitada(t) ? 'Habilitada: visible y recibiendo solicitudes' : 'Deshabilitada: oculta al público' }}</span>
          </label>
          <div class="atajos">
            <template v-if="t.activa">
              <button v-if="puede('editor')" class="btn btn-sm" @click="ir('editor')">✏️ Editor del plano</button>
              <button v-if="puede('categorias') || puede('puestos')" class="btn btn-sm" @click="ir('categorias')">🏷️ Categorías y precios</button>
              <button v-if="puede('interesados')" class="btn btn-sm" @click="ir('solicitudes', { tab: 'ferias' })">📥 Solicitudes</button>
              <button v-if="puede('inscripciones') || puede('control-ventas')" class="btn btn-sm" @click="ir('inscripciones', { tab: 'todas' })">🧾 Ventas</button>
              <button v-if="puede('credenciales')" class="btn btn-sm" @click="ir('credenciales')">🪪 Credenciales</button>
            </template>
            <span v-else class="muted chico">Ponla en curso (menú ⋮) para editar su plano y precios.</span>
          </div>
        </article>

        <article v-else class="card tarjeta evento">
          <header>
            <span class="tipo">🍸 Evento con mesas</span>
            <MenuAcciones :acciones="accionesEvento(t)" :etiqueta="`Acciones de ${t.nombre}`" />
          </header>
          <h3>{{ t.nombre }}</h3>
          <span class="muted chico">{{ t.localNombre }} · {{ hora(t.fechaInicio) }}</span>
          <dl>
            <div><dt>Mesas</dt><dd>{{ t.mesas }}</dd></div>
            <div><dt>Vendidas</dt><dd>{{ t.vendidas }}</dd></div>
            <div><dt>Por validar</dt><dd :class="{ urgente: t.porValidar }">{{ t.porValidar }}</dd></div>
            <div><dt>Cobrado</dt><dd class="monto">{{ bs(t.confirmado) }}</dd></div>
          </dl>
          <label class="interruptor" :class="{ deshabilitado: !puedeHabilitar(t) }">
            <input type="checkbox" :checked="habilitada(t)" :disabled="!puedeHabilitar(t)" @change="habilitar(t)" />
            <span class="pista"></span>
            <span>{{ habilitada(t) ? 'Habilitado: visible y recibiendo reservas' : 'Deshabilitado: oculto al público' }}</span>
          </label>
          <div class="atajos">
            <button v-if="puede('mapa')" class="btn btn-sm" @click="ir('mapa', { evento: t.id })">🗺️ Vender en el mapa</button>
            <button v-if="puede('mesas')" class="btn btn-sm" :class="{ 'btn-primario': t.porValidar }" @click="ir('solicitudes', { tab: 'mesas', evento: t.id })">
              📥 Solicitudes<template v-if="t.porValidar"> ({{ t.porValidar }})</template>
            </button>
            <button v-if="puede('mesas-diseno')" class="btn btn-sm" @click="ir('mesas', { evento: t.id, tab: 'evento' })">⚙️ Organizar</button>
            <button v-if="puede('mesas-diseno')" class="btn btn-sm" @click="ir('mesas', { evento: t.id, tab: 'plano' })">✏️ Editor del plano</button>
            <button v-if="puede('mesas') || puede('mesas-ingreso')" class="btn btn-sm" @click="ir('ingreso', { tab: 'escanear' })">🎟️ Ingreso</button>
          </div>
        </article>
      </template>
    </div>

    <UiModal v-if="form.abierto" :titulo="form.editando ? `Editar ${form.editando.nombre}` : 'Nueva actividad'"
             @cerrar="!form.guardando && (form.abierto = false)">
      <div class="formulario">
        <label class="campo"><span>Nombre</span><input v-model="form.nombre" class="control" maxlength="120" placeholder="Expo Emprende 2026" /></label>
        <div class="dos">
          <label class="campo"><span>Tipo</span>
            <input v-model="form.tipo" class="control" list="tipos-actividad" maxlength="40" />
            <datalist id="tipos-actividad"><option v-for="t in TIPOS" :key="t" :value="t"></option></datalist>
          </label>
          <label class="campo"><span>Año</span><input v-model.number="form.anio" class="control" type="number" min="2000" max="2100" /></label>
        </div>
        <template v-if="!form.editando">
          <label class="campo"><span>Partir de</span>
            <select v-model="form.copiarDe" class="control">
              <option value="">Vacía (sin categorías ni puestos)</option>
              <option v-for="a in lista" :key="a.id" :value="String(a.id)">Copiar estructura de {{ a.nombre }}</option>
            </select>
          </label>
          <template v-if="form.copiarDe">
            <label class="check"><input v-model="form.copiarPlano" type="checkbox" /> Copiar la imagen del plano</label>
            <label class="check"><input v-model="form.copiarTextos" type="checkbox" /> Copiar los textos del plano</label>
            <p class="muted chico">Se copian categorías, opciones de precio y puestos con su posición, todos libres. No se copian ventas, vendedores asignados ni fotos.</p>
          </template>
          <p class="muted chico">La actividad se crea inactiva. Prepárala y actívala cuando corresponda.</p>
        </template>
        <h4 class="subtitulo">Página pública</h4>
        <div class="dos">
          <label class="campo"><span>Lugar</span><input v-model="form.lugar" class="control" maxlength="200" placeholder="Campo ferial, salón, dirección…" /></label>
          <label class="campo"><span>Fechas</span><input v-model="form.fechasTexto" class="control" maxlength="120" placeholder="12 al 15 de noviembre" /></label>
        </div>
        <label class="campo"><span>Descripción</span>
          <textarea v-model="form.descripcionPublica" class="control" rows="4" maxlength="3000" placeholder="Qué es la actividad, a quién está dirigida, horarios…"></textarea>
        </label>
        <label class="campo"><span>Contacto</span><input v-model="form.contacto" class="control" maxlength="120" placeholder="Celular o correo para consultas" /></label>
        <label class="check"><input v-model="form.publica" type="checkbox" /> Mostrar en la página pública cuando esté activa</label>
        <label class="check"><input v-model="form.recibeInteresados" type="checkbox" /> Recibir solicitudes de interesados en participar</label>
        <div v-if="form.editando" class="portada">
          <img v-if="form.portadaUrl" :src="urlApi(form.portadaUrl)" alt="Portada" />
          <div class="portada-acciones">
            <label class="btn btn-sm" :class="{ deshabilitado: form.subiendo }">
              {{ form.subiendo ? 'Subiendo…' : form.portadaUrl ? 'Cambiar portada' : 'Subir portada' }}
              <input type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="form.subiendo" @change="subirPortada" />
            </label>
            <button v-if="form.portadaUrl" type="button" class="btn btn-sm btn-fantasma" @click="quitarPortada">Quitar</button>
          </div>
        </div>
        <p v-else class="muted chico">La portada se sube después de crear la actividad, al editarla.</p>
        <h4 class="subtitulo">Cómo pagan los expositores</h4>
        <p class="muted chico">Se muestra en la página pública cuando alguien solicita un puesto: puede adjuntar su comprobante o pagar en oficinas.</p>
        <div class="pago-feria">
          <div class="qr-feria">
            <img v-if="form.pagoQrUrl" :src="urlApi(form.pagoQrUrl)" alt="QR de pago" />
            <span v-else class="muted chico">Sin QR</span>
            <template v-if="form.editando">
              <label class="btn btn-sm">{{ form.pagoQrUrl ? 'Cambiar QR' : 'Subir QR' }}
                <input type="file" accept="image/png,image/jpeg,image/webp" hidden @change="subirQrPago" />
              </label>
              <button v-if="form.pagoQrUrl" type="button" class="btn btn-sm btn-fantasma" @click="quitarQrPago">Quitar</button>
            </template>
            <span v-else class="muted chico">Se sube al editar.</span>
          </div>
          <div class="dos">
            <label class="campo"><span>Banco</span><input v-model="form.pagoBanco" class="control" maxlength="80" /></label>
            <label class="campo"><span>Número de cuenta</span><input v-model="form.pagoCuenta" class="control" maxlength="60" /></label>
            <label class="campo"><span>Titular</span><input v-model="form.pagoTitular" class="control" maxlength="120" /></label>
            <label class="campo"><span>Instrucciones</span><input v-model="form.pagoInstrucciones" class="control" maxlength="500" placeholder="Ej. transfiere el monto exacto" /></label>
          </div>
        </div>
        <label class="campo"><span>Oficinas para pagar en persona</span>
          <textarea v-model="form.pagoOficina" class="control" rows="2" maxlength="300" placeholder="Dirección y horario, ej. Av. Principal 123, lunes a viernes de 8:30 a 16:30"></textarea>
        </label>
      </div>
      <template #pie>
        <button class="btn btn-fantasma" :disabled="form.guardando" @click="form.abierto = false; borradorFeria.descartar()">Cancelar</button>
        <button class="btn btn-primario" :disabled="form.guardando" @click="guardar">{{ form.guardando ? 'Guardando…' : 'Guardar' }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.actividades { display: flex; flex-direction: column; gap: 1rem; }
.cab { display: flex; justify-content: space-between; align-items: center; gap: 0.8rem; flex-wrap: wrap; }
.filtros { display: flex; gap: 0.4rem; align-items: center; flex-wrap: wrap; }
.chip { border: 1px solid var(--border); background: var(--panel); color: var(--muted); border-radius: 999px; padding: 0.35rem 0.8rem; font: inherit; font-size: 0.85rem; font-weight: 700; cursor: pointer; }
.chip.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto, #fff); }
.buscar { width: 220px; }
.nuevo { position: relative; }
.menu-nuevo {
  position: absolute; right: 0; top: calc(100% + 6px); z-index: 20; width: 300px; background: var(--panel);
  border: 1px solid var(--border); border-radius: var(--radio); box-shadow: var(--sombra-md); padding: 0.4rem; display: flex; flex-direction: column;
}
.menu-nuevo button { text-align: left; border: 0; background: none; color: var(--text); font: inherit; padding: 0.6rem 0.7rem; border-radius: var(--radio-sm); cursor: pointer; display: flex; flex-direction: column; gap: 0.15rem; }
.menu-nuevo button:hover { background: var(--panel-2); }
.menu-nuevo span { font-size: 0.8rem; color: var(--muted); }
.alerta-validar { margin: 0; padding: 0.6rem 0.9rem; border-radius: var(--radio-sm); background: var(--acento-suave); color: var(--acento); font-weight: 600; }
.rejilla { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 1rem; }
.tarjeta { padding: 1rem; display: flex; flex-direction: column; gap: 0.6rem; border-top: 4px solid var(--border); }
.tarjeta.feria { border-top-color: #f0a33a; }
.tarjeta.evento { border-top-color: var(--acento); }
.tarjeta.activa { box-shadow: 0 0 0 1px var(--acento); }
.tarjeta header { display: flex; align-items: center; gap: 0.5rem; }
.tipo { flex: 1; font-size: 0.75rem; font-weight: 700; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.tarjeta h3 { margin: 0; font-size: 1.1rem; }
.urgente { color: var(--acento); }
.interruptor { display: inline-flex; align-items: center; gap: 0.5rem; cursor: pointer; font-size: 0.82rem; font-weight: 600; }
.interruptor.deshabilitado { cursor: not-allowed; opacity: 0.6; }
.interruptor input { position: absolute; opacity: 0; width: 0; height: 0; }
.interruptor .pista { flex: none; width: 36px; height: 20px; border-radius: 999px; background: var(--border); position: relative; transition: background 0.15s; }
.interruptor .pista::after { content: ''; position: absolute; top: 3px; left: 3px; width: 14px; height: 14px; border-radius: 50%; background: #fff; transition: transform 0.15s; }
.interruptor input:checked + .pista { background: var(--ok); }
.interruptor input:checked + .pista::after { transform: translateX(16px); }
.interruptor input:focus-visible + .pista { outline: 2px solid var(--acento); outline-offset: 2px; }
.monto { font-size: 0.95rem !important; }
.atajos { display: flex; gap: 0.4rem; flex-wrap: wrap; margin-top: auto; padding-top: 0.3rem; border-top: 1px dashed var(--border); }
.vacio { text-align: center; color: var(--muted); padding: 2rem; }
@media (max-width: 600px) { .buscar { width: 100%; } .menu-nuevo { width: min(300px, 90vw); } }
.tarjeta dl { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.4rem; margin: 0; }
.tarjeta dt { font-size: 0.72rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.tarjeta dd { margin: 0; font-size: 1.1rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.chico { font-size: 0.82rem; margin: 0; }
.formulario { display: flex; flex-direction: column; gap: 0.8rem; }
.dos { display: grid; grid-template-columns: 2fr 1fr; gap: 0.8rem; }
.check { display: flex; align-items: center; gap: 0.45rem; }
.subtitulo { margin: 0.6rem 0 0; font-size: 0.95rem; }
.portada { display: flex; flex-direction: column; gap: 0.5rem; }
.portada img { width: 100%; max-height: 180px; object-fit: cover; border-radius: var(--radio, 10px); border: 1px solid var(--border); }
.portada-acciones { display: flex; gap: 0.5rem; }
.pago-feria { display: grid; grid-template-columns: 130px minmax(0, 1fr); gap: 0.8rem; align-items: start; }
.qr-feria { display: flex; flex-direction: column; align-items: center; gap: 0.4rem; border: 1px dashed var(--border); border-radius: var(--radio-sm); padding: 0.5rem; text-align: center; }
.qr-feria img { width: 110px; height: 110px; object-fit: contain; background: #fff; border-radius: 6px; }
@media (max-width: 560px) { .pago-feria { grid-template-columns: 1fr; } }
.deshabilitado { opacity: 0.5; pointer-events: none; }
</style>
