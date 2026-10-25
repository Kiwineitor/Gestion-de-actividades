<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from './UiModal.vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';

const props = defineProps({
  credencial: { type: Object, required: true },
});
const emit = defineEmits(['cerrar', 'guardado']);

const ETIQUETAS = { nombre: 'Nombre', paterno: 'Ap. paterno', materno: 'Ap. materno', ci: 'C.I.', cargo: 'Cargo', foto: 'Foto' };

const cargando = ref(true);
const guardando = ref(false);
const pestana = ref('datos');
const original = ref(null);
const form = reactive({ nombre: '', paterno: '', materno: '', ci: '', cargo: '' });
const fotoUrl = ref(null);
const fotoNueva = ref(null);
const vistaFoto = ref(null);
const cambios = ref([]);

const ruta = computed(() => `/api/app/credenciales/${props.credencial.responsableId}`);
const modificado = computed(() => {
  if (!original.value) return false;
  return Object.keys(form).some((k) => (form[k] || '').trim() !== (original.value[k] || ''))
    || fotoNueva.value != null;
});

function llenar(d) {
  original.value = d;
  for (const k of Object.keys(form)) form[k] = d[k] || '';
  fotoUrl.value = d.fotoUrl;
}

async function cargar() {
  cargando.value = true;
  try {
    const [r, h] = await Promise.all([apiFetch(`${ruta.value}/datos`), apiFetch(`${ruta.value}/cambios`)]);
    if (!r.ok) { toast('No se pudieron leer los datos', 'error'); emit('cerrar'); return; }
    llenar(await r.json());
    cambios.value = h.ok ? await h.json() : [];
  } catch (e) {
    toast(e.message, 'error');
    emit('cerrar');
  } finally {
    cargando.value = false;
  }
}

function elegirFoto(evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  if (!archivo.type.startsWith('image/')) { toast('La foto debe ser una imagen', 'advertencia'); return; }
  fotoNueva.value = archivo;
  if (vistaFoto.value) URL.revokeObjectURL(vistaFoto.value);
  vistaFoto.value = URL.createObjectURL(archivo);
}

async function guardar() {
  if (!form.nombre.trim()) { toast('El nombre no puede quedar vacío', 'advertencia'); return; }
  guardando.value = true;
  try {
    let datos = original.value;
    const datosCambiados = Object.keys(form).some((k) => (form[k] || '').trim() !== (original.value[k] || ''));
    if (datosCambiados) {
      const r = await apiFetch(`${ruta.value}/datos`, { method: 'PATCH', body: JSON.stringify(form) });
      const d = await r.json().catch(() => ({}));
      if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudieron guardar los datos', 'error'); return; }
      datos = d.datos;
    }
    if (fotoNueva.value) {
      const fd = new FormData();
      fd.append('archivo', fotoNueva.value);
      const r = await apiFetch(`${ruta.value}/foto`, { method: 'POST', body: fd });
      const d = await r.json().catch(() => ({}));
      if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar la foto', 'error'); return; }
      datos = d.datos;
    }
    toast('Datos actualizados', 'ok', 1800);
    emit('guardado', datos);
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    guardando.value = false;
  }
}

function valorLegible(c, v) {
  if (v == null || v === '') return '—';
  return c === 'foto' ? 'imagen' : v;
}

onMounted(cargar);
</script>

<template>
  <UiModal :titulo="`Datos de ${credencial.nombre}`" @cerrar="!guardando && emit('cerrar')">
    <div class="pestanas">
      <button :class="{ on: pestana === 'datos' }" @click="pestana = 'datos'">Datos</button>
      <button :class="{ on: pestana === 'historial' }" @click="pestana = 'historial'">
        Cambios<span v-if="cambios.length"> ({{ cambios.length }})</span>
      </button>
    </div>

    <p v-if="cargando" class="muted">Cargando…</p>

    <div v-else-if="pestana === 'datos'" class="formulario">
      <div class="foto-fila">
        <img v-if="vistaFoto || fotoUrl" :src="vistaFoto || fotoUrl" alt="" class="foto" />
        <div v-else class="foto vacia">Sin foto</div>
        <label class="btn btn-sm">
          Cambiar foto
          <input type="file" accept="image/*" class="oculto" @change="elegirFoto" />
        </label>
      </div>
      <label class="campo"><span>Nombres</span><input v-model="form.nombre" class="control" maxlength="60" /></label>
      <div class="dos">
        <label class="campo"><span>Apellido paterno</span><input v-model="form.paterno" class="control" maxlength="60" /></label>
        <label class="campo"><span>Apellido materno</span><input v-model="form.materno" class="control" maxlength="60" /></label>
      </div>
      <div class="dos">
        <label class="campo"><span>C.I.</span><input v-model="form.ci" class="control" maxlength="20" /></label>
        <label class="campo"><span>Cargo o tipo</span><input v-model="form.cargo" class="control" maxlength="60" placeholder="Ej. Gerente, Expositor" /></label>
      </div>
      <p class="muted chico">Los cambios se guardan en la ficha de la persona y quedan registrados. Las credenciales ya impresas no cambian: hay que volver a generarlas.</p>
    </div>

    <div v-else>
      <p v-if="!cambios.length" class="muted">Sin cambios registrados.</p>
      <ul v-else class="cambios">
        <li v-for="(c, i) in cambios" :key="i">
          <span class="cuando">{{ c.fecha.slice(0, 16).replace('T', ' ') }} · {{ c.usuario }}</span>
          <span><strong>{{ ETIQUETAS[c.campo] || c.campo }}:</strong> {{ valorLegible(c.campo, c.anterior) }} → {{ valorLegible(c.campo, c.nuevo) }}</span>
        </li>
      </ul>
    </div>

    <template #pie>
      <button class="btn btn-fantasma" :disabled="guardando" @click="emit('cerrar')">Cerrar</button>
      <button v-if="pestana === 'datos'" class="btn btn-primario" :disabled="guardando || !modificado" @click="guardar">
        {{ guardando ? 'Guardando…' : 'Guardar' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.pestanas { display: flex; gap: 0.3rem; margin-bottom: 0.9rem; border-bottom: 1px solid var(--border); }
.pestanas button {
  border: none; background: none; color: var(--muted); font: inherit; font-weight: 600;
  padding: 0.45rem 0.7rem; border-bottom: 2px solid transparent; cursor: pointer;
}
.pestanas button.on { color: var(--text); border-bottom-color: var(--acento); }
.formulario { display: flex; flex-direction: column; gap: 0.75rem; }
.dos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }
.foto-fila { display: flex; align-items: center; gap: 0.9rem; }
.foto { width: 76px; height: 76px; border-radius: 50%; object-fit: cover; border: 1px solid var(--border); }
.foto.vacia { display: grid; place-items: center; font-size: 0.75rem; color: var(--muted); background: var(--bg); }
.oculto { display: none; }
.chico { font-size: 0.82rem; margin: 0; }
.cambios { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.cambios li { display: flex; flex-direction: column; gap: 0.1rem; font-size: 0.88rem; padding-bottom: 0.45rem; border-bottom: 1px solid var(--border); }
.cuando { font-size: 0.76rem; color: var(--muted); font-variant-numeric: tabular-nums; }
@media (max-width: 480px) { .dos { grid-template-columns: 1fr; } }
</style>
