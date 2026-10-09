import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { mensajeDeError } from '../formulario/errores-formulario';
import { MENSAJES_NOMBRE_PERSONA, validadoresNombrePersona } from './nombre-persona.validator';

describe('validadoresNombrePersona', () => {
  const errores = (valor: string | null) =>
    new FormControl(valor, { validators: validadoresNombrePersona() }).errors;
  const mensaje = (valor: string) => mensajeDeError(errores(valor), MENSAJES_NOMBRE_PERSONA);

  it('exige un nombre: vacío y solo espacios son obligatorio', () => {
    expect(mensaje('')).toBe('El nombre es obligatorio');
    expect(mensaje('    ')).toBe('El nombre es obligatorio');
  });

  it('mide sin espacios de los extremos', () => {
    expect(mensaje('  A  ')).toBe('El nombre debe tener al menos 2 caracteres');
    expect(errores('  Al  ')).toBeNull();
  });

  it('acepta 2 y 100 caracteres y rechaza 101', () => {
    expect(errores('Al')).toBeNull();
    expect(errores('n'.repeat(100))).toBeNull();
    expect(errores(' ' + 'n'.repeat(100) + ' ')).toBeNull();
    expect(mensaje('n'.repeat(101))).toBe('El nombre no puede superar 100 caracteres');
  });
});
