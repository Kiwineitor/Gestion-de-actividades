<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../stores/auth';
import { alerta } from '../ui/alerta';
import { marca, iniciales, cargarMarca } from '../ui/marca';
import { url as urlApi } from '../config.js';
import CodigoRecuperacion from '../components/CodigoRecuperacion.vue';

const usuario = ref('');
const contrasena = ref('');
const error = ref('');
const cargando = ref(false);
const auth = useAuthStore();
const formVisible = ref(false);
const campoContrasena = ref(null);
const modo = ref('entrar');
const rec = ref({ usuario: '', codigo: '', nueva: '', repetir: '' });
const recError = ref('');
const nuevoCodigo = ref('');

function abrirRecuperacion() {
  rec.value = { usuario: usuario.value, codigo: '', nueva: '', repetir: '' };
  recError.value = '';
  modo.value = 'recuperar';
}

async function recuperar() {
  if (cargando.value) return;
  recError.value = '';
  if (rec.value.nueva !== rec.value.repetir) { recError.value = 'Las dos contraseñas no coinciden.'; return; }
  cargando.value = true;
  try {
    const r = await fetch(urlApi('/api/auth/recuperar'), {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ usuario: rec.value.usuario.trim(), codigo: rec.value.codigo, nueva: rec.value.nueva }),
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok || !d.ok) { recError.value = d.mensaje || 'No se pudo recuperar el acceso.'; return; }
    nuevoCodigo.value = d.codigoRecuperacion;
    modo.value = 'codigo';
  } catch {
    recError.value = 'No se pudo conectar con el servidor.';
  } finally {
    cargando.value = false;
  }
}

function volverAEntrar() {
  usuario.value = rec.value.usuario.trim();
  contrasena.value = '';
  nuevoCodigo.value = '';
  modo.value = 'entrar';
  error.value = '';
}

cargarMarca();

async function entrar() {
  if (cargando.value) return;
  error.value = '';
  cargando.value = true;
  try {
    await auth.login(usuario.value, contrasena.value);
  } catch (e) {
    error.value = e.message;
    if (e.codigo === 'MANTENIMIENTO') {
      alerta(e.message, 'advertencia', 0);
    }
  } finally {
    cargando.value = false;
  }
}

onMounted(() => {
  requestAnimationFrame(() => {
    formVisible.value = true;
  });
  document.getElementById('usuario')?.focus();
});
</script>

<template>
  <div class="login-wrap">
    <div class="login-bg" aria-hidden="true">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
      <div class="orb orb-3"></div>
    </div>

    <main class="login-card" :class="{ visible: formVisible }">
      <div class="logo-contenedor" aria-hidden="true">
        <img v-if="marca.logoUrl" :src="marca.logoUrl" :alt="marca.nombre" class="logo" />
        <span v-else class="logo-texto">{{ iniciales }}</span>
      </div>
      <p class="nombre-sistema">{{ marca.nombre }}</p>

      <header class="cabecera">
        <h1>Bienvenido</h1>
        <p>Ingresa tus credenciales para continuar</p>
      </header>

      <form v-if="modo === 'entrar'" class="formulario" @submit.prevent="entrar">
        <div class="campo-grupo">
          <label for="usuario" class="etiqueta">Usuario</label>
          <div class="input-contenedor">
            <span class="icono" aria-hidden="true">👤</span>
            <input
              id="usuario"
              v-model="usuario"
              type="text"
              autocomplete="username"
              placeholder="Tu usuario"
              class="control"
              :disabled="cargando"
              @keydown.enter.prevent="contrasena.trim() ? entrar() : campoContrasena?.focus()"
            />
          </div>
        </div>

        <div class="campo-grupo">
          <label for="contrasena" class="etiqueta">Contraseña</label>
          <div class="input-contenedor">
            <span class="icono" aria-hidden="true">🔒</span>
            <input
              ref="campoContrasena"
              id="contrasena"
              v-model="contrasena"
              type="password"
              autocomplete="current-password"
              placeholder="Tu contraseña"
              class="control"
              :disabled="cargando"
            />
          </div>
        </div>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <button type="submit" class="btn-entrar" :disabled="cargando || !usuario.trim() || !contrasena.trim()">
          <span v-if="cargando" class="spinner" aria-hidden="true"></span>
          <span v-else>Ingresar</span>
        </button>
        <button type="button" class="olvide" @click="abrirRecuperacion">¿Olvidaste tu contraseña?</button>
      </form>

      <form v-else-if="modo === 'recuperar'" class="formulario recuperar" @submit.prevent="recuperar">
        <p class="ayuda-rec">Escribe tu usuario y el <b>código de recuperación</b> que guardaste cuando creaste tu contraseña. Si no lo tienes, pide a un administrador de tu organización que te asigne una contraseña nueva.</p>
        <label class="campo-rec"><span class="etiqueta">Usuario</span><input v-model="rec.usuario" class="control sin-icono" autocomplete="username" /></label>
        <label class="campo-rec"><span class="etiqueta">Código de recuperación</span><input v-model="rec.codigo" class="control sin-icono codigo-rec" placeholder="XXXX-XXXX-XXXX-XXXX" autocomplete="off" /></label>
        <label class="campo-rec"><span class="etiqueta">Nueva contraseña</span><input v-model="rec.nueva" class="control sin-icono" type="password" autocomplete="new-password" maxlength="72" /></label>
        <label class="campo-rec"><span class="etiqueta">Repite la nueva contraseña</span><input v-model="rec.repetir" class="control sin-icono" type="password" autocomplete="new-password" maxlength="72" /></label>
        <p class="ayuda-rec chica">Al menos 10 caracteres, con letras y números, y sin tu nombre de usuario.</p>
        <p v-if="recError" class="error" role="alert">{{ recError }}</p>
        <button type="submit" class="btn-entrar" :disabled="cargando || !rec.usuario.trim() || !rec.codigo.trim() || !rec.nueva">
          <span v-if="cargando" class="spinner" aria-hidden="true"></span><span v-else>Cambiar mi contraseña</span>
        </button>
        <button type="button" class="olvide" @click="modo = 'entrar'">← Volver al ingreso</button>
      </form>

      <div v-else class="formulario">
        <p class="ayuda-rec"><b>✓ Listo, tu contraseña se cambió.</b> El código que usaste ya no sirve; este es tu código nuevo:</p>
        <CodigoRecuperacion :codigo="nuevoCodigo" :usuario="rec.usuario" @listo="volverAEntrar" />
      </div>

      <footer class="pie-login">
        <p>{{ marca.lema || marca.nombre }}</p>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.login-wrap {
  min-height: 100dvh;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 1.5rem;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background: var(--bg);
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.4;
  animation: flotar 20s ease-in-out infinite;
}

.orb-1 {
  width: 300px;
  height: 300px;
  background: var(--acento);
  top: -100px;
  right: -100px;
  animation-delay: 0s;
}

.orb-2 {
  width: 220px;
  height: 220px;
  background: #14A83A;
  bottom: -80px;
  left: -80px;
  animation-delay: -7s;
}

.orb-3 {
  width: 180px;
  height: 180px;
  background: #f59e0b;
  top: 40%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation-delay: -14s;
}

@keyframes flotar {
  0%, 100% { transform: translate(0, 0) scale(1); }
  25% { transform: translate(30px, -20px) scale(1.05); }
  50% { transform: translate(-20px, 30px) scale(0.95); }
  75% { transform: translate(15px, 25px) scale(1.02); }
}

@media (prefers-reduced-motion: reduce) {
  .orb { animation: none; opacity: 0.15; }
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 2.5rem 2rem;
  box-shadow: var(--sombra-md);
  opacity: 0;
  transform: translateY(30px) scale(0.96);
  transition: opacity 0.6s cubic-bezier(0.16, 1, 0.3, 1),
              transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.login-card.visible {
  opacity: 1;
  transform: translateY(0) scale(1);
}

.logo-contenedor {
  display: flex;
  justify-content: center;
  margin-bottom: 1.5rem;
  opacity: 0;
  transform: scale(0.9);
  animation: logoEntrada 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.15s forwards;
}

.logo {
  width: 100px;
  max-height: 100px;
  object-fit: contain;
  height: auto;
  filter: drop-shadow(0 8px 24px rgba(124, 92, 255, 0.35));
}

.logo-texto {
  width: 72px; height: 72px; border-radius: 20px; display: grid; place-items: center;
  background: linear-gradient(135deg, #7c5cff, #4f46e5); color: #fff;
  font-size: 1.6rem; font-weight: 800; letter-spacing: 0.04em;
  box-shadow: 0 8px 24px rgba(124, 92, 255, 0.35);
}

.nombre-sistema {
  margin: -0.6rem 0 1rem; text-align: center; font-weight: 700; letter-spacing: 0.04em;
  color: var(--muted, #64748b); font-size: 0.85rem; text-transform: uppercase;
}

@keyframes logoEntrada {
  from { opacity: 0; transform: scale(0.9) translateY(10px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.cabecera {
  text-align: center;
  margin-bottom: 2rem;
  opacity: 0;
  transform: translateY(10px);
  animation: entradaSuave 0.5s ease 0.3s forwards;
}

.cabecera h1 {
  margin: 0 0 0.4rem;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text);
}

.cabecera p {
  margin: 0;
  color: var(--muted);
  font-size: 0.95rem;
}

.formulario {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
}

.campo-grupo {
  opacity: 0;
  transform: translateY(12px);
  animation: entradaSuave 0.5s ease forwards;
}

.campo-grupo:nth-child(1) { animation-delay: 0.4s; }
.campo-grupo:nth-child(2) { animation-delay: 0.5s; }

@keyframes entradaSuave {
  to { opacity: 1; transform: translateY(0); }
}

.etiqueta {
  display: block;
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 0.45rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.input-contenedor {
  position: relative;
  display: flex;
  align-items: center;
}

.input-contenedor .icono {
  position: absolute;
  left: 1rem;
  font-size: 1.1rem;
  color: var(--muted);
  pointer-events: none;
  transition: color 0.2s ease;
}

.input-contenedor:focus-within .icono {
  color: var(--acento);
}

.control {
  width: 100%;
  padding: 0.85rem 1rem 0.85rem 3rem;
  font: inherit;
  font-size: 1rem;
  color: var(--text);
  background: var(--panel);
  border: 1.5px solid var(--border);
  border-radius: 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.control::placeholder {
  color: var(--muted);
  opacity: 0.7;
}

.control:focus {
  outline: none;
  border-color: var(--acento);
  box-shadow: 0 0 0 3px var(--acento-suave);
}

.control:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  margin: -0.5rem 0 0.25rem;
  padding: 0.7rem 1rem;
  background: var(--danger-suave);
  color: var(--danger);
  border-radius: 10px;
  font-size: 0.85rem;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  animation: sacudida 0.4s ease;
}

.error::before {
  content: '⚠';
  font-size: 0.9rem;
}

@keyframes sacudida {
  0%, 100% { transform: translateX(0); }
  20%, 60% { transform: translateX(-6px); }
  40%, 80% { transform: translateX(6px); }
}

.btn-entrar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.9rem 1.5rem;
  margin-top: 0.5rem;
  font: inherit;
  font-weight: 600;
  font-size: 1rem;
  color: var(--acento-texto);
  background: var(--acento);
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s ease, transform 0.1s ease, box-shadow 0.2s ease;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.35);
}

.btn-entrar:hover:not(:disabled) {
  background: var(--acento-fuerte);
  box-shadow: 0 6px 20px rgba(79, 70, 229, 0.45);
}

.btn-entrar:active:not(:disabled) {
  transform: scale(0.98);
}

.btn-entrar:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2.5px solid transparent;
  border-top-color: currentColor;
  border-radius: 50%;
  animation: girar 0.8s linear infinite;
}

@keyframes girar {
  to { transform: rotate(360deg); }
}

.olvide { align-self: center; border: 0; background: none; color: var(--acento); font: inherit; font-weight: 700; font-size: 0.88rem; cursor: pointer; padding: 0.3rem; }
.olvide:hover { text-decoration: underline; }
.recuperar { gap: 0.7rem; }
.campo-rec { display: flex; flex-direction: column; gap: 0.3rem; }
.control.sin-icono { padding-left: 0.85rem; }
.codigo-rec { font-family: ui-monospace, Consolas, monospace; letter-spacing: 0.08em; text-transform: uppercase; }
.ayuda-rec { margin: 0; font-size: 0.86rem; color: var(--muted); line-height: 1.45; }
.ayuda-rec.chica { font-size: 0.78rem; }

.pie-login {
  margin-top: 2rem;
  text-align: center;
  opacity: 0;
  animation: entradaSuave 0.5s ease 0.7s forwards;
}

.pie-login p {
  margin: 0;
  font-size: 0.75rem;
  color: var(--muted);
  opacity: 0.8;
}

@media (max-width: 480px) {
  .login-wrap { padding: 1rem; }
  .login-card { padding: 2rem 1.5rem; border-radius: 16px; }
  .logo { width: 88px; }
  .cabecera h1 { font-size: 1.35rem; }
  .control { padding: 0.9rem 1rem 0.9rem 3rem; font-size: 16px; }
  .btn-entrar { padding: 1rem 1.5rem; min-height: 52px; }
}

@media (max-width: 360px) {
  .login-card { padding: 1.75rem 1.25rem; }
  .logo { width: 80px; }
  .cabecera h1 { font-size: 1.25rem; }
}

@media (prefers-color-scheme: dark) {
  .orb { opacity: 0.25; }
}
</style>
