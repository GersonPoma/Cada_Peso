import { describe, expect, it } from 'vitest';
import { aMilliunits, deMilliunits, leerMonto } from './milliunits';

describe('leerMonto y aMilliunits', () => {
  it.each([
    ['0', 'en-US', 0],
    ['-5', 'en-US', -5000],
    ['-0.5', 'en-US', -500],
    ['-0', 'en-US', 0],
    ['0,001', 'es-BO', 1],
    ['1.234,567', 'es-BO', 1234567],
    ['1,234.567', 'en-US', 1234567],
    ['1234,5', 'es-BO', 1234500],
    [',5', 'es-BO', 500],
    ['5,', 'es-BO', 5000],
    ['  12,3  ', 'es-BO', 12300],
    ['0.29', 'en-US', 290],
    ['1.005', 'en-US', 1005],
    ['−7', 'en-US', -7000],
    ['1 234,5', 'fr-FR', 1234500],
    ['1 234,5', 'fr-FR', 1234500],
    ['9007199254740.991', 'en-US', 9007199254740991],
  ])('%s en %s son %d milésimas', (texto, region, esperado) => {
    expect(leerMonto(texto, region)).toEqual({ estado: 'valido', milliunits: esperado });
    expect(aMilliunits(texto, region)).toBe(esperado);
  });

  it('1.005 da exactamente 1005, sin el error de 1.005 * 1000 en coma flotante', () => {
    expect(1.005 * 1000).not.toBe(1005);
    expect(aMilliunits('1.005', 'en-US')).toBe(1005);
  });

  it.each([
    ['12.3456', 'en-US'],
    ['1,5000', 'es-BO'],
    ['0,0001', 'es-BO'],
  ])('%s en %s se rechaza por tener más de 3 decimales', (texto, region) => {
    expect(leerMonto(texto, region)).toEqual({ estado: 'invalido', motivo: 'decimales' });
    expect(aMilliunits(texto, region)).toBeNull();
  });

  it.each([
    ['abc', 'en-US'],
    ['1,2,3', 'es-BO'],
    ['12.5', 'es-BO'],
    ['1.23.456', 'es-BO'],
    ['1,23', 'en-US'],
    ['--1', 'en-US'],
    ['1e3', 'en-US'],
    ['-', 'en-US'],
    [',', 'es-BO'],
    ['+5', 'en-US'],
    ['5-', 'en-US'],
    ['$5', 'en-US'],
  ])('%s en %s se rechaza por formato', (texto, region) => {
    expect(leerMonto(texto, region)).toEqual({ estado: 'invalido', motivo: 'formato' });
    expect(aMilliunits(texto, region)).toBeNull();
  });

  it('el mismo texto se lee según la región: 1.234 son 1234 unidades en es-BO', () => {
    expect(aMilliunits('1.234', 'es-BO')).toBe(1234000);
    expect(aMilliunits('1.234', 'en-US')).toBe(1234);
  });

  it('un entero que no cabe en un número exacto se rechaza por rango', () => {
    expect(leerMonto('12345678901234567890', 'en-US')).toEqual({
      estado: 'invalido',
      motivo: 'rango',
    });
  });

  it.each(['', '   '])('"%s" es vacío, no un error', (texto) => {
    expect(leerMonto(texto, 'es-BO')).toEqual({ estado: 'vacio' });
    expect(aMilliunits(texto, 'es-BO')).toBeNull();
  });
});

describe('deMilliunits', () => {
  it.each([
    [1234567, 'es-BO', '1234,567'],
    [-500, 'es-BO', '-0,5'],
    [5000, 'es-BO', '5'],
    [0, 'es-BO', '0'],
    [-0, 'es-BO', '0'],
    [1, 'es-BO', '0,001'],
    [10, 'es-BO', '0,01'],
    [1234567, 'en-US', '1234.567'],
    [-9007199254740991, 'en-US', '-9007199254740.991'],
  ])('%d en %s se escribe %s', (milliunits, region, esperado) => {
    expect(deMilliunits(milliunits, region)).toBe(esperado);
  });

  it.each([0, 1, -1, 999, 1000, 1001, -250000, 5000500, 9007199254740991])(
    'aMilliunits(deMilliunits(%d)) devuelve el mismo valor',
    (milliunits) => {
      for (const region of ['es-BO', 'en-US', 'fr-FR']) {
        expect(aMilliunits(deMilliunits(milliunits, region), region)).toBe(milliunits);
      }
    },
  );
});
