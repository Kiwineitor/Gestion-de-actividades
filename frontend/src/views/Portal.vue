<script setup>
import { ref, computed, watch, onMounted, defineAsyncComponent } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { url as urlApi } from '../config.js';
import { marca, iniciales, cargarMarca } from '../ui/marca';

const SECCIONES = {
  credencial: { vista: defineAsyncComponent(() => import('./CredencialPublica.vue')), titulo: 'Credencial' },
  reserva: { vista: defineAsyncComponent(() => import('./MiReserva.vue')), titulo: 'Mi reserva' },
  evento: { vista: defineAsyncComponent(() => import('./ReservaMesas.vue')), titulo: 'Reserva de mesas' },
  actividad: { vista: defineAsyncComponent(() => import('./ActividadPublica.vue')), titulo: 'Actividad' },
};

const route = useRoute();
const router = useRouter();

const seccion = computed(() => {
  const q = route.query;
  if (q.credencial) return SECCIONES.credencial;
  if (q.reserva) return SECCIONES.reserva;
  if (q.evento) return SECCIONES.evento;
  if (q.v === 'actividad' || q.v === 'feria') return SECCIONES.actividad;
  return null;
});

const eventos = ref([]);
const feria = ref(null);
const cargando = ref(true);
const codigo = ref('');
const aviso = ref('');

watch([seccion, () => marca.nombre], ([s]) => {
  document.title = s ? `${s.titulo} · ${marca.nombre}` : marca.nombre;
}, { immediate: true });
cargarMarca();

async function cargar() {
  cargando.value = true;
  const [ev, fe] = await Promise.allSettled([
    fetch(urlApi('/api/publico/mesas')).then((r) => (r.ok ? r.json() : [])),
    fetch(urlApi('/api/publico/feria')).then((r) => (r.ok ? r.json() : null)),
  ]);
  eventos.value = ev.status === 'fulfilled' && Array.isArray(ev.value) ? ev.value : [];
  feria.value = fe.status === 'fulfilled' ? fe.value?.edicion ?? null : null;
  cargando.value = false;
}

function consultar() {
  const c = codigo.value.trim().toUpperCase();
  const m = c.match(/MSA-[A-Z0-9]+/);
  if (!m) { aviso.value = 'Escribe el código que recibiste al reservar, por ejemplo MSA-AB12CD34EF.'; return; }
  aviso.value = '';
  router.push({ path: '/', query: { reserva: m[0] } });
}

onMounted(() => { if (!seccion.value) cargar(); });
watch(seccion, (s) => { if (!s && !eventos.value.length && !feria.value) cargar(); });
</script>

<template>
  <component :is="seccion.vista" v-if="seccion" :key="route.fullPath" />

  <div v-else class="portal">
    <header class="cabecera">
      <div class="marca">
        <img v-if="marca.logoUrl" :src="marca.logoUrl" :alt="marca.nombre" class="logo-img" />
        <span v-else class="logo">{{ iniciales }}</span>
        <span>{{ marca.nombre }}</span>
      </div>
      <router-link to="/admin" class="personal">Ingreso del personal</router-link>
    </header>

    <section class="hero">
      <h1>{{ marca.lema || 'Eventos, ferias y reservas en un solo lugar' }}</h1>
      <p>Elige un evento para reservar tu mesa, revisa una feria o consulta el estado de tu reserva.</p>
    </section>

    <section class="bloque">
      <h2>Próximos eventos</h2>
      <p v-if="cargando" class="tenue">Cargando…</p>
      <p v-else-if="!eventos.length" class="tenue">No hay eventos con reserva abiertos en este momento.</p>
      <div v-else class="rejilla">
        <router-link v-for="e in eventos" :key="e.id" :to="{ path: '/', query: { evento: e.id } }" class="tarjeta">
          <span class="lugar">{{ e.localNombre }}</span>
          <strong>{{ e.nombre }}</strong>
          <span class="fecha">{{ e.fechaTexto }}</span>
          <span class="accion">Reservar mesa →</span>
        </router-link>
      </div>
    </section>

    <section v-if="feria" class="bloque">
      <h2>Actividades</h2>
      <div class="rejilla">
        <router-link :to="{ path: '/', query: { v: 'actividad' } }" class="tarjeta">
          <span class="lugar">{{ feria.lugar || 'Actividad vigente' }}</span>
          <strong>{{ feria.nombre }}</strong>
          <span v-if="feria.fechasTexto" class="fecha">{{ feria.fechasTexto }}</span>
          <span class="accion">Ver información →</span>
        </router-link>
      </div>
    </section>

    <section class="bloque">
      <h2>Consulta tu reserva</h2>
      <form class="consulta" @submit.prevent="consultar">
        <input v-model="codigo" placeholder="Código de reserva (MSA-…)" autocomplete="off" aria-label="Código de reserva" />
        <button type="submit">Consultar</button>
      </form>
      <p v-if="aviso" class="aviso">{{ aviso }}</p>
    </section>

    <footer class="pie">
      <span>{{ marca.nombre }}</span>
      <span v-if="marca.contacto">Consultas: {{ marca.contacto }}</span>
    </footer>
  </div>
</template>

<style scoped>
.portal {
  min-height: 100vh; min-height: 100dvh;
  background: radial-gradient(1200px 500px at 50% -10%, #241b4d 0%, #0d0f1c 60%);
  color: #e8e9f3; font-family: inherit;
  padding: 0 1rem 2rem;
}
.cabecera {
  max-width: 1040px; margin: 0 auto; display: flex; align-items: center; justify-content: space-between;
  padding: calc(1rem + env(safe-area-inset-top, 0px)) 0 1rem;
}
.marca { display: flex; align-items: center; gap: 0.6rem; font-weight: 700; }
.logo {
  width: 2.1rem; height: 2.1rem; border-radius: 0.6rem; display: grid; place-items: center;
  background: #7c5cff; color: #fff; font-size: 0.85rem;
}
.personal {
  color: #c9c3ff; text-decoration: none; border: 1px solid #3a3570; padding: 0.45rem 0.85rem;
  border-radius: 999px; font-size: 0.9rem;
}
.personal:hover { background: #1d1a3a; }
.hero { max-width: 1040px; margin: 2.5rem auto 1.5rem; }
.hero h1 { font-size: clamp(1.7rem, 4vw, 2.6rem); margin: 0 0 0.6rem; line-height: 1.15; }
.hero p { color: #a9abc6; margin: 0; max-width: 40rem; }
.bloque { max-width: 1040px; margin: 2rem auto 0; }
.bloque h2 { font-size: 1.1rem; margin: 0 0 0.9rem; color: #d7d4ff; }
.tenue { color: #8d8fae; }
.rejilla { display: grid; gap: 0.9rem; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); }
.tarjeta {
  display: flex; flex-direction: column; gap: 0.3rem; padding: 1.1rem 1.2rem; border-radius: 1rem;
  background: #161a2e; border: 1px solid #262b48; color: inherit; text-decoration: none;
  transition: border-color 0.15s, transform 0.15s;
}
.tarjeta:hover { border-color: #7c5cff; transform: translateY(-2px); }
.tarjeta strong { font-size: 1.15rem; }
.lugar { text-transform: uppercase; letter-spacing: 0.12em; font-size: 0.72rem; color: #a99bff; }
.fecha { color: #a9abc6; font-size: 0.9rem; }
.accion { margin-top: 0.4rem; color: #c9c3ff; font-weight: 600; font-size: 0.9rem; }
.consulta { display: flex; gap: 0.6rem; flex-wrap: wrap; max-width: 520px; }
.consulta input {
  flex: 1 1 220px; padding: 0.75rem 0.9rem; border-radius: 0.7rem; border: 1px solid #2c3152;
  background: #12152a; color: inherit; font: inherit; text-transform: uppercase;
}
.consulta button {
  padding: 0.75rem 1.2rem; border-radius: 0.7rem; border: 0; background: #7c5cff; color: #fff;
  font: inherit; font-weight: 600; cursor: pointer;
}
.aviso { color: #ffb86b; font-size: 0.9rem; }
.pie {
  max-width: 1040px; margin: 3rem auto 0; color: #6f7193; font-size: 0.85rem;
  display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap;
}
.logo-img { width: 2.1rem; height: 2.1rem; object-fit: contain; }
</style>
