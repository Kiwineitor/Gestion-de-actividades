import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';
import { alCambiarPublico } from '../ui/canalPublico.js';
import { toast } from '../ui/toast.js';

function sonar() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)();
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.connect(g);
    g.connect(ctx.destination);
    o.frequency.value = 880;
    g.gain.setValueAtTime(0.08, ctx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.35);
    o.start();
    o.stop(ctx.currentTime + 0.35);
  } catch { }
}

export const useSolicitudesStore = defineStore('solicitudes', () => {
  const pendientes = ref([]);
  const nuevosFeria = ref(0);
  const activo = ref(false);
  let alcance = { mesas: false, ferias: false };
  let soltar = null;
  let pendiente = null;
  let sondeo = null;
  let conocidas = null;

  const porValidar = computed(() => pendientes.value.filter((r) => r.estado === 'EN_REVISION'));
  const esperandoPago = computed(() => pendientes.value.filter((r) => r.estado === 'BLOQUEADA'));

  async function cargarFeria() {
    if (!alcance.ferias) return;
    try {
      const r = await apiFetch('/api/app/interesados-stand');
      if (!r.ok) return;
      nuevosFeria.value = (await r.json()).filter((x) => x.estado === 'NUEVO').length;
    } catch { }
  }

  async function cargar() {
    cargarFeria();
    if (!alcance.mesas) return;
    try {
      const r = await apiFetch('/api/app/mesas/reservas/pendientes');
      if (!r.ok) return;
      const lista = await r.json();
      const nuevas = lista.filter((x) => x.estado === 'EN_REVISION' && conocidas && !conocidas.has(x.codigo));
      conocidas = new Set(lista.filter((x) => x.estado === 'EN_REVISION').map((x) => x.codigo));
      pendientes.value = lista;
      if (nuevas.length) {
        sonar();
        toast(nuevas.length === 1
          ? `Nuevo pago por revisar: mesa ${nuevas[0].mesa} · ${nuevas[0].evento}`
          : `${nuevas.length} pagos nuevos por revisar`, 'info', 6000);
      }
    } catch { }
  }

  function iniciar(nuevoAlcance = { mesas: true, ferias: false }) {
    const igual = activo.value && alcance.mesas === nuevoAlcance.mesas && alcance.ferias === nuevoAlcance.ferias;
    if (igual) return;
    detener();
    alcance = { ...nuevoAlcance };
    activo.value = true;
    cargar();
    soltar = alCambiarPublico((m) => {
      if (m?.tipo !== 'mesas' && m?.tipo !== 'feria') return;
      clearTimeout(pendiente);
      pendiente = setTimeout(cargar, 500);
    }, { reconectado: cargar });
    sondeo = setInterval(cargar, 60000);
  }

  function detener() {
    activo.value = false;
    soltar?.();
    soltar = null;
    clearTimeout(pendiente);
    clearInterval(sondeo);
    pendientes.value = [];
    nuevosFeria.value = 0;
    conocidas = null;
  }

  return { pendientes, nuevosFeria, porValidar, esperandoPago, cargar, cargarFeria, iniciar, detener };
});
