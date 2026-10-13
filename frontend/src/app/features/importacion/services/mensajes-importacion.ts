import { ErrorArchivo } from './validacion-archivo';
import { ErrorMapeo } from './validacion-mapeo';

/*
 * Textos fijos de la importación. Los errores del servidor se interpretan por `codigo`, nunca por
 * `detail`, y ningún aviso refleja contenido del archivo.
 */

/** Por qué no se puede usar un archivo (el servidor lo respondería con un `400` sin `errores`). */
export const MENSAJES_ERROR_ARCHIVO: Readonly<Record<ErrorArchivo, string>> = {
  TAMANO: 'El archivo supera el máximo de 2 MB',
  VACIO: 'El archivo está vacío o no tiene filas de datos',
  NO_UTF8: 'El archivo debe estar codificado en UTF-8',
  DEMASIADAS_FILAS: 'El archivo tiene más de 5000 filas de datos',
  COMILLA_SIN_CERRAR: 'El archivo tiene una comilla sin cerrar',
};

/** Por qué no se puede pedir la vista previa con un mapeo. */
export const MENSAJES_ERROR_MAPEO: Readonly<Record<ErrorMapeo, string>> = {
  MILES_IGUAL_DECIMAL: 'El separador de miles no puede ser igual al de decimales',
  FALTA_FECHA: 'Elige la columna de la fecha',
  FALTA_DESCRIPCION: 'Elige la columna de la descripción',
  FALTA_MONTO: 'Elige la columna del monto',
  FALTA_DEBITO_CREDITO: 'Elige las columnas de débito y de crédito',
};

/** `400 DATOS_INVALIDOS`: todas sus causas llegan sin `errores`, así que un solo mensaje. */
export const MENSAJE_DATOS_INVALIDOS =
  'No pudimos leer el archivo con ese mapeo. Revisa que sea un CSV en UTF-8 de hasta 2 MB y ' +
  '5000 filas, que las columnas elegidas existan y que los formatos de fecha y de monto coincidan';

export const MENSAJE_CUENTA_CERRADA = 'La cuenta está cerrada; reábrela en Cuentas para importar';
export const MENSAJE_FILAS_INVALIDAS =
  'El archivo tiene filas inválidas: márcalas para omitirlas o corrige el archivo';
export const MENSAJE_REGLA_GENERICA =
  'No se pudo importar: el archivo o la cuenta ya no cumplen las reglas';

/** `404 RECURSO_NO_ENCONTRADO`: la cuenta ya no existe. */
export const MENSAJE_CUENTA_NO_ENCONTRADA = 'No encontramos esa cuenta; vuelve a Cuentas';

export const MENSAJE_LEYENDO = 'Leyendo el archivo…';
export const MENSAJE_VISTA_PREVIA_DESACTUALIZADA =
  'Cambiaste el mapeo: pide la vista previa de nuevo para importar';
export const MENSAJE_SIN_FILAS_NUEVAS =
  'No hay filas nuevas para importar: las del archivo ya existen en la cuenta';
export const AVISO_VISTA_PREVIA_DEL_SERVIDOR =
  'Esta lectura del archivo es solo para elegir las columnas: la vista previa del servidor es la ' +
  'que decide qué filas se importan';

/** Lo que se sabe cuando el servidor responde `422 REGLA_NEGOCIO_VIOLADA`. */
export interface ContextoReglaDeNegocio {
  /** La cuenta que leyó la pantalla estaba cerrada. */
  cuentaCerrada: boolean;
  /** La vista previa tenía filas inválidas y no se marcó `omitirInvalidas`. */
  hayInvalidasSinOmitir: boolean;
}

/**
 * El `422` sirve para varias reglas con el mismo código: se elige por el contexto, sin leer
 * `detail`. Primero la cuenta cerrada, luego las filas inválidas y, si no, un mensaje genérico.
 */
export function mensajeReglaDeNegocio(contexto: ContextoReglaDeNegocio): string {
  if (contexto.cuentaCerrada) {
    return MENSAJE_CUENTA_CERRADA;
  }
  if (contexto.hayInvalidasSinOmitir) {
    return MENSAJE_FILAS_INVALIDAS;
  }
  return MENSAJE_REGLA_GENERICA;
}
