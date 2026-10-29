<script setup>
import { ref, computed, onBeforeUnmount, onMounted } from 'vue';
import { url as urlApi } from '../config.js';
import { apiFetch } from '../api';
import UiModal from '../components/UiModal.vue';

const codigo = ref('');
const buscando = ref(false);
const resultado = ref(null);
const error = ref('');

const modalRepetido = ref(false);
const datosRepetido = ref(null);

const sentido = ref('E');

const dentro = ref(null);

async function cuantosDentro() {
  try {
    const r = await apiFetch('/api/app/accesos/dentro');
    if (r.ok) dentro.value = (await r.json()).dentro;
  } catch { }
}

const video = ref(null);
const camara = ref(false);
const avisoCamara = ref('');
const linterna = ref(false);
let flujo = null;
let lector = null;
let decodificaJs = null;
let lienzo = null;
let animacion = 0;
let ultimoLeido = '';

const puedeLinterna = computed(() => {
  const p = flujo?.getVideoTracks?.()[0]?.getCapabilities?.();
  return !!p && 'torch' in p;
});

function codigoDe(texto) {
  const t = (texto || '').trim();
  const m = t.match(/FX[CA]-[0-9A-Z]+-[0-9A-Za-z_-]{12}/i);
  return m ? m[0].toUpperCase() : t.toUpperCase();
}

async function encender() {
  avisoCamara.value = '';
  limpiar();
  try {
    flujo = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
      audio: false,
    });
  } catch (e) {
    avisoCamara.value = e?.name === 'NotAllowedError'
      ? 'No se dio permiso para usar la cámara. Puedes teclear el código.'
      : 'No se pudo abrir la cámara. Puedes teclear el código.';
    return;
  }

  camara.value = true;
  await new Promise((r) => setTimeout(r));
  const v = video.value;
  if (!v) { apagar(); return; }
  v.srcObject = flujo;
  v.setAttribute('playsinline', '');
  try { await v.play(); } catch { }

  if ('BarcodeDetector' in window) {
    try {
      const formatos = await window.BarcodeDetector.getSupportedFormats();
      if (formatos.includes('qr_code')) lector = new window.BarcodeDetector({ formats: ['qr_code'] });
    } catch { lector = null; }
  }
  if (!lector && !decodificaJs) {
    decodificaJs = (await import('jsqr')).default;
  }
  animacion = requestAnimationFrame(mirar);
}

function apagar() {
  cancelAnimationFrame(animacion);
  animacion = 0;
  if (video.value) video.value.srcObject = null;
  flujo?.getTracks().forEach((t) => t.stop());
  flujo = null;
  lector = null;
  linterna.value = false;
  camara.value = false;
}

async function alternarLinterna() {
  const pista = flujo?.getVideoTracks?.()[0];
  if (!pista) return;
  try {
    await pista.applyConstraints({ advanced: [{ torch: !linterna.value }] });
    linterna.value = !linterna.value;
  } catch { }
}

let ultimoIntento = 0;

async function mirar(ahora) {
  if (!camara.value) return;
  const v = video.value;
  if (v && v.readyState >= 2 && ahora - ultimoIntento > 120) {
    ultimoIntento = ahora;
    try {
      let texto = null;
      if (lector) {
        const hallado = await lector.detect(v);
        texto = hallado[0]?.rawValue || null;
      } else if (decodificaJs) {
        lienzo ||= document.createElement('canvas');
        const escala = Math.min(1, 480 / (v.videoWidth || 480));
        lienzo.width = Math.round(v.videoWidth * escala);
        lienzo.height = Math.round(v.videoHeight * escala);
        const ctx = lienzo.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(v, 0, 0, lienzo.width, lienzo.height);
        const datos = ctx.getImageData(0, 0, lienzo.width, lienzo.height);
        texto = decodificaJs(datos.data, datos.width, datos.height,
                             { inversionAttempts: 'dontInvert' })?.data || null;
      }
      if (texto) {
        const c = codigoDe(texto);
        if (c !== ultimoLeido) {
          ultimoLeido = c;
          leido(c);
          return;
        }
      }
    } catch { }
  }
  animacion = requestAnimationFrame(mirar);
}

function leido(c) {
  try { navigator.vibrate?.(120); } catch { }
  apagar();
  codigo.value = c;
  verificar();
}

onBeforeUnmount(apagar);
onMounted(() => {
  modalRepetido.value = false;
  datosRepetido.value = null;
  cuantosDentro();
});

async function verificar() {
  const c = codigoDe(codigo.value);
  if (!c || buscando.value) return;
  buscando.value = true;
  resultado.value = null;
  error.value = '';
  try {
    const r = await apiFetch('/api/app/accesos', {
      method: 'POST',
      body: JSON.stringify({ codigo: c, sentido: sentido.value }),
    });
    const d = await r.json().catch(() => ({}));
    if (r.ok && d.ok) {
      resultado.value = d;
      cuantosDentro();
      if (d.repetido) {
        datosRepetido.value = d;
        modalRepetido.value = true;
      }
    } else {
      error.value = d.mensaje || 'Esta credencial no es válida';
    }
  } catch (e) {
    error.value = e.message === 'Sesion expirada'
      ? 'Tu sesión caducó. Vuelve a entrar para seguir controlando la puerta.'
      : 'No se pudo verificar. Revisa la conexión.';
  } finally {
    buscando.value = false;
  }
}

function limpiar() {
  codigo.value = '';
  resultado.value = null;
  error.value = '';
  ultimoLeido = '';
  modalRepetido.value = false;
  datosRepetido.value = null;
}

function siguiente() {
  limpiar();
  encender();
}

const tituloRepetido = computed(() =>
  datosRepetido.value?.sentido === 'E'
    ? '⚠️ Entrada repetida — Verificar'
    : '⚠️ Salida repetida — Verificar'
);

const mensajeRepetido = computed(() =>
  datosRepetido.value?.sentido === 'E'
    ? 'Esta persona YA FIGURABA COMO DENTRO. Se registró una NUEVA ENTRADA sin haber salido.'
    : 'Esta persona YA FIGURABA COMO FUERA. Se registró una NUEVA SALIDA sin haber entrado.'
);
</script>

<template>
  <div class="escaner">
    <section class="modo card" :class="sentido === 'E' ? 'es-entrada' : 'es-salida'">
      <div class="modo-resumen">
        <strong>{{ sentido === 'E' ? '⬇️ Registrando entradas' : '⬆️ Registrando salidas' }}</strong>
        <details class="opciones-sentido">
          <summary>Cambiar tipo de registro</summary>
          <div class="botones-modo" role="group" aria-label="Tipo de registro">
            <button type="button" class="modo-btn" :class="{ activo: sentido === 'E' }"
                    :aria-pressed="sentido === 'E'" @click="sentido = 'E'">Entrada</button>
            <button type="button" class="modo-btn" :class="{ activo: sentido === 'S' }"
                    :aria-pressed="sentido === 'S'" @click="sentido = 'S'">Salida</button>
          </div>
        </details>
      </div>
      <p class="dentro" v-if="dentro !== null">
        <strong>{{ dentro }}</strong> persona{{ dentro === 1 ? '' : 's' }} dentro ahora
      </p>
    </section>

    <section class="entrada card">
      <h2>{{ sentido === 'E' ? 'Registrar entrada' : 'Registrar salida' }}</h2>

      <div v-if="camara" class="visor">
        <video ref="video" muted playsinline></video>
        <div class="mira" aria-hidden="true"></div>
        <div class="controles">
          <button v-if="puedeLinterna" class="btn btn-fantasma btn-sm"
                  :class="{ activo: linterna }" @click="alternarLinterna">
            {{ linterna ? '🔦 Apagar luz' : '🔦 Luz' }}
          </button>
          <button class="btn btn-fantasma btn-sm" @click="apagar">Apagar cámara</button>
        </div>
      </div>
      <button v-else class="btn btn-primario btn-camara" @click="encender">
        📷 Escanear con la cámara
      </button>
      <p v-if="avisoCamara" class="aviso">{{ avisoCamara }}</p>

      <p class="ayuda">
        Si el QR no se deja leer, escribe el código impreso debajo.
      </p>
      <form class="fila" @submit.prevent="verificar">
        <input v-model="codigo" class="control codigo" placeholder="FXC-1A-XXXXXXXXXXXX o FXA-1A-XXXXXXXXXXXX"
               autocapitalize="characters" autocomplete="off" spellcheck="false" />
        <button class="btn btn-primario" :disabled="buscando || !codigo.trim()">
          {{ buscando ? 'Verificando…' : 'Verificar' }}
        </button>
      </form>
    </section>

    <section v-if="error" class="resultado invalido card">
      <span class="marca">✕</span>
      <div>
        <h3>Credencial no válida</h3>
        <p>{{ error }}</p>
        <p class="pie">No permitir el ingreso con este código.</p>
        <button class="btn btn-sm" @click="siguiente">📷 Escanear otra</button>
      </div>
    </section>

    <section v-else-if="resultado" class="resultado valido card">
      <header>
        <span class="marca">✓</span>
        <h3>{{ resultado.sentido === 'E' ? 'Entrada registrada' : 'Salida registrada' }}</h3>
      </header>

      <div class="persona">
        <img v-if="resultado.fotoUrl" :src="urlApi(resultado.fotoUrl)" :alt="resultado.nombre" class="foto" />
        <div v-else class="foto sinfoto">Sin foto</div>
        <div>
          <strong>{{ resultado.nombre }}</strong>
          <p class="ci">C.I. {{ resultado.ci || '—' }}</p>
          <p v-if="resultado.tipo === 'apoyo'" class="muted">
            {{ resultado.dependencia || '—' }} · {{ resultado.rol || '—' }}
          </p>
          <p v-else class="muted">{{ resultado.entidad }}</p>
        </div>
      </div>
      <dl class="datos">
        <template v-if="resultado.tipo === 'apoyo'">
          <div><dt>Tarea</dt><dd>{{ resultado.tarea || '—' }}</dd></div>
        </template>
        <template v-else>
          <div><dt>Categoría</dt><dd>{{ resultado.categoria || '—' }}</dd></div>
          <div><dt>Caseta</dt><dd class="casetas">{{ resultado.casetas || '—' }}</dd></div>
        </template>
        <div><dt>Entradas</dt><dd>{{ resultado.entradas }}</dd></div>
        <div><dt>Salidas</dt><dd>{{ resultado.salidas }}</dd></div>
      </dl>

      <details v-if="resultado.historial?.length" class="movimientos">
        <summary>Últimos movimientos</summary>
        <ul>
          <li v-for="(h, i) in resultado.historial" :key="i">
            <span class="badge" :class="h.sentido === 'E' ? 'badge-ok' : 'badge-muted'">
              {{ h.sentido === 'E' ? 'entrada' : 'salida' }}
            </span>
            <span class="cuando">{{ (h.cuando || '').slice(0, 16).replace('T', ' ') }}</span>
            <span class="muted">{{ h.usuario || '—' }}</span>
          </li>
        </ul>
      </details>
      <div class="fila">
        <button class="btn btn-primario" @click="siguiente">📷 Escanear la siguiente</button>
        <button class="btn btn-fantasma" @click="limpiar">Limpiar</button>
      </div>
    </section>

    <UiModal v-if="modalRepetido" :titulo="tituloRepetido" :ancho="480" @cerrar="modalRepetido = false">
      <div class="modal-repetido">
        <div class="icono-advertencia">⚠️</div>
        <h3>{{ mensajeRepetido }}</h3>
        <div class="persona-modal">
          <img v-if="datosRepetido?.fotoUrl" :src="urlApi(datosRepetido.fotoUrl)" :alt="datosRepetido.nombre" class="foto" />
          <div v-else class="foto sinfoto">Sin foto</div>
          <div>
            <strong>{{ datosRepetido?.nombre }}</strong>
            <p class="ci">C.I. {{ datosRepetido?.ci || '—' }}</p>
            <p class="muted">{{ datosRepetido?.entidad }}</p>
          </div>
        </div>
        <dl class="datos-modal">
          <div><dt>Categoría</dt><dd>{{ datosRepetido?.categoria || '—' }}</dd></div>
          <div><dt>Caseta(s)</dt><dd class="casetas">{{ datosRepetido?.casetas || '—' }}</dd></div>
          <div><dt>Entradas totales</dt><dd>{{ datosRepetido?.entradas }}</dd></div>
          <div><dt>Salidas totales</dt><dd>{{ datosRepetido?.salidas }}</dd></div>
        </dl>
        <p class="ayuda-modal">Verifique la identidad de la persona antes de permitir el paso.</p>
      </div>
      <template #pie>
        <button class="btn btn-primario" @click="modalRepetido = false">Entendido, continuar</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.escaner { display: flex; flex-direction: column; gap: 1rem; max-width: 560px; margin: 0 auto; }

.modo { padding: 0.8rem; display: flex; flex-direction: column; gap: 0.6rem; border-top: 5px solid var(--border); }
.modo.es-entrada { border-top-color: var(--ok); }
.modo.es-salida { border-top-color: var(--tramite); }
.modo-resumen { display: flex; align-items: center; justify-content: space-between; gap: 0.7rem; flex-wrap: wrap; }
.opciones-sentido { position: relative; }
.opciones-sentido summary { cursor: pointer; color: var(--acento); font-size: 0.86rem; }
.opciones-sentido[open] { width: 100%; }
.opciones-sentido[open] summary { margin-bottom: 0.6rem; }
.botones-modo { display: grid; grid-template-columns: 1fr 1fr; gap: 0.6rem; }
.modo-btn {
  font: inherit; cursor: pointer; padding: 0.55rem 0.6rem; border-radius: var(--radio-sm);
  border: 2px solid var(--border); background: var(--panel); color: var(--texto);
  transition: border-color 0.15s ease, background 0.15s ease;
}
.es-entrada .modo-btn.activo {
  border-color: var(--ok); background: color-mix(in srgb, var(--ok) 14%, var(--panel));
}
.es-salida .modo-btn.activo {
  border-color: var(--tramite); background: color-mix(in srgb, var(--tramite) 14%, var(--panel));
}
.dentro { margin: 0; text-align: center; font-size: 0.9rem; color: var(--muted); }
.dentro strong { font-size: 1.15rem; color: var(--texto); font-variant-numeric: tabular-nums; }

.repetido {
  margin: 0; padding: 0.5rem 0.7rem; border-radius: var(--radio-sm);
  background: color-mix(in srgb, var(--tramite) 14%, transparent);
  color: var(--tramite); font-size: 0.88rem; font-weight: 600;
}
.movimientos { font-size: 0.85rem; }
.movimientos summary { cursor: pointer; color: var(--muted); font-weight: 600; }
.movimientos ul { list-style: none; margin: 0.5rem 0 0; padding: 0; display: flex; flex-direction: column; gap: 0.35rem; }
.movimientos li { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.movimientos .cuando { font-variant-numeric: tabular-nums; }
.entrada { padding: 1.1rem; display: flex; flex-direction: column; gap: 0.6rem; }
.entrada h2 { margin: 0; font-size: 1.1rem; }
.ayuda { margin: 0; font-size: 0.86rem; color: var(--muted); line-height: 1.5; }
.fila { display: flex; gap: 0.6rem; }
.codigo { flex: 1; font-family: ui-monospace, monospace; letter-spacing: 0.04em; text-transform: uppercase; }
.fila .btn { min-height: 48px; }
.btn-camara { min-height: 52px; font-size: 1rem; }
.aviso { margin: 0; font-size: 0.86rem; color: var(--tramite); line-height: 1.45; }

.visor { position: relative; border-radius: var(--radio-sm); overflow: hidden; background: #000; }
.visor video { display: block; width: 100%; max-height: 60vh; object-fit: cover; }
.mira {
  position: absolute; inset: 50% auto auto 50%; transform: translate(-50%, -50%);
  width: min(62%, 240px); aspect-ratio: 1; border: 3px solid rgba(255, 255, 255, 0.9);
  border-radius: 14px; box-shadow: 0 0 0 100vmax rgba(0, 0, 0, 0.35);
}
.controles {
  position: absolute; left: 0; right: 0; bottom: 0; display: flex; gap: 0.5rem;
  justify-content: center; padding: 0.6rem;
}
.controles .btn { background: rgba(15, 23, 42, 0.72); color: #fff; border-color: transparent; }
.controles .btn.activo { background: var(--acento); }

.resultado { padding: 1.1rem; display: flex; flex-direction: column; gap: 0.9rem; }
.resultado.valido { border-top: 6px solid var(--ok); }
.resultado.invalido { border-top: 6px solid var(--danger); flex-direction: row; align-items: flex-start; gap: 0.9rem; }
.marca {
  display: grid; place-items: center; width: 42px; height: 42px; flex: none;
  border-radius: 50%; font-size: 1.4rem; font-weight: 800; color: #fff;
}
.valido .marca { background: var(--ok); }
.invalido .marca { background: var(--danger); }
.resultado header { display: flex; align-items: center; gap: 0.7rem; }
.resultado h3 { margin: 0; font-size: 1.05rem; }
.invalido h3 { color: var(--danger); }
.invalido p { margin: 0.25rem 0 0; font-size: 0.9rem; }
.invalido .pie { color: var(--muted); }

.persona { display: flex; gap: 0.9rem; align-items: center; }
.foto { width: 84px; height: 84px; flex: none; object-fit: cover; border-radius: var(--radio-sm); border: 1px solid var(--border); }
.foto.sinfoto { display: grid; place-items: center; background: var(--panel-2); color: var(--muted); font-size: 0.75rem; }
.persona strong { font-size: 1.1rem; }
.ci { margin: 0.2rem 0; font-variant-numeric: tabular-nums; }
.persona p { margin: 0; font-size: 0.88rem; }

.datos { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; margin: 0; }
.datos dt { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.datos dd { margin: 0.15rem 0 0; font-size: 0.95rem; }
.datos .casetas { font-size: 1.4rem; font-weight: 800; color: var(--acento); font-variant-numeric: tabular-nums; }

.modal-repetido { display: flex; flex-direction: column; gap: 1rem; }
.icono-advertencia { font-size: 3rem; text-align: center; line-height: 1; }
.modal-repetido h3 { margin: 0; text-align: center; font-size: 1.1rem; color: var(--tramite); }
.persona-modal { display: flex; gap: 0.9rem; align-items: center; padding: 0.8rem; background: var(--panel-2); border-radius: var(--radio-sm); }
.persona-modal .foto { width: 72px; height: 72px; flex: none; object-fit: cover; border-radius: var(--radio-sm); border: 1px solid var(--border); }
.persona-modal .foto.sinfoto { display: grid; place-items: center; background: var(--panel); color: var(--muted); font-size: 0.7rem; width: 72px; height: 72px; }
.persona-modal strong { font-size: 1.05rem; }
.persona-modal .ci { margin: 0.15rem 0; font-variant-numeric: tabular-nums; font-size: 0.85rem; }
.persona-modal .muted { margin: 0; font-size: 0.8rem; }
.datos-modal { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; margin: 0; padding: 0.5rem; background: var(--panel-2); border-radius: var(--radio-sm); }
.datos-modal dt { font-size: 0.65rem; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); font-weight: 700; }
.datos-modal dd { margin: 0.1rem 0 0; font-size: 0.85rem; }
.datos-modal .casetas { font-size: 1.2rem; font-weight: 800; color: var(--acento); font-variant-numeric: tabular-nums; }
.ayuda-modal { margin: 0; font-size: 0.8rem; color: var(--muted); text-align: center; font-style: italic; }
</style>
