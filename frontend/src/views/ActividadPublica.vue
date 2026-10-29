<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { url as urlApi } from '../config.js';
import { marca, cargarMarca } from '../ui/marca';

const datos = ref(null);
const error = ref('');
const planoAbierto = ref(false);
const form = reactive({ nombreCompleto: '', celular: '', empresa: '', rubro: '', categoriaId: '' });
const enviando = ref(false);
const respuesta = ref(null);

const actividad = computed(() => datos.value?.edicion ?? null);
const categorias = computed(() => {
  const stats = new Map((datos.value?.estadisticasPorCategoria ?? []).map((s) => [s.categoriaId, s]));
  return (datos.value?.categorias ?? []).map((c) => ({ ...c, stats: stats.get(c.id) }));
});
const stats = computed(() => datos.value?.estadisticas ?? null);
const planoUrl = computed(() => {
  const p = datos.value?.plano;
  return p?.propio && p.url ? urlApi(p.url) : null;
});
const portada = computed(() => (actividad.value?.portadaUrl ? urlApi(actividad.value.portadaUrl) : null));
const formListo = computed(() => form.nombreCompleto.trim() && form.empresa.trim() && form.rubro.trim()
  && /^\+?\d{7,15}$/.test(form.celular.trim()) && form.categoriaId);
const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;

async function cargar() {
  try {
    const r = await fetch(urlApi('/api/publico/feria'));
    if (!r.ok) throw new Error();
    datos.value = await r.json();
    if (!datos.value?.edicion) error.value = 'No hay una actividad publicada en este momento.';
    else document.title = `${datos.value.edicion.nombre} · ${marca.nombre}`;
  } catch {
    error.value = 'No se pudo cargar la información. Revisa tu conexión.';
  }
}

async function enviar() {
  if (!formListo.value || enviando.value) return;
  enviando.value = true;
  respuesta.value = null;
  try {
    const r = await fetch(urlApi('/api/publico/interesados-stand'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nombreCompleto: form.nombreCompleto.trim(),
        celular: form.celular.trim(),
        empresa: form.empresa.trim(),
        rubro: form.rubro.trim(),
        categoriaId: Number(form.categoriaId),
      }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(d.mensaje || 'No se pudo enviar tu solicitud.');
    respuesta.value = { ok: true, texto: 'Recibimos tu solicitud. Te contactaremos por celular.' };
    Object.assign(form, { nombreCompleto: '', celular: '', empresa: '', rubro: '', categoriaId: '' });
  } catch (e) {
    respuesta.value = { ok: false, texto: e.message || 'No se pudo enviar tu solicitud.' };
  } finally {
    enviando.value = false;
  }
}

onMounted(() => { cargarMarca(); cargar(); });
</script>

<template>
  <div class="pagina">
    <header class="cabecera">
      <router-link to="/" class="volver">← {{ marca.nombre }}</router-link>
      <router-link to="/admin" class="personal">Ingreso del personal</router-link>
    </header>

    <p v-if="error" class="estado">{{ error }}</p>
    <p v-else-if="!datos" class="estado">Cargando…</p>

    <template v-else-if="actividad">
      <section class="hero" :style="portada ? { backgroundImage: `linear-gradient(180deg, rgba(13,15,28,.35), #0d0f1c), url(${portada})` } : null">
        <div class="hero-txt">
          <span v-if="actividad.lugar" class="lugar">{{ actividad.lugar }}</span>
          <h1>{{ actividad.nombre }}</h1>
          <p v-if="actividad.fechasTexto" class="fechas">{{ actividad.fechasTexto }}</p>
          <p v-if="actividad.descripcion" class="descripcion">{{ actividad.descripcion }}</p>
          <div class="acciones">
            <a v-if="actividad.recibeInteresados && categorias.length" href="#participar" class="boton">Quiero participar</a>
            <button v-if="planoUrl" type="button" class="boton fantasma" @click="planoAbierto = true">Ver plano</button>
          </div>
        </div>
      </section>

      <section v-if="stats && stats.total" class="bloque cifras">
        <div><strong>{{ stats.total }}</strong><span>espacios</span></div>
        <div><strong>{{ stats.libres }}</strong><span>disponibles</span></div>
        <div><strong>{{ stats.porcentajeOcupacion }}%</strong><span>ocupado</span></div>
      </section>

      <section v-if="categorias.length" class="bloque">
        <h2>Espacios disponibles</h2>
        <div class="rejilla">
          <article v-for="c in categorias" :key="c.id" class="tarjeta" :style="{ '--c': c.color || '#7c5cff' }">
            <div class="tarjeta-cab">
              <span class="punto"></span>
              <strong>{{ c.nombre }}</strong>
            </div>
            <p v-if="c.descripcion" class="tenue">{{ c.descripcion }}</p>
            <p v-if="c.precioBase" class="precio">Desde {{ bs(c.precioBase) }}</p>
            <p v-if="c.stats" class="tenue">{{ c.stats.libres }} libres de {{ c.stats.total }}</p>
          </article>
        </div>
      </section>

      <section v-if="planoUrl" class="bloque">
        <h2>Plano</h2>
        <button type="button" class="plano" @click="planoAbierto = true">
          <img :src="planoUrl" :alt="`Plano de ${actividad.nombre}`" loading="lazy" />
        </button>
      </section>

      <section v-if="actividad.recibeInteresados && categorias.length" id="participar" class="bloque">
        <h2>Quiero participar</h2>
        <p class="tenue">Déjanos tus datos y te contactaremos para reservar tu espacio.</p>
        <form class="formulario" @submit.prevent="enviar">
          <label>Nombre completo<input v-model="form.nombreCompleto" autocomplete="name" /></label>
          <label>Celular<input v-model="form.celular" inputmode="tel" autocomplete="tel" placeholder="70000000" /></label>
          <label>Empresa o emprendimiento<input v-model="form.empresa" /></label>
          <label>Rubro<input v-model="form.rubro" placeholder="Comida, artesanía, servicios…" /></label>
          <label>Espacio que te interesa
            <select v-model="form.categoriaId">
              <option value="" disabled>Elige una opción</option>
              <option v-for="c in categorias" :key="c.id" :value="c.id">{{ c.nombre }}</option>
            </select>
          </label>
          <button type="submit" class="boton" :disabled="!formListo || enviando">{{ enviando ? 'Enviando…' : 'Enviar solicitud' }}</button>
          <p v-if="respuesta" :class="respuesta.ok ? 'ok' : 'mal'">{{ respuesta.texto }}</p>
        </form>
      </section>

      <footer class="pie">
        <span v-if="actividad.contacto">Consultas: {{ actividad.contacto }}</span>
        <span v-else-if="marca.contacto">Consultas: {{ marca.contacto }}</span>
        <span>{{ marca.nombre }}</span>
      </footer>
    </template>

    <div v-if="planoAbierto" class="visor" role="dialog" aria-modal="true" @click.self="planoAbierto = false">
      <button type="button" class="cerrar" aria-label="Cerrar" @click="planoAbierto = false">✕</button>
      <img :src="planoUrl" :alt="`Plano de ${actividad?.nombre}`" />
    </div>
  </div>
</template>

<style scoped>
.pagina {
  min-height: 100vh; min-height: 100dvh; background: #0d0f1c; color: #e8e9f3;
  padding: 0 1rem 2rem;
}
.cabecera {
  max-width: 1040px; margin: 0 auto; display: flex; justify-content: space-between; align-items: center;
  padding: calc(1rem + env(safe-area-inset-top, 0px)) 0 1rem; gap: 1rem;
}
.volver { color: #c9c3ff; text-decoration: none; font-weight: 600; }
.personal {
  color: #c9c3ff; text-decoration: none; border: 1px solid #3a3570; padding: 0.45rem 0.85rem;
  border-radius: 999px; font-size: 0.9rem; white-space: nowrap;
}
.estado { max-width: 1040px; margin: 4rem auto; text-align: center; color: #a9abc6; }
.hero {
  max-width: 1040px; margin: 0 auto; border-radius: 1.25rem; overflow: hidden;
  background: radial-gradient(900px 400px at 20% 0%, #2a1f5c 0%, #161a2e 70%);
  background-size: cover; background-position: center;
  min-height: 300px; display: flex; align-items: flex-end;
}
.hero-txt { padding: 2rem 1.5rem; max-width: 46rem; }
.lugar { text-transform: uppercase; letter-spacing: 0.14em; font-size: 0.75rem; color: #a99bff; }
.hero h1 { font-size: clamp(1.8rem, 5vw, 3rem); margin: 0.3rem 0 0.4rem; line-height: 1.1; }
.fechas { color: #d7d4ff; margin: 0 0 0.6rem; font-weight: 600; }
.descripcion { color: #c5c7dc; margin: 0; white-space: pre-line; }
.acciones { display: flex; gap: 0.6rem; flex-wrap: wrap; margin-top: 1.2rem; }
.boton {
  display: inline-block; padding: 0.75rem 1.2rem; border-radius: 0.75rem; border: 0; background: #7c5cff;
  color: #fff; font: inherit; font-weight: 600; text-decoration: none; cursor: pointer;
}
.boton:disabled { opacity: 0.45; cursor: not-allowed; }
.boton.fantasma { background: transparent; border: 1px solid #4a4390; color: #d7d4ff; }
.bloque { max-width: 1040px; margin: 2.2rem auto 0; }
.bloque h2 { font-size: 1.15rem; margin: 0 0 0.9rem; color: #d7d4ff; }
.cifras { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.8rem; }
.cifras div {
  background: #161a2e; border: 1px solid #262b48; border-radius: 1rem; padding: 1rem; text-align: center;
  display: flex; flex-direction: column;
}
.cifras strong { font-size: 1.6rem; }
.cifras span { color: #a9abc6; font-size: 0.85rem; }
.rejilla { display: grid; gap: 0.9rem; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); }
.tarjeta {
  background: #161a2e; border: 1px solid #262b48; border-top: 4px solid var(--c); border-radius: 1rem;
  padding: 1rem 1.1rem;
}
.tarjeta-cab { display: flex; align-items: center; gap: 0.5rem; }
.punto { width: 0.7rem; height: 0.7rem; border-radius: 50%; background: var(--c); flex: none; }
.tarjeta p { margin: 0.45rem 0 0; }
.precio { font-weight: 700; color: #fff; }
.tenue { color: #a9abc6; font-size: 0.92rem; }
.plano {
  display: block; width: 100%; padding: 0; border: 1px solid #262b48; border-radius: 1rem;
  overflow: hidden; background: #fff; cursor: zoom-in;
}
.plano img { display: block; width: 100%; height: auto; }
.formulario { display: grid; gap: 0.8rem; max-width: 560px; }
.formulario label { display: grid; gap: 0.35rem; font-size: 0.9rem; color: #c5c7dc; }
.formulario input, .formulario select {
  padding: 0.7rem 0.85rem; border-radius: 0.7rem; border: 1px solid #2c3152; background: #12152a;
  color: inherit; font: inherit;
}
.ok { color: #4ade80; }
.mal { color: #ffb86b; }
.pie {
  max-width: 1040px; margin: 3rem auto 0; display: flex; justify-content: space-between; gap: 1rem;
  flex-wrap: wrap; color: #6f7193; font-size: 0.85rem;
}
.visor {
  position: fixed; inset: 0; z-index: 50; background: rgba(5, 6, 12, 0.92);
  display: grid; place-items: center; padding: 1rem;
}
.visor img { max-width: 100%; max-height: 92vh; border-radius: 0.6rem; background: #fff; }
.cerrar {
  position: absolute; top: calc(0.8rem + env(safe-area-inset-top, 0px)); right: 0.8rem; width: 2.6rem; height: 2.6rem;
  border-radius: 50%; border: 0; background: #262b48; color: #fff; font-size: 1.1rem; cursor: pointer;
}
@media (max-width: 520px) {
  .cifras strong { font-size: 1.25rem; }
}
</style>
