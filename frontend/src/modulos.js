import { defineAsyncComponent } from 'vue';

const vista = (cargar) => defineAsyncComponent(cargar);

export const MODULOS = {
  inicio: { vista: vista(() => import('./views/Inicio.vue')), titulo: 'Inicio', pantalla: 'inicio' },
  mapa: { vista: vista(() => import('./views/Mapa.vue')), titulo: 'Mapa de ventas', inmersivo: true, pantalla: 'mapa' },
  venta: { vista: vista(() => import('./views/Venta.vue')), titulo: 'Registrar venta', pantalla: 'venta' },
  'mis-ventas': { vista: vista(() => import('./views/MisVentas.vue')), titulo: 'Mis ventas', pantalla: 'mis-ventas' },
  mesas: { vista: vista(() => import('./views/GestionMesas.vue')), titulo: 'Eventos con mesas', pantallas: ['mesas', 'mesas-diseno'] },
  actividades: { vista: vista(() => import('./views/Actividades.vue')), titulo: 'Centro de actividades', pantalla: 'actividades', oAdministracion: true },
  catalogo: { vista: vista(() => import('./views/Catalogo.vue')), titulo: 'Catálogo', pantalla: 'catalogo' },
  tablero: { vista: vista(() => import('./views/Board.vue')), titulo: 'Tablero', pantalla: 'tablero' },
  editor: { vista: vista(() => import('./views/Editor.vue')), titulo: 'Editor del plano', inmersivo: true, pantalla: 'editor' },
  categorias: { vista: vista(() => import('./views/Categorias.vue')), titulo: 'Categorías', pantalla: 'categorias' },
  puestos: { vista: vista(() => import('./views/Puestos.vue')), titulo: 'Puestos', pantalla: 'puestos' },
  seguimiento: { vista: vista(() => import('./views/Seguimiento.vue')), titulo: 'Seguimiento en vivo', pantalla: 'seguimiento' },
  anuncios: { vista: vista(() => import('./views/Anuncios.vue')), titulo: 'Anuncios', pantalla: 'anuncios' },
  'responsables-extra': { vista: vista(() => import('./views/ResponsablesExtra.vue')), titulo: 'Responsables extra', pantalla: 'responsables-extra' },
  usuarios: { vista: vista(() => import('./views/Usuarios.vue')), titulo: 'Usuarios', pantalla: 'usuarios' },
  roles: { vista: vista(() => import('./views/RolesPermisos.vue')), titulo: 'Roles y permisos', pantalla: 'roles' },
  permisos: { vista: vista(() => import('./views/RolesPermisos.vue')), titulo: 'Roles y permisos', pantalla: 'permisos' },
  mantenimiento: { vista: vista(() => import('./views/Mantenimiento.vue')), titulo: 'Mantenimiento', soloSuper: true },
  errores: { vista: vista(() => import('./views/Errores.vue')), titulo: 'Registro de errores', soloSuper: true },
  sistema: { vista: vista(() => import('./views/DatosSistema.vue')), titulo: 'Datos del sistema', soloSuper: true },
  whatsapp: { vista: vista(() => import('./views/WhatsApp.vue')), titulo: 'Instancias de WhatsApp', soloAdministracion: true },
  personas: { vista: vista(() => import('./views/Personas.vue')), titulo: 'Personas', pantalla: 'personas' },
  'personal-apoyo': { vista: vista(() => import('./views/PersonalApoyo.vue')), titulo: 'Personal de apoyo', pantalla: 'personal-apoyo' },
  dashboard: { vista: vista(() => import('./views/Dashboard.vue')), titulo: 'Dashboard', pantallas: ['direccion', 'control-ventas', 'reportes'] },
  'control-ventas': { vista: vista(() => import('./views/ControlVentas.vue')), titulo: 'Ventas', pantalla: 'control-ventas' },
  'reporte-responsables': { vista: vista(() => import('./views/ReporteResponsables.vue')), titulo: 'Reporte de Responsables', pantalla: 'reporte-responsables' },
  inscripciones: { vista: vista(() => import('./views/Inscripciones.vue')), titulo: 'Inscripciones', pantalla: 'inscripciones' },
  interesados: { vista: vista(() => import('./views/Interesados.vue')), titulo: 'Interesados en exponer', pantalla: 'interesados' },
  notificaciones: { vista: vista(() => import('./views/Notificaciones.vue')), titulo: 'Notificaciones', pantalla: 'notificaciones' },
  vendedores: { vista: vista(() => import('./views/Vendedores.vue')), titulo: 'Vendedores', pantalla: 'vendedores' },
  credenciales: { vista: vista(() => import('./views/Credenciales.vue')), titulo: 'Credenciales', pantalla: 'credenciales' },
  'credenciales-diseno': { vista: vista(() => import('./views/DisenoCredenciales.vue')), titulo: 'Diseño de credenciales', pantalla: 'credenciales-diseno', oAdministracion: true, completo: true },
  'credenciales-apoyo': { vista: vista(() => import('./views/CredencialesApoyo.vue')), titulo: 'Credenciales personal apoyo', pantalla: 'credenciales-apoyo' },
  'impresion-masiva': { vista: vista(() => import('./views/ImpresionMasiva.vue')), titulo: 'Impresión Masiva', pantalla: 'impresion-masiva' },
  escaner: { vista: vista(() => import('./views/Escaner.vue')), titulo: 'Escanear credencial', pantalla: 'escaner' },
  'control-acceso': { vista: vista(() => import('./views/ControlAcceso.vue')), titulo: 'Control de acceso', pantalla: 'control-acceso' },
};

export const VISTAS_EN_CACHE = ['Mapa', 'Editor', 'Board'];

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
  'mesas-gestion': 'mesas',
  direccion: ['dashboard', { tab: 'resumen' }],
  reportes: ['dashboard', { tab: 'reportes' }],
};

export function destinoAntiguo(path, query) {
  const partes = path.replace(/^\/+|\/+$/g, '').split('/');
  const [primera, segunda] = partes;
  if (primera === 'credencial' && segunda) return { path: '/', query: { ...query, credencial: partes.slice(1).join('/') } };
  if (primera === 'mesas' && segunda) return { path: '/', query: { ...query, evento: segunda } };
  if (primera === 'reserva' && segunda) return { path: '/', query: { ...query, reserva: segunda } };
  if (primera === 'feria') return { path: '/', query: { ...query, v: 'actividad' } };
  if (primera === 'login') return { path: '/admin' };
  const antigua = ANTIGUAS[primera];
  if (Array.isArray(antigua)) return aModulo(antigua[0], { ...query, ...antigua[1] });
  if (antigua) return aModulo(antigua, query);
  if (MODULOS[primera]) return aModulo(primera, query);
  return { path: '/' };
}
