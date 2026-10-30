<script setup>
import { usePermisosStore } from '../stores/permisos';
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import PanZoom from '../components/PanZoom.vue';
import CasetaDetalle from '../components/CasetaDetalle.vue';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { usePuestosStore } from '../stores/puestos';
import { usePlanoStore } from '../stores/plano';
import { toast } from '../ui/toast';
import { alerta } from '../ui/alerta';
import { iniciarMedicion, marcarPintado, marcarRed, purgarMediciones } from '../ui/medir';
import { CLASE_ESTADO, ETIQUETA_ESTADO, LEYENDA, anchoParaLeer, anchoParaTocar, estiloPin, numerosVisibles } from '../mapa';
import { asegurarCategorias, opcionesDe, tieneVariosPrecios } from '../ui/catalogoCategorias';
import RotuloPlano from '../components/RotuloPlano.vue';
import { usePlanoTextosStore } from '../stores/planoTextos.js';
import { aModulo } from '../modulos';

const tienda = usePuestosStore();
const planoTienda = usePlanoStore();
const textosTienda = usePlanoTextosStore();
const enPeticion = ref(new Set());
const seleccionada = ref(null);
let temporizadorMedicion = null;
const auth = useAuthStore();
const permisosPlano = usePermisosStore();
const puedeDisenar = computed(() => auth.puedeEditarPlano || permisosPlano.puedeVer('editor'));
const router = useRouter();

const ZONA_RESPALDO = { x0: 0.08, y0: 0.17, x1: 0.87, y1: 0.86 };
const ZONA_COMPLETA = { x0: 0, y0: 0, x1: 1, y1: 1 };
const zonaEncuadre = computed(() =>
  (planoTienda.plano.propio ? ZONA_COMPLETA : ZONA_RESPALDO));

const esMovil = window.innerWidth <= 820;
const focoInicial = { x: 0.44, y: 0.34, scale: 2.4 };

const ubicados = computed(() => tienda.ubicadas);

const avisoSync = computed(() => {
  if (tienda.desdeCache) return 'Copia guardada, aún sin confirmar';
  if (!tienda.enVivo) return 'Sin tiempo real: el mapa puede estar desfasado';
  return null;
});

const topeZoom = computed(() => anchoParaTocar(ubicados.value));
const umbralNumeros = computed(() => anchoParaLeer(ubicados.value));
const sinUbicar = computed(() => tienda.puestos.length - tienda.ubicadas.length);

const carrito = computed(() => tienda.carritoDe(auth.id));
const totalCarrito = computed(() =>
  carrito.value.reduce((s, p) => s + Number(p.precio || 0), 0));
const sinPrecio = computed(() => carrito.value.some((p) => !(Number(p.precio) > 0)));
const vaciando = ref(false);

function sesionCaducada() {
  auth.logout();
  router.push('/admin');
}

const asignacionDe = (p) => tienda.asignaciones.get(p.id) || [];

const ocupanteDe = (p) => tienda.ocupacion.get(p.id) || null;

const puedoVender = (p) => {
  if (!auth.esVendedor) return true;
  return asignacionDe(p).some((a) => a.vendedorId === auth.id);
};

const sinHabilitadas = computed(() =>
  auth.esVendedor
  && !tienda.cargando
  && tienda.puestos.length > 0
  && ![...tienda.asignaciones.values()]
        .some((lista) => lista.some((a) => a.vendedorId === auth.id)));

const esMia = (p) => p.estado === 'T' && p.reservadoPor != null && auth.id != null
  && Number(p.reservadoPor) === Number(auth.id);

function rotulo(p) {
  const o = ocupanteDe(p);
  if (o?.vendedor && !esMia(p)) {
    const verbo = p.estado === 'O' ? 'la vendio' : 'la esta registrando';
    return `${p.categoria} ${p.codigo} · ${verbo} ${o.vendedor}`;
  }
  if (!puedoVender(p)) {
    const quienes = asignacionDe(p).map((a) => a.vendedor).filter(Boolean);
    return `${p.categoria} ${p.codigo} · ${quienes.length
      ? `la vende${quienes.length > 1 ? 'n' : ''} ${quienes.join(', ')}`
      : 'sin vendedor asignado'}`;
  }
  const duenio = esMia(p) ? ' (tuya)' : p.estado === 'T' ? ' (de otro vendedor)' : '';
  return `${p.categoria} ${p.codigo} · ${p.tamano || ''} · ${ETIQUETA_ESTADO[p.estado]}${duenio}${precioRotulo(p)}`;
}

function precioRotulo(p) {
  const ops = tieneVariosPrecios(p) ? opcionesDe(p.categoriaId) : [];
  if (ops.length > 1) {
    const desde = Math.min(...ops.map((o) => Number(o.precio) || 0));
    return ` · desde ${desde} Bs (${ops.length} precios)`;
  }
  return p.precio > 0 ? ` · ${p.precio} Bs` : ' · sin precio';
}

function abrirFicha(p) {
  seleccionada.value = p;
}

const casetaEnFicha = computed(() => {
  if (!seleccionada.value) return null;
  return tienda.puestos.find((p) => p.id === seleccionada.value.id) || seleccionada.value;
});

async function click(p, evento) {
  if (!puedoVender(p)) return;

  if (enPeticion.value.has(p.id)) return;

  if (p.estado === 'T' && !esMia(p)) {
    toast(`${p.categoria} ${p.codigo}: la tiene reservada otro vendedor`, 'info');
    return;
  }
  const acc = p.estado === 'L' ? 'agregar' : esMia(p) ? 'quitar' : null;
  if (!acc) return;

  const estadoPrevio = p.estado;
  const reservadoPrevio = p.reservadoPor ?? null;
  iniciarMedicion(p.id, acc, evento);
  tienda.aplicar(acc === 'agregar'
    ? { ...p, estado: 'T', reservadoPor: auth.id }
    : { ...p, estado: 'L', reservadoPor: null });
  enPeticion.value = new Set(enPeticion.value).add(p.id);

  nextTick(() => requestAnimationFrame(() => requestAnimationFrame(() => marcarPintado(p.id))));

  try {
    const r = await apiFetch('/api/app/puestos/carrito', {
      method: acc === 'agregar' ? 'POST' : 'DELETE',
      body: JSON.stringify({ ids: [p.id] }),
    });
    marcarRed(p.id);
    const d = await r.json().catch(() => ({}));
    const logrado = r.ok && (d.logradas ?? []).includes(p.id);
    if (logrado && seleccionada.value?.id === p.id) seleccionada.value = null;
    if (!logrado) {
      tienda.aplicar({ ...p, estado: estadoPrevio, reservadoPor: reservadoPrevio });
      toast(`${p.categoria} ${p.codigo}: ${d.mensaje ?? 'No disponible'}`, 'error');
    }
  } catch (e) {
    tienda.aplicar({ ...p, estado: estadoPrevio, reservadoPor: reservadoPrevio });
    toast(e.message, 'error');
  } finally {
    const s = new Set(enPeticion.value);
    s.delete(p.id);
    enPeticion.value = s;
  }
}

async function vaciarCarrito() {
  const ids = carrito.value.map((p) => p.id);
  if (!ids.length || vaciando.value) return;
  vaciando.value = true;
  try {
    const r = await apiFetch('/api/app/puestos/carrito', {
      method: 'DELETE', body: JSON.stringify({ ids }),
    });
    const d = await r.json().catch(() => ({}));
    for (const id of d.logradas ?? []) {
      const p = tienda.puestos.find((x) => x.id === id);
      if (p) tienda.aplicar({ ...p, estado: 'L', reservadoPor: null });
    }
    toast(`${(d.logradas ?? []).length} caseta(s) liberadas`, 'ok');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    vaciando.value = false;
  }
}

let dejarDeOirAsignaciones = null;

onMounted(() => {
  dejarDeOirAsignaciones = tienda.registrarNotificaciones((n) => {
    if (n?.tipo === 'ASIGNACION_CAMBIADA' && n.cuerpo) alerta(n.cuerpo, 'info', 8000);
  });
  tienda.asegurar(sesionCaducada);
  asegurarCategorias();
  textosTienda.asegurar();
  planoTienda.asegurar();
  if (import.meta.env.DEV) {
    temporizadorMedicion = setInterval(purgarMediciones, 3000);
  }
});
onUnmounted(() => {
  clearInterval(temporizadorMedicion);
  if (dejarDeOirAsignaciones) dejarDeOirAsignaciones();
});
</script>

<template>
  <div class="barra">
      <div class="legend">
        <span v-for="l in LEYENDA" :key="l.clase" class="chip" :class="l.clase">{{ l.txt }}</span>
        <span v-if="auth.esVendedor" class="chip chip-ajena">De otro vendedor</span>
      </div>
      <button v-if="avisoSync" class="aviso-sync" @click="tienda.reintentar()"
              :title="avisoSync + '. Tocá para actualizar ahora.'">
        ⚠ {{ avisoSync }} · Actualizar
      </button>

      <button class="btn btn-fantasma btn-sm numeros" :class="{ on: numerosVisibles }"
              :aria-pressed="numerosVisibles" @click="numerosVisibles = !numerosVisibles"
              title="Muestra el número de cada caseta sobre el plano (hay que acercarse para leerlo)">
        🔢 Nº
      </button>
      <button v-if="puedeDisenar" class="btn btn-fantasma btn-sm"
              @click="planoTienda.invalidarCache(); planoTienda.asegurar()"
              title="Forzar recarga del plano desde el servidor">
        🔄 Plano
      </button>
      <div v-if="puedeDisenar" class="info">
        {{ ubicados.length }} ubicadas
        <span v-if="sinUbicar > 0" class="muted">· {{ sinUbicar }} sin ubicar (Editor)</span>
      </div>
    </div>
    <Transition name="subir">
      <div v-if="carrito.length" class="carrito" role="status">
        <div class="resumen">
          <strong>{{ carrito.length }}</strong> caseta{{ carrito.length === 1 ? '' : 's' }}
          <span class="sep">·</span>
          <strong class="total">{{ totalCarrito.toLocaleString('es-BO') }} Bs</strong>
          <span v-if="sinPrecio" class="alerta" title="Hay casetas cuya categoría no tiene precio">
            ⚠ sin precio
          </span>
        </div>
        <div class="botones">
          <button class="btn btn-fantasma" :disabled="vaciando" @click="vaciarCarrito">
            {{ vaciando ? 'Liberando…' : 'Vaciar' }}
          </button>
          <router-link :to="aModulo('venta')" class="btn btn-primario">
            Registrar venta
          </router-link>
        </div>
      </div>
    </Transition>

    <CasetaDetalle
      :puesto="casetaEnFicha"
      :es-mia="casetaEnFicha ? esMia(casetaEnFicha) : false"
      :vendible="casetaEnFicha ? puedoVender(casetaEnFicha) : true"
      :asignaciones="casetaEnFicha ? asignacionDe(casetaEnFicha) : []"
      :ocupante="casetaEnFicha ? ocupanteDe(casetaEnFicha) : null"
      :ocupado="casetaEnFicha ? enPeticion.has(casetaEnFicha.id) : false"
      @cerrar="seleccionada = null"
      @agregar="(p) => click(p)"
      @quitar="(p) => click(p)"
    />

    <div v-if="tienda.sinAsignaciones" class="sin-asignaciones">
      <span class="icono">🗺️</span>
      <h2>Todavía no hay casetas en el plano</h2>
      <p>
        Administración aún no armó el mapa de la feria.
        En cuanto lo haga, aparecerá aquí solo: no hace falta que cierres la aplicación.
      </p>
    </div>

    <div v-else-if="sinHabilitadas" class="aviso-habilitadas" role="status">
      <strong>Todavía no tienes casetas habilitadas.</strong>
      Administración aún no te asignó ninguna; aparecerán solas en cuanto lo haga.
      Mientras tanto puedes tocar cualquier caseta para ver quién la vende.
    </div>

    <PanZoom v-if="!tienda.sinAsignaciones" :key="planoTienda.src" :focus="focoInicial" :contenido="esMovil ? zonaEncuadre : null"
             :aspect="planoTienda.aspecto" :umbral-detalle="umbralNumeros" :max-ancho="topeZoom" llenar>
      <div class="plano" :class="{ 'con-numeros': numerosVisibles }" :style="{ aspectRatio: planoTienda.aspecto ? 1 / planoTienda.aspecto : 'auto' }">
        <img
          :src="planoTienda.src"
          :key="planoTienda.src"
          alt="Plano de la feria"
          :width="planoTienda.plano.ancho"
          :height="planoTienda.plano.alto"
          decoding="async"
          draggable="false"
        />
        <button
          v-for="p in ubicados"
          :key="p.id"
          v-memo="[p.estado, esMia(p), p.mapaX, p.mapaY, p.mapaEscala, p.mapaRotacion, p.tamanoMapa, p.color, p.forma, p.codigo, puedoVender(p), ocupanteDe(p)?.vendedor]"
          class="pin"
          :class="[CLASE_ESTADO[p.estado], `forma-${p.forma || 'cuadrado'}`,
                   { mia: esMia(p), ajena: !puedoVender(p) }]"
          :style="{ ...estiloPin(p), '--categoria': p.color || 'transparent' }"
          :title="rotulo(p)"
          @pointerdown.stop
          @click="abrirFicha(p)"
        ><span class="num-caseta">{{ p.codigo }}</span></button>

        <RotuloPlano v-for="t in textosTienda.textos" :key="t.id" :texto="t" />
      </div>
    </PanZoom>
</template>

<style scoped>

.sin-asignaciones {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 0.6rem; padding: 2.5rem 1.5rem; text-align: center; color: var(--muted);
}
.sin-asignaciones .icono { font-size: 2.6rem; }
.sin-asignaciones h2 { margin: 0; font-size: 1.15rem; color: var(--text); }
.sin-asignaciones p { margin: 0; max-width: 42ch; line-height: 1.5; }

.barra {
  display: flex; justify-content: space-between; align-items: center;
  padding: 0.6rem 1rem; gap: 1rem; flex-wrap: wrap;
}
.legend { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.chip { padding: 0.2rem 0.55rem; border-radius: 999px; font-size: 0.78rem; font-weight: 600; }
.chip-ajena { background: var(--bloqueado); color: #fff; }

.aviso-habilitadas {
  margin: 0 0 0.6rem; padding: 0.7rem 0.9rem; border-radius: var(--radio-sm);
  border: 1px solid color-mix(in srgb, var(--tramite) 45%, transparent);
  background: color-mix(in srgb, var(--tramite) 12%, var(--panel));
  color: var(--text); font-size: 0.9rem; line-height: 1.45;
}
.aviso-habilitadas strong { color: var(--tramite); }
.info { font-size: 0.9rem; }
.numeros.on { border-color: var(--acento); color: var(--acento); font-weight: 700; }

.aviso-sync {
  border: 1px solid color-mix(in srgb, var(--tramite) 45%, transparent);
  background: color-mix(in srgb, var(--tramite) 14%, transparent);
  color: var(--tramite);
  border-radius: 999px; padding: 0.25rem 0.7rem;
  font: inherit; font-size: 0.8rem; font-weight: 700; cursor: pointer;
}
.muted { color: var(--muted); }
.plano { position: relative; width: 100%; }

.plano img {
  width: 100%; height: auto; display: block;
  pointer-events: none;
  user-select: none; -webkit-user-select: none;
  transform: translateZ(0);
}

.pin {
  position: absolute; width: 100px; height: var(--alto, 100px);
  transform-origin: 0 0;
  transform: scale(calc(var(--pin, 20) / 100)) rotate(var(--giro, 0deg)) translate(-50%, -50%);
  border: none;
  padding: 0; cursor: pointer;
  --borde: 6px;
  --aro: 12px;
  box-shadow: 0 0 0 var(--borde) rgba(255, 255, 255, 0.75);
}

.world.detalle .pin {
  box-shadow:
    0 0 0 var(--aro) var(--categoria, transparent),
    0 0 0 calc(var(--aro) + var(--borde)) rgba(255, 255, 255, 0.75);
}
.pin.ajena { background: var(--bloqueado); cursor: pointer; }

.pin:hover { z-index: 5; filter: brightness(1.12); }
.pin.ocupado, .pin.bloqueado { cursor: default; }

.pin.tramite { cursor: default; }
.pin.tramite.mia { cursor: pointer; }

.carrito {
  position: fixed; left: 50%; transform: translateX(-50%); bottom: calc(var(--tabbar-h) + 1rem); z-index: 40;
  display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;
  padding: 0.7rem 1rem; border-radius: var(--radio);
  background: var(--panel); border: 1px solid var(--border); box-shadow: var(--sombra-md);
  max-width: calc(100vw - 2rem);
}
.carrito .resumen { display: flex; align-items: center; gap: 0.4rem; font-size: 0.92rem; }
.carrito .total { font-variant-numeric: tabular-nums; }
.carrito .sep { color: var(--muted); }
.carrito .alerta {
  font-size: 0.75rem; font-weight: 700; color: var(--tramite);
  background: color-mix(in srgb, var(--tramite) 15%, transparent);
  border-radius: 999px; padding: 0.1rem 0.5rem;
}
.carrito .botones { display: flex; gap: 0.5rem; }
.carrito .botones .btn { min-height: 48px; padding-left: 1.1rem; padding-right: 1.1rem; font-size: 1rem; }
@media (max-width: 560px) {
  .carrito { left: 1rem; right: 1rem; transform: none; }
  .carrito .botones { flex: 1; }
  .carrito .botones .btn { flex: 1; }
  .subir-enter-from, .subir-leave-to { transform: translateY(0.5rem); }
}

.subir-enter-active, .subir-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.subir-enter-from, .subir-leave-to { opacity: 0; transform: translateX(-50%) translateY(0.5rem); }
@media (prefers-reduced-motion: reduce) {
  .subir-enter-active, .subir-leave-active { transition: none; }
}

.forma-cuadrado { border-radius: 10%; }
.forma-circulo { border-radius: 50%; }
.forma-triangulo { clip-path: polygon(50% 0%, 100% 100%, 0% 100%); border: none; }
.forma-rectangulo { border-radius: 6%; }
.forma-hexagono { clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%); border: none; }

.num-caseta {
  display: none;
  position: absolute; inset: 0;
  align-items: center; justify-content: center;
  transform: rotate(calc(-1 * var(--giro, 0deg)));
  transform-origin: 50% 50%;
  font-size: 52px; line-height: 1; font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #fff;
  text-shadow: 0 2px 4px rgba(2, 6, 23, 0.92), 0 0 4px rgba(2, 6, 23, 0.85);
  pointer-events: none;
  overflow: hidden;
}
.world.detalle .con-numeros .num-caseta { display: flex; }
.forma-triangulo .num-caseta { align-items: flex-end; padding-bottom: 8px; font-size: 40px; }
</style>
