import test from 'node:test';
import assert from 'node:assert/strict';
import { mock } from 'node:test';

let peticiones = 0;
let respuesta = [];
let fallaRed = false;
mock.module('../src/api.js', {
  namedExports: {
    apiFetch: async (ruta) => {
      if (String(ruta).endsWith('/api/app/puestos')) peticiones++;
      if (fallaRed) throw new Error('sin red');
      return {
        ok: true,
        status: 200,
        headers: new Headers({ ETag: `"prueba-${peticiones}"` }),
        json: async () => structuredClone(respuesta),
      };
    },
  },
});

let conexiones = 0;
let desconexiones = 0;
let empujar = null;
let rechazar = null;
mock.module('../src/ws.js', {
  namedExports: {
    crearClientePuestos: (onEstado, onRechazo, onConectado) => {
      conexiones++;
      empujar = onEstado;
      rechazar = onRechazo;
      if (onConectado) onConectado();
      return { deactivate: () => { desconexiones++; } };
    },
  },
});

const { createPinia, setActivePinia } = await import('pinia');
const { usePuestosStore } = await import('../src/stores/puestos.js');

function nuevaTienda() {
  peticiones = 0; conexiones = 0; desconexiones = 0;
  setActivePinia(createPinia());
  return usePuestosStore();
}

const caseta = (id, extra = {}) => ({
  id, codigo: String(id), categoria: 'ZONA', estado: 'L',
  mapaX: 0.5, mapaY: 0.5, mapaEscala: 1, activo: true, ...extra,
});

test('las cargas concurrentes comparten una sola peticion', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1), caseta(2)];
  await Promise.all([t.cargar(), t.cargar(), t.cargar()]);
  assert.equal(peticiones, 1, 'deberia bajarse la lista una sola vez');
  assert.equal(t.puestos.length, 2);
});

test('asegurar() abre una unica conexion aunque lo llamen las tres vistas', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1)];
  await Promise.all([t.asegurar(), t.asegurar(), t.asegurar()]);
  assert.equal(conexiones, 1, 'una sola conexion para toda la app');
  assert.equal(t.enVivo, true);
});

test('recargar() si fuerza una peticion nueva', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1)];
  await t.cargar();
  respuesta = [caseta(1), caseta(2)];
  await t.recargar();
  assert.equal(peticiones, 2);
  assert.equal(t.puestos.length, 2);
});

test('un broadcast actualiza, agrega y da de baja casetas', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1)];
  await t.asegurar();

  empujar(caseta(1, { estado: 'T' }));
  assert.equal(t.puestos[0].estado, 'T', 'actualiza el estado de venta');

  empujar(caseta(2));
  assert.equal(t.puestos.length, 2, 'un alta aparece sola');

  empujar({ id: 2, activo: false });
  assert.equal(t.puestos.length, 1, 'una caseta anulada desaparece');
});

test('una ráfaga del servidor cuesta DOS pasadas, no una por mensaje', async () => {
  const pendientes = [];
  const rafOriginal = globalThis.requestAnimationFrame;
  globalThis.requestAnimationFrame = (fn) => { pendientes.push(fn); return pendientes.length; };
  try {
    const t = nuevaTienda();
    respuesta = [caseta(1), caseta(2), caseta(3)];
    await t.asegurar();

    empujar(caseta(1, { estado: 'T' }));
    empujar(caseta(2, { estado: 'T' }));
    empujar(caseta(3, { estado: 'T' }));

    assert.equal(pendientes.length, 1,
      'tres mensajes abren UNA sola ventana, no tres');
    assert.equal(t.puestos.find((p) => p.id === 1).estado, 'T',
      'el primero se aplica en el acto: una caseta suelta no espera un fotograma');
    assert.equal(t.puestos.filter((p) => p.estado === 'T').length, 1,
      'y los de detras esperan a la ventana en vez de repintar cada uno');

    pendientes.pop()();
    assert.equal(t.puestos.filter((p) => p.estado === 'T').length, 3,
      'al cerrarse la ventana entran los dos restantes de una vez');

    empujar(caseta(1, { estado: 'O' }));
    empujar(caseta(1, { estado: 'L' }));
    empujar(caseta(1, { estado: 'X' }));
    pendientes.pop()();
    assert.equal(t.puestos.find((p) => p.id === 1).estado, 'X',
      'de varios mensajes de la misma caseta gana el mas nuevo');

    empujar(caseta(3, { estado: 'O' }));
    empujar(caseta(2, { estado: 'L' }));
    t.aplicar(caseta(2, { estado: 'T' }));
    assert.equal(t.puestos.find((p) => p.id === 2).estado, 'T',
      'el cambio local queda ENCIMA de lo que habia encolado');
    if (pendientes.length) pendientes.pop()();
    assert.equal(t.puestos.find((p) => p.id === 2).estado, 'T',
      'y el fotograma pendiente no lo deshace');
  } finally {
    globalThis.requestAnimationFrame = rafOriginal;
  }
});

test('un broadcast NO pisa la geometria que el Editor no ha guardado', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1, { mapaX: 0.1, mapaY: 0.1 })];
  await t.asegurar();

  const sucias = new Set([1]);
  const quitar = t.protegerLocales((id) => sucias.has(id));
  t.puestos[0].mapaX = 0.9;
  t.puestos[0].mapaY = 0.8;

  empujar(caseta(1, { mapaX: 0.1, mapaY: 0.1, estado: 'O' }));

  assert.equal(t.puestos[0].mapaX, 0.9, 'conserva el arrastre sin guardar');
  assert.equal(t.puestos[0].mapaY, 0.8, 'conserva el arrastre sin guardar');
  assert.equal(t.puestos[0].estado, 'O', 'pero si acepta el estado de venta del servidor');

  quitar();
  empujar(caseta(1, { mapaX: 0.1, mapaY: 0.1, estado: 'O' }));
  assert.equal(t.puestos[0].mapaX, 0.1, 'sin cambios pendientes, manda el servidor');
});

test('el carrito son las casetas en tramite del propio vendedor', async () => {
  const t = nuevaTienda();
  respuesta = [
    caseta(1, { estado: 'T', reservadoPor: 7 }),
    caseta(2, { estado: 'T', reservadoPor: 9 }),
    caseta(3, { estado: 'L' }),
    caseta(4, { estado: 'O', reservadoPor: 7 }),
  ];
  await t.cargar();

  assert.deepEqual(t.carritoDe(7).map((p) => p.id), [1]);
  assert.deepEqual(t.carritoDe(9).map((p) => p.id), [2]);
  assert.deepEqual(t.carritoDe(999), [], 'un vendedor sin reservas tiene el carrito vacio');
});

test('el carrito se rehace solo al llegar un broadcast (sobrevive a recargar)', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1, { estado: 'L' })];
  await t.asegurar();
  assert.equal(t.carritoDe(7).length, 0);

  empujar(caseta(1, { estado: 'T', reservadoPor: 7 }));
  assert.deepEqual(t.carritoDe(7).map((p) => p.id), [1], 'entra al carrito sin pedir nada');

  empujar(caseta(1, { estado: 'L', reservadoPor: null }));
  assert.equal(t.carritoDe(7).length, 0);
});

test('desconectar() cierra la conexion y vacia el estado', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1)];
  await t.asegurar();
  t.desconectar();
  assert.equal(desconexiones, 1, 'se cierra el cliente STOMP');
  assert.equal(t.enVivo, false);
  assert.equal(t.puestos.length, 0, 'no queda estado de la sesion anterior');
});

test('tras desconectar, una vista nueva vuelve a conectar (no queda inservible)', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1)];
  await t.asegurar();
  t.desconectar();
  await t.asegurar();
  assert.equal(conexiones, 2, 'la segunda sesion abre su propia conexion');
  assert.equal(t.puestos.length, 1);
});

test('un token rechazado avisa a la vista y marca que ya no hay tiempo real', async () => {
  const t = nuevaTienda();
  respuesta = [caseta(1)];
  let motivo = null;
  await t.asegurar((m) => { motivo = m; });
  rechazar('token expirado');
  assert.equal(motivo, 'token expirado');
  assert.equal(t.enVivo, false, 'el mapa no debe aparentar estar en vivo');
});

test('un fallo de red no deja la carga bloqueada para siempre', async () => {
  const t = nuevaTienda();

  fallaRed = true;
  await assert.rejects(() => t.cargar(), /sin red/);
  assert.equal(t.error, 'sin red');
  assert.equal(t.cargando, false, 'no se queda con el indicador de carga encendido');

  fallaRed = false;
  respuesta = [caseta(1)];
  await t.cargar();
  assert.equal(peticiones, 2, 'el reintento si sale a la red');
  assert.equal(t.puestos.length, 1);
  assert.equal(t.error, '', 'se limpia el error anterior');
});
