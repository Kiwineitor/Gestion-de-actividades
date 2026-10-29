import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const fuente = readFileSync(new URL('../src/components/CampoCelular.vue', import.meta.url), 'utf8');

test('el componente declara Bolivia y Brasil con sus codigos', () => {
  assert.match(fuente, /codigo: '591'/);
  assert.match(fuente, /codigo: '55'/);
});

test('guarda sin "+" ni separadores', () => {
  assert.match(fuente, /paisActual\.value\.codigo \+ n/);
  assert.doesNotMatch(fuente, /'\+' \+/);
});

test('con el campo vacio NO guarda el codigo suelto', () => {
  assert.match(fuente, /n \? paisActual\.value\.codigo \+ n : ''/);
});

test('para reconocer un prefijo exige ademas que el largo cuadre', () => {
  assert.match(fuente, /p\.largo\.includes\(d\.length - p\.codigo\.length\)/);
});
