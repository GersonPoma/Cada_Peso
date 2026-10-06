import { describe, expect, it } from 'vitest';
import { evaluarMonto } from './evaluar-monto';

describe('evaluarMonto', () => {
  it.each([
    ['30+20+50', 'en-US', 100000],
    ['12,5*2', 'es-BO', 25000],
    ['-5+2', 'en-US', -3000],
    ['2+3*4', 'en-US', 14000],
    ['10-2*3+1', 'en-US', 5000],
    ['5--2', 'en-US', 7000],
    ['5*-2', 'en-US', -10000],
    ['10/3', 'en-US', 3333],
    ['2/3', 'en-US', 667],
    ['-2/3', 'en-US', -667],
    ['10/3*3', 'en-US', 10000],
    ['0.1+0.2', 'en-US', 300],
    ['0,001', 'es-BO', 1],
    ['1.234,567', 'es-BO', 1234567],
    ['1,234.567', 'en-US', 1234567],
    ['0,001/2', 'es-BO', 1],
    ['-0,001/2', 'es-BO', -1],
    ['0,001/3', 'es-BO', 0],
    [' 30 + 20 ', 'en-US', 50000],
    ['5 + -2', 'en-US', 3000],
    ['42', 'en-US', 42000],
    ['-0', 'en-US', 0],
  ])('%s en %s es %d milésimas', (texto, region, esperado) => {
    expect(evaluarMonto(texto, region)).toBe(esperado);
  });

  it('0.1+0.2 es exacto: la coma flotante daría 0.30000000000000004', () => {
    expect(0.1 + 0.2).not.toBe(0.3);
    expect(evaluarMonto('0.1+0.2', 'en-US')).toBe(300);
  });

  it.each([
    ['1/0'],
    ['5/(1-1)'],
    ['5++2'],
    ['5+'],
    ['5*'],
    [''],
    ['   '],
    ['abc'],
    ['5+abc'],
    ['12.3456'],
    ['(1+2)'],
    ['*5'],
    ['--5'],
    ['5/0*3'],
    ['999999999999*999999999'],
  ])('%s no tiene resultado', (texto) => {
    expect(evaluarMonto(texto, 'en-US')).toBeNull();
  });

  it('usa la región del usuario por defecto', () => {
    expect(typeof evaluarMonto('1')).toBe('number');
  });
});
