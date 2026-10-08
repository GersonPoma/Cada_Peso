import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { avisoError } from './errores-reporte';
import {
  MENSAJE_CUENTA_INEXISTENTE,
  MENSAJE_PRESUPUESTO_INEXISTENTE,
  MENSAJE_RANGO_INVALIDO,
} from './mensajes-reporte';

const problema = (status: number, codigo?: string, detail = 'texto del servidor') =>
  new HttpErrorResponse({ status, error: { codigo, detail } });

describe('avisoError', () => {
  it('DATOS_INVALIDOS avisa del rango, sin usar detail', () => {
    expect(avisoError(problema(400, 'DATOS_INVALIDOS'))).toEqual({
      mensaje: MENSAJE_RANGO_INVALIDO,
      accion: 'reintentar',
    });
  });

  it('RECURSO_NO_ENCONTRADO en un reporte ofrece recargar', () => {
    expect(avisoError(problema(404, 'RECURSO_NO_ENCONTRADO'))).toEqual({
      mensaje: MENSAJE_PRESUPUESTO_INEXISTENTE,
      accion: 'recargar',
    });
  });

  it('RECURSO_NO_ENCONTRADO en el saldo de una cuenta marca la cuenta inexistente', () => {
    expect(avisoError(problema(404, 'RECURSO_NO_ENCONTRADO'), 'cuenta')).toEqual({
      mensaje: MENSAJE_CUENTA_INEXISTENTE,
      accion: 'reintentar',
      cuentaInexistente: true,
    });
  });

  it.each([
    ['otro código', problema(500, 'ERROR_INTERNO')],
    ['error de red', new HttpErrorResponse({ status: 0 })],
    ['algo que no es HTTP', new Error('x')],
  ])('%s da el aviso genérico con Reintentar', (_caso, error) => {
    expect(avisoError(error)).toEqual({ mensaje: MENSAJE_ERROR_GENERICO, accion: 'reintentar' });
  });
});
