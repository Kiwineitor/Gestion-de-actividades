<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import PlanoMesas from '../components/PlanoMesas.vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch, jsonOError } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { alertaConAccion } from '../ui/alerta';
import { escucharTopicPublico } from '../ws.js';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';
import { useRoute, useRouter } from 'vue-router';

const auth = useAuthStore();
const permisos = usePermisosStore();
const route = useRoute();
const router = useRouter();
const esAdmin = computed(() => auth.puedeEditarPlano || permisos.puedeVer('mesas-diseno'));
const puedeValidar = computed(() => auth.puedeEditarPlano || permisos.puedeVer('mesas'));

const ESTADOS = {
  BLOQUEADA: { txt: 'Bloqueada', clase: 'badge-parcial' },
  EN_REVISION: { txt: 'Por validar', clase: 'badge-info' },
  CONFIRMADA: { txt: 'Confirmada', clase: 'badge-ok' },
  RECHAZADA: { txt: 'Rechazada', clase: 'badge-danger' },
  VENCIDA: { txt: 'Vencida', clase: 'badge-muted' },
  CANCELADA: { txt: 'Cancelada', clase: 'badge-muted' },
};
const ESTADO_MESA = { L: 'Libre', T: 'Bloqueada', R: 'En revisión', O: 'Vendida', X: 'Fuera de venta' };

const eventos = ref([]);
const selId = ref(null);
const detalle = ref(null);
const pestana = ref('reservas');
const cargando = ref(true);
const guardando = ref(false);

const form = reactive({});
const mesasEdit = ref([]);
const mesaSel = ref(null);
const cambiosMesas = ref(false);
const reservas = ref([]);
const filtro = ref('');
const rechazo = reactive({ abierto: false, reserva: null, motivo: '' });
const ingreso = reactive({ codigo: '', resultado: null, enviando: false });
let cerrarCanal = null;
let pendiente = null;

const aspecto = computed(() => (detalle.value?.planoAncho && detalle.value?.planoAlto ? detalle.value.planoAlto / detalle.value.planoAncho : 0.62));
const reservasFiltradas = computed(() => reservas.value.filter((r) => !filtro.value || r.estado === filtro.value));
const porValidar = computed(() => reservas.value.filter((r) => r.estado === 'EN_REVISION').length);
const enlacePublico = computed(() => (detalle.value ? `${location.origin}${import.meta.env.BASE_URL}?evento=${detalle.value.id}` : ''));
const mesaElegida = computed(() => mesasEdit.value.find((m) => (m.id ?? m.clave) === mesaSel.value) || null);
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO')}`;
const hora = (s) => (s ? s.replace('T', ' ').slice(0, 16) : '—');

function vacio() {
  const manana = new Date(Date.now() + 7 * 864e5);
  const fecha = manana.toISOString().slice(0, 10);
  return {
    nombre: '', localNombre: '', localDireccion: '', descripcion: '', fechaInicio: `${fecha}T22:00`, horaLimite: `${fecha}T23:30`,
    minutosBloqueo: 5, pagoBanco: '', pagoCuenta: '', pagoTitular: '', pagoInstrucciones: '', celularContacto: '',
    politicas: '', publicado: false,
  };
}

async function cargarEventos(conservar = selId.value) {
  cargando.value = true;
  try {
    eventos.value = await jsonOError(await apiFetch('/api/app/mesas/eventos'), 'No se pudieron cargar los eventos');
    const id = eventos.value.some((e) => e.id === conservar) ? conservar : eventos.value[0]?.id ?? null;
    if (id) await elegir(id); else if (esAdmin.value) nuevoEvento();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function elegir(id) {
  if (cambiosMesas.value && !confirm('Hay cambios sin guardar en el plano. ¿Descartarlos?')) return;
  selId.value = id;
  await cargarDetalle();
  if (puedeValidar.value) await cargarReservas();
  if (!puedeValidar.value && (pestana.value === 'reservas' || pestana.value === 'ingreso')) pestana.value = 'evento';
  cerrarCanal?.();
  cerrarCanal = escucharTopicPublico(`/topic/publico/mesas/${id}`, { onMensaje: programarRecarga });
}

async function cargarDetalle() {
  const d = await jsonOError(await apiFetch(`/api/app/mesas/eventos/${selId.value}`), 'No se pudo cargar el evento');
  detalle.value = d;
  Object.assign(form, vacio(), {
    ...d, fechaInicio: d.fechaInicio?.slice(0, 16) || '', horaLimite: d.horaLimite?.slice(0, 16) || '',
    politicas: d.politicasPropias || '',
  });
  mesasEdit.value = d.mesas.map((m) => ({ ...m }));
  cambiosMesas.value = false;
}

async function cargarReservas() {
  if (!selId.value) return;
  reservas.value = await jsonOError(await apiFetch(`/api/app/mesas/eventos/${selId.value}/reservas`), 'No se pudieron cargar las reservas');
}

function programarRecarga() {
  clearTimeout(pendiente);
  pendiente = setTimeout(async () => {
    if (puedeValidar.value) await cargarReservas().catch(() => {});
    const d = await jsonOError(await apiFetch(`/api/app/mesas/eventos/${selId.value}`), '').catch(() => null);
    if (!d) return;
    detalle.value = { ...detalle.value, mesas: d.mesas };
    if (!cambiosMesas.value) mesasEdit.value = d.mesas.map((m) => ({ ...m }));
    else for (const m of mesasEdit.value) {
      const actual = d.mesas.find((x) => x.id === m.id);
      if (actual) Object.assign(m, { estado: actual.estado, reservadoPor: actual.reservadoPor });
    }
  }, 400);
}

function nuevoEvento() {
  selId.value = null;
  detalle.value = null;
  Object.assign(form, vacio());
  pestana.value = 'evento';
}

async function guardarEvento() {
  guardando.value = true;
  try {
    const cuerpo = { ...form, minutosBloqueo: Number(form.minutosBloqueo) || 5 };
    const r = selId.value
      ? await apiFetch(`/api/app/mesas/eventos/${selId.value}`, { method: 'PATCH', body: JSON.stringify(cuerpo) })
      : await apiFetch('/api/app/mesas/eventos', { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    toast(d.mensaje, 'ok', 1800);
    await cargarEventos(d.id);
  } finally {
    guardando.value = false;
  }
}

async function subirImagen(evento, tipo) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo || !selId.value) return;
  const datos = new FormData();
  datos.append('archivo', archivo);
  const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/${tipo}`, { method: 'POST', body: datos });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir la imagen', 'error'); return; }
  toast(d.mensaje, 'ok', 1600);
  await cargarDetalle();
}

async function quitarQr() {
  const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/qr-pago`, { method: 'DELETE' });
  if (r.ok) await cargarDetalle();
}

function agregarMesa() {
  const numeros = mesasEdit.value.map((m) => parseInt(m.codigo, 10)).filter(Number.isFinite);
  const ultima = mesasEdit.value[mesasEdit.value.length - 1];
  const nueva = {
    clave: `n${Date.now()}`, codigo: String((numeros.length ? Math.max(...numeros) : 0) + 1),
    zona: ultima?.zona || 'General', color: ultima?.color || '#6d5bd0', capacidad: ultima?.capacidad || 4,
    precio: ultima?.precio || 0, beneficios: ultima?.beneficios || '', forma: ultima?.forma || 'circulo',
    tamano: ultima?.tamano || 0.06, mapaX: 0.5, mapaY: 0.5, estado: 'L',
  };
  mesasEdit.value.push(nueva);
  mesaSel.value = nueva.clave;
  cambiosMesas.value = true;
}

function moverMesa(m, x, y) {
  m.mapaX = Math.round(x * 1000) / 1000;
  m.mapaY = Math.round(y * 1000) / 1000;
  cambiosMesas.value = true;
}

async function guardarMesas() {
  guardando.value = true;
  try {
    const lista = mesasEdit.value.map((m) => ({
      id: m.id ?? null, codigo: m.codigo, zona: m.zona, capacidad: Number(m.capacidad) || 1, precio: Number(m.precio) || 0,
      beneficios: m.beneficios, forma: m.forma, color: m.color, mapaX: m.mapaX, mapaY: m.mapaY, tamano: Number(m.tamano) || 0.06,
    }));
    const r = await apiFetch(`/api/app/mesas/eventos/${selId.value}/mesas`, { method: 'PUT', body: JSON.stringify(lista) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar el plano', 'error'); return; }
    toast('Plano guardado ✓', 'ok', 1600);
    mesasEdit.value = d.mesas.map((m) => ({ ...m }));
    cambiosMesas.value = false;
    mesaSel.value = null;
  } finally {
    guardando.value = false;
  }
}

async function quitarMesa(m) {
  if (!m.id) {
    mesasEdit.value = mesasEdit.value.filter((x) => x !== m);
    mesaSel.value = null;
    return;
  }
  const si = await alertaConAccion(`¿Eliminar la mesa ${m.codigo}?`, 'advertencia', () => {});
  if (!si) return;
  const r = await apiFetch(`/api/app/mesas/${m.id}`, { method: 'DELETE' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo eliminar', 'error'); return; }
  mesasEdit.value = mesasEdit.value.filter((x) => x.id !== m.id);
  mesaSel.value = null;
}

async function alternarVenta(m) {
  const r = await apiFetch(`/api/app/mesas/${m.id}/venta`, { method: 'PATCH', body: JSON.stringify({ fueraDeVenta: m.estado === 'L' }) });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo cambiar', 'error'); return; }
  m.estado = m.estado === 'L' ? 'X' : 'L';
}

async function confirmar(r) {
  const si = await alertaConAccion(`¿Validar el pago de ${r.nombre} ${r.apellidos} por ${bs(r.monto)} (mesa ${r.mesa})?`, 'advertencia', () => {});
  if (!si) return;
  const res = await apiFetch(`/api/app/mesas/reservas/${r.id}/confirmar`, { method: 'POST' });
  const d = await res.json().catch(() => ({}));
  toast(d.mensaje || 'No se pudo validar', res.ok && d.ok ? 'ok' : 'error');
  await cargarReservas();
}

async function enviarRechazo() {
  const res = await apiFetch(`/api/app/mesas/reservas/${rechazo.reserva.id}/rechazar`, { method: 'POST', body: JSON.stringify({ motivo: rechazo.motivo }) });
  const d = await res.json().catch(() => ({}));
  if (!res.ok || !d.ok) { toast(d.mensaje || 'No se pudo rechazar', 'error'); return; }
  toast(d.mensaje, 'ok');
  rechazo.abierto = false;
  await cargarReservas();
}

async function noPresentado(r) {
  const si = await alertaConAccion(`¿Liberar la mesa ${r.mesa} de ${r.nombre} ${r.apellidos} por no presentarse?`, 'advertencia', () => {});
  if (!si) return;
  const res = await apiFetch(`/api/app/mesas/reservas/${r.id}/no-presentado`, { method: 'POST' });
  const d = await res.json().catch(() => ({}));
  toast(d.mensaje || 'No se pudo liberar', res.ok && d.ok ? 'ok' : 'error');
  await cargarReservas();
}

async function registrarIngreso() {
  const codigo = (ingreso.codigo.match(/MSA-[A-Z0-9]+/i)?.[0] || ingreso.codigo).trim().toUpperCase();
  if (!codigo) return;
  ingreso.enviando = true;
  try {
    const r = await apiFetch('/api/app/mesas/ingreso', { method: 'POST', body: JSON.stringify({ codigo }) });
    ingreso.resultado = await r.json().catch(() => ({ ok: false, mensaje: 'Error de conexión' }));
    ingreso.codigo = '';
    if (ingreso.resultado.ok) await cargarReservas();
  } finally {
    ingreso.enviando = false;
  }
}

async function copiarEnlace() {
  try {
    await navigator.clipboard.writeText(enlacePublico.value);
    toast('Enlace copiado', 'ok', 1500);
  } catch {
    toast(enlacePublico.value, 'info', 6000);
  }
}

function desdeRuta() {
  const q = route.query;
  if (q.nuevo === '1' && esAdmin.value) {
    nuevoEvento();
    router.replace({ query: { m: q.m } });
    return true;
  }
  const id = Number(q.evento);
  if (q.tab && ['reservas', 'ingreso', 'evento', 'plano'].includes(q.tab)) pestana.value = q.tab;
  if (id) {
    if (id !== selId.value) cargarEventos(id);
    return true;
  }
  return false;
}

watch(pestana, (p) => { if (p !== 'plano') mesaSel.value = null; });
watch(() => [route.query.evento, route.query.tab, route.query.nuevo], () => { if (route.query.m === 'mesas') desdeRuta(); });
onMounted(() => { if (!desdeRuta()) cargarEventos(); });
onUnmounted(() => { cerrarCanal?.(); clearTimeout(pendiente); });
</script>

<template>
  <div class="mesas-gestion">
    <aside class="card lista">
      <div class="lista-cab">
        <strong>Eventos</strong>
        <button v-if="esAdmin" class="btn btn-primario btn-sm" @click="nuevoEvento">＋ Nuevo</button>
      </div>
      <p v-if="cargando" class="muted">Cargando…</p>
      <ul>
        <li v-for="e in eventos" :key="e.id">
          <button :class="{ on: e.id === selId }" @click="elegir(e.id)">
            <span class="fila-ev"><strong>{{ e.nombre }}</strong><span v-if="e.porValidar" class="badge badge-info">{{ e.porValidar }}</span></span>
            <span class="muted chico">{{ e.localNombre }} · {{ hora(e.fechaInicio) }}</span>
            <span class="muted chico">{{ e.vendidas }}/{{ e.mesas }} vendidas · {{ bs(e.confirmado) }}<template v-if="!e.publicado"> · borrador</template></span>
          </button>
        </li>
      </ul>
    </aside>

    <section class="card principal">
      <header v-if="detalle" class="cab">
        <div>
          <h3>{{ detalle.nombre }}</h3>
          <span class="muted chico">{{ detalle.localNombre }} · {{ detalle.fechaTexto }}</span>
        </div>
        <span class="badge" :class="detalle.publicado ? 'badge-ok' : 'badge-muted'">{{ detalle.publicado ? 'Publicado' : 'Borrador' }}</span>
        <button v-if="detalle.publicado" class="btn btn-sm" @click="copiarEnlace">Copiar enlace público</button>
        <a v-if="detalle.publicado" class="btn btn-sm" :href="enlacePublico" target="_blank" rel="noopener">Abrir</a>
      </header>
      <h3 v-else>Nuevo evento</h3>

      <nav class="pestanas">
        <button v-if="detalle && puedeValidar" :class="{ on: pestana === 'reservas' }" @click="pestana = 'reservas'">Reservas<span v-if="porValidar"> ({{ porValidar }})</span></button>
        <button v-if="detalle && puedeValidar" :class="{ on: pestana === 'ingreso' }" @click="pestana = 'ingreso'">Ingreso</button>
        <button v-if="esAdmin" :class="{ on: pestana === 'evento' }" @click="pestana = 'evento'">Evento y pago</button>
        <button v-if="esAdmin && detalle" :class="{ on: pestana === 'plano' }" @click="pestana = 'plano'">Plano y mesas</button>
      </nav>

      <div v-if="pestana === 'evento'" class="formulario">
        <div class="dos">
          <label class="campo"><span>Nombre del evento</span><input id="ev-nombre" v-model="form.nombre" class="control" maxlength="120" placeholder="Noche Neón" /></label>
          <label class="campo"><span>Local</span><input id="ev-local" v-model="form.localNombre" class="control" maxlength="120" /></label>
          <label class="campo"><span>Fecha y hora</span><input id="ev-fecha" v-model="form.fechaInicio" type="datetime-local" class="control" /></label>
          <label class="campo"><span>Hora límite de llegada</span><input id="ev-limite" v-model="form.horaLimite" type="datetime-local" class="control" /></label>
          <label class="campo"><span>Dirección</span><input id="ev-dir" v-model="form.localDireccion" class="control" maxlength="200" /></label>
          <label class="campo"><span>Minutos de bloqueo</span><input id="ev-min" v-model.number="form.minutosBloqueo" type="number" min="2" max="30" class="control" /></label>
        </div>
        <label class="campo"><span>Descripción</span><input id="ev-desc" v-model="form.descripcion" class="control" maxlength="500" /></label>

        <h4>Cómo paga el cliente</h4>
        <div class="pago">
          <div class="qr-caja">
            <img v-if="detalle?.pagoQrUrl" :src="urlApi(detalle.pagoQrUrl)" alt="QR de pago" />
            <span v-else class="muted chico">Sin QR</span>
            <label v-if="selId" class="btn btn-sm">{{ detalle?.pagoQrUrl ? 'Cambiar QR' : 'Subir QR de pago' }}
              <input type="file" accept="image/png,image/jpeg,image/webp" class="oculto" @change="subirImagen($event, 'qr-pago')" />
            </label>
            <button v-if="detalle?.pagoQrUrl" class="btn btn-sm btn-fantasma" @click="quitarQr">Quitar</button>
            <span v-if="!selId" class="muted chico">Guarda el evento para subir el QR.</span>
          </div>
          <div class="dos">
            <label class="campo"><span>Banco</span><input id="ev-banco" v-model="form.pagoBanco" class="control" maxlength="80" /></label>
            <label class="campo"><span>Número de cuenta</span><input id="ev-cuenta" v-model="form.pagoCuenta" class="control" maxlength="60" /></label>
            <label class="campo"><span>Titular</span><input id="ev-titular" v-model="form.pagoTitular" class="control" maxlength="120" /></label>
            <label class="campo"><span>Celular de consultas (WhatsApp público)</span><input id="ev-cel" v-model="form.celularContacto" class="control" maxlength="30" /></label>
          </div>
        </div>
        <label class="campo"><span>Instrucciones de pago</span><input id="ev-instr" v-model="form.pagoInstrucciones" class="control" maxlength="500" placeholder="Ej. transfiere el monto exacto" /></label>

        <label class="campo"><span>Condiciones de la reserva (una por línea)</span>
          <textarea id="ev-pol" v-model="form.politicas" class="control" rows="7" :placeholder="detalle?.politicas || 'Si lo dejas vacío se usan las condiciones básicas del sistema.'"></textarea>
        </label>
        <p class="muted chico">Si lo dejas vacío, se muestran las condiciones básicas con tu hora límite y tus minutos de bloqueo.</p>

        <label class="check"><input id="ev-pub" v-model="form.publicado" type="checkbox" /> Publicado: el enlace público acepta reservas</label>
        <div class="acciones"><button class="btn btn-primario" :disabled="guardando" @click="guardarEvento">{{ guardando ? 'Guardando…' : 'Guardar evento' }}</button></div>
      </div>

      <div v-else-if="pestana === 'plano'" class="plano-tab">
        <div class="barra">
          <button class="btn btn-sm" @click="agregarMesa">＋ Mesa</button>
          <label class="btn btn-sm">{{ detalle?.planoUrl ? 'Cambiar imagen del plano' : 'Subir imagen del plano' }}
            <input type="file" accept="image/png,image/jpeg,image/webp" class="oculto" @change="subirImagen($event, 'plano')" />
          </label>
          <span class="crecer"></span>
          <span v-if="cambiosMesas" class="pendiente">Cambios sin guardar</span>
          <button class="btn btn-primario btn-sm" :disabled="!cambiosMesas || guardando" @click="guardarMesas">Guardar plano</button>
        </div>
        <div class="plano-cuerpo">
          <PlanoMesas :mesas="mesasEdit" :plano-url="detalle?.planoUrl" :aspecto="aspecto" editable gestion
                      :seleccionada="mesaSel" @elegir="(m) => (mesaSel = m.id ?? m.clave)" @mover="moverMesa" />
          <aside v-if="mesaElegida" class="ficha-mesa">
            <div class="ficha-cab"><strong>Mesa {{ mesaElegida.codigo }}</strong><span class="muted chico">{{ ESTADO_MESA[mesaElegida.estado] }}</span></div>
            <div class="dos">
              <label class="campo"><span>Número</span><input v-model="mesaElegida.codigo" class="control" maxlength="20" @input="cambiosMesas = true" /></label>
              <label class="campo"><span>Zona</span><input v-model="mesaElegida.zona" class="control" maxlength="60" @input="cambiosMesas = true" /></label>
              <label class="campo"><span>Precio (Bs)</span><input v-model.number="mesaElegida.precio" type="number" min="0" class="control" @input="cambiosMesas = true" /></label>
              <label class="campo"><span>Capacidad</span><input v-model.number="mesaElegida.capacidad" type="number" min="1" max="50" class="control" @input="cambiosMesas = true" /></label>
              <label class="campo"><span>Forma</span>
                <select v-model="mesaElegida.forma" class="control" @change="cambiosMesas = true">
                  <option value="circulo">Redonda</option><option value="cuadrado">Cuadrada</option><option value="rectangulo">Rectangular</option>
                </select>
              </label>
              <label class="campo"><span>Color de zona</span><input :value="mesaElegida.color || '#6d5bd0'" type="color" class="control control-color" @input="mesaElegida.color = $event.target.value; cambiosMesas = true" /></label>
            </div>
            <label class="campo"><span>Tamaño · {{ Math.round((mesaElegida.tamano || 0.06) * 1000) / 10 }}%</span>
              <input v-model.number="mesaElegida.tamano" type="range" min="0.02" max="0.15" step="0.005" @input="cambiosMesas = true" />
            </label>
            <label class="campo"><span>Incluye</span><input v-model="mesaElegida.beneficios" class="control" maxlength="300" placeholder="1 botella + 4 mixers" @input="cambiosMesas = true" /></label>
            <p v-if="mesaElegida.reservadoPor" class="muted chico">Reserva de {{ mesaElegida.reservadoPor }}</p>
            <div class="acciones">
              <button v-if="mesaElegida.id && (mesaElegida.estado === 'L' || mesaElegida.estado === 'X')" class="btn btn-sm" @click="alternarVenta(mesaElegida)">
                {{ mesaElegida.estado === 'X' ? 'Volver a vender' : 'Sacar de venta' }}
              </button>
              <button v-if="!mesaElegida.id || mesaElegida.estado === 'L' || mesaElegida.estado === 'X'" class="btn btn-sm btn-peligro" @click="quitarMesa(mesaElegida)">Eliminar</button>
            </div>
          </aside>
          <p v-else class="muted chico ayuda">Arrastra las mesas para ubicarlas. Toca una para editar su número, zona, precio y capacidad.</p>
        </div>
      </div>

      <div v-else-if="pestana === 'reservas'" class="reservas">
        <div class="filtros">
          <select v-model="filtro" class="control">
            <option value="">Todas ({{ reservas.length }})</option>
            <option v-for="(v, k) in ESTADOS" :key="k" :value="k">{{ v.txt }}</option>
          </select>
          <span class="muted chico">Se actualiza solo cuando un cliente reserva o envía su comprobante.</span>
        </div>
        <p v-if="!reservasFiltradas.length" class="vacio">Sin reservas.</p>
        <p v-if="porValidar" class="muted chico">Al validar o rechazar, el cliente lo ve al instante en su página y recibe un WhatsApp si hay una instancia activa.</p>
        <article v-for="r in reservasFiltradas" :key="r.id" class="reserva" :class="{ destacar: r.estado === 'EN_REVISION' }">
          <div class="r-cab">
            <strong>Mesa {{ r.mesa }}</strong>
            <span class="badge" :class="ESTADOS[r.estado]?.clase">{{ ESTADOS[r.estado]?.txt }}</span>
            <span class="crecer"></span>
            <strong>{{ bs(r.monto) }}</strong>
          </div>
          <div class="r-datos">
            <span>{{ r.nombre }} {{ r.apellidos }}</span>
            <span>C.I. {{ r.ci }}</span>
            <span>Cel. {{ r.celular }}</span>
            <span v-if="r.correo">{{ r.correo }}</span>
            <span class="muted">{{ r.codigo }} · {{ hora(r.creadoEn) }}</span>
          </div>
          <p v-if="r.motivo" class="muted chico">{{ r.motivo }}</p>
          <p v-if="r.validadoPor" class="muted chico">Validado por {{ r.validadoPor }} · {{ hora(r.validadoEn) }}</p>
          <p v-if="r.ingresoEn" class="muted chico">Ingresó {{ hora(r.ingresoEn) }}</p>
          <div class="acciones">
            <a v-if="r.comprobanteUrl" class="btn btn-sm" :href="urlApi(r.comprobanteUrl)" target="_blank" rel="noopener">Ver comprobante</a>
            <template v-if="r.estado === 'EN_REVISION'">
              <button class="btn btn-sm btn-primario" @click="confirmar(r)">Validar pago</button>
              <button class="btn btn-sm btn-peligro" @click="Object.assign(rechazo, { abierto: true, reserva: r, motivo: '' })">Rechazar</button>
            </template>
            <button v-if="r.estado === 'CONFIRMADA' && !r.ingresoEn" class="btn btn-sm btn-fantasma" @click="noPresentado(r)">No se presentó</button>
          </div>
        </article>
      </div>

      <div v-else-if="pestana === 'ingreso'" class="ingreso">
        <form class="fila-ingreso" @submit.prevent="registrarIngreso">
          <input id="in-codigo" v-model="ingreso.codigo" class="control" placeholder="Escanea o escribe el código MSA-…" autocomplete="off" />
          <button class="btn btn-primario" :disabled="ingreso.enviando">Registrar ingreso</button>
        </form>
        <div v-if="ingreso.resultado" class="resultado" :class="ingreso.resultado.ok ? 'bien' : 'mal'">
          <strong>{{ ingreso.resultado.mensaje }}</strong>
          <template v-if="ingreso.resultado.reserva">
            <span>Mesa {{ ingreso.resultado.reserva.mesa }} · {{ ingreso.resultado.reserva.nombre }} {{ ingreso.resultado.reserva.apellidos }}</span>
            <span>Capacidad {{ ingreso.resultado.reserva.capacidad }}<template v-if="ingreso.resultado.reserva.beneficios"> · {{ ingreso.resultado.reserva.beneficios }}</template></span>
          </template>
        </div>
        <p class="muted chico">Pide el carnet del titular y compáralo con el nombre de la reserva.</p>
      </div>
    </section>

    <UiModal v-if="rechazo.abierto" :titulo="`Rechazar pago · mesa ${rechazo.reserva?.mesa}`" @cerrar="rechazo.abierto = false">
      <label class="campo"><span>Motivo (lo verá el cliente)</span>
        <textarea id="rz-motivo" v-model="rechazo.motivo" class="control" rows="3" maxlength="300" placeholder="El monto no coincide con el precio de la mesa"></textarea>
      </label>
      <template #pie>
        <button class="btn btn-fantasma" @click="rechazo.abierto = false">Cancelar</button>
        <button class="btn btn-peligro" :disabled="!rechazo.motivo.trim()" @click="enviarRechazo">Rechazar y liberar la mesa</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.mesas-gestion { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 1rem; align-items: start; }
.card { padding: 1rem; }
.lista-cab { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.6rem; }
.lista ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
.lista li button { width: 100%; text-align: left; display: flex; flex-direction: column; gap: 2px; border: 1px solid transparent; background: none; color: var(--text); font: inherit; padding: 0.55rem 0.6rem; border-radius: var(--radio-sm); cursor: pointer; }
.lista li button:hover { background: var(--bg); }
.lista li button.on { background: var(--acento-suave); border-color: var(--acento); }
.fila-ev { display: flex; justify-content: space-between; gap: 0.4rem; }
.chico { font-size: 0.8rem; margin: 0; }
.principal { display: flex; flex-direction: column; gap: 0.9rem; min-width: 0; }
.cab { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.cab > div { flex: 1; min-width: 200px; }
.cab h3, h3 { margin: 0; }
h4 { margin: 0.4rem 0 0; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
.pestanas { display: flex; gap: 0.3rem; border-bottom: 1px solid var(--border); overflow-x: auto; }
.pestanas button { border: none; background: none; color: var(--muted); font: inherit; font-weight: 700; padding: 0.55rem 0.8rem; border-bottom: 2px solid transparent; cursor: pointer; white-space: nowrap; }
.pestanas button.on { color: var(--text); border-bottom-color: var(--acento); }
.formulario { display: flex; flex-direction: column; gap: 0.8rem; }
.dos { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0.7rem; }
.pago { display: grid; grid-template-columns: 180px minmax(0, 1fr); gap: 1rem; align-items: start; }
.qr-caja { display: flex; flex-direction: column; gap: 0.4rem; align-items: center; border: 1px dashed var(--border); border-radius: var(--radio-sm); padding: 0.6rem; }
.qr-caja img { width: 150px; height: 150px; object-fit: contain; background: #fff; border-radius: 8px; }
.check { display: flex; align-items: center; gap: 0.5rem; }
.acciones { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; }
.oculto { display: none; }
.control-color { height: 40px; padding: 2px; }
.barra { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.crecer { flex: 1; }
.pendiente { font-size: 0.8rem; color: var(--tramite); font-weight: 600; }
.plano-cuerpo { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 1rem; align-items: start; }
.ficha-mesa { border: 1px solid var(--border); border-radius: var(--radio); padding: 0.8rem; display: flex; flex-direction: column; gap: 0.6rem; }
.ficha-cab { display: flex; justify-content: space-between; align-items: center; }
.ayuda { align-self: center; }
.filtros { display: flex; align-items: center; gap: 0.8rem; flex-wrap: wrap; }
.filtros .control { width: auto; }
.reserva { border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.7rem 0.8rem; display: flex; flex-direction: column; gap: 0.4rem; }
.reserva.destacar { border-color: var(--acento); box-shadow: 0 0 0 1px var(--acento); }
.reservas { display: flex; flex-direction: column; gap: 0.6rem; }
.r-cab { display: flex; align-items: center; gap: 0.5rem; }
.r-datos { display: flex; flex-wrap: wrap; gap: 0.3rem 1rem; font-size: 0.88rem; }
.ingreso { display: flex; flex-direction: column; gap: 0.8rem; max-width: 560px; }
.fila-ingreso { display: flex; gap: 0.5rem; }
.fila-ingreso .control { font-family: ui-monospace, monospace; font-size: 1.05rem; }
.resultado { border-radius: var(--radio); padding: 1rem; display: flex; flex-direction: column; gap: 0.3rem; }
.resultado.bien { background: color-mix(in srgb, var(--ok) 14%, transparent); border: 1px solid var(--ok); }
.resultado.mal { background: var(--danger-suave); border: 1px solid var(--danger); }
.vacio { text-align: center; color: var(--muted); padding: 2rem; }
@media (max-width: 980px) {
  .mesas-gestion, .plano-cuerpo, .pago { grid-template-columns: 1fr; }
}
</style>
