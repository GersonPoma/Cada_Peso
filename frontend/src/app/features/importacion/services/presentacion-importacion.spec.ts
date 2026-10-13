import { describe, expect, it } from 'vitest';
import { formatearTamano, textoFilasDeDatos, textosTotales } from './presentacion-importacion';

describe('formatearTamano', () => {
  it('bytes, KB y MB con el formato de la región', () => {
    expect(formatearTamano(512, 'en-US')).toBe('512 bytes');
    expect(formatearTamano(3 * 1024, 'en-US')).toBe('3 KB');
    expect(formatearTamano(1536, 'en-US')).toBe('1.5 KB');
    expect(formatearTamano(2 * 1024 * 1024, 'en-US')).toBe('2 MB');
  });

  it('usa el decimal de la región', () => {
    expect(formatearTamano(1536, 'es-BO')).toBe('1,5 KB');
  });
});

describe('textoFilasDeDatos', () => {
  it('singular y plural', () => {
    expect(textoFilasDeDatos(1)).toBe('1 fila de datos');
    expect(textoFilasDeDatos(12)).toBe('12 filas de datos');
    expect(textoFilasDeDatos(5000)).toBe('5000 filas de datos');
  });
});

describe('textosTotales', () => {
  it('plural', () => {
    expect(
      textosTotales({ total: 10, nuevas: 6, duplicadas: 3, invalidas: 1, sinCategoria: 6 }).map(
        (t) => t.texto,
      ),
    ).toEqual(['10 filas en total', '6 nuevas', '3 duplicadas', '1 inválida']);
  });

  it('singular y cero', () => {
    expect(
      textosTotales({ total: 1, nuevas: 1, duplicadas: 0, invalidas: 0, sinCategoria: 1 }).map(
        (t) => t.texto,
      ),
    ).toEqual(['1 fila en total', '1 nueva', '0 duplicadas', '0 inválidas']);
  });
});
