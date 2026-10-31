<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { apiFetch } from '../api';
import { aModulo } from '../modulos';

const props = defineProps({
  actual: { type: String, required: true },
});

const router = useRouter();
const ferias = ref([]);
const eventos = ref([]);

const valor = computed({
  get: () => props.actual,
  set: (v) => ir(v),
});

function ir(v) {
  if (!v || v === props.actual) return;
  const [tipo, id] = v.split('-');
  if (tipo === 'e') router.push(aModulo('mesas', { evento: id, tab: 'plano' }));
  else router.push(aModulo('editor'));
}

async function leer(ruta) {
  try {
    const r = await apiFetch(ruta);
    return r.ok ? await r.json() : [];
  } catch {
    return [];
  }
}

onMounted(async () => {
  const [f, e] = await Promise.all([leer('/api/app/ediciones'), leer('/api/app/mesas/eventos')]);
  ferias.value = Array.isArray(f) ? f : [];
  eventos.value = Array.isArray(e) ? e : [];
});
</script>

<template>
  <label v-if="ferias.length + eventos.length > 1" class="cambiar-plano" title="Abrir el plano de otra actividad">
    <span>Plano de</span>
    <select v-model="valor" aria-label="Plano de la actividad">
      <optgroup v-if="ferias.length" label="Ferias (puestos)">
        <option v-for="f in ferias" :key="`f-${f.id}`" :value="`f-${f.id}`" :disabled="!f.activa">
          {{ f.nombre }}{{ f.activa ? '' : ' — ponla en curso para editar' }}
        </option>
      </optgroup>
      <optgroup v-if="eventos.length" label="Eventos con mesas">
        <option v-for="e in eventos" :key="`e-${e.id}`" :value="`e-${e.id}`">{{ e.nombre }}</option>
      </optgroup>
    </select>
  </label>
</template>

<style scoped>
.cambiar-plano {
  display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.1rem 0.15rem 0.1rem 0.6rem;
  border: 1px solid var(--border); border-radius: var(--radio-sm); background: var(--panel);
  font-size: 0.82rem; color: var(--muted); font-weight: 600; max-width: 100%;
}
.cambiar-plano select {
  border: 0; background: transparent; color: var(--text); font: inherit; font-weight: 700; padding: 0.3rem 0.3rem;
  max-width: min(46vw, 260px); cursor: pointer; outline: none;
}
</style>
