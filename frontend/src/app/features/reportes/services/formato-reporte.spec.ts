import { describe, expect, it } from 'vitest';
import {
  escalaVertical,
  pasoRedondo,
  presentacionEstadoMeta,
  pasoRotulos,
  posicionY,
  posicionesX,
  proporcion,
  textoMesCorto,
  textoPorcentaje,
} from './formato-reporte';

/** Normaliza los espacios duros que usa `Intl`. */
const limpio = (texto: string) => texto.replace(/ | /g, ' ');

describe('textoPorcentaje', () => {
  it.each([
    [5283, '52,83%'],
    [0, '0,00%'],
    [-150, '-1,50%'],
    [12500, '125,00%'],
    [189, '1,89%'],
  ])('%i en es-BO es %s', (centesimas, texto) => {
    expect(limpio(textoPorcentaje(centesimas, 'es-BO'))).toBe(texto);
  });

  it('usa el formato de la región', () => {
    expect(textoPorcentaje(5283, 'en-US')).toBe('52.83%');
  });

  it('null se lee Sin necesidad', () => {
    expect(textoPorcentaje(null, 'es-BO')).toBe('Sin necesidad');
  });
});

describe('proporcion', () => {
  it('da por mil, en enteros', () => {
    expect(proporcion(140000, 265000)).toBe(528);
    expect(proporcion(265000, 265000)).toBe(1000);
    expect(proporcion(1, 3)).toBe(333);
  });

  it('da 0 con máximo 0 o valores negativos', () => {
    expect(proporcion(5000, 0)).toBe(0);
    expect(proporcion(-20000, 265000)).toBe(0);
  });

  it('nunca supera la escala', () => {
    expect(proporcion(300, 100)).toBe(1000);
  });

  it('es exacta en el límite seguro', () => {
    expect(proporcion(9_000_000_000_000, 9_000_000_000_000)).toBe(1000);
    expect(proporcion(4_500_000_000_000, 9_000_000_000_000)).toBe(500);
  });
});

describe('escalaVertical', () => {
  it('con solo positivos empieza en 0 y redondea el máximo', () => {
    expect(escalaVertical([500000, 265000])).toEqual({
      minimo: 0,
      maximo: 600000,
      marcas: [0, 200000, 400000, 600000],
    });
  });

  it('con negativos incluye el cero entre las marcas', () => {
    const escala = escalaVertical([1800000, -1250000, 3050000]);
    expect(escala.minimo).toBeLessThan(0);
    expect(escala.maximo).toBeGreaterThanOrEqual(3050000);
    expect(escala.marcas).toContain(0);
  });

  it('con todo en cero no tiene alto cero', () => {
    expect(escalaVertical([0, 0])).toEqual({ minimo: 0, maximo: 1000, marcas: [0, 1000] });
  });

  it('las marcas son enteras', () => {
    for (const marca of escalaVertical([123457, -7]).marcas) {
      expect(Number.isInteger(marca)).toBe(true);
    }
  });
});

describe('pasoRedondo', () => {
  it.each([
    [1, 1],
    [3, 5],
    [7, 10],
    [125000, 200000],
    [762500, 1000000],
  ])('%i se redondea a %i', (x, paso) => {
    expect(pasoRedondo(x)).toBe(paso);
  });
});

describe('presentacionEstadoMeta', () => {
  it.each([
    ['FINANCIADA', 'Financiada', 'ok'],
    ['POSPUESTA', 'Pospuesta', 'neutro'],
    ['SOBREGASTADA', 'Sobregastada', 'error'],
    ['FALTA', 'Falta', 'falta'],
  ] as const)('%s del mes actual se lee %s', (estado, texto, tono) => {
    expect(presentacionEstadoMeta(estado, '2026-10', '2026-10')).toMatchObject({ texto, tono });
  });

  it('FALTA de un mes pasado se lee Faltaron en tono neutro', () => {
    expect(presentacionEstadoMeta('FALTA', '2026-09', '2026-10')).toEqual({
      texto: 'Faltaron',
      icono: 'history',
      tono: 'neutro',
    });
  });

  it('FALTA de un mes futuro sigue siendo Falta', () => {
    expect(presentacionEstadoMeta('FALTA', '2026-11', '2026-10').texto).toBe('Falta');
  });
});

describe('geometría de los gráficos', () => {
  const escala = { minimo: -1000, maximo: 3000, marcas: [] };

  it('posicionY pone el máximo arriba, el mínimo abajo y el cero en su lugar', () => {
    expect(posicionY(3000, escala, 10, 200)).toBe(10);
    expect(posicionY(-1000, escala, 10, 200)).toBe(210);
    expect(posicionY(0, escala, 10, 200)).toBe(160);
  });

  it('posicionesX reparte los meses con centros enteros', () => {
    expect(posicionesX(1, 0, 100)).toEqual([50]);
    expect(posicionesX(4, 40, 400)).toEqual([90, 190, 290, 390]);
  });

  it('textoMesCorto usa la región', () => {
    expect(textoMesCorto('2026-10', 'es-BO')).toBe('oct 26');
  });

  it('pasoRotulos rotula como mucho 12 meses', () => {
    expect(pasoRotulos(6)).toBe(1);
    expect(pasoRotulos(24)).toBe(2);
    expect(pasoRotulos(60)).toBe(5);
  });
});
