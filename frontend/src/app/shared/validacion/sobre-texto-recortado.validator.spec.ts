import { FormControl, Validators } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { sobreTextoRecortado } from './sobre-texto-recortado.validator';

describe('sobreTextoRecortado', () => {
  describe('con Validators.required', () => {
    const validador = sobreTextoRecortado(Validators.required);

    it.each(['', '   ', null])('rechaza %j', (valor) => {
      expect(validador(new FormControl(valor))).toEqual({ required: true });
    });

    it('acepta un texto con espacios alrededor', () => {
      expect(validador(new FormControl(' a '))).toBeNull();
    });
  });

  describe('con Validators.email', () => {
    const validador = sobreTextoRecortado(Validators.email);

    it('acepta un email con espacios alrededor', () => {
      expect(validador(new FormControl('  ana@ejemplo.com  '))).toBeNull();
    });

    it('rechaza un email sin arroba conservando la clave de Angular', () => {
      expect(validador(new FormControl('ana-arroba-ejemplo.com'))).toHaveProperty('email');
    });
  });

  describe('con longitudes', () => {
    it('minLength(2) mide después de recortar', () => {
      const validador = sobreTextoRecortado(Validators.minLength(2));

      expect(validador(new FormControl('  A  '))).toHaveProperty('minlength');
      expect(validador(new FormControl('  Ab '))).toBeNull();
    });

    it('maxLength(3) mide después de recortar', () => {
      const validador = sobreTextoRecortado(Validators.maxLength(3));

      expect(validador(new FormControl('  abc '))).toBeNull();
      expect(validador(new FormControl('  abcd '))).toHaveProperty('maxlength');
    });
  });

  it('no modifica el valor del control', () => {
    const control = new FormControl('  hola  ');

    sobreTextoRecortado(Validators.required)(control);

    expect(control.value).toBe('  hola  ');
  });
});
