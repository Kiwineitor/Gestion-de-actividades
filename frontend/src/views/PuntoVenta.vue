<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted, shallowRef } from 'vue';
import { useRouter } from 'vue-router';
import PanZoom from '../components/PanZoom.vue';
import RotuloPlano from '../components/RotuloPlano.vue';
import EntregaVenta from '../components/EntregaVenta.vue';
import CampoCelular from '../components/CampoCelular.vue';
import { apiFetch, jsonOError } from '../api';
import { url as urlApi } from '../config.js';
import { toast } from '../ui/toast';
import { confirmar } from '../ui/alerta';
import { descargarPdf } from '../ui/descargas';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { alCambiarPublico } from '../ui/canalPublico';
import { editorDeFeria } from '../ui/editorFeria';
import { estiloPin, anchoParaTocar, anchoParaLeer } from '../mapa';
import { nombreEspacio, mayuscula } from '../ui/espacios';
import { useAuthStore } from '../stores/auth';
import { aModulo } from '../modulos';

const auth = useAuthStore();
const router = useRouter();
const CLAVE = 'punto-venta.actividad';
const MEDIOS = [
  { id: 'EFECTIVO', icono: '💵', txt: 'Efectivo' },
  { id: 'QR', icono: '📱', txt: 'QR' },
  { id: 'TRANSFERENCIA', icono: '🏦', txt: 'Transferencia' },
];

const actividades = ref([]);
const edicion = ref(Number(sessionStorage.getItem(CLAVE)) || null);
const modo = ref('espacios');
const tienda = shallowRef(null);
const tipos = ref([]);
const cantidades = reactive({});
const ocupado = ref(new Set());
const enviando = ref(false);
const entrega = ref(null);
const ahora = ref(Date.now());
const form = reactive({ nombre: '', ci: '', celular: '', negocio: '', nit: '', rubro: '', medio: 'EFECTIVO', foto: null, acompanantes: [] });
let reloj = null;
let soltar = null;

const actual = computed(() => actividades.value.find((a) => a.id === edicion.value) || null);
const esPersona = computed(() => actual.value?.comprador === 'PERSONA');
const uno = computed(() => nombreEspacio(actual.value || {}));
const varios = computed(() => mayuscula(nombreEspacio(actual.value || {}, true)));
const puestos = computed(() => (tienda.value?.puestos.puestos || []).filter((p) => p.mapaX != null && p.mapaY != null));
const textos = computed(() => tienda.value?.textos.textos || []);
const plano = computed(() => tienda.value?.plano);
const fondo = computed(() => plano.value?.src || null);
const aspecto = computed(() => plano.value?.aspecto || 0.62);
const carrito = computed(() => puestos.value.filter((p) => p.estado === 'T' && p.reservadoPor === auth.id));
const entradasElegidas = computed(() => tipos.value.filter((t) => cantidades[t.id] > 0).map((t) => ({ ...t, cantidad: cantidades[t.id] })));
const totalEspacios = computed(() => carrito.value.reduce((s, p) => s + Number(p.precio || 0), 0));
const totalEntradas = computed(() => entradasElegidas.value.reduce((s, t) => s + t.cantidad * Number(t.precio || 0), 0));
const total = computed(() => (modo.value === 'espacios' ? totalEspacios.value : totalEntradas.value));
const hayAlgo = computed(() => (modo.value === 'espacios' ? carrito.value.length > 0 : entradasElegidas.value.length > 0));
const encuadre = computed(() => {
  const xs = puestos.value.map((p) => p.mapaX);
  const ys = puestos.value.map((p) => p.mapaY);
  if (!xs.length) return null;
  const m = 0.05;
  return { x0: Math.max(0, Math.min(...xs) - m), x1: Math.min(1, Math.max(...xs) + m), y0: Math.max(0, Math.min(...ys) - m), y1: Math.min(1, Math.max(...ys) + m) };
});
const libres = computed(() => puestos.value.filter((p) => p.estado === 'L').length);
const listo = computed(() => hayAlgo.value && form.nombre.trim().length >= 3 && form.ci.trim().length >= 4
  && String(form.celular).replace(/\D/g, '').length >= 7 && (esPersona.value || modo.value === 'entradas' || form.negocio.trim()));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;
const segundos = (p) => (p.reservaExpira ? Math.max(0, Math.floor((new Date(p.reservaExpira).getTime() - ahora.value) / 1000)) : 0);
const reloj2 = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

async function cargarActividades() {
  try {
    actividades.value = await jsonOError(await apiFetch('/api/app/punto-venta/actividades'), 'No se pudieron cargar las actividades');
    if (!actividades.value.some((a) => a.id === edicion.value)) edicion.value = actividades.value[0]?.id ?? null;
  } catch (e) { toast(e.message, 'error'); }
}

async function prepararActividad() {
  tienda.value?.cerrar();
  tienda.value = null;
  tipos.value = [];
  Object.keys(cantidades).forEach((k) => delete cantidades[k]);
  entrega.value = null;
  const a = actual.value;
  if (!a) return;
  try { sessionStorage.setItem(CLAVE, String(a.id)); } catch { }
  modo.value = a.vendeEspacios ? 'espacios' : 'entradas';
  if (a.vendeEspacios) {
    const t = editorDeFeria(a.id);
    tienda.value = t;
    await Promise.all([t.plano.asegurar(), t.puestos.asegurar(), t.textos.asegurar()]);
  }
  if (a.vendeEntradas) await cargarTipos();
}

async function cargarTipos() {
  if (!actual.value?.vendeEntradas) return;
  const r = await fetch(urlApi(`/api/publico/entradas/${actual.value.id}/disponibles`)).catch(() => null);
  if (r?.ok) tipos.value = await r.json();
}

async function tocar(p) {
  if (ocupado.value.has(p.id)) return;
  const mio = p.estado === 'T' && p.reservadoPor === auth.id;
  if (!mio && p.estado !== 'L') {
    toast(`${mayuscula(uno.value)} ${p.codigo}: ${p.estado === 'O' ? 'ya vendid' + (uno.value.endsWith('a') ? 'a' : 'o') : p.estado === 'X' ? 'bloqueado' : 'en proceso con otra persona'}`, 'info', 2200);
    return;
  }
  ocupado.value = new Set(ocupado.value).add(p.id);
  try {
    const r = await apiFetch(`/api/app/puestos/${p.id}/${mio ? 'liberar' : 'reservar'}`, { method: 'POST' });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) toast(d.mensaje || 'No se pudo', 'error');
  } finally {
    const s = new Set(ocupado.value);
    s.delete(p.id);
    ocupado.value = s;
  }
}

function cambiar(t, delta) {
  const actualN = Number(cantidades[t.id]) || 0;
  const tope = t.quedan == null ? 50 : t.quedan;
  cantidades[t.id] = Math.max(0, Math.min(tope, actualN + delta));
}

async function vaciar() {
  for (const p of carrito.value) await apiFetch(`/api/app/puestos/${p.id}/liberar`, { method: 'POST' });
  Object.keys(cantidades).forEach((k) => { cantidades[k] = 0; });
}

function limpiarForm() {
  Object.assign(form, { nombre: '', ci: '', celular: '', negocio: '', nit: '', rubro: '', medio: 'EFECTIVO', foto: null, acompanantes: [] });
}

async function cobrar() {
  if (!listo.value || enviando.value) return;
  const medio = MEDIOS.find((m) => m.id === form.medio);
  if (!(await confirmar(`Cobrar ${bs(total.value)} en ${medio.txt.toUpperCase()} a ${form.nombre.trim()}.`, { titulo: '¿Confirmar la venta?', aceptar: `${medio.icono} Cobrar ${bs(total.value)}` }))) return;
  enviando.value = true;
  try {
    const cuerpo = new FormData();
    let ruta;
    if (modo.value === 'espacios') {
      ruta = '/api/app/punto-venta/espacios';
      cuerpo.append('datos', new Blob([JSON.stringify({
        nombre: form.nombre.trim(), ci: form.ci.trim(), celular: form.celular, negocio: form.negocio.trim(), nit: form.nit.trim(),
        rubro: form.rubro.trim(), medio: form.medio, puestos: carrito.value.map((p) => p.id),
        acompanantes: form.acompanantes.filter((x) => x.nombre.trim()).map((x) => ({ nombre: x.nombre.trim(), ci: x.ci.trim() })),
      })], { type: 'application/json' }));
    } else {
      ruta = `/api/app/entradas/oficina/${actual.value.id}`;
      cuerpo.append('datos', new Blob([JSON.stringify({
        nombre: form.nombre.trim(), ci: form.ci.trim(), celular: String(form.celular).replace(/[^\d+]/g, ''),
        modoPago: form.medio === 'TRANSFERENCIA' ? 'COMPROBANTE' : form.medio,
        items: entradasElegidas.value.map((t) => ({ tipoId: t.id, cantidad: t.cantidad })),
      })], { type: 'application/json' }));
    }
    if (form.foto) cuerpo.append('comprobante', form.foto);
    const r = await apiFetch(ruta, { method: 'POST', body: cuerpo });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo registrar la venta', 'error'); return; }
    entrega.value = modo.value === 'espacios'
      ? { tipo: 'espacios', datos: d.entrega, titular: { nombre: form.nombre.trim(), celular: form.celular, empresa: form.negocio || form.nombre }, total: d.total, medio: form.medio }
      : { tipo: 'entradas', codigo: d.codigo, total: d.total, cantidad: d.cantidad, nombre: form.nombre.trim(), celular: String(form.celular).replace(/[^\d+]/g, ''), medio: form.medio };
    toast(`Venta registrada: ${bs(d.total)}`, 'ok');
    Object.keys(cantidades).forEach((k) => { cantidades[k] = 0; });
    limpiarForm();
    cargarTipos();
  } finally {
    enviando.value = false;
  }
}

function nuevaVenta() {
  entrega.value = null;
}

const enlaceCompra = computed(() => (entrega.value?.codigo ? `${window.location.origin}/?compra=${entrega.value.codigo}` : ''));

watch(edicion, prepararActividad);

onMounted(async () => {
  await cargarActividades();
  await prepararActividad();
  reloj = setInterval(() => { ahora.value = Date.now(); }, 1000);
  soltar = alCambiarPublico((m) => { if (m?.tipo === 'entradas') cargarTipos(); });
});
onUnmounted(() => { tienda.value?.cerrar(); clearInterval(reloj); soltar?.(); });
</script>

<template>
  <div class="pv">
    <header class="cab">
      <select v-model.number="edicion" class="control actividad" aria-label="Actividad">
        <option v-for="a in actividades" :key="a.id" :value="a.id">{{ a.nombre }}</option>
      </select>
      <nav v-if="actual?.vendeEspacios && actual?.vendeEntradas" class="modos" role="tablist">
        <button role="tab" :class="{ on: modo === 'espacios' }" @click="modo = 'espacios'">🗺️ {{ varios }}</button>
        <button role="tab" :class="{ on: modo === 'entradas' }" @click="modo = 'entradas'">🎟️ Entradas</button>
      </nav>
      <router-link :to="aModulo('mis-ventas')" class="btn btn-sm btn-fantasma">🗂️ Mis ventas</router-link>
    </header>

    <p v-if="!actividades.length" class="vacio card">No hay actividades a la venta. Publica una en <router-link :to="aModulo('actividades')">Mis actividades</router-link>.</p>

    <div v-else class="cuerpo">
      <section class="zona card">
        <template v-if="modo === 'espacios'">
          <div class="barra-plano">
            <span><b>{{ libres }}</b> {{ varios.toLowerCase() }} libres</span>
            <span class="leyenda"><i class="l"></i>Libre <i class="m"></i>Tu selección <i class="t"></i>En proceso <i class="o"></i>Vendido</span>
          </div>
          <div class="marco">
            <PanZoom v-if="plano" :key="`${edicion}-${aspecto}-${Boolean(encuadre)}`" :aspect="aspecto" :contenido="encuadre" :max-ancho="anchoParaTocar(puestos)" :umbral-detalle="anchoParaLeer(puestos)" llenar>
              <div class="plano" :style="{ aspectRatio: 1 / aspecto }">
                <img v-if="fondo" :src="fondo" alt="Plano" draggable="false" />
                <RotuloPlano v-for="t in textos.filter((x) => x.fondo)" :key="t.id" :texto="t" />
                <button v-for="p in puestos" :key="p.id" type="button" class="pin"
                        :class="[`forma-${p.forma || 'cuadrado'}`, `est-${p.estado}`, { mio: p.estado === 'T' && p.reservadoPor === auth.id, espera: ocupado.has(p.id) }]"
                        :style="{ ...estiloPin(p), '--c': p.color || '#6366f1' }"
                        :title="`${p.categoria} ${p.codigo} · ${bs(p.precio)}`" :aria-label="`${uno} ${p.codigo}`"
                        @pointerdown.stop @click="tocar(p)"><span class="num">{{ p.codigo }}</span></button>
                <RotuloPlano v-for="t in textos.filter((x) => !x.fondo)" :key="t.id" :texto="t" />
              </div>
            </PanZoom>
          </div>
        </template>
        <template v-else>
          <div class="tipos">
            <div v-for="t in tipos" :key="t.id" class="tipo" :class="{ agotado: t.quedan === 0 }">
              <div><strong>{{ t.nombre }}</strong><small>{{ bs(t.precio) }}{{ t.quedan != null ? ` · quedan ${t.quedan}` : '' }}</small></div>
              <div class="contador">
                <button type="button" :disabled="!cantidades[t.id]" @click="cambiar(t, -1)">−</button>
                <span>{{ cantidades[t.id] || 0 }}</span>
                <button type="button" :disabled="t.quedan === 0 || (t.quedan != null && cantidades[t.id] >= t.quedan)" @click="cambiar(t, 1)">+</button>
              </div>
            </div>
            <p v-if="!tipos.length" class="vacio">Esta actividad no tiene tipos de entrada.</p>
          </div>
        </template>
      </section>

      <aside class="panel card">
        <template v-if="entrega">
          <div class="exito">
            <span class="ok">✓</span>
            <h3>Venta registrada · {{ bs(entrega.total) }}</h3>
            <span class="medio" :class="{ efectivo: entrega.medio === 'EFECTIVO' }">{{ entrega.medio === 'EFECTIVO' ? '💵 Cobrado en EFECTIVO' : entrega.medio === 'QR' ? '📱 Cobrado por QR' : '🏦 Transferencia' }}</span>
          </div>
          <EntregaVenta v-if="entrega.tipo === 'espacios' && entrega.datos" :entrega="entrega.datos" :titular="entrega.titular" :actividad="actual?.nombre || ''" />
          <div v-else-if="entrega.tipo === 'entradas'" class="entrega-entradas">
            <p>{{ entrega.cantidad }} entrada(s) a nombre de <b>{{ entrega.nombre }}</b> · código <b>{{ entrega.codigo }}</b></p>
            <button class="btn btn-primario" @click="descargarPdf(`/api/app/entradas/compra/${entrega.codigo}/pases.pdf`, `entradas-${entrega.codigo}.pdf`)">🎟️ Descargar entradas (PDF)</button>
            <button class="btn" @click="descargarPdf(`/api/app/entradas/compra/${entrega.codigo}/recibo.pdf`, `recibo-${entrega.codigo}.pdf`)">🧾 Descargar recibo</button>
            <a class="btn btn-whatsapp" :href="enlaceWhatsApp(entrega.celular, `Hola ${entrega.nombre.split(' ')[0]}, aquí están tus entradas para ${actual?.nombre}: ${enlaceCompra}`)" target="_blank" rel="noopener">💬 Enviar por WhatsApp</a>
          </div>
          <button class="btn btn-primario grande" @click="nuevaVenta">＋ Nueva venta</button>
        </template>

        <template v-else>
          <h3>{{ modo === 'espacios' ? `${varios} ${uno.endsWith('a') ? 'elegidas' : 'elegidos'}` : 'Entradas elegidas' }}</h3>
          <p v-if="!hayAlgo" class="muted chico">{{ modo === 'espacios' ? `Toca ${uno.endsWith('a') ? 'una' : 'un'} ${uno} libre en el plano. Queda apartad${uno.endsWith('a') ? 'a' : 'o'} a tu nombre unos minutos mientras cobras.` : 'Suma entradas con el botón +.' }}</p>
          <ul v-else class="carrito">
            <template v-if="modo === 'espacios'">
              <li v-for="p in carrito" :key="p.id">
                <span>{{ p.categoria }} · <b>{{ p.codigo }}</b><small :class="{ urgente: segundos(p) < 60 }"> ⏳ {{ reloj2(segundos(p)) }}</small></span>
                <span>{{ bs(p.precio) }}</span>
                <button type="button" class="quitar" aria-label="Quitar" @click="tocar(p)">✕</button>
              </li>
            </template>
            <template v-else>
              <li v-for="t in entradasElegidas" :key="t.id"><span>{{ t.cantidad }} × {{ t.nombre }}</span><span>{{ bs(t.cantidad * t.precio) }}</span></li>
            </template>
            <li class="total"><span>Total</span><b>{{ bs(total) }}</b></li>
          </ul>

          <form v-if="hayAlgo" class="formulario" @submit.prevent="cobrar">
            <label class="campo"><span>Nombre completo</span><input v-model="form.nombre" class="control" maxlength="160" autocomplete="off" /></label>
            <div class="dos">
              <label class="campo"><span>C.I.</span><input v-model="form.ci" class="control" maxlength="20" inputmode="numeric" /></label>
              <label class="campo"><span>Celular (WhatsApp)</span><CampoCelular v-model="form.celular" /></label>
            </div>
            <template v-if="modo === 'espacios' && !esPersona">
              <label class="campo"><span>Nombre del negocio</span><input v-model="form.negocio" class="control" maxlength="200" /></label>
              <div class="dos">
                <label class="campo"><span>NIT (opcional)</span><input v-model="form.nit" class="control" maxlength="30" /></label>
                <label class="campo"><span>Rubro</span><input v-model="form.rubro" class="control" maxlength="100" placeholder="Comida, artesanía…" /></label>
              </div>
            </template>
            <div v-if="modo === 'espacios'" class="acompanantes">
              <div v-for="(x, i) in form.acompanantes" :key="i" class="acompanante">
                <input v-model="x.nombre" class="control" placeholder="Nombre del acompañante" />
                <input v-model="x.ci" class="control" placeholder="C.I." />
                <button type="button" class="btn btn-fantasma btn-icono" aria-label="Quitar" @click="form.acompanantes.splice(i, 1)">✕</button>
              </div>
              <button type="button" class="btn btn-sm btn-fantasma" @click="form.acompanantes.push({ nombre: '', ci: '' })">＋ Acompañante (opcional)</button>
            </div>

            <div class="medios" role="radiogroup" aria-label="Medio de pago">
              <button v-for="m in MEDIOS" :key="m.id" type="button" class="medio-btn" :class="[{ on: form.medio === m.id }, m.id.toLowerCase()]"
                      role="radio" :aria-checked="form.medio === m.id" @click="form.medio = m.id">
                <span class="ico">{{ m.icono }}</span>{{ m.txt }}
              </button>
            </div>
            <p v-if="form.medio === 'EFECTIVO'" class="aviso-efectivo">💵 Pago en EFECTIVO: recibe el dinero antes de confirmar. Se registra como cobrado.</p>
            <label class="foto" :class="{ lista: form.foto }">
              <input type="file" accept="image/*,application/pdf" capture="environment" @change="form.foto = $event.target.files?.[0] || null" />
              {{ form.foto ? `✓ ${form.foto.name}` : form.medio === 'EFECTIVO' ? '📷 Foto del recibo (opcional)' : '📷 Foto del comprobante (opcional)' }}
            </label>

            <button type="submit" class="btn btn-primario grande cobrar" :class="{ efectivo: form.medio === 'EFECTIVO' }" :disabled="!listo || enviando">
              {{ enviando ? 'Registrando…' : `Cobrar ${bs(total)}` }}
            </button>
            <button type="button" class="btn btn-fantasma" :disabled="enviando" @click="vaciar">Vaciar</button>
          </form>
        </template>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.pv { display: flex; flex-direction: column; gap: 0.8rem; height: 100%; min-height: 0; }
.cab { display: flex; gap: 0.6rem; align-items: center; flex-wrap: wrap; }
.actividad { max-width: 340px; font-weight: 700; }
.modos { display: flex; gap: 0.3rem; }
.modos button { border: 1px solid var(--border); background: var(--panel); color: var(--muted); border-radius: 999px; padding: 0.4rem 0.9rem; font: inherit; font-weight: 700; cursor: pointer; }
.modos button.on { background: var(--acento); border-color: var(--acento); color: var(--acento-texto); }
.cab .btn-fantasma { margin-left: auto; }
.cuerpo { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 0.8rem; flex: 1; min-height: 0; }
.zona { display: flex; flex-direction: column; gap: 0.5rem; padding: 0.7rem; min-height: 420px; }
.barra-plano { display: flex; justify-content: space-between; gap: 0.6rem; flex-wrap: wrap; font-size: 0.85rem; }
.leyenda { display: flex; gap: 0.5rem; align-items: center; color: var(--muted); font-size: 0.78rem; flex-wrap: wrap; }
.leyenda i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; }
.leyenda .l { background: #22c55e; } .leyenda .m { background: var(--mia); } .leyenda .t { background: #f59e0b; } .leyenda .o { background: #64748b; }
.marco { flex: 1; min-height: 360px; display: flex; border-radius: var(--radio); overflow: hidden; border: 1px solid var(--border); }
.marco :deep(.viewport) { background: #e9ebf5; }
.marco > * { flex: 1; }
.plano { position: relative; width: 100%; background: #f4f5fb; }
.plano img { width: 100%; height: auto; display: block; pointer-events: none; user-select: none; }
.pin {
  position: absolute; z-index: 1; width: 100px; height: var(--alto, 100px); padding: 0; border: 0; cursor: pointer;
  transform-origin: 0 0; transform: scale(calc(var(--pin, 20) / 100)) rotate(var(--giro, 0deg)) translate(-50%, -50%);
  background: var(--c); box-shadow: 0 0 0 6px rgba(255, 255, 255, 0.9); transition: filter 0.15s;
}
.pin:hover { filter: brightness(1.12); z-index: 3; }
.pin.est-T { background: repeating-linear-gradient(45deg, #ffb347 0 14px, #e58f1c 14px 28px); }
.pin.est-O { background: #59607a; cursor: not-allowed; }
.pin.est-X { background: #9aa0b5; opacity: 0.55; cursor: not-allowed; }
.pin.mio { background: var(--mia); box-shadow: 0 0 0 10px #fff, 0 0 0 20px var(--mia); z-index: 4; }
.pin.espera { opacity: 0.5; }
.forma-cuadrado { border-radius: 10%; } .forma-rectangulo { border-radius: 6%; } .forma-circulo { border-radius: 50%; }
.forma-triangulo { clip-path: polygon(50% 0%, 100% 100%, 0% 100%); }
.forma-hexagono { clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%); }
.num { display: none; position: absolute; inset: 0; align-items: center; justify-content: center; pointer-events: none; transform: rotate(calc(-1 * var(--giro, 0deg))); font: 700 46px/1 system-ui, sans-serif; color: #fff; text-shadow: 0 2px 4px rgba(2, 6, 23, 0.9); }
.marco :deep(.world.detalle) .num { display: flex; }
.tipos { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 0.7rem; align-content: start; }
.tipo { display: flex; justify-content: space-between; align-items: center; gap: 0.6rem; padding: 1rem; border: 1px solid var(--border); border-radius: var(--radio); background: var(--panel-2); }
.tipo.agotado { opacity: 0.5; }
.tipo small { display: block; color: var(--muted); }
.contador { display: flex; align-items: center; gap: 0.5rem; }
.contador button { width: 44px; height: 44px; border-radius: 50%; border: 1px solid var(--border); background: var(--panel); font-size: 1.3rem; font-weight: 800; cursor: pointer; color: var(--text); }
.contador button:disabled { opacity: 0.35; cursor: default; }
.contador span { min-width: 26px; text-align: center; font-weight: 800; font-size: 1.15rem; }
.panel { padding: 1rem; display: flex; flex-direction: column; gap: 0.7rem; overflow-y: auto; }
.panel h3 { margin: 0; font-size: 1.05rem; }
.chico { font-size: 0.85rem; margin: 0; }
.carrito { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.35rem; }
.carrito li { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; padding: 0.45rem 0.6rem; border-radius: var(--radio-sm); background: var(--panel-2); font-variant-numeric: tabular-nums; }
.carrito li small { color: var(--muted); margin-left: 0.3rem; }
.carrito li small.urgente { color: var(--danger); font-weight: 700; }
.carrito li.total { background: var(--acento-suave); font-size: 1.1rem; }
.quitar { border: 0; background: none; color: var(--muted); cursor: pointer; font-size: 0.9rem; }
.formulario { display: flex; flex-direction: column; gap: 0.6rem; }
.dos { display: grid; grid-template-columns: 1fr 1.3fr; gap: 0.5rem; }
.acompanantes { display: flex; flex-direction: column; gap: 0.4rem; }
.acompanante { display: grid; grid-template-columns: 1.6fr 1fr auto; gap: 0.4rem; }
.medios { display: grid; grid-template-columns: 1.4fr 1fr 1fr; gap: 0.4rem; }
.medio-btn { display: flex; flex-direction: column; align-items: center; gap: 0.2rem; padding: 0.6rem 0.3rem; border: 2px solid var(--border); background: var(--panel); color: var(--text); border-radius: var(--radio); font: inherit; font-weight: 800; cursor: pointer; }
.medio-btn .ico { font-size: 1.5rem; }
.medio-btn.on { border-color: var(--acento); background: var(--acento-suave); }
.medio-btn.efectivo.on { border-color: #d97706; background: #fef3c7; color: #92400e; }
.aviso-efectivo { margin: 0; padding: 0.55rem 0.7rem; border-radius: var(--radio-sm); background: #fef3c7; color: #92400e; font-weight: 700; font-size: 0.85rem; }
.foto { display: flex; align-items: center; justify-content: center; min-height: 44px; border: 2px dashed var(--border); border-radius: var(--radio-sm); cursor: pointer; font-weight: 700; font-size: 0.88rem; color: var(--muted); }
.foto input { display: none; }
.foto.lista { border-style: solid; border-color: var(--ok); color: var(--ok); }
.grande { padding: 0.8rem; font-size: 1.05rem; }
.cobrar.efectivo { background: #d97706; border-color: #d97706; color: #fff; }
.exito { display: flex; flex-direction: column; align-items: center; gap: 0.3rem; text-align: center; }
.exito .ok { display: grid; place-items: center; width: 52px; height: 52px; border-radius: 50%; background: var(--ok); color: #fff; font-size: 1.6rem; font-weight: 800; }
.medio { font-weight: 700; }
.medio.efectivo { color: #d97706; }
.entrega-entradas { display: flex; flex-direction: column; gap: 0.5rem; }
.entrega-entradas p { margin: 0; }
.btn-whatsapp { background: #16a34a; border-color: #16a34a; color: #fff; text-align: center; }
.vacio { text-align: center; color: var(--muted); padding: 1.5rem; }
@media (max-width: 960px) {
  .cuerpo { grid-template-columns: 1fr; }
  .zona { min-height: 0; }
  .marco { min-height: 52vh; }
  .actividad { max-width: none; flex: 1 1 100%; }
  .cab .btn-fantasma { margin-left: 0; }
}
</style>
