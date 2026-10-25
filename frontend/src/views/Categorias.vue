<script setup>
/**
 * Modulo de categorias: nombre, color, forma, tamaño en el plano y OPCIONES DE PRECIO.
 *
 * Lo que se toca aqui se ve al instante en el mapa de todos: el servidor redifunde las casetas
 * de la categoria por `/topic/puestos` en cada guardado, porque la apariencia y el precio de
 * una caseta los hereda de su categoria. No hay que recargar nada en la otra pantalla.
 *
 * Las OPCIONES son la parte nueva. Una categoria ya no tiene un precio, tiene una lista:
 * "PYMES" a 800 y "PYMES con tarima" a 1.200. Al registrar la venta se elige una por categoria.
 * Siempre hay exactamente una PREDETERMINADA —la que se aplica si nadie elige— y su precio es
 * el que el mapa canta en la ficha de la caseta.
 */
import { ref, reactive, computed, watch, onMounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { aviso, alertaConAccion } from '../ui/alerta';
import { usePlanoStore } from '../stores/plano';
import { medidaLegible } from '../mapa';

const planoTienda = usePlanoStore();

const categorias = ref([]);
const cargando = ref(true);
const filtro = ref('');
const guardando = ref(false);
const ocupado = ref(false);

const FORMAS = [
  { valor: 'cuadrado', etiqueta: 'Cuadrado' },
  { valor: 'rectangulo', etiqueta: 'Rectángulo' },
  { valor: 'circulo', etiqueta: 'Círculo' },
  { valor: 'triangulo', etiqueta: 'Triángulo' },
  { valor: 'hexagono', etiqueta: 'Hexágono' },
];
const etiquetaForma = (f) => FORMAS.find((x) => x.valor === f)?.etiqueta ?? 'Cuadrado';

const aMilesimas = (v) => Math.round((Number(v) || 0) * 1000);
const aFraccion = (v) => (Number(v) || 0) / 1000;

const modal = reactive({
  abierto: false, editando: null,
  nombre: '', color: '#3b82f6', forma: 'cuadrado', tamanoMilesimas: 12, cantidad: 10,
  proporcion: 1, tamano: '3x3', cantidadOriginal: 0, tamanoOriginal: '',
});

watch(() => modal.forma, (f, previa) => {
  if (!modal.abierto || f === previa) return;
  if (f === 'rectangulo' && modal.proporcion === 1) modal.proporcion = 0.6;
  if (f !== 'rectangulo') modal.proporcion = 1;
});

const vistaPrevia = computed(() => {
  const ancho = aFraccion(modal.tamanoMilesimas);
  const alto = (ancho * modal.proporcion) / (planoTienda.aspecto || 1);
  return Array.from({ length: 3 }, (_, i) => ({
    left: `${(0.5 + (i - 1) * ancho * 1.35) * 100}%`,
    top: '50%',
    width: `${ancho * 100}%`,
    height: `${alto * 100}%`,
  }));
});
const zoomVista = computed(() => Math.min(8, Math.max(1.5, 0.18 / Math.max(aFraccion(modal.tamanoMilesimas), 0.004))));

const modalOpcion = reactive({
  abierto: false, categoria: null, editando: null, nombre: '', precio: 0, predeterminada: false,
});

const filtradas = computed(() => {
  const q = filtro.value.trim().toLowerCase();
  if (!q) return categorias.value;
  return categorias.value.filter((c) => (c.nombre || '').toLowerCase().includes(q));
});

const bs = (n) => Number(n || 0).toLocaleString('es-BO');

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch('/api/app/categorias');
    categorias.value = await r.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}
onMounted(() => {
  planoTienda.asegurar();
  cargar();
});

// ---------------------------------------------------------------- categoria

function abrirCrear() {
  Object.assign(modal, {
    abierto: true, editando: null,
    nombre: '', color: '#3b82f6', forma: 'cuadrado', tamanoMilesimas: 12, cantidad: 10,
    proporcion: 1, tamano: '3x3', cantidadOriginal: 0, tamanoOriginal: '',
  });
}

function abrirEditar(c) {
  Object.assign(modal, {
    abierto: true, editando: c,
    nombre: c.nombre || '',
    color: c.color || '#3b82f6',
    forma: c.forma || 'cuadrado',
    tamanoMilesimas: aMilesimas(c.tamanoMapa) || 12,
    proporcion: c.mapaProporcion ?? 1,
    cantidad: c.cantidad ?? 0,
    cantidadOriginal: c.cantidad ?? 0,
    tamano: c.tamano || '',
    tamanoOriginal: c.tamano || '',
  });
}

async function ajustarCantidad(id) {
  const r = await apiFetch(`/api/app/categorias/${id}/cantidad`, {
    method: 'PATCH', body: JSON.stringify({ cantidad: Number(modal.cantidad) || 0 }),
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || d.ok === false) {
    toast(d.mensaje || 'No se pudo ajustar la cantidad', 'error');
    return;
  }
  if (d.noQuitadas) toast(`${d.noQuitadas} puesto(s) no se quitaron porque están vendidos o reservados`, 'advertencia');
}

async function guardarCategoria() {
  if (!modal.nombre.trim()) { toast('La categoría necesita un nombre', 'error'); return; }
  const cantidad = Number(modal.cantidad);
  if (!Number.isInteger(cantidad) || cantidad < 0) { toast('Cantidad inválida', 'error'); return; }
  guardando.value = true;
  try {
    const cuerpo = {
      nombre: modal.nombre.trim(),
      color: modal.color,
      forma: modal.forma,
      tamanoMapa: aFraccion(modal.tamanoMilesimas),
      mapaProporcion: modal.proporcion,
    };
    const tamano = modal.tamano.trim();
    if (!modal.editando) cuerpo.tamano = tamano || '3x3';
    else if (tamano && tamano !== modal.tamanoOriginal) cuerpo.tamano = tamano;
    const r = modal.editando
      ? await apiFetch(`/api/app/categorias/${modal.editando.id}`, { method: 'PATCH', body: JSON.stringify(cuerpo) })
      : await apiFetch('/api/app/categorias', {
        method: 'POST',
        body: JSON.stringify({ ...cuerpo, cantidad, precioBase: 0 }),
      });
    const d = await r.json();
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    if (modal.editando && cantidad !== modal.cantidadOriginal) await ajustarCantidad(modal.editando.id);
    modal.abierto = false;
    toast(modal.editando ? 'Categoría actualizada' : 'Categoría creada', 'ok');
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

async function eliminarCategoria(c) {
  const si = await alertaConAccion(
    `¿Eliminar "${c.nombre}"? Se dan de baja todas sus casetas. `
    + 'Solo se puede si ninguna está vendida ni reservada.', 'advertencia', () => {});
  if (!si || ocupado.value) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/categorias/${c.id}`, { method: 'DELETE' });
    const d = await r.json();
    if (!r.ok || d.ok === false) { await aviso(d.mensaje || 'No se pudo eliminar', 'error', 0); return; }
    toast(`${c.nombre} eliminada`, 'ok');
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

// ---------------------------------------------------------------- opciones de precio

function abrirOpcion(c, o = null) {
  Object.assign(modalOpcion, {
    abierto: true, categoria: c, editando: o,
    nombre: o?.nombre ?? c.nombre,
    precio: o?.precio ?? 0,
    predeterminada: o?.predeterminada ?? false,
  });
}

async function guardarOpcion() {
  const c = modalOpcion.categoria;
  if (!modalOpcion.nombre.trim()) { toast('La opción necesita un nombre', 'error'); return; }
  guardando.value = true;
  try {
    const cuerpo = {
      nombre: modalOpcion.nombre.trim(),
      precio: Number(modalOpcion.precio) || 0,
      predeterminada: modalOpcion.predeterminada,
    };
    const r = modalOpcion.editando
      ? await apiFetch(`/api/app/categorias/${c.id}/opciones/${modalOpcion.editando.id}`,
        { method: 'PATCH', body: JSON.stringify(cuerpo) })
      : await apiFetch(`/api/app/categorias/${c.id}/opciones`,
        { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json();
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    modalOpcion.abierto = false;
    toast('Opción guardada', 'ok');
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

async function hacerPredeterminada(c, o) {
  if (ocupado.value) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/categorias/${c.id}/opciones/${o.id}`,
      { method: 'PATCH', body: JSON.stringify({ predeterminada: true }) });
    const d = await r.json();
    if (!r.ok || d.ok === false) { toast(d.mensaje || 'No se pudo cambiar', 'error'); return; }
    toast(`"${o.nombre}" es ahora la opción por defecto`, 'ok');
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}

async function eliminarOpcion(c, o) {
  const si = await alertaConAccion(
    `¿Quitar la opción "${o.nombre}"? Las ventas ya registradas con ella no cambian: `
    + 'conservan su precio y su nombre.', 'advertencia', () => {});
  if (!si || ocupado.value) return;
  ocupado.value = true;
  try {
    const r = await apiFetch(`/api/app/categorias/${c.id}/opciones/${o.id}`, { method: 'DELETE' });
    const d = await r.json();
    if (!r.ok || d.ok === false) { await aviso(d.mensaje || 'No se pudo quitar', 'error', 0); return; }
    toast('Opción quitada', 'ok');
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = false;
  }
}
</script>

<template>
  <div class="fila entre encabezado">
    <input v-model="filtro" class="control busca" placeholder="Buscar categoría…" />
    <button class="btn btn-primario" @click="abrirCrear">＋ Nueva categoría</button>
  </div>

  <p class="muted nota">
    Lo que cambies aquí —nombre, color, forma, tamaño o precio— se ve <strong>al instante</strong>
    en el mapa de todos los vendedores, sin que nadie recargue nada.
    Cada categoría tiene una o varias <strong>opciones de precio</strong>; al registrar una venta
    se elige una y de ahí sale el total.
  </p>

  <div v-if="cargando" class="vacio">Cargando…</div>
  <div v-else-if="!filtradas.length" class="vacio">No hay categorías que mostrar.</div>

  <div v-else class="rejilla">
    <article v-for="c in filtradas" :key="c.id" class="card categoria">
      <header class="cab">
        <span class="muestra" :style="{ background: c.color || 'transparent' }"
              :class="`forma-${c.forma || 'cuadrado'}`"></span>
        <div class="titulo">
          <strong>{{ c.nombre }}</strong>
          <span class="muted chico">{{ medidaLegible(c.tamano) }} · {{ c.cantidad ?? 0 }} puestos · {{ etiquetaForma(c.forma) }}</span>
        </div>
        <span class="crecer"></span>
        <button class="btn btn-sm btn-fantasma" @click="abrirEditar(c)">Editar</button>
        <button class="btn btn-sm btn-peligro" :disabled="ocupado" @click="eliminarCategoria(c)">Eliminar</button>
      </header>

      <div class="opciones">
        <div class="cab-op">
          <span class="muted chico">Opciones de precio</span>
          <span class="crecer"></span>
          <button class="btn btn-sm btn-fantasma" @click="abrirOpcion(c)">＋ Agregar opción</button>
        </div>

        <p v-if="!c.opciones?.length" class="muted chico sin-op">
          Sin opciones. Agrega al menos una para poder venderla.
        </p>

        <ul v-else class="lista-op">
          <li v-for="o in c.opciones" :key="o.id" :class="{ predet: o.predeterminada }">
            <span class="nombre-op">{{ o.nombre }}</span>
            <!-- La marca no es decorativa: es el precio que el mapa canta en la ficha de la
                 caseta y el que se cobra si el vendedor no elige nada. -->
            <span v-if="o.predeterminada" class="badge badge-ok">por defecto</span>
            <button v-else class="btn btn-sm btn-fantasma marcar" :disabled="ocupado"
                    @click="hacerPredeterminada(c, o)">Usar por defecto</button>
            <span class="crecer"></span>
            <strong class="precio-op">{{ bs(o.precio) }} Bs</strong>
            <button class="btn btn-sm btn-fantasma" @click="abrirOpcion(c, o)">Editar</button>
            <button class="btn btn-sm btn-fantasma" :disabled="ocupado" @click="eliminarOpcion(c, o)">✕</button>
          </li>
        </ul>
      </div>
    </article>
  </div>

  <!-- ---------------------------------------------------------------- categoria -->
  <UiModal v-if="modal.abierto"
           :titulo="modal.editando ? `Editar ${modal.editando.nombre}` : 'Nueva categoría'"
           @cerrar="modal.abierto = false">
    <div class="formulario">
      <label>Nombre
        <input v-model="modal.nombre" class="control" placeholder="PYMES" />
      </label>

      <div class="dos">
        <label>Color en el mapa
          <input v-model="modal.color" type="color" class="control control-color" />
        </label>
        <label>Medida del puesto
          <input v-model="modal.tamano" class="control" maxlength="40" placeholder="3x3" />
          <span class="muted chico">{{ medidaLegible(modal.tamano) }}</span>
        </label>
      </div>

      <div class="campo-formas">
        <span>Forma</span>
        <div class="formas">
          <button v-for="f in FORMAS" :key="f.valor" type="button" class="forma-opcion"
                  :class="{ on: modal.forma === f.valor }" @click="modal.forma = f.valor">
            <span class="muestra" :class="`forma-${f.valor}`" :style="{ background: modal.color }"></span>
            {{ f.etiqueta }}
          </button>
        </div>
      </div>

      <div class="vista-previa" :style="{ '--zoom': zoomVista }">
        <div class="lienzo-previa" :style="{ aspectRatio: 1 / (planoTienda.aspecto || 1) }">
          <img v-if="planoTienda.src" :src="planoTienda.src" alt="" draggable="false" />
          <span v-for="(b, i) in vistaPrevia" :key="i" class="puesto-previa" :class="`forma-${modal.forma}`"
                :style="{ ...b, background: modal.color }"></span>
        </div>
      </div>

      <label>Tamaño en el plano
        <input v-model.number="modal.tamanoMilesimas" type="range" min="4" max="60" class="control-rango-ancho" />
        <span class="muted chico">Ancho de cada puesto: {{ (modal.tamanoMilesimas / 10).toFixed(1) }}% del ancho del plano. Cada puesto puede ajustarse después en el Editor.</span>
      </label>

      <label v-if="modal.forma === 'rectangulo'">Proporción alto / ancho
        <input v-model.number="modal.proporcion" type="range" min="0.2" max="3" step="0.05" class="control-rango-ancho" />
        <span class="muted chico">{{ modal.proporcion < 1 ? 'Más ancho que alto' : modal.proporcion > 1 ? 'Más alto que ancho' : 'Cuadrado' }} ({{ Number(modal.proporcion).toFixed(2) }})</span>
      </label>

      <label>{{ modal.editando ? 'Cantidad de puestos' : 'Cuántos puestos crear' }}
        <input v-model.number="modal.cantidad" type="number" min="0" class="control" />
        <span v-if="modal.editando" class="muted chico">Subir crea puestos nuevos; bajar quita los libres de número más alto.</span>
      </label>

      <p v-if="!modal.editando" class="muted chico">
        La categoría nace con una opción de precio en 0 Bs, con su mismo nombre.
        Ponle precio en cuanto la crees o se venderá en cero.
      </p>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modal.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardarCategoria">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>

  <!-- ---------------------------------------------------------------- opcion -->
  <UiModal v-if="modalOpcion.abierto"
           :titulo="modalOpcion.editando ? 'Editar opción' : `Nueva opción de ${modalOpcion.categoria?.nombre}`"
           @cerrar="modalOpcion.abierto = false">
    <div class="formulario">
      <label>Nombre de la opción
        <input v-model="modalOpcion.nombre" class="control" placeholder="PYMES con tarima" />
        <span class="muted chico">Es lo que el vendedor elige en el formulario de venta.</span>
      </label>
      <label>Precio
        <input v-model.number="modalOpcion.precio" type="number" min="0" step="1" class="control" />
        <span class="muted chico">En bolivianos, por caseta.</span>
      </label>
      <label class="check">
        <input v-model="modalOpcion.predeterminada" type="checkbox" />
        Usar esta opción por defecto
      </label>
      <p class="muted chico">
        La opción por defecto es la que se cobra si el vendedor no elige nada, y su precio es el
        que el mapa muestra en la ficha de cada caseta.
      </p>
    </div>
    <template #pie>
      <button class="btn btn-fantasma" @click="modalOpcion.abierto = false">Cancelar</button>
      <button class="btn btn-primario" :disabled="guardando" @click="guardarOpcion">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.encabezado { gap: 0.75rem; margin-bottom: 0.5rem; flex-wrap: wrap; }
.busca { flex: 1; min-width: 220px; }
.nota { margin: 0 0 1rem; line-height: 1.5; }
.crecer { flex: 1; }
.chico { font-size: 0.85rem; }

.rejilla { display: grid; gap: 1rem; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); }
.categoria { padding: 0.9rem; }

.cab { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.titulo { display: flex; flex-direction: column; }
.muestra { width: 22px; height: 22px; flex: none; border: 1px solid rgba(0,0,0,0.15); }
.muestra.forma-circulo { border-radius: 50%; }
.muestra.forma-rectangulo { height: 14px; width: 24px; border-radius: 3px; }
.muestra.forma-triangulo { clip-path: polygon(50% 0, 100% 100%, 0 100%); border: none; }
.muestra.forma-hexagono { clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%); border: none; }

.campo-formas { display: flex; flex-direction: column; gap: 0.35rem; }
.formas { display: grid; grid-template-columns: repeat(auto-fill, minmax(122px, 1fr)); gap: 0.4rem; }
.forma-opcion {
  display: flex; align-items: center; gap: 0.45rem; padding: 0.45rem 0.55rem;
  border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel);
  color: var(--text); font: inherit; font-size: 0.85rem; cursor: pointer;
}
.forma-opcion .muestra { width: 16px; height: 16px; border: none; }
.forma-opcion .muestra.forma-rectangulo { width: 20px; height: 12px; }
.forma-opcion.on { border-color: var(--acento); background: var(--acento-suave); font-weight: 600; }

.vista-previa {
  position: relative; height: 170px; overflow: hidden; border-radius: var(--radio-sm);
  border: 1px solid var(--border); background: var(--bg);
}
.lienzo-previa {
  position: absolute; left: 0; top: 50%; width: 100%;
  transform: translateY(-50%) scale(var(--zoom, 2)); transform-origin: 50% 50%;
}
.lienzo-previa img { width: 100%; height: 100%; display: block; opacity: 0.55; }
.puesto-previa { position: absolute; transform: translate(-50%, -50%); box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.9); }
.puesto-previa.forma-cuadrado { border-radius: 10%; }
.puesto-previa.forma-rectangulo { border-radius: 6%; }
.puesto-previa.forma-circulo { border-radius: 50%; }
.puesto-previa.forma-triangulo { clip-path: polygon(50% 0, 100% 100%, 0 100%); box-shadow: none; }
.puesto-previa.forma-hexagono { clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%); box-shadow: none; }

.opciones { margin-top: 0.85rem; border-top: 1px solid var(--borde, rgba(128,128,128,0.25)); padding-top: 0.6rem; }
.cab-op { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.4rem; }
.sin-op { margin: 0.3rem 0; }

.lista-op { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.35rem; }
.lista-op li { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;
  padding: 0.35rem 0.5rem; border-radius: 8px; background: var(--panel-2, rgba(128,128,128,0.08)); }
.lista-op li.predet { outline: 1px solid var(--ok, #16a34a); }
.nombre-op { font-weight: 600; }
.precio-op { white-space: nowrap; }
/* En el teléfono el botón de "usar por defecto" no puede empujar el precio fuera de la fila. */
.marcar { font-size: 0.78rem; }

.formulario { display: flex; flex-direction: column; gap: 0.9rem; }
.formulario label { display: flex; flex-direction: column; gap: 0.3rem; }
.formulario label.check { flex-direction: row; align-items: center; gap: 0.5rem; }
.dos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.9rem; }
.control-color { height: 42px; padding: 2px; }
.control-rango-ancho { width: 100%; }

@media (max-width: 480px) {
  .rejilla { grid-template-columns: 1fr; }
  .dos { grid-template-columns: 1fr; }
}
</style>
