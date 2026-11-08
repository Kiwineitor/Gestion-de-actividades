<script setup>
import { watch, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useSolicitudesStore } from '../stores/solicitudes';
import { aModulo } from '../modulos';

const router = useRouter();
const solicitudes = useSolicitudesStore();
let temporizador = 0;

const bs = (n) => `Bs ${Number(n || 0).toLocaleString('es-BO', { maximumFractionDigits: 2 })}`;

watch(() => solicitudes.aviso?.clave, (clave) => {
  clearTimeout(temporizador);
  if (clave) temporizador = setTimeout(() => solicitudes.cerrarAviso(), 20000);
});

function pausar() {
  clearTimeout(temporizador);
}

function revisar() {
  const a = solicitudes.aviso;
  solicitudes.cerrarAviso();
  router.push(aModulo('cobros', { tab: 'validar', ...(a?.edicion ? { edicion: a.edicion } : {}) }));
}

onBeforeUnmount(() => clearTimeout(temporizador));
</script>

<template>
  <Transition name="aviso">
    <aside v-if="solicitudes.aviso" :key="solicitudes.aviso.clave" class="aviso-solicitud" role="status" aria-live="polite"
           @mouseenter="pausar">
      <span class="campana" aria-hidden="true">{{ solicitudes.aviso.icono }}</span>
      <div class="cuerpo">
        <small class="etiqueta">🔔 {{ solicitudes.aviso.titulo }}</small>
        <strong>{{ solicitudes.aviso.quien }}</strong>
        <span class="detalle">{{ solicitudes.aviso.detalle }}</span>
        <span v-if="Number(solicitudes.aviso.monto)" class="monto">{{ bs(solicitudes.aviso.monto) }} · pago por validar</span>
        <small v-if="solicitudes.aviso.mas > 0" class="mas">y {{ solicitudes.aviso.mas }} más</small>
        <div class="botones">
          <button type="button" class="btn btn-primario btn-sm" @click="revisar">Revisar ahora</button>
          <button type="button" class="btn btn-fantasma btn-sm" @click="solicitudes.cerrarAviso()">Luego</button>
        </div>
      </div>
      <button type="button" class="cerrar" aria-label="Cerrar aviso" @click="solicitudes.cerrarAviso()">✕</button>
    </aside>
  </Transition>
</template>

<style scoped>
.aviso-solicitud {
  position: fixed; right: 18px; bottom: 18px; z-index: 300; width: min(360px, calc(100vw - 32px));
  display: flex; gap: 0.8rem; padding: 0.9rem 1rem; border-radius: 18px; background: var(--panel); color: var(--text);
  border: 1px solid color-mix(in srgb, var(--acento) 45%, var(--border));
  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.35), 0 0 0 4px color-mix(in srgb, var(--acento) 14%, transparent);
}
.campana { flex: none; width: 52px; height: 52px; border-radius: 16px; display: grid; place-items: center; font-size: 1.7rem; background: linear-gradient(135deg, var(--acento), #db2777); animation: timbre 1.4s ease 2; }
.cuerpo { display: flex; flex-direction: column; gap: 0.15rem; min-width: 0; flex: 1; }
.etiqueta { color: var(--acento); font-weight: 800; font-size: 0.74rem; text-transform: uppercase; letter-spacing: 0.04em; }
.cuerpo strong { font-size: 1rem; overflow-wrap: anywhere; }
.detalle { color: var(--muted); font-size: 0.84rem; }
.monto { font-weight: 800; font-size: 0.9rem; }
.mas { color: var(--muted); }
.botones { display: flex; gap: 0.4rem; margin-top: 0.4rem; }
.cerrar { position: absolute; top: 6px; right: 8px; border: 0; background: none; color: var(--muted); cursor: pointer; font-size: 0.9rem; }
.aviso-enter-active { transition: transform 0.35s cubic-bezier(0.2, 0.9, 0.3, 1.2), opacity 0.25s; }
.aviso-leave-active { transition: transform 0.25s ease, opacity 0.2s; }
.aviso-enter-from, .aviso-leave-to { transform: translateY(24px) scale(0.96); opacity: 0; }
@keyframes timbre { 0%, 100% { transform: rotate(0); } 15% { transform: rotate(14deg); } 30% { transform: rotate(-12deg); } 45% { transform: rotate(8deg); } 60% { transform: rotate(-5deg); } }
@media (max-width: 640px) { .aviso-solicitud { left: 16px; right: 16px; width: auto; bottom: calc(76px + env(safe-area-inset-bottom, 0px)); } }
@media (prefers-reduced-motion: reduce) { .campana { animation: none; } }
</style>
