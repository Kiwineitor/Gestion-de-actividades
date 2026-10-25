<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { primeraPaginaEnCanvas } from '../ui/pdf';
import PanZoom from '../components/PanZoom.vue';
import RotuloPlano from '../components/RotuloPlano.vue';
import { apiFetch } from '../api';
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
const PASO_REJILLA = 0.02;
const GIRO_PASO = 15;
const ESTADOS = [
  { id: 'L', clase: 'libre' },
  { id: 'T', clase: 'tramite' },
  { id: 'O', clase: 'ocupado' },
  { id: 'X', clase: 'bloqueado' },
];

const auth = useAuthStore();
const router = useRouter();
const tienda = usePuestosStore();
const planoTienda = usePlanoStore();
const textosTienda = usePlanoTextosStore();

const puestos = computed(() => tienda.puestos);
const puedeDisenar = computed(() => auth.puedeEditarPlano);

const modo = ref('gestion');
const herramienta = ref(null);
const enDiseno = computed(() => modo.value === 'diseno' && puedeDisenar.value);

const rejilla = ref(leerPreferencia('editor.rejilla') === '1');
watch(rejilla, (v) => guardarPreferencia('editor.rejilla', v ? '1' : '0'));
const panelCategorias = ref(leerPreferencia('editor.categorias') !== '0');
watch(panelCategorias, (v) => guardarPreferencia('editor.categorias', v ? '1' : '0'));

function leerPreferencia(clave) {
  try { return localStorage.getItem(clave); } catch { return null; }
}
function guardarPreferencia(clave, valor) {
  try { localStorage.setItem(clave, valor); } catch { /* sin almacenamiento */ }
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

const plano = ref(null);
const lienzo = ref(null);
const guiaEl = ref(null);
const cajaEl = ref(null);
const entradaFoto = ref(null);
const entradaPlano = ref(null);
const entradaTexto = ref(null);

let quitarGuardia = null;

const buscar = (id) => puestos.value.find((p) => p.id === id);
const geom = (p) => ({
  id: p.id, mapaX: p.mapaX, mapaY: p.mapaY,
  mapaEscala: p.mapaEscala ?? 1, mapaRotacion: p.mapaRotacion ?? 0,
});
const aRejilla = (v) => (rejilla.value ? Math.round(v / PASO_REJILLA) * PASO_REJILLA : v);
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
  if (!confirm(`¿Pasar el puesto ${p.codigo} a ${destino?.nombre ?? 'otra categoría'}? Toma su color, forma y precio.`)) {
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

const textoSelId = ref(null);
const textoSel = computed(() => textosTienda.textos.find((t) => t.id === textoSelId.value) || null);

const ayuda = computed(() => {
  if (!enDiseno.value) return 'Arrastra el mapa para moverte · clic en un puesto para ver sus datos';
  if (herramienta.value === 'texto') return 'Haz clic en el mapa para agregar texto';
  if (herramienta.value === 'colocar') {
    return activa.value
      ? `Clic coloca un puesto de ${activa.value.nombre}; arrastra para colocar una fila`
      : 'Elige una categoría para colocar puestos';
  }
  if (herramienta.value === 'seleccionar') return 'Arrastra para seleccionar varios puestos';
  return 'Arrastra un puesto para moverlo · Shift + arrastre selecciona varios';
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
  const r = plano.value.getBoundingClientRect();
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
  if (enDiseno.value && herramienta.value === 'texto') {
    e.stopPropagation();
    herramienta.value = null;
    crearTexto(p);
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
  if (enDiseno.value && (herramienta.value === 'colocar' || herramienta.value === 'texto')) return;
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
  arrastre = { antes: seleccionadas.value.map(geom), x0: q.x, y0: q.y, movido: false, dx: 0, dy: 0 };
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
  arrastreTexto = { id: t.id, x0: q.x, y0: q.y, base: { x: t.mapaX ?? 0.5, y: t.mapaY ?? 0.5 }, movido: false };
}

function onMove(e) {
  if (arrastreTexto) {
    const q = norm(e);
    const dx = q.x - arrastreTexto.x0;
    const dy = q.y - arrastreTexto.y0;
    if (Math.abs(dx) + Math.abs(dy) > 0.0005) arrastreTexto.movido = true;
    const t = textosTienda.textos.find((x) => x.id === arrastreTexto.id);
    if (t) {
      t.mapaX = acotar(aRejilla(arrastreTexto.base.x + dx));
      t.mapaY = acotar(aRejilla(arrastreTexto.base.y + dy));
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
    const dx = q.x - arrastre.x0;
    const dy = q.y - arrastre.y0;
    if (Math.abs(dx) + Math.abs(dy) > 0.0005) arrastre.movido = true;
    arrastre.dx = dx;
    arrastre.dy = dy;
    for (const g of arrastre.antes) {
      const el = pinEls.get(g.id);
      if (!el) continue;
      el.style.left = `${acotar(aRejilla(g.mapaX + dx)) * 100}%`;
      el.style.top = `${acotar(aRejilla(g.mapaY + dy)) * 100}%`;
    }
  }
}

function onUp() {
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

const escalaSeleccion = computed(() => principal.value?.mapaEscala ?? 1);
let antesEscala = null;

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
  if (!confirm(`¿Eliminar ${objetivo.length} puesto(s)?\n\n${rotulo}\n\nNo se puede deshacer. Los que tengan ventas no se eliminarán.`)) return;
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
    await tienda.recargar();
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
    await tienda.recargar();
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
    await tienda.recargar();
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
  const texto = prompt('Ubicación de los puestos seleccionados (ej. frente a la puerta 3):', principal.value?.referencia || '');
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

async function subirPlano(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  const n = colocadas.value.length;
  const aviso = n
    ? `Hay ${n} puesto(s) colocados. Si el plano nuevo tiene otro encuadre quedarán fuera de lugar. ¿Continuar?`
    : '¿Reemplazar el plano?';
  if (!confirm(aviso)) return;
  ocupado.value = true;
  try {
    let subir = archivo;
    if (archivo.type === 'application/pdf') subir = new File([await pdfAImagen(archivo)], 'plano.png', { type: 'image/png' });
    const datos = new FormData();
    datos.append('archivo', subir);
    const r = await apiFetch('/api/app/plano', { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (r.ok && d.ok) {
      planoTienda.aplicar(d.plano);
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

const pendientesTexto = new Map();
let temporizadorTexto = null;

function editarTexto(campos, id = textoSelId.value) {
  const t = textosTienda.textos.find((x) => x.id === id);
  if (!t) return;
  Object.assign(t, campos);
  pendientesTexto.set(id, { ...(pendientesTexto.get(id) || {}), ...campos });
  clearTimeout(temporizadorTexto);
  temporizadorTexto = setTimeout(enviarTextos, 350);
}

async function enviarTextos() {
  for (const [id, cambios] of [...pendientesTexto]) {
    pendientesTexto.delete(id);
    try {
      const r = await apiFetch(`/api/app/plano-textos/${id}`, { method: 'PATCH', body: JSON.stringify(cambios) });
      if (!r.ok) { toast('No se pudo guardar el texto', 'error'); continue; }
      const d = await r.json();
      textosTienda.aplicar({ ...d, ...(pendientesTexto.get(id) || {}) });
    } catch (e) {
      toast(e.message, 'error');
    }
  }
}

async function crearTexto(punto) {
  try {
    const r = await apiFetch('/api/app/plano-textos', {
      method: 'POST',
      body: JSON.stringify({ contenido: 'TEXTO', mapaX: aRejilla(punto.x), mapaY: aRejilla(punto.y) }),
    });
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
  if (!t || !confirm(`¿Quitar el texto "${t.contenido}" del plano?`)) return;
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
  else if (e.ctrlKey && tecla === 'c') { e.preventDefault(); copiarSeleccion(); }
  else if (e.ctrlKey && tecla === 'v') { e.preventDefault(); pegar(); }
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
  router.push('/login');
}

onMounted(async () => {
  quitarGuardia = tienda.protegerLocales((id) => dirty.value.has(id));
  planoTienda.asegurar();
  textosTienda.asegurar();
  await tienda.asegurar(sesionCaducada);
  if (catSel.value == null && categorias.value.length) catSel.value = categorias.value[0].id;
});
onActivated(() => {
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
  if (pendientesTexto.size) enviarTextos();
  if (quitarGuardia) quitarGuardia();
});
</script>

<template>
  <div class="editor">
    <header class="barra">
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
                title="Agregar un texto al plano">T Texto</button>
      </div>

      <div class="grupo">
        <button class="herr" :class="{ on: rejilla }" :aria-pressed="rejilla" @click="rejilla = !rejilla"
                title="Mostrar la cuadrícula y alinear a ella">⊞ Rejilla</button>
        <button class="herr" :class="{ on: numerosVisibles }" :aria-pressed="numerosVisibles"
                @click="numerosVisibles = !numerosVisibles" title="Mostrar el número de cada puesto"># Números</button>
      </div>

      <div v-if="enDiseno" class="grupo">
        <input ref="entradaPlano" type="file" accept="image/png,image/jpeg,application/pdf" class="oculto" @change="subirPlano" />
        <button class="herr" :disabled="ocupado" @click="entradaPlano?.click()"
                :title="`Imagen o PDF del plano · actual ${planoTienda.plano.ancho}×${planoTienda.plano.alto}`">⇆ Cambiar plano</button>
      </div>

      <div class="grupo derecha">
        <span v-if="enDiseno" class="estado-guardado" :class="{ pendiente: dirty.size }">
          <template v-if="ocupado && dirty.size">Guardando…</template>
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
      <PanZoom :key="planoTienda.src" llenar :aspect="planoTienda.aspecto"
               :focus="{ x: 0.5, y: 0.45, scale: 1.4 }" :max-ancho="topeZoom">
        <div ref="lienzo" class="plano"
             :class="[`herr-${enDiseno ? herramienta || 'ninguna' : 'gestion'}`, { 'con-numeros': numerosVisibles, disenando: enDiseno }]"
             :style="{ aspectRatio: planoTienda.aspecto ? 1 / planoTienda.aspecto : 'auto', '--aspecto': planoTienda.aspecto || 1 }"
             @pointerdown="onDown" @pointermove="onMove" @pointerup="onUp" @pointercancel="onUp">
          <img ref="plano" :src="planoTienda.src" :key="planoTienda.src" alt="Plano"
               :width="planoTienda.plano.ancho" :height="planoTienda.plano.alto"
               decoding="async" draggable="false" />

          <div v-if="rejilla" class="rejilla"></div>

          <div v-for="p in colocadas" :key="p.id"
               :ref="(el) => (el ? pinEls.set(p.id, el) : pinEls.delete(p.id))"
               class="pin" :class="[`forma-${p.forma || 'cuadrado'}`, `est-${p.estado}`, { sel: seleccion.has(p.id), pagado: p.estado === 'O' && pagados.has(p.id) }]"
               :style="{ ...estiloPin(p), background: p.color || '#94a3b8' }"
               :title="`${p.categoria} ${p.codigo} · ${ETIQUETA_ESTADO[p.estado] || ''}`"
               @pointerdown="onPinDown($event, p)">
            <span class="num-caseta">{{ p.codigo }}</span>
            <span v-if="p.estado !== 'L'" class="marca-estado"></span>
          </div>

          <RotuloPlano v-for="t in textosTienda.textos" :key="t.id" :texto="t"
                       :seleccionado="textoSelId === t.id"
                       :class="{ tocable: enDiseno && herramienta !== 'colocar' }"
                       @pointerdown="onTextoDown($event, t)" />

          <div v-if="asaEstilo" class="asa" :style="asaEstilo" title="Arrastra para cambiar el tamaño"
               @pointerdown="onAsaDown"></div>
          <div v-if="lineaActiva" ref="guiaEl" class="guia"></div>
          <div v-if="cajaActiva" ref="cajaEl" class="caja"></div>
        </div>
      </PanZoom>

      <p class="ayuda">{{ ayuda }}</p>

      <section v-if="enDiseno && herramienta === 'colocar'" class="flotante panel-categorias" :class="{ plegado: !panelCategorias }">
        <button class="cabecera" @click="panelCategorias = !panelCategorias">
          <span>Categorías</span><span class="chevron">{{ panelCategorias ? '▾' : '▸' }}</span>
        </button>
        <template v-if="panelCategorias">
          <ul class="lista-cat">
            <li v-for="c in categorias" :key="c.id">
              <button :class="{ on: catSel === c.id }" @click="catSel = c.id">
                <span class="muestra" :class="`forma-${c.forma}`" :style="{ background: c.color || '#94a3b8' }"></span>
                <span class="nom">{{ c.nombre }}</span>
                <span class="cuenta">{{ c.puestas }}/{{ c.total }}</span>
              </button>
            </li>
          </ul>
          <div class="pie-cat">
            <label class="mini">Por fila <input type="number" min="1" max="60" v-model.number="porLinea" /></label>
            <button class="btn btn-sm" :disabled="ocupado || !activa" @click="agregarCaseta">＋ 1 puesto</button>
          </div>
          <router-link to="/categorias" class="enlace">Administrar categorías →</router-link>
        </template>
      </section>

      <div class="leyenda flotante">
        <span v-for="e in ESTADOS" :key="e.id" class="item">
          <i class="punto" :class="`p-${e.clase}`"></i>{{ ETIQUETA_ESTADO[e.id] }}
        </span>
        <span v-if="puedeDisenar" class="item"><i class="punto p-pagado"></i>Pagado</span>
      </div>

      <div v-if="enDiseno && seleccion.size" class="flotante barra-seleccion">
        <span class="cuenta-sel">{{ seleccion.size }} sel.</span>
        <label class="deslizador" title="Escala visual respecto al tamaño de su categoría">
          <input type="range" :min="ESCALA_MIN" :max="ESCALA_MAX" step="0.05" :value="escalaSeleccion"
                 @pointerdown="iniciarEscala" @input="fijarEscala($event.target.value)" @change="terminarEscala" />
          <span>{{ Math.round(escalaSeleccion * 100) }}%</span>
        </label>
        <button class="herr icono" @click="girar(-GIRO_PASO)" title="Girar a la izquierda (Shift+R)">⟲</button>
        <button class="herr icono" @click="girar(GIRO_PASO)" title="Girar a la derecha (R)">⟳</button>
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
          <strong>Texto</strong>
          <button class="cerrar" @click="textoSelId = null" title="Cerrar (Esc)">✕</button>
        </div>
        <input ref="entradaTexto" class="control" :value="textoSel.contenido" maxlength="200"
               @input="editarTexto({ contenido: $event.target.value })" />
        <label class="fila-control">
          <span>Tamaño</span>
          <input type="range" min="5" max="120" :value="aMilesimas(textoSel.tamano)"
                 @input="editarTexto({ tamano: Number($event.target.value) / 1000 })" />
        </label>
        <label class="fila-control">
          <span>Giro</span>
          <input type="range" min="-180" max="180" :value="textoSel.rotacion || 0"
                 @input="editarTexto({ rotacion: Number($event.target.value) })" />
        </label>
        <div class="fila-control colores">
          <label>Color <input type="color" :value="textoSel.color || '#111827'" @input="editarTexto({ color: $event.target.value })" /></label>
          <label>Borde <input type="color" :value="textoSel.colorBorde || '#ffffff'" @input="editarTexto({ colorBorde: $event.target.value })" /></label>
        </div>
        <label class="fila-control">
          <span>Grosor</span>
          <input type="range" min="0" max="400" :value="aMilesimas(textoSel.grosorBorde)"
                 @input="editarTexto({ grosorBorde: Number($event.target.value) / 1000 })" />
        </label>
        <button class="btn btn-sm btn-peligro" @click="borrarTexto">Eliminar texto</button>
      </aside>

      <aside v-if="fichaPuesto" class="flotante panel-ficha">
        <div class="panel-cab">
          <div>
            <strong>Puesto #{{ fichaPuesto.codigo }}</strong>
            <span class="badge-estado" :class="`b-${fichaPuesto.estado}`">{{ ETIQUETA_ESTADO[fichaPuesto.estado] }}</span>
            <span v-if="venta?.pagado" class="badge-estado b-pagado">Pagado</span>
          </div>
          <button class="cerrar" @click="limpiarSeleccion" title="Cerrar (Esc)">✕</button>
        </div>

        <div class="cuerpo-ficha">
          <div class="dato-categoria">
            <span class="muestra" :class="`forma-${fichaPuesto.forma || 'cuadrado'}`" :style="{ background: fichaPuesto.color || '#94a3b8' }"></span>
            <select v-if="enDiseno && (fichaPuesto.estado === 'L' || fichaPuesto.estado === 'X')" class="control control-cat"
                    :value="fichaPuesto.categoriaId" title="Cambiar de categoría" @change="cambiarCategoria">
              <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
            </select>
            <span v-else>{{ fichaPuesto.categoria }}</span>
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
            <label v-if="enDiseno" class="deslizador ancho" title="Tamaño en el plano respecto a su categoría">
              <input type="range" :min="ESCALA_MIN" :max="ESCALA_MAX" step="0.05" :value="escalaSeleccion"
                     @pointerdown="iniciarEscala" @input="fijarEscala($event.target.value)" @change="terminarEscala" />
            </label>

            <div class="campo">
              <span>Precio (Bs)</span>
              <label class="check"><input type="checkbox" v-model="ficha.precioPropio" /> Precio propio</label>
              <input v-if="ficha.precioPropio" class="control" type="number" min="0" step="1" v-model="ficha.precio" />
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

.panel-categorias { top: 0.6rem; left: 0.6rem; width: 230px; max-height: calc(100% - 4.5rem); display: flex; flex-direction: column; overflow: hidden; }
.panel-categorias .cabecera {
  display: flex; justify-content: space-between; align-items: center; width: 100%;
  border: none; background: none; color: var(--text); font: inherit; font-weight: 700; font-size: 0.85rem;
  padding: 0.55rem 0.7rem; cursor: pointer;
}
.lista-cat { list-style: none; margin: 0; padding: 0 0.35rem; overflow-y: auto; flex: 1; }
.lista-cat button {
  width: 100%; display: flex; align-items: center; gap: 0.5rem; text-align: left;
  border: 1px solid transparent; background: none; color: var(--text); border-radius: 6px;
  padding: 0.35rem 0.45rem; font: inherit; font-size: 0.83rem; cursor: pointer;
}
.lista-cat button:hover { background: var(--bg); }
.lista-cat button.on { background: var(--acento-suave); border-color: var(--acento); }
.lista-cat .nom { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lista-cat .cuenta { color: var(--muted); font-variant-numeric: tabular-nums; font-size: 0.78rem; }
.pie-cat { display: flex; align-items: center; justify-content: space-between; gap: 0.4rem; padding: 0.5rem 0.7rem 0.2rem; border-top: 1px solid var(--border); }
.mini { display: flex; align-items: center; gap: 0.3rem; font-size: 0.78rem; color: var(--muted); }
.mini input { width: 3.4rem; padding: 0.2rem 0.3rem; border: 1px solid var(--border); border-radius: 6px; background: var(--panel); color: var(--text); font: inherit; }
.enlace { display: block; padding: 0.35rem 0.7rem 0.6rem; font-size: 0.78rem; }

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
  top: 0.6rem; right: 0.6rem; width: 240px; padding: 0.7rem;
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

.plano { position: relative; width: 100%; }
.plano img {
  width: 100%; height: auto; display: block;
  pointer-events: none; user-select: none; -webkit-user-select: none;
  transform: translateZ(0);
}
.herr-colocar, .herr-texto { cursor: crosshair; }
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
  position: absolute; width: 100px; height: var(--alto, 100px);
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
