import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { accionError } from './errores-programada';
import {
  MENSAJE_FECHAS_MONTO,
  MENSAJE_REFERENCIA_INEXISTENTE,
  MENSAJE_REGLA_PROGRAMADA,
} from './mensajes-programada';

const problema = (status: number, cuerpo: object) =>
  new HttpErrorResponse({ status, error: { detail: 'texto del servidor', ...cuerpo } });

describe('accionError', () => {
  it('400 con errores va a cada campo', () => {
    expect(
      accionError(problema(400, { codigo: 'DATOS_INVALIDOS', errores: { memo: 'Muy largo' } })),
    ).toEqual({ accion: 'campos', errores: { memo: 'Muy largo' } });
  });

  it('400 sin errores pide revisar fechas y monto', () => {
    expect(accionError(problema(400, { codigo: 'DATOS_INVALIDOS' }))).toEqual({
      accion: 'mensaje',
      mensaje: MENSAJE_FECHAS_MONTO,
    });
  });

  it('422 se muestra en el diálogo', () => {
    expect(accionError(problema(422, { codigo: 'REGLA_NEGOCIO_VIOLADA' }))).toEqual({
      accion: 'mensaje',
      mensaje: MENSAJE_REGLA_PROGRAMADA,
    });
  });

  it('404 cierra y recarga', () => {
    expect(accionError(problema(404, { codigo: 'RECURSO_NO_ENCONTRADO' }))).toEqual({
      accion: 'recargar',
      mensaje: MENSAJE_REFERENCIA_INEXISTENTE,
    });
  });

  it('401 no hace nada (el interceptor redirige)', () => {
    expect(accionError(problema(401, { codigo: 'NO_AUTENTICADO' }))).toEqual({
      accion: 'ninguna',
    });
  });

  it.each([
    ['otro código', problema(500, { codigo: 'ERROR_INTERNO' })],
    ['error de red', new HttpErrorResponse({ status: 0 })],
  ])('%s da el aviso genérico, nunca el detail', (_caso, error) => {
    expect(accionError(error)).toEqual({ accion: 'generico', mensaje: MENSAJE_ERROR_GENERICO });
  });
});
