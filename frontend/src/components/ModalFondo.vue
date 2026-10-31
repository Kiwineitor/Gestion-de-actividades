<script setup>
import { ref } from 'vue';
import UiModal from './UiModal.vue';

const props = defineProps({
  fondoUrl: { type: String, default: null },
  acepta: { type: String, default: 'image/png,image/jpeg,image/webp' },
  sobre: { type: String, default: 'las mesas, los textos y las zonas' },
  ocupado: { type: Boolean, default: false },
});
const emit = defineEmits(['elegir', 'quitar', 'cerrar']);
const entrada = ref(null);

function elegido(e) {
  const archivo = e.target.files?.[0];
  e.target.value = '';
  if (archivo) emit('elegir', archivo);
}
</script>

<template>
  <UiModal titulo="Imagen de fondo del plano" ancho="620px" @cerrar="emit('cerrar')">
    <div class="escena" aria-hidden="true">
      <div class="pila">
        <div class="capa capa-fondo">
          <span class="pared p1"></span><span class="pared p2"></span><span class="pared p3"></span>
          <span class="puerta"></span>
        </div>
        <div class="capa capa-objetos">
          <span class="zona">VIP</span>
          <span class="mesa m1"></span><span class="mesa m2"></span><span class="mesa m3"></span>
          <span class="mesa m4"></span><span class="mesa m5"></span>
        </div>
      </div>
      <span class="rotulo r-objetos">{{ sobre }}</span>
      <span class="rotulo r-fondo">tu imagen, debajo</span>
    </div>

    <ol class="pasos">
      <li><b>Elige una foto o dibujo del local</b> (PNG o JPG). Sirve como guía para ubicar todo en su lugar real.</li>
      <li><b>Queda debajo de {{ sobre }}</b>: nada se borra ni se mueve, solo cambia lo que se ve atrás.</li>
      <li><b>El plano toma la forma de tu imagen</b> (si es más ancha o más alta). Revisa después que todo siga bien ubicado.</li>
      <li><b>Puedes quitarla cuando quieras</b> y el plano vuelve a un lienzo liso con cuadrícula.</li>
    </ol>

    <div v-if="fondoUrl" class="actual">
      <img :src="fondoUrl" alt="Fondo actual" />
      <div>
        <strong>Fondo actual</strong>
        <p class="muted chico">Si eliges otra imagen, reemplaza a esta.</p>
      </div>
    </div>

    <input ref="entrada" type="file" :accept="acepta" class="oculto" @change="elegido" />
    <template #pie>
      <button class="btn btn-fantasma" @click="emit('cerrar')">Cancelar</button>
      <button v-if="fondoUrl" class="btn btn-peligro" :disabled="ocupado" @click="emit('quitar')">Quitar fondo</button>
      <button class="btn btn-primario" :disabled="ocupado" @click="entrada?.click()">
        {{ ocupado ? 'Subiendo…' : fondoUrl ? 'Elegir otra imagen…' : 'Sí, elegir imagen…' }}
      </button>
    </template>
  </UiModal>
</template>

<style scoped>
.escena {
  position: relative; height: 210px; border-radius: var(--radio); overflow: hidden;
  background: radial-gradient(500px 220px at 50% 0%, color-mix(in srgb, var(--acento) 20%, transparent), transparent 70%), var(--bg);
  display: grid; place-items: center; perspective: 900px;
}
.pila { position: relative; width: 230px; height: 150px; transform-style: preserve-3d; transform: rotateX(56deg) rotateZ(-34deg); margin-top: 30px; }
.capa { position: absolute; inset: 0; border-radius: 12px; }
.capa-fondo {
  background: linear-gradient(135deg, #f5e7c8, #d9c49b); box-shadow: 0 0 0 2px rgba(0,0,0,.12) inset;
  animation: fondo-baja 4.2s ease-in-out infinite;
}
.pared { position: absolute; background: #8b6b3e; border-radius: 2px; }
.p1 { left: 8%; top: 10%; width: 84%; height: 5px; }
.p2 { left: 8%; top: 10%; width: 5px; height: 80%; }
.p3 { right: 8%; top: 10%; width: 5px; height: 60%; }
.puerta { position: absolute; left: 42%; bottom: 6%; width: 18%; height: 5px; background: #2bb5a0; border-radius: 2px; }
.capa-objetos { transform: translateZ(26px); animation: objetos 4.2s ease-in-out infinite; }
.zona {
  position: absolute; left: 10%; top: 18%; width: 30%; height: 34%; border-radius: 8px; display: grid; place-items: center;
  font: 800 11px system-ui; letter-spacing: 0.1em; color: #fff; background: rgba(255, 79, 163, .35); border: 2px solid #ff4fa3;
}
.mesa { position: absolute; width: 22px; height: 22px; border-radius: 50%; background: #1f9d63; border: 3px solid #6d5bd0; box-shadow: 0 3px 6px rgba(0,0,0,.35); }
.m1 { left: 52%; top: 22%; } .m2 { left: 70%; top: 22%; } .m3 { left: 52%; top: 55%; background: #d23b5a; }
.m4 { left: 70%; top: 55%; } .m5 { left: 22%; top: 64%; }
.rotulo {
  position: absolute; font-size: 0.75rem; font-weight: 700; padding: 0.2rem 0.55rem; border-radius: 999px;
  background: var(--panel); border: 1px solid var(--border); color: var(--text); white-space: nowrap;
}
.r-objetos { top: 14px; right: 14px; }
.r-fondo { bottom: 14px; left: 14px; animation: aparece 4.2s ease-in-out infinite; }
@keyframes fondo-baja {
  0% { transform: translateZ(-150px); opacity: 0; }
  20% { opacity: 1; }
  55%, 100% { transform: translateZ(0); opacity: 1; }
}
@keyframes objetos {
  0%, 50% { transform: translateZ(26px); }
  65%, 100% { transform: translateZ(4px); }
}
@keyframes aparece { 0%, 15% { opacity: 0; } 35%, 100% { opacity: 1; } }
.pasos { margin: 0; padding-left: 1.2rem; display: flex; flex-direction: column; gap: 0.45rem; font-size: 0.9rem; }
.actual { display: flex; gap: 0.8rem; align-items: center; border: 1px solid var(--border); border-radius: var(--radio-sm); padding: 0.5rem; }
.actual img { width: 110px; height: 70px; object-fit: cover; border-radius: 6px; background: #fff; }
.actual p { margin: 0.2rem 0 0; }
.chico { font-size: 0.8rem; }
.oculto { display: none; }
@media (prefers-reduced-motion: reduce) {
  .capa-fondo, .capa-objetos, .r-fondo { animation: none; }
}
</style>
