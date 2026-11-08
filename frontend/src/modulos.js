import { defineAsyncComponent } from 'vue';

const vista = (cargar) => defineAsyncComponent(cargar);

export const MODULOS = {
  inicio: { vista: vista(() => import('./views/Inicio.vue')), titulo: 'Inicio', pantalla: 'inicio' },
  mapa: { vista: vista(() => import('./views/Mapa.vue')), titulo: 'Mapa de ventas', inmersivo: true, pantalla: 'mapa' },
  'punto-venta': { vista: vista(() => import('./views/PuntoVenta.vue')), titulo: 'Vender', pantalla: 'mapa' },
  'mis-ventas': { vista: vista(() => import('./views/MisVentas.vue')), titulo: 'Mis ventas', pantalla: 'mis-ventas' },
  cobros: { vista: vista(() => import('./views/VentasCobros.vue')), titulo: 'Ventas y cobros', pantallas: ['interesados', 'mesas', 'control-ventas', 'reportes', 'direccion'], oAdministracion: true },
  solicitudes: { vista: vista(() => import('./views/Solicitudes.vue')), titulo: 'Pagos por validar', pantallas: ['mesas', 'interesados'], oAdministracion: true },
  ingreso: { vista: vista(() => import('./views/IngresoCentro.vue')), titulo: 'Recepción e ingreso', pantallas: ['mesas-ingreso', 'mesas', 'escaner', 'control-acceso'], oAdministracion: true },
  actividades: { vista: vista(() => import('./views/Actividades.vue')), titulo: 'Mis actividades', pantalla: 'actividades', oAdministracion: true },
  actividad: { vista: vista(() => import('./views/ActividadAsistente.vue')), titulo: 'Configurar actividad', pantalla: 'actividades', oAdministracion: true },
  catalogo: { vista: vista(() => import('./views/Catalogo.vue')), titulo: 'Catálogo de precios', pantalla: 'catalogo' },
  editor: { vista: vista(() => import('./views/Editor.vue')), titulo: 'Editor del plano', inmersivo: true, pantalla: 'editor' },
  categorias: { vista: vista(() => import('./views/CategoriasCentro.vue')), titulo: 'Categorías y precios', pantallas: ['categorias', 'puestos'] },
  seguimiento: { vista: vista(() => import('./views/Seguimiento.vue')), titulo: 'Seguimiento en vivo', pantalla: 'seguimiento' },
  anuncios: { vista: vista(() => import('./views/Anuncios.vue')), titulo: 'Anuncios y promociones', pantalla: 'anuncios' },
  usuarios: { vista: vista(() => import('./views/Usuarios.vue')), titulo: 'Usuarios', pantalla: 'usuarios' },
  roles: { vista: vista(() => import('./views/RolesPermisos.vue')), titulo: 'Roles y permisos', pantalla: 'roles' },
  permisos: { vista: vista(() => import('./views/RolesPermisos.vue')), titulo: 'Roles y permisos', pantalla: 'permisos' },
  historial: { vista: vista(() => import('./views/Historial.vue')), titulo: 'Historial de acciones', pantalla: 'historial', oAdministracion: true },
  mantenimiento: { vista: vista(() => import('./views/Mantenimiento.vue')), titulo: 'Mantenimiento', soloSuper: true },
  errores: { vista: vista(() => import('./views/Errores.vue')), titulo: 'Registro de errores', soloSuper: true },
  sistema: { vista: vista(() => import('./views/DatosSistema.vue')), titulo: 'Datos del sistema', soloSuper: true },
  whatsapp: { vista: vista(() => import('./views/WhatsApp.vue')), titulo: 'WhatsApp', soloAdministracion: true },
  reportes: { vista: vista(() => import('./views/ReportesCentro.vue')), titulo: 'Reportes y descargas', pantallas: ['direccion', 'control-ventas', 'reportes', 'reporte-responsables'], oAdministracion: true },
  inscripciones: { vista: vista(() => import('./views/InscripcionesCentro.vue')), titulo: 'Detalle de ventas', pantallas: ['inscripciones', 'control-ventas', 'reportes', 'direccion', 'responsables-extra'], oAdministracion: true },
  interesados: { vista: vista(() => import('./views/Interesados.vue')), titulo: 'Interesados en exponer', pantalla: 'interesados' },
  credenciales: {
    vista: vista(() => import('./views/CredencialesCentro.vue')), titulo: 'Credenciales e impresión',
    pantallas: ['credenciales-diseno', 'personal-apoyo', 'credenciales-apoyo', 'impresion-masiva'], oAdministracion: true,
    completo: (query) => !query.tab || query.tab === 'diseno',
  },
};

const ALIAS = {
  mesas: ['actividades'],
  'organizar-feria': ['actividad'],
  venta: ['mapa'],
  tablero: ['mapa'],
  notificaciones: ['inicio'],
  dashboard: ['reportes'],
  'control-ventas': ['inscripciones', { tab: 'ventas' }],
  'responsables-extra': ['inscripciones', { tab: 'extras' }],
  'reporte-responsables': ['reportes', { tab: 'responsables' }],
  vendedores: ['reportes', { tab: 'usuarios' }],
  'credenciales-diseno': ['credenciales', { tab: 'diseno' }],
  'credenciales-apoyo': ['credenciales', { tab: 'apoyo' }],
  'personal-apoyo': ['credenciales', { tab: 'apoyo' }],
  'impresion-masiva': ['credenciales', { tab: 'impresion' }],
  escaner: ['ingreso', { tab: 'escanear' }],
  'control-acceso': ['ingreso', { tab: 'movimientos' }],
  puestos: ['categorias', { tab: 'puestos' }],
  personas: ['usuarios'],
};

export function aliasDe(query) {
  const destino = typeof query?.m === 'string' ? ALIAS[query.m] : null;
  if (!destino) return null;
  const { m, ...resto } = query;
  return aModulo(destino[0], { ...resto, ...(destino[1] || {}) });
}

export function esCompleto(modulo, query) {
  return typeof modulo?.completo === 'function' ? modulo.completo(query || {}) : Boolean(modulo?.completo);
}

export function claveModulo(route) {
  const m = route.query?.m;
  return typeof m === 'string' && MODULOS[m] ? m : 'inicio';
}

export function moduloActual(route) {
  return MODULOS[claveModulo(route)];
}

export function aModulo(m, query = {}) {
  return { path: '/admin', query: !m || m === 'inicio' ? { ...query } : { m, ...query } };
}

export function puedeAbrir(modulo, auth, permisos) {
  if (!modulo) return false;
  if (modulo.soloSuper) return auth.esSuperUsuario;
  if (modulo.soloAdministracion) return auth.puedeEditarPlano;
  if (modulo.oAdministracion && auth.puedeEditarPlano) return true;
  if (modulo.pantallas) return modulo.pantallas.some((p) => permisos.puedeVer(p));
  if (modulo.pantalla) return permisos.puedeVer(modulo.pantalla);
  return true;
}

const ANTIGUAS = {
  '': 'inicio',
  'mesas-gestion': 'actividades',
  direccion: ['reportes', { tab: 'resumen' }],
};

export function destinoAntiguo(path, query) {
  const partes = path.replace(/^\/+|\/+$/g, '').split('/');
  const [primera, segunda] = partes;
  if (primera === 'credencial' && segunda) return { path: '/', query: { ...query, credencial: partes.slice(1).join('/') } };
  if ((primera === 'mesas' || primera === 'reserva') && segunda) return { path: '/' };
  if (primera === 'feria') return { path: '/', query: { ...query, v: 'actividad' } };
  if (primera === 'login') return { path: '/admin' };
  const antigua = ANTIGUAS[primera];
  if (Array.isArray(antigua)) return aModulo(antigua[0], { ...query, ...antigua[1] });
  if (antigua) return aModulo(antigua, query);
  if (ALIAS[primera]) return aliasDe({ ...query, m: primera });
  if (MODULOS[primera]) return aModulo(primera, query);
  return { path: '/' };
}
