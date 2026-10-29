import { ref, watch } from 'vue';

export const CLASE_ESTADO = { L: 'libre', T: 'tramite', O: 'ocupado', X: 'bloqueado' };

export const ETIQUETA_ESTADO = {
  L: 'Libre',
  T: 'En trámite',
  O: 'Ocupado',
  X: 'Bloqueado',
};

export const LEYENDA = [
  { clase: 'libre', txt: 'Libre' },
  { clase: 'mia', txt: 'En mi venta' },
  { clase: 'tramite', txt: 'Otro vendedor' },
  { clase: 'ocupado', txt: 'Vendida' },
  { clase: 'bloqueado', txt: 'Bloqueada' },
];

export function tamanoDe(p) {
  return (p.tamanoMapa ?? 0.012) * (p.mapaEscala ?? 1);
}

export function estiloPin(p) {
  return {
    left: `${p.mapaX * 100}%`,
    top: `${p.mapaY * 100}%`,
    '--pin': `calc(var(--mundo, 1200) * ${tamanoDe(p)})`,
    '--giro': `${p.mapaRotacion || 0}deg`,
    '--alto': `${100 * (p.mapaProporcion ?? 1)}px`,
  };
}

export function medidaLegible(tamano) {
  const t = String(tamano ?? '').trim();
  if (!t) return 'Sin medida';
  const m = t.match(/^(\d+(?:[.,]\d+)?)\s*[xX×*]\s*(\d+(?:[.,]\d+)?)\s*(m)?$/);
  return m ? `${m[1]} × ${m[2]} m` : t;
}

export const numerosVisibles = ref(localStorage.getItem('mapa.numeros') !== '0');
watch(numerosVisibles, (v) => localStorage.setItem('mapa.numeros', v ? '1' : '0'));

export const PIN_COMODO_PX = 44;
export const PIN_LEGIBLE_PX = 22;

function tamanoTipico(puestos) {
  const tams = puestos.map(tamanoDe).filter((t) => t > 0).sort((a, b) => a - b);
  return tams.length ? tams[Math.floor(tams.length / 2)] : 0.012;
}

function tamanoMinimo(puestos) {
  const tams = puestos.map(tamanoDe).filter((t) => t > 0);
  return tams.length ? Math.min(...tams) : 0.012;
}

export function anchoParaTocar(puestos) {
  return PIN_COMODO_PX / tamanoMinimo(puestos);
}

export function anchoParaLeer(puestos) {
  return PIN_LEGIBLE_PX / tamanoTipico(puestos);
}

export function ordenLectura(lista, aspecto = 2376 / 1836, columnas = false) {
  const ubicadas = lista.filter((p) => p.mapaX != null && p.mapaY != null);
  if (!ubicadas.length) return [];

  const eje = columnas ? 'mapaX' : 'mapaY';
  const cruz = columnas ? 'mapaY' : 'mapaX';
  const lado = Math.max(0.004, ...ubicadas.map(tamanoDe));
  const tolerancia = columnas ? lado : lado / aspecto;

  const bandas = [];
  for (const p of [...ubicadas].sort((a, b) => a[eje] - b[eje])) {
    const ultima = bandas[bandas.length - 1];
    if (ultima && p[eje] - ultima.ref <= tolerancia) ultima.items.push(p);
    else bandas.push({ ref: p[eje], items: [p] });
  }
  return bandas.flatMap((b) => b.items.sort((a, b2) => a[cruz] - b2[cruz]));
}

export function numeracionCompacta(puestos, categoriaId) {
  const dela = puestos
    .filter((p) => p.categoriaId === categoriaId)
    .sort((a, b) => (parseInt(a.codigo) || 0) - (parseInt(b.codigo) || 0));
  return dela
    .map((p, i) => ({ id: p.id, codigo: String(i + 1) }))
    .filter((c, i) => String(dela[i].codigo ?? '').trim() !== c.codigo);
}

