import { describe, expect, it } from 'vitest';
import {
  NOTA_CUENTA_DE_SEGUIMIENTO,
  NOTA_CUENTA_DEL_PRESUPUESTO,
  NOTA_TARJETA_DE_CREDITO,
  notaListoParaAsignar,
} from './nota-listo-para-asignar';

describe('notaListoParaAsignar', () => {
  it('cuenta del presupuesto: las entradas suben y las salidas no bajan hasta categorizar', () => {
    const nota = notaListoParaAsignar({ tipo: 'CORRIENTE', enPresupuesto: true });

    expect(nota).toBe(NOTA_CUENTA_DEL_PRESUPUESTO);
    expect(nota).toContain('suben Listo para asignar');
    expect(nota).toContain('no lo bajan hasta que las categorices');
  });

  it('tarjeta de crédito del presupuesto: no cambia Listo para asignar', () => {
    expect(notaListoParaAsignar({ tipo: 'TARJETA_CREDITO', enPresupuesto: true })).toBe(
      NOTA_TARJETA_DE_CREDITO,
    );
  });

  it('cuenta de seguimiento: no cambia Listo para asignar', () => {
    expect(notaListoParaAsignar({ tipo: 'AHORRO', enPresupuesto: false })).toBe(
      NOTA_CUENTA_DE_SEGUIMIENTO,
    );
  });

  it('tarjeta de crédito de seguimiento usa el texto de tarjeta', () => {
    expect(notaListoParaAsignar({ tipo: 'TARJETA_CREDITO', enPresupuesto: false })).toBe(
      NOTA_TARJETA_DE_CREDITO,
    );
  });
});
