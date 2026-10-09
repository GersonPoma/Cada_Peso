import { Validators, ValidatorFn } from '@angular/forms';
import { maxBytesUtf8 } from './max-bytes-utf8.validator';
import { sobreTextoRecortado } from './sobre-texto-recortado.validator';

/**
 * Reglas de una contraseña nueva, las mismas del registro y del cambio de contraseña (replican
 * `@ContrasenaValida` del backend): obligatoria (un texto de solo espacios cuenta como vacío), al
 * menos 8 caracteres y como máximo 72 bytes en UTF-8. La contraseña nunca se recorta: solo
 * `required` ignora los espacios de los extremos. Los mensajes viven en `app-campo-contrasena`.
 */
export function validadoresContrasenaNueva(): ValidatorFn[] {
  return [sobreTextoRecortado(Validators.required), Validators.minLength(8), maxBytesUtf8(72)];
}
