import { describe, expect, it } from 'vitest';
import { TransaccionResponse } from '../models/transaccion-response.model';
import {
  MOTIVO_RECONCILIADA,
  MOTIVO_TRANSFERENCIA,
  accionesDe,
  filtrarLote,
} from './acciones-transaccion';

function transaccion(cambios: Partial<TransaccionResponse> = {}): TransaccionResponse {
  return {
    id: 1,
    cuentaId: 5,
    fecha: '2026-10-06',
    monto: -1000,
    categoriaId: 7,
    beneficiario: null,
    memo: null,
    estado: 'NO_CONCILIADA',
    aprobada: false,
    subtransacciones: [],
    transaccionParId: null,
    fechaCreacion: '',
    fechaActualizacion: '',
    ...cambios,
  };
}

const PARTE = { id: 1, categoriaId: 7, monto: -500, memo: null };

describe('accionesDe', () => {
  it('una transacción normal sin aprobar admite todo', () => {
    const acciones = accionesDe(transaccion(), false);

    for (const accion of Object.values(acciones)) {
      expect(accion).toEqual({ visible: true, habilitada: true, motivo: null });
    }
  });

  it('una aprobada no muestra Aprobar', () => {
    expect(accionesDe(transaccion({ aprobada: true }), false).aprobar.visible).toBe(false);
  });

  it('una reconciliada bloquea editar, mover, borrar y estado con su motivo', () => {
    const acciones = accionesDe(transaccion({ estado: 'RECONCILIADA' }), false);

    for (const clave of ['editar', 'mover', 'borrar', 'estado'] as const) {
      expect(acciones[clave]).toEqual({
        visible: true,
        habilitada: false,
        motivo: MOTIVO_RECONCILIADA,
      });
    }
    expect(acciones.duplicar.habilitada).toBe(true);
    expect(acciones.aprobar.habilitada).toBe(true);
  });

  it('una pata de transferencia solo admite aprobar y cambiar el estado', () => {
    const acciones = accionesDe(transaccion({ transaccionParId: 2 }), false);

    for (const clave of ['editar', 'duplicar', 'mover', 'borrar'] as const) {
      expect(acciones[clave]).toEqual({
        visible: true,
        habilitada: false,
        motivo: MOTIVO_TRANSFERENCIA,
      });
    }
    expect(acciones.aprobar.habilitada).toBe(true);
    expect(acciones.estado.habilitada).toBe(true);
  });

  it('con la cuenta cerrada no se ofrece Editar', () => {
    expect(accionesDe(transaccion(), true).editar.visible).toBe(false);
  });
});

describe('filtrarLote', () => {
  const simple = transaccion({ id: 1 });
  const pata = transaccion({ id: 2, transaccionParId: 3 });
  const dividida = transaccion({ id: 4, categoriaId: null, subtransacciones: [PARTE, PARTE] });
  const reconciliada = transaccion({ id: 5, estado: 'RECONCILIADA' });
  const todas = [simple, pata, dividida, reconciliada];
  const ids = (lista: TransaccionResponse[]) => lista.map((t) => t.id);

  it('APROBAR se aplica a todas', () => {
    const { aplicables, omitidas } = filtrarLote(todas, 'APROBAR');

    expect(ids(aplicables)).toEqual([1, 2, 4, 5]);
    expect(omitidas).toEqual([]);
  });

  it('CATEGORIZAR omite patas, divididas y reconciliadas', () => {
    const { aplicables, omitidas } = filtrarLote(todas, 'CATEGORIZAR');

    expect(ids(aplicables)).toEqual([1]);
    expect(ids(omitidas)).toEqual([2, 4, 5]);
  });

  it('BORRAR omite patas y reconciliadas', () => {
    const { aplicables, omitidas } = filtrarLote(todas, 'BORRAR');

    expect(ids(aplicables)).toEqual([1, 4]);
    expect(ids(omitidas)).toEqual([2, 5]);
  });
});
