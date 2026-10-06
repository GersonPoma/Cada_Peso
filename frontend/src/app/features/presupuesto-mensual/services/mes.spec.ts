import { afterEach, describe, expect, it, vi } from 'vitest';
import { MES_MAXIMO, MES_MINIMO, esMesValido, mesActual, sumarMeses, textoMes } from './mes';

const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';

describe('mesActual', () => {
  afterEach(() => {
    vi.useRealTimers();
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });

  function fijarHoraLocal(zona: string, ...fecha: [number, number, number, number]): void {
    process.env['TZ'] = zona;
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(...fecha));
  }

  it('usa el mes local: 31 de octubre a las 21:00 en Nueva York es octubre', () => {
    fijarHoraLocal('America/New_York', 2026, 9, 31, 21);

    // En UTC ya es 1 de noviembre: toISOString() daría el mes equivocado.
    expect(new Date().toISOString().slice(0, 7)).toBe('2026-11');
    expect(mesActual()).toBe('2026-10');
  });

  it('usa el mes local: 1 de noviembre a las 08:00 en Tokio es noviembre', () => {
    fijarHoraLocal('Asia/Tokyo', 2026, 10, 1, 8);

    // En UTC todavía es 31 de octubre.
    expect(new Date().toISOString().slice(0, 7)).toBe('2026-10');
    expect(mesActual()).toBe('2026-11');
  });

  it('acepta una fecha explícita', () => {
    expect(mesActual(new Date(2026, 0, 15))).toBe('2026-01');
  });
});

describe('esMesValido', () => {
  it.each(['2026-10', '2026-01', '2026-12', MES_MINIMO, MES_MAXIMO])('%s es válido', (mes) => {
    expect(esMesValido(mes)).toBe(true);
  });

  it.each(['2026-13', '2026-00', '2026-1', '1999-12', '2101-01', 'octubre', '', null, undefined])(
    '%s no es válido',
    (mes) => {
      expect(esMesValido(mes)).toBe(false);
    },
  );
});

describe('sumarMeses', () => {
  it.each([
    ['2026-10', 1, '2026-11'],
    ['2026-12', 1, '2027-01'],
    ['2027-01', -1, '2026-12'],
    ['2026-10', -10, '2025-12'],
    ['2026-10', 0, '2026-10'],
    ['2026-03', 24, '2028-03'],
  ])('%s más %d es %s', (mes, n, esperado) => {
    expect(sumarMeses(mes, n)).toBe(esperado);
  });
});

describe('textoMes', () => {
  it('en es-BO es "octubre de 2026"', () => {
    expect(textoMes('2026-10', 'es-BO')).toBe('octubre de 2026');
  });

  it('en en-US es "October 2026"', () => {
    expect(textoMes('2026-10', 'en-US')).toBe('October 2026');
  });

  it('no se corre de mes en una zona horaria al este de UTC', () => {
    process.env['TZ'] = 'Asia/Tokyo';
    try {
      expect(textoMes('2026-01', 'es-BO')).toBe('enero de 2026');
    } finally {
      process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
    }
  });
});
