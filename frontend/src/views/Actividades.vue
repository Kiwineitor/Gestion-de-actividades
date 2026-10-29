<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import UiModal from '../components/UiModal.vue';
import MenuAcciones from '../components/MenuAcciones.vue';
import { apiFetch, jsonOError } from '../api';
import { toast } from '../ui/toast';
import { alertaConAccion } from '../ui/alerta';
import { useAuthStore } from '../stores/auth';
import { useActividadStore } from '../stores/actividad';
import { url as urlApi } from '../config.js';

const TIPOS = ['Feria', 'Exposición', 'Evento', 'Congreso', 'Festival', 'Otro'];

const auth = useAuthStore();
const actividad = useActividadStore();
const esSuper = computed(() => (auth.rol || '').toUpperCase().replace(/ /g, '_') === 'SUPER_USUARIO');

const lista = ref([]);
const cargando = ref(true);
const form = reactive({
  abierto: false, editando: null, nombre: '', anio: new Date().getFullYear(), tipo: 'Feria',
  copiarDe: '', copiarPlano: true, copiarTextos: true, guardando: false,
  lugar: '', fechasTexto: '', descripcionPublica: '', contacto: '', publica: true, recibeInteresados: true,
  portadaUrl: null, subiendo: false,
});

const PUBLICO_VACIO = { lugar: '', fechasTexto: '', descripcionPublica: '', contacto: '', publica: true, recibeInteresados: true, portadaUrl: null };

const activa = computed(() => lista.value.find((a) => a.activa) || null);

async function cargar() {
  cargando.value = true;
  try {
    lista.value = await jsonOError(await apiFetch('/api/app/ediciones/resumen'), 'No se pudieron cargar las actividades');
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

function abrirCrear() {
  Object.assign(form, {
    abierto: true, editando: null, nombre: '', anio: new Date().getFullYear(), tipo: 'Feria',
    copiarDe: activa.value ? String(activa.value.id) : '', copiarPlano: true, copiarTextos: true, guardando: false,
    ...PUBLICO_VACIO,
  });
}

function abrirEditar(a) {
  Object.assign(form, {
    abierto: true, editando: a, nombre: a.nombre, anio: a.anio, tipo: a.tipo || '', copiarDe: '', guardando: false,
    lugar: a.lugar || '', fechasTexto: a.fechasTexto || '', descripcionPublica: a.descripcionPublica || '',
    contacto: a.contacto || '', publica: a.publica !== false, recibeInteresados: a.recibeInteresados !== false,
    portadaUrl: a.portadaUrl || null,
  });
}

async function guardar() {
  if (!form.nombre.trim()) { toast('La actividad necesita un nombre', 'advertencia'); return; }
  form.guardando = true;
  try {
    const cuerpo = {
      nombre: form.nombre.trim(), anio: Number(form.anio) || null, tipo: form.tipo || null,
      copiarDe: form.editando || !form.copiarDe ? null : Number(form.copiarDe),
      copiarPlano: form.copiarPlano, copiarTextos: form.copiarTextos,
      lugar: form.lugar, fechasTexto: form.fechasTexto, descripcionPublica: form.descripcionPublica,
      contacto: form.contacto, publica: form.publica, recibeInteresados: form.recibeInteresados,
    };
    const r = form.editando
      ? await apiFetch(`/api/app/ediciones/${form.editando.id}`, { method: 'PATCH', body: JSON.stringify(cuerpo) })
      : await apiFetch('/api/app/ediciones', { method: 'POST', body: JSON.stringify(cuerpo) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return; }
    toast(d.mensaje, 'ok');
    form.abierto = false;
    await cargar();
    await actividad.cargar();
  } finally {
    form.guardando = false;
  }
}

async function subirPortada(e) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (!archivo || !form.editando) return;
  form.subiendo = true;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    const r = await apiFetch(`/api/app/ediciones/${form.editando.id}/portada`, { method: 'POST', body: datos });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo subir la portada', 'error'); return; }
    toast(d.mensaje, 'ok');
    await cargar();
    form.portadaUrl = lista.value.find((x) => x.id === form.editando.id)?.portadaUrl || null;
  } finally {
    form.subiendo = false;
  }
}

async function quitarPortada() {
  if (!form.editando) return;
  const r = await apiFetch(`/api/app/ediciones/${form.editando.id}/portada`, { method: 'DELETE' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo quitar la portada', 'error'); return; }
  form.portadaUrl = null;
  await cargar();
}

async function activar(a) {
  const si = await alertaConAccion(
    `¿Activar "${a.nombre}"? Todos los usuarios pasarán a ver su plano, sus puestos y sus ventas. `
    + `"${activa.value?.nombre ?? 'La actividad actual'}" queda guardada y se puede volver a activar.`,
    'advertencia', () => {});
  if (!si) return;
  const r = await apiFetch(`/api/app/ediciones/${a.id}/activar`, { method: 'POST' });
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo activar', 'error'); return; }
  toast(d.mensaje, 'ok');
  await cargar();
  await actividad.cargar();
}

function acciones(a) {
  return [
    { txt: 'Editar', accion: () => abrirEditar(a) },
    {
      txt: 'Activar', deshabilitado: a.activa || !esSuper.value,
      titulo: !esSuper.value ? 'Solo el SUPER USUARIO cambia la actividad activa' : '', accion: () => activar(a),
    },
  ];
}

onMounted(cargar);
</script>

<template>
  <div class="actividades">
    <div class="cab">
      <p class="muted">
        Cada actividad tiene su propio plano, categorías, precios, puestos, ventas y credenciales.
        Solo una está activa: es la que ven el mapa, las ventas y los reportes.
      </p>
      <button class="btn btn-primario" @click="abrirCrear">＋ Nueva actividad</button>
    </div>

    <p v-if="cargando" class="vacio">Cargando…</p>
    <div v-else class="rejilla">
      <article v-for="a in lista" :key="a.id" class="card tarjeta" :class="{ activa: a.activa }">
        <header>
          <div>
            <h3>{{ a.nombre }}</h3>
            <span class="muted chico">{{ a.tipo || 'Actividad' }} · {{ a.anio }}</span>
          </div>
          <span v-if="a.activa" class="badge badge-ok">Activa</span>
          <MenuAcciones :acciones="acciones(a)" :etiqueta="`Acciones de ${a.nombre}`" />
        </header>
        <dl>
          <div><dt>Categorías</dt><dd>{{ a.categorias }}</dd></div>
          <div><dt>Puestos</dt><dd>{{ a.puestos }}</dd></div>
          <div><dt>Vendidos</dt><dd>{{ a.vendidos }}</dd></div>
          <div><dt>Ventas</dt><dd>{{ a.ventas }}</dd></div>
        </dl>
        <p class="muted chico">{{ a.conPlano ? 'Con plano propio' : 'Usa el plano de respaldo' }}
          · {{ a.publica ? 'Visible en la página pública' : 'Oculta al público' }}</p>
      </article>
    </div>

    <UiModal v-if="form.abierto" :titulo="form.editando ? `Editar ${form.editando.nombre}` : 'Nueva actividad'"
             @cerrar="!form.guardando && (form.abierto = false)">
      <div class="formulario">
        <label class="campo"><span>Nombre</span><input v-model="form.nombre" class="control" maxlength="120" placeholder="Expo Emprende 2026" /></label>
        <div class="dos">
          <label class="campo"><span>Tipo</span>
            <input v-model="form.tipo" class="control" list="tipos-actividad" maxlength="40" />
            <datalist id="tipos-actividad"><option v-for="t in TIPOS" :key="t" :value="t"></option></datalist>
          </label>
          <label class="campo"><span>Año</span><input v-model.number="form.anio" class="control" type="number" min="2000" max="2100" /></label>
        </div>
        <template v-if="!form.editando">
          <label class="campo"><span>Partir de</span>
            <select v-model="form.copiarDe" class="control">
              <option value="">Vacía (sin categorías ni puestos)</option>
              <option v-for="a in lista" :key="a.id" :value="String(a.id)">Copiar estructura de {{ a.nombre }}</option>
            </select>
          </label>
          <template v-if="form.copiarDe">
            <label class="check"><input v-model="form.copiarPlano" type="checkbox" /> Copiar la imagen del plano</label>
            <label class="check"><input v-model="form.copiarTextos" type="checkbox" /> Copiar los textos del plano</label>
            <p class="muted chico">Se copian categorías, opciones de precio y puestos con su posición, todos libres. No se copian ventas, vendedores asignados ni fotos.</p>
          </template>
          <p class="muted chico">La actividad se crea inactiva. Prepárala y actívala cuando corresponda.</p>
        </template>
        <h4 class="subtitulo">Página pública</h4>
        <div class="dos">
          <label class="campo"><span>Lugar</span><input v-model="form.lugar" class="control" maxlength="200" placeholder="Campo ferial, salón, dirección…" /></label>
          <label class="campo"><span>Fechas</span><input v-model="form.fechasTexto" class="control" maxlength="120" placeholder="12 al 15 de noviembre" /></label>
        </div>
        <label class="campo"><span>Descripción</span>
          <textarea v-model="form.descripcionPublica" class="control" rows="4" maxlength="3000" placeholder="Qué es la actividad, a quién está dirigida, horarios…"></textarea>
        </label>
        <label class="campo"><span>Contacto</span><input v-model="form.contacto" class="control" maxlength="120" placeholder="Celular o correo para consultas" /></label>
        <label class="check"><input v-model="form.publica" type="checkbox" /> Mostrar en la página pública cuando esté activa</label>
        <label class="check"><input v-model="form.recibeInteresados" type="checkbox" /> Recibir solicitudes de interesados en participar</label>
        <div v-if="form.editando" class="portada">
          <img v-if="form.portadaUrl" :src="urlApi(form.portadaUrl)" alt="Portada" />
          <div class="portada-acciones">
            <label class="btn btn-sm" :class="{ deshabilitado: form.subiendo }">
              {{ form.subiendo ? 'Subiendo…' : form.portadaUrl ? 'Cambiar portada' : 'Subir portada' }}
              <input type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="form.subiendo" @change="subirPortada" />
            </label>
            <button v-if="form.portadaUrl" type="button" class="btn btn-sm btn-fantasma" @click="quitarPortada">Quitar</button>
          </div>
        </div>
        <p v-else class="muted chico">La portada se sube después de crear la actividad, al editarla.</p>
      </div>
      <template #pie>
        <button class="btn btn-fantasma" :disabled="form.guardando" @click="form.abierto = false">Cancelar</button>
        <button class="btn btn-primario" :disabled="form.guardando" @click="guardar">{{ form.guardando ? 'Guardando…' : 'Guardar' }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.actividades { display: flex; flex-direction: column; gap: 1rem; }
.cab { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap; }
.cab p { margin: 0; max-width: 640px; }
.rejilla { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; }
.tarjeta { padding: 1rem; display: flex; flex-direction: column; gap: 0.7rem; }
.tarjeta.activa { border-color: var(--acento); box-shadow: 0 0 0 1px var(--acento); }
.tarjeta header { display: flex; align-items: flex-start; gap: 0.5rem; }
.tarjeta header > div { flex: 1; }
.tarjeta h3 { margin: 0; font-size: 1.05rem; }
.tarjeta dl { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.4rem; margin: 0; }
.tarjeta dt { font-size: 0.72rem; color: var(--muted); text-transform: uppercase; letter-spacing: 0.04em; }
.tarjeta dd { margin: 0; font-size: 1.1rem; font-weight: 800; font-variant-numeric: tabular-nums; }
.chico { font-size: 0.82rem; margin: 0; }
.formulario { display: flex; flex-direction: column; gap: 0.8rem; }
.dos { display: grid; grid-template-columns: 2fr 1fr; gap: 0.8rem; }
.check { display: flex; align-items: center; gap: 0.45rem; }
.subtitulo { margin: 0.6rem 0 0; font-size: 0.95rem; }
.portada { display: flex; flex-direction: column; gap: 0.5rem; }
.portada img { width: 100%; max-height: 180px; object-fit: cover; border-radius: var(--radio, 10px); border: 1px solid var(--border); }
.portada-acciones { display: flex; gap: 0.5rem; }
.deshabilitado { opacity: 0.5; pointer-events: none; }
</style>
