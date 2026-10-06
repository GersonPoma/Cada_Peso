import { FormGroup, ValidationErrors } from '@angular/forms';

/** Un texto, o una función que lo arma a partir del valor del error (ej. `minlength`). */
export type MensajeDeError = string | ((valorDelError: unknown) => string);

/** Mensajes por clave de error; el orden de las claves es el orden de prioridad. */
export type MensajesDeError = Readonly<Record<string, MensajeDeError>>;

/** Clave del error que un control recibe desde una respuesta del backend. */
export const CLAVE_ERROR_SERVIDOR = 'servidor';

/**
 * Devuelve el mensaje del error más prioritario del control, o `null` si no hay ninguno que se
 * sepa mostrar. Un error del servidor (`servidor`, cuyo valor es el texto) va siempre primero; el
 * resto sigue el orden de `mensajes`, no el del objeto de errores.
 */
export function mensajeDeError(
  errores: ValidationErrors | null,
  mensajes: MensajesDeError,
): string | null {
  if (!errores) {
    return null;
  }
  const delServidor = errores[CLAVE_ERROR_SERVIDOR];
  if (typeof delServidor === 'string') {
    return delServidor;
  }
  for (const clave of Object.keys(mensajes)) {
    if (clave in errores) {
      const mensaje = mensajes[clave];
      return typeof mensaje === 'function' ? mensaje(errores[clave]) : mensaje;
    }
  }
  return null;
}

/**
 * Pone cada mensaje del backend (`errores` de un `DATOS_INVALIDOS`) como error `servidor` del
 * control con ese nombre y lo marca como tocado. Devuelve las claves que no corresponden a ningún
 * control. El error se borra solo cuando el control revalida al editarse, y mientras exista el
 * formulario es inválido.
 */
export function aplicarErroresDeCampos(
  formulario: FormGroup,
  errores: Readonly<Record<string, string>>,
): string[] {
  const sinControl: string[] = [];
  for (const [campo, mensaje] of Object.entries(errores)) {
    const control = formulario.get(campo);
    if (!control) {
      sinControl.push(campo);
      continue;
    }
    control.setErrors({ ...control.errors, [CLAVE_ERROR_SERVIDOR]: mensaje });
    control.markAsTouched();
  }
  return sinControl;
}
