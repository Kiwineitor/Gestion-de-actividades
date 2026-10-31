<script setup>
import { ref, reactive, computed } from 'vue';
import UiModal from './UiModal.vue';

const props = defineProps({
  aspecto: { type: Number, default: 0.62 },
  conFondo: { type: Boolean, default: false },
  ocupado: { type: Boolean, default: false },
  objetos: { type: String, default: 'mesas, textos y zonas' },
});
const emit = defineEmits(['aplicar', 'quitar-fondo', 'cerrar']);

const FORMAS = [
  { clave: 'panoramica', nombre: 'Horizontal ancha', detalle: '16 : 9', ancho: 1600, alto: 900 },
  { clave: 'horizontal', nombre: 'Horizontal', detalle: '3 : 2', ancho: 1500, alto: 1000 },
  { clave: 'cuadrada', nombre: 'Cuadrada', detalle: '1 : 1', ancho: 1200, alto: 1200 },
  { clave: 'vertical', nombre: 'Vertical', detalle: '3 : 4', ancho: 1200, alto: 1600 },
  { clave: 'alta', nombre: 'Vertical alta', detalle: '9 : 16', ancho: 900, alto: 1600 },
];

const actual = FORMAS.find((f) => Math.abs(f.alto / f.ancho - props.aspecto) < 0.02);
const elegida = ref(actual ? actual.clave : 'medidas');
const medidas = reactive({ ancho: 40, largo: Math.round(40 * props.aspecto) });
const conservar = ref(true);

const nueva = computed(() => {
  if (elegida.value !== 'medidas') {
    const f = FORMAS.find((x) => x.clave === elegida.value);
    return { ancho: f.ancho, alto: f.alto };
  }
  const a = Number(medidas.ancho);
  const l = Number(medidas.largo);
  if (!(a > 0) || !(l > 0)) return null;
  const relacion = Math.min(5, Math.max(0.2, l / a));
  return relacion <= 1 ? { ancho: 1600, alto: Math.round(1600 * relacion) } : { ancho: Math.round(1600 / relacion), alto: 1600 };
});
const aspectoNuevo = computed(() => (nueva.value ? nueva.value.alto / nueva.value.ancho : props.aspecto));
const igual = computed(() => Math.abs(aspectoNuevo.value - props.aspecto) < 0.005);

function caja(aspecto, lado = 78) {
  return aspecto <= 1 ? { width: `${lado}px`, height: `${lado * aspecto}px` } : { width: `${lado / aspecto}px`, height: `${lado}px` };
}

function aplicar() {
  if (nueva.value && !igual.value) emit('aplicar', { ...nueva.value, conservar: conservar.value });
}
</script>

<template>
  <UiModal titulo="Forma del plano" ancho="640px" @cerrar="emit('cerrar')">
    <template v-if="conFondo">
      <p class="aviso">La forma del plano la da la <b>imagen de fondo</b>. Para elegir otra forma, primero quita el fondo
        (los {{ objetos }} se quedan) o sube una imagen con la forma que quieras.</p>
    </template>
    <template v-else>
      <p class="muted chico">Elige cómo es tu lugar: más ancho, cuadrado o más largo. Lo verás así en el editor, en la vista previa y en la página pública.</p>
      <div class="formas">
        <button v-for="f in FORMAS" :key="f.clave" type="button" class="forma" :class="{ on: elegida === f.clave }" @click="elegida = f.clave">
          <span class="marco"><i :style="caja(f.alto / f.ancho)"></i></span>
          <strong>{{ f.nombre }}</strong><small>{{ f.detalle }}</small>
        </button>
        <button type="button" class="forma" :class="{ on: elegida === 'medidas' }" @click="elegida = 'medidas'">
          <span class="marco"><i class="medida" :style="caja(elegida === 'medidas' ? aspectoNuevo : 0.7)"></i></span>
          <strong>Medidas del lugar</strong><small>en metros</small>
        </button>
      </div>
      <div v-if="elegida === 'medidas'" class="medidas">
        <label class="campo"><span>Ancho (m)</span><input v-model.number="medidas.ancho" type="number" min="1" max="2000" class="control" /></label>
        <span class="por">×</span>
        <label class="campo"><span>Largo (m)</span><input v-model.number="medidas.largo" type="number" min="1" max="2000" class="control" /></label>
      </div>

      <div class="comparar">
        <div><span class="marco grande"><i :style="caja(aspecto, 110)"></i></span><small>Ahora</small></div>
        <span class="flecha">→</span>
        <div><span class="marco grande"><i class="nuevo" :style="caja(aspectoNuevo, 110)"></i></span><small>Nueva</small></div>
      </div>

      <div class="opciones">
        <label class="opcion" :class="{ on: conservar }">
          <input v-model="conservar" type="radio" :value="true" />
          <span><b>Mantener la distribución</b><small>Todo se acomoda centrado, sin estirarse ni deformarse. Recomendado.</small></span>
        </label>
        <label class="opcion" :class="{ on: !conservar }">
          <input v-model="conservar" type="radio" :value="false" />
          <span><b>Solo cambiar la forma</b><small>Cada cosa queda en la misma posición relativa; puede que tengas que reacomodar.</small></span>
        </label>
      </div>
    </template>

    <template #pie>
      <button class="btn btn-fantasma" @click="emit('cerrar')">Cancelar</button>
      <button v-if="conFondo" class="btn btn-peligro" :disabled="ocupado" @click="emit('quitar-fondo')">Quitar fondo</button>
      <button v-else class="btn btn-primario" :disabled="ocupado || !nueva || igual" @click="aplicar">
        {{ ocupado ? 'Aplicando…' : igual ? 'Ya tiene esta forma' : 'Aplicar forma' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.chico { font-size: 0.85rem; margin: 0; }
.aviso { margin: 0; padding: 0.8rem 1rem; border-radius: var(--radio-sm); background: color-mix(in srgb, var(--tramite) 14%, transparent); }
.formas { display: grid; grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); gap: 0.5rem; }
.forma {
  display: flex; flex-direction: column; align-items: center; gap: 0.25rem; padding: 0.6rem 0.4rem; cursor: pointer;
  border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel); color: var(--text); font: inherit;
}
.forma:hover { border-color: var(--acento); }
.forma.on { border-color: var(--acento); background: var(--acento-suave); box-shadow: 0 0 0 1px var(--acento) inset; }
.forma strong { font-size: 0.82rem; text-align: center; }
.forma small { font-size: 0.72rem; color: var(--muted); }
.marco { width: 84px; height: 84px; display: grid; place-items: center; }
.marco i { display: block; border-radius: 4px; background: color-mix(in srgb, var(--acento) 25%, transparent); border: 2px solid var(--acento); transition: width 0.2s, height 0.2s; }
.marco i.medida { border-style: dashed; }
.medidas { display: flex; align-items: flex-end; gap: 0.6rem; }
.medidas .campo { flex: 1; }
.por { padding-bottom: 0.7rem; color: var(--muted); font-weight: 700; }
.comparar { display: flex; align-items: center; justify-content: center; gap: 1.2rem; padding: 0.6rem; border-radius: var(--radio-sm); background: var(--bg); }
.comparar > div { display: flex; flex-direction: column; align-items: center; gap: 0.2rem; }
.comparar small { color: var(--muted); font-size: 0.78rem; }
.marco.grande { width: 116px; height: 116px; }
.marco i.nuevo { background: color-mix(in srgb, var(--ok) 22%, transparent); border-color: var(--ok); }
.flecha { font-size: 1.4rem; color: var(--muted); }
.opciones { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; }
.opcion { display: flex; gap: 0.5rem; align-items: flex-start; padding: 0.6rem; border: 1px solid var(--border); border-radius: var(--radio-sm); cursor: pointer; }
.opcion.on { border-color: var(--acento); background: var(--acento-suave); }
.opcion span { display: flex; flex-direction: column; gap: 0.15rem; font-size: 0.88rem; }
.opcion small { color: var(--muted); font-size: 0.76rem; }
@media (max-width: 560px) { .opciones { grid-template-columns: 1fr; } }
</style>
