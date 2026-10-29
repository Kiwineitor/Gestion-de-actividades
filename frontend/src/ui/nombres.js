export function partirNombre(completo) {
  const partes = String(completo || '').trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return { nombre: '', paterno: '', materno: '' };
  if (partes.length === 1) return { nombre: partes[0], paterno: '', materno: '' };
  if (partes.length === 2) return { nombre: partes[0], paterno: partes[1], materno: '' };
  return {
    nombre: partes.slice(0, -2).join(' '),
    paterno: partes[partes.length - 2],
    materno: partes[partes.length - 1],
  };
}
