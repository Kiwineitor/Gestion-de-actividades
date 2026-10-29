import { defineStore } from 'pinia';
import { url as urlApi } from '../config.js';

const ROLES_EDITAN_PLANO = ['SUPER_USUARIO', 'ADMINISTRADOR'];

const normalizar = (rol) => (rol || '').toUpperCase().replace(/ /g, '_');

const CACHE_PERMISOS = 'permisos.cache.v1';

function caducaEn(token) {
  const exp = delToken(token, 'exp');
  return typeof exp === 'number' ? exp * 1000 : 0;
}

const MARGEN_MS = 10_000;

function delToken(token, clave) {
  try {
    const carga = String(token).split('.')[1];
    if (!carga) return null;
    return JSON.parse(atob(carga.replace(/-/g, '+').replace(/_/g, '/')))[clave] ?? null;
  } catch {
    return null;
  }
}

function idInicial(token) {
  const guardado = Number(localStorage.getItem('id'));
  if (guardado) return guardado;
  const delJwt = Number(delToken(token, 'uid'));
  return delJwt || null;
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    id: idInicial(localStorage.getItem('token') || ''),
    usuario: localStorage.getItem('usuario') || '',
    rol: localStorage.getItem('rol') || '',
  }),
  getters: {
    autenticado: (s) => {
      if (!s.token) return false;
      const caduca = caducaEn(s.token);
      return caduca === 0 || caduca - MARGEN_MS > Date.now();
    },
    puedeEditarPlano: (s) => ROLES_EDITAN_PLANO.includes(normalizar(s.rol)),
    esSuperUsuario: (s) => normalizar(s.rol) === 'SUPER_USUARIO',
    esVendedor: (s) => normalizar(s.rol) === 'ADMINISTRATIVO',
    veTodasLasInscripciones: (s) => ['SUPER_USUARIO', 'ADMINISTRADOR', 'VERIFICADOR', 'ASESORIA']
      .includes(normalizar(s.rol)),
    veTodasLasCredenciales: (s) => ['SUPER_USUARIO', 'ADMINISTRADOR', 'VERIFICADOR']
      .includes(normalizar(s.rol)),
  },
  actions: {
    async login(usuario, contrasena) {
      const res = await fetch(urlApi('/api/auth/login'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ usuario, contrasena }),
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.ok) {
        const error = new Error(data.mensaje || 'No se pudo iniciar sesion');
        error.codigo = data.codigo || '';
        throw error;
      }
      this.token = data.token;
      this.id = Number(data.id) || Number(delToken(data.token, 'uid')) || null;
      this.usuario = data.usuario;
      this.rol = data.rol;
      localStorage.setItem('token', this.token);
      if (this.id != null) localStorage.setItem('id', String(this.id));
      localStorage.setItem('usuario', this.usuario);
      localStorage.setItem('rol', this.rol);
    },
    logout() {
      this.token = '';
      this.id = null;
      this.usuario = '';
      this.rol = '';
      localStorage.removeItem('token');
      localStorage.removeItem('id');
      localStorage.removeItem('usuario');
      localStorage.removeItem('rol');
      try { localStorage.removeItem(CACHE_PERMISOS); } catch { }
    },
  },
});
