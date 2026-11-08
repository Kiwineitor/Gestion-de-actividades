<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import NumeroPaso from './NumeroPaso.vue';
import PersonasPorEspacio from './PersonasPorEspacio.vue';
import { apiFetch } from '../api';
import { apiDeFeria } from '../ui/editorFeria';
import { toast } from '../ui/toast';
import { medidaLegible } from '../mapa';

const props = defineProps({
  edicion: { type: Number, default: null },
  categoriaId: { type: Number, default: null },
  uno: { type: String, default: 'puesto' },
  varios: { type: String, default: 'puestos' },
  registraPersonas: { type: Boolean, default: true },
});
const emit = defineEmits(['previa', 'guardada', 'cerrar', 'avanzado']);

const COLORES = ['#8b5cf6', '#3b82f6', '#06b6d4', '#10b981', '#84cc16', '#f59e0b', '#f97316', '#ef4444', '#ec4899', '#64748b'];
const FORMAS = [
  { v: 'cuadrado', t: 'Cuadrado' }, { v: 'rectangulo', t: 'Rectángulo' }, { v: 'circulo', t: 'Círculo' },
  { v: 'triangulo', t: 'Triángulo' }, { v: 'hexagono', t: 'Hexágono' },
];

const cargando = ref(Boolean(props.categoriaId));
const guardando = ref(false);
const original = ref(null);
const f = reactive({
  nombre: '', precio: 0, descripcion: '', cantidad: 10, tamano: '', color: '#8b5cf6', forma: 'cuadrado',
  tamanoPct: 1.2, proporcion: 1, conPersonas: false, personas: 2, costoExtra: 0,
});

const editando = computed(() => Boolean(props.categoriaId));
const opciones = computed(() => original.value?.opciones || []);
const opcionDefecto = computed(() => opciones.value.find((o) => o.predeterminada) || opciones.value[0] || null);
const variasOpciones = computed(() => opciones.value.length > 1);

function aplicarDatos(c) {
  original.value = c;
  const conPersonas = Number(c.personasPorPuesto || 1) > 1 || Number(c.costoPersonaExtra || 0) > 0;
  Object.assign(f, {
    nombre: c.nombre || '', descripcion: c.descripcion || '', cantidad: Number(c.cantidad || 0), tamano: c.tamano || '',
    color: c.color || '#8b5cf6', forma: c.forma || 'cuadrado', tamanoPct: Number(((c.tamanoMapa ?? 0.012) * 100).toFixed(2)),
    proporcion: Number(c.mapaProporcion ?? 1), conPersonas,
    personas: conPersonas ? Math.max(1, Number(c.personasPorPuesto || 2)) : 2,
    costoExtra: Number(c.costoPersonaExtra || 0),
    precio: Number((opcionDefecto.value?.precio ?? c.precioBase) || 0),
  });
}

async function cargar() {
  if (!props.categoriaId) { cargando.value = false; return; }
  try {
    const r = await apiDeFeria('/api/app/categorias', props.edicion);
    const lista = await r.json();
    const c = lista.find((x) => x.id === props.categoriaId);
    if (!c) { toast('La categoría ya no existe', 'error'); emit('cerrar'); return; }
    aplicarDatos(c);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

watch(() => f.forma, (forma, antes) => {
  if (cargando.value || forma === antes) return;
  if (forma === 'rectangulo' && f.proporcion === 1) f.proporcion = 0.6;
  if (forma !== 'rectangulo') f.proporcion = 1;
});

watch(() => [f.color, f.forma, f.tamanoPct, f.proporcion], () => {
  if (cargando.value) return;
  emit('previa', { categoriaId: props.categoriaId, color: f.color, forma: f.forma, tamanoMapa: f.tamanoPct / 100, mapaProporcion: f.proporcion });
}, { immediate: true });

const listo = computed(() => f.nombre.trim() && Number.isInteger(f.cantidad) && f.cantidad >= 0 && f.precio >= 0);

async function guardar() {
  if (!listo.value || guardando.value) return;
  guardando.value = true;
  const cuerpo = {
    nombre: f.nombre.trim(), color: f.color, forma: f.forma, tamanoMapa: f.tamanoPct / 100, mapaProporcion: f.proporcion,
    descripcion: f.descripcion.trim(), personasPorPuesto: f.conPersonas ? Math.min(50, Math.max(1, Number(f.personas) || 1)) : 1,
    costoPersonaExtra: f.conPersonas ? Math.max(0, f.costoExtra) : 0, precioBase: Number(f.precio) || 0,
  };
  try {
    if (!editando.value) {
      const r = await apiDeFeria('/api/app/categorias', props.edicion, {
        method: 'POST', body: JSON.stringify({ ...cuerpo, cantidad: f.cantidad, tamano: f.tamano.trim() || '3x3' }),
      });
      const d = await r.json().catch(() => ({}));
      if (!r.ok || d.ok === false) throw new Error(d.mensaje || 'No se pudo crear la categoría');
      emit('guardada', { id: d.id, nombre: cuerpo.nombre, nueva: true, cantidad: f.cantidad });
      return;
    }
    if (f.tamano.trim() && f.tamano.trim() !== (original.value.tamano || '')) cuerpo.tamano = f.tamano.trim();
    const r = await apiFetch(`/api/app/categorias/${props.categoriaId}`, { method: 'PATCH', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || d.ok === false) throw new Error(d.mensaje || 'No se pudo guardar');
    if (!variasOpciones.value && opcionDefecto.value && Number(opcionDefecto.value.precio) !== Number(f.precio)) {
      await apiFetch(`/api/app/categorias/${props.categoriaId}/opciones/${opcionDefecto.value.id}`, {
        method: 'PATCH', body: JSON.stringify({ precio: Number(f.precio) || 0 }),
      });
    }
    if (f.cantidad !== Number(original.value.cantidad || 0)) {
      const rc = await apiFetch(`/api/app/categorias/${props.categoriaId}/cantidad`, { method: 'PATCH', body: JSON.stringify({ cantidad: f.cantidad }) });
      const dc = await rc.json().catch(() => ({}));
      if (dc.noQuitadas) toast(`${dc.noQuitadas} no se quitaron porque están vendidos o reservados`, 'advertencia');
    }
    emit('guardada', { id: props.categoriaId, nombre: cuerpo.nombre, nueva: false, cantidad: f.cantidad });
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

function tecla(e) {
  if (e.key === 'Escape' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(e.target?.tagName)) emit('cerrar');
}

onMounted(() => { cargar(); window.addEventListener('keydown', tecla); });
onUnmounted(() => { window.removeEventListener('keydown', tecla); emit('previa', null); });
</script>

<template>
  <aside class="categoria-rapida" role="dialog" :aria-label="editando ? 'Editar categoría' : 'Nueva categoría'">
    <header class="cab">
      <div>
        <strong>{{ editando ? `Editar ${original?.nombre || 'categoría'}` : 'Nueva categoría' }}</strong>
        <small class="muted">Los cambios de color, forma y tamaño se ven en el plano mientras escribes.</small>
      </div>
      <button type="button" class="cerrar" aria-label="Cerrar" title="Cerrar (Esc)" @click="emit('cerrar')">✕</button>
    </header>

    <p v-if="cargando" class="muted">Cargando…</p>
    <form v-else class="cuerpo desliza" @submit.prevent="guardar">
      <label class="campo"><span>Nombre *</span>
        <input v-model="f.nombre" class="control" maxlength="80" :placeholder="`Ej.: ${uno === 'mesa' ? 'Mesa VIP' : 'Stand esquina'}`" autofocus />
      </label>
      <div class="dos">
        <div class="campo"><span>Precio (Bs) *</span>
          <NumeroPaso v-model="f.precio" :min="0" :max="9999999" :paso="10" :decimales="2" etiqueta="Precio" />
          <small v-if="variasOpciones" class="muted">Tiene {{ opciones.length }} precios; cámbialos en «Categorías y precios».</small>
        </div>
        <div class="campo"><span>{{ editando ? `Cantidad de ${varios}` : `¿Cuántos ${varios}?` }}</span>
          <NumeroPaso v-model="f.cantidad" :min="0" :max="2000" :paso="1" :etiqueta="`Cantidad de ${varios}`" />
        </div>
      </div>
      <label class="campo"><span>Qué incluye <small class="muted">(lo ve el cliente)</small></span>
        <textarea v-model="f.descripcion" class="control" rows="2" maxlength="255" placeholder="Ej.: Mesa para 6, una botella y atención preferencial"></textarea>
      </label>

      <fieldset class="grupo">
        <legend>Cómo se ve en el plano</legend>
        <div class="colores">
          <button v-for="c in COLORES" :key="c" type="button" class="color" :class="{ on: f.color === c }" :style="{ background: c }" :aria-label="`Color ${c}`" @click="f.color = c"></button>
          <label class="color propio" title="Otro color"><input v-model="f.color" type="color" /></label>
        </div>
        <div class="formas">
          <button v-for="x in FORMAS" :key="x.v" type="button" class="forma" :class="{ on: f.forma === x.v }" :title="x.t" @click="f.forma = x.v">
            <span class="muestra" :class="`forma-${x.v}`" :style="{ background: f.color }"></span>{{ x.t }}
          </button>
        </div>
        <div class="dos">
          <div class="campo"><span>Tamaño en el plano</span>
            <NumeroPaso v-model="f.tamanoPct" :min="0.3" :max="8" :paso="0.1" :decimales="2" unidad="%" etiqueta="Tamaño en el plano" />
            <small class="muted">Del ancho del plano. Míralo en el mapa: los {{ varios }} ya colocados cambian al instante.</small>
          </div>
          <div v-if="f.forma === 'rectangulo'" class="campo"><span>Alto / ancho</span>
            <NumeroPaso v-model="f.proporcion" :min="0.2" :max="3" :paso="0.05" :decimales="2" etiqueta="Proporción alto sobre ancho" />
          </div>
          <label v-else class="campo"><span>Medida real <small class="muted">(opcional)</small></span>
            <input v-model="f.tamano" class="control" maxlength="40" placeholder="3x3" />
            <small class="muted">{{ medidaLegible(f.tamano) }}</small>
          </label>
        </div>
      </fieldset>

      <PersonasPorEspacio v-model:con-personas="f.conPersonas" v-model:personas="f.personas" v-model:costo-extra="f.costoExtra"
                          :uno="uno" :registra-personas="registraPersonas" />
    </form>

    <footer v-if="!cargando" class="pie">
      <button v-if="editando" type="button" class="btn btn-fantasma btn-sm" @click="emit('avanzado')">Más precios…</button>
      <span class="crecer"></span>
      <button type="button" class="btn btn-fantasma" @click="emit('cerrar')">Cancelar</button>
      <button type="button" class="btn btn-primario" :disabled="!listo || guardando" @click="guardar">{{ guardando ? 'Guardando…' : editando ? 'Guardar' : `Crear y colocar →` }}</button>
    </footer>
  </aside>
</template>

<style scoped>
.categoria-rapida {
  position: absolute; top: 0.6rem; right: 0.6rem; bottom: 0.6rem; z-index: 40; width: 360px; display: flex; flex-direction: column;
  background: var(--panel); color: var(--text); border: 1px solid var(--border); border-radius: var(--radio); box-shadow: var(--sombra-md);
}
.cab { display: flex; justify-content: space-between; gap: 0.5rem; padding: 0.8rem 0.9rem 0.5rem; border-bottom: 1px solid var(--border); }
.cab > div { display: flex; flex-direction: column; gap: 0.15rem; }
.cab small { font-size: 0.75rem; line-height: 1.3; }
.cerrar { border: 0; background: none; color: var(--muted); font-size: 1.05rem; cursor: pointer; align-self: flex-start; }
.cuerpo { flex: 1; min-height: 0; overflow-y: auto; padding: 0.8rem 0.9rem; display: flex; flex-direction: column; gap: 0.8rem; }
.campo { display: flex; flex-direction: column; gap: 0.3rem; min-width: 0; }
.campo > span { font-size: 0.8rem; font-weight: 700; color: var(--muted); }
.campo small { font-size: 0.74rem; line-height: 1.3; }
.dos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; align-items: start; }
.grupo { border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.6rem 0.7rem 0.75rem; margin: 0; display: flex; flex-direction: column; gap: 0.6rem; }
.grupo legend { font-size: 0.78rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: var(--muted); padding: 0 0.3rem; }
.colores { display: flex; flex-wrap: wrap; gap: 0.35rem; }
.color { width: 26px; height: 26px; border-radius: 50%; border: 2px solid rgba(255, 255, 255, 0.6); cursor: pointer; padding: 0; position: relative; }
.color.on { outline: 3px solid var(--acento); outline-offset: 1px; }
.color.propio { background: conic-gradient(red, orange, yellow, lime, cyan, blue, magenta, red); overflow: hidden; }
.color.propio input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
.formas { display: flex; flex-wrap: wrap; gap: 0.3rem; }
.forma { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.3rem 0.5rem; border-radius: 8px; border: 1px solid var(--border); background: var(--panel); color: var(--text); font: inherit; font-size: 0.78rem; cursor: pointer; }
.forma.on { border-color: var(--acento); background: var(--acento-suave); font-weight: 700; }
.muestra { width: 14px; height: 14px; display: inline-block; }
.forma-cuadrado { border-radius: 3px; }
.forma-rectangulo { width: 18px; height: 11px; border-radius: 2px; }
.forma-circulo { border-radius: 50%; }
.forma-triangulo { clip-path: polygon(50% 0, 100% 100%, 0 100%); }
.forma-hexagono { clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%); }
.check { display: flex; align-items: center; gap: 0.5rem; font-weight: 700; font-size: 0.88rem; cursor: pointer; }
.check input { width: 18px; height: 18px; }
.chico { font-size: 0.78rem; margin: 0; line-height: 1.4; }
.aviso { padding: 0.45rem 0.6rem; border-radius: 8px; background: color-mix(in srgb, var(--tramite, #d97706) 14%, transparent); }
.pie { display: flex; align-items: center; gap: 0.4rem; padding: 0.6rem 0.9rem; border-top: 1px solid var(--border); }
.crecer { flex: 1; }
@media (max-width: 820px) {
  .categoria-rapida { left: 0.5rem; right: 0.5rem; top: auto; width: auto; max-height: 72%; }
}
</style>
