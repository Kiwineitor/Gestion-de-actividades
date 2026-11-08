export const CANALES = {
  EN_LINEA: { icono: '🌐', texto: 'En línea', rol: 'Aprobó', clase: 'en-linea' },
  EN_PERSONA: { icono: '🏢', texto: 'En persona', rol: 'Registró', clase: 'en-persona' },
};

export const canalDe = (c) => CANALES[c] || CANALES.EN_PERSONA;
