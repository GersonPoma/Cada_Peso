import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { CODIGOS_API, MENSAJE_ERROR_GENERICO, leerProblemaApi } from './problema-api';

describe('leerProblemaApi', () => {
  it('lee status, codigo, detail y errores de un ProblemDetail', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        status: 400,
        detail: 'Uno o más campos no son válidos',
        codigo: 'DATOS_INVALIDOS',
        errores: { email: 'Formato inválido', contrasena: 'Muy corta' },
      },
    });

    expect(leerProblemaApi(error)).toEqual({
      status: 400,
      codigo: 'DATOS_INVALIDOS',
      detail: 'Uno o más campos no son válidos',
      errores: { email: 'Formato inválido', contrasena: 'Muy corta' },
    });
  });

  it('ignora los valores de errores que no son texto', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: { codigo: 'DATOS_INVALIDOS', errores: { email: 'Mal', otro: 5 } },
    });

    expect(leerProblemaApi(error)?.errores).toEqual({ email: 'Mal' });
  });

  it('devuelve un problema sin codigo ante un fallo de red', () => {
    const error = new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') });

    expect(leerProblemaApi(error)).toEqual({ status: 0 });
  });

  it('devuelve un problema sin codigo ante un cuerpo de texto', () => {
    const error = new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' });

    expect(leerProblemaApi(error)).toEqual({ status: 502 });
  });

  it('devuelve null si el error no es un HttpErrorResponse', () => {
    expect(leerProblemaApi(new Error('x'))).toBeNull();
    expect(leerProblemaApi(null)).toBeNull();
    expect(leerProblemaApi('texto')).toBeNull();
  });

  it('expone los códigos usados y el mensaje genérico', () => {
    expect(CODIGOS_API.EMAIL_YA_REGISTRADO).toBe('EMAIL_YA_REGISTRADO');
    expect(CODIGOS_API.BENEFICIARIO_YA_EXISTE).toBe('BENEFICIARIO_YA_EXISTE');
    expect(MENSAJE_ERROR_GENERICO).toBe('No pudimos completar la operación. Inténtalo de nuevo.');
  });
});
