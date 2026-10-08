import { describe, expect, it } from 'vitest';
import {
  atajo,
  contarMeses,
  fechasDelRango,
  primerDia,
  rangoDesdeUrl,
  textoRango,
  ultimoDia,
  validarRango,
} from './rango-reporte';

describe('contarMeses', () => {
  it.each([
    ['2026-01', '2026-01', 1],
    ['2026-01', '2026-12', 12],
    ['2025-11', '2026-02', 4],
    ['2021-02', '2026-01', 60],
  ])('%s a %s son %i meses', (desde, hasta, meses) => {
    expect(contarMeses(desde, hasta)).toBe(meses);
  });
});

describe('validarRango', () => {
  it('acepta un mes y 60 meses', () => {
    expect(validarRango('2026-10', '2026-10')).toBeNull();
    expect(validarRango('2021-02', '2026-01')).toBeNull();
  });

  it('rechaza un rango invertido', () => {
    expect(validarRango('2026-10', '2026-09')).toBe('invertido');
  });

  it('rechaza 61 meses', () => {
    expect(validarRango('2021-01', '2026-01')).toBe('excede-maximo');
  });

  it.each([
    ['2026-13', '2026-12'],
    ['2026-1', '2026-12'],
    ['ayer', '2026-12'],
    ['1999-12', '2000-01'],
    ['2100-12', '2101-01'],
  ])('rechaza %s a %s como mal formado', (desde, hasta) => {
    expect(validarRango(desde, hasta)).toBe('mal-formado');
  });
});

describe('atajo', () => {
  it.each([
    ['este-mes', '2026-10', '2026-10'],
    ['ultimos-3', '2026-08', '2026-10'],
    ['ultimos-6', '2026-05', '2026-10'],
    ['ultimos-12', '2025-11', '2026-10'],
    ['este-anio', '2026-01', '2026-10'],
  ] as const)('%s con el mes 2026-10 va de %s a %s', (tipo, desde, hasta) => {
    expect(atajo(tipo, '2026-10')).toEqual({ desde, hasta });
  });

  it('cruza el año hacia atrás en enero', () => {
    expect(atajo('ultimos-3', '2026-01')).toEqual({ desde: '2025-11', hasta: '2026-01' });
    expect(atajo('este-anio', '2026-01')).toEqual({ desde: '2026-01', hasta: '2026-01' });
  });
});

describe('rangoDesdeUrl', () => {
  it('usa el rango válido de la URL', () => {
    expect(rangoDesdeUrl('2026-01', '2026-06', '2026-10')).toEqual({
      rango: { desde: '2026-01', hasta: '2026-06' },
      normalizado: false,
    });
  });

  it.each([
    [null, null],
    ['2026-01', null],
    ['2026-13', '2026-06'],
    ['2026-07', '2026-06'],
    ['2021-01', '2026-01'],
  ])('con %s y %s usa los últimos 6 meses y pide normalizar', (desde, hasta) => {
    expect(rangoDesdeUrl(desde, hasta, '2026-10')).toEqual({
      rango: { desde: '2026-05', hasta: '2026-10' },
      normalizado: true,
    });
  });
});

describe('primerDia y ultimoDia', () => {
  it.each([
    ['2026-01', '2026-01-31'],
    ['2026-02', '2026-02-28'],
    ['2028-02', '2028-02-29'],
    ['2100-02', '2100-02-28'],
    ['2000-02', '2000-02-29'],
    ['2026-04', '2026-04-30'],
    ['2026-12', '2026-12-31'],
  ])('el último día de %s es %s', (mes, dia) => {
    expect(ultimoDia(mes)).toBe(dia);
  });

  it('el primer día es el 01', () => {
    expect(primerDia('2026-09')).toBe('2026-09-01');
  });

  it('fechasDelRango arma desde y hasta para la lista de transacciones', () => {
    expect(fechasDelRango({ desde: '2026-09', hasta: '2026-10' })).toEqual({
      desde: '2026-09-01',
      hasta: '2026-10-31',
    });
  });
});

describe('textoRango', () => {
  it('muestra los dos meses en texto largo según la región', () => {
    expect(textoRango({ desde: '2026-01', hasta: '2026-06' }, 'es-BO')).toBe(
      'enero de 2026 – junio de 2026',
    );
  });

  it('con un solo mes muestra ese mes', () => {
    expect(textoRango({ desde: '2026-10', hasta: '2026-10' }, 'es-BO')).toBe('octubre de 2026');
  });
});
