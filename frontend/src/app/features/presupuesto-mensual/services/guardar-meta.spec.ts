import { describe, expect, it } from 'vitest';
import { ValorFormularioMeta, aGuardarMetaRequest } from './guardar-meta';

/** Un formulario con todos los campos rellenos: cada caso cambia el tipo y la frecuencia. */
function valor(cambios: Partial<ValorFormularioMeta>): ValorFormularioMeta {
  return {
    tipo: 'MONTO_MENSUAL',
    monto: 100000,
    frecuencia: 'MENSUAL',
    diaSemana: 3,
    intervaloDias: 14,
    fechaInicio: new Date(2026, 9, 2),
    fechaObjetivo: new Date(2026, 11, 15),
    ...cambios,
  };
}

describe('aGuardarMetaRequest', () => {
  it('monto mensual: solo tipo, monto y frecuencia', () => {
    expect(aGuardarMetaRequest(valor({}))).toEqual({
      tipo: 'MONTO_MENSUAL',
      monto: 100000,
      frecuencia: 'MENSUAL',
    });
  });

  it('semanal: agrega el día de la semana', () => {
    expect(
      aGuardarMetaRequest(valor({ frecuencia: 'SEMANAL', diaSemana: 1, monto: 20000 })),
    ).toEqual({ tipo: 'MONTO_MENSUAL', monto: 20000, frecuencia: 'SEMANAL', diaSemana: 1 });
  });

  it('personalizada: agrega el intervalo y la fecha de inicio local', () => {
    expect(aGuardarMetaRequest(valor({ frecuencia: 'PERSONALIZADA' }))).toEqual({
      tipo: 'MONTO_MENSUAL',
      monto: 100000,
      frecuencia: 'PERSONALIZADA',
      intervaloDias: 14,
      fechaInicio: '2026-10-02',
    });
  });

  it('para una fecha: solo la fecha objetivo, sin frecuencia', () => {
    expect(aGuardarMetaRequest(valor({ tipo: 'MONTO_PARA_FECHA', monto: 600000 }))).toEqual({
      tipo: 'MONTO_PARA_FECHA',
      monto: 600000,
      fechaObjetivo: '2026-12-15',
    });
  });

  it('saldo objetivo: solo tipo y monto aunque queden valores de otro tipo', () => {
    expect(
      aGuardarMetaRequest(valor({ tipo: 'SALDO_OBJETIVO', frecuencia: 'SEMANAL', diaSemana: 1 })),
    ).toEqual({ tipo: 'SALDO_OBJETIVO', monto: 100000 });
  });

  it('una fecha de medianoche local no se corre de día', () => {
    const solicitud = aGuardarMetaRequest(
      valor({ tipo: 'MONTO_PARA_FECHA', fechaObjetivo: new Date(2026, 0, 1, 0, 0) }),
    );

    expect(solicitud.fechaObjetivo).toBe('2026-01-01');
  });

  it('los campos vacíos no se envían (los marca el backend)', () => {
    expect(
      aGuardarMetaRequest(
        valor({ frecuencia: 'PERSONALIZADA', intervaloDias: null, fechaInicio: null }),
      ),
    ).toEqual({ tipo: 'MONTO_MENSUAL', monto: 100000, frecuencia: 'PERSONALIZADA' });
  });
});
