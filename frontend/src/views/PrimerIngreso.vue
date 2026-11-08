<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue';
import { apiFetch } from '../api';
import { useAuthStore } from '../stores/auth';
import { marca, cargarMarca } from '../ui/marca';
import { terminosDeUso } from '../ui/legal';
import { toast } from '../ui/toast';
import TextoLegal from '../components/TextoLegal.vue';
import FormCambioClave from '../components/FormCambioClave.vue';

const auth = useAuthStore();
const acepto = ref(false);
const leido = ref(false);
const enviando = ref(false);
const caja = ref(null);

async function revisarLargo() {
  await nextTick();
  const el = caja.value;
  if (el && el.scrollHeight <= el.clientHeight + 30) leido.value = true;
}

const documento = computed(() => terminosDeUso({ institucion: marca.nombre, contacto: marca.contacto }));
const pasoTerminos = computed(() => auth.pendientes.includes('TERMINOS'));
const total = computed(() => (auth.pendientesIniciales || auth.pendientes.length));
const numero = computed(() => total.value - auth.pendientes.length + 1);

function alDesplazar(e) {
  const el = e.target;
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 30) leido.value = true;
}

async function aceptar() {
  enviando.value = true;
  try {
    const r = await apiFetch('/api/auth/aceptar-terminos', { method: 'POST' });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo registrar la aceptación', 'error'); return; }
    auth.aplicar(d);
  } finally {
    enviando.value = false;
  }
}

onMounted(async () => {
  cargarMarca();
  const r = await apiFetch('/api/auth/yo').catch(() => null);
  if (r?.ok) auth.aplicarPendientes((await r.json()).pendientes || []);
  revisarLargo();
});
watch(pasoTerminos, revisarLargo);
</script>

<template>
  <div class="primer-ingreso">
    <div class="tarjeta">
      <header>
        <span class="paso">Paso {{ numero }} de {{ total }}</span>
        <h1>{{ pasoTerminos ? 'Antes de empezar' : 'Crea tu contraseña personal' }}</h1>
        <p class="muted">Hola <b>{{ auth.usuario }}</b>. {{ pasoTerminos
          ? 'Lee y acepta los términos de uso. Explican cómo cuidar tu cuenta y los datos de los clientes.'
          : 'Por seguridad, la contraseña inicial que te dieron se reemplaza por una que solo tú conozcas.' }}</p>
      </header>

      <template v-if="pasoTerminos">
        <div ref="caja" class="lectura" tabindex="0" @scroll="alDesplazar">
          <TextoLegal :documento="documento" />
        </div>
        <p v-if="!leido" class="muted chico">Desplázate hasta el final para poder aceptar.</p>
        <label class="acepto" :class="{ apagado: !leido }">
          <input v-model="acepto" type="checkbox" :disabled="!leido" />
          He leído y acepto los términos de uso del sistema
        </label>
        <div class="acciones">
          <button type="button" class="btn btn-fantasma" @click="auth.logout()">Salir</button>
          <button type="button" class="btn btn-primario" :disabled="!acepto || enviando" @click="aceptar">{{ enviando ? 'Guardando…' : 'Aceptar y continuar' }}</button>
        </div>
      </template>

      <template v-else>
        <FormCambioClave texto-boton="Guardar y entrar al sistema" />
        <button type="button" class="btn btn-fantasma salir" @click="auth.logout()">Salir</button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.primer-ingreso { min-height: 100dvh; display: grid; place-items: center; padding: 1rem; background: radial-gradient(circle at 20% 10%, var(--acento-suave), transparent 60%), var(--bg); }
.tarjeta { width: min(100%, 720px); display: flex; flex-direction: column; gap: 0.9rem; padding: 1.4rem; border-radius: 20px; background: var(--panel); border: 1px solid var(--border); box-shadow: 0 20px 50px rgba(0, 0, 0, 0.25); }
.paso { font-size: 0.75rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.06em; color: var(--acento); }
h1 { margin: 0.2rem 0; font-size: 1.45rem; }
header p { margin: 0; }
.lectura { max-height: 52vh; overflow: auto; padding: 1rem; border-radius: 14px; border: 1px solid var(--border); background: var(--panel-2); }
.acepto { display: flex; gap: 0.5rem; align-items: center; font-weight: 700; cursor: pointer; }
.acepto.apagado { opacity: 0.5; cursor: default; }
.acepto input { width: 18px; height: 18px; }
.acciones { display: flex; justify-content: space-between; gap: 0.5rem; }
.salir { align-self: center; }
.chico { margin: 0; font-size: 0.82rem; }
</style>
