<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { marca } from '../ui/marca';
import { terminosDeCompra, politicaDePrivacidad, terminosDeUso } from '../ui/legal';
import TextoLegal from '../components/TextoLegal.vue';

const route = useRoute();
const DOCUMENTOS = { terminos: terminosDeCompra, privacidad: politicaDePrivacidad, uso: terminosDeUso };
const documento = computed(() => (DOCUMENTOS[route.query.v] || terminosDeCompra)({ institucion: marca.nombre, contacto: marca.contacto }));
const otros = computed(() => [
  { v: 'terminos', t: 'Términos de compra' },
  { v: 'privacidad', t: 'Privacidad' },
  { v: 'uso', t: 'Uso del sistema (personal)' },
].filter((o) => o.v !== (route.query.v || 'terminos')));
</script>

<template>
  <div class="pagina-legal">
    <header class="barra">
      <router-link to="/" class="pub-volver">← Inicio</router-link>
      <nav class="otros"><router-link v-for="o in otros" :key="o.v" :to="{ path: '/', query: { v: o.v } }">{{ o.t }}</router-link></nav>
    </header>
    <TextoLegal :documento="documento" />
  </div>
</template>

<style scoped>
.pagina-legal { max-width: 820px; margin: 0 auto; padding: calc(12px + env(safe-area-inset-top, 0px)) 16px 40px; box-sizing: border-box; display: flex; flex-direction: column; gap: 18px; }
.barra { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.otros { display: flex; gap: 14px; flex-wrap: wrap; font-size: 0.88rem; }
.otros a { color: var(--pub-acento-claro, #a78bfa); font-weight: 700; text-decoration: none; }
</style>
