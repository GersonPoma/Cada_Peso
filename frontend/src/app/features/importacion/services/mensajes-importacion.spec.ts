import { describe, expect, it } from 'vitest';
import {
  MENSAJES_ERROR_ARCHIVO,
  MENSAJES_ERROR_MAPEO,
  MENSAJE_CUENTA_CERRADA,
  MENSAJE_DATOS_INVALIDOS,
  MENSAJE_FILAS_INVALIDAS,
  MENSAJE_REGLA_GENERICA,
  mensajeReglaDeNegocio,
} from './mensajes-importacion';

describe('textos fijos (los de la spec)', () => {
  it('errores del archivo', () => {
    expect(MENSAJES_ERROR_ARCHIVO).toEqual({
      TAMANO: 'El archivo supera el máximo de 2 MB',
      VACIO: 'El archivo está vacío o no tiene filas de datos',
      NO_UTF8: 'El archivo debe estar codificado en UTF-8',
      DEMASIADAS_FILAS: 'El archivo tiene más de 5000 filas de datos',
      COMILLA_SIN_CERRAR: 'El archivo tiene una comilla sin cerrar',
    });
  });

  it('errores del mapeo', () => {
    expect(MENSAJES_ERROR_MAPEO).toEqual({
      MILES_IGUAL_DECIMAL: 'El separador de miles no puede ser igual al de decimales',
      FALTA_FECHA: 'Elige la columna de la fecha',
      FALTA_DESCRIPCION: 'Elige la columna de la descripción',
      FALTA_MONTO: 'Elige la columna del monto',
      FALTA_DEBITO_CREDITO: 'Elige las columnas de débito y de crédito',
    });
  });

  it('el mensaje único del 400 enumera las correcciones posibles', () => {
    expect(MENSAJE_DATOS_INVALIDOS).toBe(
      'No pudimos leer el archivo con ese mapeo. Revisa que sea un CSV en UTF-8 de hasta 2 MB y ' +
        '5000 filas, que las columnas elegidas existan y que los formatos de fecha y de monto coincidan',
    );
  });

  it('los mensajes del 422', () => {
    expect(MENSAJE_CUENTA_CERRADA).toBe(
      'La cuenta está cerrada; reábrela en Cuentas para importar',
    );
    expect(MENSAJE_FILAS_INVALIDAS).toBe(
      'El archivo tiene filas inválidas: márcalas para omitirlas o corrige el archivo',
    );
    expect(MENSAJE_REGLA_GENERICA).toBe(
      'No se pudo importar: el archivo o la cuenta ya no cumplen las reglas',
    );
  });
});

describe('mensajeReglaDeNegocio: el 422 se decide por el contexto', () => {
  it('con la cuenta cerrada', () => {
    expect(mensajeReglaDeNegocio({ cuentaCerrada: true, hayInvalidasSinOmitir: false })).toBe(
      MENSAJE_CUENTA_CERRADA,
    );
  });

  it('con filas inválidas sin omitir', () => {
    expect(mensajeReglaDeNegocio({ cuentaCerrada: false, hayInvalidasSinOmitir: true })).toBe(
      MENSAJE_FILAS_INVALIDAS,
    );
  });

  it('sin contexto conocido es el mensaje genérico', () => {
    expect(mensajeReglaDeNegocio({ cuentaCerrada: false, hayInvalidasSinOmitir: false })).toBe(
      MENSAJE_REGLA_GENERICA,
    );
  });

  it('la cuenta cerrada tiene prioridad sobre las filas inválidas', () => {
    expect(mensajeReglaDeNegocio({ cuentaCerrada: true, hayInvalidasSinOmitir: true })).toBe(
      MENSAJE_CUENTA_CERRADA,
    );
  });

  it('ningún mensaje incluye contenido de un archivo', () => {
    const todos = [
      ...Object.values(MENSAJES_ERROR_ARCHIVO),
      ...Object.values(MENSAJES_ERROR_MAPEO),
      MENSAJE_DATOS_INVALIDOS,
      MENSAJE_CUENTA_CERRADA,
      MENSAJE_FILAS_INVALIDAS,
      MENSAJE_REGLA_GENERICA,
    ];

    expect(todos.every((texto) => !/[{}$]/.test(texto))).toBe(true);
  });
});
