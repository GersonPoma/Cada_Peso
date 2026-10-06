import { FormControl, FormGroup, Validators } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { aplicarErroresDeCampos, mensajeDeError } from './errores-formulario';

describe('mensajeDeError', () => {
  it('sigue el orden del mapa de mensajes, no el de los errores', () => {
    const errores = { required: true, matDatepickerParse: { text: '31/02/2003' } };

    expect(
      mensajeDeError(errores, {
        matDatepickerParse: 'La fecha no es válida',
        required: 'Obligatoria',
      }),
    ).toBe('La fecha no es válida');
    expect(
      mensajeDeError(errores, {
        required: 'Obligatoria',
        matDatepickerParse: 'La fecha no es válida',
      }),
    ).toBe('Obligatoria');
  });

  it('pone primero el error del servidor, aunque no esté en el mapa', () => {
    const errores = { required: true, servidor: 'Ya existe una cuenta con ese email' };

    expect(mensajeDeError(errores, { required: 'Obligatorio' })).toBe(
      'Ya existe una cuenta con ese email',
    );
  });

  it('acepta mensajes en forma de función que reciben el valor del error', () => {
    const errores = { minlength: { requiredLength: 8, actualLength: 3 } };

    const mensaje = mensajeDeError(errores, {
      minlength: (valor) => `Al menos ${(valor as { requiredLength: number }).requiredLength}`,
    });

    expect(mensaje).toBe('Al menos 8');
  });

  it('devuelve null sin errores o sin coincidencias', () => {
    expect(mensajeDeError(null, { required: 'Obligatorio' })).toBeNull();
    expect(mensajeDeError({ otro: true }, { required: 'Obligatorio' })).toBeNull();
  });
});

describe('aplicarErroresDeCampos', () => {
  function crearFormulario(): FormGroup {
    return new FormGroup({
      email: new FormControl('ana@ejemplo.com', Validators.required),
      nombre: new FormControl('Ana', Validators.required),
    });
  }

  it('pone el error servidor en cada control, lo marca como tocado y devuelve las claves sin control', () => {
    const formulario = crearFormulario();

    const sinControl = aplicarErroresDeCampos(formulario, {
      email: 'Ya existe una cuenta con ese email',
      desconocido: 'x',
    });

    expect(sinControl).toEqual(['desconocido']);
    expect(formulario.get('email')?.errors).toEqual({
      servidor: 'Ya existe una cuenta con ese email',
    });
    expect(formulario.get('email')?.touched).toBe(true);
    expect(formulario.invalid).toBe(true);
  });

  it('conserva los errores que el control ya tenía', () => {
    const formulario = crearFormulario();
    formulario.get('nombre')?.setValue('');

    aplicarErroresDeCampos(formulario, { nombre: 'Muy corto' });

    expect(formulario.get('nombre')?.errors).toEqual({ required: true, servidor: 'Muy corto' });
  });

  it('el error desaparece al editar ese control sin afectar a los demás', () => {
    const formulario = crearFormulario();
    aplicarErroresDeCampos(formulario, { email: 'Mal email', nombre: 'Mal nombre' });

    formulario.get('email')?.setValue('otra@ejemplo.com');

    expect(formulario.get('email')?.errors).toBeNull();
    expect(formulario.get('nombre')?.errors).toEqual({ servidor: 'Mal nombre' });
    expect(formulario.invalid).toBe(true);
  });
});
