<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const props = defineProps({
  edicion: { type: Number, required: true },
});
const emit = defineEmits(['personalizar']);

const info = ref(null);
const estilo = ref('NOCHE');
const datos = ref(true);
const vista = ref('');
const cargandoVista = ref(false);
const guardando = ref(false);
let peticion = 0;

const OTROS = [
  { clave: 'FONDO', nombre: 'Mi imagen de fondo', icono: '🖼️', detalle: 'Subes tu diseño y el sistema coloca el QR y los datos.' },
  { clave: 'SOLO_QR', nombre: 'Solo el QR', icono: '🔳', detalle: 'Fondo blanco y un QR grande en el centro.' },
  { clave: 'PERSONALIZADA', nombre: 'Diseño avanzado', icono: '🎨', detalle: 'Ubicas cada dato a mano en el diseñador de credenciales.' },
];

const cambiado = computed(() => info.value && (estilo.value !== info.value.estilo || datos.value !== info.value.datos));

async function cargar() {
  const r = await apiFetch(`/api/app/ediciones/${props.edicion}/entrada-diseno`);
  if (!r.ok) return;
  info.value = await r.json();
  estilo.value = info.value.estilo;
  datos.value = info.value.datos;
  await previsualizar();
}

async function previsualizar() {
  const n = ++peticion;
  cargandoVista.value = true;
  try {
    const r = await apiFetch(`/api/app/ediciones/${props.edicion}/entrada-diseno/vista.png?estilo=${estilo.value}&datos=${datos.value}`);
    if (!r.ok || n !== peticion) return;
    const url = URL.createObjectURL(await r.blob());
    if (vista.value) URL.revokeObjectURL(vista.value);
    vista.value = url;
  } finally {
    if (n === peticion) cargandoVista.value = false;
  }
}

function elegir(clave) {
  if (clave === 'FONDO' && !info.value?.fondoUrl) { selector.value?.click(); return; }
  if (clave === 'PERSONALIZADA' && !info.value?.tienePersonalizada) { emit('personalizar'); return; }
  estilo.value = clave;
}

const selector = ref(null);
async function subirFondo(e) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (!archivo) return;
  const cuerpo = new FormData();
  cuerpo.append('archivo', archivo);
  const r = await apiFetch(`/api/app/ediciones/${props.edicion}/entrada-diseno/fondo`, { method: 'POST', body: cuerpo });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir el fondo', 'error'); return; }
  toast('Fondo guardado: así se verá tu entrada', 'ok', 2000);
  await cargar();
}

async function guardar() {
  guardando.value = true;
  try {
    const r = await apiFetch(`/api/app/ediciones/${props.edicion}/entrada-diseno`, { method: 'PUT', body: JSON.stringify({ estilo: estilo.value, datos: datos.value }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    toast(d.mensaje, 'ok', 1800);
    info.value = { ...info.value, estilo: estilo.value, datos: datos.value };
  } finally {
    guardando.value = false;
  }
}

watch([estilo, datos], previsualizar);
watch(() => props.edicion, cargar);
onMounted(cargar);
onBeforeUnmount(() => { if (vista.value) URL.revokeObjectURL(vista.value); });

defineExpose({ guardar, cambiado });
</script>

<template>
  <div class="diseno-entrada">
    <div class="opciones-de">
      <p class="muted chico">Así recibe el cliente cada entrada: una imagen vertical para el celular, con un QR único. Al escanearlo se verifica que sea real y si ya entró.</p>
      <div class="estilos">
        <button v-for="t in info?.estilos || []" :key="t.clave" type="button" class="estilo" :class="{ on: estilo === t.clave }" @click="elegir(t.clave)">
          <span class="muestra" :style="{ background: `linear-gradient(145deg, ${t.desde}, ${t.hasta})` }"><i :style="{ background: t.acento }"></i></span>
          <b>{{ t.nombre }}</b>
        </button>
      </div>
      <div class="otros">
        <button v-for="o in OTROS" :key="o.clave" type="button" class="otro" :class="{ on: estilo === o.clave }" @click="elegir(o.clave)">
          <span class="ico">{{ o.icono }}</span>
          <span><b>{{ o.nombre }}</b><small>{{ o.detalle }}</small></span>
        </button>
      </div>
      <input ref="selector" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="subirFondo" />
      <div v-if="estilo === 'FONDO'" class="ayuda-fondo">
        <b>📐 Medida recomendada: 1080 × 1920 px</b> (vertical 9:16, como una historia de Instagram). Pon el nombre de la actividad y tu arte arriba y deja libre la parte de abajo: ahí va el QR.
        <button type="button" class="btn btn-sm" @click="selector?.click()">{{ info?.fondoUrl ? '🔁 Cambiar imagen' : '⬆ Subir imagen' }}</button>
      </div>
      <p v-if="estilo === 'PERSONALIZADA'" class="ayuda-fondo">Usa el diseño que armaste en el diseñador de credenciales. <button type="button" class="btn btn-sm" @click="emit('personalizar')">Editar diseño avanzado</button></p>
      <label v-if="estilo !== 'SOLO_QR' && estilo !== 'PERSONALIZADA'" class="check-datos">
        <input v-model="datos" type="checkbox" />
        <span>{{ estilo === 'FONDO' ? 'Mostrar un panel con el tipo de entrada y el nombre del comprador' : 'Mostrar el nombre del comprador' }}</span>
      </label>
      <button type="button" class="btn btn-primario" :disabled="!cambiado || guardando" @click="guardar">{{ guardando ? 'Guardando…' : cambiado ? '💾 Guardar diseño de la entrada' : '✓ Diseño guardado' }}</button>
    </div>
    <figure class="vista">
      <img v-if="vista" :src="vista" alt="Vista previa de la entrada" :class="{ cargando: cargandoVista }" />
      <figcaption class="muted chico">Vista previa con datos de ejemplo</figcaption>
    </figure>
  </div>
</template>

<style scoped>
.diseno-entrada { display: grid; grid-template-columns: minmax(0, 1fr) 220px; gap: 1rem; align-items: start; }
.opciones-de { display: flex; flex-direction: column; gap: 0.7rem; min-width: 0; }
.estilos { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0.5rem; }
.estilo { display: flex; flex-direction: column; align-items: center; gap: 0.35rem; padding: 0.4rem; border-radius: var(--radio-sm); border: 2px solid var(--border); background: var(--panel); color: var(--text); font: inherit; font-size: 0.82rem; cursor: pointer; }
.estilo.on, .otro.on { border-color: var(--acento); background: var(--acento-suave); }
.muestra { position: relative; width: 100%; aspect-ratio: 9 / 12; border-radius: 8px; overflow: hidden; }
.muestra i { position: absolute; left: 22%; right: 22%; bottom: 14%; height: 40%; border-radius: 6px; background: #fff !important; box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.4); }
.otros { display: flex; flex-direction: column; gap: 0.4rem; }
.otro { display: flex; gap: 0.6rem; align-items: center; text-align: left; padding: 0.5rem 0.65rem; border-radius: var(--radio-sm); border: 2px solid var(--border); background: var(--panel); color: var(--text); font: inherit; cursor: pointer; }
.otro .ico { font-size: 1.3rem; }
.otro span { display: flex; flex-direction: column; }
.otro small { color: var(--muted); font-size: 0.78rem; }
.ayuda-fondo { margin: 0; display: flex; flex-direction: column; align-items: flex-start; gap: 0.4rem; font-size: 0.82rem; padding: 0.55rem 0.7rem; border-radius: var(--radio-sm); background: var(--panel-2); }
.check-datos { display: flex; gap: 0.5rem; align-items: center; font-size: 0.88rem; cursor: pointer; }
.vista { margin: 0; display: flex; flex-direction: column; gap: 0.3rem; align-items: center; position: sticky; top: 0.5rem; }
.vista img { width: 100%; border-radius: 14px; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.35); transition: opacity 0.2s; }
.vista img.cargando { opacity: 0.55; }
@media (max-width: 720px) {
  .diseno-entrada { grid-template-columns: 1fr; }
  .vista { position: static; max-width: 240px; justify-self: center; }
  .estilos { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
