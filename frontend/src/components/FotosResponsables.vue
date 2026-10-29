<script setup>
import { ref, computed, watch } from 'vue';
import { apiFetch } from '../api';
import { url as urlServidor } from '../config';
import { toast } from '../ui/toast';
import ArchivoPreview from './ArchivoPreview.vue';

const props = defineProps({
  inscripcionId: { type: [Number, String], required: true },
});

const responsables = ref([]);
const cargando = ref(false);
const ocupado = ref(null);
const entradas = ref({});
const archivoSeleccionado = ref(null);
const responsableParaFoto = ref(null);

const completas = computed(() =>
  responsables.value.length > 0 && responsables.value.every((r) => r.tieneFoto));
const faltan = computed(() => responsables.value.filter((r) => !r.tieneFoto).length);

const fotoDe = (r) => (r.fotoUrl ? urlServidor(r.fotoUrl) : null);

async function cargar() {
  cargando.value = true;
  try {
    const r = await apiFetch(`/api/app/inscripciones/${props.inscripcionId}/responsables`);
    if (!r.ok) throw new Error('No se pudieron cargar los responsables');
    const d = await r.json();
    responsables.value = d.responsables ?? [];
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function onArchivoElegido(responsable, evento) {
  const archivo = evento.target.files?.[0];
  evento.target.value = '';
  if (!archivo) return;
  responsableParaFoto.value = responsable.id;
  archivoSeleccionado.value = archivo;
}

async function confirmarSubidaFoto(archivo) {
  const responsableId = responsableParaFoto.value;
  if (!archivo || !responsableId) return;

  ocupado.value = responsableId;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(
      `/api/app/inscripciones/${props.inscripcionId}/responsables/${responsableId}/foto`,
      { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (r.ok && d.ok) {
      const i = responsables.value.findIndex((x) => x.id === responsableId);
      if (i >= 0 && d.responsable) responsables.value[i] = d.responsable;
      toast(`Foto guardada`, 'ok');
    } else {
      toast(d.mensaje || 'No se pudo guardar la foto', 'error');
    }
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = null;
    responsableParaFoto.value = null;
    archivoSeleccionado.value = null;
  }
}

async function quitar(responsable) {
  if (!confirm(`¿Quitar la foto de ${responsable.nombre}?`)) return;
  ocupado.value = responsable.id;
  try {
    const r = await apiFetch(
      `/api/app/inscripciones/${props.inscripcionId}/responsables/${responsable.id}/foto`,
      { method: 'DELETE' });
    const d = await r.json().catch(() => ({}));
    if (r.ok && d.ok) {
      const i = responsables.value.findIndex((x) => x.id === responsable.id);
      if (i >= 0 && d.responsable) responsables.value[i] = d.responsable;
    } else {
      toast(d.mensaje || 'No se pudo quitar la foto', 'error');
    }
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    ocupado.value = null;
  }
}

watch(() => props.inscripcionId, cargar, { immediate: true });
</script>

<template>
  <div class="fotos">
    <div class="cabecera">
      <h4>Fotos para credenciales</h4>
      <span v-if="cargando" class="muted">Cargando…</span>
      <span v-else-if="completas" class="badge badge-ok">Completas</span>
      <span v-else-if="responsables.length" class="badge badge-danger">
        Falta{{ faltan === 1 ? '' : 'n' }} {{ faltan }}
      </span>
    </div>

    <p v-if="!cargando && !responsables.length" class="muted">
      Esta venta no tiene responsables registrados.
    </p>

    <ul v-else class="lista">
      <li v-for="r in responsables" :key="r.id" class="persona">
        <div class="retrato" :class="{ vacio: !r.tieneFoto }">
          <img v-if="r.tieneFoto" :src="fotoDe(r)" :alt="`Foto de ${r.nombre}`" loading="lazy" />
          <span v-else aria-hidden="true">👤</span>
        </div>

        <div class="datos">
          <strong>{{ r.nombre }}</strong>
          <span class="muted">
            {{ r.esTitular ? 'Responsable 1' : 'Responsable 2' }}{{ r.ci ? ` · C.I. ${r.ci}` : '' }}
          </span>
        </div>

        <div class="acciones">
          <input :ref="(el) => (entradas[r.id] = el)" type="file" accept="image/jpeg,image/png"
                 class="oculto" @change="(e) => onArchivoElegido(r, e)" />
          <button class="btn btn-sm" :disabled="ocupado === r.id"
                  @click="entradas[r.id]?.click()">
            {{ ocupado === r.id ? 'Guardando…' : (r.tieneFoto ? 'Cambiar' : '📷 Agregar foto') }}
          </button>
          <button v-if="r.tieneFoto" class="btn btn-fantasma btn-sm" :disabled="ocupado === r.id"
                  title="Quitar la foto" @click="quitar(r)">✕</button>
          <span class="formato-permitido">Formatos: JPG, PNG</span>
        </div>
      </li>
    </ul>
  </div>

  <ArchivoPreview
    v-if="archivoSeleccionado"
    :archivo="archivoSeleccionado"
    :texto-confirmar="'Subir foto'"
    :texto-cancelar="'Cancelar'"
    :previsualizar-imagen="true"
    @confirmar="confirmarSubidaFoto"
    @cancelar="archivoSeleccionado = null"
  />
</template>

<style scoped>
.fotos { margin-top: 0.9rem; }
.cabecera { display: flex; align-items: center; gap: 0.6rem; margin-bottom: 0.6rem; }
.cabecera h4 { margin: 0; font-size: 0.95rem; }

.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.6rem; }
.persona {
  display: flex; align-items: center; gap: 0.8rem;
  padding: 0.6rem; border: 1px solid var(--border); border-radius: var(--radio-sm);
  background: var(--panel);
}

.retrato {
  width: 56px; height: 56px; flex: none; border-radius: var(--radio-sm); overflow: hidden;
  background: var(--panel-2); display: grid; place-items: center; font-size: 1.5rem;
}
.retrato.vacio { border: 2px dashed var(--border); color: var(--muted); }
.retrato img { width: 100%; height: 100%; object-fit: cover; display: block; }

.datos { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.datos .muted { font-size: 0.82rem; }
.acciones { display: flex; align-items: center; gap: 0.35rem; flex: none; }
.oculto { display: none; }
.formato-permitido { font-size: 0.72rem; color: var(--muted); margin-left: auto; }

@media (max-width: 560px) {
  .persona { flex-wrap: wrap; }
  .retrato { width: 64px; height: 64px; }
  .acciones { width: 100%; }
  .acciones .btn:first-child { flex: 1; }
}
</style>
