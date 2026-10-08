import { describe, expect, it } from 'vitest';
import { MetaMesResponse } from '../models/metas-mes-response.model';
import { presentacionMeta, progresoMeta } from './presentacion-meta';

function meta(cambios: Partial<MetaMesResponse>): MetaMesResponse {
  return {
    categoriaId: 7,
    nombre: 'Comida',
    tipo: 'MONTO_MENSUAL',
    monto: 100000,
    necesidad: 100000,
    asignado: 60000,
    disponible: 60000,
    faltante: 40000,
    estado: 'FALTA',
    ...cambios,
  };
}

describe('presentacionMeta', () => {
  it('FINANCIADA: Financiada, tono ok y barra llena', () => {
    expect(
      presentacionMeta(meta({ estado: 'FINANCIADA', asignado: 100000, faltante: 0 }), false),
    ).toEqual({ texto: 'Financiada', monto: null, icono: 'check_circle', tono: 'ok', progreso: 100 });
  });

  it('FALTA en el mes actual: Falta con el faltante y la barra proporcional', () => {
    expect(presentacionMeta(meta({}), false)).toEqual({
      texto: 'Falta',
      monto: 40000,
      icono: 'schedule',
      tono: 'falta',
      progreso: 60,
    });
  });

  it('FALTA en un mes pasado: Faltaron en tono neutro', () => {
    const presentacion = presentacionMeta(meta({ faltante: 100000, asignado: 0 }), true);

    expect(presentacion).toEqual({
      texto: 'Faltaron',
      monto: 100000,
      icono: 'history',
      tono: 'neutro',
      progreso: 0,
    });
    expect(presentacion.icono).not.toBe('warning');
  });

  it('POSPUESTA: necesidad 0, barra llena y tono neutro', () => {
    expect(
      presentacionMeta(meta({ estado: 'POSPUESTA', necesidad: 0, asignado: 0, faltante: 0 }), false),
    ).toEqual({
      texto: 'Pospuesta este mes',
      monto: null,
      icono: 'pause_circle',
      tono: 'neutro',
      progreso: 100,
    });
  });

  it('SOBREGASTADA: Sobregastada con ícono de advertencia y tono de error', () => {
    const presentacion = presentacionMeta(meta({ estado: 'SOBREGASTADA' }), false);

    expect(presentacion.texto).toBe('Sobregastada');
    expect(presentacion.icono).toBe('warning');
    expect(presentacion.tono).toBe('error');
  });

  it('SOBREGASTADA en un mes pasado sigue siendo un error', () => {
    expect(presentacionMeta(meta({ estado: 'SOBREGASTADA' }), true).tono).toBe('error');
  });
});

describe('progresoMeta', () => {
  it.each([
    [60000, 100000, 60],
    [0, 0, 100],
    [5000, 0, 100],
    [150000, 100000, 100],
    [-20000, 100000, 0],
    [1, 3, 33],
    [2, 3, 67],
  ])('asignado %i de necesidad %i da %i', (asignado, necesidad, esperado) => {
    expect(progresoMeta(asignado, necesidad)).toBe(esperado);
  });
});
