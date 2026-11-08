<script setup>
import { ref } from 'vue';
import { marca } from '../ui/marca';

const props = defineProps({
  codigo: { type: String, required: true },
  usuario: { type: String, default: '' },
});
const emit = defineEmits(['listo']);

const guardado = ref(false);
const copiado = ref(false);

async function copiar() {
  try {
    await navigator.clipboard.writeText(props.codigo);
    copiado.value = true;
    setTimeout(() => { copiado.value = false; }, 1800);
  } catch { }
}

function descargar() {
  const texto = [
    `${marca.nombre} · Código de recuperación`,
    '',
    `Usuario: ${props.usuario}`,
    `Código: ${props.codigo}`,
    `Generado: ${new Date().toLocaleString('es-BO')}`,
    '',
    'Si olvidas tu contraseña, en la pantalla de ingreso pulsa «¿Olvidaste tu contraseña?»,',
    'escribe tu usuario y este código y crea una contraseña nueva.',
    'El código sirve una sola vez: al usarlo el sistema te da uno nuevo.',
    'Guárdalo en un lugar seguro y no lo compartas.',
  ].join('\r\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([texto], { type: 'text/plain;charset=utf-8' }));
  a.download = `codigo-recuperacion-${props.usuario || 'usuario'}.txt`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
</script>

<template>
  <div class="codigo-recuperacion">
    <p class="intro">Guárdalo ahora: <b>solo se muestra esta vez</b>. Si un día olvidas tu contraseña, con este código creas una nueva tú mismo, sin pedir ayuda.</p>
    <div class="codigo" aria-label="Código de recuperación">{{ codigo }}</div>
    <div class="acciones">
      <button type="button" class="btn" @click="copiar">{{ copiado ? '✓ Copiado' : '📋 Copiar' }}</button>
      <button type="button" class="btn" @click="descargar">⬇ Descargar (.txt)</button>
    </div>
    <ul class="consejos">
      <li>Anótalo en papel o guárdalo en un lugar seguro, lejos de tu contraseña.</li>
      <li>Sirve una sola vez: al usarlo, el sistema te da uno nuevo.</li>
      <li>Si lo pierdes, genera otro desde «🔑 Clave» o pide a un administrador que te restablezca la contraseña.</li>
    </ul>
    <label class="confirmo"><input v-model="guardado" type="checkbox" /> Ya lo guardé en un lugar seguro</label>
    <button type="button" class="btn btn-primario" :disabled="!guardado" @click="emit('listo')">Continuar</button>
  </div>
</template>

<style scoped>
.codigo-recuperacion { display: flex; flex-direction: column; gap: 0.75rem; }
.intro { margin: 0; }
.codigo { font: 800 1.5rem/1.2 ui-monospace, Consolas, monospace; letter-spacing: 0.08em; text-align: center; padding: 0.9rem; border-radius: 12px;
  border: 2px dashed var(--acento); background: var(--acento-suave); color: var(--text); user-select: all; word-break: break-all; }
.acciones { display: flex; gap: 0.5rem; justify-content: center; flex-wrap: wrap; }
.consejos { margin: 0; padding-left: 1.1rem; font-size: 0.85rem; color: var(--muted); display: flex; flex-direction: column; gap: 0.2rem; }
.confirmo { display: flex; gap: 0.5rem; align-items: center; font-weight: 700; cursor: pointer; }
.confirmo input { width: 18px; height: 18px; }
</style>
