import { convertToParamMap } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { aQueryParams, filtrosVacios, hayFiltros, leerFiltros } from './filtros-url';

describe('filtros en la URL', () => {
  it('sin parámetros son los filtros vacíos en la página 0 con tamaño 20', () => {
    expect(leerFiltros(convertToParamMap({}))).toEqual(filtrosVacios());
  });

  it('lee cada parámetro', () => {
    const filtros = leerFiltros(
      convertToParamMap({
        cuentaId: '5',
        categoriaId: '7',
        desde: '2026-10-01',
        hasta: '2026-10-31',
        estado: 'CONCILIADA',
        sinAprobar: '1',
        q: '  super ',
        pagina: '2',
        tamano: '50',
      }),
    );

    expect(filtros).toEqual({
      cuentaId: 5,
      categoriaId: 7,
      desde: '2026-10-01',
      hasta: '2026-10-31',
      estado: 'CONCILIADA',
      soloSinAprobar: true,
      q: 'super',
      pagina: 2,
      tamano: 50,
    });
  });

  it('descarta valores inválidos', () => {
    const filtros = leerFiltros(
      convertToParamMap({
        cuentaId: 'abc',
        categoriaId: '-1',
        desde: '2026-13-01',
        hasta: 'ayer',
        estado: 'OTRO',
        sinAprobar: 'si',
        q: '   ',
        pagina: '-3',
        tamano: '33',
      }),
    );

    expect(filtros).toEqual(filtrosVacios());
  });

  it('escribe solo lo que no es vacío ni por defecto, y la ida y vuelta conserva los filtros', () => {
    expect(aQueryParams(filtrosVacios())).toEqual({});

    const filtros = {
      ...filtrosVacios(),
      cuentaId: 5,
      soloSinAprobar: true,
      pagina: 1,
      tamano: 50,
    };
    const params = aQueryParams(filtros);

    expect(params).toEqual({ cuentaId: 5, sinAprobar: '1', pagina: 1, tamano: 50 });
    const comoTexto = Object.fromEntries(Object.entries(params).map(([k, v]) => [k, String(v)]));
    expect(leerFiltros(convertToParamMap(comoTexto))).toEqual(filtros);
  });

  it('hayFiltros ignora la página y el tamaño', () => {
    expect(hayFiltros({ ...filtrosVacios(), pagina: 3, tamano: 50 })).toBe(false);
    expect(hayFiltros({ ...filtrosVacios(), q: 'x' })).toBe(true);
    expect(hayFiltros({ ...filtrosVacios(), soloSinAprobar: true })).toBe(true);
  });
});
