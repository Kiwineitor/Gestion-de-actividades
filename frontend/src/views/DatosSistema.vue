<script setup>
import { reactive, ref, onMounted } from 'vue';
import { apiFetch } from '../api';
import { toast } from '../ui/toast';
import { marca, iniciales, cargarMarca, aplicarMarca } from '../ui/marca';

const form = reactive({ nombre: '', lema: '', contacto: '' });
const guardando = ref(false);
const subiendo = ref(false);

function llenar() {
  Object.assign(form, { nombre: marca.nombre, lema: marca.lema, contacto: marca.contacto });
}

async function responder(r) {
  const d = await r.json().catch(() => ({}));
  if (!r.ok || !d.ok) { toast(d.mensaje || 'No se pudo guardar', 'error'); return false; }
  if (d.marca) aplicarMarca(d.marca);
  toast(d.mensaje, 'ok');
  return true;
}

async function guardar() {
  if (!form.nombre.trim()) { toast('El sistema necesita un nombre', 'advertencia'); return; }
  guardando.value = true;
  try {
    await responder(await apiFetch('/api/app/sistema', { method: 'PUT', body: JSON.stringify(form) }));
  } finally {
    guardando.value = false;
  }
}

async function subirLogo(e) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (!archivo) return;
  subiendo.value = true;
  try {
    const datos = new FormData();
    datos.append('archivo', archivo);
    await responder(await apiFetch('/api/app/sistema/logo', { method: 'POST', body: datos }));
  } finally {
    subiendo.value = false;
  }
}

async function quitarLogo() {
  await responder(await apiFetch('/api/app/sistema/logo', { method: 'DELETE' }));
}

onMounted(async () => {
  await cargarMarca(true);
  llenar();
});
</script>

<template>
  <div class="sistema">
    <section class="card bloque">
      <h3>Identidad</h3>
      <p class="muted">Se muestra en la página pública, el ingreso del personal, el menú y la pestaña del navegador.</p>
      <label class="campo"><span>Nombre del sistema</span><input v-model="form.nombre" class="control" maxlength="80" /></label>
      <label class="campo"><span>Lema de la página pública</span><input v-model="form.lema" class="control" maxlength="160" /></label>
      <label class="campo"><span>Contacto general</span><input v-model="form.contacto" class="control" maxlength="120" placeholder="Celular o correo" /></label>
      <div class="pie">
        <button class="btn btn-primario" :disabled="guardando" @click="guardar">{{ guardando ? 'Guardando…' : 'Guardar' }}</button>
      </div>
    </section>

    <section class="card bloque">
      <h3>Logo</h3>
      <div class="logo-fila">
        <img v-if="marca.logoUrl" :src="marca.logoUrl" :alt="marca.nombre" class="logo" />
        <span v-else class="logo-texto">{{ iniciales }}</span>
        <div class="acciones">
          <label class="btn btn-sm" :class="{ deshabilitado: subiendo }">
            {{ subiendo ? 'Subiendo…' : marca.logoUrl ? 'Cambiar logo' : 'Subir logo' }}
            <input type="file" accept="image/png,image/jpeg,image/webp" hidden :disabled="subiendo" @change="subirLogo" />
          </label>
          <button v-if="marca.logoUrl" type="button" class="btn btn-sm btn-fantasma" @click="quitarLogo">Quitar</button>
        </div>
      </div>
      <p class="muted chico">PNG, JPG o WEBP, hasta 2 MB. Mejor cuadrado y con fondo transparente. Sin logo se usan las iniciales del nombre.</p>
    </section>
  </div>
</template>

<style scoped>
.sistema { display: grid; gap: 1rem; max-width: 720px; }
.bloque { padding: 1.1rem; display: flex; flex-direction: column; gap: 0.8rem; }
.bloque h3 { margin: 0; }
.bloque p { margin: 0; }
.pie { display: flex; justify-content: flex-end; }
.logo-fila { display: flex; align-items: center; gap: 1rem; }
.logo { width: 72px; height: 72px; object-fit: contain; }
.logo-texto {
  width: 72px; height: 72px; border-radius: 18px; display: grid; place-items: center;
  background: var(--acento); color: #fff; font-size: 1.5rem; font-weight: 800;
}
.acciones { display: flex; gap: 0.5rem; }
.chico { font-size: 0.82rem; }
.deshabilitado { opacity: 0.5; pointer-events: none; }
</style>
