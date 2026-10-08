import { describe, expect, it } from 'vitest';
import { TransaccionProgramadaResponse } from '../models/transaccion-programada-response.model';
import {
  estadoProgramada,
  filaProgramada,
  montoAbsoluto,
  necesitaAvisoFinDeMes,
  textoFrecuencia,
  textoResultadoGeneracion,
  tipoMonto,
} from './presentacion-programada';

function programada(
  cambios: Partial<TransaccionProgramadaResponse> = {},
): TransaccionProgramadaResponse {
  return {
    id: 1,
    cuentaId: 5,
    fechaInicio: '2026-11-05',
    frecuencia: 'MENSUAL',
    fechaFin: null,
    monto: -150000,
    categoriaId: 7,
    beneficiario: 'Inmobiliaria',
    memo: null,
    activa: true,
    proximaFecha: '2026-11-05',
    ultimoError: null,
    fechaCreacion: '2026-10-08T12:00:00Z',
    fechaActualizacion: '2026-10-08T12:00:00Z',
    ...cambios,
  };
}

describe('textoFrecuencia', () => {
  it.each([
    ['DIARIA', 'Cada día'],
    ['SEMANAL', 'Cada semana'],
    ['CADA_2_SEMANAS', 'Cada 2 semanas'],
    ['MENSUAL', 'Cada mes'],
    ['CADA_3_MESES', 'Cada 3 meses'],
    ['ANUAL', 'Cada año'],
  ] as const)('%s se lee %s', (frecuencia, texto) => {
    expect(textoFrecuencia(frecuencia)).toBe(texto);
  });
});

describe('estadoProgramada', () => {
  it('activa con error: No se pudo generar', () => {
    expect(estadoProgramada(programada({ ultimoError: 'La cuenta está cerrada' }))).toEqual({
      estado: 'error',
      texto: 'No se pudo generar',
      icono: 'error',
      tono: 'error',
    });
  });

  it('pausada con error se ve Pausada', () => {
    expect(estadoProgramada(programada({ activa: false, ultimoError: 'x' })).texto).toBe('Pausada');
  });

  it('pausada', () => {
    expect(estadoProgramada(programada({ activa: false })).estado).toBe('pausada');
  });

  it('sin próxima fecha: Finalizada', () => {
    expect(estadoProgramada(programada({ proximaFecha: null })).texto).toBe('Finalizada');
  });

  it('activa', () => {
    expect(estadoProgramada(programada()).texto).toBe('Activa');
  });
});

describe('tipoMonto y montoAbsoluto', () => {
  it('un monto negativo es una salida y se escribe en positivo', () => {
    expect(tipoMonto(-150000)).toBe('salida');
    expect(montoAbsoluto(-150000)).toBe(150000);
  });

  it('un monto positivo es una entrada', () => {
    expect(tipoMonto(50500)).toBe('entrada');
    expect(montoAbsoluto(50500)).toBe(50500);
  });
});

describe('textoResultadoGeneracion', () => {
  it.each([
    [0, 0, 'No había ocurrencias pendientes.'],
    [1, 0, 'Se generó 1 transacción.'],
    [2, 0, 'Se generaron 2 transacciones.'],
    [1, 1, 'Se generó 1 transacción. 1 programada no se pudo generar.'],
    [0, 3, 'No había ocurrencias pendientes. 3 programadas no se pudieron generar.'],
  ])('%i generadas y %i con error: %s', (generadas, plantillasConError, texto) => {
    expect(textoResultadoGeneracion({ generadas, plantillasConError })).toBe(texto);
  });
});

describe('necesitaAvisoFinDeMes', () => {
  it.each([
    ['MENSUAL', 31, true],
    ['MENSUAL', 29, true],
    ['MENSUAL', 28, false],
    ['CADA_3_MESES', 30, true],
    ['ANUAL', 29, true],
    ['SEMANAL', 31, false],
    ['DIARIA', 31, false],
    ['CADA_2_SEMANAS', 30, false],
  ] as const)('%s con día %i: %s', (frecuencia, dia, aviso) => {
    expect(necesitaAvisoFinDeMes(frecuencia, new Date(2027, 0, dia))).toBe(aviso);
  });

  it('sin fecha no avisa', () => {
    expect(necesitaAvisoFinDeMes('MENSUAL', null)).toBe(false);
  });
});

describe('filaProgramada', () => {
  const cuentas = new Map([[5, { id: 5, nombre: 'Banco', enPresupuesto: true, cerrada: false }]]);
  const categorias = new Map([[7, 'Alquiler']]);

  it('arma la fila con nombres, tipo, monto en positivo, frecuencia y estado', () => {
    expect(filaProgramada(programada(), cuentas, categorias)).toMatchObject({
      cuenta: 'Banco',
      beneficiario: 'Inmobiliaria',
      categoria: 'Alquiler',
      tipo: 'Salida',
      monto: 150000,
      frecuencia: 'Cada mes',
      estado: { texto: 'Activa' },
    });
  });

  it('sin categoría ni beneficiario, y con referencias que ya no están', () => {
    const fila = filaProgramada(
      programada({ cuentaId: 99, categoriaId: null, beneficiario: null, monto: 1000 }),
      cuentas,
      categorias,
    );
    expect(fila).toMatchObject({
      cuenta: '—',
      beneficiario: '—',
      categoria: 'Sin categoría',
      tipo: 'Entrada',
    });
    expect(filaProgramada(programada({ categoriaId: 99 }), cuentas, categorias).categoria).toBe(
      '—',
    );
  });
});
