<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { url as urlApi } from '../config.js';
import { alCambiarPublico } from '../ui/canalPublico';
import { enlaceWhatsApp } from '../ui/tokenPublico';
import { guardarCompra } from '../ui/comprasGuardadas';

const route = useRoute();
const codigo = String(route.query.compra || '').trim().toUpperCase();
const compra = ref(null);
const error = ref('');
const version = ref(Date.now());
let soltar = null;
let sondeo = null;

const ESTADOS = {
  REVISION: { t: 'Estamos revisando tu pago', d: 'Te avisaremos por WhatsApp. Esta página se actualiza sola.', c: 'revision', i: '⏳' },
  APROBADA: { t: '¡Tus entradas están listas!', d: 'Muestra el QR de cada entrada en la puerta. Cada QR permite un ingreso.', c: 'aprobada', i: '🎉' },
  RECHAZADA: { t: 'No pudimos aprobar tu compra', d: '', c: 'rechazada', i: '⚠️' },
  ANULADA: { t: 'Compra anulada', d: '', c: 'rechazada', i: '🚫' },
};
const estado = computed(() => ESTADOS[compra.value?.estado] || ESTADOS.REVISION);
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;
const urlPase = (p) => urlApi(`/api/publico/entradas/compra/${encodeURIComponent(codigo)}/pase/${p.numero}.png?v=${version.value}`);
const whatsapp = computed(() => enlaceWhatsApp(compra.value?.contacto, `Hola, consulto por mi compra de entradas ${codigo}`));

async function cargar() {
  try {
    const r = await fetch(urlApi(`/api/publico/entradas/compra/${encodeURIComponent(codigo)}`));
    if (r.status === 404) { error.value = 'No encontramos una compra con ese código.'; return; }
    if (!r.ok) throw new Error();
    compra.value = await r.json();
    error.value = '';
    guardarCompra(codigo, compra.value.actividad);
  } catch {
    if (!compra.value) error.value = 'No se pudo cargar tu compra. Revisa tu conexión.';
  }
}

onMounted(() => {
  cargar();
  soltar = alCambiarPublico((m) => { if (m?.tipo === 'entradas') cargar(); }, { reconectado: cargar });
  sondeo = setInterval(() => { if (compra.value?.estado === 'REVISION') cargar(); }, 30000);
});
onUnmounted(() => { soltar?.(); clearInterval(sondeo); });
</script>

<template>
  <div class="pagina">
    <header class="barra">
      <router-link :to="compra ? { path: '/', query: { v: 'actividad', edicion: compra.edicion } } : '/'" class="pub-volver">← Volver</router-link>
      <div class="titulo"><h1>Mi compra</h1><span class="codigo">{{ codigo }}</span></div>
    </header>

    <p v-if="error" class="estado-pagina">{{ error }} <router-link to="/">Ir al inicio</router-link></p>
    <p v-else-if="!compra" class="estado-pagina">Cargando…</p>

    <main v-else class="contenido">
      <section class="estado" :class="estado.c">
        <span class="icono">{{ estado.i }}</span>
        <div>
          <h2>{{ estado.t }}</h2>
          <p>{{ compra.motivo ? `Motivo: ${compra.motivo}` : estado.d }}</p>
        </div>
      </section>

      <section class="tarjeta">
        <h3>{{ compra.actividad }}</h3>
        <span class="tenue">{{ [compra.fechas, compra.lugar].filter(Boolean).join(' · ') }}</span>
        <div v-for="d in compra.detalle" :key="d.nombre" class="linea">
          <span>{{ d.cantidad }} × {{ d.nombre }}</span><b>{{ bs(d.precio * d.cantidad) }}</b>
        </div>
        <div class="linea total"><span>Total</span><b>{{ bs(compra.total) }}</b></div>
        <span class="tenue">A nombre de {{ compra.nombre }} · guarda tu código <b>{{ codigo }}</b> para consultar tu compra desde el inicio.</span>
      </section>

      <section v-if="compra.pases.length" class="pases">
        <div class="acciones">
          <a class="pub-boton" :href="urlApi(`/api/publico/entradas/compra/${codigo}/pases.pdf`)" target="_blank" rel="noopener">⬇ Descargar entradas (PDF)</a>
          <a class="pub-boton secundario" :href="urlApi(`/api/publico/entradas/compra/${codigo}/recibo.pdf`)" target="_blank" rel="noopener">🧾 Recibo</a>
        </div>
        <p class="consejo">📲 Toca <b>Guardar</b> en cada entrada para tenerla en tu galería: así la muestras en la puerta aunque no tengas internet.</p>
        <div class="rejilla">
          <article v-for="p in compra.pases" :key="p.codigo" class="pase" :class="{ dentro: p.dentro }">
            <img :src="urlPase(p)" :alt="`Entrada ${p.numero} de ${compra.pases.length}`" loading="lazy" />
            <div class="pie-pase">
              <span><b>{{ p.tipo }}</b><small>Entrada {{ p.numero }} de {{ compra.pases.length }}</small></span>
              <span v-if="p.dentro" class="chip">Dentro</span>
              <a class="guardar" :href="urlPase(p)" :download="`entrada-${codigo}-${p.numero}.png`" target="_blank" rel="noopener">⬇ Guardar</a>
            </div>
          </article>
        </div>
      </section>

      <a v-if="whatsapp" class="pub-boton whatsapp" :href="whatsapp" target="_blank" rel="noopener">Consultas por WhatsApp</a>
    </main>
  </div>
</template>

<style scoped>
.pagina { max-width: 760px; margin: 0 auto; padding: 0 16px 32px; box-sizing: border-box; }
.barra { display: flex; align-items: center; gap: 12px; padding: calc(12px + env(safe-area-inset-top, 0px)) 0 14px; }
.titulo { display: flex; flex-direction: column; }
.titulo h1 { margin: 0; font-size: 1.4rem; }
.codigo { font-family: var(--pub-mono); color: var(--pub-suave); font-size: 0.85rem; }
.estado-pagina { text-align: center; color: var(--pub-suave); margin: 4rem 0; }
.contenido { display: flex; flex-direction: column; gap: 14px; }
.estado { display: flex; gap: 14px; align-items: center; padding: 16px; border-radius: 18px; background: var(--pub-panel-2); border: 1px solid var(--pub-linea); }
.estado .icono { font-size: 2.2rem; }
.estado h2 { margin: 0; font-size: 1.15rem; }
.estado p { margin: 4px 0 0; color: var(--pub-suave); }
.estado.aprobada { border-color: var(--pub-ok); }
.estado.rechazada { border-color: var(--pub-error); }
.tarjeta { display: flex; flex-direction: column; gap: 8px; padding: 16px; border-radius: 18px; background: var(--pub-panel-2); }
.tarjeta h3 { margin: 0; }
.tenue { color: var(--pub-suave); font-size: 0.88rem; }
.linea { display: flex; justify-content: space-between; gap: 10px; font-variant-numeric: tabular-nums; }
.linea.total { border-top: 1px solid var(--pub-linea); padding-top: 8px; font-size: 1.1rem; }
.acciones { display: flex; gap: 10px; flex-wrap: wrap; }
.consejo { margin: 10px 0 0; font-size: 0.86rem; color: var(--pub-suave); }
.rejilla { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 230px), 1fr)); gap: 14px; margin-top: 12px; }
.pase { display: flex; flex-direction: column; gap: 8px; padding: 8px; border-radius: 20px; background: var(--pub-panel-2); border: 1px solid var(--pub-linea); animation: entrar 0.4s ease both; }
.pase img { width: 100%; aspect-ratio: 9 / 16; object-fit: cover; border-radius: 14px; background: #1e1b4b; }
.pie-pase { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 0 4px 4px; }
.pie-pase span { display: flex; flex-direction: column; }
.pie-pase small { color: var(--pub-suave); font-size: 0.78rem; }
.guardar { color: var(--pub-acento-claro, #a78bfa); font-weight: 800; text-decoration: none; white-space: nowrap; }
.pase.dentro { outline: 3px solid var(--pub-ok); }
@keyframes entrar { from { opacity: 0; transform: translateY(10px) scale(0.98); } to { opacity: 1; transform: none; } }
.chip { background: #dcfce7; color: #166534; border-radius: 999px; padding: 2px 10px; font-size: 0.75rem; font-weight: 700; }
</style>
