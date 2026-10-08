import { describe, expect, it } from 'vitest';
import { CuentaResumen } from '../models/cuenta-resumen.model';
import { reglaCategoriaTransferencia } from './regla-categoria-transferencia';

const BANCO: CuentaResumen = { id: 1, nombre: 'Banco', enPresupuesto: true, cerrada: false };
const AHORRO: CuentaResumen = { id: 2, nombre: 'Ahorro', enPresupuesto: true, cerrada: false };
const INVERSIONES: CuentaResumen = {
  id: 3,
  nombre: 'Inversiones',
  enPresupuesto: false,
  cerrada: false,
};
const HIPOTECA: CuentaResumen = { id: 4, nombre: 'Hipoteca', enPresupuesto: false, cerrada: false };

describe('reglaCategoriaTransferencia', () => {
  it('ambas del presupuesto: oculta', () => {
    expect(reglaCategoriaTransferencia(BANCO, AHORRO)).toBe('oculta-presupuesto');
  });

  it('ambas de seguimiento: oculta', () => {
    expect(reglaCategoriaTransferencia(HIPOTECA, INVERSIONES)).toBe('oculta-seguimiento');
  });

  it('del presupuesto a seguimiento: obligatoria', () => {
    expect(reglaCategoriaTransferencia(BANCO, INVERSIONES)).toBe('obligatoria');
  });

  it('de seguimiento al presupuesto: opcional', () => {
    expect(reglaCategoriaTransferencia(INVERSIONES, BANCO)).toBe('opcional');
  });

  it('pendiente mientras falte una cuenta', () => {
    expect(reglaCategoriaTransferencia(null, BANCO)).toBe('pendiente');
    expect(reglaCategoriaTransferencia(BANCO, null)).toBe('pendiente');
    expect(reglaCategoriaTransferencia(null, null)).toBe('pendiente');
  });
});
