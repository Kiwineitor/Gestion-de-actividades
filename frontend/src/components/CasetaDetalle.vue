<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue';
import { ETIQUETA_ESTADO, CLASE_ESTADO } from '../mapa';
import { categorias, asegurarCategorias } from '../ui/catalogoCategorias';

const props = defineProps({
  puesto: { type: Object, default: null },
  esMia: { type: Boolean, default: false },
  ocupado: { type: Boolean, default: false },
  vendible: { type: Boolean, default: true },
  asignaciones: { type: Array, default: () => [] },
  ocupante: { type: Object, default: null },
});
const emit = defineEmits(['cerrar', 'agregar', 'quitar']);

const accion = computed(() => {
  if (!props.puesto) return null;
  if (!props.vendible) return null;
  if (props.puesto.estado === 'L') return 'agregar';
  if (props.esMia) return 'quitar';
  return null;
});

const motivoSinAccion = computed(() => {
  if (!props.puesto || accion.value) return '';
  if (!props.vendible) return '';
  if (quienLaTiene.value) return '';
  if (props.puesto.estado === 'T') return 'La tiene reservada otro vendedor.';
  if (props.puesto.estado === 'O') return 'Ya está vendida.';
  if (props.puesto.estado === 'X') return 'Está bloqueada por reparación.';
  return '';
});

const quienLaTiene = computed(() => {
  const o = props.ocupante;
  if (!o || !o.vendedor) return null;
  if (props.puesto?.estado !== 'T' && props.puesto?.estado !== 'O') return null;
  if (props.esMia) return null;
  return o;
});

const vendida = computed(() => props.puesto?.estado === 'O');

const venceEn = computed(() => {
  if (props.puesto?.estado !== 'T' || !props.puesto?.reservaExpira) return '';
  const ms = new Date(props.puesto.reservaExpira).getTime() - ahora.value;
  if (!Number.isFinite(ms) || ms <= 0) return '';
  const min = Math.round(ms / 60000);
  if (min < 1) return 'vence en menos de un minuto';
  if (min < 60) return `vence en ${min} min`;
  return `vence en ${Math.round(min / 60)} h`;
});

const ahora = ref(Date.now());
let reloj = null;
watch(() => props.puesto?.id, (id) => {
  clearInterval(reloj);
  reloj = null;
  if (!id) return;
  ahora.value = Date.now();
  reloj = setInterval(() => { ahora.value = Date.now(); }, 30000);
}, { immediate: true });
onBeforeUnmount(() => clearInterval(reloj));

watch(() => props.puesto?.id, (id) => { if (id) asegurarCategorias(); }, { immediate: true });

const opciones = computed(() => {
  const c = (categorias.value || []).find((x) => x.id === props.puesto?.categoriaId);
  return (c?.opciones || []).slice().sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0));
});

const hayVariosPrecios = computed(() => !props.puesto?.precioPropio && opciones.value.length > 1);

const precio = computed(() => Number(props.puesto?.precio || 0));
const bs = (n) => Number(n || 0).toLocaleString('es-BO');

const telefono = (celular) => (celular || '').replace(/[^\d+]/g, '');

const copiado = ref(null);
async function copiarContacto(a) {
  if (!a) return;
  try {
    await navigator.clipboard.writeText(`${a.vendedor}${a.celular ? ' — ' + a.celular : ''}`);
    copiado.value = a.vendedorId;
    setTimeout(() => { if (copiado.value === a.vendedorId) copiado.value = null; }, 2000);
  } catch {
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="hoja">
      <div v-if="puesto" class="velo" @click.self="emit('cerrar')">
        <aside class="ficha" role="dialog" aria-label="Detalle de la caseta">
          <header>
            <div>
              <h2>{{ puesto.categoria }} {{ puesto.codigo }}</h2>
              <span class="chip" :class="[CLASE_ESTADO[puesto.estado], { mia: esMia }]">
                {{ esMia ? 'En mi venta' : ETIQUETA_ESTADO[puesto.estado] }}
              </span>
            </div>
            <button class="btn btn-fantasma btn-icono" aria-label="Cerrar" @click="emit('cerrar')">✕</button>
          </header>

          <div v-if="hayVariosPrecios" class="precios">
            <p class="titulo-precios">
              Esta categoría se vende de {{ opciones.length }} formas
              <span class="muted">· el precio depende de cuál se elija al registrar</span>
            </p>
            <ul>
              <li v-for="o in opciones" :key="o.id">
                <span class="nom">
                  {{ o.nombre }}
                  <span v-if="o.predeterminada" class="marca">por defecto</span>
                </span>
                <strong>{{ bs(o.precio) }} Bs</strong>
              </li>
            </ul>
          </div>

          <dl class="datos">
            <div v-if="!hayVariosPrecios">
              <dt>Precio</dt>
              <dd class="precio">
                {{ precio > 0 ? bs(precio) + ' Bs' : 'sin precio' }}
                <span v-if="puesto.precioPropio" class="marca">precio propio</span>
              </dd>
            </div>
            <div><dt>Medida</dt><dd>{{ puesto.tamano || '—' }}</dd></div>
            <div v-if="puesto.referencia" class="ancho"><dt>Ubicación</dt><dd>{{ puesto.referencia }}</dd></div>
          </dl>

          <div v-if="quienLaTiene" class="ocupada" :class="{ vendida }">
            <p class="quien">
              <span class="etiqueta">{{ vendida ? 'La vendió' : 'La está registrando' }}</span>
              <strong>{{ quienLaTiene.vendedor }}</strong>
            </p>
            <p v-if="venceEn" class="cuando">La reserva {{ venceEn }}.</p>
            <div class="contacto">
              <a v-if="quienLaTiene.celular" class="btn btn-primario grande"
                 :href="`tel:${telefono(quienLaTiene.celular)}`">
                📞 {{ quienLaTiene.celular }}
              </a>
              <button v-if="quienLaTiene.celular" class="btn" @click="copiarContacto(quienLaTiene)">
                {{ copiado === quienLaTiene.vendedorId ? '✓ Copiado' : 'Copiar contacto' }}
              </button>
              <p v-else class="motivo">No tiene celular registrado.</p>
            </div>
          </div>

          <div v-if="!vendible && !quienLaTiene" class="ajena">
            <template v-if="asignaciones.length">
              <p class="quien">
                {{ asignaciones.length > 1 ? 'La venden' : 'La vende' }}
                <strong>{{ asignaciones.map((a) => a.vendedor).join(', ') }}</strong>
              </p>
              <div v-for="a in asignaciones" :key="a.vendedorId" class="contacto">
                <a v-if="a.celular" class="btn btn-primario grande" :href="`tel:${telefono(a.celular)}`">
                  📞 {{ a.celular }}<template v-if="asignaciones.length > 1"> · {{ a.vendedor }}</template>
                </a>
                <p v-else class="motivo">{{ a.vendedor }} no tiene celular registrado.</p>
                <button v-if="a.celular" class="btn" @click="copiarContacto(a)">
                  {{ copiado === a.vendedorId ? '✓ Copiado' : 'Copiar contacto' }}
                </button>
              </div>
            </template>
            <p v-else class="motivo">Esta caseta todavía no tiene vendedor asignado.</p>
          </div>

          <footer>
            <p v-if="motivoSinAccion" class="motivo">{{ motivoSinAccion }}</p>
            <button v-if="accion === 'agregar'" class="btn btn-primario grande"
                    :disabled="ocupado" @click="emit('agregar', puesto)">
              Agregar a la venta
            </button>
            <button v-else-if="accion === 'quitar'" class="btn btn-peligro grande"
                    :disabled="ocupado" @click="emit('quitar', puesto)">
              Quitar de la venta
            </button>
          </footer>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.velo {
  position: fixed; inset: 0; z-index: 80; background: rgba(2, 6, 23, 0.45);
  display: flex; align-items: flex-end; justify-content: center;
}
.ficha {
  background: var(--panel); width: 100%; max-width: 560px;
  border-radius: var(--radio) var(--radio) 0 0;
  box-shadow: var(--sombra-md); max-height: 92vh; overflow-y: auto;
  display: flex; flex-direction: column; gap: 1rem;
  padding: 1.1rem 1.2rem calc(1.3rem + var(--safe-bottom));
}
header { display: flex; align-items: flex-start; justify-content: space-between; gap: 0.6rem; }
header h2 { margin: 0 0 0.35rem; font-size: 1.35rem; }
.chip { padding: 0.15rem 0.55rem; border-radius: 999px; font-size: 0.75rem; font-weight: 700; }

.datos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; margin: 0; }
.datos .ancho { grid-column: 1 / -1; }
.datos dt { font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.datos dd { margin: 0.15rem 0 0; font-size: 1.1rem; }
.datos .precio { font-weight: 800; font-size: 1.45rem; font-variant-numeric: tabular-nums; }

.precios {
  display: flex; flex-direction: column; gap: 0.5rem;
  padding: 0.9rem 1rem; border-radius: var(--radio-sm);
  background: var(--panel-2); border: 1px solid var(--border);
}
.titulo-precios { margin: 0; font-size: 0.95rem; line-height: 1.4; }
.precios ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.45rem; }
.precios li {
  display: flex; justify-content: space-between; align-items: baseline; gap: 0.8rem;
  padding-top: 0.45rem; border-top: 1px solid var(--border);
}
.precios li:first-child { border-top: none; padding-top: 0; }
.precios .nom { font-size: 1.05rem; }
.precios strong { font-size: 1.25rem; font-variant-numeric: tabular-nums; white-space: nowrap; }
.marca {
  margin-left: 0.4rem; padding: 0.05rem 0.45rem; border-radius: 999px;
  font-size: 0.7rem; font-weight: 700; vertical-align: middle;
  color: var(--acento); border: 1px solid color-mix(in srgb, var(--acento) 45%, transparent);
}
.muted { color: var(--muted); }

.ajena {
  display: flex; flex-direction: column; gap: 0.6rem;
  padding: 0.9rem 1rem; border-radius: var(--radio-sm);
  background: var(--panel-2); border: 1px solid var(--border);
}
.ajena .quien { margin: 0; font-size: 1.05rem; }
.ajena .contacto { display: flex; flex-direction: column; gap: 0.5rem; }
.ajena .contacto .btn { min-height: 52px; font-size: 1.05rem; }

.ocupada {
  display: flex; flex-direction: column; gap: 0.6rem;
  padding: 0.9rem 1rem; border-radius: var(--radio-sm);
  border: 1px solid color-mix(in srgb, var(--tramite) 45%, transparent);
  background: color-mix(in srgb, var(--tramite) 12%, var(--panel));
}
.ocupada.vendida {
  border-color: color-mix(in srgb, var(--ocupado) 45%, transparent);
  background: color-mix(in srgb, var(--ocupado) 12%, var(--panel));
}
.ocupada .quien { margin: 0; font-size: 1.05rem; display: flex; flex-wrap: wrap; gap: 0.35rem; }
.ocupada .etiqueta { color: var(--muted); }
.ocupada .cuando { margin: 0; font-size: 0.9rem; color: var(--muted); }
.ocupada .contacto { display: flex; flex-direction: column; gap: 0.5rem; }
.ocupada .contacto .btn { min-height: 52px; font-size: 1.05rem; }

footer { display: flex; flex-direction: column; gap: 0.5rem; }
.motivo { margin: 0; font-size: 0.92rem; color: var(--muted); text-align: center; }
.grande { width: 100%; min-height: 56px; padding: 0.9rem; font-size: 1.1rem; font-weight: 700; }

@media (min-width: 720px) {
  .velo { align-items: center; }
  .ficha { border-radius: var(--radio); max-height: 88vh; }
}

.hoja-enter-active, .hoja-leave-active { transition: opacity 0.18s ease; }
.hoja-enter-active .ficha, .hoja-leave-active .ficha { transition: transform 0.18s ease; }
.hoja-enter-from, .hoja-leave-to { opacity: 0; }
.hoja-enter-from .ficha, .hoja-leave-to .ficha { transform: translateY(1.5rem); }
@media (prefers-reduced-motion: reduce) {
  .hoja-enter-active, .hoja-leave-active,
  .hoja-enter-active .ficha, .hoja-leave-active .ficha { transition: none; }
}
</style>
