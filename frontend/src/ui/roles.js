const ETIQUETAS = {
  'SUPER USUARIO': 'Superadministrador',
  ADMINISTRADOR: 'Administrador',
  ADMINISTRATIVO: 'Operador de ventas',
  CONTROL: 'Control de acceso',
  ASESORIA: 'Supervisor',
  VERIFICADOR: 'Verificador',
};

export function etiquetaRol(nombre) {
  const clave = String(nombre || '').trim().replace(/\s+/g, ' ').replace(/_/g, ' ').toUpperCase();
  return ETIQUETAS[clave] || (nombre ? String(nombre) : '');
}
