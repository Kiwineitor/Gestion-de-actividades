<script setup>
import { computed } from 'vue';

const props = defineProps({
  texto: { type: Object, required: true },
  seleccionado: { type: Boolean, default: false },
});

const contorno = (color, g) => {
  const d = `${g}em`;
  return [`${d} ${d}`, `-${d} ${d}`, `${d} -${d}`, `-${d} -${d}`]
    .map((o) => `${o} 0 ${color}`).join(', ');
};

const estilo = computed(() => {
  const t = props.texto;
  const base = {
    left: `${(t.mapaX ?? 0.5) * 100}%`,
    top: `${(t.mapaY ?? 0.5) * 100}%`,
    fontSize: `calc(var(--mundo, 1200) * ${t.tamano ?? 0.02} * 1px)`,
    color: t.color || '#111827',
    transform: `translate(-50%, -50%) rotate(${t.rotacion ?? 0}deg)`,
  };
  if (t.fondo) {
    return {
      ...base,
      width: `${(t.ancho ?? 0.16) * 100}%`,
      height: `${(t.alto ?? 0.08) * 100}%`,
      '--fondo': t.fondo,
    };
  }
  return { ...base, textShadow: contorno(t.colorBorde || '#ffffff', t.grosorBorde ?? 0.15) };
});
</script>

<template>
  <div class="rotulo" :class="{ seleccionado, 'con-recuadro': Boolean(texto.fondo) }" :style="estilo">{{ texto.contenido }}</div>
</template>

<style scoped>
.rotulo {
  position: absolute;
  z-index: 2;
  pointer-events: none;
  user-select: none;
  white-space: nowrap;
  font-weight: 800;
  line-height: 1;
  letter-spacing: 0.02em;
}
.rotulo.con-recuadro {
  z-index: 0;
  display: grid; place-items: center; text-align: center; white-space: normal; overflow: hidden;
  box-sizing: border-box; padding: 0.2em; line-height: 1.1; word-break: break-word;
  background: color-mix(in srgb, var(--fondo) 28%, transparent);
  border: 0.15em solid color-mix(in srgb, var(--fondo) 90%, transparent);
  border-radius: 0.5em;
}
.rotulo.seleccionado {
  outline: 2px dashed var(--acento);
  outline-offset: 0.15em;
}
</style>
