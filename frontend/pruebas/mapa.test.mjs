import test from 'node:test';
import assert from 'node:assert/strict';

globalThis.localStorage ??= {
  datos: new Map(),
  getItem(k) { return this.datos.has(k) ? this.datos.get(k) : null; },
  setItem(k, v) { this.datos.set(k, String(v)); },
};

const { medidaLegible, estiloPin, tamanoDe } = await import('../src/mapa.js');

test('medidaLegible muestra metros con signo de multiplicar', () => {
  assert.equal(medidaLegible('3x3'), '3 × 3 m');
  assert.equal(medidaLegible('2.5 X 4'), '2.5 × 4 m');
  assert.equal(medidaLegible('6×3 m'), '6 × 3 m');
});

test('medidaLegible respeta textos libres y vacios', () => {
  assert.equal(medidaLegible('Doble esquina'), 'Doble esquina');
  assert.equal(medidaLegible(''), 'Sin medida');
  assert.equal(medidaLegible(null), 'Sin medida');
});

test('estiloPin aplica la proporcion alto/ancho de la categoria', () => {
  const base = { mapaX: 0.5, mapaY: 0.5, tamanoMapa: 0.01, mapaEscala: 2 };
  assert.equal(estiloPin(base)['--alto'], '100px');
  assert.equal(estiloPin({ ...base, mapaProporcion: 0.5 })['--alto'], '50px');
  assert.equal(tamanoDe(base), 0.02);
});
