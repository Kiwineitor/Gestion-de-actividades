import { ref } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';
import { escucharTopic } from '../ws.js';

export const useAccesosStore = defineStore('accesos', () => {
  const movimientos = ref([]);
  const dentroDetalle = ref([]);
  const resumenCategoria = ref([]);
  const resumenUsuario = ref([]);
  const cargando = ref(false);
  const error = ref('');
  const enVivo = ref(false);
  const ultimaSync = ref(0);

  let cliente = null;
  let promesaCarga = null;

  let desconectarWs = null;

  function conectar() {
    if (desconectarWs) return;
    desconectarWs = escucharTopic('/topic/accesos', {
      onMensaje: (evento) => aplicarEvento(evento),
      onConectado: () => { enVivo.value = true; },
      onCerrado: () => { enVivo.value = false; },
    });
  }

  function desconectar() {
    if (desconectarWs) {
      desconectarWs();
      desconectarWs = null;
    }
    enVivo.value = false;
  }

  function aplicarEvento(e) {
    if (!e || e.tipo !== 'MOVIMIENTO') return;

    movimientos.value = [
      {
        id: Date.now(),
        cuando: e.cuando,
        sentido: e.sentido,
        nombre: e.nombre,
        paterno: '',
        materno: '',
        ci: e.ci,
        entidad: e.entidad,
        categoria: e.categoria,
        casetas: e.casetas,
        registrado_por: 'Tiempo real',
        origen: 'WS',
      },
      ...movimientos.value.slice(0, 99),
    ];

    if (e.sentido === 'E') {
      const existe = dentroDetalle.value.find(p => p.responsable_id === e.responsableId);
      if (!existe) {
        dentroDetalle.value.push({
          responsable_id: e.responsableId,
          nombre: e.nombre,
          paterno: '',
          materno: '',
          ci: e.ci,
          entidad: e.entidad,
          categoria: e.categoria,
          casetas: e.casetas,
          ultima_entrada: e.cuando,
        });
      } else {
        existe.ultima_entrada = e.cuando;
      }
    } else if (e.sentido === 'S') {
      const idx = dentroDetalle.value.findIndex(p => p.responsable_id === e.responsableId);
      if (idx >= 0) dentroDetalle.value.splice(idx, 1);
    }

    actualizarResumenOptimista(e);
  }

  function actualizarResumenOptimista(e) {
    let cat = resumenCategoria.value.find(c => c.categoria === e.categoria);
    if (!cat) {
      cat = { categoria_id: 0, categoria: e.categoria || '—', entradas: 0, salidas: 0, personas_unicas: 0 };
      resumenCategoria.value.push(cat);
    }
    if (e.sentido === 'E') cat.entradas = (cat.entradas || 0) + 1;
    else cat.salidas = (cat.salidas || 0) + 1;

  }

  async function cargar(filtros = {}) {
    if (promesaCarga) return promesaCarga;
    cargando.value = true;
    error.value = '';
    promesaCarga = (async () => {
      try {
        const params = new URLSearchParams();
        if (filtros.categoriaId) params.set('categoriaId', filtros.categoriaId);
        if (filtros.desde) params.set('desde', filtros.desde);
        if (filtros.hasta) params.set('hasta', filtros.hasta);
        if (filtros.sentido) params.set('sentido', filtros.sentido);
        params.set('limite', String(filtros.limite || 50));
        params.set('offset', String(filtros.offset || 0));

        const [movs, resCat, resUsr, dentro] = await Promise.all([
          apiFetch(`/api/app/accesos/movimientos?${params.toString()}`),
          apiFetch(`/api/app/accesos/resumen/categoria?${new URLSearchParams({ desde: filtros.desde || '', hasta: filtros.hasta || '' }).toString()}`),
          apiFetch(`/api/app/accesos/resumen/usuario?${new URLSearchParams({ desde: filtros.desde || '', hasta: filtros.hasta || '' }).toString()}`),
          apiFetch('/api/app/accesos/dentro/detalle'),
        ]);

        if (movs.ok) {
          const d = await movs.json();
          movimientos.value = d.datos || [];
        }
        if (resCat.ok) resumenCategoria.value = await resCat.json();
        if (resUsr.ok) resumenUsuario.value = await resUsr.json();
        if (dentro.ok) dentroDetalle.value = await dentro.json();

        ultimaSync.value = Date.now();
      } catch (e) {
        error.value = e.message;
      } finally {
        cargando.value = false;
        promesaCarga = null;
      }
    })();
    return promesaCarga;
  }

  async function recargar(filtros) {
    promesaCarga = null;
    return cargar(filtros);
  }

  return {
    movimientos,
    dentroDetalle,
    resumenCategoria,
    resumenUsuario,
    cargando,
    error,
    enVivo,
    ultimaSync,
    conectar,
    desconectar,
    cargar,
    recargar,
  };
});