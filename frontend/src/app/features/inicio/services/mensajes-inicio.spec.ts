import { describe, expect, it } from 'vitest';
import { textoMetasQueFaltan, textoSinAprobar, textoSobregastadas } from './mensajes-inicio';

describe('textoSobregastadas', () => {
  it('cero, una y varias', () => {
    expect(textoSobregastadas(0)).toBe('Ninguna categoría sobregastada');
    expect(textoSobregastadas(1)).toBe('1 categoría sobregastada');
    expect(textoSobregastadas(2)).toBe('2 categorías sobregastadas');
  });
});

describe('textoSinAprobar', () => {
  it('cero, una y varias', () => {
    expect(textoSinAprobar(0)).toBe('Todo aprobado');
    expect(textoSinAprobar(1)).toBe('1 transacción sin aprobar');
    expect(textoSinAprobar(7)).toBe('7 transacciones sin aprobar');
  });
});

describe('textoMetasQueFaltan', () => {
  it('sin metas, ninguna pendiente, una y varias', () => {
    expect(textoMetasQueFaltan(0, 0)).toBe('Aún no tienes metas');
    expect(textoMetasQueFaltan(0, 3)).toBe('Ninguna meta pendiente');
    expect(textoMetasQueFaltan(1, 3)).toBe('1 meta por cumplir este mes');
    expect(textoMetasQueFaltan(3, 6)).toBe('3 metas por cumplir este mes');
  });
});
