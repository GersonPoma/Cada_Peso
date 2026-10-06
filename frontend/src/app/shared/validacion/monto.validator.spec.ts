import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { montoValido } from './monto.validator';

describe('montoValido', () => {
  const validar = (valor: string | null) => montoValido('es-BO')(new FormControl(valor));

  it.each(['', '   ', null, '1,5', '-1.234,567', '0'])('%s es válido', (valor) => {
    expect(validar(valor)).toBeNull();
  });

  it('un texto que no es un número da montoFormato', () => {
    expect(validar('abc')).toEqual({ montoFormato: true });
  });

  it('más de 3 decimales da montoDecimales', () => {
    expect(validar('1,2345')).toEqual({ montoDecimales: true });
  });

  it('un entero que no cabe exacto da montoRango', () => {
    expect(validar('12345678901234567890')).toEqual({ montoRango: true });
  });
});
