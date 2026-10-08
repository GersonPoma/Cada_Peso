import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import {
  MENSAJE_FECHAS_MONTO,
  MENSAJE_REFERENCIA_INEXISTENTE,
  MENSAJE_REGLA_PROGRAMADA,
} from './mensajes-programada';

/**
 * Qué hacer con un error de guardar, decidido por `codigo` (nunca por `detail`):
 * - `campos`: poner cada mensaje en su campo.
 * - `mensaje`: mostrarlo en el diálogo, sin cerrarlo.
 * - `recargar`: cerrar el diálogo, avisar y recargar la pantalla.
 * - `generico`: aviso genérico, sin cerrar.
 * - `ninguna`: sesión vencida (el interceptor ya redirige).
 */
export type AccionError =
  | { accion: 'campos'; errores: Record<string, string> }
  | { accion: 'mensaje'; mensaje: string }
  | { accion: 'recargar'; mensaje: string }
  | { accion: 'generico'; mensaje: string }
  | { accion: 'ninguna' };

export function accionError(error: unknown): AccionError {
  const problema = leerProblemaApi(error);
  if (problema?.status === 401) {
    return { accion: 'ninguna' };
  }
  if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS) {
    return problema.errores && Object.keys(problema.errores).length > 0
      ? { accion: 'campos', errores: problema.errores }
      : { accion: 'mensaje', mensaje: MENSAJE_FECHAS_MONTO };
  }
  if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
    return { accion: 'mensaje', mensaje: MENSAJE_REGLA_PROGRAMADA };
  }
  if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
    return { accion: 'recargar', mensaje: MENSAJE_REFERENCIA_INEXISTENTE };
  }
  return { accion: 'generico', mensaje: MENSAJE_ERROR_GENERICO };
}
