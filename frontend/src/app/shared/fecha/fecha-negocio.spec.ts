import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { aFechaNegocio, deFechaNegocio } from './fecha-negocio';

declare const process: { env: Record<string, string | undefined> };

/** Zona horaria que fija `src/test-setup.ts` para toda la suite. */
const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

function enZonaHoraria(zona: string): void {
  beforeEach(() => {
    process.env['TZ'] = zona;
  });
  afterEach(() => {
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });
}

describe('aFechaNegocio', () => {
  describe('en America/New_York (desfase negativo)', () => {
    enZonaHoraria('America/New_York');

    it('convierte la medianoche del 1 de octubre de 2026 en 2026-10-01', () => {
      expect(aFechaNegocio(new Date(2026, 9, 1))).toBe('2026-10-01');
    });

    it('convierte las 23:59 del 1 de octubre de 2026 en 2026-10-01, nunca 2026-10-02', () => {
      expect(aFechaNegocio(new Date(2026, 9, 1, 23, 59))).toBe('2026-10-01');
    });
  });

  describe('en Asia/Tokyo (desfase positivo)', () => {
    enZonaHoraria('Asia/Tokyo');

    it('convierte la medianoche del 1 de octubre de 2026 en 2026-10-01, nunca 2026-09-30', () => {
      expect(aFechaNegocio(new Date(2026, 9, 1))).toBe('2026-10-01');
    });

    it('convierte las 23:59 del 1 de octubre de 2026 en 2026-10-01', () => {
      expect(aFechaNegocio(new Date(2026, 9, 1, 23, 59))).toBe('2026-10-01');
    });

    it('control: en esta zona toISOString() sí corre el día (el bug que se evita)', () => {
      expect(new Date(2026, 9, 1).toISOString().slice(0, 10)).toBe('2026-09-30');
    });
  });

  it('escribe el mes y el día en dos dígitos', () => {
    expect(aFechaNegocio(new Date(2026, 2, 5))).toBe('2026-03-05');
  });

  it('devuelve null si no hay fecha elegida', () => {
    expect(aFechaNegocio(null)).toBeNull();
    expect(aFechaNegocio(undefined)).toBeNull();
  });

  it('lanza un error con un Date inválido', () => {
    expect(() => aFechaNegocio(new Date('no es una fecha'))).toThrow();
  });
});

describe('deFechaNegocio', () => {
  it('devuelve la medianoche local del día, que vuelve igual con aFechaNegocio', () => {
    const fecha = deFechaNegocio('2026-10-01') as Date;

    expect([fecha.getFullYear(), fecha.getMonth(), fecha.getDate(), fecha.getHours()]).toEqual([
      2026, 9, 1, 0,
    ]);
    expect(aFechaNegocio(fecha)).toBe('2026-10-01');
  });

  it.each(['2026-02-30', '2026-13-01', 'ayer', '', null, undefined])(
    '%s no es una fecha',
    (texto) => {
      expect(deFechaNegocio(texto)).toBeNull();
    },
  );
});
