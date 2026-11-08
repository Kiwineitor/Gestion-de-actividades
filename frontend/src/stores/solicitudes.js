import { ref } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';
import { alCambiarPublico } from '../ui/canalPublico.js';
import { actividades, cargarActividades } from '../ui/actividadElegida.js';

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
  const nuevosFeria = ref(0);
  const nuevasEntradas = ref(0);
  const activo = ref(false);
  const aviso = ref(null);
  let soltar = null;
  let pendiente = null;
  let sondeo = null;
  let vistos = false;

  async function cargar() {
    try {
      const [r, e] = await Promise.all([apiFetch('/api/app/interesados-stand'), apiFetch('/api/app/entradas/pendientes')]);
      const antesFeria = nuevosFeria.value;
      const antesEntradas = nuevasEntradas.value;
      let nuevas = [];
      let ultima = null;
      if (r.ok) {
        nuevas = (await r.json()).filter((x) => x.estado === 'NUEVO');
        nuevosFeria.value = nuevas.length;
      }
      if (e.ok) {
        const d = await e.json();
        nuevasEntradas.value = Number(d.revision) || 0;
        ultima = d.ultima || null;
      }
      if (vistos && nuevasEntradas.value > antesEntradas && ultima) {
        sonar();
        aviso.value = {
          clave: `e${ultima.id}`, icono: '🎟️', titulo: 'Nueva compra de entradas', quien: ultima.nombre,
          detalle: `${ultima.cantidad || ''} entrada${Number(ultima.cantidad) === 1 ? '' : 's'} · ${ultima.actividad}`,
          monto: ultima.total, edicion: ultima.edicion, mas: nuevasEntradas.value - antesEntradas - 1,
        };
      } else if (vistos && nuevosFeria.value > antesFeria && nuevas.length) {
        sonar();
        const s = nuevas.reduce((a, b) => (b.id > a.id ? b : a));
        await cargarActividades(true);
        const act = actividades.value.find((a) => a.clave === `f-${s.edicionId}`);
        aviso.value = {
          clave: `s${s.id}`, icono: '🗺️', titulo: 'Nueva solicitud de compra', quien: s.empresa || s.nombreCompleto,
          detalle: [[s.categoriaNombre, s.puestoCodigo].filter(Boolean).join(' · '), act?.nombre].filter(Boolean).join(' · '),
          monto: s.precio, edicion: s.edicionId, mas: nuevosFeria.value - antesFeria - 1,
        };
      }
      vistos = true;
    } catch { }
  }

  const cargarFeria = cargar;

  function iniciar() {
    if (activo.value) return;
    activo.value = true;
    cargar();
    soltar = alCambiarPublico((m) => {
      if (m?.tipo !== 'feria' && m?.tipo !== 'entradas') return;
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
    nuevosFeria.value = 0;
    nuevasEntradas.value = 0;
    aviso.value = null;
    vistos = false;
  }

  function cerrarAviso() {
    aviso.value = null;
  }

  return { nuevosFeria, nuevasEntradas, aviso, cerrarAviso, cargar, cargarFeria, iniciar, detener };
});
