<script setup>
const props = defineProps({
  texto: { type: Object, required: true },
  seleccionado: { type: Boolean, default: false },
});

const contorno = (color, g) => {
  const d = `${g}em`;
  return [`${d} ${d}`, `-${d} ${d}`, `${d} -${d}`, `-${d} -${d}`]
    .map((o) => `${o} 0 ${color}`).join(', ');
};
</script>

<template>
  <div
    class="rotulo"
    :class="{ seleccionado }"
    :style="{
      left: `calc(${(texto.mapaX ?? 0.5) * 100}% )`,
      top: `calc(${(texto.mapaY ?? 0.5) * 100}% )`,
      fontSize: `calc(var(--mundo, 1200) * ${texto.tamano ?? 0.02} * 1px)`,
      color: texto.color || '#111827',
      textShadow: contorno(texto.colorBorde || '#ffffff', texto.grosorBorde ?? 0.15),
      transform: `translate(-50%, -50%) rotate(${texto.rotacion ?? 0}deg)`,
    }"
  >{{ texto.contenido }}</div>
</template>

<style scoped>
.rotulo {
  position: absolute;
  pointer-events: none;
  user-select: none;
  white-space: nowrap;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0.02em;
}
.rotulo.seleccionado {
  outline: 2px dashed var(--acento);
  outline-offset: 0.15em;
}
</style>
