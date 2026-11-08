<script setup>
import { reactive, ref, computed } from 'vue';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { toast } from '../ui/toast';

const props = defineProps({
  textoBoton: { type: String, default: 'Guardar contraseña' },
});
const emit = defineEmits(['listo']);

const auth = useAuthStore();
const f = reactive({ actual: '', nueva: '', repetir: '' });
const ver = ref(false);
const enviando = ref(false);
const error = ref('');

const reglas = computed(() => [
  { ok: f.nueva.length >= 10, t: 'Al menos 10 caracteres' },
  { ok: /\p{L}/u.test(f.nueva) && /\d/.test(f.nueva), t: 'Letras y números' },
  { ok: Boolean(f.nueva) && !f.nueva.toLowerCase().includes((auth.usuario || '').toLowerCase()), t: 'Sin tu nombre de usuario' },
  { ok: Boolean(f.nueva) && f.nueva !== f.actual, t: 'Distinta de la actual' },
  { ok: Boolean(f.nueva) && f.nueva === f.repetir, t: 'Las dos coinciden' },
]);
const fuerza = computed(() => {
  let n = 0;
  if (f.nueva.length >= 10) n++;
  if (f.nueva.length >= 14) n++;
  if (/[a-z]/.test(f.nueva) && /[A-Z]/.test(f.nueva)) n++;
  if (/\d/.test(f.nueva)) n++;
  if (/[^\p{L}\d]/u.test(f.nueva)) n++;
  return n;
});
const etiquetaFuerza = computed(() => ['Muy débil', 'Débil', 'Aceptable', 'Buena', 'Fuerte', 'Muy fuerte'][fuerza.value]);
const lista = computed(() => f.actual && reglas.value.every((r) => r.ok));

async function enviar() {
  if (!lista.value || enviando.value) return;
  enviando.value = true;
  error.value = '';
  try {
    const r = await apiFetch('/api/auth/cambiar-clave', { method: 'POST', body: JSON.stringify({ actual: f.actual, nueva: f.nueva }) });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { error.value = d.mensaje || 'No se pudo cambiar la contraseña'; return; }
    auth.aplicar(d);
    Object.assign(f, { actual: '', nueva: '', repetir: '' });
    toast(d.mensaje || 'Contraseña actualizada', 'ok', 3500);
    emit('listo');
  } catch (e) {
    error.value = e.message;
  } finally {
    enviando.value = false;
  }
}
</script>

<template>
  <form class="form-clave" autocomplete="off" @submit.prevent="enviar">
    <label class="campo"><span>Contraseña actual</span>
      <input v-model="f.actual" class="control" :type="ver ? 'text' : 'password'" autocomplete="current-password" maxlength="72" /></label>
    <label class="campo"><span>Nueva contraseña</span>
      <input v-model="f.nueva" class="control" :type="ver ? 'text' : 'password'" autocomplete="new-password" maxlength="72" /></label>
    <div v-if="f.nueva" class="fuerza" :data-n="fuerza"><span :style="{ width: `${(fuerza / 5) * 100}%` }"></span><small>{{ etiquetaFuerza }}</small></div>
    <label class="campo"><span>Repite la nueva contraseña</span>
      <input v-model="f.repetir" class="control" :type="ver ? 'text' : 'password'" autocomplete="new-password" maxlength="72" /></label>
    <label class="ver"><input v-model="ver" type="checkbox" /> Mostrar contraseñas</label>
    <ul class="reglas">
      <li v-for="r in reglas" :key="r.t" :class="{ ok: r.ok }">{{ r.ok ? '✓' : '○' }} {{ r.t }}</li>
    </ul>
    <p class="consejo">💡 Usa una frase que recuerdes, por ejemplo <b>MiFeria-Cobija2026</b>, y no la uses en otros sitios.</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button type="submit" class="btn btn-primario" :disabled="!lista || enviando">{{ enviando ? 'Guardando…' : props.textoBoton }}</button>
  </form>
</template>

<style scoped>
.form-clave { display: flex; flex-direction: column; gap: 0.65rem; }
.fuerza { position: relative; height: 8px; border-radius: 999px; background: var(--panel-2); margin-top: -0.3rem; }
.fuerza span { display: block; height: 100%; border-radius: inherit; background: var(--danger); transition: width 0.2s, background 0.2s; }
.fuerza[data-n="2"] span, .fuerza[data-n="3"] span { background: var(--tramite); }
.fuerza[data-n="4"] span, .fuerza[data-n="5"] span { background: var(--ok); }
.fuerza small { position: absolute; right: 0; top: 10px; font-size: 0.72rem; color: var(--muted); }
.ver { display: flex; gap: 0.4rem; align-items: center; font-size: 0.85rem; cursor: pointer; }
.reglas { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 0.2rem 0.6rem; font-size: 0.82rem; color: var(--muted); }
.reglas li.ok { color: var(--ok); font-weight: 700; }
.consejo { margin: 0; font-size: 0.8rem; color: var(--muted); }
.error { margin: 0; color: var(--danger); font-size: 0.88rem; }
</style>
