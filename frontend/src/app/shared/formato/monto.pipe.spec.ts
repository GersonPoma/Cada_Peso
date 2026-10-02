import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MontoPipe } from './monto.pipe';

function normalizarEspacios(texto: string): string {
  return texto.replace(/[\u00A0\u202F]/g, ' ');
}

describe('MontoPipe', () => {
  let pipe: MontoPipe;

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'en-US' });
    pipe = new MontoPipe();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('formatea milésimas como la unidad monetaria completa', () => {
    const resultado = normalizarEspacios(pipe.transform(1500000, 'USD'));

    expect(resultado).toBe('$1,500.00');
  });

  it('devuelve cadena vacía para un monto null', () => {
    expect(pipe.transform(null, 'USD')).toBe('');
  });

  it('devuelve cadena vacía para un monto undefined', () => {
    expect(pipe.transform(undefined, 'USD')).toBe('');
  });
});
