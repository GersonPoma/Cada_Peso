import { describe, expect, it } from 'vitest';
import { esCategoriaDePago, esGrupoDePagos } from './pago-tarjeta';

describe('esGrupoDePagos', () => {
  it('es verdadero solo para PAGOS_TARJETA', () => {
    expect(esGrupoDePagos({ tipo: 'PAGOS_TARJETA' })).toBe(true);
    expect(esGrupoDePagos({ tipo: 'NORMAL' })).toBe(false);
  });

  it('un grupo sin el campo es normal', () => {
    expect(esGrupoDePagos({})).toBe(false);
  });
});

describe('esCategoriaDePago', () => {
  it('es verdadero solo con esPagoTarjeta verdadero', () => {
    expect(esCategoriaDePago({ esPagoTarjeta: true })).toBe(true);
    expect(esCategoriaDePago({ esPagoTarjeta: false })).toBe(false);
  });

  it('una categoría sin el campo es normal', () => {
    expect(esCategoriaDePago({})).toBe(false);
  });
});
