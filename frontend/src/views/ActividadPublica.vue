<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import ModalPublico from '../components/ModalPublico.vue';
import SelectorPublico from '../components/SelectorPublico.vue';
import PanZoom from '../components/PanZoom.vue';
import RotuloPlano from '../components/RotuloPlano.vue';
import { url as urlApi } from '../config.js';
import { marca, cargarMarca } from '../ui/marca';
import { alCambiarPublico } from '../ui/canalPublico';
import { avisoPublico } from '../ui/avisosPublicos';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { estiloPin, anchoParaTocar, anchoParaLeer, medidaLegible } from '../mapa';

const RUBROS = ['Comida y bebidas', 'Artesanía', 'Ropa y accesorios', 'Belleza y cuidado', 'Tecnología', 'Servicios', 'Educación', 'Hogar y decoración'];
const ESTADO = { L: 'Libre', S: 'Solicitado', R: 'Reservado', T: 'En trámite', O: 'Ocupado', X: 'No disponible' };

const edicion = useRoute().query.edicion;
const consulta = edicion ? `?${new URLSearchParams({ edicion })}` : '';
const datos = ref(null);
const plano = ref(null);
const error = ref('');
const filtro = ref(null);
const infoAbierta = ref(false);
const formAbierto = ref(false);
const enviado = ref(null);
const puestoId = ref(null);
const enviando = ref(false);
const mensaje = ref('');
const resaltados = ref(new Set());
const form = reactive({ nombreCompleto: '', celular: '', empresa: '', rubro: '', categoriaId: '', modoPago: '', comprobante: null });
let soltarCanal = null;
let sondeo = null;
let pendiente = null;

const actividad = computed(() => datos.value?.edicion ?? null);
const pago = computed(() => actividad.value?.pago || {});
const puestos = computed(() => plano.value?.puestos ?? []);
const textos = computed(() => plano.value?.textos ?? []);
const conPlano = computed(() => puestos.value.length > 0);
const aspecto = computed(() => {
  const p = plano.value?.plano;
  return p?.ancho && p?.alto ? p.alto / p.ancho : 0.62;
});
const fondo = computed(() => {
  const p = plano.value?.plano;
  if (!p?.url) return null;
  return p.propio ? urlApi(p.url) : `${import.meta.env.BASE_URL}mapa.png`;
});
const recibe = computed(() => Boolean(actividad.value?.recibeInteresados));
const contacto = computed(() => actividad.value?.contacto || marca.contacto || '');
const whatsapp = computed(() => enlaceWhatsApp(contacto.value, actividad.value ? `Hola, consulto por ${actividad.value.nombre}` : ''));
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;

const categorias = computed(() => {
  const stats = new Map((datos.value?.estadisticasPorCategoria ?? []).map((s) => [s.categoriaId, s]));
  return (datos.value?.categorias ?? []).map((c) => {
    const propios = puestos.value.filter((p) => p.categoriaId === c.id);
    const libres = propios.length ? propios.filter((p) => p.estado === 'L').length : (stats.get(c.id)?.libres ?? 0);
    const total = propios.length || stats.get(c.id)?.total || 0;
    return { ...c, libres, total };
  });
});
const libresTotal = computed(() => categorias.value.reduce((s, c) => s + c.libres, 0));
const espaciosTotal = computed(() => categorias.value.reduce((s, c) => s + c.total, 0));
const opcionesCategoria = computed(() => categorias.value.map((c) => ({
  valor: c.id, etiqueta: c.nombre, color: c.color || '#8f6dff',
  detalle: [c.precioBase ? `Desde ${bs(c.precioBase)}` : null, c.descripcion].filter(Boolean).join(' · '),
  extra: c.total ? `${c.libres} libre${c.libres === 1 ? '' : 's'}` : null,
  deshabilitado: c.total > 0 && c.libres === 0,
})));
const puesto = computed(() => puestos.value.find((p) => p.id === puestoId.value) || null);
const categoriaElegida = computed(() => categorias.value.find((c) => String(c.id) === String(form.categoriaId)) || null);
const hayComprobante = computed(() => Boolean(pago.value.qrUrl || pago.value.banco || pago.value.cuenta));
const hayOficina = computed(() => Boolean(pago.value.oficina));
const necesitaModo = computed(() => hayComprobante.value || hayOficina.value);
const celularValido = computed(() => /^\+?\d{7,15}$/.test(form.celular.replace(/\s/g, '')));
const formListo = computed(() => form.nombreCompleto.trim() && celularValido.value && form.empresa.trim() && form.rubro.trim()
  && form.categoriaId && (!necesitaModo.value || form.modoPago) && (form.modoPago !== 'COMPROBANTE' || form.comprobante));
const encuadre = computed(() => {
  const xs = [];
  const ys = [];
  for (const p of puestos.value) { xs.push(p.mapaX); ys.push(p.mapaY); }
  for (const t of textos.value) { xs.push(t.mapaX ?? 0.5); ys.push(t.mapaY ?? 0.5); }
  if (!xs.length) return null;
  const m = 0.04;
  return {
    x0: Math.max(0, Math.min(...xs) - m), x1: Math.min(1, Math.max(...xs) + m),
    y0: Math.max(0, Math.min(...ys) - m), y1: Math.min(1, Math.max(...ys) + m),
  };
});
const topeZoom = computed(() => anchoParaTocar(puestos.value));
const umbralNumeros = computed(() => anchoParaLeer(puestos.value));

async function cargar() {
  try {
    const [ri, rp] = await Promise.all([fetch(urlApi(`/api/publico/feria${consulta}`)), fetch(urlApi(`/api/publico/feria/plano${consulta}`))]);
    if (!ri.ok) throw new Error();
    datos.value = await ri.json();
    plano.value = rp.ok ? await rp.json() : null;
    error.value = datos.value?.edicion ? '' : 'No hay una actividad publicada en este momento.';
    if (datos.value?.edicion) document.title = `${datos.value.edicion.nombre} · ${marca.nombre}`;
  } catch {
    if (!datos.value) error.value = 'No se pudo cargar la información. Revisa tu conexión.';
  }
}

async function recargarPlano() {
  const antes = new Map(puestos.value.map((p) => [p.id, p.estado]));
  const r = await fetch(urlApi(`/api/publico/feria/plano${consulta}`)).catch(() => null);
  if (!r?.ok) return;
  plano.value = await r.json();
  const liberados = puestos.value.filter((p) => p.estado === 'L' && antes.get(p.id) && antes.get(p.id) !== 'L').map((p) => p.id);
  if (liberados.length) {
    resaltados.value = new Set(liberados);
    avisoPublico(liberados.length === 1 ? 'Se liberó un espacio en el plano' : `Se liberaron ${liberados.length} espacios`, 'libre', 3500);
    setTimeout(() => { resaltados.value = new Set(); }, 4000);
  }
  if (formAbierto.value && puesto.value && puesto.value.estado !== 'L' && !enviando.value) {
    mensaje.value = 'Este espacio acaba de ser solicitado por otra persona. Cierra y elige otro en el plano.';
  }
}

const CLAVE_BORRADOR = 'feria.solicitud.borrador.v1';
const VIGENCIA_BORRADOR_MS = 6 * 60 * 60 * 1000;

function guardarBorrador() {
  try {
    const { comprobante, ...datosForm } = form;
    localStorage.setItem(CLAVE_BORRADOR, JSON.stringify({ datos: datosForm, puestoId: puestoId.value, abierto: formAbierto.value, t: Date.now() }));
  } catch { }
}

function restaurarBorrador() {
  try {
    const b = JSON.parse(localStorage.getItem(CLAVE_BORRADOR) || 'null');
    if (!b || Date.now() - (b.t || 0) > VIGENCIA_BORRADOR_MS) return;
    Object.assign(form, b.datos || {}, { comprobante: null });
    if (!b.abierto || !recibe.value) return;
    const p = b.puestoId ? puestos.value.find((x) => x.id === b.puestoId) : null;
    if (b.puestoId && (!p || p.estado !== 'L')) {
      avisoPublico('El espacio que elegiste ya no está libre. Elige otro en el plano.', 'info', 4000);
      return;
    }
    puestoId.value = p?.id ?? null;
    formAbierto.value = true;
    avisoPublico('Retomamos tu solicitud donde la dejaste.', 'info', 3500);
  } catch { }
}

watch([form, puestoId, formAbierto], guardarBorrador, { deep: true });
const pararRestaurar = watch(() => Boolean(plano.value && datos.value), (v) => {
  if (!v) return;
  restaurarBorrador();
  queueMicrotask(() => pararRestaurar());
});

function programarRecarga() {
  clearTimeout(pendiente);
  pendiente = setTimeout(recargarPlano, 350);
}

function claseCategoria(p) {
  return filtro.value && p.categoriaId !== filtro.value ? 'apagado' : '';
}

function tocarPuesto(p) {
  if (!recibe.value) { avisoPublico('Por ahora no se están recibiendo solicitudes.', 'info', 3000); return; }
  if (p.estado !== 'L') { avisoPublico(`El espacio ${p.codigo} está ${ESTADO[p.estado]?.toLowerCase() || 'ocupado'}. Elige uno libre.`, 'info', 2800); return; }
  abrirFormulario(p);
}

function abrirFormulario(p = null) {
  mensaje.value = '';
  puestoId.value = p?.id ?? null;
  form.categoriaId = p ? p.categoriaId : (filtro.value || '');
  if (!necesitaModo.value) form.modoPago = '';
  else if (!form.modoPago) form.modoPago = hayComprobante.value ? 'COMPROBANTE' : 'OFICINA';
  formAbierto.value = true;
}

function elegirArchivo(e) {
  form.comprobante = e.target.files?.[0] || null;
}

async function enviar() {
  if (!formListo.value || enviando.value) return;
  enviando.value = true;
  mensaje.value = '';
  try {
    const cuerpo = {
      nombreCompleto: form.nombreCompleto.trim(), celular: form.celular.replace(/\s/g, ''), empresa: form.empresa.trim(),
      rubro: form.rubro.trim(), categoriaId: Number(form.categoriaId), puestoId: puestoId.value, modoPago: form.modoPago || null,
    };
    const datosEnvio = new FormData();
    datosEnvio.append('datos', new Blob([JSON.stringify(cuerpo)], { type: 'application/json' }));
    if (form.modoPago === 'COMPROBANTE' && form.comprobante) datosEnvio.append('comprobante', form.comprobante);
    const r = await fetch(urlApi('/api/publico/interesados-stand'), { method: 'POST', body: datosEnvio });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.mensaje || 'No se pudo enviar tu solicitud.');
    enviado.value = { puesto: puesto.value, categoria: categoriaElegida.value, celular: cuerpo.celular, modoPago: cuerpo.modoPago, empresa: cuerpo.empresa };
    formAbierto.value = false;
    Object.assign(form, { empresa: '', rubro: '', comprobante: null });
    recargarPlano();
  } catch (e) {
    mensaje.value = e.message || 'No se pudo enviar tu solicitud.';
    if (/tomado|disponible/i.test(mensaje.value)) recargarPlano();
  } finally {
    enviando.value = false;
  }
}

const mensajeWhatsApp = computed(() => {
  const e = enviado.value;
  if (!e) return '';
  return `Hola, envié una solicitud para ${actividad.value?.nombre || 'la actividad'}`
    + (e.puesto ? `: espacio ${e.puesto.codigo} (${e.puesto.categoria})` : e.categoria ? `: ${e.categoria.nombre}` : '')
    + ` a nombre de ${e.empresa}.`;
});

onMounted(() => {
  cargarMarca();
  cargar();
  soltarCanal = alCambiarPublico((m) => {
    if (m?.tipo === 'feria') programarRecarga();
    else if (m?.tipo === 'actividad') cargar();
  }, { reconectado: cargar });
  sondeo = setInterval(cargar, 90000);
});
onUnmounted(() => { soltarCanal?.(); clearInterval(sondeo); clearTimeout(pendiente); });
</script>

<template>
  <div class="pagina">
    <header class="barra">
      <router-link to="/" class="pub-volver">← Inicio</router-link>
      <div v-if="actividad" class="titulo">
        <h1>{{ actividad.nombre }}</h1>
        <span class="cuando">{{ [actividad.fechasTexto, actividad.lugar].filter(Boolean).join(' · ') }}</span>
      </div>
      <span v-else class="titulo marca">{{ marca.nombre }}</span>
      <div class="acciones-barra">
        <button v-if="actividad" type="button" class="pub-boton secundario chico" @click="infoAbierta = true">Información</button>
        <a v-if="whatsapp" :href="whatsapp" target="_blank" rel="noopener" class="pub-boton whatsapp chico">Consultas</a>
      </div>
    </header>

    <p v-if="error" class="estado">{{ error }} <router-link to="/">Volver al inicio</router-link></p>
    <p v-else-if="!datos" class="estado">Cargando…</p>

    <main v-else-if="actividad" class="contenido">
      <section class="zona-plano">
        <div class="resumen-plano">
          <span><b>{{ libresTotal }}</b> de {{ espaciosTotal }} espacios libres</span>
          <span class="pub-vivo">En vivo</span>
          <button v-if="recibe" type="button" class="pub-boton chico solo-movil" @click="abrirFormulario()">Quiero participar</button>
        </div>
        <div class="filtros" role="group" aria-label="Filtrar por categoría">
          <button type="button" class="filtro" :class="{ on: !filtro }" @click="filtro = null">Todas <small>{{ libresTotal }}</small></button>
          <button v-for="c in categorias" :key="c.id" type="button" class="filtro" :class="{ on: filtro === c.id, agotada: c.total && !c.libres }"
                  :style="{ '--c': c.color || '#8f6dff' }" @click="filtro = filtro === c.id ? null : c.id">
            <i></i>{{ c.nombre }} <small>{{ c.libres }}/{{ c.total }}</small>
          </button>
        </div>

        <template v-if="conPlano">
          <div class="marco-plano">
            <PanZoom :key="aspecto" :aspect="aspecto" :contenido="encuadre" :max-ancho="topeZoom" :umbral-detalle="umbralNumeros" llenar>
              <div class="plano" :class="{ 'sin-fondo': !fondo }" :style="{ aspectRatio: 1 / aspecto }">
                <img v-if="fondo" :src="fondo" alt="Plano de la actividad" draggable="false" />
                <RotuloPlano v-for="t in textos.filter((x) => x.fondo)" :key="t.id" :texto="t" />
                <button v-for="p in puestos" :key="p.id" type="button" class="pin"
                        :class="[`forma-${p.forma || 'cuadrado'}`, `est-${p.estado}`, claseCategoria(p), { brilla: resaltados.has(p.id) }]"
                        :style="{ ...estiloPin(p), '--c': p.color || '#8f6dff' }"
                        :title="`${p.categoria} ${p.codigo} · ${ESTADO[p.estado]}${p.estado === 'L' ? ` · ${bs(p.precio)}` : ''}`"
                        :aria-label="`Espacio ${p.codigo}, ${p.categoria}, ${ESTADO[p.estado]}`"
                        @pointerdown.stop @click="tocarPuesto(p)"><span class="num">{{ p.codigo }}</span></button>
                <RotuloPlano v-for="t in textos.filter((x) => !x.fondo)" :key="t.id" :texto="t" />
              </div>
            </PanZoom>
          </div>
          <div class="leyenda">
            <span><i class="l"></i>Libre (color de su categoría)</span>
            <span><i class="s"></i>Solicitado</span>
            <span><i class="o"></i>Ocupado o reservado</span>
          </div>
          <p class="pista">{{ recibe ? 'Toca un espacio libre para solicitarlo · acerca con la rueda o pellizcando' : 'Las solicitudes están cerradas por ahora.' }}</p>
        </template>
        <div v-else class="tarjetas">
          <article v-for="c in categorias" :key="c.id" class="tarjeta" :style="{ '--c': c.color || '#8f6dff' }">
            <div class="tarjeta-cab"><i></i><strong>{{ c.nombre }}</strong></div>
            <p v-if="c.descripcion" class="tenue">{{ c.descripcion }}</p>
            <div class="tarjeta-pie">
              <span v-if="c.precioBase" class="precio">Desde {{ bs(c.precioBase) }}</span>
              <span class="tenue">{{ c.libres }} de {{ c.total }} libres</span>
            </div>
            <button v-if="recibe" type="button" class="pub-boton secundario chico" :disabled="c.total > 0 && !c.libres" @click="filtro = c.id; abrirFormulario()">Solicitar espacio</button>
          </article>
        </div>
      </section>

      <aside class="lateral">
        <img v-if="actividad.portadaUrl" class="portada" :src="urlApi(actividad.portadaUrl)" alt="" />
        <p v-if="actividad.descripcion" class="descripcion">{{ actividad.descripcion }}</p>
        <div class="pasos">
          <strong>¿Cómo participo?</strong>
          <ol>
            <li><b>Elige</b> un espacio libre en el plano.</li>
            <li><b>Completa</b> tus datos y los de tu negocio.</li>
            <li><b>Paga</b> {{ hayComprobante ? 'con QR o transferencia' : '' }}{{ hayComprobante && hayOficina ? ' o ' : '' }}{{ hayOficina ? 'en nuestras oficinas' : '' }}{{ !necesitaModo ? 'como te indiquemos' : '' }}.</li>
            <li><b>Te confirmamos</b> por WhatsApp.</li>
          </ol>
        </div>
        <button v-if="recibe" type="button" class="pub-boton" @click="abrirFormulario()">Quiero participar</button>
      </aside>
    </main>

    <ModalPublico v-if="formAbierto" ancho="640px" :cerrable="!enviando" @cerrar="formAbierto = false">
      <template #cabecera>
        <div class="ficha-cab">
          <span class="pub-chip" :style="{ color: (puesto || categoriaElegida)?.color || undefined }">{{ puesto ? puesto.categoria : 'Solicitud de espacio' }}</span>
          <h2>{{ puesto ? `Espacio ${puesto.codigo}` : 'Quiero participar' }}</h2>
        </div>
      </template>

      <div v-if="puesto" class="resumen">
        <div><span>Precio</span><strong class="precio">{{ bs(puesto.precio) }}</strong></div>
        <div><span>Medida</span><strong>{{ medidaLegible(puesto.tamano) }}</strong></div>
        <div v-if="puesto.referencia" class="ancho"><span>Ubicación</span><strong>{{ puesto.referencia }}</strong></div>
      </div>

      <form id="form-interes" class="formulario" @submit.prevent="enviar">
        <fieldset>
          <legend>1. Tus datos</legend>
          <div class="campos">
            <label class="pub-campo ancho"><span>Nombre completo</span>
              <input id="i-nombre" v-model="form.nombreCompleto" autocomplete="name" maxlength="200" />
              <small>Tal como figura en tu carnet.</small></label>
            <label class="pub-campo ancho"><span>Celular con WhatsApp</span>
              <input id="i-celular" v-model="form.celular" inputmode="tel" autocomplete="tel" maxlength="16" placeholder="70000000" />
              <small>Por aquí te confirmaremos el espacio.</small></label>
          </div>
        </fieldset>

        <fieldset>
          <legend>2. Tu negocio</legend>
          <div class="campos">
            <label class="pub-campo ancho"><span>Nombre del negocio o emprendimiento</span>
              <input id="i-empresa" v-model="form.empresa" maxlength="200" placeholder="Ej. Delicias de la Abuela" />
              <small>El nombre con el que te conocen tus clientes. Solo letras.</small></label>
            <label class="pub-campo ancho"><span>Rubro: ¿a qué se dedica tu negocio?</span>
              <input id="i-rubro" v-model="form.rubro" maxlength="200" placeholder="Ej. Comida y bebidas" />
              <span class="sugerencias">
                <button v-for="r in RUBROS" :key="r" type="button" :class="{ on: form.rubro === r }" @click="form.rubro = r">{{ r }}</button>
              </span></label>
          </div>
        </fieldset>

        <fieldset>
          <legend>3. Espacio</legend>
          <div class="pub-campo"><span>Categoría de espacio</span>
            <SelectorPublico id="i-categoria" v-model="form.categoriaId" :opciones="opcionesCategoria" :bloqueado="Boolean(puesto)"
                             marcador="Elige el tipo de espacio" buscar-marcador="Buscar categoría…" />
            <small v-if="puesto">Se llenó al elegir el espacio en el plano. Para cambiarlo, cierra y toca otro espacio.</small>
            <small v-else>Es el tipo de espacio que quieres (no tu rubro). Te asignaremos uno libre de esa categoría.</small>
          </div>
        </fieldset>

        <fieldset v-if="necesitaModo">
          <legend>4. Pago</legend>
          <div class="modos">
            <label v-if="hayComprobante" class="modo" :class="{ on: form.modoPago === 'COMPROBANTE' }">
              <input v-model="form.modoPago" type="radio" value="COMPROBANTE" />
              <span><b>{{ pago.qrUrl ? '📱 Pago con QR' : '🏦 Pago por transferencia' }}</b>
                <small>{{ pago.qrUrl ? 'Escanea el QR desde tu banco y adjunta el comprobante.' : 'Transfiere a la cuenta y adjunta el comprobante.' }}</small></span>
            </label>
            <label v-if="hayOficina" class="modo" :class="{ on: form.modoPago === 'OFICINA' }">
              <input v-model="form.modoPago" type="radio" value="OFICINA" />
              <span><b>🏢 Pago en oficinas</b><small>Te apartamos el espacio y pagas en persona.</small></span>
            </label>
          </div>
          <div v-if="form.modoPago === 'COMPROBANTE'" class="datos-pago">
            <div v-if="pago.qrUrl" class="qr-caja">
              <img :src="urlApi(pago.qrUrl)" alt="QR de pago" class="qr" />
              <a class="guardar-qr" :href="urlApi(pago.qrUrl)" download="qr-de-pago" target="_blank" rel="noopener">⬇ Guardar QR</a>
            </div>
            <ol class="pasos-pago">
              <li v-if="pago.qrUrl">Escanea el QR con la app de tu banco o guárdalo y ábrelo desde ahí.</li>
              <li v-else>Transfiere a la cuenta de abajo desde tu banco.</li>
              <li>Paga <b>{{ puesto ? bs(puesto.precio) : 'el monto de tu espacio' }}</b>.</li>
              <li>Toma una captura o foto del comprobante y adjúntala aquí.</li>
            </ol>
            <dl>
              <div v-if="puesto"><dt>Monto</dt><dd class="precio">{{ bs(puesto.precio) }}</dd></div>
              <div v-if="pago.banco"><dt>Banco</dt><dd>{{ pago.banco }}</dd></div>
              <div v-if="pago.cuenta"><dt>Cuenta</dt><dd class="mono">{{ pago.cuenta }}</dd></div>
              <div v-if="pago.titular"><dt>Titular</dt><dd>{{ pago.titular }}</dd></div>
              <div v-if="pago.instrucciones" class="ancho"><dt>Indicaciones</dt><dd>{{ pago.instrucciones }}</dd></div>
            </dl>
            <label class="archivo" :class="{ listo: form.comprobante }">
              <input id="i-comprobante" type="file" accept="image/png,image/jpeg,image/webp,application/pdf" @change="elegirArchivo" />
              <span>{{ form.comprobante ? `✓ ${form.comprobante.name}` : '📷 Adjuntar comprobante (foto o PDF)' }}</span>
            </label>
          </div>
          <div v-else-if="form.modoPago === 'OFICINA'" class="datos-pago oficina">
            <p><b>Dónde pagar:</b> {{ pago.oficina }}</p>
          </div>
        </fieldset>

        <p class="nota">Revisaremos tu solicitud y te escribiremos por WhatsApp para confirmar tu espacio.
          <template v-if="puesto"> Mientras tanto, el espacio {{ puesto.codigo }} queda apartado a tu nombre y nadie más podrá pedirlo.</template></p>
        <p v-if="mensaje" class="aviso-error" role="alert">{{ mensaje }}</p>
      </form>

      <template #pie>
        <button type="button" class="pub-boton secundario" :disabled="enviando" @click="formAbierto = false">{{ puesto ? 'Elegir otro' : 'Cancelar' }}</button>
        <button type="submit" form="form-interes" class="pub-boton" :disabled="!formListo || enviando">{{ enviando ? 'Enviando…' : 'Enviar solicitud' }}</button>
      </template>
    </ModalPublico>

    <ModalPublico v-if="enviado" titulo="¡Solicitud enviada!" @cerrar="enviado = null">
      <div class="exito">
        <span class="exito-icono">✓</span>
        <p>Recibimos tu solicitud{{ enviado.puesto ? ` del espacio ${enviado.puesto.codigo} (${enviado.puesto.categoria})` : enviado.categoria ? ` para ${enviado.categoria.nombre}` : '' }}.</p>
        <ol>
          <li>Revisamos tus datos{{ enviado.modoPago === 'COMPROBANTE' ? ' y tu comprobante' : '' }}.</li>
          <li>Te escribimos por WhatsApp al <b>{{ enviado.celular }}</b> para confirmar.</li>
          <li v-if="enviado.modoPago === 'OFICINA'">Pasas por nuestras oficinas a pagar: {{ pago.oficina }}</li>
        </ol>
      </div>
      <template #pie>
        <a v-if="contacto" class="pub-boton whatsapp" :href="enlaceWhatsApp(contacto, mensajeWhatsApp)" target="_blank" rel="noopener">Escribir por WhatsApp</a>
        <button type="button" class="pub-boton secundario" @click="enviado = null">Listo</button>
      </template>
    </ModalPublico>

    <ModalPublico v-if="infoAbierta && actividad" :titulo="actividad.nombre" @cerrar="infoAbierta = false">
      <img v-if="actividad.portadaUrl" class="portada" :src="urlApi(actividad.portadaUrl)" alt="" />
      <p v-if="actividad.descripcion" class="texto-largo">{{ actividad.descripcion }}</p>
      <dl class="info">
        <div v-if="actividad.lugar"><dt>Lugar</dt><dd>{{ actividad.lugar }}</dd></div>
        <div v-if="actividad.fechasTexto"><dt>Fechas</dt><dd>{{ actividad.fechasTexto }}</dd></div>
        <div v-if="contacto"><dt>Consultas</dt><dd>{{ contacto }}</dd></div>
        <div v-if="pago.oficina"><dt>Oficinas</dt><dd>{{ pago.oficina }}</dd></div>
      </dl>
      <div class="lista-cat">
        <div v-for="c in categorias" :key="c.id" :style="{ '--c': c.color || '#8f6dff' }"><i></i><span>{{ c.nombre }}</span>
          <small>{{ c.precioBase ? `Desde ${bs(c.precioBase)} · ` : '' }}{{ c.libres }}/{{ c.total }} libres</small></div>
      </div>
    </ModalPublico>
  </div>
</template>

<style scoped>
.pagina {
  height: 100vh; height: 100dvh; display: grid; grid-template-rows: auto minmax(0, 1fr); grid-template-columns: minmax(0, 1fr); max-width: 1320px; margin: 0 auto;
  padding: 0 max(16px, env(safe-area-inset-right, 0px)) 0 max(16px, env(safe-area-inset-left, 0px)); box-sizing: border-box;
}
.barra { display: flex; align-items: center; gap: 12px; padding: calc(12px + env(safe-area-inset-top, 0px)) 0 10px; }
.titulo { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.titulo h1 { margin: 0; font-size: clamp(1.15rem, 2.6vw, 1.7rem); line-height: 1.1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cuando { color: var(--pub-suave); font-size: 0.85rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.marca { font-weight: 700; color: var(--pub-suave); }
.acciones-barra { display: flex; gap: 8px; }
.estado { text-align: center; color: var(--pub-suave); margin: 4rem 0; }
.estado a { color: var(--pub-acento-claro); }
.contenido { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; min-height: 0; padding-bottom: calc(14px + env(safe-area-inset-bottom, 0px)); }
.zona-plano { display: flex; flex-direction: column; gap: 8px; min-height: 0; min-width: 0; }
.resumen-plano { display: flex; justify-content: space-between; align-items: center; gap: 10px; font-size: 0.88rem; }
.solo-movil { display: none; }
.resumen-plano b { color: var(--pub-ok); font-size: 1.05rem; }
.filtros { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 2px; scrollbar-width: thin; }
.filtro {
  flex: none; display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border-radius: 999px; cursor: pointer;
  border: 1px solid var(--pub-linea); background: var(--pub-panel); color: var(--pub-texto); font: 600 0.82rem 'Public Sans', sans-serif;
}
.filtro i { width: 10px; height: 10px; border-radius: 50%; background: var(--c); }
.filtro small { color: var(--pub-suave); font-weight: 700; }
.filtro.on { border-color: var(--c, var(--pub-acento)); background: color-mix(in srgb, var(--c, var(--pub-acento)) 22%, var(--pub-panel)); }
.filtro.agotada { opacity: 0.55; }
.marco-plano { flex: 1; min-height: 0; display: flex; flex-direction: column; border-radius: 16px; overflow: hidden; border: 1px solid var(--pub-linea); }
.marco-plano :deep(.viewport) { background: #e9ebf5; border: 0; }
.marco-plano :deep(.controls button) { color: #111827; }
.plano { position: relative; width: 100%; background: #f4f5fb; }
.plano img { width: 100%; height: auto; display: block; pointer-events: none; user-select: none; }
.pin {
  position: absolute; z-index: 1; width: 100px; height: var(--alto, 100px); padding: 0; border: 0; cursor: pointer;
  transform-origin: 0 0; transform: scale(calc(var(--pin, 20) / 100)) rotate(var(--giro, 0deg)) translate(-50%, -50%);
  background: var(--c); box-shadow: 0 0 0 6px rgba(255, 255, 255, 0.9); transition: opacity 0.2s, filter 0.15s;
}
.pin:hover { filter: brightness(1.12); z-index: 3; }
.pin.est-S, .pin.est-T { background: repeating-linear-gradient(45deg, #ffb347 0 14px, #e58f1c 14px 28px); cursor: not-allowed; }
.pin.est-O, .pin.est-R { background: #59607a; cursor: not-allowed; }
.pin.est-X { background: #9aa0b5; opacity: 0.55; cursor: not-allowed; }
.pin.apagado { opacity: 0.18; }
.pin.brilla { animation: brillar 1.2s ease-out 3; }
@keyframes brillar { 0% { box-shadow: 0 0 0 6px #fff, 0 0 0 6px rgba(63, 209, 143, 0.9); } 100% { box-shadow: 0 0 0 6px #fff, 0 0 0 60px rgba(63, 209, 143, 0); } }
.forma-cuadrado { border-radius: 10%; }
.forma-rectangulo { border-radius: 6%; }
.forma-circulo { border-radius: 50%; }
.forma-triangulo { clip-path: polygon(50% 0%, 100% 100%, 0% 100%); }
.forma-hexagono { clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%); }
.num {
  display: none; position: absolute; inset: 0; align-items: center; justify-content: center; pointer-events: none;
  transform: rotate(calc(-1 * var(--giro, 0deg))); font: 700 46px/1 system-ui, sans-serif; color: #fff;
  text-shadow: 0 2px 4px rgba(2, 6, 23, 0.9);
}
.marco-plano :deep(.world.detalle) .num { display: flex; }
.leyenda { display: flex; gap: 14px; flex-wrap: wrap; font-size: 0.78rem; color: var(--pub-suave); }
.leyenda i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; vertical-align: -1px; }
.leyenda .l { background: linear-gradient(135deg, #3fd18f, #8f6dff); } .leyenda .s { background: #ffb347; } .leyenda .o { background: #59607a; }
.pista { margin: 0; text-align: center; color: var(--pub-suave); font-size: 0.82rem; }
.tarjetas { flex: 1; min-height: 0; overflow-y: auto; display: grid; gap: 12px; align-content: start; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); }
.tarjeta { background: var(--pub-panel); border: 1px solid var(--pub-linea); border-top: 4px solid var(--c); border-radius: 16px; padding: 14px; display: flex; flex-direction: column; gap: 8px; }
.tarjeta-cab { display: flex; align-items: center; gap: 8px; }
.tarjeta-cab i { width: 10px; height: 10px; border-radius: 50%; background: var(--c); }
.tarjeta p { margin: 0; }
.tarjeta-pie { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; margin-top: auto; }
.tarjeta .pub-boton { align-self: flex-start; }
.tenue { color: var(--pub-suave); font-size: 0.88rem; }
.lateral {
  display: flex; flex-direction: column; gap: 14px; min-height: 0; overflow-y: auto; padding: 16px; border-radius: 18px;
  background: rgba(21, 26, 46, 0.78); border: 1px solid var(--pub-linea);
}
.portada { width: 100%; max-height: 160px; object-fit: cover; border-radius: 12px; }
.descripcion { margin: 0; color: var(--pub-suave); white-space: pre-line; display: -webkit-box; -webkit-line-clamp: 6; line-clamp: 6; -webkit-box-orient: vertical; overflow: hidden; }
.pasos ol { margin: 8px 0 0; padding-left: 20px; display: flex; flex-direction: column; gap: 6px; color: var(--pub-suave); font-size: 0.9rem; }
.pasos b { color: var(--pub-texto); }
.lateral .pub-boton { margin-top: auto; }
.ficha-cab { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.ficha-cab h2 { margin: 0; font-size: 1.6rem; line-height: 1; }
.resumen { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 12px 14px; border-radius: 14px; background: var(--pub-panel-2); }
.resumen span, .datos-pago dt, .info dt { display: block; font-size: 0.72rem; color: var(--pub-suave); text-transform: uppercase; letter-spacing: 0.06em; }
.resumen .ancho { grid-column: 1 / -1; }
.precio { font: 800 1.3rem var(--pub-titulo); color: var(--pub-acento-claro); }
.formulario { display: flex; flex-direction: column; gap: 14px; }
fieldset { border: 0; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
legend { font: 800 0.95rem var(--pub-titulo); margin-bottom: 8px; color: var(--pub-acento-claro); }
.campos { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.campos .ancho { grid-column: 1 / -1; }
.pub-campo small { color: var(--pub-suave); font-size: 0.76rem; }
.sugerencias { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 2px; }
.sugerencias button { border: 1px solid var(--pub-linea); background: transparent; color: var(--pub-suave); border-radius: 999px; padding: 4px 10px; font: 600 0.76rem 'Public Sans', sans-serif; cursor: pointer; }
.sugerencias button.on, .sugerencias button:hover { border-color: var(--pub-acento-claro); color: var(--pub-texto); }
.modos { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.modo { display: flex; gap: 10px; align-items: flex-start; padding: 12px; border-radius: 12px; border: 1px solid var(--pub-linea); cursor: pointer; }
.modo.on { border-color: var(--pub-acento); background: color-mix(in srgb, var(--pub-acento) 12%, transparent); }
.modo input { accent-color: var(--pub-acento); margin-top: 3px; }
.modo span { display: flex; flex-direction: column; gap: 2px; }
.modo small { color: var(--pub-suave); font-size: 0.78rem; }
.datos-pago { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 12px; padding: 12px; border-radius: 14px; background: var(--pub-panel-2); align-items: start; }
.qr-caja { grid-row: span 2; display: flex; flex-direction: column; align-items: center; gap: 6px; }
.datos-pago .qr { width: 170px; height: 170px; object-fit: contain; background: #fff; border-radius: 10px; padding: 6px; box-sizing: border-box; }
.guardar-qr { color: var(--pub-acento-claro); font-weight: 700; font-size: 0.85rem; text-decoration: none; }
.pasos-pago { margin: 0; padding-left: 18px; display: flex; flex-direction: column; gap: 4px; font-size: 0.86rem; color: var(--pub-suave); }
.pasos-pago b { color: var(--pub-texto); }
.datos-pago dl { margin: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.datos-pago dd, .info dd { margin: 2px 0 0; font-weight: 600; overflow-wrap: anywhere; }
.datos-pago .ancho { grid-column: 1 / -1; }
.mono { font-family: var(--pub-mono); }
.oficina { grid-template-columns: 1fr; }
.oficina p { margin: 0; }
.archivo { grid-column: 1 / -1; display: flex; align-items: center; justify-content: center; min-height: 48px; border: 2px dashed var(--pub-linea); border-radius: 12px; cursor: pointer; font-weight: 700; padding: 0 12px; text-align: center; }
.archivo input { display: none; }
.archivo.listo { border-color: var(--pub-ok); color: var(--pub-ok); border-style: solid; }
.nota { margin: 0; font-size: 0.84rem; color: var(--pub-suave); }
.aviso-error { margin: 0; padding: 10px 14px; border-radius: 12px; background: color-mix(in srgb, var(--pub-error) 14%, transparent); color: var(--pub-error); font-size: 0.9rem; }
.exito { display: flex; flex-direction: column; align-items: center; gap: 10px; text-align: center; }
.exito-icono { width: 64px; height: 64px; border-radius: 50%; display: grid; place-items: center; font-size: 2rem; background: color-mix(in srgb, var(--pub-ok) 20%, transparent); color: var(--pub-ok); animation: pub-entrar 0.3s ease-out; }
.exito p { margin: 0; }
.exito ol { margin: 0; text-align: left; padding-left: 20px; color: var(--pub-suave); display: flex; flex-direction: column; gap: 6px; }
.texto-largo { margin: 0; white-space: pre-line; color: var(--pub-suave); }
.info { margin: 0; display: flex; flex-direction: column; gap: 10px; }
.lista-cat { display: flex; flex-direction: column; gap: 8px; }
.lista-cat div { display: grid; grid-template-columns: auto 1fr auto; gap: 8px; align-items: center; font-size: 0.9rem; }
.lista-cat i { width: 10px; height: 10px; border-radius: 50%; background: var(--c); }
.lista-cat small { color: var(--pub-suave); }
@media (max-width: 900px) {
  .contenido { grid-template-columns: minmax(0, 1fr); }
  .lateral { display: none; }
  .solo-movil { display: inline-flex; }
}
@media (max-width: 560px) {
  .barra { flex-wrap: wrap; row-gap: 6px; }
  .titulo { order: 3; flex-basis: 100%; }
  .acciones-barra { margin-left: auto; }
  .campos, .modos { grid-template-columns: 1fr; }
  .datos-pago { grid-template-columns: 1fr; justify-items: center; }
  .qr-caja { grid-row: auto; }
  .datos-pago dl { width: 100%; }
}
</style>
