<script setup>
import { ref, reactive, computed, onMounted, onActivated, onUnmounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { aviso, confirmar } from '../ui/alerta';
import { useAnunciosStore } from '../stores/anuncios';
import PromoTarjeta from '../components/PromoTarjeta.vue';
import { url as urlApi } from '../config.js';
import '../publico.css';

const tienda = useAnunciosStore();

const FORMATOS = [
  { v: 'DESTACADO', icono: '🖼️', txt: 'Destacado', desc: 'Imagen grande que rota en el inicio y en la actividad.' },
  { v: 'COMBO', icono: '🍔', txt: 'Combo o promoción', desc: 'Precio especial, qué incluye y botón para pedirlo.' },
  { v: 'EMERGENTE', icono: '💬', txt: 'Aviso emergente', desc: 'Se abre una vez al entrar. Ideal para lanzamientos.' },
  { v: 'AVISO', icono: '📢', txt: 'Aviso rápido', desc: 'Mensaje corto para el equipo o para los visitantes.' },
];
const NOMBRE_FORMATO = Object.fromEntries(FORMATOS.map((f) => [f.v, `${f.icono} ${f.txt}`]));

const NIVELES = [
  { v: 'info', txt: 'ℹ️ Información' },
  { v: 'aviso', txt: '⚠️ Aviso' },
  { v: 'urgente', txt: '🚨 Urgente' },
];
const DESTINOS = [
  { v: 'INTERNO', txt: '👥 Equipo', desc: 'Sale en la pantalla de quienes usan el sistema.' },
  { v: 'PUBLICO', txt: '🌐 Visitantes', desc: 'Sale flotando en la página pública.' },
  { v: 'AMBOS', txt: '📣 Todos', desc: 'Equipo y visitantes.' },
];
const ETIQUETA_DESTINO = { INTERNO: 'Equipo', PUBLICO: 'Página pública', AMBOS: 'Equipo y página pública' };
const DURACIONES = [
  { v: 120, txt: '2 horas' },
  { v: 1440, txt: '1 día' },
  { v: 4320, txt: '3 días' },
  { v: 10080, txt: '1 semana' },
  { v: 43200, txt: '1 mes' },
  { v: null, txt: 'Sin límite (hasta retirarlo)' },
  { v: 'fecha', txt: 'Hasta una fecha y hora…' },
];
const BOTONES = [
  { v: 'NINGUNO', txt: 'Sin botón' },
  { v: 'ACTIVIDAD', txt: 'Ir a la actividad', texto: 'Ver actividad' },
  { v: 'WHATSAPP', txt: 'Escribir por WhatsApp', texto: 'Lo quiero' },
  { v: 'ENLACE', txt: 'Abrir un enlace', texto: 'Ver más' },
];
const PESTANAS = [['curso', 'En curso'], ['programados', 'Programados'], ['terminados', 'Terminados']];

const historial = ref([]);
const actividades = ref([]);
const cargando = ref(true);
const guardando = ref(false);
const subiendo = ref(false);
const editando = ref(null);
const pestana = ref('curso');
let urlLocal = null;

const vacio = () => ({
  formato: 'DESTACADO', titulo: '', mensaje: '', imagen: null, imagenLocal: null, edicionId: '',
  precio: '', precioAntes: '', incluye: '', botonAccion: 'NINGUNO', botonTexto: '', enlace: '',
  destino: 'PUBLICO', nivel: 'info', programar: false, desde: '', minutos: 10080, hasta: '',
});
const form = reactive(vacio());

const esAviso = computed(() => form.formato === 'AVISO');
const ahoraLocal = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
const local = (t) => (t ? String(t).slice(0, 16) : '');
const cuando = (t) => {
  if (!t) return '';
  try { return new Date(t).toLocaleString('es-BO', { dateStyle: 'medium', timeStyle: 'short' }); } catch { return ''; }
};
const vencido = (a) => !!a.vigenteHasta && new Date(a.vigenteHasta) <= new Date();
const programado = (a) => !!a.vigenteDesde && new Date(a.vigenteDesde) > new Date();
const grupos = computed(() => ({
  curso: historial.value.filter((a) => a.activo && !vencido(a) && !programado(a)),
  programados: historial.value.filter((a) => a.activo && !vencido(a) && programado(a)),
  terminados: historial.value.filter((a) => !a.activo || vencido(a)),
}));
const actividadElegida = computed(() => actividades.value.find((x) => String(x.id) === String(form.edicionId)) || null);
const botonesDisponibles = computed(() => BOTONES.filter((b) => b.v !== 'ACTIVIDAD' || form.edicionId));
const rebaja = computed(() => {
  const p = Number(form.precio);
  const a = Number(form.precioAntes);
  return p > 0 && a > p ? Math.round((1 - p / a) * 100) : 0;
});
const dondeTexto = computed(() => {
  const act = actividadElegida.value;
  if (form.formato === 'EMERGENTE') return act ? `Se abre al entrar a la página de «${act.nombre}».` : 'Se abre al entrar a la página de inicio.';
  return act ? `Rota en el carrusel del inicio y en la página de «${act.nombre}».` : 'Rota en el carrusel de la página de inicio.';
});
const previa = computed(() => ({
  id: 0, formato: form.formato, titulo: form.titulo.trim() || tituloEjemplo(), mensaje: form.mensaje.trim() || null,
  imagenUrl: form.imagenLocal || (form.imagen ? `/files/${form.imagen}` : null),
  edicionId: form.edicionId || null, edicionNombre: actividadElegida.value?.nombre || null,
  precio: form.formato === 'COMBO' && Number(form.precio) ? Number(form.precio) : null,
  precioAntes: form.formato === 'COMBO' && Number(form.precioAntes) ? Number(form.precioAntes) : null,
  incluye: form.formato === 'COMBO' ? form.incluye.split('\n').map((x) => x.trim()).filter(Boolean) : [],
  botonAccion: form.botonAccion, botonTexto: form.botonTexto.trim() || textoBoton(form.botonAccion),
  enlace: form.enlace || 'https://ejemplo.com', contacto: '70000000',
}));
const listo = computed(() => (esAviso.value ? !!form.mensaje.trim() : !!form.titulo.trim())
  && (form.formato !== 'COMBO' || Number(form.precio) > 0)
  && (form.botonAccion !== 'ENLACE' || /^https?:\/\/\S{3,}$/i.test(form.enlace.trim()))
  && (form.minutos !== 'fecha' || !!form.hasta) && (!form.programar || !!form.desde));

function tituloEjemplo() {
  return { DESTACADO: 'Título del destacado', COMBO: 'Nombre del combo', EMERGENTE: '¡Novedad!', AVISO: '' }[form.formato];
}
function textoBoton(v) {
  return BOTONES.find((b) => b.v === v)?.texto || 'Ver más';
}

function elegirFormato(v) {
  if (editando.value) return;
  form.formato = v;
  if (v === 'AVISO') {
    form.destino = 'INTERNO';
    form.minutos = 120;
  } else {
    form.destino = 'PUBLICO';
    if (form.minutos === 120) form.minutos = 10080;
    if (v === 'COMBO' && form.botonAccion === 'NINGUNO') form.botonAccion = 'WHATSAPP';
  }
}

function soltarLocal() {
  if (urlLocal) URL.revokeObjectURL(urlLocal);
  urlLocal = null;
}

function limpiar() {
  soltarLocal();
  Object.assign(form, vacio());
  editando.value = null;
}

async function cargar() {
  cargando.value = true;
  try {
    const [h, a] = await Promise.all([apiFetch('/api/app/anuncios/historial'), apiFetch('/api/app/anuncios/actividades')]);
    if (h.ok) historial.value = await h.json();
    if (a.ok) actividades.value = await a.json();
  } catch (e) {
    toast(e.message, 'error');
  } finally {
    cargando.value = false;
  }
}

async function subirImagen(e) {
  const f = e.target.files?.[0];
  e.target.value = '';
  if (!f) return;
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(f.type)) { toast('Sube una imagen PNG, JPG o WEBP', 'error'); return; }
  if (f.size > 6 * 1024 * 1024) { toast('La imagen no puede pasar de 6 MB', 'error'); return; }
  soltarLocal();
  urlLocal = URL.createObjectURL(f);
  form.imagenLocal = urlLocal;
  subiendo.value = true;
  try {
    const cuerpo = new FormData();
    cuerpo.append('archivo', f);
    const r = await apiFetch('/api/app/anuncios/imagen', { method: 'POST', body: cuerpo });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.mensaje || 'No se pudo subir la imagen');
    form.imagen = d.imagen;
  } catch (err) {
    form.imagenLocal = null;
    soltarLocal();
    toast(err.message, 'error');
  } finally {
    subiendo.value = false;
  }
}

function quitarImagen() {
  soltarLocal();
  form.imagen = null;
  form.imagenLocal = null;
}

function cuerpoPeticion() {
  const num = (v) => (v === '' || v == null ? null : Number(v));
  return {
    formato: form.formato,
    titulo: form.titulo.trim() || null,
    mensaje: form.mensaje.trim() || null,
    nivel: form.nivel,
    destino: esAviso.value ? form.destino : 'PUBLICO',
    imagen: esAviso.value ? null : form.imagen,
    edicionId: esAviso.value || !form.edicionId ? null : Number(form.edicionId),
    precio: form.formato === 'COMBO' ? num(form.precio) : null,
    precioAntes: form.formato === 'COMBO' ? num(form.precioAntes) : null,
    incluye: form.formato === 'COMBO' ? form.incluye.trim() || null : null,
    botonAccion: form.botonAccion,
    botonTexto: form.botonAccion === 'NINGUNO' ? null : form.botonTexto.trim() || textoBoton(form.botonAccion),
    enlace: form.botonAccion === 'ENLACE' ? form.enlace.trim() : null,
    desde: form.programar ? form.desde : null,
    minutos: form.minutos === 'fecha' ? null : form.minutos,
    hasta: form.minutos === 'fecha' ? form.hasta : null,
  };
}

async function guardar() {
  if (!listo.value || guardando.value || subiendo.value) return;
  if (!editando.value) {
    const quien = esAviso.value
      ? { INTERNO: 'la pantalla de todo el equipo', PUBLICO: 'la página pública', AMBOS: 'la página pública y la pantalla del equipo' }[form.destino]
      : 'la página pública';
    const si = await confirmar(`Va a salir en ${quien} ${form.programar ? 'desde la fecha elegida' : 'ahora mismo'}.`,
      { titulo: '¿Publicar?', aceptar: 'Publicar', tipo: 'info' });
    if (!si) return;
  }
  guardando.value = true;
  try {
    const r = await apiFetch(editando.value ? `/api/app/anuncios/${editando.value}` : '/api/app/anuncios', {
      method: editando.value ? 'PUT' : 'POST', body: JSON.stringify(cuerpoPeticion()),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { await aviso(d.mensaje || 'No se pudo guardar', 'error', 0); return; }
    if (d.destino !== 'PUBLICO' && !programado(d)) tienda.aplicar(d);
    toast(editando.value ? 'Cambios guardados' : 'Publicado', 'ok');
    limpiar();
    await cargar();
  } catch (e) {
    await aviso(e.message, 'error', 0);
  } finally {
    guardando.value = false;
  }
}

function cargarEnFormulario(a, copia) {
  soltarLocal();
  const futuro = programado(a);
  Object.assign(form, vacio(), {
    formato: a.formato || 'AVISO', titulo: a.titulo || '', mensaje: a.mensaje || '',
    imagen: a.imagenUrl ? a.imagenUrl.replace(/^\/files\//, '') : null,
    edicionId: a.edicionId ? String(a.edicionId) : '',
    precio: a.precio ?? '', precioAntes: a.precioAntes ?? '', incluye: (a.incluye || []).join('\n'),
    botonAccion: a.botonAccion || 'NINGUNO', botonTexto: a.botonTexto || '', enlace: a.enlace || '',
    destino: a.destino || 'PUBLICO', nivel: a.nivel || 'info',
    programar: !copia && futuro, desde: !copia && futuro ? local(a.vigenteDesde) : '',
    minutos: !copia && a.vigenteHasta && !vencido(a) ? 'fecha' : copia ? 10080 : null,
    hasta: !copia && a.vigenteHasta && !vencido(a) ? local(a.vigenteHasta) : '',
  });
  editando.value = copia ? null : a.id;
  document.querySelector('.anuncios .editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function retirar(a) {
  const si = await confirmar('Dejará de verse para todos de inmediato. Sus vistas y clics se conservan.',
    { titulo: '¿Retirar la publicación?', aceptar: 'Retirar' });
  if (!si) return;
  try {
    const r = await apiFetch(`/api/app/anuncios/${a.id}`, { method: 'DELETE' });
    if (!r.ok) { toast('No se pudo retirar', 'error'); return; }
    tienda.aplicar({ id: a.id, activo: false });
    if (editando.value === a.id) limpiar();
    toast('Publicación retirada', 'ok');
    await cargar();
  } catch (e) {
    toast(e.message, 'error');
  }
}

const tasa = (a) => (a.vistas ? `${Math.round((a.clics / a.vistas) * 100)}%` : '—');
const miniatura = (a) => (a.imagenUrl ? { backgroundImage: `url(${urlApi(a.imagenUrl)})` } : null);

onMounted(cargar);
onActivated(cargar);
onUnmounted(soltarLocal);
</script>

<template>
  <div class="anuncios">
    <section class="card elegir">
      <h2>{{ editando ? 'Editar publicación' : '¿Qué quieres publicar?' }}</h2>
      <div class="formatos">
        <button v-for="f in FORMATOS" :key="f.v" type="button" class="formato"
                :class="{ marcado: form.formato === f.v, bloqueado: editando && form.formato !== f.v }"
                :disabled="!!editando && form.formato !== f.v" @click="elegirFormato(f.v)">
          <span class="icono">{{ f.icono }}</span>
          <strong>{{ f.txt }}</strong>
          <span class="muted">{{ f.desc }}</span>
        </button>
      </div>
    </section>

    <section class="editor">
      <form class="card formulario" @submit.prevent="guardar">
        <template v-if="!esAviso">
          <label class="campo">
            <span>¿Dónde se muestra?</span>
            <select v-model="form.edicionId" class="control">
              <option value="">🏠 Página de inicio</option>
              <option v-for="x in actividades" :key="x.id" :value="String(x.id)">🗓️ {{ x.nombre }}{{ x.visible ? '' : ' (no publicada aún)' }}</option>
            </select>
            <small class="muted">{{ dondeTexto }}</small>
          </label>

          <label class="campo">
            <span>Título *</span>
            <input v-model="form.titulo" class="control" maxlength="120" :placeholder="form.formato === 'COMBO' ? 'Ej.: Combo Familiar' : 'Ej.: ¡Ya están a la venta las mesas VIP!'" />
          </label>
          <label class="campo">
            <span>Texto <span class="muted">(opcional)</span></span>
            <textarea v-model="form.mensaje" class="control" rows="2" maxlength="1000" placeholder="Una o dos frases que inviten a participar."></textarea>
          </label>

          <div class="campo">
            <span>Imagen <span class="muted">{{ form.formato === 'EMERGENTE' ? '(ideal 1200 × 900)' : '(ideal horizontal, 1600 × 700)' }}</span></span>
            <div class="imagen">
              <div class="mini" :style="form.imagenLocal || form.imagen ? { backgroundImage: `url(${form.imagenLocal || urlApi('/files/' + form.imagen)})` } : null">
                <span v-if="!form.imagenLocal && !form.imagen">🖼️</span>
              </div>
              <label class="btn btn-sm">
                {{ subiendo ? 'Subiendo…' : form.imagen ? 'Cambiar imagen' : 'Subir imagen' }}
                <input type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="subiendo" @change="subirImagen" />
              </label>
              <button v-if="form.imagen || form.imagenLocal" type="button" class="btn btn-sm btn-fantasma" @click="quitarImagen">Quitar</button>
            </div>
          </div>

          <template v-if="form.formato === 'COMBO'">
            <div class="fila2">
              <label class="campo">
                <span>Precio del combo (Bs) *</span>
                <input v-model="form.precio" type="number" min="0" step="0.5" class="control" inputmode="decimal" />
              </label>
              <label class="campo">
                <span>Precio normal <span class="muted">(opcional, se ve tachado)</span></span>
                <input v-model="form.precioAntes" type="number" min="0" step="0.5" class="control" inputmode="decimal" />
                <small v-if="rebaja" class="ahorro">Ahorran {{ rebaja }}%</small>
              </label>
            </div>
            <label class="campo">
              <span>¿Qué incluye? <span class="muted">(uno por línea)</span></span>
              <textarea v-model="form.incluye" class="control" rows="3" maxlength="1000" placeholder="Mesa para 6&#10;2 jarras de refresco&#10;Estacionamiento"></textarea>
            </label>
          </template>

          <div class="fila2">
            <label class="campo">
              <span>Botón</span>
              <select v-model="form.botonAccion" class="control">
                <option v-for="b in botonesDisponibles" :key="b.v" :value="b.v">{{ b.txt }}</option>
              </select>
              <small v-if="form.botonAccion === 'WHATSAPP'" class="muted">Abre WhatsApp con el celular de contacto de la actividad (o el general del sistema) y un mensaje listo. Si no hay ninguno configurado, el botón no aparece.</small>
            </label>
            <label v-if="form.botonAccion !== 'NINGUNO'" class="campo">
              <span>Texto del botón</span>
              <input v-model="form.botonTexto" class="control" maxlength="40" :placeholder="textoBoton(form.botonAccion)" />
            </label>
          </div>
          <label v-if="form.botonAccion === 'ENLACE'" class="campo">
            <span>Enlace *</span>
            <input v-model="form.enlace" class="control" maxlength="300" placeholder="https://…" inputmode="url" />
          </label>
        </template>

        <template v-else>
          <label class="campo">
            <span>Título <span class="muted">(opcional)</span></span>
            <input v-model="form.titulo" class="control" maxlength="120" placeholder="Ej.: Cambio de horario" />
          </label>
          <label class="campo">
            <span>Mensaje *</span>
            <textarea v-model="form.mensaje" class="control" rows="3" maxlength="1000" placeholder="Ej.: Hoy abrimos a las 9:00."></textarea>
          </label>
          <div class="campo">
            <span>¿Quién lo ve?</span>
            <div class="opciones">
              <button v-for="d in DESTINOS" :key="d.v" type="button" class="opcion" :class="{ marcado: form.destino === d.v }" @click="form.destino = d.v">
                <strong>{{ d.txt }}</strong><span class="muted">{{ d.desc }}</span>
              </button>
            </div>
          </div>
          <div class="campo">
            <span>Importancia</span>
            <div class="opciones">
              <button v-for="n in NIVELES" :key="n.v" type="button" class="opcion" :class="[`n-${n.v}`, { marcado: form.nivel === n.v }]" @click="form.nivel = n.v">
                <strong>{{ n.txt }}</strong>
              </button>
            </div>
          </div>
          <div v-if="form.destino !== 'INTERNO'" class="fila2">
            <label class="campo">
              <span>Botón</span>
              <select v-model="form.botonAccion" class="control">
                <option value="NINGUNO">Sin botón</option>
                <option value="WHATSAPP">Escribir por WhatsApp</option>
                <option value="ENLACE">Abrir un enlace</option>
              </select>
            </label>
            <label v-if="form.botonAccion === 'ENLACE'" class="campo">
              <span>Enlace *</span>
              <input v-model="form.enlace" class="control" maxlength="300" placeholder="https://…" inputmode="url" />
            </label>
          </div>
        </template>

        <div class="fila2">
          <label class="campo">
            <span>Empieza</span>
            <select v-model="form.programar" class="control">
              <option :value="false">Ahora mismo</option>
              <option :value="true">En una fecha y hora…</option>
            </select>
            <input v-if="form.programar" v-model="form.desde" type="datetime-local" class="control" :min="ahoraLocal()" />
          </label>
          <label class="campo">
            <span>Se muestra</span>
            <select v-model="form.minutos" class="control">
              <option v-for="d in DURACIONES" :key="String(d.v)" :value="d.v">{{ d.txt }}</option>
            </select>
            <input v-if="form.minutos === 'fecha'" v-model="form.hasta" type="datetime-local" class="control" :min="form.desde || ahoraLocal()" />
          </label>
        </div>

        <div class="acciones">
          <button v-if="editando" type="button" class="btn" @click="limpiar">Cancelar</button>
          <button type="submit" class="btn btn-primario publicar" :disabled="!listo || guardando || subiendo">
            {{ guardando ? 'Guardando…' : editando ? '💾 Guardar cambios' : form.programar ? '🗓️ Programar' : '📣 Publicar' }}
          </button>
        </div>
      </form>

      <aside class="card vista">
        <h3>Así se verá</h3>
        <div class="pub previa">
          <template v-if="form.formato === 'DESTACADO' || form.formato === 'COMBO'">
            <small>En el carrusel</small>
            <div class="marco-slide"><PromoTarjeta :a="previa" variante="slide" previa /></div>
            <small>En el celular, sobre el plano</small>
            <div class="marco-compacta"><PromoTarjeta :a="previa" variante="compacta" previa /></div>
          </template>
          <template v-else-if="form.formato === 'EMERGENTE'">
            <small>Al entrar a la página</small>
            <div class="marco-emergente"><PromoTarjeta :a="previa" variante="emergente" previa /></div>
          </template>
          <template v-else>
            <small>{{ form.destino === 'INTERNO' ? 'En la pantalla del equipo' : 'Flotando en la página pública' }}</small>
            <div class="aviso-previo" :class="`n-${form.nivel}`">
              <span>{{ form.nivel === 'urgente' ? '🚨' : form.nivel === 'aviso' ? '📢' : '✨' }}</span>
              <div><b v-if="form.titulo">{{ form.titulo }}</b><p>{{ form.mensaje || 'Tu mensaje aparecerá aquí.' }}</p></div>
            </div>
          </template>
        </div>
      </aside>
    </section>

    <section class="listado">
      <div class="pestanas" role="tablist">
        <button v-for="[k, t] in PESTANAS" :key="k" type="button" role="tab" :aria-selected="pestana === k" :class="{ on: pestana === k }" @click="pestana = k">
          {{ t }} <span class="cuenta">{{ grupos[k].length }}</span>
        </button>
      </div>
      <p v-if="cargando" class="muted">Cargando…</p>
      <p v-else-if="!grupos[pestana].length" class="muted vacio card">
        {{ pestana === 'curso' ? 'No hay nada publicado ahora. Elige arriba qué quieres publicar.' : pestana === 'programados' ? 'No hay publicaciones programadas.' : 'Todavía no hay publicaciones terminadas.' }}
      </p>
      <ul v-else class="lista">
        <li v-for="a in grupos[pestana]" :key="a.id" class="card item" :class="{ apagado: pestana === 'terminados', editando: editando === a.id }">
          <div class="miniatura" :style="miniatura(a)"><span v-if="!a.imagenUrl">{{ NOMBRE_FORMATO[a.formato]?.slice(0, 2) }}</span></div>
          <div class="info">
            <div class="chips">
              <span class="chip">{{ NOMBRE_FORMATO[a.formato] }}</span>
              <span class="chip suave">{{ a.formato === 'AVISO' ? ETIQUETA_DESTINO[a.destino] : a.edicionNombre || 'Página de inicio' }}</span>
              <span v-if="a.precio" class="chip precio">Bs {{ a.precio }}</span>
            </div>
            <strong>{{ a.titulo || a.mensaje }}</strong>
            <span class="muted chico">
              {{ a.publicadoPor || '—' }} · {{ cuando(a.publicado) }}
              <template v-if="programado(a)"> · empieza {{ cuando(a.vigenteDesde) }}</template>
              <template v-if="a.vigenteHasta"> · {{ vencido(a) ? 'terminó' : 'hasta' }} {{ cuando(a.vigenteHasta) }}</template>
              <template v-else-if="a.activo"> · sin límite</template>
              <template v-if="!a.activo"> · retirado</template>
            </span>
          </div>
          <div v-if="a.destino !== 'INTERNO'" class="metricas" :title="'Vistas, clics en el botón y porcentaje de clics'">
            <span><b>{{ a.vistas }}</b> vistas</span>
            <span><b>{{ a.clics }}</b> clics</span>
            <span><b>{{ tasa(a) }}</b></span>
          </div>
          <div class="botones">
            <button v-if="pestana !== 'terminados'" type="button" class="btn btn-sm" @click="cargarEnFormulario(a, false)">Editar</button>
            <button type="button" class="btn btn-sm btn-fantasma" @click="cargarEnFormulario(a, true)">Duplicar</button>
            <button v-if="pestana !== 'terminados'" type="button" class="btn btn-sm btn-peligro" @click="retirar(a)">Retirar</button>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.anuncios { display: flex; flex-direction: column; gap: 1rem; }
.elegir { padding: 1rem; display: flex; flex-direction: column; gap: 0.75rem; }
.elegir h2, .vista h3 { margin: 0; font-size: 1.05rem; }
.formatos { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0.6rem; }
.formato {
  display: flex; flex-direction: column; gap: 0.2rem; text-align: left; padding: 0.8rem; border-radius: 12px; cursor: pointer;
  font: inherit; color: inherit; border: 2px solid var(--border); background: var(--panel); transition: border-color 0.15s ease, transform 0.15s ease;
}
.formato:hover:not(:disabled) { transform: translateY(-2px); border-color: var(--acento); }
.formato.marcado { border-color: var(--acento); background: color-mix(in srgb, var(--acento) 10%, var(--panel)); }
.formato.bloqueado { opacity: 0.4; cursor: default; }
.formato .icono { font-size: 1.6rem; }
.formato .muted { font-size: 0.8rem; line-height: 1.35; }

.editor { display: grid; grid-template-columns: minmax(0, 1fr) minmax(300px, 420px); gap: 1rem; align-items: start; scroll-margin-top: 1rem; }
.formulario { padding: 1rem; display: flex; flex-direction: column; gap: 0.85rem; }
.campo { display: flex; flex-direction: column; gap: 0.3rem; min-width: 0; }
.campo small { font-size: 0.78rem; }
.fila2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.75rem; }
.ahorro { color: var(--ok); font-weight: 700; }
.imagen { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.mini { width: 120px; height: 68px; border-radius: 10px; background: var(--panel-2) center / cover no-repeat; border: 1px dashed var(--border); display: grid; place-items: center; font-size: 1.4rem; }
.opciones { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 0.5rem; }
.opcion { display: flex; flex-direction: column; gap: 0.1rem; text-align: left; padding: 0.55rem 0.7rem; border-radius: 10px; cursor: pointer; font: inherit; color: inherit; border: 2px solid var(--border); background: var(--panel); }
.opcion .muted { font-size: 0.78rem; }
.opcion.marcado { border-color: var(--color-nivel, var(--acento)); background: color-mix(in srgb, var(--color-nivel, var(--acento)) 12%, transparent); }
.n-info { --color-nivel: var(--acento); }
.n-aviso { --color-nivel: var(--tramite, #d97706); }
.n-urgente { --color-nivel: var(--danger); }
.acciones { display: flex; justify-content: flex-end; gap: 0.5rem; }
.publicar { min-height: 46px; min-width: 180px; font-size: 1rem; }

.vista { padding: 1rem; position: sticky; top: 0.5rem; display: flex; flex-direction: column; gap: 0.6rem; }
.previa { min-height: 0; border-radius: 14px; padding: 14px; display: flex; flex-direction: column; gap: 8px; }
.previa small { color: var(--pub-suave); font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.06em; font-weight: 700; }
.marco-slide { height: 210px; }
.marco-compacta { height: 64px; }
.marco-emergente { max-width: 360px; width: 100%; align-self: center; }
.aviso-previo { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 10px; padding: 12px; border-radius: 14px; background: color-mix(in srgb, var(--pub-acento) 22%, var(--pub-panel)); border: 1px solid color-mix(in srgb, var(--pub-acento) 55%, transparent); }
.aviso-previo.n-aviso { background: color-mix(in srgb, var(--pub-alerta) 22%, var(--pub-panel)); }
.aviso-previo.n-urgente { background: color-mix(in srgb, var(--pub-error) 22%, var(--pub-panel)); }
.aviso-previo p { margin: 2px 0 0; color: var(--pub-suave); font-size: 0.86rem; overflow-wrap: anywhere; }

.listado { display: flex; flex-direction: column; gap: 0.6rem; }
.pestanas { display: flex; gap: 0.4rem; flex-wrap: wrap; }
.pestanas button { border: 1px solid var(--border); background: var(--panel); color: var(--muted); border-radius: 999px; padding: 0.4rem 0.9rem; font: inherit; font-weight: 600; cursor: pointer; }
.pestanas button.on { background: var(--acento); border-color: var(--acento); color: #fff; }
.cuenta { font-size: 0.75rem; opacity: 0.8; margin-left: 0.2rem; }
.vacio { padding: 1.5rem; text-align: center; }
.lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
.item { display: grid; grid-template-columns: 84px minmax(0, 1fr) auto auto; gap: 0.8rem; align-items: center; padding: 0.7rem; }
.item.apagado { opacity: 0.65; }
.item.editando { outline: 2px solid var(--acento); }
.miniatura { width: 84px; height: 56px; border-radius: 10px; background: var(--panel-2) center / cover no-repeat; display: grid; place-items: center; font-size: 1.4rem; }
.info { display: flex; flex-direction: column; gap: 0.2rem; min-width: 0; }
.info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chips { display: flex; gap: 0.3rem; flex-wrap: wrap; }
.chip { font-size: 0.72rem; font-weight: 700; padding: 0.05rem 0.5rem; border-radius: 999px; background: var(--acento-suave); color: var(--acento); }
.chip.suave { background: var(--panel-2); color: var(--muted); }
.chip.precio { background: color-mix(in srgb, var(--ok) 15%, transparent); color: var(--ok); }
.chico { font-size: 0.8rem; }
.metricas { display: flex; gap: 0.75rem; font-size: 0.8rem; color: var(--muted); white-space: nowrap; }
.metricas b { color: var(--text); font-size: 0.95rem; }
.botones { display: flex; gap: 0.35rem; flex-wrap: wrap; justify-content: flex-end; }

@media (max-width: 1100px) {
  .editor { grid-template-columns: minmax(0, 1fr); }
  .vista { position: static; order: -1; }
  .item { grid-template-columns: 64px minmax(0, 1fr); }
  .miniatura { width: 64px; height: 48px; }
  .metricas, .botones { grid-column: 1 / -1; justify-content: flex-start; }
}
@media (max-width: 760px) {
  .formatos { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .fila2, .opciones { grid-template-columns: minmax(0, 1fr); }
  .formato .muted { display: none; }
}
</style>
