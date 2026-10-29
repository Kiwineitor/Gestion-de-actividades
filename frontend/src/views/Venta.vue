<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { toast } from '../ui/toast';
import { descargarRecibo, descargarCredencialVirtual } from '../ui/descargas';
import { guardarBorrador, leerBorrador, borrarBorrador } from '../ui/borrador';
import { partirNombre } from '../ui/nombres';
import CampoCelular from '../components/CampoCelular.vue';
import { mostrarCarga, ocultarCarga, textoCarga } from '../ui/cargando';
import { alerta, aviso, alertaConAccion } from '../ui/alerta';
import { marcarAvance, limpiarAvance } from '../ui/presencia';
import { aModulo } from '../modulos';

const auth = useAuthStore();
const tienda = usePuestosStore();
const router = useRouter();

const PASOS = ['Entidad', 'Responsables', 'Confirmar'];
const paso = ref(0);
const enviando = ref(false);
const tiposEntidad = ref([]);
const perdidas = ref([]);

const carrito = computed(() => tienda.carritoDe(auth.id));

const opcionesDe = ref(new Map());
const opcionElegida = ref(new Map());

async function cargarOpciones() {
  try {
    const r = await apiFetch('/api/app/categorias');
    if (!r.ok) return;
    const lista = await r.json();
    const m = new Map();
    for (const c of lista) m.set(c.id, c.opciones || []);
    opcionesDe.value = m;
  } catch {
  }
}

function opcionDe(categoriaId) {
  const lista = opcionesDe.value.get(categoriaId) || [];
  const elegida = opcionElegida.value.get(categoriaId);
  return lista.find((o) => o.id === elegida) || lista.find((o) => o.predeterminada) || null;
}

function precioDe(p) {
  if (p.precioPropio) return Number(p.precio || 0);
  const o = opcionDe(p.categoriaId);
  return Number(o ? o.precio : (p.precio || 0));
}

function elegirOpcion(categoriaId, opcionId) {
  const m = new Map(opcionElegida.value);
  m.set(categoriaId, opcionId);
  opcionElegida.value = m;
}

const total = computed(() => carrito.value.reduce((s, p) => s + precioDe(p), 0));

const porCategoria = computed(() => {
  const m = new Map();
  for (const p of carrito.value) {
    const clave = p.categoria || 'Sin categoría';
    if (!m.has(clave)) {
      m.set(clave, { categoria: clave, categoriaId: p.categoriaId, color: p.color,
                     casetas: [], subtotal: 0,
                     opciones: opcionesDe.value.get(p.categoriaId) || [] });
    }
    const g = m.get(clave);
    g.casetas.push(p);
    g.subtotal += precioDe(p);
  }
  for (const g of m.values()) {
    g.casetas.sort((a, b) => String(a.codigo).localeCompare(String(b.codigo), 'es', { numeric: true }));
    g.propias = g.casetas.filter((c) => c.precioPropio);
    const precios = new Set(g.casetas.map((c) => precioDe(c)));
    g.preciosMezclados = precios.size > 1;
  }
  return [...m.values()];
});

function listar(codigos) {
  if (codigos.length === 0) return '';
  if (codigos.length === 1) return String(codigos[0]);
  return `${codigos.slice(0, -1).join(', ')} y ${codigos[codigos.length - 1]}`;
}

const bs = (n) => Number(n || 0).toLocaleString('es-BO');

const AYUDAS = {
  entidadNombre: ['Nombre de la entidad',
    'El nombre con el que el expositor quiere aparecer en la feria y en su credencial. '
    + 'Puede ser una empresa, una asociación o un emprendimiento personal.'],
  tipoEntidad: ['Tipo de entidad',
    'La clasificación que usa la feria para esa entidad. Si no estás seguro, pregunta en '
    + 'administración: cambia el trato y a veces el precio.'],
  nit: ['NIT',
    'El número de identificación tributaria, si lo tiene. Es opcional: muchos emprendimientos '
    + 'pequeños no lo tienen y la venta se registra igual.'],
  rubro: ['Rubro o descripción',
    'Qué vende o expone, en pocas palabras: "artesanía en cuero", "café", "ropa infantil". '
    + 'Sale impreso en la credencial y sirve para agrupar por sector.'],
  responsableLegal: ['Responsable legal',
    'El DUEÑO de la caseta: a quien hay que llamar por un cobro o un problema. Es obligatorio. '
    + 'No tiene por qué ser quien atienda la caseta durante la feria.'],
  ciLegal: ['C.I. del responsable legal',
    'La cédula de identidad del dueño, solo los números. Es el dato con el que se le identifica '
    + 'si hay que reclamar algo.'],
  celularLegal: ['Celular del responsable legal',
    'El número al que se le puede llamar durante la feria. Elige primero el país: Bolivia o '
    + 'Brasil. El código se guarda junto al número, así que el teléfono queda marcable desde '
    + 'cualquier sitio.'],
  fechas: ['Desde y hasta',
    'Los días que ocupará la caseta, si es un periodo distinto al de toda la feria. Se pueden '
    + 'dejar en blanco.'],
  responsable: ['Responsables',
    'Quién ATIENDE la caseta durante la feria. Son los que reciben credencial y los que entran '
    + 'por la puerta con ella, así que necesitan foto. Pueden ser hasta dos, y pueden ser '
    + 'personas distintas del dueño.'],
  fotoResp: ['Foto del responsable',
    'Se imprime en su credencial. Es opcional ahora: si no la tomas, la venta se registra igual '
    + 'y la puedes subir después desde Mis ventas. Pero sin foto no se le puede emitir la '
    + 'credencial con etiquetas.'],
  contado: ['Pagó al contado',
    'Marca esto si te pagó en efectivo, en el momento. OJO: marcarlo dice CÓMO pagó, no que '
    + 'exista el recibo. El comprobante hay que subirlo igual, o no se le puede acreditar.'],
  banco: ['Entidad bancaria y N.º de comprobante',
    'Si pagó por transferencia o depósito: el nombre del banco (ej. Banco Unión) y el número '
    + 'que figura en el comprobante. Sirven para cuadrar el cobro con el extracto bancario.'],
};

function ayuda(clave) {
  const [titulo, texto] = AYUDAS[clave] || ['Ayuda', ''];
  return aviso(texto, 'info', 0, titulo);
}

const personaVacia = () => ({ nombre: '', paterno: '', materno: '', ci: '', celular: '' });

const form = reactive({
  entidadNombre: '', nit: '', descripcion: '', objeto: '',
  representanteLegal: '', ciRepresentante: '', celularRepresentante: '',
  tipoEntidadId: null,
  fechaInicio: '', fechaFin: '',
  copiaLegal: false,
  responsables: [personaVacia()],
  formaPago: '',
  entidadBancaria: '', numComprobante: null, pagoContado: false,
});

const comprobante = ref(null);

const fotos = ref([null, null]);

const hayResponsable2 = computed(() => form.responsables.length > 1);

const maxResponsables = computed(() => Math.max(1, carrito.value.length) * 2);
const puedeAgregarResponsable = computed(() => form.responsables.length < maxResponsables.value);

watch(() => form.formaPago, (v) => {
  form.pagoContado = v === 'contado';
  if (v === 'contado') { form.entidadBancaria = ''; form.numComprobante = null; }
});

function elegirComprobante(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  if (comprobante.value?.url) URL.revokeObjectURL(comprobante.value.url);
  comprobante.value = {
    archivo,
    url: archivo.type.startsWith('image/') ? URL.createObjectURL(archivo) : null,
    nombre: archivo.name,
  };
}

function quitarComprobante() {
  if (comprobante.value?.url) URL.revokeObjectURL(comprobante.value.url);
  comprobante.value = null;
}

function copiarDelLegal() {
  const r = form.responsables[0];
  if (!r) return;
  const partido = partirNombre(form.representanteLegal);
  r.nombre = partido.nombre;
  r.paterno = partido.paterno;
  r.materno = partido.materno;
  r.ci = form.ciRepresentante;
  r.celular = form.celularRepresentante;
}

async function alMarcarCopia(evento) {
  const marcado = evento.target.checked;
  if (!marcado) { form.copiaLegal = false; return; }

  const r = form.responsables[0];
  if (r && algoEscrito(r)) {
    form.copiaLegal = false;
    const sigue = await alertaConAccion(
      'El Responsable 1 ya tiene datos escritos. Si continúas se reemplazan por los del '
      + 'responsable legal.',
      'advertencia', null, 0);
    if (!sigue) return;
  }
  form.copiaLegal = true;
  copiarDelLegal();
}

watch(
  () => [form.representanteLegal, form.ciRepresentante, form.celularRepresentante],
  () => { if (form.copiaLegal) copiarDelLegal(); },
);

const intentado = ref(false);

const algoEscrito = (p) => Boolean(p.nombre || p.paterno || p.materno || p.ci || p.celular);

const requisitos = computed(() => {
  const lista = [];
  const pide = (paso, clave, etiqueta, cumplido) => {
    lista.push({ paso, clave, etiqueta, ok: Boolean(cumplido) });
  };

  pide(0, 'entidadNombre', 'Nombre de la entidad', form.entidadNombre.trim());
  pide(0, 'tipoEntidadId', 'Tipo de entidad', form.tipoEntidadId);
  pide(0, 'representanteLegal', 'Nombre del responsable legal', form.representanteLegal.trim());
  pide(0, 'ciRepresentante', 'C.I. del responsable legal', form.ciRepresentante.trim());
  pide(0, 'celularRepresentante', 'Celular del responsable legal', form.celularRepresentante.trim());

  const r0 = form.responsables[0] || {};
  pide(1, 'r0.nombre', 'Nombre del Responsable 1', (r0.nombre || '').trim());
  pide(1, 'r0.ci', 'C.I. del Responsable 1', (r0.ci || '').trim());
  for (let i = 1; i < form.responsables.length; i++) {
    const r = form.responsables[i];
    if (r && algoEscrito(r)) {
      pide(1, `r${i}.nombre`, `Nombre del Responsable ${i + 1}`, (r.nombre || '').trim());
    }
  }

  pide(2, 'formaPago', 'Cómo pagó (contado o depósito)', form.formaPago);
  return lista;
});

const faltantes = computed(() => new Map(
  requisitos.value
    .filter((r) => r.paso === paso.value && !r.ok)
    .map((r) => [r.clave, r.etiqueta]),
));

const falta = (clave) => intentado.value && faltantes.value.has(clave);

const avanceRegistro = computed(() => {
  const total = requisitos.value.length;
  if (!total) return 0;
  return Math.round((requisitos.value.filter((r) => r.ok).length / total) * 100);
});

const faltanTodos = computed(() => requisitos.value.filter((r) => !r.ok).map((r) => r.etiqueta));

watch([avanceRegistro, paso, faltanTodos, carrito], () => {
  if (!carrito.value.length) { marcarAvance(); return; }
  marcarAvance({
    porcentaje: avanceRegistro.value,
    paso: PASOS[paso.value],
    faltan: faltanTodos.value.slice(0, 3).join(', '),
  });
}, { immediate: true });

onUnmounted(() => limpiarAvance());

function siguiente() {
  intentado.value = true;
  if (faltantes.value.size) {
    nextTick(() => {
      const primero = document.querySelector('.control.falta');
      if (primero) {
        primero.focus({ preventScroll: true });
        primero.scrollIntoView({ block: 'center', behavior: 'smooth' });
      }
    });
    return;
  }
  intentado.value = false;
  if (paso.value < PASOS.length - 1) paso.value++;
}

function atras() {
  intentado.value = false;
  if (paso.value > 0) paso.value--;
}

function agregarResponsable() {
  if (!puedeAgregarResponsable.value) return;
  form.responsables.push(personaVacia());
}

function quitarUltimoResponsable() {
  if (form.responsables.length <= 1) return;
  const i = form.responsables.length - 1;
  form.responsables.splice(i, 1);
  fotos.value[i] = null;
}

function elegirFoto(indice, evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  if (fotos.value[indice]?.url) URL.revokeObjectURL(fotos.value[indice].url);
  fotos.value[indice] = { archivo, url: URL.createObjectURL(archivo) };
}

function quitarFoto(indice) {
  if (fotos.value[indice]?.url) URL.revokeObjectURL(fotos.value[indice].url);
  fotos.value[indice] = null;
}

async function subirFotos(inscripcionId) {
  const pendientes = fotos.value
    .map((f, i) => ({ f, i }))
    .filter((x) => x.f?.archivo && form.responsables[x.i]);
  if (!pendientes.length) return true;

  try {
    const r = await apiFetch(`/api/app/inscripciones/${inscripcionId}/responsables`);
    const lista = (await r.json())?.responsables || [];
    let subidas = 0;
    for (const { f, i } of pendientes) {
      const ci = (form.responsables[i].ci || '').trim();
      const destino = lista.find((x) => (x.ci || '').trim() === ci);
      if (!destino) continue;
      const datos = new FormData();
      datos.append('archivo', f.archivo);
      const res = await apiFetch(
        `/api/app/inscripciones/${inscripcionId}/responsables/${destino.id}/foto`,
        { method: 'POST', body: datos },
      );
      if (res.ok) subidas++;
    }
    return subidas === pendientes.length;
  } catch {
    return false;
  }
}

async function enviarWhatsAppVenta(inscripcionId, responsables) {
  if (!responsables.length) return null;
  try {
    const res = await apiFetch('/api/app/credenciales/whatsapp', {
      method: 'POST',
      body: JSON.stringify({
        inscripcionId,
        responsables: responsables.map((r) => r.id),
      }),
    });
    const data = await res.json().catch(() => ({}));
    return res.ok && data.ok;
  } catch {
    return false;
  }
}

const mayus = (v) => (typeof v === 'string' ? v.trim().toUpperCase() : v);

async function registrar() {
  if (enviando.value) return;
  if (!carrito.value.length) { alerta('No tienes casetas seleccionadas.', 'error'); return; }

  intentado.value = true;
  if (faltantes.value.size) {
    nextTick(() => {
      document.querySelector('.faltan')?.scrollIntoView({ block: 'center', behavior: 'smooth' });
    });
    return;
  }
  intentado.value = false;
  enviando.value = true;
  perdidas.value = [];
  mostrarCarga('Registrando la venta…');
  try {
    const responsables = form.responsables
      .filter((p) => p.nombre.trim() && p.ci.trim())
      .map((p) => ({
        nombre: mayus(p.nombre), paterno: mayus(p.paterno), materno: mayus(p.materno),
        ci: p.ci.trim(), celular: p.celular.trim(), correo: null,
      }));
    const r = await apiFetch('/api/app/inscripciones', {
      method: 'POST',
      body: JSON.stringify({
        entidadNombre: mayus(form.entidadNombre),
        nit: form.nit,
        descripcion: mayus(form.descripcion),
        objeto: mayus(form.objeto),
        representanteLegal: mayus(form.representanteLegal),
        ciRepresentante: form.ciRepresentante.trim(),
        celularRepresentante: form.celularRepresentante.trim(),
        tipoEntidadId: form.tipoEntidadId,
        fechaInicio: form.fechaInicio || null,
        fechaFin: form.fechaFin || null,
        responsables,
        entidadBancaria: mayus(form.entidadBancaria),
        numComprobante: form.numComprobante,
        pagoContado: form.pagoContado,
        puestos: carrito.value.map((p) => p.id),
        opcionesPorCategoria: Object.fromEntries(
          porCategoria.value
            .map((g) => [g.categoriaId, opcionDe(g.categoriaId)?.id])
            .filter(([cat, op]) => cat != null && op != null),
        ),
      }),
    });
    const d = await r.json().catch(() => ({}));

    if (r.status === 409) {
      await alerta(d.mensaje || 'Una caseta ya no está disponible', 'error', 0);
      await tienda.recargar();
      paso.value = 2;
      return;
    }
    if (!r.ok || !d.ok) {
      await alerta(d.mensaje || 'No se pudo registrar la venta', 'error', 0);
      return;
    }

    let comprobanteOk = true;
    if (comprobante.value?.archivo) {
      textoCarga.value = 'Subiendo el comprobante…';
      try {
        const datos = new FormData();
        datos.append('archivo', comprobante.value.archivo);
        if (form.entidadBancaria) datos.append('entidadBancaria', form.entidadBancaria);
        if (form.numComprobante) datos.append('numComprobante', String(form.numComprobante));
        const rc = await apiFetch(`/api/app/inscripciones/${d.inscripcionId}/comprobante`,
                                  { method: 'POST', body: datos });
        comprobanteOk = rc.ok;
      } catch {
        comprobanteOk = false;
      }
    }

    textoCarga.value = 'Subiendo las fotos…';
    const todas = await subirFotos(d.inscripcionId);
    borrarBorrador(auth.id);

    textoCarga.value = 'Preparando las credenciales…';
    const conCredencial = [];
    if (comprobanteOk && comprobante.value?.archivo) {
      try {
        const rr = await apiFetch(`/api/app/inscripciones/${d.inscripcionId}/responsables`);
        const lista = (await rr.json())?.responsables || [];
        for (const r of lista) if (r.tieneFoto) conCredencial.push(r);
      } catch { }
    }
    const carpeta = mayus(form.entidadNombre);
    for (const r of conCredencial) {
      await descargarCredencialVirtual(r.id, r.nombre, carpeta);
    }

    textoCarga.value = 'Enviando por WhatsApp…';
    const whatsappOk = await enviarWhatsAppVenta(d.inscripcionId, conCredencial);

    textoCarga.value = 'Preparando el recibo…';
    await descargarRecibo(d.inscripcionId, carpeta);
    ocultarCarga();

    const casetas = listar(carrito.value.map((p) => p.codigo));
    const pendiente = [
      todas ? null : 'alguna foto',
      comprobanteOk ? null : 'el comprobante',
    ].filter(Boolean).join(' y ');

    let sobreCredenciales = '';
    if (conCredencial.length) {
      sobreCredenciales = `\n\nSe descargó ${conCredencial.length === 1
        ? 'la credencial virtual'
        : `${conCredencial.length} credenciales virtuales`}. Ya se puede${conCredencial.length === 1 ? '' : 'n'} mandar al expositor.`
        + `\n\nEstán en Documentos, en la carpeta «${carpeta}», junto con el recibo.`;
      if (whatsappOk === true) sobreCredenciales += '\n\nTambién se envió por WhatsApp con las fotos.';
      else if (whatsappOk === false) sobreCredenciales += '\n\nNo se pudo enviar por WhatsApp; puedes reenviarlo desde Credenciales.';
    } else {
      sobreCredenciales = '\n\nLa credencial virtual se descarga desde Credenciales, en cuanto '
        + 'estén el comprobante y la foto del responsable.';
    }

    await aviso(
      `Caseta${carrito.value.length === 1 ? '' : 's'} ${casetas} · ${bs(d.total)} Bs`
      + sobreCredenciales
      + (pendiente ? `\n\nNo se pudo subir ${pendiente}. Puedes completarlo desde Mis ventas.` : ''),
      'ok', 0, '¡Venta registrada!');
    router.push(aModulo('mis-ventas', { registrada: d.inscripcionId }));
  } catch (e) {
    await alerta(e.message, 'error', 0);
  } finally {
    ocultarCarga();
    enviando.value = false;
  }
}

watch(form, () => guardarBorrador(auth.id, form), { deep: true });

const ventaRef = ref(null);

watch(paso, () => {
  ventaRef.value?.scrollIntoView({ block: 'start', behavior: 'auto' });
});

onMounted(async () => {
  const guardado = leerBorrador(auth.id);
  if (guardado) {
    Object.assign(form, guardado);
    if (!Array.isArray(form.responsables) || !form.responsables.length) {
      form.responsables = [personaVacia()];
    }
    toast('Se recuperó lo que habías escrito', 'info');
  }
  await tienda.asegurar();
  const opcionesListas = cargarOpciones();
  try {
    const r = await apiFetch('/api/app/catalogos/tipos-entidad');
    tiposEntidad.value = await r.json();
  } catch (e) {
    toast('No se pudieron cargar los tipos de entidad', 'error');
  }
  await opcionesListas;
});

onUnmounted(() => {
  fotos.value.forEach((f) => f?.url && URL.revokeObjectURL(f.url));
  if (comprobante.value?.url) URL.revokeObjectURL(comprobante.value.url);
});
</script>

<template>
  <div class="venta" ref="ventaRef">
    <header class="resumen card">
      <div class="lineas">
        <div v-for="g in porCategoria" :key="g.categoria" class="grupo-cab">
          <span class="punto" :style="{ background: g.color || 'var(--acento)' }"></span>
          <span class="cat">{{ g.categoria }}</span>
          <span class="nums">
            caseta{{ g.casetas.length === 1 ? '' : 's' }}
            {{ listar(g.casetas.map((c) => c.codigo)) }}
          </span>
          <span v-if="porCategoria.length > 1" class="sub-bs">{{ bs(g.subtotal) }} Bs</span>
        </div>
        <div class="total-cab">
          <span>Total</span>
          <strong class="total">{{ bs(total) }} Bs</strong>
        </div>
      </div>
      <router-link :to="aModulo('mapa')" class="btn btn-fantasma btn-sm volver">← Volver al mapa</router-link>
    </header>

    <p v-if="!carrito.length" class="vacio card">
      No tienes casetas seleccionadas.
      <router-link :to="aModulo('mapa')">Ve al mapa</router-link> y toca las que vas a vender.
    </p>

    <template v-else>
      <ol class="pasos">
        <li v-for="(p, i) in PASOS" :key="p" :class="{ activo: i === paso, hecho: i < paso }">
          <span class="num">{{ i + 1 }}</span>{{ p }}
        </li>
      </ol>

      <div v-if="intentado && faltantes.size" class="faltan card" role="alert">
        <strong>Falta completar:</strong>
        <ul>
          <li v-for="[clave, etiqueta] in faltantes" :key="clave">{{ etiqueta }}</li>
        </ul>
      </div>

      <section v-show="paso === 0" class="card bloque">
        <div v-if="porCategoria.some((g) => g.opciones.length > 1 || g.preciosMezclados || g.propias.length)"
             class="opciones-precio">
          <h3 class="titulo-bloque">Qué se está vendiendo</h3>
          <div v-for="g in porCategoria" :key="'op-' + g.categoria" class="grupo-opcion">
            <div class="cab-grupo-op">
              <span class="punto" :style="{ background: g.color || 'var(--acento)' }"></span>
              <strong>{{ g.categoria }}</strong>
              <span class="muted">
                · caseta{{ g.casetas.length === 1 ? '' : 's' }}
                {{ listar(g.casetas.map((c) => c.codigo)) }}
              </span>
            </div>

            <div v-if="g.opciones.length > 1" class="elecciones">
              <button v-for="o in g.opciones" :key="o.id" type="button" class="opcion"
                      :class="{ marcada: opcionDe(g.categoriaId)?.id === o.id }"
                      @click="elegirOpcion(g.categoriaId, o.id)">
                <span class="nom">{{ o.nombre }}</span>
                <span class="pre">{{ bs(o.precio) }} Bs</span>
              </button>
            </div>
            <p v-else class="muted sin-eleccion">
              {{ opcionDe(g.categoriaId)?.nombre || g.categoria }} ·
              {{ bs(opcionDe(g.categoriaId)?.precio ?? g.casetas[0]?.precio) }} Bs por caseta
            </p>

            <p v-if="g.propias.length" class="aviso-propio">
              Caseta{{ g.propias.length === 1 ? '' : 's' }}
              {{ listar(g.propias.map((c) => c.codigo)) }}
              {{ g.propias.length === 1 ? 'tiene' : 'tienen' }} precio propio y no
              {{ g.propias.length === 1 ? 'cambia' : 'cambian' }} con la opción.
            </p>

            <ul v-if="g.preciosMezclados" class="desglose-op">
              <li v-for="c in g.casetas" :key="c.id">
                <span>Caseta {{ c.codigo }}<span v-if="c.precioPropio" class="marca-propio">precio propio</span></span>
                <strong>{{ bs(precioDe(c)) }} Bs</strong>
              </li>
            </ul>

            <p class="subtotal-op">
              <template v-if="!g.preciosMezclados">
                {{ g.casetas.length }} × {{ bs(g.subtotal / (g.casetas.length || 1)) }} Bs =
              </template>
              <template v-else>Subtotal: </template>
              <strong>{{ bs(g.subtotal) }} Bs</strong>
            </p>
          </div>
          <div class="separador"></div>
        </div>

        <label class="campo">
          <span>Nombre de la entidad *<button type="button" class="ayuda" @click.prevent="ayuda('entidadNombre')" aria-label="Qué es esto">?</button></span>
          <input class="control mayus" :class="{ falta: falta('entidadNombre') }"
                 v-model="form.entidadNombre" placeholder="Ej. Artesanías Illimani" />
        </label>
        <label class="campo">
          <span>Tipo de entidad *<button type="button" class="ayuda" @click.prevent="ayuda('tipoEntidad')" aria-label="Qué es esto">?</button></span>
          <select class="control" :class="{ falta: falta('tipoEntidadId') }" v-model="form.tipoEntidadId">
            <option :value="null" disabled>Elige una opción…</option>
            <option v-for="t in tiposEntidad" :key="t.id" :value="t.id">{{ t.nombre }}</option>
          </select>
        </label>
        <div class="dos">
          <label class="campo">
            <span>NIT<button type="button" class="ayuda" @click.prevent="ayuda('nit')" aria-label="Qué es esto">?</button></span>
            <input class="control" v-model="form.nit" inputmode="numeric" placeholder="Solo números" />
          </label>
          <label class="campo">
            <span>Rubro o descripción<button type="button" class="ayuda" @click.prevent="ayuda('rubro')" aria-label="Qué es esto">?</button></span>
            <input class="control mayus" v-model="form.descripcion" placeholder="Qué vende o expone" />
          </label>
        </div>

        <div class="separador"></div>

        <h3 class="sub">
          Responsable legal
          <span class="muted">— el dueño de la caseta</span>
          <button type="button" class="ayuda" @click.prevent="ayuda('responsableLegal')" aria-label="Qué es esto">?</button>
        </h3>
        <label class="campo">
          <span>Nombre completo *<button type="button" class="ayuda" @click.prevent="ayuda('responsableLegal')" aria-label="Qué es esto">?</button></span>
          <input class="control mayus" :class="{ falta: falta('representanteLegal') }"
                 v-model="form.representanteLegal" placeholder="Ej. María Quispe Mamani" />
        </label>
        <div class="dos">
          <label class="campo">
            <span>C.I. *<button type="button" class="ayuda" @click.prevent="ayuda('ciLegal')" aria-label="Qué es esto">?</button></span>
            <input class="control" :class="{ falta: falta('ciRepresentante') }"
                   v-model="form.ciRepresentante" inputmode="numeric" placeholder="Ej. 8765432" />
          </label>
          <label class="campo">
            <span>Celular *<button type="button" class="ayuda" @click.prevent="ayuda('celularLegal')" aria-label="Qué es esto">?</button></span>
            <CampoCelular v-model="form.celularRepresentante" :falta="falta('celularRepresentante')" />
          </label>
        </div>

        <div class="separador"></div>

        <div class="dos">
          <label class="campo"><span>Desde</span><input class="control" type="date" v-model="form.fechaInicio" /></label>
          <label class="campo"><span>Hasta</span><input class="control" type="date" v-model="form.fechaFin" /></label>
        </div>
      </section>

      <section v-show="paso === 1" class="card bloque">
        <p class="nota">
          Quién <strong>atiende</strong> la caseta durante la feria. Son los que reciben
          credencial, por eso se les puede tomar la foto aquí mismo.
          <button type="button" class="ayuda" @click.prevent="ayuda('responsable')" aria-label="Saber más">?</button>
        </p>

        <template v-for="(r, i) in form.responsables" :key="i">
          <div v-if="i > 0" class="separador"></div>
          <h3 class="sub">Responsable {{ i + 1 }}</h3>

          <label v-if="i === 0" class="fila-check">
            <input type="checkbox" :checked="form.copiaLegal" @change="alMarcarCopia" />
            Es el mismo responsable legal
          </label>
          <p v-if="i === 0 && form.copiaLegal" class="nota">
            Se repartió el nombre completo en nombre y apellidos. Si quedó mal, corrígelo aquí
            mismo: los campos siguen editables.
          </p>

          <div class="dos">
            <label class="campo">
              <span>Nombre {{ i === 0 ? '*' : '' }}</span>
              <input class="control mayus" :class="{ falta: falta(`r${i}.nombre`) }"
                     v-model="r.nombre" placeholder="Ej. María" />
            </label>
            <label class="campo">
              <span>C.I. {{ i === 0 ? '*' : '' }}</span>
              <input class="control" :class="{ falta: falta(`r${i}.ci`) }"
                     v-model="r.ci" inputmode="numeric" placeholder="Ej. 8765432" />
            </label>
          </div>
          <div class="dos">
            <label class="campo">
              <span>Apellido paterno</span>
              <input class="control mayus"
                     v-model="r.paterno" placeholder="Ej. Quispe" />
            </label>
            <label class="campo">
              <span>Apellido materno</span>
              <input class="control mayus"
                     v-model="r.materno" placeholder="Ej. Mamani" />
            </label>
          </div>
          <label class="campo">
            <span>Celular</span>
            <CampoCelular v-model="r.celular" />
          </label>

          <div class="foto">
            <img v-if="fotos[i]?.url" :src="fotos[i].url" alt="Foto del responsable" />
            <div v-else class="sinfoto">Sin foto</div>
            <div class="foto-acciones">
              <input :id="`foto-${i}`" class="oculto" type="file" accept="image/jpeg,image/png"
                     @change="elegirFoto(i, $event)" />
              <label :for="`foto-${i}`" class="btn btn-sm">
                📷 {{ fotos[i] ? 'Cambiar foto' : 'Tomar foto' }}
              </label>
              <button v-if="fotos[i]" class="btn btn-peligro btn-sm" @click="quitarFoto(i)">Quitar</button>
              <span class="muted formato-permitido">Formatos: JPG, PNG</span>
              <span class="muted opcional">Opcional</span>
              <button type="button" class="ayuda" @click.prevent="ayuda('fotoResp')" aria-label="Qué es esto">?</button>
            </div>
          </div>
        </template>

        <p class="muted derecho">
          {{ carrito.length }} caseta{{ carrito.length === 1 ? '' : 's' }} dan derecho a
          <strong>{{ maxResponsables }} responsables</strong>
          ({{ form.responsables.length }} de {{ maxResponsables }} usados).
          Si hacen falta más, se agregan desde <strong>Mis ventas</strong> y tienen un costo de
          15 Bs cada uno.
        </p>
        <div class="fila acciones-resp">
          <button v-if="puedeAgregarResponsable" class="btn" @click="agregarResponsable">
            ＋ Agregar responsable
          </button>
          <button v-if="form.responsables.length > 1" class="btn btn-peligro"
                  @click="quitarUltimoResponsable">
            Quitar el último
          </button>
        </div>
      </section>

      <section v-show="paso === 2" class="card bloque">
        <h3 class="sub">Qué se está vendiendo</h3>
        <div v-for="g in porCategoria" :key="g.categoria" class="grupo-conf">
          <header>
            <span class="sw" :style="{ background: g.color || '#94a3b8' }"></span>
            <strong>{{ g.categoria }}</strong>
            <span class="sub-bs">{{ bs(g.subtotal) }} Bs</span>
          </header>
          <ul class="casetas">
            <li v-for="p in g.casetas" :key="p.id" :class="{ perdida: perdidas.includes(p.id) }">
              <span class="nom">Caseta {{ p.codigo }}</span>
              <span class="muted">{{ p.tamano }}</span>
              <span class="precio">{{ bs(p.precio) }} Bs</span>
            </li>
          </ul>
        </div>

        <div class="cuenta">
          <div class="linea grande">
            <span>Total a cobrar</span>
            <strong>{{ bs(total) }} Bs</strong>
          </div>
        </div>
        <p class="nota">
          ¿Hay que cambiar alguna caseta?
          <router-link :to="aModulo('mapa')">Vuelve al mapa</router-link> para quitarla o agregar otra.
        </p>

        <div class="separador"></div>

        <h3 class="sub">
          Pago
          <button type="button" class="ayuda" @click.prevent="ayuda('contado')" aria-label="Qué es esto">?</button>
        </h3>

        <div class="formas" :class="{ falta: falta('formaPago') }" role="radiogroup"
             aria-label="Cómo pagó">
          <button type="button" class="forma" :class="{ activa: form.formaPago === 'contado' }"
                  role="radio" :aria-checked="form.formaPago === 'contado'"
                  @click="form.formaPago = 'contado'">
            <span class="ico">💵</span>
            <strong>Al contado</strong>
            <small>Efectivo, en el momento</small>
          </button>
          <button type="button" class="forma" :class="{ activa: form.formaPago === 'deposito' }"
                  role="radio" :aria-checked="form.formaPago === 'deposito'"
                  @click="form.formaPago = 'deposito'">
            <span class="ico">🏦</span>
            <strong>Depósito</strong>
            <small>Transferencia o banco</small>
          </button>
        </div>

        <template v-if="form.formaPago === 'deposito'">
          <div class="dos">
            <label class="campo">
              <span>Entidad bancaria<button type="button" class="ayuda" @click.prevent="ayuda('banco')" aria-label="Qué es esto">?</button></span>
              <input class="control mayus" v-model="form.entidadBancaria" placeholder="Ej. Banco Unión" />
            </label>
            <label class="campo">
              <span>N.º de comprobante</span>
              <input class="control" type="text" inputmode="text"
                     v-model="form.numComprobante" maxlength="100" placeholder="Número o código del comprobante" />
              <small class="muted">Hasta 100 caracteres. Admite letras, números y ceros iniciales.</small>
            </label>
          </div>
        </template>

        <div class="comprobante-adj">
          <div class="cabecera-adj">
            <strong>Comprobante de pago</strong>
            <span class="badge" :class="comprobante ? 'badge-ok' : 'badge-aviso'">
              {{ comprobante ? 'adjuntado' : 'pendiente' }}
            </span>
          </div>
          <p class="nota">
            Hace falta <strong>siempre</strong>, también al contado: sin él no se le puede
            emitir la credencial al expositor. Si lo tienes ahora, adjúntalo; si no, se sube
            después desde <strong>Mis ventas</strong>.
          </p>
          <div class="acciones-adj">
            <input id="comprobante-venta" class="oculto" type="file"
                   accept="image/jpeg,image/png,application/pdf" @change="elegirComprobante" />
            <label for="comprobante-venta" class="btn btn-sm">
              📎 {{ comprobante ? 'Cambiar' : 'Adjuntar comprobante' }}
            </label>
            <button v-if="comprobante" type="button" class="btn btn-peligro btn-sm"
                    @click="quitarComprobante">Quitar</button>
            <span class="muted formato-permitido">Formatos: JPG, PNG, PDF</span>
          </div>
          <div v-if="comprobante" class="previa-adj">
            <img v-if="comprobante.url" :src="comprobante.url" alt="Comprobante elegido" />
            <span v-else class="muted">📄 {{ comprobante.nombre }}</span>
          </div>
        </div>
      </section>

      <div class="pie">
        <div class="acciones">
          <button class="btn" :disabled="paso === 0 || enviando" @click="atras">Atrás</button>
          <button v-if="paso < 2" class="btn btn-primario" @click="siguiente">Siguiente</button>
          <button v-else class="btn btn-primario" :disabled="enviando" @click="registrar">
            {{ enviando ? 'Registrando…' : `Registrar venta (${total.toLocaleString('es-BO')} Bs)` }}
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.opciones-precio { display: flex; flex-direction: column; gap: 0.9rem; }
.titulo-bloque { margin: 0; font-size: 1.05rem; }
.grupo-opcion { display: flex; flex-direction: column; gap: 0.45rem; }
.cab-grupo-op { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.elecciones { display: flex; gap: 0.5rem; flex-wrap: wrap; }

.aviso-propio {
  margin: 0; font-size: 0.88rem; line-height: 1.4;
  color: var(--tramite, #d97706);
}
.desglose-op { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.25rem; }
.desglose-op li {
  display: flex; justify-content: space-between; align-items: baseline; gap: 0.6rem;
  font-size: 0.92rem;
}
.marca-propio {
  margin-left: 0.4rem; padding: 0.05rem 0.4rem; border-radius: 999px;
  font-size: 0.72rem; font-weight: 700;
  color: var(--tramite, #d97706);
  border: 1px solid color-mix(in srgb, var(--tramite, #d97706) 45%, transparent);
}
.opcion {
  flex: 1 1 160px; min-height: 56px; display: flex; flex-direction: column; justify-content: center;
  gap: 0.15rem; padding: 0.5rem 0.75rem; border-radius: 10px; cursor: pointer;
  border: 2px solid var(--borde, rgba(128,128,128,0.35)); background: var(--panel, transparent);
  text-align: left; font: inherit; color: inherit;
}
.opcion.marcada { border-color: var(--acento); background: color-mix(in srgb, var(--acento) 12%, transparent); }
.opcion .nom { font-weight: 600; }
.opcion .pre { font-size: 1.05rem; }
.sin-eleccion { margin: 0; }
.derecho { margin: 0.5rem 0; line-height: 1.5; }
.acciones-resp { gap: 0.5rem; flex-wrap: wrap; }
.subtotal-op { margin: 0; font-size: 0.95rem; }

.venta {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 720px;
  margin: 0 auto;
  width: 100%;
}
.resumen {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 0.8rem;
  padding: 0.8rem 1rem; position: sticky; top: 0; z-index: 5;
}
.lineas { display: flex; flex-direction: column; gap: 0.25rem; min-width: 0; flex: 1; }
.grupo-cab { display: flex; align-items: baseline; gap: 0.35rem; flex-wrap: wrap; font-size: 0.9rem; }
.grupo-cab .punto { width: 10px; height: 10px; border-radius: 3px; flex: none; }
.grupo-cab .cat { font-weight: 700; }
.grupo-cab .nums { color: var(--muted); }
.grupo-cab .sub-bs { margin-left: auto; font-variant-numeric: tabular-nums; }
.total-cab {
  display: flex; align-items: baseline; justify-content: space-between; gap: 0.6rem;
  border-top: 1px solid var(--border); padding-top: 0.3rem; margin-top: 0.15rem;
  font-size: 0.88rem; color: var(--muted);
}
.resumen .total { font-variant-numeric: tabular-nums; font-size: 1.15rem; color: var(--texto); }
.volver { flex: none; white-space: nowrap; }

.grupo-conf { display: flex; flex-direction: column; gap: 0.2rem; }
.grupo-conf > header { display: flex; align-items: center; gap: 0.5rem; }
.grupo-conf > header .sub-bs { margin-left: auto; font-variant-numeric: tabular-nums; font-weight: 700; }
.cuenta {
  display: flex; flex-direction: column; gap: 0.3rem;
  padding: 0.7rem 0.9rem; border-radius: var(--radio-sm); background: var(--panel-2);
}
.cuenta .linea { display: flex; justify-content: space-between; gap: 0.8rem; font-size: 0.9rem; color: var(--muted); }
.cuenta .linea.grande { font-size: 1.1rem; color: var(--texto); font-weight: 700; }
.cuenta .linea.grande strong { font-variant-numeric: tabular-nums; }

.formas { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; }
.formas.falta { outline: 2px solid var(--danger); outline-offset: 4px; border-radius: var(--radio-sm); }
.forma {
  display: flex; flex-direction: column; align-items: center; gap: 0.15rem;
  padding: 0.85rem 0.6rem; border-radius: var(--radio-sm);
  border: 2px solid var(--border); background: var(--panel); color: var(--texto);
  cursor: pointer; transition: border-color 0.15s ease, background 0.15s ease, transform 0.1s ease;
  font: inherit; text-align: center;
}
.forma .ico { font-size: 1.5rem; line-height: 1.1; }
.forma strong { font-size: 1rem; }
.forma small { color: var(--muted); font-size: 0.78rem; }
.forma:active { transform: scale(0.98); }
.forma.activa {
  border-color: var(--acento);
  background: color-mix(in srgb, var(--acento) 12%, var(--panel));
}
.forma.activa strong { color: var(--acento); }

.comprobante-adj {
  display: flex; flex-direction: column; gap: 0.5rem;
  padding: 0.8rem 0.9rem; border-radius: var(--radio-sm); background: var(--panel-2);
  border-left: 3px solid var(--tramite);
}
.cabecera-adj { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; }
.badge-aviso {
  background: color-mix(in srgb, var(--tramite) 16%, transparent);
  color: var(--tramite); font-weight: 700;
}
.acciones-adj { display: flex; gap: 0.5rem; flex-wrap: wrap; align-items: center; }
.acciones-adj label.btn { cursor: pointer; }
.previa-adj img {
  width: 100%; max-height: 200px; object-fit: contain; border-radius: var(--radio-sm);
  border: 1px solid var(--border); background: var(--panel);
}
.formato-permitido { font-size: 0.72rem; color: var(--muted); margin-left: auto; }

.ayuda {
  display: inline-grid; place-items: center; width: 22px; height: 22px; margin-left: 0.35rem;
  border-radius: 50%; border: 1px solid var(--border); background: var(--panel-2);
  color: var(--muted); font-size: 0.75rem; font-weight: 800; cursor: pointer;
  vertical-align: middle; flex: none;
}
.ayuda:hover, .ayuda:focus-visible { border-color: var(--acento); color: var(--acento); }

.vacio { padding: 2rem 1rem; text-align: center; color: var(--muted); }

.pasos { display: flex; gap: 0.5rem; list-style: none; margin: 0; padding: 0; flex-wrap: wrap; }
.pasos li {
  display: flex; align-items: center; gap: 0.4rem; font-size: 0.85rem;
  color: var(--muted); font-weight: 600;
}
.pasos .num {
  display: grid; place-items: center; width: 1.5rem; height: 1.5rem; border-radius: 50%;
  background: var(--panel-2); border: 1px solid var(--border); font-size: 0.75rem;
}
.pasos .activo { color: var(--acento); }
.pasos .activo .num { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.pasos .hecho .num { background: var(--ok); border-color: var(--ok); color: #fff; }

.faltan {
  padding: 0.8rem 1rem; border-color: color-mix(in srgb, var(--danger) 40%, var(--border));
  background: var(--danger-suave); color: var(--danger); font-size: 0.9rem;
}
.faltan ul { margin: 0.3rem 0 0; padding-left: 1.1rem; }
.faltan li { margin: 0.1rem 0; }

.bloque { padding: 1.1rem; display: flex; flex-direction: column; gap: 0.85rem; }
.sub { margin: 0; font-size: 0.95rem; font-weight: 700; }
.dos { display: grid; gap: 0.85rem; grid-template-columns: 1fr 1fr; }
.separador { height: 1px; background: var(--border); }
.fila-check { display: flex; align-items: center; gap: 0.5rem; font-size: 0.92rem; }
.nota { margin: 0; font-size: 0.83rem; color: var(--muted); line-height: 1.45; }

.control.falta {
  border-color: var(--danger); border-width: 2px;
  background: var(--danger-suave);
}
.control:disabled { opacity: 0.65; cursor: not-allowed; }

.mayus { text-transform: uppercase; }
.mayus::placeholder { text-transform: none; }

.foto { display: flex; align-items: center; gap: 0.8rem; }
.foto img, .foto .sinfoto {
  width: 72px; height: 72px; border-radius: var(--radio-sm); flex: none;
  border: 1px solid var(--border); object-fit: cover;
}
.foto .sinfoto {
  display: grid; place-items: center; background: var(--panel-2);
  color: var(--muted); font-size: 0.72rem; text-align: center;
}
.foto-acciones { display: flex; align-items: center; gap: 0.4rem; flex-wrap: wrap; }
.foto-acciones .opcional { font-size: 0.78rem; }
.oculto { display: none; }

.casetas { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
.casetas li {
  display: flex; align-items: center; gap: 0.6rem; padding: 0.5rem 0;
  border-bottom: 1px solid var(--border); font-size: 0.9rem;
}
.casetas li:last-child { border-bottom: none; }
.casetas .sw { width: 12px; height: 12px; border-radius: 3px; flex: none; }
.casetas .nom { font-weight: 650; }
.casetas .precio { margin-left: auto; font-variant-numeric: tabular-nums; font-weight: 650; }
.casetas .perdida { color: var(--danger); text-decoration: line-through; }

.acciones { display: flex; gap: 0.6rem; justify-content: flex-end; padding-bottom: 1rem; }

.pie { display: flex; flex-direction: column; align-items: stretch; gap: 0.5rem; position: relative; }

@media (max-width: 560px) {
  .dos { grid-template-columns: 1fr; }

  .pie {
    position: sticky;
    bottom: var(--tabbar-h);
    margin: 0 -1.4rem;
    padding: 0.7rem 1.4rem;
    background: var(--bg);
    z-index: 6;
    border-top: 1px solid var(--border);
  }
  .acciones { padding-bottom: 0; }
  .acciones .btn { flex: 1; }

  .venta { gap: 1.1rem; }
  .bloque { padding: 1.15rem 1rem 1.3rem; gap: 1.1rem; }
  .sub { font-size: 1.1rem; }
  .pasos li { font-size: 0.95rem; }
  .pasos .num { width: 1.75rem; height: 1.75rem; font-size: 0.85rem; }
  .resumen { padding: 0.9rem 1rem; font-size: 1rem; }
  .resumen .total { font-size: 1.15rem; }
  .casetas li { font-size: 1rem; padding: 0.7rem 0; gap: 0.7rem; }
  .fila-check { font-size: 1.02rem; gap: 0.7rem; }
  .fila-check input[type='checkbox'] { width: 24px; height: 24px; }
  .nota { font-size: 0.95rem; }
  .campo > span { font-size: 0.95rem; font-weight: 650; }
  .control { font-size: 1.05rem; min-height: 50px; }
  select.control { min-height: 50px; }
  .grupo-cab { font-size: 0.98rem; }
  .resumen .total { font-size: 1.3rem; }
  .cuenta .linea { font-size: 0.98rem; }
  .cuenta .linea.grande { font-size: 1.25rem; }
  .ayuda { width: 28px; height: 28px; font-size: 0.9rem; }
  .forma { padding: 1rem 0.6rem; }
  .forma strong { font-size: 1.05rem; }
  .acciones-adj .btn { min-height: 46px; }
  .faltan { font-size: 1rem; }
  .acciones .btn { min-height: 56px; font-size: 1.08rem; font-weight: 700; }
  .bloque > .btn { min-height: 50px; font-size: 1rem; }
  .foto img, .foto .sinfoto { width: 88px; height: 88px; }
  .foto-acciones .btn { min-height: 44px; }
}
</style>
