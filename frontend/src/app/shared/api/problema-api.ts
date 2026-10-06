import { HttpErrorResponse } from '@angular/common/http';

/** Códigos de error (`codigo` del `ProblemDetail`) que el frontend interpreta. */
export const CODIGOS_API = {
  DATOS_INVALIDOS: 'DATOS_INVALIDOS',
  CREDENCIALES_INVALIDAS: 'CREDENCIALES_INVALIDAS',
  EMAIL_YA_REGISTRADO: 'EMAIL_YA_REGISTRADO',
  NO_AUTENTICADO: 'NO_AUTENTICADO',
  PRESUPUESTO_YA_EXISTE: 'PRESUPUESTO_YA_EXISTE',
} as const;

/** Aviso para cualquier error que el frontend no sabe mostrar de otra forma. */
export const MENSAJE_ERROR_GENERICO = 'No pudimos completar la operación. Inténtalo de nuevo.';

/** Lo que el frontend usa de un `ProblemDetail` del backend. */
export interface ProblemaApi {
  status: number;
  codigo?: string;
  detail?: string;
  /** Mensaje por campo, solo en `DATOS_INVALIDOS` de validación de campos. */
  errores?: Record<string, string>;
}

/**
 * Lee un error de `HttpClient` como `ProblemaApi`. Devuelve `null` si no es un
 * `HttpErrorResponse`. Un fallo de red (`status 0`) o un cuerpo que no tiene forma de
 * `ProblemDetail` da un problema sin `codigo`. Siempre se decide por `codigo`, nunca por `detail`.
 */
export function leerProblemaApi(error: unknown): ProblemaApi | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  const problema: ProblemaApi = { status: error.status };
  const cuerpo: unknown = error.error;
  if (cuerpo === null || typeof cuerpo !== 'object') {
    return problema;
  }
  const datos = cuerpo as Record<string, unknown>;
  if (typeof datos['codigo'] === 'string') {
    problema.codigo = datos['codigo'];
  }
  if (typeof datos['detail'] === 'string') {
    problema.detail = datos['detail'];
  }
  const errores = datos['errores'];
  if (errores !== null && typeof errores === 'object' && !Array.isArray(errores)) {
    problema.errores = Object.fromEntries(
      Object.entries(errores).filter((entrada): entrada is [string, string] => {
        return typeof entrada[1] === 'string';
      }),
    );
  }
  return problema;
}
