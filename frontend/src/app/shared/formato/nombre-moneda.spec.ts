import { afterEach, describe, expect, it, vi } from 'vitest';
import { nombreDeMoneda } from './nombre-moneda';

describe('nombreDeMoneda', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('muestra el código y el nombre en el idioma de la región, con mayúscula inicial', () => {
    vi.stubGlobal('navigator', { language: 'es-BO' });

    expect(nombreDeMoneda('USD')).toBe('USD · Dólar estadounidense');
  });

  it('usa el idioma de otra región', () => {
    vi.stubGlobal('navigator', { language: 'en-US' });

    expect(nombreDeMoneda('USD')).toBe('USD · US Dollar');
  });

  it('muestra solo el código si el navegador no conoce el nombre', () => {
    vi.stubGlobal('navigator', { language: 'es-BO' });

    expect(nombreDeMoneda('ZZZ')).toBe('ZZZ');
  });

  it('muestra solo el valor recibido, sin lanzar, si no es un código válido', () => {
    vi.stubGlobal('navigator', { language: 'es-BO' });

    expect(nombreDeMoneda('US')).toBe('US');
    expect(nombreDeMoneda('')).toBe('');
    expect(nombreDeMoneda('dólar')).toBe('dólar');
  });

  it('muestra solo el código si Intl.DisplayNames no existe', () => {
    vi.stubGlobal('navigator', { language: 'es-BO' });
    vi.stubGlobal('Intl', { ...Intl, DisplayNames: undefined });

    expect(nombreDeMoneda('USD')).toBe('USD');
  });
});
