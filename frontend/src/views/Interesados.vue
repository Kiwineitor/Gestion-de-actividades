<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import AprobarSolicitud from '../components/AprobarSolicitud.vue';
import { apiFetch } from '../api';
import { aviso, confirmar } from '../ui/alerta';
import { url as urlApi } from '../config.js';
import { vFiltro } from '../ui/filtroEntrada.js';
import { escucharTopic } from '../ws.js';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { toast } from '../ui/toast';
import { useSolicitudesStore } from '../stores/solicitudes';

const ESTADOS = {
  NUEVO: { texto: 'Por revisar', plural: 'Por revisar', icono: '🕓' },
  ATENDIDO: { texto: 'Aprobada', plural: 'Aprobadas', icono: '✓' },
  DESCARTADO: { texto: 'Rechazada', plural: 'Rechazadas', icono: '✕' },
};
const PAGO = { COMPROBANTE: '📎 Adjuntó comprobante', OFICINA: '🏢 Pagará en oficinas' };

const interesados = ref([]);
const categorias = ref([]);
const cargando = ref(true);
const errorCarga = ref('');
const enVivo = ref(false);
const feria = ref(null);
const subiendoQr = ref(false);
const masFiltros = ref(false);
const detalleId = ref(null);
const cambiando = ref(null);
const quedanVisibles = ref(new Set());
const aprobando = ref(null);

function abrirAprobacion(i) {
  detalleId.value = null;
  aprobando.value = i;
}

function alAprobar(dto) {
  if (!dto) return;
  quedanVisibles.value = new Set(quedanVisibles.value).add(dto.id);
  aplicarEnVivo(dto, { resaltar: false });
}
const recienLlegados = ref(new Set());

const filtros = reactive({ texto: '', categoriaId: '', desde: '', hasta: '', estado: 'NUEVO', orden: 'recientes' });

const estadoDe = (i) => i.estado || 'NUEVO';
const solicitudes = useSolicitudesStore();
watch(interesados, (lista) => { solicitudes.nuevosFeria = lista.filter((i) => estadoDe(i) === 'NUEVO').length; }, { deep: true });
const detalle = computed(() => interesados.value.find((i) => i.id === detalleId.value) || null);
const faltaQr = computed(() => feria.value && feria.value.recibeInteresados && !feria.value.pago?.qrUrl);

async function cargar({ silencioso = false } = {}) {
  if (!silencioso) cargando.value = true;
  errorCarga.value = '';
  try {
    const [ri, rc, rf] = await Promise.all([
      apiFetch('/api/app/interesados-stand'),
      apiFetch('/api/app/interesados-stand/categorias'),
      fetch(urlApi('/api/publico/feria')).catch(() => null),
    ]);
    const [di, dc] = await Promise.all([ri.json().catch(() => null), rc.json().catch(() => null)]);
    if (!ri.ok || !Array.isArray(di)) throw new Error(di?.mensaje || `No se pudo cargar la lista (error ${ri.status}).`);
    interesados.value = di;
    categorias.value = Array.isArray(dc) ? dc : [];
    feria.value = rf?.ok ? (await rf.json().catch(() => null))?.edicion ?? null : null;
  } catch (e) {
    errorCarga.value = e.message;
  } finally {
    cargando.value = false;
  }
}

function normalizar(s) {
  return (s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

function fecha(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-BO', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

function hace(iso) {
  if (!iso) return '';
  const min = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (min < 1) return 'ahora';
  if (min < 60) return `hace ${min} min`;
  if (min < 1440) return `hace ${Math.round(min / 60)} h`;
  return `hace ${Math.round(min / 1440)} d`;
}

const cuentaEstado = computed(() => {
  const n = { NUEVO: 0, ATENDIDO: 0, DESCARTADO: 0 };
  interesados.value.forEach((i) => { n[estadoDe(i)] = (n[estadoDe(i)] || 0) + 1; });
  return n;
});

const filtrados = computed(() => {
  const q = normalizar(filtros.texto.trim());
  const lista = interesados.value.filter((i) => {
    if (filtros.estado && estadoDe(i) !== filtros.estado && !quedanVisibles.value.has(i.id)) return false;
    if (filtros.categoriaId && String(i.categoriaId) !== String(filtros.categoriaId)) return false;
    const dia = (i.fechaRegistro || '').slice(0, 10);
    if (filtros.desde && dia < filtros.desde) return false;
    if (filtros.hasta && dia > filtros.hasta) return false;
    return !q || [i.nombreCompleto, i.empresa, i.rubro, i.celular, i.puestoCodigo].some((c) => normalizar(String(c ?? '')).includes(q));
  });
  const colator = new Intl.Collator('es', { sensitivity: 'base', numeric: true });
  const orden = {
    recientes: (a, b) => (b.fechaRegistro || '').localeCompare(a.fechaRegistro || ''),
    antiguos: (a, b) => (a.fechaRegistro || '').localeCompare(b.fechaRegistro || ''),
    nombre: (a, b) => colator.compare(a.nombreCompleto || '', b.nombreCompleto || ''),
    categoria: (a, b) => colator.compare(a.categoriaNombre || '', b.categoriaNombre || ''),
  }[filtros.orden];
  return lista.sort(orden);
});

const hayFiltrosExtra = computed(() => Boolean(filtros.categoriaId || filtros.desde || filtros.hasta || filtros.orden !== 'recientes'));

function limpiarFiltros() {
  Object.assign(filtros, { texto: '', categoriaId: '', desde: '', hasta: '', orden: 'recientes' });
}

watch(() => filtros.estado, () => { quedanVisibles.value = new Set(); });

function whatsapp(i) {
  const lugar = i.puestoCodigo ? `el espacio ${i.puestoCodigo} (${i.categoriaNombre})` : `un espacio de ${i.categoriaNombre}`;
  const texto = estadoDe(i) === 'ATENDIDO'
    ? `Hola ${i.nombreCompleto}, tu solicitud para ${lugar} fue aprobada. ¡Te esperamos!`
    : estadoDe(i) === 'DESCARTADO'
      ? `Hola ${i.nombreCompleto}, te escribimos sobre tu solicitud para ${lugar}.`
      : `Hola ${i.nombreCompleto}, recibimos tu solicitud para ${lugar} y la estamos revisando.`;
  return enlaceWhatsApp(i.celular, texto);
}

const esImagen = (url) => /\.(png|jpe?g|webp)(\?|$)/i.test(url || '');

async function cambiarEstado(i, estado) {
  if (estado === 'DESCARTADO') {
    const si = await confirmar(i.puestoCodigo
      ? `La solicitud de ${i.nombreCompleto} se rechaza y el espacio ${i.puestoCodigo} vuelve a estar libre en la página pública.`
      : `La solicitud de ${i.nombreCompleto} se marcará como rechazada.`, { titulo: '¿Rechazar la solicitud?', aceptar: 'Sí, rechazar' });
    if (!si) return;
  }
  cambiando.value = i.id;
  try {
    const r = await apiFetch(`/api/app/interesados-stand/${i.id}/estado`, { method: 'PATCH', body: JSON.stringify({ estado }) });
    const d = await r.json().catch(() => null);
    if (!d?.ok) { aviso(d?.mensaje || 'No se pudo cambiar el estado.', 'error', 2500); return; }
    quedanVisibles.value = new Set(quedanVisibles.value).add(i.id);
    aplicarEnVivo(d.interesado, { resaltar: false });
    toast(d.mensaje, 'ok', 2200);
  } finally {
    cambiando.value = null;
  }
}

async function subirQr(e) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (!archivo || !feria.value) return;
  subiendoQr.value = true;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/ediciones/${feria.value.id}/qr-pago`, { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (r.status === 403) { toast('No tienes permiso para cambiar los datos de pago. Pídeselo a un administrador.', 'error', 4000); return; }
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir el QR', 'error'); return; }
    toast('QR de pago listo: ya se muestra en el formulario público', 'ok', 2500);
    await cargar({ silencioso: true });
  } finally {
    subiendoQr.value = false;
  }
}

const edicion = reactive({ abierto: false, id: null, nombreCompleto: '', celular: '', empresa: '',
  rubro: '', categoriaId: null, categoriaNombre: '', fechaRegistro: '' });
const guardando = ref(false);
const categoriaDelRegistroExiste = computed(() => categorias.value.some((c) => c.id === edicion.categoriaId));

function abrirEditar(i) {
  Object.assign(edicion, {
    abierto: true, id: i.id, nombreCompleto: i.nombreCompleto || '', celular: i.celular || '',
    empresa: i.empresa || '', rubro: i.rubro || '', categoriaId: i.categoriaId,
    categoriaNombre: i.categoriaNombre || '', fechaRegistro: i.fechaRegistro,
  });
}

function cerrarEdicion() {
  if (!guardando.value) edicion.abierto = false;
}

async function guardar() {
  if (guardando.value) return;
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/interesados-stand/${edicion.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        nombreCompleto: edicion.nombreCompleto, celular: edicion.celular, empresa: edicion.empresa,
        rubro: edicion.rubro, categoriaId: edicion.categoriaId,
      }),
    });
    const d = await r.json().catch(() => null);
    if (!d?.ok) {
      aviso(d?.mensaje || `No se pudo guardar el cambio (error ${r.status}).`, 'error', 2000, 'No se pudo guardar');
      return;
    }
    aplicarEnVivo(d.interesado, { resaltar: false });
    edicion.abierto = false;
    toast('Datos de la solicitud actualizados', 'ok', 1800);
  } catch (e) {
    aviso(e.message || 'No se pudo guardar el cambio.', 'error', 2000, 'No se pudo guardar');
  } finally {
    guardando.value = false;
  }
}

function aplicarEnVivo(i, { resaltar = true } = {}) {
  if (!i?.id) return;
  const idx = interesados.value.findIndex((x) => x.id === i.id);
  if (idx >= 0) {
    interesados.value.splice(idx, 1, i);
    return;
  }
  interesados.value.unshift(i);
  if (!resaltar) return;
  recienLlegados.value = new Set(recienLlegados.value).add(i.id);
  setTimeout(() => {
    const s = new Set(recienLlegados.value);
    s.delete(i.id);
    recienLlegados.value = s;
  }, 6000);
}

let dejarDeEscuchar = null;
let primeraConexion = true;
onMounted(() => {
  cargar();
  dejarDeEscuchar = escucharTopic('/topic/interesados', {
    onMensaje: (i) => aplicarEnVivo(i),
    onConectado: () => {
      enVivo.value = true;
      if (!primeraConexion) cargar({ silencioso: true });
      primeraConexion = false;
    },
    onCerrado: () => { enVivo.value = false; },
  });
});
onUnmounted(() => dejarDeEscuchar?.());
</script>

<template>
  <div class="cabecera-vista">
    <p class="muted nota">
      Personas que pidieron un espacio desde la página pública. Revisa sus datos y su comprobante, y <b>apruébalas</b>
      (se registra la venta y salen su recibo y credenciales para enviar por WhatsApp) o <b>recházalas</b> (el espacio vuelve a estar libre).
    </p>
    <span class="en-vivo" :class="{ activo: enVivo }" :title="enVivo ? 'Recibiendo registros en tiempo real' : 'Reconectando…'">
      <span class="en-vivo-punto" aria-hidden="true"></span>{{ enVivo ? 'En vivo' : 'Reconectando…' }}
    </span>
  </div>

  <div v-if="faltaQr" class="card aviso-qr">
    <span class="aviso-icono">📱</span>
    <div>
      <strong>Tu actividad aún no tiene QR de pago</strong>
      <p class="muted">Súbelo para que los interesados lo vean en el formulario, paguen escaneándolo y adjunten su comprobante.</p>
    </div>
    <label class="btn btn-primario btn-sm" :class="{ deshabilitado: subiendoQr }">
      {{ subiendoQr ? 'Subiendo…' : 'Subir QR de pago' }}
      <input type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="subiendoQr" @change="subirQr" />
    </label>
  </div>

  <div class="card barra-filtros">
    <div class="estados" role="tablist" aria-label="Estado de las solicitudes">
      <button v-for="(e, clave) in ESTADOS" :key="clave" type="button" role="tab" class="estado-tab" :class="[`t-${clave}`, { on: filtros.estado === clave }]"
              :aria-selected="filtros.estado === clave" @click="filtros.estado = clave">
        {{ e.plural }} <span class="cuenta">{{ cuentaEstado[clave] }}</span>
      </button>
      <button type="button" role="tab" class="estado-tab" :class="{ on: !filtros.estado }" :aria-selected="!filtros.estado" @click="filtros.estado = ''">
        Todas <span class="cuenta">{{ interesados.length }}</span>
      </button>
    </div>
    <div class="fila-busqueda">
      <input v-model="filtros.texto" type="search" class="control buscar" placeholder="Buscar por persona, negocio, rubro, celular o espacio…" />
      <button type="button" class="btn btn-sm" :class="{ activo: masFiltros || hayFiltrosExtra }" @click="masFiltros = !masFiltros">
        ⚙ Filtros{{ hayFiltrosExtra ? ' •' : '' }}
      </button>
    </div>
    <div v-if="masFiltros" class="mas-filtros">
      <label class="campo"><span>Categoría</span>
        <select v-model="filtros.categoriaId" class="control">
          <option value="">Todas</option>
          <option v-for="c in categorias" :key="c.id" :value="String(c.id)">{{ c.nombre }}</option>
        </select>
      </label>
      <label class="campo"><span>Desde</span><input v-model="filtros.desde" type="date" class="control" :max="filtros.hasta || undefined" /></label>
      <label class="campo"><span>Hasta</span><input v-model="filtros.hasta" type="date" class="control" :min="filtros.desde || undefined" /></label>
      <label class="campo"><span>Ordenar</span>
        <select v-model="filtros.orden" class="control">
          <option value="recientes">Más recientes primero</option>
          <option value="antiguos">Más antiguas primero</option>
          <option value="nombre">Por nombre (A-Z)</option>
          <option value="categoria">Por categoría</option>
        </select>
      </label>
      <button v-if="hayFiltrosExtra || filtros.texto" type="button" class="btn btn-sm btn-fantasma" @click="limpiarFiltros">✕ Limpiar</button>
    </div>
  </div>

  <div v-if="cargando" class="card vacio">Cargando…</div>
  <div v-else-if="errorCarga" class="card vacio">
    <p>{{ errorCarga }}</p>
    <button class="btn btn-sm" @click="cargar()">Reintentar</button>
  </div>
  <div v-else-if="!filtrados.length" class="card vacio">
    <template v-if="!interesados.length">Todavía nadie pidió un espacio desde la página pública.</template>
    <template v-else-if="filtros.estado === 'NUEVO' && !filtros.texto && !hayFiltrosExtra">🎉 No hay solicitudes por revisar.</template>
    <template v-else>Ninguna solicitud coincide con la búsqueda.</template>
  </div>
  <ul v-else class="lista">
    <li v-for="i in filtrados" :key="i.id" class="fila" :class="[`e-${estadoDe(i)}`, { nueva: recienLlegados.has(i.id) }]"
        :style="{ '--cat': i.categoriaColor || 'var(--muted)' }">
      <button type="button" class="fila-cuerpo" title="Ver todos los datos" @click="detalleId = i.id">
        <span class="quien">
          <strong>{{ i.nombreCompleto }}</strong>
          <span class="muted">{{ i.empresa }} · {{ i.rubro }}</span>
        </span>
        <span class="que">
          <span class="cat"><i></i>{{ i.categoriaNombre || 'Sin categoría' }}</span>
          <b v-if="i.puestoCodigo" class="espacio">Espacio {{ i.puestoCodigo }}</b>
          <span v-else class="muted">Sin espacio elegido</span>
        </span>
        <span class="pago">
          <span>{{ PAGO[i.modoPago] || 'Pago por coordinar' }}</span>
          <span class="muted">{{ hace(i.fechaRegistro) }}</span>
        </span>
      </button>
      <div class="fila-acciones">
        <template v-if="estadoDe(i) !== 'NUEVO'">
          <span class="sello" :class="`s-${estadoDe(i)}`">{{ ESTADOS[estadoDe(i)].icono }} {{ i.inscripcionId ? `Vendida #${i.inscripcionId}` : ESTADOS[estadoDe(i)].texto }}</span>
          <button v-if="estadoDe(i) === 'ATENDIDO'" class="btn btn-sm" @click="abrirAprobacion(i)">{{ i.inscripcionId ? '📲 Entregar' : 'Registrar venta' }}</button>
        </template>
        <template v-else>
          <span v-if="recienLlegados.has(i.id)" class="sello s-nueva">Nueva</span>
          <button class="btn btn-sm btn-aprobar" :disabled="cambiando === i.id" @click="abrirAprobacion(i)">✓ Aprobar</button>
          <button class="btn btn-sm btn-fantasma" :disabled="cambiando === i.id" @click="cambiarEstado(i, 'DESCARTADO')">Rechazar</button>
        </template>
        <button class="btn btn-sm btn-fantasma" @click="detalleId = i.id">Ver</button>
      </div>
    </li>
  </ul>

  <UiModal v-if="detalle" :titulo="detalle.nombreCompleto" ancho="560px" @cerrar="detalleId = null">
    <div class="det-cab">
      <span class="sello" :class="`s-${estadoDe(detalle)}`">{{ ESTADOS[estadoDe(detalle)].icono }} {{ ESTADOS[estadoDe(detalle)].texto }}</span>
      <span class="muted chico">Recibida el {{ fecha(detalle.fechaRegistro) }}</span>
    </div>
    <dl class="datos">
      <div><dt>Negocio</dt><dd>{{ detalle.empresa }}</dd></div>
      <div><dt>Rubro</dt><dd>{{ detalle.rubro }}</dd></div>
      <div><dt>Celular</dt><dd><a :href="`tel:${detalle.celular}`">{{ detalle.celular }}</a></dd></div>
      <div><dt>Categoría</dt><dd><span class="cat" :style="{ '--cat': detalle.categoriaColor || 'var(--muted)' }"><i></i>{{ detalle.categoriaNombre }}</span></dd></div>
      <div><dt>Espacio</dt><dd>{{ detalle.puestoCodigo ? `Espacio ${detalle.puestoCodigo}` : 'Sin elegir' }}</dd></div>
      <div><dt>Pago</dt><dd>{{ PAGO[detalle.modoPago] || 'Por coordinar' }}</dd></div>
      <div v-if="detalle.ci"><dt>C.I.</dt><dd>{{ detalle.ci }}</dd></div>
      <div v-if="detalle.inscripcionId"><dt>Venta</dt><dd>#{{ detalle.inscripcionId }}</dd></div>
    </dl>
    <div v-if="detalle.comprobanteUrl" class="comprobante">
      <a :href="urlApi(detalle.comprobanteUrl)" target="_blank" rel="noopener" title="Abrir en tamaño completo">
        <img v-if="esImagen(detalle.comprobanteUrl)" :src="urlApi(detalle.comprobanteUrl)" alt="Comprobante de pago" />
        <span v-else class="pdf">📄 Ver comprobante (PDF)</span>
      </a>
    </div>
    <template #pie>
      <a class="btn btn-whatsapp" :href="whatsapp(detalle)" target="_blank" rel="noopener">WhatsApp</a>
      <button class="btn btn-fantasma" @click="abrirEditar(detalle)">✏️ Editar datos</button>
      <span class="crecer"></span>
      <template v-if="estadoDe(detalle) === 'NUEVO'">
        <button class="btn" :disabled="cambiando === detalle.id" @click="cambiarEstado(detalle, 'DESCARTADO')">Rechazar</button>
        <button class="btn btn-aprobar" :disabled="cambiando === detalle.id" @click="abrirAprobacion(detalle)">✓ Aprobar</button>
      </template>
      <button v-else-if="detalle.inscripcionId" class="btn btn-primario" @click="abrirAprobacion(detalle)">📲 Recibo y credenciales</button>
      <template v-else>
        <button v-if="estadoDe(detalle) === 'ATENDIDO'" class="btn btn-primario" @click="abrirAprobacion(detalle)">Registrar venta</button>
        <button class="btn" :disabled="cambiando === detalle.id" @click="cambiarEstado(detalle, 'NUEVO')">Volver a revisión</button>
      </template>
    </template>
  </UiModal>

  <AprobarSolicitud v-if="aprobando" :solicitud="aprobando" :actividad="feria?.nombre || ''"
                    @aprobada="alAprobar" @cerrar="aprobando = null" />

  <UiModal v-if="edicion.abierto" titulo="Editar datos de la solicitud" ancho="560px" @cerrar="cerrarEdicion">
    <div class="grid2">
      <label class="campo campo-ancho"><span>Nombre completo *</span>
        <input v-model="edicion.nombreCompleto" v-filtro="'nombre'" autocapitalize="characters" class="control" maxlength="200" autocomplete="off" />
      </label>
      <label class="campo"><span>Número de celular *</span>
        <input v-model="edicion.celular" v-filtro="'telefono'" type="tel" inputmode="tel" class="control" maxlength="16" autocomplete="off" />
      </label>
      <label class="campo"><span>Categoría *</span>
        <select v-model="edicion.categoriaId" class="control">
          <option v-if="!categoriaDelRegistroExiste" :value="edicion.categoriaId" disabled>
            {{ edicion.categoriaNombre || 'Sin categoría' }} (ya no existe)
          </option>
          <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
        </select>
      </label>
      <label class="campo campo-ancho"><span>Negocio o emprendimiento *</span>
        <input v-model="edicion.empresa" v-filtro="'letras'" autocapitalize="characters" class="control" maxlength="200" autocomplete="off" />
      </label>
      <label class="campo campo-ancho"><span>Rubro *</span>
        <input v-model="edicion.rubro" v-filtro="'letras'" autocapitalize="characters" class="control" maxlength="200" autocomplete="off" />
      </label>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" :disabled="guardando" @click="cerrarEdicion">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardar">{{ guardando ? 'Guardando…' : 'Guardar cambios' }}</button>
    </template>
  </UiModal>
</template>

<style scoped>
.cabecera-vista { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.8rem; margin-bottom: 0.8rem; }
.nota { margin: 0; font-size: 0.86rem; }
.en-vivo { display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.8rem; font-weight: 600; color: var(--muted); white-space: nowrap; }
.en-vivo-punto { width: 0.55rem; height: 0.55rem; border-radius: 50%; background: var(--muted); }
.en-vivo.activo { color: var(--ok); }
.en-vivo.activo .en-vivo-punto { background: var(--ok); animation: latir 1.6s ease-in-out infinite; }
@keyframes latir { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }

.aviso-qr {
  display: flex; align-items: center; gap: 0.9rem; flex-wrap: wrap; margin-bottom: 0.8rem; padding: 0.8rem 1rem;
  border: 1px solid color-mix(in srgb, var(--tramite) 50%, transparent); background: color-mix(in srgb, var(--tramite) 9%, var(--panel));
}
.aviso-qr > div { flex: 1; min-width: 220px; }
.aviso-qr p { margin: 0.15rem 0 0; font-size: 0.85rem; }
.aviso-icono { font-size: 1.6rem; }

.barra-filtros { margin-bottom: 0.8rem; padding: 0.8rem 1rem; display: flex; flex-direction: column; gap: 0.7rem; }
.estados { display: flex; gap: 0.3rem; flex-wrap: wrap; }
.estado-tab {
  display: inline-flex; align-items: center; gap: 0.45rem; padding: 0.45rem 0.85rem; border-radius: 999px; cursor: pointer;
  border: 1px solid var(--border); background: transparent; color: var(--text); font: inherit; font-weight: 600; font-size: 0.88rem;
}
.estado-tab .cuenta { min-width: 1.4rem; padding: 0 0.35rem; border-radius: 999px; background: var(--bg); font-size: 0.75rem; text-align: center; }
.estado-tab.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.estado-tab.on .cuenta { background: rgba(255, 255, 255, 0.25); }
.estado-tab.t-NUEVO:not(.on) .cuenta { background: color-mix(in srgb, var(--tramite) 22%, transparent); color: var(--tramite); }
.fila-busqueda { display: flex; gap: 0.5rem; }
.buscar { flex: 1; min-width: 0; }
.btn.activo { border-color: var(--acento); color: var(--acento); }
.mas-filtros { display: flex; flex-wrap: wrap; gap: 0.6rem; align-items: flex-end; }
.mas-filtros .campo { min-width: 150px; flex: 1; }

.vacio { padding: 2rem; text-align: center; color: var(--muted); }
.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.fila {
  display: flex; align-items: center; gap: 0.6rem; padding: 0.55rem 0.7rem 0.55rem 0.9rem; border-radius: var(--radio);
  background: var(--panel); border: 1px solid var(--border); border-left: 4px solid var(--tramite);
}
.fila.e-ATENDIDO { border-left-color: var(--ok); }
.fila.e-DESCARTADO { border-left-color: var(--border); opacity: 0.75; }
.fila.nueva { animation: resaltar 6s ease-out; }
@keyframes resaltar { 0%, 40% { background: color-mix(in srgb, var(--ok) 12%, var(--panel)); } 100% { background: var(--panel); } }
.fila-cuerpo {
  flex: 1; min-width: 0; display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) minmax(0, 1fr); gap: 0.8rem; align-items: center;
  border: 0; background: none; color: inherit; font: inherit; text-align: left; cursor: pointer; padding: 0.2rem 0;
}
.quien, .que, .pago { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; font-size: 0.86rem; }
.quien strong { font-size: 0.95rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.quien .muted, .pago .muted { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.8rem; }
.cat { display: inline-flex; align-items: center; gap: 0.35rem; font-weight: 600; }
.cat i { width: 0.6rem; height: 0.6rem; border-radius: 50%; background: var(--cat); flex: none; }
.espacio { font-size: 0.85rem; }
.fila-acciones { display: flex; align-items: center; gap: 0.35rem; flex-wrap: wrap; justify-content: flex-end; }
.sello { display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.2rem 0.6rem; border-radius: 999px; font-size: 0.78rem; font-weight: 700; white-space: nowrap; }
.s-NUEVO { background: color-mix(in srgb, var(--tramite) 18%, transparent); color: var(--tramite); }
.s-ATENDIDO { background: color-mix(in srgb, var(--ok) 18%, transparent); color: var(--ok); }
.s-DESCARTADO { background: var(--bg); color: var(--muted); }
.s-nueva { background: color-mix(in srgb, var(--ok) 18%, transparent); color: var(--ok); }
.btn-aprobar { background: var(--ok); border-color: var(--ok); color: #fff; }
.btn-aprobar:hover:not(:disabled) { filter: brightness(0.95); }
.btn-whatsapp { background: #1fa855; border-color: #1fa855; color: #fff; }

.det-cab { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; flex-wrap: wrap; }
.chico { font-size: 0.8rem; }
.datos { margin: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.7rem 1rem; }
.datos dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
.datos dd { margin: 0.1rem 0 0; font-weight: 600; overflow-wrap: anywhere; }
.comprobante a { display: block; }
.comprobante img { display: block; max-width: 100%; max-height: 42vh; margin: 0 auto; border-radius: var(--radio-sm); border: 1px solid var(--border); background: #fff; }
.pdf { display: block; padding: 0.8rem; text-align: center; border: 1px dashed var(--border); border-radius: var(--radio-sm); font-weight: 600; }
.crecer { flex: 1; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem; }
.campo-ancho { grid-column: 1 / -1; }

@media (max-width: 760px) {
  .fila { flex-direction: column; align-items: stretch; }
  .fila-cuerpo { grid-template-columns: 1fr 1fr; }
  .quien { grid-column: 1 / -1; }
  .fila-acciones { justify-content: flex-start; }
}
@media (max-width: 520px) {
  .grid2, .datos { grid-template-columns: 1fr; }
  .cabecera-vista { flex-direction: column; }
}
@media (prefers-reduced-motion: reduce) {
  .en-vivo.activo .en-vivo-punto, .fila.nueva { animation: none; }
}
</style>
