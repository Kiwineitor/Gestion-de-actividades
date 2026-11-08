<script setup>
defineProps({
  documento: { type: Object, required: true },
  compacto: { type: Boolean, default: false },
});
</script>

<template>
  <article class="texto-legal" :class="{ compacto }">
    <header v-if="!compacto">
      <h1>{{ documento.titulo }}</h1>
      <p class="subtitulo">{{ documento.subtitulo }}</p>
    </header>
    <section v-for="s in documento.secciones" :key="s.titulo">
      <h2>{{ s.titulo }}</h2>
      <p v-for="p in s.parrafos || []" :key="p">{{ p }}</p>
      <ul v-if="s.lista"><li v-for="l in s.lista" :key="l">{{ l }}</li></ul>
    </section>
  </article>
</template>

<style scoped>
.texto-legal { display: flex; flex-direction: column; gap: 0.9rem; line-height: 1.55; }
.texto-legal h1 { margin: 0; font-size: 1.5rem; }
.subtitulo { margin: 0.2rem 0 0; opacity: 0.75; }
.texto-legal h2 { margin: 0 0 0.3rem; font-size: 1rem; }
.texto-legal p { margin: 0 0 0.4rem; }
.texto-legal ul { margin: 0; padding-left: 1.2rem; display: flex; flex-direction: column; gap: 0.3rem; }
.compacto { font-size: 0.9rem; gap: 0.7rem; }
.compacto h2 { font-size: 0.95rem; }
</style>
