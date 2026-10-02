import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FechaPipe } from './fecha.pipe';

function normalizarEspacios(texto: string): string {
  return texto.replace(/[\u00A0\u202F]/g, ' ');
}

describe('FechaPipe', () => {
  let pipe: FechaPipe;

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'es-CL' });
    pipe = new FechaPipe();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('convierte un instante UTC a la hora local del entorno de test, formateada según la región', () => {
    const resultado = normalizarEspacios(pipe.transform('2026-10-01T23:30:00Z'));

    expect(resultado).toBe('1 de octubre de 2026, 7:30 p. m.');
  });

  it('formatea una fecha de negocio sin conversión de zona horaria, según la región', () => {
    const resultado = pipe.transform('2026-10-01');

    expect(resultado).toBe('1 de octubre de 2026');
  });

  it('devuelve cadena vacía para una fecha null', () => {
    expect(pipe.transform(null)).toBe('');
  });

  it('devuelve cadena vacía para una fecha undefined', () => {
    expect(pipe.transform(undefined)).toBe('');
  });

  it('lanza un error para un string presente con formato no reconocido', () => {
    expect(() => pipe.transform('01/10/2026')).toThrow();
  });

  it('prueba de regresión: una fecha de negocio nunca se muestra corrida un día en una zona con desfase negativo', () => {
    // El entorno de test corre en America/New_York (desfase negativo respecto a UTC, ver
    // src/test-setup.ts). Sin la corrección de LocalDate (Date.UTC + timeZone: 'UTC'), esta
    // fecha se interpretaría como medianoche UTC y se mostraría como "30 de septiembre".
    const resultado = pipe.transform('2026-10-01');

    expect(resultado).toContain('1 de octubre');
    expect(resultado).not.toContain('30 de septiembre');
  });
});
