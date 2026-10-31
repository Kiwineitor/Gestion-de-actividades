<script setup>
import { ref, computed, defineAsyncComponent } from 'vue';
import { useAuthStore } from '../stores/auth';
import { usePermisosStore } from '../stores/permisos';

const auth = useAuthStore();
const permisos = usePermisosStore();

const PersonalApoyo = defineAsyncComponent(() => import('./PersonalApoyo.vue'));
const CredencialesApoyo = defineAsyncComponent(() => import('./CredencialesApoyo.vue'));

const veRegistro = computed(() => permisos.puedeVer('personal-apoyo'));
const veImpresion = computed(() => auth.puedeEditarPlano || permisos.puedeVer('credenciales-apoyo'));
const modo = ref(veRegistro.value ? 'registro' : 'imprimir');
</script>

<template>
  <div class="apoyo">
    <div v-if="veRegistro && veImpresion" class="segmento" role="tablist">
      <button type="button" :class="{ on: modo === 'registro' }" @click="modo = 'registro'">👥 Registro, datos y foto</button>
      <button type="button" :class="{ on: modo === 'imprimir' }" @click="modo = 'imprimir'">🖨️ Imprimir credenciales</button>
    </div>
    <KeepAlive>
      <PersonalApoyo v-if="modo === 'registro' && veRegistro" />
      <CredencialesApoyo v-else-if="veImpresion" />
    </KeepAlive>
  </div>
</template>

<style scoped>
.apoyo { display: flex; flex-direction: column; gap: 0.8rem; }
.segmento { display: inline-flex; align-self: flex-start; border: 1px solid var(--border); border-radius: 999px; padding: 3px; background: var(--panel); flex-wrap: wrap; }
.segmento button { border: 0; background: none; color: var(--muted); font: inherit; font-size: 0.86rem; font-weight: 700; padding: 0.4rem 0.9rem; border-radius: 999px; cursor: pointer; }
.segmento button.on { background: var(--acento); color: var(--acento-texto, #fff); }
</style>
