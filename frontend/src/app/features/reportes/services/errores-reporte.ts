import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { AvisoError } from '../models/estado-carga.model';
import {
  MENSAJE_CUENTA_INEXISTENTE,
  MENSAJE_PRESUPUESTO_INEXISTENTE,
  MENSAJE_RANGO_INVALIDO,
} from './mensajes-reporte';

/** De qué reporte es el error: el de una cuenta distingue la cuenta inexistente. */
export type ContextoError = 'reporte' | 'cuenta';

/** El aviso de un error de un reporte, decidido por `codigo` (nunca por `detail`). */
export function avisoError(error: unknown, contexto: ContextoError = 'reporte'): AvisoError {
  const problema = leerProblemaApi(error);
  if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS) {
    return { mensaje: MENSAJE_RANGO_INVALIDO, accion: 'reintentar' };
  }
  if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
    return contexto === 'cuenta'
      ? { mensaje: MENSAJE_CUENTA_INEXISTENTE, accion: 'reintentar', cuentaInexistente: true }
      : { mensaje: MENSAJE_PRESUPUESTO_INEXISTENTE, accion: 'recargar' };
  }
  return { mensaje: MENSAJE_ERROR_GENERICO, accion: 'reintentar' };
}
