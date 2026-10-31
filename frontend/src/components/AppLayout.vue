<script setup>
import { computed, watch, onUnmounted, h, defineComponent, markRaw } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from './AppShell.vue';
import { usePuestosStore } from '../stores/puestos';
import { useAuthStore } from '../stores/auth';
import { usePestanasStore } from '../stores/pestanas';
import { MODULOS, claveModulo, moduloActual, aModulo } from '../modulos';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const pestanas = usePestanasStore();

const ultima = pestanas.cargar(auth.usuario);
if (!route.query.m && ultima && ultima.m !== 'inicio' && MODULOS[ultima.m]) router.replace(aModulo(ultima.m, ultima.query));

watch(() => route.fullPath, () => {
  if (route.path !== '/admin') return;
  pestanas.visitar(claveModulo(route), route.query, moduloActual(route).titulo);
}, { immediate: true });

const envoltorios = new Map();
const nombreDe = (m) => `Pestana-${m}-${pestanas.version(m)}`;

function envoltorio(m) {
  const nombre = nombreDe(m);
  if (!envoltorios.has(nombre)) {
    envoltorios.set(nombre, markRaw(defineComponent({ name: nombre, setup: () => () => h(MODULOS[m].vista) })));
  }
  return envoltorios.get(nombre);
}

const actual = computed(() => envoltorio(claveModulo(route)));
const incluidas = computed(() => pestanas.lista.map((p) => nombreDe(p.m)));

const puestos = usePuestosStore();
onUnmounted(() => puestos.desconectar());
</script>

<template>
  <AppShell>
    <KeepAlive :include="incluidas" :max="12">
      <component :is="actual" :key="actual.name" />
    </KeepAlive>
  </AppShell>
</template>
