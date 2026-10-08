import { describe, expect, it } from 'vitest';
import { TipoCuentaConciliacion } from '../models/cuenta-conciliacion.model';
import { ReglaCategoriaAjuste, reglaCategoriaAjuste } from './regla-categoria-ajuste';

const cuenta = (enPresupuesto: boolean, tipo: TipoCuentaConciliacion = 'CORRIENTE') => ({
  enPresupuesto,
  tipo,
});

describe('reglaCategoriaAjuste', () => {
  it.each<[string, boolean, TipoCuentaConciliacion, number, ReglaCategoriaAjuste]>([
    ['diferencia 0 en una cuenta del presupuesto', true, 'CORRIENTE', 0, 'sin-ajuste'],
    ['diferencia 0 en una tarjeta', true, 'TARJETA_CREDITO', 0, 'sin-ajuste'],
    ['diferencia 0 fuera del presupuesto', false, 'INVERSION', 0, 'sin-ajuste'],
    ['positiva fuera del presupuesto', false, 'INVERSION', 2000, 'no-admite'],
    ['negativa fuera del presupuesto', false, 'INVERSION', -5000, 'no-admite'],
    ['tarjeta fuera del presupuesto', false, 'TARJETA_CREDITO', 2000, 'no-admite'],
    ['negativa del presupuesto', true, 'CORRIENTE', -5000, 'obligatoria'],
    ['negativa en tarjeta', true, 'TARJETA_CREDITO', -5000, 'obligatoria'],
    ['positiva en tarjeta', true, 'TARJETA_CREDITO', 2000, 'obligatoria'],
    ['positiva en otra cuenta del presupuesto', true, 'CORRIENTE', 2000, 'opcional'],
    ['positiva en un préstamo del presupuesto', true, 'PRESTAMO', 1, 'opcional'],
  ])('%s: %s', (_, enPresupuesto, tipo, diferencia, esperada) => {
    expect(reglaCategoriaAjuste(cuenta(enPresupuesto, tipo), diferencia)).toBe(esperada);
  });
});
