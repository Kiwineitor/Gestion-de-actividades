<script setup>
import { computed } from 'vue';

const props = defineProps({
  items: { type: Array, default: () => [] },
  vacio: { type: String, default: 'Sin datos todavía.' },
  formato: { type: Function, default: (v) => v },
});

const filas = computed(() => props.items.map((i) => {
  const total = Number(i.total) || 0;
  const valor = Number(i.valor) || 0;
  const pct = total <= 0 ? 0 : Math.min(100, Math.max(0, (valor / total) * 100));
  return { ...i, valor, total, pct };
}));

const uno = (n) => Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 1 });
</script>

<template>
  <div class="barras-meta">
    <div v-for="(f, i) in filas" :key="i" class="fila-meta">
      <div class="cab">
        <span class="etq" :title="f.etiqueta">{{ f.etiqueta }}</span>
        <span class="cifra">
          <strong>{{ uno(f.pct) }}%</strong>
          <span class="muted"> · {{ formato(f.valor) }} de {{ formato(f.total) }}</span>
        </span>
      </div>
      <div class="pista" role="progressbar" :aria-valuenow="Math.round(f.pct)"
           aria-valuemin="0" aria-valuemax="100" :aria-label="f.etiqueta">
        <div class="relleno" :style="{ width: f.pct + '%' }"></div>
      </div>
      <p v-if="f.detalle" class="detalle">{{ f.detalle }}</p>
    </div>
    <p v-if="!filas.length" class="vacio">{{ vacio }}</p>
  </div>
</template>

<style scoped>
.barras-meta { display: flex; flex-direction: column; gap: 0.85rem; }
.fila-meta { display: flex; flex-direction: column; align-items: stretch; gap: 0.3rem; }
.cab { display: flex; justify-content: space-between; align-items: baseline; gap: 0.8rem; }
.etq {
  font-weight: 600; font-size: 0.92rem;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.cifra { white-space: nowrap; font-variant-numeric: tabular-nums; font-size: 0.9rem; }
.muted { color: var(--muted); }

.pista {
  height: 10px; border-radius: 999px; overflow: hidden;
  background: color-mix(in srgb, var(--text) 10%, transparent);
}
.relleno {
  height: 100%; border-radius: 0 4px 4px 0;
  background: var(--acento);
  transition: width 0.45s ease;
}
.detalle { margin: 0.1rem 0 0; font-size: 0.8rem; color: var(--muted); }
.vacio { margin: 0; color: var(--muted); font-size: 0.9rem; }

@media (prefers-reduced-motion: reduce) {
  .relleno { transition: none; }
}
</style>
