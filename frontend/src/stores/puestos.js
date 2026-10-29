import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { apiFetch } from '../api.js';
import { crearClientePuestos } from '../ws.js';
import { cerrarMedicion } from '../ui/medir.js';

export const usePuestosStore = defineStore('puestos', () => {
  const puestos = ref([]);
  const cargando = ref(false);
  const error = ref('');
  const enVivo = ref(false);
  const desdeCache = ref(false);
  const asignaciones = ref(new Map());
  const ocupacion = ref(new Map());
  const directorio = ref(new Map());
  const ultimaSync = ref(0);

  let cliente = null;
  let promesaCarga = null;
  let alRechazar = null;
  let yaConecto = false;
  let ultimaLista = [];
  let etagPuestos = null;
  let etagAsignaciones = null;
  let etagOcupacion = null;
  let pendienteOcupacion = null;
  let sondeo = null;
  let oyentesCiclo = false;

  const guardias = new Set();

  const oyentesNotificacion = new Set();
  function registrarNotificaciones(fn) {
    oyentesNotificacion.add(fn);
    return () => oyentesNotificacion.delete(fn);
  }

  const CLAVE_CACHE = 'puestos.cache.v1';

  const condicional = (etag) => ({
    cache: 'no-store',
    ...(etag ? { headers: { 'If-None-Match': etag } } : {}),
  });

  function guardarCache() {
    if (!ultimaLista.length) return;
    try {
      localStorage.setItem(CLAVE_CACHE, JSON.stringify({
        ts: Date.now(), lista: ultimaLista, asignaciones: [...asignaciones.value.entries()],
        directorio: [...directorio.value.entries()],
      }));
    } catch {
    }
  }

  function hidratarDesdeCache() {
    if (puestos.value.length) return false;
    try {
      const guardado = JSON.parse(localStorage.getItem(CLAVE_CACHE) || 'null');
      if (!guardado?.lista?.length) return false;
      puestos.value = guardado.lista;
      ultimaLista = guardado.lista;
      if (Array.isArray(guardado.asignaciones)) {
        asignaciones.value = new Map(guardado.asignaciones.map(
          ([id, v]) => [id, Array.isArray(v) ? v : [v]],
        ));
      }
      if (Array.isArray(guardado.directorio)) directorio.value = new Map(guardado.directorio);
      desdeCache.value = true;
      return true;
    } catch {
      return false;
    }
  }

  const ubicadas = computed(() => puestos.value.filter((p) => p.mapaX != null && p.mapaY != null));

  function carritoDe(idUsuario) {
    if (idUsuario == null) return [];
    const mio = Number(idUsuario);
    return puestos.value.filter((p) => p.estado === 'T' && p.reservadoPor != null
      && Number(p.reservadoPor) === mio);
  }

  let enCola = null;
  let vaciadoPedido = false;

  function abrirVentana() {
    const raf = globalThis.requestAnimationFrame;
    if (typeof raf !== 'function') return;
    vaciadoPedido = true;
    raf(() => { vaciadoPedido = false; vaciarCola(); });
  }

  function vaciarCola() {
    if (!enCola) return;
    const lote = enCola;
    enCola = null;
    for (const dto of lote.values()) aplicar(dto);
  }

  function aplicarDelServidor(dto) {
    if (!dto || dto.id == null) return;
    if (!vaciadoPedido) {
      aplicar(dto);
      abrirVentana();
      return;
    }
    if (!enCola) enCola = new Map();
    enCola.set(dto.id, dto);
  }

  function aplicar(dto) {
    if (enCola) vaciarCola();
    const i = puestos.value.findIndex((p) => p.id === dto.id);
    if (dto.activo === false) {
      if (i >= 0) puestos.value.splice(i, 1);
      if (ocupacion.value.has(dto.id)) {
        const m = new Map(ocupacion.value);
        m.delete(dto.id);
        ocupacion.value = m;
      }
      return;
    }
    sincronizarOcupacion(dto);
    if (i < 0) {
      puestos.value.push(dto);
      return;
    }
    const local = puestos.value[i];
    const protegida = [...guardias].some((g) => g(dto.id));
    if (protegida) {
      puestos.value[i] = {
        ...dto,
        mapaX: local.mapaX,
        mapaY: local.mapaY,
        mapaEscala: local.mapaEscala,
        mapaRotacion: local.mapaRotacion,
      };
    } else {
      puestos.value[i] = dto;
    }
  }

  function fusionar(lista) {
    if (!guardias.size) {
      puestos.value = lista;
      return;
    }
    const previos = new Map(puestos.value.map((p) => [p.id, p]));
    puestos.value = lista.map((dto) => {
      const local = previos.get(dto.id);
      if (!local || ![...guardias].some((g) => g(dto.id))) return dto;
      return {
        ...dto,
        mapaX: local.mapaX,
        mapaY: local.mapaY,
        mapaEscala: local.mapaEscala,
        mapaRotacion: local.mapaRotacion,
      };
    });
  }

  function aplicarAsignaciones(cambios) {
    if (!Array.isArray(cambios) || !cambios.length) return;
    const m = new Map(asignaciones.value);
    for (const a of cambios) if (a?.puestoId != null) m.delete(a.puestoId);
    for (const a of cambios) {
      if (a?.puestoId == null || a.vendedorId == null) continue;
      if (!m.has(a.puestoId)) m.set(a.puestoId, []);
      m.get(a.puestoId).push(a);
    }
    asignaciones.value = m;
    guardarCache();
  }

  function aplicarOcupacion(filas) {
    if (!Array.isArray(filas)) return;
    const m = new Map();
    for (const o of filas) {
      if (o?.puestoId == null || o.vendedorId == null) continue;
      m.set(o.puestoId, {
        vendedorId: o.vendedorId,
        vendedor: o.vendedor ?? null,
        celular: o.celular ?? null,
        estado: o.estado ?? null,
        desde: o.desde ?? null,
      });
      anotarEnDirectorio(o.vendedorId, o.vendedor, o.celular);
    }
    ocupacion.value = m;
    guardarCache();
  }

  async function cargarOcupacion() {
    const r = await apiFetch('/api/app/puestos/ocupacion', condicional(etagOcupacion));
    if (r.status === 304 || !r.ok) return;
    etagOcupacion = r.headers.get('ETag') || etagOcupacion;
    aplicarOcupacion(await r.json().catch(() => []));
  }

  function anotarEnDirectorio(vendedorId, vendedor, celular) {
    if (vendedorId == null || !vendedor) return;
    const previo = directorio.value.get(vendedorId);
    if (previo && previo.vendedor === vendedor && previo.celular === celular) return;
    directorio.value = new Map(directorio.value).set(vendedorId, { vendedor, celular: celular ?? null });
  }

  function pedirOcupacion() {
    if (pendienteOcupacion) return;
    pendienteOcupacion = setTimeout(() => {
      pendienteOcupacion = null;
      cargarOcupacion().catch(() => {
      });
    }, 700);
  }

  function sincronizarOcupacion(dto) {
    const previa = ocupacion.value.get(dto.id);

    if (dto.estado === 'T' && dto.reservadoPor != null) {
      const quien = directorio.value.get(dto.reservadoPor);
      if (previa && previa.estado === 'T' && previa.vendedorId === dto.reservadoPor) return;
      ocupacion.value = new Map(ocupacion.value).set(dto.id, {
        vendedorId: dto.reservadoPor,
        vendedor: quien?.vendedor ?? null,
        celular: quien?.celular ?? null,
        estado: 'T',
        desde: null,
      });
      if (!quien) pedirOcupacion();
      return;
    }

    if (dto.estado === 'O') {
      if (previa?.estado === 'O') return;
      ocupacion.value = new Map(ocupacion.value).set(dto.id, {
        vendedorId: previa?.vendedorId ?? null,
        vendedor: previa?.vendedor ?? null,
        celular: previa?.celular ?? null,
        estado: 'O',
        desde: null,
      });
      pedirOcupacion();
      return;
    }

    if (previa) {
      const m = new Map(ocupacion.value);
      m.delete(dto.id);
      ocupacion.value = m;
    }
  }

  function protegerLocales(fn) {
    guardias.add(fn);
    return () => guardias.delete(fn);
  }

  function cargar(forzar = false) {
    if (promesaCarga && !forzar) return promesaCarga;
    cargando.value = true;
    error.value = '';
    promesaCarga = (async () => {
      try {
        const [r, rAsig, rOcup] = await Promise.all([
          apiFetch('/api/app/puestos', condicional(etagPuestos)),
          apiFetch('/api/app/puestos/asignaciones', condicional(etagAsignaciones)).catch(() => null),
          apiFetch('/api/app/puestos/ocupacion', condicional(etagOcupacion)).catch(() => null),
        ]);

        if (r.status !== 304) {
          etagPuestos = r.headers.get('ETag') || etagPuestos;
          const lista = await r.json();
          ultimaLista = lista;
          fusionar(lista);
        }
        if (rAsig && rAsig.status !== 304 && rAsig.ok) {
          etagAsignaciones = rAsig.headers.get('ETag') || etagAsignaciones;
          const filas = await rAsig.json().catch(() => []);
          const m = new Map();
          for (const a of filas) {
            if (a?.puestoId == null) continue;
            if (!m.has(a.puestoId)) m.set(a.puestoId, []);
            m.get(a.puestoId).push(a);
            anotarEnDirectorio(a.vendedorId, a.vendedor, a.celular);
          }
          asignaciones.value = m;
        }
        if (rOcup && rOcup.status !== 304 && rOcup.ok) {
          etagOcupacion = rOcup.headers.get('ETag') || etagOcupacion;
          aplicarOcupacion(await rOcup.json().catch(() => []));
        }

        desdeCache.value = false;
        ultimaSync.value = Date.now();
        guardarCache();
      } catch (e) {
        error.value = e.message;
        promesaCarga = null;
        throw e;
      } finally {
        cargando.value = false;
      }
    })();
    return promesaCarga;
  }

  function recargar() {
    return cargar(true);
  }

  const sinAsignaciones = computed(() =>
    !cargando.value && !desdeCache.value && !error.value && puestos.value.length === 0);

  const SONDEO_MS = 5000;

  function arrancarSondeo() {
    if (sondeo) return;
    sondeo = setInterval(() => {
      if (enVivo.value) return;
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
      recargar().catch(() => {});
    }, SONDEO_MS);
    sondeo?.unref?.();
  }

  function alVolverAlFrente() {
    if (typeof document !== 'undefined' && document.visibilityState !== 'visible') return;
    recargar().catch(() => {});
  }

  function escucharCicloDeVida() {
    if (oyentesCiclo || typeof document === 'undefined') return;
    document.addEventListener('visibilitychange', alVolverAlFrente);
    window.addEventListener('online', alVolverAlFrente);
    oyentesCiclo = true;
  }

  function dejarDeEscuchar() {
    if (!oyentesCiclo || typeof document === 'undefined') return;
    document.removeEventListener('visibilitychange', alVolverAlFrente);
    window.removeEventListener('online', alVolverAlFrente);
    oyentesCiclo = false;
  }

  function reintentar() {
    recargar().catch(() => {});
    if (!cliente) conectar();
  }

  function conectar(onRechazo) {
    if (onRechazo) alRechazar = onRechazo;
    escucharCicloDeVida();
    arrancarSondeo();
    if (cliente) return;
    cliente = crearClientePuestos(
      (dto) => { cerrarMedicion(dto.id); aplicarDelServidor(dto); },
      (motivo) => {
        enVivo.value = false;
        cliente = null;
        if (alRechazar) alRechazar(motivo);
      },
      () => {
        enVivo.value = true;
        if (yaConecto) recargar().catch(() => {});
        yaConecto = true;
      },
      (notificacion) => {
        if (notificacion?.tipo === 'ASIGNACION_CAMBIADA') recargar().catch(() => {});
        oyentesNotificacion.forEach((fn) => fn(notificacion));
      },
      () => { enVivo.value = false; },
      aplicarAsignaciones,
    );
  }

  async function asegurar(onRechazo) {
    hidratarDesdeCache();
    conectar(onRechazo);
    return cargar();
  }

  function desconectar() {
    if (cliente) {
      cliente.deactivate();
      cliente = null;
    }
    if (sondeo) {
      clearInterval(sondeo);
      sondeo = null;
    }
    dejarDeEscuchar();
    enVivo.value = false;
    yaConecto = false;
    puestos.value = [];
    promesaCarga = null;
    error.value = '';
    desdeCache.value = false;
    ultimaSync.value = 0;
    asignaciones.value = new Map();
    ocupacion.value = new Map();
    if (pendienteOcupacion) { clearTimeout(pendienteOcupacion); pendienteOcupacion = null; }
    enCola = null;
    ultimaLista = [];
    etagPuestos = null;
    etagAsignaciones = null;
    etagOcupacion = null;
  }

  return {
    puestos, cargando, error, enVivo, desdeCache, ultimaSync, ubicadas, carritoDe, asignaciones,
    ocupacion, directorio,
    aplicar, aplicarDelServidor, aplicarAsignaciones, protegerLocales, cargar, recargar, conectar, asegurar, desconectar,
    registrarNotificaciones, reintentar, sinAsignaciones,
  };
});
