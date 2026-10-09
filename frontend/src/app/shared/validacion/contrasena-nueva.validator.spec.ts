import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { validadoresContrasenaNueva } from './contrasena-nueva.validator';

describe('validadoresContrasenaNueva', () => {
  const errores = (valor: string | null) =>
    new FormControl(valor, { validators: validadoresContrasenaNueva() }).errors;

  it('rechaza una contraseña vacía o de solo espacios como obligatoria', () => {
    expect(errores('')).toHaveProperty('required');
    expect(errores('        ')).toHaveProperty('required');
    expect(errores(null)).toHaveProperty('required');
  });

  it('rechaza una de 7 caracteres y acepta una de 8', () => {
    expect(errores('1234567')).toHaveProperty('minlength');
    expect(errores('12345678')).toBeNull();
  });

  it('acepta 72 caracteres ASCII', () => {
    expect(errores('a'.repeat(72))).toBeNull();
  });

  it('acepta 36 letras ñ (72 bytes) y rechaza 37 (74 bytes)', () => {
    expect(errores('ñ'.repeat(36))).toBeNull();
    expect(errores('ñ'.repeat(37))).toEqual({ maxBytesUtf8: { maximo: 72, actual: 74 } });
  });

  it('rechaza una ñ y 71 letras a (72 caracteres, 73 bytes)', () => {
    expect(errores('ñ' + 'a'.repeat(71))).toEqual({ maxBytesUtf8: { maximo: 72, actual: 73 } });
  });

  it('no recorta: los espacios de los extremos cuentan para la longitud', () => {
    expect(errores('  corta1 ')).toBeNull();
    expect(errores(' ' + 'a'.repeat(70) + ' ')).toBeNull();
    expect(errores(' ' + 'a'.repeat(71) + ' ')).toHaveProperty('maxBytesUtf8');
  });
});
