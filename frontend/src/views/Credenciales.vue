<script setup>
import { ref, computed, onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';
import { apiFetch, jsonOError, MENSAJE_SIN_PERMISO } from '../api';
import { descargarPdf, descargarCredencialVirtual } from '../ui/descargas';
import { mostrarCarga, ocultarCarga } from '../ui/cargando';
import { toast } from '../ui/toast';
import { alerta } from '../ui/alerta';
import { asegurarCategorias, categorias } from '../ui/catalogoCategorias';
import UiModal from '../components/UiModal.vue';
import EditarDatosCredencial from '../components/EditarDatosCredencial.vue';

const auth = useAuthStore();
const soloMias = computed(() => !auth.veTodasLasCredenciales);

const soloVirtual = computed(() => !auth.veTodasLasCredenciales);
const ID_VIRTUAL = 'CREDENCIAL_VIRTUAL';

const virtualesListas = (c) => grupoDe(c).filter((x) => x.listo?.[ID_VIRTUAL]);

function loQueFalta(lista) {
  const faltas = new Set();
  for (const c of lista) for (const f of c.faltantes?.[ID_VIRTUAL] || []) faltas.add(f);
  if (!faltas.size) return 'falta algo';
  return [...faltas].join(' y ');
}

async function bajarVirtual(lista) {
  if (generando.value) return;
  const aptas = lista.filter((x) => x.listo?.[ID_VIRTUAL]);
  if (!aptas.length) {
    alerta(`Todavía no se puede emitir: ${loQueFalta(lista)}.`, 'advertencia');
    return;
  }
  generando.value = true;
  mostrarCarga(aptas.length === 1 ? 'Generando la credencial…'
                                  : `Generando ${aptas.length} credenciales…`);
  try {
    for (const c of aptas) {
      await descargarCredencialVirtual(c.responsableId, c.nombre, c.entidad);
    }
    await cargar();
  } finally {
    ocultarCarga();
    generando.value = false;
  }
}

const reenvio = ref(null);
const incluirRecibo = ref(false);

function reenviarWhatsApp(lista) {
  if (generando.value) return;
  const aptas = lista.filter((x) => x.listo?.[ID_VIRTUAL]);
  if (!aptas.length) {
    alerta(`Todavía no se puede reenviar: ${loQueFalta(lista)}.`, 'advertencia');
    return;
  }
  incluirRecibo.value = false;
  reenvio.value = aptas;
}

async function confirmarReenvio() {
  if (generando.value || !reenvio.value) return;
  const aptas = reenvio.value;
  generando.value = true;
  mostrarCarga(aptas.length === 1 ? 'Reenviando por WhatsApp…'
                                  : `Reenviando ${aptas.length} credenciales por WhatsApp…`);
  try {
    const r = await apiFetch('/api/app/credenciales/whatsapp', {
      method: 'POST',
      body: JSON.stringify({
        inscripcionId: aptas[0].inscripcionId,
        responsables: aptas.map((c) => c.responsableId),
        incluirRecibo: incluirRecibo.value,
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) throw new Error(d.mensaje || 'No se pudo reenviar por WhatsApp');
    toast(d.mensaje || 'Reenviado por WhatsApp', 'ok');
    reenvio.value = null;
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    ocultarCarga();
    generando.value = false;
  }
}

const cargando = ref(true);
const credenciales = ref([]);
const busqueda = ref('');
const filtro = ref('listas');
const categoriaFiltro = ref(null);
const seleccion = ref(new Set());
const generando = ref(false);
const rotas = ref(new Set());
const subiendo = ref(null);
const historial = ref(null);
const editando = ref(null);

async function datosGuardados() {
  editando.value = null;
  await cargar();
}

const plantilla = ref('CON_ETIQUETAS');
const anchoCm = ref(10);

const plantillas = ref([]);
const plantillasRotas = ref(false);
const errorCarga = ref('');

const plantillaActual = computed(() =>
  plantillas.value.find((p) => p.id === plantilla.value) || null);

const pideFoto = computed(() => plantillaActual.value?.requiereFoto ?? true);

const altoCm = computed(() =>
  anchoCm.value * (plantillaActual.value?.proporcion || 1534 / 1182));

const nombreDePlantilla = (id) =>
  plantillas.value.find((p) => p.id === id)?.etiqueta || id;

async function cargarPlantillas() {
  try {
    const r = await apiFetch('/api/app/credenciales/plantillas');
    plantillas.value = await jsonOError(r, 'No se pudo cargar la lista de plantillas');
    if (plantillas.value.length && !plantillas.value.some((p) => p.id === plantilla.value)) {
      plantilla.value = plantillas.value[0].id;
    }
  } catch (e) {
    plantillasRotas.value = true;
    if (e.message !== MENSAJE_SIN_PERMISO) toast(e.message, 'error');
  }
}

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/credenciales');
    credenciales.value = await jsonOError(r, 'No se pudo cargar la lista');
    errorCarga.value = '';
  } catch (e) {
    errorCarga.value = e.message;
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

const esListo = (c) => Boolean(c.listo?.[plantilla.value]);
const faltaDe = (c) => c.faltantes?.[plantilla.value] || [];

const listas = computed(() => credenciales.value.filter(esListo));
const pendientes = computed(() => credenciales.value.filter((c) => !esListo(c)));

const visibles = computed(() => {
  const q = busqueda.value.trim().toLowerCase();
  const base = filtro.value === 'listas' ? listas.value
    : filtro.value === 'pendientes' ? pendientes.value
    : credenciales.value;
  let filtradas = base;
  if (categoriaFiltro.value) {
    filtradas = filtradas.filter((c) => c.categoriaId === categoriaFiltro.value);
  }
  if (!q) return filtradas;
  return filtradas.filter((c) =>
    `${c.nombre} ${c.entidad} ${c.ci} ${c.categoria} ${c.casetas}`.toLowerCase().includes(q));
});

const porEntidad = computed(() => {
  const m = new Map();
  for (const c of visibles.value) {
    if (!m.has(c.inscripcionId)) {
      m.set(c.inscripcionId, {
        inscripcionId: c.inscripcionId,
        entidad: c.entidad,
        rubro: c.rubro,
        categoria: c.categoria,
        casetas: c.casetas,
        responsables: [],
      });
    }
    m.get(c.inscripcionId).responsables.push(c);
  }
  for (const g of m.values()) {
    g.responsables.sort((a, b) => (b.esTitular ? 1 : 0) - (a.esTitular ? 1 : 0));
    g.listasVirtual = g.responsables.filter((x) => x.listo?.[ID_VIRTUAL]).length;
    g.seleccionadasVirtual = g.responsables.filter((c) =>
      seleccion.value.has(c.responsableId) && c.listo?.[ID_VIRTUAL]);
  }
  return [...m.values()];
});

const seleccionadasAptas = computed(() =>
  credenciales.value.filter((c) => seleccion.value.has(c.responsableId) && esListo(c)));

function alternar(c) {
  const s = new Set(seleccion.value);
  s.has(c.responsableId) ? s.delete(c.responsableId) : s.add(c.responsableId);
  seleccion.value = s;
}

function marcarVisibles(marcar) {
  const s = new Set(seleccion.value);
  for (const c of visibles.value) {
    if (!esListo(c)) continue;
    marcar ? s.add(c.responsableId) : s.delete(c.responsableId);
  }
  seleccion.value = s;
}

async function imprimir(ids, etiqueta, forzar = false, plantillaId = plantilla.value) {
  if (generando.value) return;
  generando.value = true;
  try {
    const nombre = ids.length === 1 ? 'credencial.pdf' : `credenciales-${etiqueta}.pdf`;
    const r = await descargarPdf('/api/app/credenciales/pdf', nombre, {
      method: 'POST',
      body: JSON.stringify({
        responsables: ids,
        plantilla: plantillaId,
        anchoCm: Number(anchoCm.value),
        forzar,
      }),
    });
    toast(r?.destino === 'telefono'
      ? 'Guardado en Documentos del teléfono'
      : 'Credenciales generadas', 'ok');
    await cargar();
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    generando.value = false;
  }
}

async function imprimirIgual(c) {
  const falta = faltaDe(c).join(' y ');
  if (!confirm(
    `A esta credencial le falta ${falta}.\n\n`
    + 'Se puede imprimir igual, pero quedará registrado que la imprimiste incompleta '
    + 'y la inscripción seguirá pendiente de completar.\n\n¿Imprimir de todas formas?')) return;
  await imprimir([c.responsableId], 1, true);
}

async function subir(c, tipo, evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo || subiendo.value) return;

  subiendo.value = c.responsableId;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const ruta = tipo === 'comprobante'
      ? `/api/app/inscripciones/${c.inscripcionId}/comprobante`
      : `/api/app/inscripciones/${c.inscripcionId}/responsables/${c.responsableId}/foto`;
    const r = await apiFetch(ruta, { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) throw new Error(d.mensaje || 'No se pudo subir');
    toast(tipo === 'comprobante' ? 'Comprobante adjuntado' : 'Foto adjuntada', 'ok');
    await cargar();
  } catch (e) {
    alerta(e.message, 'error', 0);
  } finally {
    subiendo.value = null;
  }
}

async function verHistorial(c) {
  try {
    const r = await apiFetch(`/api/app/credenciales/${c.responsableId}/impresiones`);
    historial.value = { credencial: c, filas: r.ok ? await r.json() : [] };
  } catch (e) {
    toast(e.message, 'error');
  }
}

function grupoDe(c) {
  return credenciales.value.filter((x) => x.inscripcionId === c.inscripcionId);
}

function imprimirInscripcion(c, plantillaId = plantilla.value) {
  const aptas = grupoDe(c).filter((x) => x.listo?.[plantillaId]);
  if (!aptas.length) {
    alerta(`Ninguna credencial de ${c.entidad} está lista para esta plantilla.`, 'advertencia');
    return;
  }
  imprimir(aptas.map((x) => x.responsableId), `inscripcion-${aptas.length}`, false, plantillaId);
}

function imprimirTodasListas() {
  if (!listas.value.length) {
    alerta('Ninguna credencial está lista todavía para esta plantilla. Hace falta el '
      + 'comprobante de pago' + (pideFoto.value ? ' y la foto del responsable.' : '.'),
      'advertencia');
    return;
  }
  imprimir([], `todas-${listas.value.length}`);
}

onMounted(() => {
  cargarPlantillas();
  asegurarCategorias();
  cargar();
});
</script>

<template>
  <div class="credenciales">
    <header class="resumen">
      <button class="tarjeta" :class="{ activa: filtro === 'listas' }" @click="filtro = 'listas'">
        <strong>{{ listas.length }}</strong>
        <span>listas para imprimir</span>
      </button>
      <button class="tarjeta" :class="{ activa: filtro === 'pendientes' }" @click="filtro = 'pendientes'">
        <strong class="pend">{{ pendientes.length }}</strong>
        <span>les falta algo</span>
      </button>
      <button class="tarjeta" :class="{ activa: filtro === 'todas' }" @click="filtro = 'todas'">
        <strong class="muted">{{ credenciales.length }}</strong>
        <span>en total</span>
      </button>
    </header>

    <p v-if="soloMias" class="alcance">
      Aquí salen las credenciales de <strong>tus ventas</strong>. Puedes adjuntar el comprobante
      y las fotos de tus responsables, y descargar su <strong>credencial virtual</strong>: una
      imagen para el teléfono, con el QR que se escanea en la puerta. Se la mandas al expositor
      y listo, no hay que imprimir nada.
    </p>

    <section v-if="!soloVirtual" class="ajustes card">
      <div class="grupo">
        <span class="rotulo">Plantilla</span>
        <div class="opciones">
          <button v-for="p in plantillas" :key="p.id" class="opcion"
                  :class="{ elegida: plantilla === p.id }" @click="plantilla = p.id">
            <strong>{{ p.etiqueta }}</strong>
            <small>{{ p.detalle }}</small>
          </button>
          <p v-if="!plantillas.length" class="nota">
            {{ plantillasRotas ? 'No se pudieron cargar las plantillas.' : 'Cargando plantillas…' }}
          </p>
        </div>
      </div>

      <div class="grupo">
        <span class="rotulo">Tamaño impreso</span>
        <div class="medida">
          <input type="range" min="6" max="16" step="0.5" v-model.number="anchoCm" />
          <span class="valor">{{ anchoCm }} × {{ altoCm.toFixed(1) }} cm</span>
        </div>
        <p class="nota">Una credencial por hoja carta, centrada.</p>
      </div>

      <div class="grupo previa" aria-hidden="true">
        <span class="rotulo">En la hoja</span>
        <div class="hoja">
          <div class="cred" :style="{
            width: (anchoCm / 21.6 * 100) + '%',
            height: (altoCm / 27.9 * 100) + '%',
          }"></div>
        </div>
      </div>
    </section>

    <div class="barra">
      <input v-model="busqueda" class="control" placeholder="Buscar por nombre, entidad, C.I. o caseta…" />
      <select v-model="categoriaFiltro" class="control" style="min-width: 180px;" :disabled="!categorias?.length">
        <option :value="null">Todas las categorías</option>
        <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
        <option v-if="!categorias?.length" disabled>Cargando…</option>
      </select>
      <button class="btn btn-fantasma" @click="categoriaFiltro = null" :disabled="!categoriaFiltro">Quitar filtro</button>
      <button class="btn btn-fantasma" @click="marcarVisibles(true)">Marcar visibles</button>
      <button class="btn btn-fantasma" :disabled="!seleccion.size" @click="seleccion = new Set()">
        Quitar marcas
      </button>
      <span class="crecer"></span>
      <button class="btn" :disabled="generando || !seleccionadasAptas.length"
              @click="imprimir(seleccionadasAptas.map((c) => c.responsableId), seleccionadasAptas.length)">
        Imprimir {{ seleccionadasAptas.length || '' }} marcada{{ seleccionadasAptas.length === 1 ? '' : 's' }}
      </button>
      <button v-if="soloVirtual" class="btn btn-primario" :disabled="generando || !listas.length"
              @click="bajarVirtual(listas)">
        {{ generando ? 'Generando…'
           : !listas.length ? 'Ninguna lista todavía'
           : `📱 Descargar ${listas.length} credencial${listas.length === 1 ? '' : 'es'}` }}
      </button>
      <button v-else class="btn btn-primario" :disabled="generando || !listas.length"
              @click="imprimirTodasListas">
        {{ generando ? 'Generando…'
           : !listas.length ? 'Ninguna lista todavía'
           : listas.length === 1 ? 'Imprimir la única lista'
           : `Imprimir las ${listas.length} listas` }}
      </button>
    </div>

    <div v-if="cargando" class="vacio">Cargando credenciales…</div>
    <div v-else-if="errorCarga" class="vacio" role="alert">{{ errorCarga }}</div>
    <div v-else-if="!visibles.length" class="vacio">
      <template v-if="busqueda.trim()">No hay resultados para esa búsqueda.</template>
      <template v-else-if="filtro === 'listas'">
        Todavía no hay ninguna credencial lista para esta plantilla. Hace falta el comprobante
        de pago<template v-if="pideFoto"> y la foto del responsable</template>.
      </template>
      <template v-else-if="filtro === 'pendientes'">
        No falta nada: todas las credenciales están listas para esta plantilla.
      </template>
      <template v-else-if="soloMias">
        Todavía no has registrado ninguna venta en esta edición.
      </template>
      <template v-else>Todavía no hay inscripciones en esta edición.</template>
    </div>

    <ul v-else class="entidades">
      <li v-for="g in porEntidad" :key="g.inscripcionId" class="entidad card">
        <header class="cab-ent">
          <div class="quien-ent">
            <strong class="nom-ent">{{ g.entidad }}</strong>
            <span class="muted">
              {{ g.categoria || 'sin categoría' }} ·
              caseta{{ (g.casetas || '').includes(',') ? 's' : '' }} {{ g.casetas || '—' }}
              <template v-if="g.rubro"> · {{ g.rubro }}</template>
            </span>
          </div>
          <div class="acc-ent">
            <span class="cuenta-ent">
              {{ g.responsables.length }} responsable{{ g.responsables.length === 1 ? '' : 's' }}
            </span>
            <button v-if="g.responsables.length > 1" class="btn btn-primario btn-sm"
                    :disabled="generando || !g.listasVirtual"
                    :title="g.listasVirtual
                      ? `Descargar las ${g.listasVirtual} credenciales de ${g.entidad}`
                      : 'Ninguna está lista todavía'"
                    @click="bajarVirtual(g.responsables)">
              📱 Las {{ g.listasVirtual }}
            </button>
            <button v-if="g.responsables.length > 1" class="btn btn-sm"
                    :disabled="generando || !g.seleccionadasVirtual.length"
                    :title="g.seleccionadasVirtual.length
                      ? `Reenviar por WhatsApp las ${g.seleccionadasVirtual.length} credenciales seleccionadas de ${g.entidad}`
                      : 'Marca las credenciales listas que quieras reenviar de esta empresa'"
                    @click="reenviarWhatsApp(g.seleccionadasVirtual)">
              Reenviar seleccionados
            </button>
          </div>
        </header>

        <ul class="lista">
          <li v-for="c in g.responsables" :key="c.responsableId" class="fila"
              :class="{ marcada: seleccion.has(c.responsableId), bloqueada: !esListo(c) }">
            <label class="marca">
              <input type="checkbox" :disabled="!esListo(c)"
                     :checked="seleccion.has(c.responsableId)" @change="alternar(c)" />
            </label>

            <img v-if="c.fotoUrl && !rotas.has(c.responsableId)" :src="c.fotoUrl" :alt="c.nombre"
                 class="foto" @error="rotas = new Set(rotas).add(c.responsableId)" />
            <div v-else class="foto sinfoto" aria-hidden="true">?</div>

            <div class="quien">
              <strong>{{ c.nombre }}</strong>
              <span v-if="c.esExtra" class="badge badge-extra"
                    :title="c.comprobanteExtraUrl
                      ? `Responsable adicional. Se cobraron ${c.montoExtra} Bs y tiene comprobante.`
                      : 'Responsable adicional: se le cobró, pero NO tiene comprobante adjunto.'">
                extra{{ c.montoExtra ? ` · ${c.montoExtra} Bs` : '' }}
              </span>
              <span class="muted">
                C.I. {{ c.ci || '—' }}
                <template v-if="c.esTitular"> · titular</template>
                <template v-if="c.cargo"> · {{ c.cargo }}</template>
              </span>
            </div>

            <div class="estado">
              <span v-if="esListo(c)" class="badge badge-ok">Lista</span>
              <span v-if="c.sinCosto" class="badge badge-gratis"
                    title="Esta venta no tiene costo, así que no hay comprobante que adjuntar.">
                sin costo
              </span>
              <span v-for="f in faltaDe(c)" :key="f" class="badge badge-danger">{{ f }}</span>
              <button v-if="c.impresa" class="badge"
                      :class="c.impresaIncompleta ? 'badge-aviso' : 'badge-muted'"
                      :title="`Entregada ${c.vecesImpresa} vez(ces). Toca para ver quién y cuándo.`"
                      @click="verHistorial(c)">
                {{ c.impresaIncompleta ? '⚠ entregada incompleta' : '✓ entregada' }}
                <template v-if="c.vecesImpresa > 1"> ×{{ c.vecesImpresa }}</template>
              </button>
            </div>

            <div class="acciones">
              <template v-if="!c.conComprobante && c.requiereComprobante !== false">
                <input :id="`comp-${c.responsableId}`" class="oculto" type="file"
                       accept="image/*,application/pdf" @change="subir(c, 'comprobante', $event)" />
                <label :for="`comp-${c.responsableId}`" class="btn btn-sm"
                       :class="{ inerte: subiendo === c.responsableId }"
                       title="Adjuntar el comprobante de pago de esta venta">🧾 Comprobante</label>
              </template>
              <template v-if="!c.conFoto">
                <input :id="`foto-${c.responsableId}`" class="oculto" type="file" accept="image/*"
                       @change="subir(c, 'foto', $event)" />
                <label :for="`foto-${c.responsableId}`" class="btn btn-sm"
                       :class="{ inerte: subiendo === c.responsableId }"
                       :title="`Adjuntar la foto de ${c.nombre}`">📷 Foto</label>
              </template>

              <button class="btn btn-sm" :disabled="generando"
                      :title="`Corregir nombre, C.I., cargo o foto de ${c.nombre}`"
                      @click="editando = c">✏️ Datos</button>

              <button class="btn btn-primario btn-sm"
                      :disabled="generando || !c.listo?.[ID_VIRTUAL]"
                      :title="c.listo?.[ID_VIRTUAL]
                        ? `Descargar la credencial virtual de ${c.nombre}`
                        : 'Falta el comprobante o la foto'"
                      @click="bajarVirtual([c])">📱 Credencial</button>

              <button class="btn btn-sm"
                      :disabled="generando || !c.listo?.[ID_VIRTUAL]"
                      :title="c.listo?.[ID_VIRTUAL]
                        ? `Reenviar por WhatsApp la credencial de ${c.nombre}`
                        : 'Falta el comprobante o la foto'"
                      @click="reenviarWhatsApp([c])">WhatsApp</button>

              <template v-if="!soloVirtual">
                <button v-if="esListo(c)" class="btn btn-fantasma btn-sm" :disabled="generando"
                        title="Imprimir en papel solo esta"
                        @click="imprimir([c.responsableId], 1)">🖨</button>
                <button v-else class="btn btn-fantasma btn-sm" :disabled="generando"
                        :title="`Imprimir igual, aunque le falte ${faltaDe(c).join(' y ')}`"
                        @click="imprimirIgual(c)">🖨 igual</button>
              </template>
            </div>
          </li>
        </ul>
      </li>
    </ul>

    <UiModal v-if="reenvio" titulo="Reenviar credenciales por WhatsApp" @cerrar="!generando && (reenvio = null)">
      <p>Se reenviarán {{ reenvio.length }} credencial(es) de <strong>{{ reenvio[0].entidad }}</strong> al celular del cliente registrado en la venta.</p>
      <p v-if="reenvio.some(c => c.esExtra)">Las credenciales extra seleccionadas incluirán automáticamente su propio recibo de compra.</p>
      <label class="fila">
        <input v-model="incluirRecibo" type="checkbox" :disabled="generando" />
        Incluir recibo de venta
      </label>
      <template #pie>
        <button class="btn" :disabled="generando" @click="reenvio = null">Cancelar</button>
        <button class="btn btn-primario" :disabled="generando" @click="confirmarReenvio">{{ generando ? 'Enviando…' : 'Reenviar' }}</button>
      </template>
    </UiModal>

    <EditarDatosCredencial v-if="editando" :credencial="editando"
                           @cerrar="editando = null" @guardado="datosGuardados" />

    <div v-if="historial" class="velo" @click.self="historial = null">
      <div class="panel card">
        <header>
          <h3>Impresiones de {{ historial.credencial.nombre }}</h3>
          <button class="btn btn-fantasma btn-icono" @click="historial = null">✕</button>
        </header>
        <p v-if="!historial.filas.length" class="muted">Todavía no se ha impreso.</p>
        <ul v-else class="historial">
          <li v-for="(h, i) in historial.filas" :key="i">
            <span class="cuando">{{ h.cuando.slice(0, 16).replace('T', ' ') }}</span>
            <span class="quien2">{{ h.usuario }}</span>
            <span class="plant">{{ nombreDePlantilla(h.plantilla) }}</span>
            <span v-if="h.faltaba" class="badge badge-aviso">faltaba: {{ h.faltaba }}</span>
            <span v-else class="badge badge-ok">completa</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
.badge-gratis {
  background: color-mix(in srgb, var(--acento) 15%, transparent);
  color: var(--acento); font-weight: 700;
}

.badge-extra {
  background: color-mix(in srgb, #a855f7 18%, transparent);
  color: #7e22ce; font-weight: 700;
}

.credenciales { display: flex; flex-direction: column; gap: 1rem; }

.resumen { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 0.7rem; }
.tarjeta {
  font: inherit; text-align: left; cursor: pointer;
  display: flex; flex-direction: column; gap: 0.15rem;
  padding: 0.85rem 1rem; border-radius: var(--radio-sm);
  background: var(--panel); border: 1px solid var(--border); color: var(--text);
}
.tarjeta.activa { border-color: var(--acento); box-shadow: 0 0 0 1px var(--acento); }
.tarjeta strong { font-size: 1.6rem; line-height: 1; color: var(--ok); font-variant-numeric: tabular-nums; }
.tarjeta strong.pend { color: var(--danger); }
.tarjeta strong.muted { color: var(--muted); }
.tarjeta span { font-size: 0.82rem; color: var(--muted); }

.ajustes { display: grid; grid-template-columns: 1.2fr 1fr auto; gap: 1.2rem; padding: 1rem; align-items: start; }
.grupo { display: flex; flex-direction: column; gap: 0.5rem; min-width: 0; }
.rotulo { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.opciones { display: flex; gap: 0.5rem; flex-wrap: wrap; }
.opcion {
  font: inherit; cursor: pointer; text-align: left;
  display: flex; flex-direction: column; gap: 0.1rem;
  padding: 0.5rem 0.75rem; border-radius: var(--radio-sm);
  border: 1px solid var(--border); background: var(--panel); color: var(--text);
}
.opcion.elegida { border-color: var(--acento); background: color-mix(in srgb, var(--acento) 10%, var(--panel)); }
.opcion small { color: var(--muted); font-size: 0.75rem; }
.medida { display: flex; align-items: center; gap: 0.7rem; }
.medida input { flex: 1; }
.valor { font-variant-numeric: tabular-nums; font-weight: 700; white-space: nowrap; }
.nota { margin: 0; font-size: 0.78rem; color: var(--muted); }

.previa .hoja {
  width: 92px; aspect-ratio: 21.6 / 27.9;
  border: 1px solid var(--border); background: var(--panel-2);
  display: grid; place-items: center; border-radius: 2px;
}
.previa .cred { background: var(--acento); border-radius: 1px; }

.barra { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.barra .control { flex: 1; min-width: 220px; }

.entidades { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.7rem; }
.entidad { padding: 0; overflow: hidden; }
.cab-ent {
  display: flex; align-items: center; justify-content: space-between; gap: 0.8rem;
  padding: 0.7rem 0.9rem; background: var(--panel-2); border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
}
.quien-ent { display: flex; flex-direction: column; min-width: 0; }
.nom-ent { font-size: 1rem; }
.quien-ent .muted { font-size: 0.8rem; }
.acc-ent { display: flex; align-items: center; gap: 0.6rem; }
.cuenta-ent { font-size: 0.8rem; color: var(--muted); white-space: nowrap; }
.entidad .lista { gap: 0; }
.entidad .fila { border: none; border-bottom: 1px solid var(--border); border-radius: 0; background: transparent; }
.entidad .fila:last-child { border-bottom: none; }

.vacio { padding: 2.5rem 1rem; text-align: center; color: var(--muted); line-height: 1.5; }
.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.4rem; }
.fila {
  display: grid; align-items: center; gap: 0.8rem;
  grid-template-columns: auto 46px minmax(0, 1fr) 210px 250px;
  padding: 0.55rem 0.8rem; border-radius: var(--radio-sm);
  background: var(--panel); border: 1px solid var(--border);
}
.fila.marcada { border-color: var(--acento); background: color-mix(in srgb, var(--acento) 7%, var(--panel)); }
.fila.bloqueada { opacity: 0.72; }
.marca input { width: 18px; height: 18px; }
.foto { width: 46px; height: 46px; border-radius: var(--radio-sm); object-fit: cover; border: 1px solid var(--border); }
.foto.sinfoto { display: grid; place-items: center; background: var(--panel-2); color: var(--muted); font-weight: 700; }
.quien { display: flex; flex-direction: column; min-width: 0; }
.quien strong { font-size: 0.95rem; }
.quien span { font-size: 0.8rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.donde { display: flex; flex-direction: column; min-width: 0; }
.donde .cat { font-size: 0.78rem; color: var(--muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.donde .casetas { font-weight: 750; font-variant-numeric: tabular-nums; }
.estado { display: flex; gap: 0.3rem; flex-wrap: wrap; justify-content: flex-start; }
.badge { padding: 0.12rem 0.5rem; border-radius: 999px; font-size: 0.7rem; font-weight: 700; white-space: nowrap; }

.alcance {
  margin: 0; padding: 0.6rem 0.8rem; border-radius: var(--radio-sm);
  background: color-mix(in srgb, var(--acento) 10%, transparent);
  font-size: 0.88rem; line-height: 1.45;
}

.oculto { display: none; }
.acciones { display: flex; gap: 0.3rem; align-items: center; flex-wrap: wrap; justify-content: flex-end; }
.acciones .btn { cursor: pointer; }
.acciones .inerte { opacity: 0.5; pointer-events: none; }
button.badge { border: none; font-family: inherit; font-weight: 700; cursor: pointer; }
.badge-aviso {
  background: color-mix(in srgb, var(--tramite) 16%, transparent);
  color: var(--tramite);
}

.velo {
  position: fixed; inset: 0; z-index: 80; background: rgba(2, 6, 23, 0.5);
  display: grid; place-items: center; padding: 1rem;
}
.panel { width: 100%; max-width: 540px; padding: 1.1rem; display: flex; flex-direction: column; gap: 0.8rem; }
.panel header { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; }
.panel h3 { margin: 0; font-size: 1.05rem; }
.historial { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.45rem; }
.historial li {
  display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap;
  padding: 0.5rem 0.6rem; border-radius: var(--radio-sm); background: var(--panel-2);
  font-size: 0.85rem;
}
.historial .cuando { font-variant-numeric: tabular-nums; color: var(--muted); }
.historial .quien2 { font-weight: 700; }
.historial .plant { color: var(--muted); }

@media (max-width: 820px) {
  .ajustes { grid-template-columns: 1fr; }
  .previa { display: none; }
  .fila { grid-template-columns: auto 46px 1fr; row-gap: 0.5rem; }
  .estado, .acciones { grid-column: 2 / -1; flex-direction: row; gap: 0.5rem; align-items: center; justify-content: flex-start; }
  .cab-ent { align-items: flex-start; }
  .acc-ent { flex-basis: 100%; justify-content: space-between; }
}
</style>
