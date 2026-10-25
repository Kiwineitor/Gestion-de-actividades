<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { primeraPaginaEnCanvas } from '../ui/pdf';

const CAMPOS = [
  { id: 'qr', txt: 'Código QR', muestra: 'QR', obligatorio: true },
  { id: 'foto', txt: 'Fotografía', muestra: 'FOTO' },
  { id: 'nombre', txt: 'Nombre completo', muestra: 'NOMBRE APELLIDO EJEMPLO' },
  { id: 'empresa', txt: 'Empresa / actividad', muestra: 'EMPRESA DE EJEMPLO' },
  { id: 'ci', txt: 'C.I.', muestra: '1234567' },
  { id: 'tipo', txt: 'Cargo o tipo', muestra: 'EXPOSITOR' },
  { id: 'codigo', txt: 'Número de puesto', muestra: '12' },
  { id: 'zona', txt: 'Zona / categoría', muestra: 'CATEGORÍA' },
];
const OPCIONES = [
  { id: 'requiereFoto', txt: 'Exigir foto para emitir' },
  { id: 'mayusculas', txt: 'Texto en mayúsculas' },
  { id: 'centrado', txt: 'Texto centrado' },
  { id: 'multilinea', txt: 'Permitir dos líneas' },
  { id: 'fotoCircular', txt: 'Foto redonda' },
];

const ANCHO_CM = 10;

const plantillas = ref([]);
const ediciones = ref([]);
const dependencias = ref([]);
const cargando = ref(true);
const grupos = computed(() => [
  { uso: 'EXPOSITOR', titulo: 'Expositores', items: plantillas.value.filter((p) => p.uso !== 'APOYO') },
  { uso: 'APOYO', titulo: 'Personal de apoyo', items: plantillas.value.filter((p) => p.uso === 'APOYO') },
]);
const nombreEdicion = (id) => ediciones.value.find((e) => e.id === id)?.nombre;

async function cargarCatalogos() {
  try {
    const [e, d] = await Promise.all([apiFetch('/api/app/ediciones'), apiFetch('/api/app/personal-apoyo/dependencias')]);
    if (e.ok) ediciones.value = await e.json();
    if (d.ok) dependencias.value = await d.json();
  } catch { /* sin catalogos: los selectores quedan con la opcion general */ }
}
const selId = ref(null);
const borrador = ref(null);
const original = ref('');
const guardando = ref(false);
const campoSel = ref(null);
const fondos = reactive({});
const lienzo = ref(null);

const seleccionada = computed(() => plantillas.value.find((p) => p.id === selId.value) || null);
const modificado = computed(() => borrador.value != null && JSON.stringify(borrador.value) !== original.value);
const proporcion = computed(() => borrador.value?.proporcion || 1.3);
const cajaSel = computed(() => (campoSel.value && borrador.value?.cajas[campoSel.value]) || null);
const etiquetaCampo = (id) => CAMPOS.find((c) => c.id === id)?.txt ?? id;

function copia(p) {
  return JSON.parse(JSON.stringify({ ...p, cajas: p.cajas || {} }));
}

async function cargar(conservar = selId.value) {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/plantillas-credencial');
    if (!r.ok) { toast('No se pudieron cargar las plantillas', 'error'); return; }
    plantillas.value = await r.json();
    const id = plantillas.value.some((p) => p.id === conservar) ? conservar : plantillas.value[0]?.id ?? null;
    elegir(id, true);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function elegir(id, forzar = false) {
  if (!forzar && modificado.value && !confirm('Hay cambios sin guardar en esta plantilla. ¿Descartarlos?')) return;
  selId.value = id;
  const p = plantillas.value.find((x) => x.id === id);
  borrador.value = p ? copia(p) : null;
  original.value = borrador.value ? JSON.stringify(borrador.value) : '';
  campoSel.value = null;
  if (p) {
    asegurarFondo(p.imagen);
    asegurarFondo(p.imagenReverso);
  }
}

async function asegurarFondo(imagen) {
  if (!imagen || fondos[imagen]) return;
  try {
    const r = await apiFetch(`/api/app/plantillas-credencial/fondo?imagen=${encodeURIComponent(imagen)}`);
    if (!r.ok) return;
    fondos[imagen] = URL.createObjectURL(await r.blob());
  } catch {
    fondos[imagen] = null;
  }
}

function estiloCaja(c) {
  return {
    left: `${c.x * 100}%`,
    top: `${c.y * 100}%`,
    width: `${c.ancho * 100}%`,
    height: `${c.alto * 100}%`,
  };
}

function cajaPorDefecto(id, indice) {
  const p = proporcion.value;
  if (id === 'qr') return { x: 0.6, y: 0.05, ancho: 0.3, alto: 0.3 / p };
  if (id === 'foto') return { x: 0.1, y: 0.1, ancho: 0.32, alto: 0.32 / p };
  return { x: 0.1, y: Math.min(0.9, 0.5 + indice * 0.07), ancho: 0.75, alto: 0.05 };
}

function alternarCampo(id) {
  const cajas = borrador.value.cajas;
  if (cajas[id]) {
    if (id === 'qr') return;
    delete cajas[id];
    if (campoSel.value === id) campoSel.value = null;
  } else {
    cajas[id] = cajaPorDefecto(id, Object.keys(cajas).length);
    campoSel.value = id;
  }
}

const acotar = (v, min, max) => Math.min(max, Math.max(min, v));
let gesto = null;

function onCajaDown(e, id, modoGesto) {
  e.preventDefault();
  e.stopPropagation();
  campoSel.value = id;
  const r = lienzo.value.getBoundingClientRect();
  e.currentTarget.setPointerCapture(e.pointerId);
  gesto = { id, modo: modoGesto, x0: e.clientX, y0: e.clientY, w: r.width, h: r.height, base: { ...borrador.value.cajas[id] } };
}

function onCajaMove(e) {
  if (!gesto) return;
  const c = borrador.value.cajas[gesto.id];
  const dx = (e.clientX - gesto.x0) / gesto.w;
  const dy = (e.clientY - gesto.y0) / gesto.h;
  const b = gesto.base;
  if (gesto.modo === 'mover') {
    c.x = acotar(b.x + dx, 0, 1 - b.ancho);
    c.y = acotar(b.y + dy, 0, 1 - b.alto);
    return;
  }
  c.ancho = acotar(b.ancho + dx, 0.02, 1 - b.x);
  c.alto = gesto.id === 'qr'
    ? acotar(c.ancho / proporcion.value, 0.02, 1 - b.y)
    : acotar(b.alto + dy, 0.015, 1 - b.y);
  if (gesto.id === 'qr') c.ancho = Math.min(c.ancho, c.alto * proporcion.value);
}

function onCajaUp() {
  if (!gesto) return;
  const c = borrador.value.cajas[gesto.id];
  for (const k of ['x', 'y', 'ancho', 'alto']) c[k] = Math.round(c[k] * 10000) / 10000;
  gesto = null;
}

function fijarNumero(clave, valor) {
  const c = cajaSel.value;
  const v = Number(valor) / 100;
  if (!c || !Number.isFinite(v)) return;
  c[clave] = Math.round(acotar(v, 0, 1) * 10000) / 10000;
  if (campoSel.value === 'qr' && clave === 'ancho') c.alto = Math.round((c.ancho / proporcion.value) * 10000) / 10000;
}

function datosParaGuardar() {
  const b = borrador.value;
  return {
    codigo: b.codigo, etiqueta: b.etiqueta, detalle: b.detalle, imagen: b.imagen,
    requiereFoto: b.requiereFoto, centrado: b.centrado, mayusculas: b.mayusculas,
    fotoCircular: b.fotoCircular, multilinea: b.multilinea,
    colorTexto: b.colorTexto || null, tipoTexto: b.tipoTexto || null,
    cajas: b.cajas, orden: b.orden, predeterminada: b.predeterminada,
    uso: b.uso || 'EXPOSITOR', idEdicion: b.idEdicion ?? null,
    idDependencia: b.uso === 'APOYO' ? b.idDependencia ?? null : null,
    imagenReverso: b.imagenReverso || null,
  };
}

async function guardar() {
  if (!borrador.value || guardando.value) return;
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/plantillas-credencial/${borrador.value.id}`, {
      method: 'PATCH', body: JSON.stringify(datosParaGuardar()),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    const i = plantillas.value.findIndex((p) => p.id === d.id);
    if (i >= 0) plantillas.value[i] = d;
    if (d.predeterminada) plantillas.value.forEach((p) => { if (p.id !== d.id) p.predeterminada = false; });
    elegir(d.id, true);
    toast('Plantilla guardada ✓', 'ok', 1800);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

function descartar() {
  if (seleccionada.value) elegir(seleccionada.value.id, true);
}

async function duplicar() {
  if (!seleccionada.value) return;
  if (modificado.value && !confirm('Se duplicará la versión guardada; los cambios sin guardar no se copian. ¿Continuar?')) return;
  const r = await apiFetch(`/api/app/plantillas-credencial/${seleccionada.value.id}/duplicar`, { method: 'POST' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) { toast(d.mensaje || 'No se pudo duplicar', 'error'); return; }
  plantillas.value.push(d);
  elegir(d.id, true);
  toast('Copia creada', 'ok', 1600);
}

async function eliminar() {
  const p = seleccionada.value;
  if (!p || !confirm(`¿Quitar la plantilla "${p.etiqueta}"? Las credenciales ya impresas no cambian.`)) return;
  const r = await apiFetch(`/api/app/plantillas-credencial/${p.id}`, { method: 'DELETE' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) { toast(d.mensaje || 'No se pudo quitar', 'error'); return; }
  await cargar(null);
}

async function subirImagen(archivo) {
  const fd = new FormData();
  fd.append('archivo', archivo);
  const r = await apiFetch('/api/app/plantillas-credencial/fondo', { method: 'POST', body: fd });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) { toast(d.mensaje || 'No se pudo subir la imagen', 'error'); return null; }
  return d.imagen;
}

async function proporcionDe(archivo) {
  const url = URL.createObjectURL(archivo);
  try {
    const img = new Image();
    await new Promise((ok, mal) => { img.onload = ok; img.onerror = mal; img.src = url; });
    return img.naturalHeight / img.naturalWidth;
  } finally {
    URL.revokeObjectURL(url);
  }
}

async function cambiarFondo(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo || !borrador.value) return;
  const imagen = await subirImagen(archivo);
  if (!imagen) return;
  fondos[imagen] = URL.createObjectURL(archivo);
  borrador.value.imagen = imagen;
  borrador.value.proporcion = await proporcionDe(archivo);
  toast('Fondo cargado. Revisa la posición de los campos y guarda.', 'info');
}

async function cambiarReverso(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo || !borrador.value) return;
  const imagen = await subirImagen(archivo);
  if (!imagen) return;
  fondos[imagen] = URL.createObjectURL(archivo);
  borrador.value.imagenReverso = imagen;
}

const nueva = reactive({ abierta: false, etiqueta: '', archivo: null, creando: false, uso: 'EXPOSITOR' });

async function crear() {
  if (!nueva.etiqueta.trim() || !nueva.archivo) { toast('Pon un nombre y elige la imagen de fondo', 'advertencia'); return; }
  nueva.creando = true;
  try {
    const imagen = await subirImagen(nueva.archivo);
    if (!imagen) return;
    const prop = await proporcionDe(nueva.archivo);
    const codigo = nueva.etiqueta.normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 30);
    const r = await apiFetch('/api/app/plantillas-credencial', {
      method: 'POST',
      body: JSON.stringify({
        codigo, etiqueta: nueva.etiqueta.trim(), imagen, requiereFoto: false, mayusculas: true, uso: nueva.uso,
        cajas: { qr: { x: 0.35, y: 0.05, ancho: 0.3, alto: Math.round((0.3 / prop) * 10000) / 10000 } },
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { toast(d.mensaje || 'No se pudo crear', 'error'); return; }
    fondos[imagen] = URL.createObjectURL(nueva.archivo);
    plantillas.value.push(d);
    Object.assign(nueva, { abierta: false, etiqueta: '', archivo: null, uso: 'EXPOSITOR' });
    elegir(d.id, true);
  } finally {
    nueva.creando = false;
  }
}

const previa = reactive({ abierta: false, cargando: false, formato: 'pdf', url: null, imagen: null, persona: '', personas: [] });
const lienzoPrevia = ref(null);

const personaId = computed(() => previa.personas.find((p) => p.etiqueta === previa.persona)?.id ?? null);

async function abrirPrevia() {
  previa.abierta = true;
  if (!previa.personas.length) {
    try {
      const r = await apiFetch('/api/app/credenciales');
      if (r.ok) {
        previa.personas = (await r.json()).map((c) => ({ id: c.responsableId, etiqueta: `${c.nombre} · ${c.entidad || ''}` }));
      }
    } catch { /* sin lista: se usa el ejemplo */ }
  }
  await generarPrevia();
}

async function generarPrevia() {
  if (!borrador.value) return;
  previa.cargando = true;
  if (previa.imagen) URL.revokeObjectURL(previa.imagen);
  previa.imagen = null;
  try {
    const r = await apiFetch('/api/app/plantillas-credencial/vista-previa', {
      method: 'POST',
      body: JSON.stringify({ plantilla: datosParaGuardar(), responsableId: personaId.value, formato: previa.formato, anchoCm: ANCHO_CM }),
    });
    if (!r.ok) {
      const d = await r.json().catch(() => ({}));
      toast(d.mensaje || 'No se pudo generar la vista previa', 'error');
      return;
    }
    const blob = await r.blob();
    if (previa.formato === 'png') {
      previa.imagen = URL.createObjectURL(blob);
      return;
    }
    const escala = 2.2;
    const hoja = await primeraPaginaEnCanvas(await blob.arrayBuffer(), document.createElement('canvas'), escala);
    const ancho = (ANCHO_CM * 72) / 2.54;
    const alto = ancho * proporcion.value;
    const x0 = (hoja.width / escala - ancho) / 2;
    const y0 = (hoja.height / escala - alto) / 2;
    await nextTick();
    const canvas = lienzoPrevia.value;
    if (!canvas) return;
    canvas.width = Math.round(ancho * escala);
    canvas.height = Math.round(alto * escala);
    canvas.getContext('2d').drawImage(hoja, x0 * escala, y0 * escala, ancho * escala, alto * escala,
      0, 0, canvas.width, canvas.height);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    previa.cargando = false;
  }
}

watch(() => previa.formato, () => { if (previa.abierta) generarPrevia(); });

function onTecla(e) {
  if (!cajaSel.value || ['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target?.tagName)) return;
  const paso = e.shiftKey ? 0.01 : 0.002;
  const c = cajaSel.value;
  const mover = { ArrowLeft: ['x', -paso], ArrowRight: ['x', paso], ArrowUp: ['y', -paso], ArrowDown: ['y', paso] }[e.key];
  if (mover) {
    e.preventDefault();
    const [k, d] = mover;
    const max = k === 'x' ? 1 - c.ancho : 1 - c.alto;
    c[k] = Math.round(acotar(c[k] + d, 0, max) * 10000) / 10000;
  } else if (e.key === 'Escape') {
    campoSel.value = null;
  }
}

function avisar(e) {
  if (!modificado.value) return;
  e.preventDefault();
  e.returnValue = '';
}

onMounted(() => {
  cargarCatalogos();
  cargar();
});
onActivated(() => {
  window.addEventListener('keydown', onTecla);
  window.addEventListener('beforeunload', avisar);
});
onDeactivated(() => {
  window.removeEventListener('keydown', onTecla);
  window.removeEventListener('beforeunload', avisar);
});
onUnmounted(() => {
  window.removeEventListener('keydown', onTecla);
  window.removeEventListener('beforeunload', avisar);
  Object.values(fondos).forEach((u) => u && URL.revokeObjectURL(u));
});
</script>

<template>
  <div class="diseno">
    <aside class="lista card">
      <div class="lista-cab">
        <strong>Plantillas</strong>
        <button class="btn btn-sm btn-primario" @click="nueva.abierta = true">＋ Nueva</button>
      </div>
      <p v-if="cargando" class="muted">Cargando…</p>
      <template v-for="g in grupos" :key="g.uso">
        <h5 class="grupo-tit">{{ g.titulo }}</h5>
        <p v-if="!g.items.length" class="muted chico">{{ g.uso === 'APOYO' ? 'Sin plantillas: se usa el diseño de siempre.' : 'Sin plantillas.' }}</p>
        <ul>
          <li v-for="p in g.items" :key="p.id">
            <button :class="{ on: p.id === selId }" @click="elegir(p.id)">
              <span class="nom">{{ p.etiqueta }}</span>
              <span v-if="p.predeterminada" class="badge badge-ok">por defecto</span>
              <span v-else-if="p.idEdicion" class="badge badge-muted">{{ nombreEdicion(p.idEdicion) || 'actividad' }}</span>
            </button>
          </li>
        </ul>
      </template>
    </aside>

    <section v-if="borrador" class="centro">
      <div class="barra">
        <strong class="titulo">{{ borrador.etiqueta }}</strong>
        <span v-if="modificado" class="pendiente">Cambios sin guardar</span>
        <span class="crecer"></span>
        <button class="btn btn-sm" @click="abrirPrevia">👁 Vista previa</button>
        <button class="btn btn-sm" :disabled="!modificado || guardando" @click="descartar">Descartar</button>
        <button class="btn btn-sm btn-primario" :disabled="!modificado || guardando" @click="guardar">
          {{ guardando ? 'Guardando…' : 'Guardar' }}
        </button>
      </div>
      <div class="escenario" @pointerdown.self="campoSel = null">
        <div ref="lienzo" class="lienzo" :style="{ '--prop': proporcion }">
          <img v-if="fondos[borrador.imagen]" :src="fondos[borrador.imagen]" alt="" draggable="false" />
          <div v-for="(c, id) in borrador.cajas" :key="id" class="caja"
               :class="[`c-${id}`, { sel: campoSel === id, circular: id === 'foto' && borrador.fotoCircular }]"
               :style="estiloCaja(c)"
               @pointerdown="onCajaDown($event, id, 'mover')" @pointermove="onCajaMove"
               @pointerup="onCajaUp" @pointercancel="onCajaUp">
            <span class="rotulo">{{ etiquetaCampo(id) }}</span>
            <span v-if="id !== 'qr' && id !== 'foto'" class="muestra-texto"
                  :style="{ color: borrador.colorTexto || '#111827', justifyContent: borrador.centrado ? 'center' : 'flex-start' }">
              {{ id === 'tipo' && borrador.tipoTexto ? borrador.tipoTexto : CAMPOS.find((x) => x.id === id)?.muestra }}
            </span>
            <span class="asa" @pointerdown="onCajaDown($event, id, 'tamano')" @pointermove="onCajaMove"
                  @pointerup="onCajaUp" @pointercancel="onCajaUp"></span>
          </div>
        </div>
      </div>
    </section>

    <aside v-if="borrador" class="props card">
      <h4>Plantilla</h4>
      <label class="campo"><span>Nombre</span><input v-model="borrador.etiqueta" class="control" maxlength="80" /></label>
      <label class="campo"><span>Descripción</span><input v-model="borrador.detalle" class="control" maxlength="200" /></label>
      <div class="dos">
        <label class="campo"><span>Cargo por defecto</span><input v-model="borrador.tipoTexto" class="control" maxlength="40" placeholder="EXPOSITOR" /></label>
        <label class="campo"><span>Color del texto</span><input :value="borrador.colorTexto || '#000000'" type="color" class="control control-color"
                 @input="borrador.colorTexto = $event.target.value" /></label>
      </div>
      <div class="dos">
        <label class="campo"><span>Uso</span>
          <select v-model="borrador.uso" class="control" :disabled="seleccionada?.predeterminada">
            <option value="EXPOSITOR">Expositores</option>
            <option value="APOYO">Personal de apoyo</option>
          </select>
        </label>
        <label class="campo"><span>Actividad</span>
          <select v-model="borrador.idEdicion" class="control" :disabled="borrador.predeterminada">
            <option :value="null">Todas</option>
            <option v-for="e in ediciones" :key="e.id" :value="e.id">{{ e.nombre }}{{ e.activa ? ' (actual)' : '' }}</option>
          </select>
        </label>
      </div>
      <label v-if="borrador.uso === 'APOYO'" class="campo"><span>Dependencia</span>
        <select v-model="borrador.idDependencia" class="control">
          <option :value="null">Todas las dependencias</option>
          <option v-for="d in dependencias" :key="d.id" :value="d.id">{{ d.nombre }}</option>
        </select>
      </label>
      <label v-for="o in OPCIONES" :key="o.id" class="check"><input v-model="borrador[o.id]" type="checkbox" /> {{ o.txt }}</label>
      <label v-if="borrador.uso !== 'APOYO'" class="check">
        <input v-model="borrador.predeterminada" type="checkbox" :disabled="seleccionada?.predeterminada || borrador.idEdicion != null" /> Plantilla por defecto
      </label>
      <label class="btn btn-sm">Cambiar imagen de fondo
        <input type="file" accept="image/png,image/jpeg" class="oculto" @change="cambiarFondo" />
      </label>
      <div class="reverso">
        <img v-if="borrador.imagenReverso && fondos[borrador.imagenReverso]" :src="fondos[borrador.imagenReverso]" alt="Reverso" />
        <span class="muted chico">{{ borrador.imagenReverso ? 'Reverso propio' : 'Reverso general' }}</span>
        <label class="btn btn-sm">{{ borrador.imagenReverso ? 'Cambiar' : 'Subir reverso' }}
          <input type="file" accept="image/png,image/jpeg" class="oculto" @change="cambiarReverso" />
        </label>
        <button v-if="borrador.imagenReverso" class="btn btn-sm btn-fantasma" @click="borrador.imagenReverso = null">Quitar</button>
      </div>

      <h4>Campos</h4>
      <ul class="campos">
        <li v-for="c in CAMPOS" :key="c.id" :class="{ sel: campoSel === c.id }">
          <label class="check">
            <input type="checkbox" :checked="!!borrador.cajas[c.id]" :disabled="c.obligatorio" @change="alternarCampo(c.id)" />
            <span @click.prevent="borrador.cajas[c.id] ? (campoSel = c.id) : alternarCampo(c.id)">{{ c.txt }}</span>
          </label>
        </li>
      </ul>

      <div v-if="cajaSel" class="medidas">
        <strong>{{ etiquetaCampo(campoSel) }}</strong>
        <div class="cuatro">
          <label>X % <input type="number" step="0.1" :value="(cajaSel.x * 100).toFixed(1)" @change="fijarNumero('x', $event.target.value)" /></label>
          <label>Y % <input type="number" step="0.1" :value="(cajaSel.y * 100).toFixed(1)" @change="fijarNumero('y', $event.target.value)" /></label>
          <label>Ancho % <input type="number" step="0.1" :value="(cajaSel.ancho * 100).toFixed(1)" @change="fijarNumero('ancho', $event.target.value)" /></label>
          <label>Alto % <input type="number" step="0.1" :value="(cajaSel.alto * 100).toFixed(1)" :disabled="campoSel === 'qr'" @change="fijarNumero('alto', $event.target.value)" /></label>
        </div>
        <p class="muted chico">Arrastra la caja para moverla y su esquina para cambiar el tamaño. Flechas: ajuste fino.</p>
      </div>

      <div class="pie-props">
        <button class="btn btn-sm" @click="duplicar">Duplicar</button>
        <button class="btn btn-sm btn-peligro" :disabled="seleccionada?.predeterminada" @click="eliminar">Quitar</button>
      </div>
    </aside>

    <UiModal v-if="nueva.abierta" titulo="Nueva plantilla" @cerrar="!nueva.creando && (nueva.abierta = false)">
      <div class="formulario">
        <label class="campo"><span>Nombre</span><input v-model="nueva.etiqueta" class="control" maxlength="80" placeholder="Credencial staff" /></label>
        <label class="campo"><span>Uso</span>
          <select v-model="nueva.uso" class="control">
            <option value="EXPOSITOR">Expositores</option>
            <option value="APOYO">Personal de apoyo</option>
          </select>
        </label>
        <label class="campo"><span>Imagen de fondo (PNG o JPG)</span>
          <input type="file" accept="image/png,image/jpeg" class="control" @change="nueva.archivo = $event.target.files?.[0] || null" />
        </label>
        <p class="muted chico">Se crea con el QR; después agregas y acomodas los demás campos. También puedes duplicar una plantilla existente.</p>
      </div>
      <template #pie>
        <button class="btn btn-fantasma" :disabled="nueva.creando" @click="nueva.abierta = false">Cancelar</button>
        <button class="btn btn-primario" :disabled="nueva.creando" @click="crear">{{ nueva.creando ? 'Creando…' : 'Crear' }}</button>
      </template>
    </UiModal>

    <UiModal v-if="previa.abierta" titulo="Vista previa" @cerrar="previa.abierta = false">
      <div class="previa-controles">
        <div class="segmentado">
          <button :class="{ on: previa.formato === 'pdf' }" @click="previa.formato = 'pdf'">Impresión (PDF)</button>
          <button :class="{ on: previa.formato === 'png' }" @click="previa.formato = 'png'">Credencial virtual</button>
        </div>
        <input v-model="previa.persona" class="control" list="personas-previa" placeholder="Persona de ejemplo (opcional)"
               @change="generarPrevia" />
        <datalist id="personas-previa">
          <option v-for="p in previa.personas" :key="p.id" :value="p.etiqueta"></option>
        </datalist>
      </div>
      <p v-if="previa.cargando" class="muted">Generando…</p>
      <div class="previa-hoja">
        <canvas v-show="previa.formato === 'pdf'" ref="lienzoPrevia"></canvas>
        <img v-if="previa.formato === 'png' && previa.imagen" :src="previa.imagen" alt="Vista previa" />
      </div>
      <p class="muted chico">Generada por el mismo motor que imprime, con los cambios aún sin guardar.</p>
    </UiModal>
  </div>
</template>

<style scoped>
.diseno { display: grid; grid-template-columns: 220px 1fr 300px; gap: 0.9rem; align-items: start; }
.card { padding: 0.8rem; }
.lista-cab { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; }
.lista ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
.lista li button {
  width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 0.4rem;
  border: 1px solid transparent; background: none; color: var(--text); font: inherit; font-size: 0.88rem;
  padding: 0.45rem 0.55rem; border-radius: var(--radio-sm); cursor: pointer; text-align: left;
}
.lista li button:hover { background: var(--bg); }
.lista li button.on { background: var(--acento-suave); border-color: var(--acento); }
.nom { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.grupo-tit { margin: 0.7rem 0 0.25rem; font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); }
.reverso { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.reverso img { width: 44px; height: 56px; object-fit: cover; border-radius: 4px; border: 1px solid var(--border); }

.centro { display: flex; flex-direction: column; gap: 0.6rem; min-width: 0; }
.barra { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.titulo { font-size: 1rem; }
.pendiente { font-size: 0.8rem; color: var(--tramite); font-weight: 600; }
.crecer { flex: 1; }
.escenario {
  display: flex; justify-content: center; padding: 1rem; border-radius: var(--radio);
  background: var(--bg); border: 1px solid var(--border); overflow: auto;
}
.lienzo {
  position: relative; aspect-ratio: calc(1 / var(--prop));
  width: min(100%, calc((100vh - 230px) / var(--prop)));
  box-shadow: var(--sombra-md); background: #fff; user-select: none; touch-action: none;
}
.lienzo img { position: absolute; inset: 0; width: 100%; height: 100%; display: block; pointer-events: none; }
.caja {
  position: absolute; border: 1.5px dashed rgba(79, 70, 229, 0.9); background: rgba(79, 70, 229, 0.08);
  cursor: move; display: flex; align-items: center; overflow: hidden;
}
.caja.sel { border-style: solid; border-color: var(--acento); background: rgba(79, 70, 229, 0.16); z-index: 2; }
.caja.c-qr { background: repeating-linear-gradient(45deg, rgba(17, 24, 39, 0.25) 0 6px, rgba(255, 255, 255, 0.5) 6px 12px); }
.caja.c-foto { background: rgba(148, 163, 184, 0.45); }
.caja.circular { border-radius: 50%; }
.rotulo {
  position: absolute; top: 0; left: 0; font-size: 10px; line-height: 1; padding: 2px 4px;
  background: var(--acento); color: #fff; border-bottom-right-radius: 4px; pointer-events: none; white-space: nowrap;
}
.caja.circular .rotulo { left: 50%; transform: translateX(-50%); border-radius: 0 0 4px 4px; }
.muestra-texto {
  display: flex; width: 100%; padding: 0 4px; font-weight: 800; font-size: clamp(8px, 1.2vw, 15px);
  white-space: nowrap; overflow: hidden; pointer-events: none;
}
.asa {
  position: absolute; right: -1px; bottom: -1px; width: 12px; height: 12px;
  background: var(--acento); cursor: nwse-resize; border-top-left-radius: 3px;
}

.props { display: flex; flex-direction: column; gap: 0.6rem; max-height: calc(100vh - 140px); overflow-y: auto; }
.props h4 { margin: 0.3rem 0 0; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); }
.dos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; }
.control-color { height: 40px; padding: 2px; }
.check { display: flex; align-items: center; gap: 0.45rem; font-size: 0.88rem; cursor: pointer; }
.oculto { display: none; }
.campos { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.2rem; }
.campos li { padding: 0.2rem 0.35rem; border-radius: 6px; }
.campos li.sel { background: var(--acento-suave); }
.medidas { display: flex; flex-direction: column; gap: 0.4rem; border-top: 1px solid var(--border); padding-top: 0.6rem; }
.cuatro { display: grid; grid-template-columns: 1fr 1fr; gap: 0.4rem; }
.cuatro label { display: flex; flex-direction: column; gap: 0.15rem; font-size: 0.76rem; color: var(--muted); }
.cuatro input {
  padding: 0.3rem 0.4rem; border: 1px solid var(--border); border-radius: 6px;
  background: var(--panel); color: var(--text); font: inherit; font-size: 0.85rem;
}
.chico { font-size: 0.8rem; margin: 0; }
.pie-props { display: flex; gap: 0.5rem; border-top: 1px solid var(--border); padding-top: 0.6rem; }
.formulario { display: flex; flex-direction: column; gap: 0.8rem; }

.previa-controles { display: flex; flex-direction: column; gap: 0.6rem; margin-bottom: 0.7rem; }
.segmentado { display: inline-flex; padding: 2px; border-radius: 9px; background: var(--bg); border: 1px solid var(--border); align-self: flex-start; }
.segmentado button {
  border: none; background: transparent; color: var(--text); font: inherit; font-size: 0.85rem; font-weight: 600;
  padding: 0.3rem 0.8rem; border-radius: 7px; cursor: pointer;
}
.segmentado button.on { background: var(--acento); color: var(--acento-texto); }
.previa-hoja { display: flex; justify-content: center; background: var(--bg); border-radius: var(--radio-sm); padding: 0.6rem; max-height: 65vh; overflow: auto; }
.previa-hoja canvas, .previa-hoja img { max-width: 100%; height: auto; box-shadow: var(--sombra-md); }

@media (max-width: 1100px) {
  .diseno { grid-template-columns: 1fr; }
  .props { max-height: none; }
  .lienzo { width: min(100%, calc(70vh / var(--prop))); }
}
</style>
