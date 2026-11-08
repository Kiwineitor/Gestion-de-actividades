<script setup>
import { confirmar, pedirTexto } from '../ui/alerta';
import { usePermisosStore } from '../stores/permisos';
import { ref, reactive, computed, watch, nextTick, onMounted, onActivated, onDeactivated, onUnmounted, defineAsyncComponent } from 'vue';
import { aModulo } from '../modulos';
import { useRoute, useRouter } from 'vue-router';
import { primeraPaginaEnCanvas } from '../ui/pdf';
import PanZoom from '../components/PanZoom.vue';
import RotuloPlano from '../components/RotuloPlano.vue';
import ModalFondo from '../components/ModalFondo.vue';
import ModalForma from '../components/ModalForma.vue';
import CambiarPlano from '../components/CambiarPlano.vue';
import CategoriaRapida from '../components/CategoriaRapida.vue';
import NumeroPaso from '../components/NumeroPaso.vue';
import { nombreEspacio } from '../ui/espacios';
import { useActividadStore } from '../stores/actividad';
import { imagenPlanoFeria } from '../ui/imagenPlano';
import { guardarBlob } from '../ui/descargas';
import { apiFetch } from '../api';
import { apiDeFeria, editorDeFeria } from '../ui/editorFeria';
import { toast } from '../ui/toast';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { usePlanoStore } from '../stores/plano';
import { usePlanoTextosStore } from '../stores/planoTextos.js';
import {
  ETIQUETA_ESTADO, anchoParaTocar, estiloPin, numeracionCompacta, numerosVisibles,
  ordenLectura, tamanoDe, medidaLegible,
} from '../mapa';

const ESCALA_MIN = 0.2;
const ESCALA_MAX = 6;
const CLIC_MAXIMO = 0.008;
const PASO_REJILLA = 0.01;
const UMBRAL_GUIA_PX = 5;
const GIRO_PASO = 15;
const ESTADOS = [
  { id: 'L', clase: 'libre' },
  { id: 'T', clase: 'tramite' },
  { id: 'O', clase: 'ocupado' },
  { id: 'X', clase: 'bloqueado' },
];

const auth = useAuthStore();
const router = useRouter();
const actividadEnCurso = useActividadStore();
const rutaInicial = useRoute();
const edicion = Number(rutaInicial.query.edicion) || null;
async function seguirAsistente() {
  if (dirty.value.size) await guardar();
  if (dirty.value.size) return;
  router.push(aModulo('actividad', { id: edicion, paso: 'credenciales' }));
}
const desdeAsistente = computed(() => rutaInicial.query.asistente === '1' && Boolean(edicion));
const feria = ref(null);
const editor = edicion ? editorDeFeria(edicion) : null;
const tienda = editor?.puestos ?? usePuestosStore();
const planoTienda = editor?.plano ?? usePlanoStore();
const textosTienda = editor?.textos ?? usePlanoTextosStore();
const consultarPlano = (ruta, opciones) => apiDeFeria(ruta, edicion, opciones);

const puestos = computed(() => tienda.puestos);
const permisos = usePermisosStore();
const puedeDisenar = computed(() => auth.puedeEditarPlano || permisos.puedeVer('editor'));

const modo = ref(edicion ? 'diseno' : 'gestion');
const herramienta = ref(null);
const enDiseno = computed(() => modo.value === 'diseno' && puedeDisenar.value);

const rejilla = ref(leerPreferencia('editor.rejilla') === '1');
watch(rejilla, (v) => guardarPreferencia('editor.rejilla', v ? '1' : '0'));
const iman = ref(leerPreferencia('editor.iman') === '1');
watch(iman, (v) => guardarPreferencia('editor.iman', v ? '1' : '0'));
const guiasActivas = ref(leerPreferencia('editor.guias') !== '0');
watch(guiasActivas, (v) => guardarPreferencia('editor.guias', v ? '1' : '0'));
const textoSigueZona = ref(leerPreferencia('editor.textoZona') !== '0');
watch(textoSigueZona, (v) => guardarPreferencia('editor.textoZona', v ? '1' : '0'));
const panelCategorias = ref(leerPreferencia('editor.categorias') !== '0');
watch(panelCategorias, (v) => guardarPreferencia('editor.categorias', v ? '1' : '0'));

function leerPreferencia(clave) {
  try { return localStorage.getItem(clave); } catch { return null; }
}
function guardarPreferencia(clave, valor) {
  try { localStorage.setItem(clave, valor); } catch { }
}

const catSel = ref(null);
const porLinea = ref(8);
const seleccion = ref(new Set());
const principalId = ref(null);
const dirty = ref(new Set());
const ocupado = ref(false);
const guardadoReciente = ref(false);
const menuMas = ref(false);
const numeracion = reactive({ inicio: 1, orden: 'filas' });

const lienzo = ref(null);
const guiaEl = ref(null);
const cajaEl = ref(null);
const entradaFoto = ref(null);
const entradaTexto = ref(null);

let quitarGuardia = null;

const buscar = (id) => puestos.value.find((p) => p.id === id);
const geom = (p) => ({
  id: p.id, mapaX: p.mapaX, mapaY: p.mapaY,
  mapaEscala: p.mapaEscala ?? 1, mapaRotacion: p.mapaRotacion ?? 0,
});
const aRejilla = (v) => (iman.value ? Math.round(v / PASO_REJILLA) * PASO_REJILLA : v);
const guiasV = ref([]);
const guiasH = ref([]);

function umbralesGuia() {
  const r = lienzo.value?.getBoundingClientRect();
  return r?.width ? { ux: UMBRAL_GUIA_PX / r.width, uy: UMBRAL_GUIA_PX / r.height } : { ux: 0.004, uy: 0.004 };
}

function candidatosGuia(pinesFuera = new Set(), textoFuera = null) {
  const xs = [0.5];
  const ys = [0.5];
  for (const p of puestos.value) {
    if (p.mapaX == null || pinesFuera.has(p.id)) continue;
    xs.push(p.mapaX);
    ys.push(p.mapaY);
  }
  for (const t of textosTienda.textos) {
    if (t.id === textoFuera) continue;
    const cx = t.mapaX ?? 0.5;
    const cy = t.mapaY ?? 0.5;
    xs.push(cx);
    ys.push(cy);
    if (t.fondo && !t.rotacion) {
      const w = (t.ancho ?? 0.16) / 2;
      const h = (t.alto ?? 0.08) / 2;
      xs.push(cx - w, cx + w);
      ys.push(cy - h, cy + h);
    }
  }
  return { xs, ys };
}

function mejorAjuste(valores, candidatos, umbral) {
  let mejor = null;
  for (const v of valores) {
    for (const c of candidatos) {
      const d = c - v;
      if (Math.abs(d) <= umbral && (!mejor || Math.abs(d) < Math.abs(mejor.delta))) mejor = { delta: d, linea: c };
    }
  }
  return mejor;
}

function limpiarGuias() {
  if (guiasV.value.length) guiasV.value = [];
  if (guiasH.value.length) guiasH.value = [];
  if (medidor.value) medidor.value = null;
}

const medidor = ref(null);
const desplazamiento = ref(null);

function posDe(p) {
  const base = conPrevia(p);
  const d = desplazamiento.value;
  if (!d || !d.ids.has(p.id)) return base;
  return { ...base, mapaX: acotar(aRejilla(p.mapaX + d.dx)), mapaY: acotar(aRejilla(p.mapaY + d.dy)) };
}
const arrastrandoPin = computed(() => Boolean(medidor.value));
const referencias = computed(() => new Set(medidor.value?.referencias || []));
const enPct = (d) => `${(Math.abs(d) * 100).toFixed(1).replace('.', ',')} %`;

function ajustarPin(a, xBruto, yBruto) {
  let x = xBruto;
  let y = yBruto;
  const sx = a.fijoX != null && Math.abs(x - a.fijoX) <= a.ux * 1.8 ? { delta: a.fijoX - x, linea: a.fijoX } : mejorAjuste([x], a.cand.xs, a.ux);
  const sy = a.fijoY != null && Math.abs(y - a.fijoY) <= a.uy * 1.8 ? { delta: a.fijoY - y, linea: a.fijoY } : mejorAjuste([y], a.cand.ys, a.uy);
  if (sx) x += sx.delta;
  if (sy) y += sy.delta;
  a.fijoX = sx?.linea ?? null;
  a.fijoY = sy?.linea ?? null;

  const enFila = a.otros.filter((o) => Math.abs(o.y - y) <= a.uy * 0.8).sort((p, q) => p.x - q.x);
  const enColumna = a.otros.filter((o) => Math.abs(o.x - x) <= a.ux * 0.8).sort((p, q) => p.y - q.y);
  const izquierda = enFila.filter((o) => o.x < x);
  const derecha = enFila.filter((o) => o.x > x);
  const arriba = enColumna.filter((o) => o.y < y);
  const abajo = enColumna.filter((o) => o.y > y);

  let igualX = null;
  let igualY = null;
  if (!sx && izquierda.length >= 2) {
    const [b, c] = izquierda.slice(-2);
    const objetivo = c.x + (c.x - b.x);
    if (Math.abs(objetivo - x) <= a.ux) { x = objetivo; igualX = [b, c]; }
  }
  if (!sx && !igualX && derecha.length >= 2) {
    const [c, b] = derecha.slice(0, 2);
    const objetivo = c.x - (b.x - c.x);
    if (Math.abs(objetivo - x) <= a.ux) { x = objetivo; igualX = [c, b]; }
  }
  if (!sy && arriba.length >= 2) {
    const [b, c] = arriba.slice(-2);
    const objetivo = c.y + (c.y - b.y);
    if (Math.abs(objetivo - y) <= a.uy) { y = objetivo; igualY = [b, c]; }
  }
  if (!sy && !igualY && abajo.length >= 2) {
    const [c, b] = abajo.slice(0, 2);
    const objetivo = c.y - (b.y - c.y);
    if (Math.abs(objetivo - y) <= a.uy) { y = objetivo; igualY = [c, b]; }
  }

  const textos = [];
  const refs = [];
  const mismaAltura = sy ? a.otros.find((o) => Math.abs(o.y - y) < 1e-6) : null;
  const mismaColumna = sx ? a.otros.find((o) => Math.abs(o.x - x) < 1e-6) : null;
  if (mismaAltura) { textos.push(`↔ Misma altura que ${mismaAltura.etq}`); refs.push(mismaAltura.id); }
  else if (sy) textos.push('↔ Alineado en horizontal');
  if (mismaColumna) { textos.push(`↕ Misma columna que ${mismaColumna.etq}`); refs.push(mismaColumna.id); }
  else if (sx) textos.push('↕ Alineado en vertical');
  if (igualX) { textos.push(`⇔ Igual separación que ${igualX[0].etq} y ${igualX[1].etq}`); refs.push(igualX[0].id, igualX[1].id); }
  if (igualY) { textos.push(`⇕ Igual separación que ${igualY[0].etq} y ${igualY[1].etq}`); refs.push(igualY[0].id, igualY[1].id); }
  const vi = enFila.filter((o) => o.x < x).pop();
  const vd = enFila.find((o) => o.x > x);
  if (vi) { textos.push(`← ${enPct(x - vi.x)} de ${vi.etq}`); refs.push(vi.id); }
  if (vd) { textos.push(`→ ${enPct(vd.x - x)} de ${vd.etq}`); refs.push(vd.id); }
  if (!textos.length) textos.push(`Libre · X ${enPct(x)} · Y ${enPct(y)}`);

  return {
    x, y,
    v: sx ? [sx.linea] : igualX ? [x] : [],
    h: sy ? [sy.linea] : igualY ? [y] : [],
    medidor: { x, y, textos, referencias: refs, alineado: Boolean(sx || sy || igualX || igualY) },
  };
}
const acotar = (v) => Math.min(1, Math.max(0, v));

const historial = ref([]);
const indice = ref(-1);
const puedeDeshacer = computed(() => indice.value >= 0);
const puedeRehacer = computed(() => indice.value < historial.value.length - 1);

function marcarSucio(id) {
  const s = new Set(dirty.value);
  s.add(id);
  dirty.value = s;
  guardadoReciente.value = false;
}

function aplicarGeom(g) {
  const p = buscar(g.id);
  if (!p) return;
  p.mapaX = g.mapaX;
  p.mapaY = g.mapaY;
  p.mapaEscala = g.mapaEscala;
  p.mapaRotacion = g.mapaRotacion;
  marcarSucio(g.id);
}

function registrar(antes) {
  const despues = antes.map((g) => buscar(g.id)).filter(Boolean).map(geom);
  historial.value = historial.value.slice(0, indice.value + 1);
  historial.value.push({ antes, despues });
  indice.value = historial.value.length - 1;
}

function conHistoria(ids, mutar) {
  const antes = ids.map(buscar).filter(Boolean).map(geom);
  if (!antes.length) return;
  mutar();
  registrar(antes);
}

function deshacer() {
  if (!puedeDeshacer.value) return;
  historial.value[indice.value].antes.forEach(aplicarGeom);
  indice.value--;
}

function rehacer() {
  if (!puedeRehacer.value) return;
  indice.value++;
  historial.value[indice.value].despues.forEach(aplicarGeom);
}

const categorias = computed(() => {
  const m = new Map();
  for (const p of puestos.value) {
    if (!m.has(p.categoriaId)) {
      m.set(p.categoriaId, {
        id: p.categoriaId, nombre: p.categoria, color: p.color, forma: p.forma || 'cuadrado',
        total: 0, puestas: 0,
      });
    }
    const c = m.get(p.categoriaId);
    c.total++;
    if (p.mapaX != null) c.puestas++;
  }
  return [...m.values()].sort((a, b) => (a.nombre || '').localeCompare(b.nombre || ''));
});
const activa = computed(() => categorias.value.find((c) => c.id === catSel.value) || null);
const porColocar = computed(() =>
  puestos.value
    .filter((p) => p.categoriaId === catSel.value && p.mapaX == null)
    .sort((a, b) => (parseInt(a.codigo) || 0) - (parseInt(b.codigo) || 0)),
);
const colocadas = computed(() => puestos.value.filter((p) => p.mapaX != null));
const topeZoom = computed(() => anchoParaTocar(colocadas.value));
const seleccionadas = computed(() => [...seleccion.value].map(buscar).filter(Boolean));
const principal = computed(() => {
  if (!seleccion.value.size) return null;
  const id = seleccion.value.has(principalId.value) ? principalId.value : [...seleccion.value][0];
  return buscar(id) || null;
});
const fichaPuesto = computed(() => (seleccion.value.size === 1 ? principal.value : null));

const pagados = ref(new Set());
let temporizadorPagados = null;

async function cargarPagados() {
  if (!puedeDisenar.value) return;
  try {
    const r = await apiFetch('/api/app/puestos/pagados');
    if (r.ok) pagados.value = new Set(await r.json());
  } catch {
    pagados.value = new Set(pagados.value);
  }
}

watch(
  () => puestos.value.reduce((n, p) => n + (p.estado === 'O' ? 1 : 0), 0),
  () => {
    clearTimeout(temporizadorPagados);
    temporizadorPagados = setTimeout(cargarPagados, 800);
  },
);

async function cambiarCategoria(evento) {
  const p = fichaPuesto.value;
  const categoriaId = Number(evento.target.value);
  if (!p || !categoriaId || categoriaId === p.categoriaId) return;
  const destino = categorias.value.find((c) => c.id === categoriaId);
  if (!(await confirmar(`¿Pasar el puesto ${p.codigo} a ${destino?.nombre ?? 'otra categoría'}? Toma su color, forma y precio.`, { titulo: 'Confirmar', aceptar: 'Sí, continuar' }))) {
    evento.target.value = p.categoriaId;
    return;
  }
  const r = await apiFetch(`/api/app/puestos/${p.id}/categoria`, { method: 'PATCH', body: JSON.stringify({ categoriaId }) });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) {
    evento.target.value = p.categoriaId;
    toast(d.mensaje || 'No se pudo cambiar la categoría', 'error');
    return;
  }
  toast('Categoría cambiada ✓', 'ok', 1600);
}

const Categorias = defineAsyncComponent(() => import('./Categorias.vue'));
const catRapida = ref(null);
const previa = ref(null);
const avisoColocar = ref(null);
const unoEspacio = computed(() => (feria.value?.tipoEspacio ? nombreEspacio(feria.value) : 'puesto'));
const variosEspacio = computed(() => (feria.value?.tipoEspacio ? nombreEspacio(feria.value, true) : 'puestos'));

function conPrevia(p) {
  const v = previa.value;
  if (!v || v.categoriaId == null || v.categoriaId !== p.categoriaId) return p;
  return { ...p, color: v.color, forma: v.forma, tamanoMapa: v.tamanoMapa, mapaProporcion: v.mapaProporcion };
}

const fantasmas = computed(() => {
  const v = previa.value;
  if (!v || v.categoriaId != null) return [];
  const n = 4;
  const separacion = v.tamanoMapa * 1.7;
  return Array.from({ length: n }, (_, i) => ({
    id: `fantasma-${i}`, mapaX: 0.5 + (i - (n - 1) / 2) * separacion, mapaY: 0.5, tamanoMapa: v.tamanoMapa,
    mapaProporcion: v.mapaProporcion, mapaEscala: 1, mapaRotacion: 0, forma: v.forma, color: v.color, codigo: String(i + 1),
  }));
});

const faltanColocar = computed(() => (avisoColocar.value && catSel.value === avisoColocar.value.id ? porColocar.value.length : 0));

function abrirCategoria(id = null) {
  limpiarSeleccion();
  catRapida.value = { id };
}

function cerrarCategoriaRapida() {
  catRapida.value = null;
  previa.value = null;
}

async function categoriaGuardada(c) {
  catRapida.value = null;
  previa.value = null;
  await tienda.recargar?.();
  if (c.nueva) {
    catSel.value = c.id;
    herramienta.value = 'colocar';
    panelCategorias.value = true;
    avisoColocar.value = { id: c.id, nombre: c.nombre };
  } else {
    toast(`«${c.nombre}» actualizada ✓`, 'ok', 1800);
  }
}

function elegirCategoria(id) {
  catSel.value = id;
  herramienta.value = 'colocar';
}
const modalFondo = ref(false);
const cajonCategorias = ref(false);
const puedeCategorias = computed(() => auth.puedeEditarPlano || permisos.puedeVer('categorias'));

async function cerrarCategorias() {
  cajonCategorias.value = false;
  if (!edicion) await tienda.recargar();
}
const modalForma = ref(false);
const conFondoPropio = computed(() => Boolean(planoTienda.src && planoTienda.plano.propio && !planoTienda.plano.sinFondo));
const ayudaAbierta = ref(false);
const generandoImagen = ref(false);
const CLAVE_AYUDA = 'ayuda.editor-feria.vista';
const textoSelId = ref(null);
const textoSel = computed(() => textosTienda.textos.find((t) => t.id === textoSelId.value) || null);

const ayuda = computed(() => {
  if (!enDiseno.value) return 'Arrastra el mapa para moverte · clic en un puesto para ver sus datos';
  if (herramienta.value === 'texto') return 'Haz clic en el mapa para agregar texto';
  if (herramienta.value === 'zona') return 'Haz clic en el mapa para agregar una zona con recuadro (ej. Escenario, Patio de comidas)';
  if (textoSel.value?.fondo) return 'Arrastra para mover · esquina blanca: tamaño (Shift mantiene la proporción) · círculo de arriba: girar · flechas: mover fino · Ctrl+D duplica';
  if (textoSel.value) return 'Arrastra para mover · círculo de arriba: girar · flechas: mover fino · Ctrl+D duplica';
  if (herramienta.value === 'colocar') {
    return activa.value
      ? `Clic coloca un puesto de ${activa.value.nombre}; arrastra para colocar una fila`
      : 'Elige una categoría para colocar puestos';
  }
  if (herramienta.value === 'seleccionar') return 'Arrastra para seleccionar varios puestos';
  return 'Arrastra para mover: aparecen guías al alinear (Alt = sin guías) · Shift + arrastre selecciona varios · flechas: mover fino';
});

watch(modo, () => {
  herramienta.value = null;
  menuMas.value = false;
  textoSelId.value = null;
});
watch(herramienta, () => { menuMas.value = false; });

function limpiarSeleccion() {
  seleccion.value = new Set();
  principalId.value = null;
  textoSelId.value = null;
  menuMas.value = false;
}

function elegirHerramienta(h) {
  herramienta.value = herramienta.value === h ? null : h;
  if (h === 'colocar' && catSel.value == null && categorias.value.length) {
    catSel.value = categorias.value[0].id;
    panelCategorias.value = true;
  }
}

function norm(e) {
  const r = lienzo.value.getBoundingClientRect();
  return { x: (e.clientX - r.left) / r.width, y: (e.clientY - r.top) / r.height };
}

const caja = { activo: false, x0: 0, y0: 0, x1: 0, y1: 0 };
const linea = { activo: false, x0: 0, y0: 0, x1: 0, y1: 0 };
const cajaActiva = ref(false);
const lineaActiva = ref(false);
const pinEls = new Map();
let arrastre = null;
let arrastreTexto = null;
let redimension = null;
let redimZona = null;
let toqueFondo = null;

function capturar(e) {
  lienzo.value?.setPointerCapture(e.pointerId);
}

function pintarGuia() {
  const el = guiaEl.value;
  if (!el) return;
  el.style.left = `${linea.x0 * 100}%`;
  el.style.top = `${linea.y0 * 100}%`;
  el.style.width = `${Math.hypot(linea.x1 - linea.x0, linea.y1 - linea.y0) * 100}%`;
  el.style.transform = `rotate(${Math.atan2(linea.y1 - linea.y0, linea.x1 - linea.x0)}rad)`;
}

function pintarCaja() {
  const el = cajaEl.value;
  if (!el) return;
  el.style.left = `${Math.min(caja.x0, caja.x1) * 100}%`;
  el.style.top = `${Math.min(caja.y0, caja.y1) * 100}%`;
  el.style.width = `${Math.abs(caja.x1 - caja.x0) * 100}%`;
  el.style.height = `${Math.abs(caja.y1 - caja.y0) * 100}%`;
}

function onDown(e) {
  if (e.pointerType === 'mouse' && e.button !== 0) return;
  const p = norm(e);
  if (enDiseno.value && (herramienta.value === 'texto' || herramienta.value === 'zona')) {
    e.stopPropagation();
    const zona = herramienta.value === 'zona';
    herramienta.value = null;
    crearTexto(p, zona);
    return;
  }
  if (enDiseno.value && herramienta.value === 'colocar') {
    e.stopPropagation();
    if (catSel.value == null) {
      panelCategorias.value = true;
      toast('Elige una categoría para colocar puestos.', 'info');
      return;
    }
    capturar(e);
    linea.activo = true;
    lineaActiva.value = true;
    linea.x0 = linea.x1 = p.x;
    linea.y0 = linea.y1 = p.y;
    nextTick(pintarGuia);
    return;
  }
  if (enDiseno.value && (herramienta.value === 'seleccionar' || e.shiftKey)) {
    e.stopPropagation();
    capturar(e);
    caja.activo = true;
    cajaActiva.value = true;
    caja.x0 = caja.x1 = p.x;
    caja.y0 = caja.y1 = p.y;
    nextTick(pintarCaja);
    return;
  }
  toqueFondo = { x: e.clientX, y: e.clientY };
  window.addEventListener('pointerup', alSoltarFondo, { once: true });
}

function alSoltarFondo(e) {
  if (toqueFondo && Math.hypot(e.clientX - toqueFondo.x, e.clientY - toqueFondo.y) < 5) limpiarSeleccion();
  toqueFondo = null;
}

function onPinDown(e, p) {
  if (enDiseno.value && (herramienta.value === 'colocar' || herramienta.value === 'texto' || herramienta.value === 'zona')) return;
  e.stopPropagation();
  textoSelId.value = null;
  menuMas.value = false;
  if (!enDiseno.value) {
    seleccion.value = new Set([p.id]);
    principalId.value = p.id;
    return;
  }
  capturar(e);
  if (e.shiftKey || e.ctrlKey || e.metaKey) alternar(p.id);
  else if (!seleccion.value.has(p.id)) seleccion.value = new Set([p.id]);
  principalId.value = p.id;
  const q = norm(e);
  arrastre = {
    antes: seleccionadas.value.map(geom), x0: q.x, y0: q.y, movido: false, dx: 0, dy: 0,
    ids: new Set(seleccion.value), ref: geom(p), cand: candidatosGuia(new Set(seleccion.value)), ...umbralesGuia(),
    otros: puestos.value.filter((o) => o.mapaX != null && !seleccion.value.has(o.id)).map((o) => ({ id: o.id, x: o.mapaX, y: o.mapaY, codigo: o.codigo, etq: `${o.categoria || ''} ${o.codigo}`.trim() })),
    fijoX: null, fijoY: null,
  };
}

function onAsaDown(e) {
  const p = principal.value;
  if (!p) return;
  e.stopPropagation();
  capturar(e);
  const q = norm(e);
  const aspecto = planoTienda.aspecto || 1;
  const d0 = Math.hypot(q.x - p.mapaX, (q.y - p.mapaY) * aspecto);
  redimension = { antes: seleccionadas.value.map(geom), cx: p.mapaX, cy: p.mapaY, d0: Math.max(d0, 0.0005), aspecto };
}

function onTextoDown(e, t) {
  if (!enDiseno.value || herramienta.value === 'colocar') return;
  e.stopPropagation();
  capturar(e);
  seleccion.value = new Set();
  principalId.value = null;
  textoSelId.value = t.id;
  const q = norm(e);
  arrastreTexto = {
    id: t.id, x0: q.x, y0: q.y, base: { x: t.mapaX ?? 0.5, y: t.mapaY ?? 0.5 }, movido: false,
    medio: t.fondo && !t.rotacion ? { w: (t.ancho ?? 0.16) / 2, h: (t.alto ?? 0.08) / 2 } : { w: 0, h: 0 },
    cand: candidatosGuia(new Set(), t.id), ...umbralesGuia(),
  };
}

function onAsaZonaDown(e) {
  const t = textoSel.value;
  if (!t?.fondo) return;
  e.stopPropagation();
  capturar(e);
  redimZona = {
    id: t.id, cx: t.mapaX ?? 0.5, cy: t.mapaY ?? 0.5, rot: Number(t.rotacion || 0), aspecto: planoTienda.aspecto || 1,
    ancho0: t.ancho ?? 0.16, alto0: t.alto ?? 0.08, tamano0: t.tamano ?? 0.02,
  };
}

let giroTexto = null;

function onAsaGiroDown(e) {
  const t = textoSel.value;
  if (!t) return;
  e.stopPropagation();
  capturar(e);
  giroTexto = { id: t.id, cx: t.mapaX ?? 0.5, cy: t.mapaY ?? 0.5, aspecto: planoTienda.aspecto || 1 };
}

function onMove(e) {
  if (giroTexto) {
    const q = norm(e);
    const t = textosTienda.textos.find((x) => x.id === giroTexto.id);
    if (t) {
      const vx = q.x - giroTexto.cx;
      const vy = (q.y - giroTexto.cy) * giroTexto.aspecto;
      let grados = (Math.atan2(vx, -vy) * 180) / Math.PI;
      if (e.shiftKey) grados = Math.round(grados / GIRO_PASO) * GIRO_PASO;
      else if (!e.altKey) {
        const cercano = Math.round(grados / GIRO_PASO) * GIRO_PASO;
        if (Math.abs(cercano - grados) < 4) grados = cercano;
      }
      t.rotacion = Math.round(((grados + 540) % 360) - 180);
    }
    return;
  }
  if (redimZona) {
    const q = norm(e);
    const t = textosTienda.textos.find((x) => x.id === redimZona.id);
    if (t) {
      const r = redimZona;
      const vx = q.x - r.cx;
      const vy = (q.y - r.cy) * r.aspecto;
      const a = (-r.rot * Math.PI) / 180;
      const lx = vx * Math.cos(a) - vy * Math.sin(a);
      const ly = vx * Math.sin(a) + vy * Math.cos(a);
      let ancho = Math.abs(lx) * 2;
      let alto = (Math.abs(ly) * 2) / r.aspecto;
      if (e.shiftKey) {
        const f = Math.max(ancho / r.ancho0, alto / r.alto0);
        ancho = r.ancho0 * f;
        alto = r.alto0 * f;
      }
      t.ancho = Math.min(1, Math.max(0.02, aRejilla(ancho)));
      t.alto = Math.min(1, Math.max(0.02, aRejilla(alto)));
      if (textoSigueZona.value) {
        const f = Math.min(t.ancho / r.ancho0, t.alto / r.alto0);
        t.tamano = Math.min(0.2, Math.max(0.004, r.tamano0 * f));
      }
    }
    return;
  }
  if (arrastreTexto) {
    const q = norm(e);
    const dx = q.x - arrastreTexto.x0;
    const dy = q.y - arrastreTexto.y0;
    if (Math.abs(dx) + Math.abs(dy) > 0.0005) arrastreTexto.movido = true;
    const t = textosTienda.textos.find((x) => x.id === arrastreTexto.id);
    if (t) {
      let nx = arrastreTexto.base.x + dx;
      let ny = arrastreTexto.base.y + dy;
      if (guiasActivas.value && !iman.value && !e.altKey) {
        const { w, h } = arrastreTexto.medio;
        const sx = mejorAjuste([nx, nx - w, nx + w], arrastreTexto.cand.xs, arrastreTexto.ux);
        const sy = mejorAjuste([ny, ny - h, ny + h], arrastreTexto.cand.ys, arrastreTexto.uy);
        if (sx) nx += sx.delta;
        if (sy) ny += sy.delta;
        guiasV.value = sx ? [sx.linea] : [];
        guiasH.value = sy ? [sy.linea] : [];
      } else limpiarGuias();
      t.mapaX = acotar(aRejilla(nx));
      t.mapaY = acotar(aRejilla(ny));
    }
    return;
  }
  if (redimension) {
    const q = norm(e);
    const d = Math.hypot(q.x - redimension.cx, (q.y - redimension.cy) * redimension.aspecto);
    const factor = d / redimension.d0;
    for (const g of redimension.antes) {
      const p = buscar(g.id);
      if (!p) continue;
      p.mapaEscala = Math.min(ESCALA_MAX, Math.max(ESCALA_MIN, g.mapaEscala * factor));
    }
    return;
  }
  if (linea.activo) {
    const p = norm(e);
    linea.x1 = p.x;
    linea.y1 = p.y;
    pintarGuia();
    return;
  }
  if (caja.activo) {
    const p = norm(e);
    caja.x1 = p.x;
    caja.y1 = p.y;
    pintarCaja();
    return;
  }
  if (arrastre) {
    const q = norm(e);
    let dx = q.x - arrastre.x0;
    let dy = q.y - arrastre.y0;
    if (Math.abs(dx) + Math.abs(dy) > 0.0005) arrastre.movido = true;
    if (arrastre.ref?.mapaX != null && arrastre.movido) {
      if (guiasActivas.value && !iman.value && !e.altKey) {
        const ajuste = ajustarPin(arrastre, arrastre.ref.mapaX + dx, arrastre.ref.mapaY + dy);
        dx = ajuste.x - arrastre.ref.mapaX;
        dy = ajuste.y - arrastre.ref.mapaY;
        guiasV.value = ajuste.v;
        guiasH.value = ajuste.h;
        medidor.value = ajuste.medidor;
      } else {
        if (guiasV.value.length) guiasV.value = [];
        if (guiasH.value.length) guiasH.value = [];
        const x = acotar(aRejilla(arrastre.ref.mapaX + dx));
        const y = acotar(aRejilla(arrastre.ref.mapaY + dy));
        medidor.value = { x, y, textos: [`${iman.value ? '🧲 Imán · ' : 'Libre · '}X ${enPct(x)} · Y ${enPct(y)}`], referencias: [], alineado: false };
      }
    }
    arrastre.dx = dx;
    arrastre.dy = dy;
    if (arrastre.movido) desplazamiento.value = { ids: arrastre.ids, dx, dy };
  }
}

function onUp() {
  limpiarGuias();
  if (giroTexto) {
    const { id } = giroTexto;
    giroTexto = null;
    const t = textosTienda.textos.find((x) => x.id === id);
    if (t) editarTexto({ rotacion: t.rotacion }, id);
    return;
  }
  if (redimZona) {
    const { id } = redimZona;
    redimZona = null;
    const t = textosTienda.textos.find((x) => x.id === id);
    if (t) editarTexto({ ancho: t.ancho, alto: t.alto, tamano: t.tamano }, id);
    return;
  }
  if (arrastreTexto) {
    const { id, movido } = arrastreTexto;
    arrastreTexto = null;
    if (movido) {
      const t = textosTienda.textos.find((x) => x.id === id);
      if (t) editarTexto({ mapaX: t.mapaX, mapaY: t.mapaY }, id);
    }
    return;
  }
  if (redimension) {
    const antes = redimension.antes;
    redimension = null;
    const s = new Set(dirty.value);
    antes.forEach((g) => s.add(g.id));
    dirty.value = s;
    guardadoReciente.value = false;
    registrar(antes);
    return;
  }
  if (arrastre) {
    if (arrastre.movido) {
      const s = new Set(dirty.value);
      for (const g of arrastre.antes) {
        const p = buscar(g.id);
        if (!p) continue;
        p.mapaX = acotar(aRejilla(g.mapaX + arrastre.dx));
        p.mapaY = acotar(aRejilla(g.mapaY + arrastre.dy));
        s.add(g.id);
      }
      dirty.value = s;
      guardadoReciente.value = false;
      registrar(arrastre.antes);
    }
    arrastre = null;
    desplazamiento.value = null;
    return;
  }
  if (caja.activo) {
    caja.activo = false;
    cajaActiva.value = false;
    const x1 = Math.min(caja.x0, caja.x1), x2 = Math.max(caja.x0, caja.x1);
    const y1 = Math.min(caja.y0, caja.y1), y2 = Math.max(caja.y0, caja.y1);
    if (x2 - x1 < 0.002 && y2 - y1 < 0.002) { limpiarSeleccion(); return; }
    const dentro = colocadas.value.filter((p) => p.mapaX >= x1 && p.mapaX <= x2 && p.mapaY >= y1 && p.mapaY <= y2);
    seleccion.value = new Set(dentro.map((p) => p.id));
    principalId.value = dentro[0]?.id ?? null;
    return;
  }
  if (linea.activo) {
    linea.activo = false;
    lineaActiva.value = false;
    colocar();
  }
}

function colocar() {
  const disp = porColocar.value;
  if (!disp.length) {
    toast(`No quedan puestos de ${activa.value?.nombre ?? 'esta categoría'} por colocar.`, 'info');
    return;
  }
  const dist = Math.hypot(linea.x1 - linea.x0, linea.y1 - linea.y0);
  if (dist < CLIC_MAXIMO) {
    const p = disp[0];
    conHistoria([p.id], () => {
      p.mapaX = aRejilla(linea.x1);
      p.mapaY = aRejilla(linea.y1);
      marcarSucio(p.id);
    });
    return;
  }
  const n = Math.min(porLinea.value, disp.length);
  conHistoria(disp.slice(0, n).map((p) => p.id), () => {
    for (let i = 0; i < n; i++) {
      const t = n === 1 ? 0 : i / (n - 1);
      const p = disp[i];
      p.mapaX = aRejilla(linea.x0 + (linea.x1 - linea.x0) * t);
      p.mapaY = aRejilla(linea.y0 + (linea.y1 - linea.y0) * t);
      marcarSucio(p.id);
    }
  });
}

function alternar(id) {
  const s = new Set(seleccion.value);
  if (s.has(id)) s.delete(id);
  else s.add(id);
  seleccion.value = s;
}

const asaEstilo = computed(() => {
  const p = principal.value;
  if (!enDiseno.value || !p || p.mapaX == null) return null;
  const tam = tamanoDe(p);
  const aspecto = planoTienda.aspecto || 1;
  const medioAlto = (tam * (p.mapaProporcion ?? 1)) / 2 / aspecto;
  return {
    left: `${(p.mapaX + tam / 2) * 100}%`,
    top: `${(p.mapaY + medioAlto) * 100}%`,
    '--asa': `calc(var(--mundo, 1200) * ${Math.max(tam * 0.45, 0.003)})`,
  };
});

function puntoRotado(t, dx, dy) {
  const aspecto = planoTienda.aspecto || 1;
  const a = ((t.rotacion || 0) * Math.PI) / 180;
  const ox = dx * Math.cos(a) - dy * Math.sin(a);
  const oy = dx * Math.sin(a) + dy * Math.cos(a);
  return { left: `${((t.mapaX ?? 0.5) + ox) * 100}%`, top: `${((t.mapaY ?? 0.5) + oy / aspecto) * 100}%` };
}

const asaZona = computed(() => {
  const t = textoSel.value;
  if (!enDiseno.value || !t?.fondo) return null;
  const aspecto = planoTienda.aspecto || 1;
  return puntoRotado(t, (t.ancho ?? 0.16) / 2, ((t.alto ?? 0.08) / 2) * aspecto);
});

const asaGiro = computed(() => {
  const t = textoSel.value;
  if (!enDiseno.value || !t) return null;
  const aspecto = planoTienda.aspecto || 1;
  const medio = t.fondo ? ((t.alto ?? 0.08) / 2) * aspecto : (t.tamano ?? 0.02) * 0.8;
  return puntoRotado(t, 0, -(medio + 0.022));
});

const portaTexto = ref(null);
let ultimoCopiado = 'puestos';
let vecesPegadoTexto = 0;

function copiarTexto(silencioso = false) {
  const t = textoSel.value;
  if (!t) return;
  portaTexto.value = {
    contenido: t.contenido, mapaX: t.mapaX ?? 0.5, mapaY: t.mapaY ?? 0.5, tamano: t.tamano, color: t.color,
    colorBorde: t.colorBorde, grosorBorde: t.grosorBorde, rotacion: t.rotacion || 0,
    fondo: t.fondo || '', ancho: t.ancho, alto: t.alto,
  };
  ultimoCopiado = 'texto';
  vecesPegadoTexto = 0;
  if (!silencioso) toast(t.fondo ? 'Zona copiada · Ctrl+V para pegar' : 'Texto copiado · Ctrl+V para pegar', 'info', 1600);
}

async function pegarTexto() {
  const c = portaTexto.value;
  if (!c) return;
  vecesPegadoTexto++;
  const desplazamiento = 0.025 * vecesPegadoTexto;
  try {
    const cuerpo = { ...c, mapaX: acotar(c.mapaX + desplazamiento), mapaY: acotar(c.mapaY + desplazamiento) };
    const r = await consultarPlano('/api/app/plano-textos', { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { toast(d.mensaje || 'No se pudo pegar', 'error'); return; }
    textosTienda.aplicar(d);
    seleccion.value = new Set();
    textoSelId.value = d.id;
  } catch (e) {
    toast(e.message, 'error');
  }
}

async function duplicarTexto() {
  copiarTexto(true);
  await pegarTexto();
}

function moverTexto(dx, dy) {
  const t = textoSel.value;
  if (!t) return;
  editarTexto({ mapaX: acotar((t.mapaX ?? 0.5) + dx), mapaY: acotar((t.mapaY ?? 0.5) + dy) });
}

function girarTexto(delta) {
  const t = textoSel.value;
  if (!t) return;
  editarTexto({ rotacion: Math.round((((Number(t.rotacion || 0) + delta) + 540) % 360) - 180) });
}

const pct = (v, def) => Number(((v ?? def) * 100).toFixed(2));

const escalaSeleccion = computed(() => principal.value?.mapaEscala ?? 1);
const giroSeleccion = computed(() => principal.value?.mapaRotacion ?? 0);
let antesEscala = null;
let temporizadorEscala = null;

function escalaNumerica(porcentaje) {
  if (!antesEscala) iniciarEscala();
  fijarEscala(porcentaje / 100);
  clearTimeout(temporizadorEscala);
  temporizadorEscala = setTimeout(terminarEscala, 700);
}

function giroNumerico(grados) {
  const ids = [...seleccion.value];
  if (!ids.length) return;
  conHistoria(ids, () => {
    for (const p of seleccionadas.value) {
      p.mapaRotacion = normalizarGiro(grados);
      marcarSucio(p.id);
    }
  });
}

function iniciarEscala() {
  antesEscala = seleccionadas.value.map(geom);
}
function fijarEscala(valor) {
  const v = Math.min(ESCALA_MAX, Math.max(ESCALA_MIN, Number(valor)));
  for (const p of seleccionadas.value) {
    p.mapaEscala = v;
    marcarSucio(p.id);
  }
}
function terminarEscala() {
  if (antesEscala?.length) registrar(antesEscala);
  antesEscala = null;
}

const normalizarGiro = (g) => ((Math.round(Number(g) || 0) % 360) + 360) % 360;

function girar(delta) {
  const ids = [...seleccion.value];
  if (!ids.length) return;
  conHistoria(ids, () => {
    for (const p of seleccionadas.value) {
      p.mapaRotacion = normalizarGiro((p.mapaRotacion ?? 0) + delta);
      marcarSucio(p.id);
    }
  });
}

function enderezar() {
  const ids = [...seleccion.value];
  if (!ids.length) return;
  conHistoria(ids, () => {
    for (const p of seleccionadas.value) {
      p.mapaRotacion = 0;
      marcarSucio(p.id);
    }
  });
}

function desplazar(dx, dy) {
  const ids = [...seleccion.value];
  if (!ids.length) return;
  conHistoria(ids, () => {
    for (const p of seleccionadas.value) {
      if (p.mapaX == null) continue;
      p.mapaX = acotar(aRejilla(p.mapaX + dx));
      p.mapaY = acotar(aRejilla(p.mapaY + dy));
      marcarSucio(p.id);
    }
  });
}

function quitarDelMapa() {
  const ids = [...seleccion.value];
  if (!ids.length) return;
  conHistoria(ids, () => {
    for (const p of seleccionadas.value) {
      p.mapaX = null;
      p.mapaY = null;
      marcarSucio(p.id);
    }
  });
  limpiarSeleccion();
}

async function eliminarCasetas() {
  menuMas.value = false;
  if (ocupado.value) return;
  const objetivo = seleccionadas.value;
  if (!objetivo.length) return;
  const rotulo = objetivo.map((p) => `${p.categoria} ${p.codigo}`).join(', ');
  if (!(await confirmar(`¿Eliminar ${objetivo.length} puesto(s)?\n\n${rotulo}\n\nNo se puede deshacer. Los que tengan ventas no se eliminarán.`, { titulo: 'Eliminar', aceptar: 'Sí, eliminar' }))) return;
  const categoriasTocadas = [...new Set(objetivo.map((p) => p.categoriaId))];
  ocupado.value = true;
  try {
    let ok = 0;
    const rechazadas = [];
    for (const p of objetivo) {
      const r = await apiFetch(`/api/app/puestos/${p.id}`, { method: 'DELETE' });
      if (r.ok) ok++;
      else rechazadas.push(`${p.categoria} ${p.codigo}`);
    }
    limpiarSeleccion();
    if (!edicion) await tienda.recargar();
    if (ok) await compactarNumeracion(categoriasTocadas);
    if (rechazadas.length) toast(`No se eliminaron (vendidos o reservados): ${rechazadas.join(', ')}`, 'advertencia');
    else toast(`${ok} puesto(s) eliminados`, 'ok');
  } finally {
    ocupado.value = false;
  }
}

const accionBloqueo = computed(() => {
  const sel = seleccionadas.value.filter((p) => p.estado === 'L' || p.estado === 'X');
  if (!sel.length) return null;
  return sel.every((p) => p.estado === 'X') ? 'Desbloquear' : 'Bloquear';
});

async function alternarBloqueo(lista = seleccionadas.value) {
  menuMas.value = false;
  if (ocupado.value) return;
  const objetivo = lista.filter((p) => p.estado === 'L' || p.estado === 'X');
  if (!objetivo.length) return;
  ocupado.value = true;
  try {
    const rechazadas = [];
    for (const p of objetivo) {
      const accion = p.estado === 'L' ? 'bloquear' : 'desbloquear';
      const r = await apiFetch(`/api/app/puestos/${p.id}/${accion}`, { method: 'POST' });
      if (r.ok) p.estado = accion === 'bloquear' ? 'X' : 'L';
      else rechazadas.push(`${p.categoria} ${p.codigo}`);
    }
    if (rechazadas.length) toast(`No se pudo cambiar: ${rechazadas.join(', ')}`, 'advertencia');
  } finally {
    ocupado.value = false;
  }
}

const portapapeles = ref([]);
let vecesPegado = 0;

function copiarSeleccion() {
  menuMas.value = false;
  const sel = seleccionadas.value.filter((p) => p.mapaX != null);
  if (!sel.length) return;
  portapapeles.value = sel.map((p) => ({
    categoriaId: p.categoriaId, tamano: p.tamano || '3x3',
    mapaX: p.mapaX, mapaY: p.mapaY, mapaEscala: p.mapaEscala ?? 1, mapaRotacion: p.mapaRotacion ?? 0,
  }));
  vecesPegado = 0;
  toast(`${sel.length} puesto(s) copiados`, 'info', 1800);
}

async function pegar() {
  menuMas.value = false;
  if (ocupado.value || !portapapeles.value.length) return;
  ocupado.value = true;
  vecesPegado++;
  try {
    const despl = aRejilla(0.03) * vecesPegado;
    const usadosPorCat = new Map();
    const posiciones = [];
    for (const c of portapapeles.value) {
      let usados = usadosPorCat.get(c.categoriaId);
      if (!usados) {
        usados = new Set(puestos.value.filter((p) => p.categoriaId === c.categoriaId).map((p) => parseInt(p.codigo) || 0));
        usadosPorCat.set(c.categoriaId, usados);
      }
      let codigo = 1;
      while (usados.has(codigo)) codigo++;
      usados.add(codigo);
      const r = await apiFetch('/api/app/puestos', {
        method: 'POST',
        body: JSON.stringify({ categoriaId: c.categoriaId, codigo: String(codigo), tamano: c.tamano }),
      });
      const d = await r.json().catch(() => ({}));
      if (!d.ok) continue;
      posiciones.push({
        id: d.id,
        x: acotar(aRejilla(c.mapaX) + despl),
        y: acotar(aRejilla(c.mapaY) + despl),
        escala: c.mapaEscala,
        rotacion: c.mapaRotacion,
      });
    }
    if (!posiciones.length) { toast('No se pudo pegar.', 'error'); return; }
    await apiFetch('/api/app/puestos/posiciones', { method: 'POST', body: JSON.stringify(posiciones) });
    if (!edicion) await tienda.recargar();
    seleccion.value = new Set(posiciones.map((p) => p.id));
    principalId.value = posiciones[0].id;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

async function agregarCaseta() {
  if (ocupado.value || !activa.value) return;
  ocupado.value = true;
  try {
    const usados = puestos.value.filter((p) => p.categoriaId === catSel.value).map((p) => parseInt(p.codigo) || 0);
    const codigo = String(Math.max(0, ...usados) + 1);
    const hermana = puestos.value.find((p) => p.categoriaId === catSel.value && p.tamano);
    const r = await apiFetch('/api/app/puestos', {
      method: 'POST',
      body: JSON.stringify({ categoriaId: catSel.value, codigo, tamano: hermana?.tamano || '3x3' }),
    });
    const d = await r.json().catch(() => ({}));
    if (!d.ok) { toast(d.mensaje || 'No se pudo crear el puesto', 'error'); return; }
    if (!edicion) await tienda.recargar();
    herramienta.value = 'colocar';
    toast(`Puesto ${codigo} creado: haz clic en el mapa para colocarlo`, 'info');
  } finally {
    ocupado.value = false;
  }
}

const aNumerar = computed(() =>
  ordenLectura(seleccionadas.value, planoTienda.aspecto, numeracion.orden === 'columnas'));

async function numerarSeleccion() {
  if (ocupado.value) return;
  const orden = aNumerar.value;
  const inicio = parseInt(numeracion.inicio, 10);
  if (!orden.length || !Number.isFinite(inicio) || inicio < 0) return;
  ocupado.value = true;
  try {
    const cambios = orden.map((p, i) => ({ id: p.id, codigo: String(inicio + i) }));
    const r = await apiFetch('/api/app/puestos/codigos', { method: 'PATCH', body: JSON.stringify(cambios) });
    const d = await r.json().catch(() => ({}));
    if (!d.ok) { toast(d.mensaje || 'No se pudo numerar', 'error'); return; }
    menuMas.value = false;
    toast(`Numerados ${inicio}–${inicio + orden.length - 1}`, 'ok', 2000);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

async function compactarNumeracion(categoriaIds) {
  for (const catId of new Set(categoriaIds)) {
    const cambios = numeracionCompacta(puestos.value, catId);
    if (!cambios.length) continue;
    try {
      await apiFetch('/api/app/puestos/codigos', { method: 'PATCH', body: JSON.stringify(cambios) });
    } catch {
      toast('No se pudo corregir la numeración', 'advertencia');
    }
  }
}

async function cargarFeria() {
  if (!edicion) return;
  const r = await apiFetch('/api/app/ediciones/resumen');
  if (r.ok) feria.value = (await r.json()).find((f) => f.id === edicion);
}

async function publicarFeria() {
  if (ocupado.value || !feria.value) return;
  clearTimeout(temporizadorTexto);
  await enviarTextos();
  if (sinConfirmar.size) { toast("Hay textos sin guardar. Revisa la conexión antes de publicar", "error"); return; }
  if (dirty.value.size) await guardar();
  if (dirty.value.size) return;
  if (!colocadas.value.length) { toast('Ubica al menos un puesto antes de publicar', 'advertencia'); return; }
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/ediciones/${edicion}/habilitar`, {
      method: 'POST', body: JSON.stringify({ habilitada: true }),
    });
    const datos = await r.json();
    if (!r.ok || !datos.ok) throw new Error(datos.mensaje || 'No se pudo publicar');
    feria.value.publicada = true;
    toast('Plano publicado', 'ok');
  } catch (e) { toast(e.message, 'error'); }
  finally { ocupado.value = false; }
}

async function guardar() {
  if (ocupado.value || !dirty.value.size) return;
  const pos = [...dirty.value].map(buscar).filter(Boolean).map((p) => ({
    id: p.id, x: p.mapaX, y: p.mapaY, escala: p.mapaEscala ?? 1, rotacion: p.mapaRotacion ?? 0,
  }));
  ocupado.value = true;
  try {
    const r = await apiFetch('/api/app/puestos/posiciones', { method: 'POST', body: JSON.stringify(pos) });
    if (!r.ok) { toast('No se pudo guardar el plano', 'error'); return; }
    dirty.value = new Set();
    historial.value = [];
    indice.value = -1;
    guardadoReciente.value = true;
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

const ficha = reactive({ codigo: '', precio: '', precioPropio: false, tamano: '', referencia: '', observacion: '' });
const venta = ref(null);
const cargandoVenta = ref(false);
const guardandoFicha = ref(false);

function llenarFicha(p) {
  ficha.codigo = p?.codigo ?? '';
  ficha.precioPropio = Boolean(p?.precioPropio);
  ficha.precio = p?.precio ?? '';
  ficha.tamano = p?.tamano ?? '';
  ficha.referencia = p?.referencia ?? '';
  ficha.observacion = p?.observacion ?? '';
}

const precioCategoria = computed(() => {
  const p = fichaPuesto.value;
  if (!p) return null;
  const hermana = puestos.value.find((x) => x.categoriaId === p.categoriaId && !x.precioPropio);
  return hermana ? hermana.precio : null;
});

const fichaCambiada = computed(() => {
  const p = fichaPuesto.value;
  if (!p) return false;
  return String(ficha.codigo).trim() !== String(p.codigo ?? '')
    || ficha.tamano.trim() !== (p.tamano ?? '')
    || ficha.referencia.trim() !== (p.referencia ?? '')
    || ficha.observacion.trim() !== (p.observacion ?? '')
    || ficha.precioPropio !== Boolean(p.precioPropio)
    || (ficha.precioPropio && Number(ficha.precio) !== Number(p.precio));
});

watch(() => fichaPuesto.value?.id, () => llenarFicha(fichaPuesto.value), { immediate: true });
watch(
  () => [fichaPuesto.value?.id, fichaPuesto.value?.estado],
  async ([id, estado]) => {
    venta.value = null;
    if (!id || !puedeDisenar.value || (estado !== 'O' && estado !== 'T')) return;
    cargandoVenta.value = true;
    try {
      const r = await apiFetch(`/api/app/puestos/${id}/venta`);
      const d = await r.json().catch(() => ({}));
      if (fichaPuesto.value?.id === id) venta.value = d.venta || null;
    } catch {
      venta.value = null;
    } finally {
      cargandoVenta.value = false;
    }
  },
  { immediate: true },
);

async function guardarFicha() {
  const p = fichaPuesto.value;
  if (!p || guardandoFicha.value) return;
  const codigo = String(ficha.codigo).trim();
  if (!codigo) { toast('El número del puesto no puede quedar vacío.', 'advertencia'); return; }
  const precio = Number(ficha.precio);
  if (ficha.precioPropio && (!Number.isFinite(precio) || precio < 0)) {
    toast('Precio inválido.', 'advertencia');
    return;
  }
  guardandoFicha.value = true;
  try {
    if (codigo !== String(p.codigo ?? '')) {
      const r = await apiFetch('/api/app/puestos/codigos', {
        method: 'PATCH', body: JSON.stringify([{ id: p.id, codigo }]),
      });
      const d = await r.json().catch(() => ({}));
      if (!d.ok) { toast(d.mensaje || 'Ese número ya está en uso', 'error'); return; }
      p.codigo = codigo;
    }
    const cambioPrecio = ficha.precioPropio !== Boolean(p.precioPropio)
      || (ficha.precioPropio && precio !== Number(p.precio));
    if (cambioPrecio) {
      const r = await apiFetch('/api/app/puestos/precios', {
        method: 'PATCH', body: JSON.stringify([{ id: p.id, precio: ficha.precioPropio ? precio : null }]),
      });
      const d = await r.json().catch(() => ({}));
      if (!d.ok) { toast(d.mensaje || 'No se pudo cambiar el precio', 'error'); return; }
      p.precioPropio = ficha.precioPropio;
      if (ficha.precioPropio) p.precio = precio;
      else if (precioCategoria.value != null) p.precio = precioCategoria.value;
    }
    const cuerpo = {
      tamano: ficha.tamano.trim(), referencia: ficha.referencia.trim(), observacion: ficha.observacion.trim(),
    };
    if (cuerpo.tamano !== (p.tamano ?? '') || cuerpo.referencia !== (p.referencia ?? '')
        || cuerpo.observacion !== (p.observacion ?? '')) {
      const r = await apiFetch(`/api/app/puestos/${p.id}/ficha`, { method: 'PATCH', body: JSON.stringify(cuerpo) });
      if (!r.ok) { toast('No se pudieron guardar los datos', 'error'); return; }
      p.tamano = cuerpo.tamano || p.tamano;
      p.referencia = cuerpo.referencia || null;
      p.observacion = cuerpo.observacion || null;
    }
    llenarFicha(p);
    toast('Guardado ✓', 'ok', 1600);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardandoFicha.value = false;
  }
}

async function subirFoto(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  menuMas.value = false;
  if (!archivo || !seleccion.value.size) return;
  ocupado.value = true;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    for (const id of seleccion.value) datos.append('puestos', id);
    const r = await apiFetch('/api/app/puestos/fotos', { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    toast(d.mensaje || (r.ok ? 'Foto agregada' : 'No se pudo subir la foto'), r.ok ? 'ok' : 'error');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

async function ubicacionSeleccion() {
  menuMas.value = false;
  const ids = [...seleccion.value];
  if (!ids.length) return;
  const texto = await pedirTexto('Escribe dónde están los puestos seleccionados.', {
    titulo: 'Ubicación', valor: principal.value?.referencia || '', marcador: 'Ej. frente a la puerta 3',
  });
  if (texto == null) return;
  ocupado.value = true;
  try {
    for (const id of ids) {
      await apiFetch(`/api/app/puestos/${id}/referencia`, { method: 'PATCH', body: JSON.stringify({ referencia: texto }) });
      const p = buscar(id);
      if (p) p.referencia = texto.trim() || null;
    }
    toast('Ubicación guardada', 'ok', 1600);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

async function pdfAImagen(archivo) {
  const canvas = await primeraPaginaEnCanvas(await archivo.arrayBuffer(), document.createElement('canvas'), 2);
  return new Blob([await new Promise((resolve) => canvas.toBlob(resolve, 'image/png', 0.9))], { type: 'image/png' });
}

async function subirPlano(archivo) {
  if (!archivo) return;
  ocupado.value = true;
  try {
    let subir = archivo;
    if (archivo.type === 'application/pdf') subir = new File([await pdfAImagen(archivo)], 'plano.png', { type: 'image/png' });
    const datos = new FormData();
    datos.append('archivo', subir);
    const r = await consultarPlano('/api/app/plano', { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (r.ok && d.ok) {
      planoTienda.aplicar(d.plano);
      modalFondo.value = false;
      toast(d.mensaje || 'Plano actualizado', 'ok');
    } else {
      toast(d.mensaje || 'No se pudo actualizar el plano', 'error');
    }
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

async function quitarFondo() {
  const si = await confirmar('Los puestos, textos y zonas se quedan donde están; el plano pasa a un lienzo liso.', { titulo: '¿Quitar el fondo?', aceptar: 'Sí, quitar fondo' });
  if (!si) return;
  ocupado.value = true;
  try {
    const r = await consultarPlano('/api/app/plano', { method: 'DELETE' });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo quitar el fondo', 'error'); return; }
    planoTienda.aplicar(d.plano);
    modalFondo.value = false;
    toast('Fondo quitado', 'ok', 1500);
  } finally {
    ocupado.value = false;
  }
}

async function aplicarForma(forma) {
  if (dirty.value.size) await guardar();
  if (dirty.value.size) return;
  ocupado.value = true;
  try {
    const r = await consultarPlano('/api/app/plano/forma', { method: 'PATCH', body: JSON.stringify(forma) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo cambiar la forma', 'error'); return; }
    planoTienda.aplicar(d.plano);
    if (!edicion) {
      tienda.recargar();
      textosTienda.cargar();
    }
    modalForma.value = false;
    toast('Forma del plano actualizada', 'ok', 1600);
  } finally {
    ocupado.value = false;
  }
}

function cerrarAyuda() {
  ayudaAbierta.value = false;
  guardarPreferencia(CLAVE_AYUDA, '1');
}

function pantallaCompleta() {
  try {
    if (document.fullscreenElement) document.exitFullscreen?.();
    else document.documentElement.requestFullscreen?.();
  } catch { }
}

function abrirPublico() {
  window.open(router.resolve({ path: '/', query: { v: 'actividad', ...(edicion ? { edicion } : {}) } }).href, '_blank', 'noopener');
}

async function descargarImagen() {
  generandoImagen.value = true;
  try {
    const blob = await imagenPlanoFeria({
      titulo: 'Plano de la actividad', subtitulo: `${colocadas.value.length} puestos · ${categorias.value.length} categorías`,
      aspecto: planoTienda.aspecto || 1, fondoUrl: planoTienda.src, puestos: colocadas.value, textos: textosTienda.textos,
      leyenda: categorias.value.map((c) => ({ color: c.color || '#94a3b8', texto: c.nombre })),
    });
    const r = await guardarBlob(blob, 'plano-actividad.png');
    toast(r.destino === 'telefono' ? 'Imagen guardada en Documentos' : 'Imagen descargada', 'ok', 1800);
  } catch (e) {
    toast(`No se pudo generar la imagen: ${e.message}`, 'error');
  } finally {
    generandoImagen.value = false;
  }
}

const pendientesTexto = new Map();
const sinConfirmar = new Map();
const guardandoTextos = ref(false);
const contenidoEdit = ref('');
let temporizadorTexto = null;
const soltarProteccionTextos = textosTienda.protegerLocales((id) => sinConfirmar.get(id) || null);

watch(textoSelId, () => { contenidoEdit.value = textoSel.value?.contenido ?? ''; });

function escribirTexto(valor) {
  contenidoEdit.value = valor;
  if (valor.trim()) editarTexto({ contenido: valor });
}

function terminarTexto() {
  if (!contenidoEdit.value.trim() && textoSel.value) contenidoEdit.value = textoSel.value.contenido;
}

function editarTexto(campos, id = textoSelId.value) {
  const t = textosTienda.textos.find((x) => x.id === id);
  if (!t) return;
  Object.assign(t, campos);
  pendientesTexto.set(id, { ...(pendientesTexto.get(id) || {}), ...campos });
  sinConfirmar.set(id, { ...(sinConfirmar.get(id) || {}), ...campos });
  guardandoTextos.value = true;
  guardadoReciente.value = false;
  clearTimeout(temporizadorTexto);
  temporizadorTexto = setTimeout(enviarTextos, 350);
}

async function enviarTextos() {
  guardandoTextos.value = true;
  for (const [id, cambios] of [...pendientesTexto]) {
    pendientesTexto.delete(id);
    try {
      const r = await apiFetch(`/api/app/plano-textos/${id}`, { method: 'PATCH', body: JSON.stringify(cambios) });
      if (!r.ok) { toast('No se pudo guardar el texto', 'error'); continue; }
      const d = await r.json();
      if (!pendientesTexto.has(id)) sinConfirmar.delete(id);
      textosTienda.aplicar(d);
    } catch (e) {
      toast(e.message, 'error');
    }
  }
  if (!pendientesTexto.size) {
    guardandoTextos.value = false;
    guardadoReciente.value = true;
  }
}

async function crearTexto(punto, zona = false) {
  try {
    const cuerpo = zona
      ? { contenido: 'ZONA', mapaX: aRejilla(punto.x), mapaY: aRejilla(punto.y), fondo: '#7c5cff', ancho: 0.16, alto: 0.08, color: '#3b2a8f', tamano: 0.02 }
      : { contenido: 'TEXTO', mapaX: aRejilla(punto.x), mapaY: aRejilla(punto.y) };
    const r = await consultarPlano('/api/app/plano-textos', { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { toast(d.mensaje || 'No se pudo crear el texto', 'error'); return; }
    textosTienda.aplicar(d);
    seleccion.value = new Set();
    textoSelId.value = d.id;
    await nextTick();
    entradaTexto.value?.focus();
    entradaTexto.value?.select();
  } catch (e) {
    toast(e.message, 'error');
  }
}

async function borrarTexto() {
  const t = textoSel.value;
  if (!t || !(await confirmar(`«${t.contenido}» se quitará del plano.`, { titulo: t.fondo ? '¿Quitar la zona?' : '¿Quitar el texto?', aceptar: 'Sí, quitar' }))) return;
  try {
    const r = await apiFetch(`/api/app/plano-textos/${t.id}`, { method: 'DELETE' });
    if (!r.ok) { toast('No se pudo quitar el texto', 'error'); return; }
    pendientesTexto.delete(t.id);
    textosTienda.aplicar({ id: t.id, activo: false });
    textoSelId.value = null;
  } catch (e) {
    toast(e.message, 'error');
  }
}

const aMilesimas = (v) => Math.round((Number(v) || 0) * 1000);

function onTecla(e) {
  if (cajonCategorias.value) {
    if (e.key === 'Escape' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target?.tagName)) cerrarCategorias();
    return;
  }
  const etiqueta = e.target?.tagName;
  if (etiqueta === 'INPUT' || etiqueta === 'TEXTAREA' || etiqueta === 'SELECT') {
    if (e.key === 'Escape') e.target.blur();
    return;
  }
  if (e.key === 'Escape') {
    if (menuMas.value) menuMas.value = false;
    else if (herramienta.value) herramienta.value = null;
    else limpiarSeleccion();
    return;
  }
  if (!enDiseno.value) return;
  const tecla = e.key.toLowerCase();
  const paso = e.shiftKey ? 0.005 : 0.001;
  if (e.ctrlKey && tecla === 'z') { e.preventDefault(); if (e.shiftKey) rehacer(); else deshacer(); }
  else if (e.ctrlKey && tecla === 'y') { e.preventDefault(); rehacer(); }
  else if (e.ctrlKey && tecla === 's') { e.preventDefault(); guardar(); }
  else if (e.ctrlKey && tecla === 'c') { e.preventDefault(); if (textoSel.value && !seleccion.value.size) copiarTexto(); else { copiarSeleccion(); ultimoCopiado = 'puestos'; } }
  else if (e.ctrlKey && tecla === 'v') { e.preventDefault(); if (ultimoCopiado === 'texto' && portaTexto.value) pegarTexto(); else pegar(); }
  else if (e.ctrlKey && tecla === 'd') { e.preventDefault(); if (textoSel.value && !seleccion.value.size) duplicarTexto(); else if (seleccion.value.size) { copiarSeleccion(); ultimoCopiado = 'puestos'; pegar(); } }
  else if (textoSel.value && !seleccion.value.size && e.key.startsWith('Arrow')) {
    e.preventDefault();
    if (e.key === 'ArrowLeft') moverTexto(-paso, 0);
    else if (e.key === 'ArrowRight') moverTexto(paso, 0);
    else if (e.key === 'ArrowUp') moverTexto(0, -paso);
    else moverTexto(0, paso);
  }
  else if (textoSel.value && !seleccion.value.size && (e.key === 'Delete' || e.key === 'Backspace')) { e.preventDefault(); borrarTexto(); }
  else if (textoSel.value && !seleccion.value.size && !e.ctrlKey && tecla === 'r') { e.preventDefault(); girarTexto(e.shiftKey ? -GIRO_PASO : GIRO_PASO); }
  else if (!e.ctrlKey && !e.altKey && tecla === 'r' && seleccion.value.size) {
    e.preventDefault();
    girar(e.shiftKey ? -GIRO_PASO : GIRO_PASO);
  }
  else if (e.key === 'ArrowLeft') { e.preventDefault(); desplazar(-paso, 0); }
  else if (e.key === 'ArrowRight') { e.preventDefault(); desplazar(paso, 0); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); desplazar(0, -paso); }
  else if (e.key === 'ArrowDown') { e.preventDefault(); desplazar(0, paso); }
}

function avisarSinGuardar(e) {
  if (!dirty.value.size) return;
  e.preventDefault();
  e.returnValue = '';
}

function sesionCaducada() {
  auth.logout();
  router.push('/admin');
}

onMounted(async () => {
  cargarFeria().catch(() => {});
  quitarGuardia = tienda.protegerLocales((id) => dirty.value.has(id));
  planoTienda.asegurar();
  textosTienda.asegurar();
  await tienda.asegurar(sesionCaducada);
  if (catSel.value == null && categorias.value.length) catSel.value = categorias.value[0].id;
  if (puedeDisenar.value && leerPreferencia(CLAVE_AYUDA) !== '1') ayudaAbierta.value = true;
});
onActivated(() => {
  cargarFeria().catch(() => {});
  cargarPagados();
  window.addEventListener('keydown', onTecla);
  window.addEventListener('beforeunload', avisarSinGuardar);
});
onDeactivated(() => {
  window.removeEventListener('keydown', onTecla);
  window.removeEventListener('beforeunload', avisarSinGuardar);
});
onUnmounted(() => {
  window.removeEventListener('keydown', onTecla);
  window.removeEventListener('beforeunload', avisarSinGuardar);
  clearTimeout(temporizadorTexto);
  clearTimeout(temporizadorPagados);
  clearTimeout(temporizadorEscala);
  if (pendientesTexto.size) enviarTextos();
  soltarProteccionTextos();
  if (quitarGuardia) quitarGuardia();
  editor?.cerrar();
});
</script>

<template>
  <div class="editor">
    <header class="barra">
      <router-link v-if="desdeAsistente" :to="aModulo('actividad', { id: edicion, paso: 'plano' })" class="herr volver" title="Volver a la configuración de la actividad">← Configuración</router-link>
      <router-link v-else :to="aModulo('actividades')" class="herr volver" title="Volver a Mis actividades">← Actividades</router-link>
      <CambiarPlano v-if="edicion || actividadEnCurso.actual" :actual="`f-${edicion || actividadEnCurso.actual.id}`" />
      <span v-if="feria" class="badge">{{ feria.nombre }}</span>
      <button v-if="desdeAsistente" type="button" class="btn btn-primario btn-sm" :disabled="ocupado" title="Guarda el plano y sigue con las credenciales" @click="seguirAsistente">Plano listo · Seguir →</button>
      <button v-else-if="feria && !feria.publicada" type="button" class="herr" :disabled="ocupado" @click="publicarFeria">Publicar</button>
      <router-link v-if="feria?.publicada" :to="{ path: '/', query: { v: 'actividad', edicion } }" target="_blank" class="herr">Ver página pública</router-link>
      <button v-if="puedeCategorias" type="button" class="herr" title="Categorías, precios y personas por puesto de esta feria" @click="cajonCategorias = true">🏷️ Categorías y precios</button>
      <div v-if="puedeDisenar" class="segmentado" role="tablist" aria-label="Modo del editor">
        <button role="tab" :aria-selected="modo === 'gestion'" :class="{ on: modo === 'gestion' }"
                @click="modo = 'gestion'">Gestión</button>
        <button role="tab" :aria-selected="modo === 'diseno'" :class="{ on: modo === 'diseno' }"
                @click="modo = 'diseno'">Diseño</button>
      </div>

      <div v-if="enDiseno" class="grupo">
        <button class="herr" :class="{ on: herramienta === 'colocar' }" @click="elegirHerramienta('colocar')"
                title="Colocar puestos de la categoría elegida">＋ Puesto</button>
        <button class="herr" :class="{ on: herramienta === 'seleccionar' }" @click="elegirHerramienta('seleccionar')"
                title="Seleccionar varios arrastrando un recuadro">⬚ Seleccionar</button>
        <button class="herr" :class="{ on: herramienta === 'texto' }" @click="elegirHerramienta('texto')"
                title="Agregar un texto suelto al plano">T Texto</button>
        <button class="herr" :class="{ on: herramienta === 'zona' }" @click="elegirHerramienta('zona')"
                title="Agregar una zona con recuadro de color y nombre, ej. Escenario o Patio de comidas">▭ Zona</button>
      </div>

      <div class="grupo">
        <button class="herr" :class="{ on: guiasActivas }" :aria-pressed="guiasActivas" @click="guiasActivas = !guiasActivas"
                title="Al mover, aparecen líneas cuando algo queda alineado con otro elemento o con el centro, y se ajusta solo. Mantén Alt para mover sin ajuste">📐 Guías</button>
        <button class="herr" :class="{ on: rejilla }" :aria-pressed="rejilla" @click="rejilla = !rejilla"
                title="Muestra una cuadrícula de referencia (no obliga a nada)">⊞ Cuadrícula</button>
        <button class="herr" :class="{ on: iman }" :aria-pressed="iman" @click="iman = !iman"
                title="Obliga a que todo caiga sobre la cuadrícula al moverlo (pasos de 1%)">🧲 Imán</button>
        <button class="herr" :class="{ on: numerosVisibles }" :aria-pressed="numerosVisibles"
                @click="numerosVisibles = !numerosVisibles" title="Mostrar el número de cada puesto"># Números</button>
      </div>

      <div v-if="enDiseno" class="grupo">
        <button class="herr" :disabled="ocupado" @click="modalFondo = true"
                :title="`Poner, cambiar o quitar la imagen o PDF debajo del plano · ${planoTienda.plano.ancho}×${planoTienda.plano.alto}`">🖼 Fondo{{ planoTienda.src ? ' ✓' : '' }}</button>
        <button class="herr" :disabled="ocupado" title="Elegir si el plano es horizontal, cuadrado o vertical" @click="modalForma = true">📐 Forma</button>
      </div>

      <div class="grupo">
        <button class="herr" :disabled="Boolean(edicion && !feria?.publicada)" title="Ver la página pública de la actividad" @click="abrirPublico">👁 Vista pública</button>
        <button class="herr" :disabled="generandoImagen" title="Descargar el plano como imagen PNG" @click="descargarImagen">{{ generandoImagen ? 'Generando…' : '⬇ Imagen' }}</button>
        <button class="herr icono" title="Pantalla completa" @click="pantallaCompleta">⛶</button>
        <button class="herr icono" title="Cómo se usa el editor" @click="ayudaAbierta = true">?</button>
      </div>

      <div class="grupo derecha">
        <span v-if="enDiseno" class="estado-guardado" :class="{ pendiente: dirty.size }">
          <template v-if="(ocupado && dirty.size) || guardandoTextos">Guardando…</template>
          <template v-else-if="dirty.size">{{ dirty.size }} sin guardar</template>
          <template v-else-if="guardadoReciente">Guardado ✓</template>
        </span>
        <template v-if="enDiseno">
          <button class="herr icono" :disabled="!puedeDeshacer" @click="deshacer" title="Deshacer (Ctrl+Z)">↶</button>
          <button class="herr icono" :disabled="!puedeRehacer" @click="rehacer" title="Rehacer (Ctrl+Y)">↷</button>
          <button class="btn btn-primario btn-sm" :disabled="ocupado || !dirty.size" @click="guardar"
                  title="Guardar posiciones y tamaños (Ctrl+S)">Guardar</button>
        </template>
      </div>
    </header>

    <div class="zona">
      <PanZoom :key="`${planoTienda.src}-${planoTienda.plano.version}`" llenar :aspect="planoTienda.aspecto"
               :focus="{ x: 0.5, y: 0.45, scale: 1.4 }" :max-ancho="topeZoom">
        <div ref="lienzo" class="plano"
             :class="[`herr-${enDiseno ? herramienta || 'ninguna' : 'gestion'}`, { 'con-numeros': numerosVisibles, disenando: enDiseno }]"
             :style="{ aspectRatio: planoTienda.aspecto ? 1 / planoTienda.aspecto : 'auto', '--aspecto': planoTienda.aspecto || 1 }"
             @pointerdown="onDown" @pointermove="onMove" @pointerup="onUp" @pointercancel="onUp">
          <img v-if="planoTienda.src" :src="planoTienda.src" :key="planoTienda.src" alt="Plano"
               :width="planoTienda.plano.ancho" :height="planoTienda.plano.alto"
               decoding="async" draggable="false" />

          <div v-if="rejilla" class="rejilla"></div>

          <div v-for="p in colocadas" :key="p.id"
               :ref="(el) => (el ? pinEls.set(p.id, el) : pinEls.delete(p.id))"
               class="pin" :class="[`forma-${conPrevia(p).forma || 'cuadrado'}`, `est-${p.estado}`, { sel: seleccion.has(p.id), pagado: p.estado === 'O' && pagados.has(p.id), previa: previa && previa.categoriaId === p.categoriaId, referencia: referencias.has(p.id) }]"
               :style="{ ...estiloPin(posDe(p)), background: conPrevia(p).color || '#94a3b8' }"
               :title="`${p.categoria} ${p.codigo} · ${ETIQUETA_ESTADO[p.estado] || ''}`"
               @pointerdown="onPinDown($event, p)">
            <span class="num-caseta">{{ p.codigo }}</span>
            <span v-if="p.estado !== 'L'" class="marca-estado"></span>
          </div>

          <div v-for="g in fantasmas" :key="g.id" class="pin fantasma" :class="`forma-${g.forma || 'cuadrado'}`"
               :style="{ ...estiloPin(g), background: g.color }"><span class="num-caseta">{{ g.codigo }}</span></div>
          <span v-if="fantasmas.length" class="etiqueta-previa" :style="{ left: '50%', top: `calc(50% + ${(fantasmas[0].tamanoMapa * (fantasmas[0].mapaProporcion || 1) / (planoTienda.aspecto || 1)) * 60 + 2}%)` }">Así se verán</span>

          <RotuloPlano v-for="t in textosTienda.textos" :key="t.id" :texto="t"
                       :seleccionado="textoSelId === t.id"
                       :class="{ tocable: enDiseno && herramienta !== 'colocar' }"
                       @pointerdown="onTextoDown($event, t)" />

          <div v-if="asaEstilo && !arrastrandoPin" class="asa" :style="asaEstilo" title="Arrastra para cambiar el tamaño"
               @pointerdown="onAsaDown"></div>
          <div v-if="asaZona" class="asa asa-fija" :style="asaZona" title="Arrastra para cambiar el tamaño (Shift mantiene la proporción)"
               @pointerdown="onAsaZonaDown"></div>
          <div v-if="asaGiro" class="asa-giro" :style="asaGiro" title="Arrastra para girar (se ajusta cada 15°; Alt = libre)"
               @pointerdown="onAsaGiroDown">⟳</div>
          <div v-if="medidor" class="medidor" :class="{ alineado: medidor.alineado, izquierda: medidor.x > 0.68, abajo: medidor.y < 0.12 }" :style="{ left: `${medidor.x * 100}%`, top: `${medidor.y * 100}%` }">
            <span v-for="t in medidor.textos" :key="t">{{ t }}</span>
          </div>
          <div v-for="x in guiasV" :key="`v${x}`" class="guia-alineacion vertical" :style="{ left: `${x * 100}%` }"></div>
          <div v-for="y in guiasH" :key="`h${y}`" class="guia-alineacion horizontal" :style="{ top: `${y * 100}%` }"></div>
          <div v-if="lineaActiva" ref="guiaEl" class="guia"></div>
          <div v-if="cajaActiva" ref="cajaEl" class="caja"></div>
        </div>
      </PanZoom>

      <p v-if="!(faltanColocar && herramienta === 'colocar')" class="ayuda">{{ ayuda }}</p>
      <div v-else class="aviso-colocar flotante" role="status">
        <span class="muestra grande" :class="`forma-${activa?.forma || 'cuadrado'}`" :style="{ background: activa?.color || '#94a3b8' }"></span>
        <div>
          <strong>«{{ avisoColocar.nombre }}» lista · faltan {{ faltanColocar }} {{ faltanColocar === 1 ? unoEspacio : variosEspacio }} por colocar</strong>
          <span>Toca el plano para poner {{ unoEspacio.endsWith('a') ? 'una' : 'uno' }}, o arrastra una línea para poner una fila de {{ Math.min(porLinea, faltanColocar) }}.</span>
        </div>
        <button type="button" class="cerrar" aria-label="Entendido" @click="avisoColocar = null">✕</button>
      </div>

      <section v-if="enDiseno && !catRapida" class="flotante panel-categorias" :class="{ plegado: !panelCategorias }">
        <div class="cabecera">
          <button type="button" class="plegar" @click="panelCategorias = !panelCategorias">
            <span>Categorías</span><span class="chevron">{{ panelCategorias ? '▾' : '▸' }}</span>
          </button>
          <button v-if="puedeCategorias" type="button" class="btn btn-sm btn-primario nueva" title="Crear una categoría nueva" @click="abrirCategoria()">＋ Nueva</button>
        </div>
        <template v-if="panelCategorias">
          <p v-if="!categorias.length" class="vacia">Aún no hay categorías. Crea la primera (ej.: Mesa VIP, Mesa general) y luego colócalas en el plano.</p>
          <ul v-else class="lista-cat desliza">
            <li v-for="c in categorias" :key="c.id" :class="{ on: catSel === c.id && herramienta === 'colocar' }">
              <button class="elegir" :title="`Colocar ${c.nombre} en el plano`" @click="elegirCategoria(c.id)">
                <span class="muestra" :class="`forma-${c.forma}`" :style="{ background: c.color || '#94a3b8' }"></span>
                <span class="nom">{{ c.nombre }}</span>
                <span class="cuenta" :class="{ completa: c.puestas === c.total }" :title="`${c.puestas} de ${c.total} colocados`">{{ c.puestas }}/{{ c.total }}</span>
              </button>
              <button v-if="puedeCategorias" class="editar" :title="`Editar ${c.nombre}: precio, tamaño, color…`" :aria-label="`Editar ${c.nombre}`" @click="abrirCategoria(c.id)">✏️</button>
            </li>
          </ul>
          <div v-if="herramienta === 'colocar' && activa" class="pie-cat">
            <label class="mini">Por fila <input type="number" min="1" max="60" v-model.number="porLinea" /></label>
            <button class="btn btn-sm" :disabled="ocupado" @click="agregarCaseta">＋ 1 {{ unoEspacio }}</button>
          </div>
          <button v-if="puedeCategorias" type="button" class="enlace" @click="cajonCategorias = true">Todas las categorías y precios →</button>
        </template>
      </section>

      <CategoriaRapida v-if="catRapida" :key="catRapida.id ?? 'nueva'" :edicion="edicion" :categoria-id="catRapida.id"
                       :uno="unoEspacio" :varios="variosEspacio" :registra-personas="feria ? feria.registraPersonas !== false : true"
                       @previa="previa = $event" @guardada="categoriaGuardada" @cerrar="cerrarCategoriaRapida"
                       @avanzado="cerrarCategoriaRapida(); cajonCategorias = true" />

      <div class="leyenda flotante">
        <span v-for="e in ESTADOS" :key="e.id" class="item">
          <i class="punto" :class="`p-${e.clase}`"></i>{{ ETIQUETA_ESTADO[e.id] }}
        </span>
        <span v-if="puedeDisenar" class="item"><i class="punto p-pagado"></i>Pagado</span>
      </div>

      <div v-if="enDiseno && seleccion.size" class="flotante barra-seleccion">
        <span class="cuenta-sel">{{ seleccion.size }} sel.</span>
        <label class="numero-etiquetado" title="Tamaño respecto al de su categoría">
          <span>Tamaño</span>
          <NumeroPaso compacto :model-value="Math.round(escalaSeleccion * 100)" :min="ESCALA_MIN * 100" :max="ESCALA_MAX * 100" :paso="5" unidad="%"
                      etiqueta="Tamaño" @update:model-value="escalaNumerica" />
        </label>
        <label class="numero-etiquetado" title="Giro en grados (R / Shift+R gira 15°)">
          <span>Giro</span>
          <NumeroPaso compacto :model-value="giroSeleccion" :min="0" :max="359" :paso="15" unidad="°" etiqueta="Giro" @update:model-value="giroNumerico" />
        </label>
        <button class="herr icono" @click="enderezar" title="Enderezar">⊾</button>
        <div class="menu-contenedor">
          <button class="herr icono" :class="{ on: menuMas }" @click="menuMas = !menuMas" title="Más acciones">⋮</button>
          <div v-if="menuMas" class="menu">
            <button @click="copiarSeleccion">Copiar <kbd>Ctrl+C</kbd></button>
            <button :disabled="!portapapeles.length" @click="pegar">Pegar como nuevos <kbd>Ctrl+V</kbd></button>
            <div class="menu-numerar">
              <span>Numerar desde</span>
              <input type="number" min="0" v-model.number="numeracion.inicio" />
              <select v-model="numeracion.orden">
                <option value="filas">por filas</option>
                <option value="columnas">por columnas</option>
              </select>
              <button class="btn btn-sm" :disabled="ocupado || !aNumerar.length" @click="numerarSeleccion">Aplicar</button>
            </div>
            <input ref="entradaFoto" type="file" accept="image/*" class="oculto" @change="subirFoto" />
            <button @click="entradaFoto?.click()">Agregar foto…</button>
            <button @click="ubicacionSeleccion">Ubicación…</button>
            <button @click="quitarDelMapa">Quitar del plano</button>
            <button v-if="accionBloqueo" @click="alternarBloqueo()">{{ accionBloqueo }}</button>
            <button class="peligro" @click="eliminarCasetas">Eliminar puestos</button>
          </div>
        </div>
      </div>

      <aside v-if="textoSel && enDiseno" class="flotante panel-texto">
        <div class="panel-cab">
          <strong>{{ textoSel.fondo ? 'Zona' : 'Texto' }}</strong>
          <button class="cerrar" @click="textoSelId = null" title="Cerrar (Esc)">✕</button>
        </div>
        <input ref="entradaTexto" class="control" :value="contenidoEdit" maxlength="200" placeholder="Escribe el texto"
               @input="escribirTexto($event.target.value)" @blur="terminarTexto" />
        <div class="fila-control">
          <span>Letra</span>
          <NumeroPaso compacto :model-value="pct(textoSel.tamano, 0.02)" :min="0.4" :max="20" :paso="0.1" :decimales="2" unidad="%"
                      etiqueta="Tamaño de letra" @update:model-value="editarTexto({ tamano: $event / 100 })" />
        </div>
        <div class="fila-control">
          <span>Giro</span>
          <NumeroPaso compacto :model-value="textoSel.rotacion || 0" :min="-180" :max="180" :paso="1" unidad="°"
                      etiqueta="Giro" @update:model-value="editarTexto({ rotacion: $event })" />
          <button type="button" class="herr icono" title="Girar 15° a la izquierda (Shift+R)" @click="girarTexto(-GIRO_PASO)">⟲</button>
          <button type="button" class="herr icono" title="Girar 15° a la derecha (R)" @click="girarTexto(GIRO_PASO)">⟳</button>
        </div>
        <div class="fila-control colores">
          <label>Color <input type="color" :value="textoSel.color || '#111827'" @input="editarTexto({ color: $event.target.value })" /></label>
          <label>Borde <input type="color" :value="textoSel.colorBorde || '#ffffff'" @input="editarTexto({ colorBorde: $event.target.value })" /></label>
        </div>
        <div v-if="!textoSel.fondo" class="fila-control">
          <span>Borde</span>
          <NumeroPaso compacto :model-value="Math.round((textoSel.grosorBorde ?? 0.15) * 100)" :min="0" :max="40" :paso="1"
                      etiqueta="Grosor del borde" @update:model-value="editarTexto({ grosorBorde: $event / 100 })" />
        </div>
        <label class="check">
          <input type="checkbox" :checked="Boolean(textoSel.fondo)"
                 @change="editarTexto($event.target.checked ? { fondo: '#7c5cff', ancho: textoSel.ancho || 0.16, alto: textoSel.alto || 0.08 } : { fondo: '' })" />
          Con recuadro de color (zona)
        </label>
        <template v-if="textoSel.fondo">
          <div class="fila-control colores">
            <label>Recuadro <input type="color" :value="textoSel.fondo" @input="editarTexto({ fondo: $event.target.value })" /></label>
          </div>
          <div class="fila-control">
            <span>Ancho</span>
            <NumeroPaso compacto :model-value="pct(textoSel.ancho, 0.16)" :min="2" :max="100" :paso="0.5" :decimales="1" unidad="%"
                        etiqueta="Ancho de la zona" @update:model-value="editarTexto({ ancho: $event / 100 })" />
          </div>
          <div class="fila-control">
            <span>Alto</span>
            <NumeroPaso compacto :model-value="pct(textoSel.alto, 0.08)" :min="2" :max="100" :paso="0.5" :decimales="1" unidad="%"
                        etiqueta="Alto de la zona" @update:model-value="editarTexto({ alto: $event / 100 })" />
          </div>
          <label class="check"><input v-model="textoSigueZona" type="checkbox" /> La letra crece y se achica con la zona</label>
        </template>
        <div class="acciones-texto">
          <button class="btn btn-sm" title="Duplicar (Ctrl+D) · también puedes copiar con Ctrl+C y pegar con Ctrl+V" @click="duplicarTexto">⧉ Duplicar</button>
          <button class="btn btn-sm btn-peligro" @click="borrarTexto">{{ textoSel.fondo ? 'Eliminar zona' : 'Eliminar texto' }}</button>
        </div>
      </aside>

      <ModalForma v-if="modalForma" :aspecto="planoTienda.aspecto || 1" :con-fondo="conFondoPropio" :ocupado="ocupado"
                  objetos="puestos, textos y zonas" @aplicar="aplicarForma" @quitar-fondo="quitarFondo" @cerrar="modalForma = false" />

      <div v-if="cajonCategorias" class="cajon-fondo" @click.self="cerrarCategorias">
        <aside class="cajon" role="dialog" aria-modal="true" aria-label="Categorías y precios">
          <header class="cajon-cab">
            <div><strong>🏷️ Categorías y precios</strong><p>Lo que cambies aquí se ve en el plano al cerrar este panel.</p></div>
            <button type="button" class="btn btn-fantasma btn-icono" aria-label="Cerrar" @click="cerrarCategorias">✕</button>
          </header>
          <div class="cajon-cuerpo desliza"><Categorias :edicion="edicion" :aspecto="planoTienda.aspecto" /></div>
        </aside>
      </div>
      <ModalFondo v-if="modalFondo" :fondo-url="planoTienda.src" acepta="image/png,image/jpeg,application/pdf"
                  sobre="los puestos, los textos y las zonas" :ocupado="ocupado"
                  @elegir="subirPlano" @quitar="quitarFondo" @cerrar="modalFondo = false" />

      <div v-if="ayudaAbierta" class="ayuda-velo" @click.self="cerrarAyuda">
        <section class="ayuda-caja" role="dialog" aria-modal="true" aria-label="Cómo se usa el editor del plano">
          <h3>Cómo armar el plano de la actividad</h3>
          <ol>
            <li><b>Gestión / Diseño:</b> en Gestión ves y consultas puestos; en <b>Diseño</b> los creas, mueves y cambias de tamaño.</li>
            <li><b>＋ Puesto:</b> elige una categoría y haz clic en el plano (o arrastra para colocar una fila entera).</li>
            <li><b>T Texto y ▭ Zona:</b> rotula el plano (Ingreso, Baños…) o marca áreas con recuadro de color (Escenario, Patio de comidas). Arrastra para mover; la esquina blanca cambia el tamaño de la zona.</li>
            <li><b>🖼 Fondo:</b> pon una imagen o PDF del lugar debajo del plano, cámbiala o quítala para trabajar sobre un lienzo liso.</li>
            <li><b>📐 Forma:</b> elige si el plano es horizontal, cuadrado o vertical, o escribe las medidas del lugar en metros.</li>
            <li><b>📐 Guías:</b> al mover algo aparecen líneas cuando queda alineado con otro elemento o con el centro, y se ajusta solo. Mantén <kbd>Alt</kbd> para moverlo libre. <b>⊞ Cuadrícula</b> solo dibuja referencias; <b>🧲 Imán</b> obliga a caer en ella.</li>
            <li><b>Precisión:</b> con algo seleccionado, las <kbd>flechas</kbd> lo mueven fino (con <kbd>Shift</kbd>, más rápido). Tamaño y giro se escriben con números o con los botones − / +.</li>
            <li><b>Zonas:</b> la esquina blanca cambia el tamaño (la letra la acompaña), el círculo de arriba gira. <kbd>Ctrl+C</kbd> / <kbd>Ctrl+V</kbd> copian y pegan; <kbd>Ctrl+D</kbd> duplica.</li>
            <li><b>Categorías:</b> «＋ Nueva» en el panel de la izquierda; el lápiz ✏️ la edita y ves el cambio de tamaño y color en el plano al instante.</li>
            <li><b>Guardar:</b> los puestos que mueves o cambias de tamaño se guardan con «Guardar» o Ctrl + S. Los textos, las zonas, el fondo y la forma se guardan solos al instante (verás «Guardando…» y «Guardado ✓»). Todo llega a la página pública sin recargar.</li>
            <li><b>Vista pública e Imagen:</b> mira cómo lo ve el público o descarga el plano como imagen para compartirlo.</li>
          </ol>
          <div class="acciones-ayuda"><button class="btn btn-primario" @click="cerrarAyuda">Entendido</button></div>
        </section>
      </div>

      <aside v-if="fichaPuesto" class="flotante panel-ficha" :class="{ atenuado: arrastrandoPin }">
        <div class="panel-cab">
          <div>
            <strong>Puesto #{{ fichaPuesto.codigo }}</strong>
            <span class="badge-estado" :class="`b-${fichaPuesto.estado}`">{{ ETIQUETA_ESTADO[fichaPuesto.estado] }}</span>
            <span v-if="venta?.pagado" class="badge-estado b-pagado">Pagado</span>
          </div>
          <button class="cerrar" @click="limpiarSeleccion" title="Cerrar (Esc)">✕</button>
        </div>

        <div class="cuerpo-ficha desliza">
          <div class="dato-categoria">
            <span class="muestra" :class="`forma-${fichaPuesto.forma || 'cuadrado'}`" :style="{ background: fichaPuesto.color || '#94a3b8' }"></span>
            <select v-if="enDiseno && (fichaPuesto.estado === 'L' || fichaPuesto.estado === 'X')" class="control control-cat"
                    :value="fichaPuesto.categoriaId" title="Cambiar de categoría" @change="cambiarCategoria">
              <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
            </select>
            <span v-else>{{ fichaPuesto.categoria }}</span>
            <button v-if="puedeCategorias" type="button" class="btn btn-sm btn-fantasma" title="Editar la categoría: precio, tamaño, color…" @click="abrirCategoria(fichaPuesto.categoriaId)">✏️ Categoría</button>
          </div>

          <div v-if="fichaPuesto.estado === 'O' || fichaPuesto.estado === 'T'" class="bloque-venta">
            <p v-if="cargandoVenta" class="muted">Cargando venta…</p>
            <template v-else-if="venta">
              <div class="par"><span>Expositor</span><strong>{{ venta.expositor || '—' }}</strong></div>
              <div v-if="venta.actividad" class="par"><span>Actividad</span><span>{{ venta.actividad }}</span></div>
              <div v-if="venta.representante" class="par"><span>Representante</span><span>{{ venta.representante }}<template v-if="venta.celular"> · {{ venta.celular }}</template></span></div>
              <div v-if="venta.vendedor" class="par"><span>Vendedor</span><span>{{ venta.vendedor }}</span></div>
              <div class="par"><span>Cobro</span><span>{{ venta.pagado ? 'Comprobante adjunto' : 'Pendiente de comprobante' }}</span></div>
            </template>
            <p v-else class="muted">{{ fichaPuesto.estado === 'T' ? 'En proceso de venta' : 'Sin datos de venta en esta edición' }}</p>
          </div>

          <fieldset :disabled="!puedeDisenar || guardandoFicha">
            <div class="dos">
              <label class="campo"><span>Número</span><input class="control" v-model="ficha.codigo" maxlength="20" /></label>
              <label class="campo"><span>Medida</span><input class="control" v-model="ficha.tamano" maxlength="40" placeholder="3x3" /></label>
            </div>
            <p class="medida">{{ medidaLegible(ficha.tamano) }} · escala visual {{ Math.round((fichaPuesto.mapaEscala ?? 1) * 100) }}%</p>
            <div v-if="enDiseno" class="dos">
              <label class="campo"><span>Tamaño</span>
                <NumeroPaso :model-value="Math.round(escalaSeleccion * 100)" :min="ESCALA_MIN * 100" :max="ESCALA_MAX * 100" :paso="5" unidad="%"
                            etiqueta="Tamaño respecto a su categoría" @update:model-value="escalaNumerica" />
              </label>
              <label class="campo"><span>Giro</span>
                <NumeroPaso :model-value="giroSeleccion" :min="0" :max="359" :paso="15" unidad="°" etiqueta="Giro" @update:model-value="giroNumerico" />
              </label>
            </div>

            <div class="campo">
              <span>Precio (Bs)</span>
              <label class="check"><input type="checkbox" v-model="ficha.precioPropio" /> Precio propio</label>
              <input v-if="ficha.precioPropio" class="control" type="number" min="0" step="0.01" v-model="ficha.precio" />
              <p v-else class="muted chico">Usa el de su categoría: {{ precioCategoria ?? fichaPuesto.precio }} Bs</p>
            </div>

            <label class="campo"><span>Ubicación</span><input class="control" v-model="ficha.referencia" maxlength="200" placeholder="Ej. frente a la puerta 3" /></label>
            <label class="campo"><span>Observaciones</span><textarea class="control" rows="3" v-model="ficha.observacion" maxlength="500" placeholder="Notas internas del puesto"></textarea></label>
          </fieldset>

          <div v-if="puedeDisenar" class="acciones-ficha">
            <button v-if="fichaPuesto.estado === 'L' || fichaPuesto.estado === 'X'" class="btn btn-sm"
                    :disabled="ocupado" @click="alternarBloqueo([fichaPuesto])">
              {{ fichaPuesto.estado === 'X' ? 'Desbloquear' : 'Bloquear' }}
            </button>
            <button class="btn btn-primario btn-sm crecer" :disabled="!fichaCambiada || guardandoFicha" @click="guardarFicha">
              {{ guardandoFicha ? 'Guardando…' : 'Guardar cambios' }}
            </button>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.editor { display: flex; flex-direction: column; flex: 1; min-height: 0; height: 100%; }

.cajon-fondo { position: fixed; inset: 0; z-index: 950; background: rgba(15, 18, 25, 0.45); display: flex; justify-content: flex-end; }
.cajon {
  width: min(760px, 100vw); height: 100%; background: var(--bg, var(--panel)); display: flex; flex-direction: column;
  box-shadow: var(--sombra-md); border-left: 1px solid var(--border);
}
.cajon-cab {
  display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; padding: 0.6rem 1rem;
  border-bottom: 1px solid var(--border); background: var(--panel);
}
.cajon-cab p { margin: 0.1rem 0 0; font-size: 0.8rem; color: var(--muted); }
.cajon-cuerpo { flex: 1; overflow: auto; padding: 1rem; }
.barra {
  display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;
  padding: 0.45rem 0.75rem; background: var(--panel); border-bottom: 1px solid var(--border);
}
.grupo { display: flex; align-items: center; gap: 0.3rem; }
.grupo + .grupo { padding-left: 0.5rem; border-left: 1px solid var(--border); }
.derecha { margin-left: auto; border-left: none !important; }

.segmentado { display: inline-flex; padding: 2px; border-radius: 9px; background: var(--bg); border: 1px solid var(--border); }
.segmentado button {
  border: none; background: transparent; color: var(--text); font: inherit; font-size: 0.85rem; font-weight: 600;
  padding: 0.3rem 0.8rem; border-radius: 7px; cursor: pointer;
}
.segmentado button.on { background: var(--acento); color: var(--acento-texto); }

.volver { text-decoration: none; display: inline-flex; align-items: center; }
.herr {
  display: inline-flex; align-items: center; gap: 0.3rem;
  border: 1px solid var(--border); background: var(--panel); color: var(--text);
  font: inherit; font-size: 0.84rem; font-weight: 500;
  padding: 0.32rem 0.6rem; border-radius: var(--radio-sm); cursor: pointer; white-space: nowrap;
}
.herr:hover:not(:disabled) { border-color: var(--acento); }
.herr.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.herr:disabled { opacity: 0.45; cursor: default; }
.herr.icono { width: 2rem; justify-content: center; padding: 0.32rem 0; }

.estado-guardado { font-size: 0.8rem; color: var(--ok); font-weight: 600; min-width: 5.5rem; text-align: right; }
.estado-guardado.pendiente { color: var(--tramite); }
.oculto { display: none; }

.zona { position: relative; flex: 1; min-height: 0; display: flex; flex-direction: column; }
.zona :deep(.viewport) { background: var(--bg); }

.flotante {
  position: absolute; z-index: 20;
  background: var(--panel); color: var(--text);
  border: 1px solid var(--border); border-radius: var(--radio); box-shadow: var(--sombra-md);
}

.ayuda {
  position: absolute; top: 0.6rem; left: 50%; transform: translateX(-50%); z-index: 15;
  margin: 0; padding: 0.3rem 0.75rem; border-radius: 999px;
  font-size: 0.78rem; color: var(--text); pointer-events: none;
  background: color-mix(in srgb, var(--panel) 88%, transparent); border: 1px solid var(--border);
  max-width: calc(100% - 2rem); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}

.panel-categorias { top: 0.6rem; left: 0.6rem; width: 250px; max-height: calc(100% - 4.5rem); display: flex; flex-direction: column; overflow: hidden; }
.panel-categorias .cabecera { display: flex; align-items: center; gap: 0.3rem; padding: 0.35rem 0.4rem 0.35rem 0.2rem; }
.panel-categorias .plegar {
  flex: 1; display: flex; justify-content: space-between; align-items: center;
  border: none; background: none; color: var(--text); font: inherit; font-weight: 700; font-size: 0.85rem;
  padding: 0.3rem 0.5rem; cursor: pointer;
}
.panel-categorias .nueva { padding: 0.25rem 0.55rem; font-size: 0.78rem; }
.panel-categorias .vacia { margin: 0; padding: 0.4rem 0.8rem 0.7rem; font-size: 0.8rem; color: var(--muted); line-height: 1.4; }
.lista-cat li { display: flex; align-items: center; gap: 0.15rem; border-radius: 6px; }
.lista-cat li.on { background: var(--acento-suave); box-shadow: inset 0 0 0 1px var(--acento); }
.lista-cat .elegir { flex: 1; min-width: 0; }
.lista-cat .editar { width: auto; flex: none; border: 0; background: none; cursor: pointer; padding: 0.25rem 0.35rem; border-radius: 6px; font-size: 0.85rem; opacity: 0.65; }
.lista-cat .editar:hover { opacity: 1; background: var(--bg); }
.lista-cat .cuenta.completa { color: var(--ok); font-weight: 700; }
.lista-cat { list-style: none; margin: 0; padding: 0 0.35rem; overflow-y: auto; flex: 1; }
.lista-cat button {
  width: 100%; display: flex; align-items: center; gap: 0.5rem; text-align: left;
  border: 1px solid transparent; background: none; color: var(--text); border-radius: 6px;
  padding: 0.35rem 0.45rem; font: inherit; font-size: 0.83rem; cursor: pointer;
}
.lista-cat .elegir:hover { background: var(--bg); }
.lista-cat .nom { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lista-cat .cuenta { color: var(--muted); font-variant-numeric: tabular-nums; font-size: 0.78rem; }
.pie-cat { display: flex; align-items: center; justify-content: space-between; gap: 0.4rem; padding: 0.5rem 0.7rem 0.2rem; border-top: 1px solid var(--border); }
.mini { display: flex; align-items: center; gap: 0.3rem; font-size: 0.78rem; color: var(--muted); }
.mini input { width: 3.4rem; padding: 0.2rem 0.3rem; border: 1px solid var(--border); border-radius: 6px; background: var(--panel); color: var(--text); font: inherit; }
.enlace { display: block; padding: 0.35rem 0.7rem 0.6rem; font-size: 0.78rem; background: none; border: 0; color: var(--acento); cursor: pointer; font-weight: 600; text-align: left; }

.muestra { width: 14px; height: 14px; flex: none; display: inline-block; }
.muestra.forma-cuadrado { border-radius: 3px; }
.muestra.forma-rectangulo { width: 18px; height: 11px; border-radius: 2px; }
.muestra.forma-circulo { border-radius: 50%; }
.muestra.forma-triangulo { clip-path: polygon(50% 0, 100% 100%, 0 100%); }
.muestra.forma-hexagono { clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%); }

.leyenda {
  left: 0.6rem; bottom: 0.6rem; display: flex; flex-wrap: wrap; gap: 0.75rem;
  padding: 0.35rem 0.7rem; font-size: 0.76rem;
}
.leyenda .item { display: inline-flex; align-items: center; gap: 0.3rem; }
.punto { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.p-libre { background: var(--panel); border: 2px solid var(--libre); }
.p-tramite { background: var(--tramite); }
.p-ocupado { background: var(--ocupado); }
.p-bloqueado { background: var(--bloqueado); }
.p-pagado { background: var(--ok); box-shadow: inset 0 0 0 2px var(--ocupado); }

.barra-seleccion {
  left: 50%; bottom: 0.6rem; transform: translateX(-50%);
  display: flex; align-items: center; gap: 0.35rem; padding: 0.35rem 0.5rem;
}
.cuenta-sel { font-size: 0.8rem; font-weight: 700; padding: 0 0.3rem; }
.deslizador { display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.78rem; font-variant-numeric: tabular-nums; }
.deslizador input { width: 110px; accent-color: var(--acento); }
.deslizador.ancho input { width: 100%; }
.deslizador.ancho { display: flex; }

.menu-contenedor { position: relative; }
.menu {
  position: absolute; right: 0; bottom: calc(100% + 0.4rem); min-width: 230px;
  display: flex; flex-direction: column; padding: 0.3rem;
  background: var(--panel); border: 1px solid var(--border); border-radius: var(--radio); box-shadow: var(--sombra-md);
}
.menu > button {
  display: flex; justify-content: space-between; gap: 1rem; text-align: left;
  border: none; background: none; color: var(--text); font: inherit; font-size: 0.85rem;
  padding: 0.45rem 0.6rem; border-radius: 6px; cursor: pointer;
}
.menu > button:hover:not(:disabled) { background: var(--bg); }
.menu > button:disabled { opacity: 0.45; cursor: default; }
.menu > button.peligro { color: var(--danger); }
.menu kbd { font: inherit; font-size: 0.72rem; color: var(--muted); }
.menu-numerar {
  display: flex; flex-wrap: wrap; align-items: center; gap: 0.35rem;
  padding: 0.45rem 0.6rem; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border);
  font-size: 0.8rem;
}
.menu-numerar input, .menu-numerar select {
  width: 4rem; padding: 0.2rem 0.3rem; border: 1px solid var(--border); border-radius: 6px;
  background: var(--panel); color: var(--text); font: inherit;
}
.menu-numerar select { width: auto; }

.panel-cab { display: flex; justify-content: space-between; align-items: flex-start; gap: 0.5rem; }
.panel-cab strong { font-size: 1rem; margin-right: 0.4rem; }
.cerrar { border: none; background: none; color: var(--muted); font-size: 1rem; cursor: pointer; padding: 0.1rem 0.3rem; }
.cerrar:hover { color: var(--text); }

.panel-texto {
  top: 0.6rem; right: 0.6rem; width: 290px; padding: 0.7rem;
  display: flex; flex-direction: column; gap: 0.55rem;
}
.fila-control { display: flex; align-items: center; gap: 0.5rem; font-size: 0.8rem; }
.fila-control > span { width: 3.6rem; color: var(--muted); }
.fila-control input[type='range'] { flex: 1; accent-color: var(--acento); }
.colores { justify-content: space-between; }
.colores label { display: flex; align-items: center; gap: 0.35rem; }
.colores input { width: 2.2rem; height: 1.8rem; padding: 0; border: 1px solid var(--border); border-radius: 6px; background: none; }

.panel-ficha {
  top: 0.6rem; right: 0.6rem; bottom: 0.6rem; width: 320px; padding: 0.85rem;
  display: flex; flex-direction: column; gap: 0.6rem; overflow: hidden;
}
.cuerpo-ficha { display: flex; flex-direction: column; gap: 0.65rem; overflow-y: auto; padding-right: 0.15rem; }
.cuerpo-ficha fieldset { border: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.6rem; min-width: 0; }
.dos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; }
.dato-categoria { display: flex; align-items: center; gap: 0.45rem; font-weight: 600; font-size: 0.9rem; }
.control-cat { padding: 0.3rem 0.5rem; font-weight: 600; }
.badge-estado { display: inline-block; font-size: 0.7rem; font-weight: 700; padding: 0.12rem 0.5rem; border-radius: 999px; vertical-align: middle; margin-right: 0.25rem; }
.b-L { background: color-mix(in srgb, var(--libre) 18%, transparent); color: var(--libre); }
.b-T { background: color-mix(in srgb, var(--tramite) 18%, transparent); color: var(--tramite); }
.b-O { background: color-mix(in srgb, var(--ocupado) 18%, transparent); color: var(--ocupado); }
.b-X { background: color-mix(in srgb, var(--bloqueado) 25%, transparent); color: var(--text); }
.b-pagado { background: color-mix(in srgb, var(--ok) 18%, transparent); color: var(--ok); }
.bloque-venta { border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.55rem 0.65rem; display: flex; flex-direction: column; gap: 0.3rem; background: var(--bg); }
.par { display: flex; justify-content: space-between; gap: 0.6rem; font-size: 0.82rem; }
.par > span:first-child { color: var(--muted); flex: none; }
.par > :last-child { text-align: right; }
.medida { margin: -0.2rem 0 0; font-size: 0.78rem; color: var(--muted); }
.check { display: flex; align-items: center; gap: 0.4rem; font-size: 0.85rem; color: var(--text); font-weight: 500; }
.chico { font-size: 0.8rem; margin: 0; }
.acciones-ficha { display: flex; gap: 0.5rem; padding-top: 0.2rem; margin-top: auto; }

.plano { position: relative; width: 100%; background: #f4f5fb; }
.ayuda-velo { position: fixed; inset: 0; z-index: 90; background: rgba(2, 6, 23, 0.55); display: grid; place-items: center; padding: 1rem; }
.ayuda-caja { background: var(--panel); color: var(--text); border-radius: var(--radio); box-shadow: var(--sombra-md); padding: 1.2rem 1.4rem; max-width: 600px; width: 100%; max-height: 90vh; overflow: auto; display: flex; flex-direction: column; gap: 0.7rem; }
.ayuda-caja h3 { margin: 0; }
.ayuda-caja ol { margin: 0; padding-left: 1.2rem; display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.92rem; }
.acciones-ayuda { display: flex; justify-content: flex-end; }
.plano img {
  width: 100%; height: auto; display: block;
  pointer-events: none; user-select: none; -webkit-user-select: none;
  transform: translateZ(0);
}
.herr-colocar, .herr-texto, .herr-zona { cursor: crosshair; }
.herr-seleccionar { cursor: default; }

.rejilla {
  position: absolute; inset: 0; pointer-events: none;
  --linea: calc(var(--mundo, 1200) * 0.0007px);
  background-image:
    linear-gradient(to right, rgba(79, 70, 229, 0.35) var(--linea), transparent var(--linea)),
    linear-gradient(to bottom, rgba(79, 70, 229, 0.35) var(--linea), transparent var(--linea));
  background-size: 2% calc(2% / var(--aspecto, 1));
}

.pin {
  position: absolute; width: 100px; height: var(--alto, 100px); z-index: 1;
  transform-origin: 0 0;
  transform: scale(calc(var(--pin, 20) / 100)) rotate(var(--giro, 0deg)) translate(-50%, -50%);
  --borde: 5px;
  --aro: 12px;
  box-shadow: 0 0 0 var(--borde) rgba(255, 255, 255, 0.9);
}
.disenando .pin { cursor: move; }
.herr-gestion .pin { cursor: pointer; }
.pin.sel { outline: var(--aro) solid var(--acento); outline-offset: var(--borde); z-index: 4; }
.pin.est-X { filter: grayscale(1) brightness(0.85); }
.pin.est-O { box-shadow: 0 0 0 var(--aro) var(--ocupado); }
.pin.est-T { box-shadow: 0 0 0 var(--aro) var(--tramite); }
.marca-estado {
  position: absolute; top: -14px; right: -14px; width: 34px; height: 34px; border-radius: 50%;
  border: 6px solid #fff; pointer-events: none;
}
.est-T .marca-estado { background: var(--tramite); }
.est-O .marca-estado { background: var(--ocupado); }
.pin.pagado .marca-estado { background: var(--ok); border-color: var(--ocupado); }
.est-X .marca-estado { background: #475569; }
.forma-cuadrado { border-radius: 10%; }
.forma-rectangulo { border-radius: 6%; }
.forma-circulo { border-radius: 50%; }
.forma-triangulo { clip-path: polygon(50% 0%, 100% 100%, 0% 100%); }
.forma-hexagono { clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%); }
.forma-triangulo .marca-estado, .forma-hexagono .marca-estado { top: 4px; right: 4px; }

.num-caseta {
  display: none; position: absolute; inset: 0;
  align-items: center; justify-content: center;
  transform: rotate(calc(-1 * var(--giro, 0deg))); transform-origin: 50% 50%;
  font-size: 46px; line-height: 1; font-weight: 700; font-variant-numeric: tabular-nums;
  color: #fff; text-shadow: 0 2px 4px rgba(2, 6, 23, 0.9), 0 0 4px rgba(2, 6, 23, 0.8);
  pointer-events: none; overflow: hidden;
}
.con-numeros .num-caseta { display: flex; }
.forma-triangulo .num-caseta { align-items: flex-end; padding-bottom: 10px; font-size: 36px; }

.plano :deep(.rotulo.tocable) { pointer-events: auto; cursor: move; }

.asa {
  position: absolute; z-index: 6;
  width: calc(var(--asa) * 1px); height: calc(var(--asa) * 1px);
  transform: translate(-50%, -50%);
  background: #fff; border: calc(var(--asa) * 0.14px) solid var(--acento); border-radius: 25%;
  cursor: nwse-resize; touch-action: none;
}
.asa.asa-fija, .asa-giro {
  width: calc(18px / var(--zoom, 1)); height: calc(18px / var(--zoom, 1));
  border-width: calc(3px / var(--zoom, 1)); box-shadow: 0 calc(1px / var(--zoom, 1)) calc(4px / var(--zoom, 1)) rgba(0, 0, 0, 0.35);
}
.asa.asa-fija::before, .asa-giro::before { content: ''; position: absolute; inset: calc(-8px / var(--zoom, 1)); }
.asa-giro {
  position: absolute; z-index: 7; transform: translate(-50%, -50%); border-radius: 50%; border-style: solid; border-color: var(--acento);
  background: #fff; color: var(--acento); display: grid; place-items: center; cursor: grab; touch-action: none;
  font-size: calc(12px / var(--zoom, 1)); line-height: 1; font-weight: 800;
}
.asa-giro:active { cursor: grabbing; }
.guia-alineacion { position: absolute; z-index: 8; pointer-events: none; background: #ec4899; }
.medidor {
  position: absolute; z-index: 9; pointer-events: none; display: flex; flex-direction: column; gap: calc(2px / var(--zoom, 1));
  transform: translate(calc(22px / var(--zoom, 1)), calc(-100% - 14px / var(--zoom, 1)));
  padding: calc(5px / var(--zoom, 1)) calc(8px / var(--zoom, 1)); border-radius: calc(8px / var(--zoom, 1));
  background: rgba(15, 23, 42, 0.92); color: #fff; font: 700 calc(12px / var(--zoom, 1)) / 1.35 system-ui, sans-serif; white-space: nowrap;
  box-shadow: 0 calc(4px / var(--zoom, 1)) calc(14px / var(--zoom, 1)) rgba(0, 0, 0, 0.3);
}
.medidor.alineado { background: #be185d; }
.medidor.izquierda { transform: translate(calc(-100% - 22px / var(--zoom, 1)), calc(-100% - 14px / var(--zoom, 1))); }
.medidor.abajo { transform: translate(calc(22px / var(--zoom, 1)), calc(14px / var(--zoom, 1))); }
.medidor.izquierda.abajo { transform: translate(calc(-100% - 22px / var(--zoom, 1)), calc(14px / var(--zoom, 1))); }
.panel-ficha, .barra-seleccion { transition: opacity 0.15s ease; }
.panel-ficha.atenuado { opacity: 0.12; pointer-events: none; }
.pin.referencia { outline: 8px dashed #ec4899; outline-offset: 6px; z-index: 3; }
.guia-alineacion.vertical { top: 0; bottom: 0; width: calc(1px / var(--zoom, 1)); transform: translateX(-50%); }
.guia-alineacion.horizontal { left: 0; right: 0; height: calc(1px / var(--zoom, 1)); transform: translateY(-50%); }
.pin.fantasma { opacity: 0.85; pointer-events: none; outline: 10px dashed var(--acento); outline-offset: 8px; box-shadow: none; z-index: 5; }
.con-numeros .pin.fantasma .num-caseta, .pin.fantasma .num-caseta { display: flex; }
.pin.previa { outline: 8px dashed var(--acento); outline-offset: 6px; }
.etiqueta-previa {
  position: absolute; z-index: 6; transform: translateX(-50%); pointer-events: none; white-space: nowrap;
  font: 800 calc(13px / var(--zoom, 1)) system-ui, sans-serif; color: #fff; background: var(--acento);
  padding: calc(3px / var(--zoom, 1)) calc(8px / var(--zoom, 1)); border-radius: 999px;
}
.aviso-colocar {
  top: 0.6rem; left: 50%; transform: translateX(-50%); z-index: 25; display: flex; align-items: center; gap: 0.7rem;
  padding: 0.55rem 0.7rem 0.55rem 0.8rem; max-width: min(560px, calc(100% - 2rem)); border-color: var(--acento);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--acento) 25%, transparent), var(--sombra-md);
}
.aviso-colocar > div { display: flex; flex-direction: column; gap: 0.1rem; font-size: 0.82rem; }
.aviso-colocar strong { font-size: 0.9rem; }
.aviso-colocar span:not(.muestra) { color: var(--muted); }
.muestra.grande { width: 22px; height: 22px; }
.numero-etiquetado { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.75rem; color: var(--muted); font-weight: 600; }
.numero-etiquetado .numero-paso { width: 118px; }
.fila-control .numero-paso { flex: 1; min-width: 112px; }
.acciones-texto { display: flex; gap: 0.4rem; }
.acciones-texto .btn { flex: 1; justify-content: center; }
.panel-texto .check { font-size: 0.8rem; }
.dato-categoria { flex-wrap: wrap; }
.guia {
  position: absolute; height: calc(var(--mundo, 1200) * 0.002px);
  background: var(--acento); transform-origin: 0 50%; opacity: 0.75; pointer-events: none;
}
.caja {
  position: absolute; border: calc(var(--mundo, 1200) * 0.0015px) dashed var(--acento);
  background: color-mix(in srgb, var(--acento) 10%, transparent); pointer-events: none;
}

@media (max-width: 820px) {
  .barra { gap: 0.35rem; padding: 0.4rem 0.5rem; }
  .grupo + .grupo { padding-left: 0.3rem; }
  .panel-ficha { left: 0.5rem; right: 0.5rem; top: auto; bottom: 0.5rem; width: auto; max-height: 62%; }
  .panel-texto { left: 0.5rem; right: 0.5rem; width: auto; }
  .panel-categorias { width: 200px; }
  .leyenda { display: none; }
  .ayuda { top: auto; bottom: 0.6rem; }
  .barra-seleccion { bottom: 3rem; }
}
</style>
