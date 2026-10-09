import { ValidatorFn, Validators } from '@angular/forms';
import { MensajesDeError } from '../formulario/errores-formulario';
import { sobreTextoRecortado } from './sobre-texto-recortado.validator';

/**
 * Reglas del nombre de la persona, las mismas del registro y del cambio de nombre: obligatorio y
 * de 2 a 100 caracteres, medidos sin espacios al inicio ni al final.
 */
export function validadoresNombrePersona(): ValidatorFn[] {
  return [
    sobreTextoRecortado(Validators.required),
    sobreTextoRecortado(Validators.minLength(2)),
    sobreTextoRecortado(Validators.maxLength(100)),
  ];
}

export const MENSAJES_NOMBRE_PERSONA: MensajesDeError = {
  required: 'El nombre es obligatorio',
  minlength: 'El nombre debe tener al menos 2 caracteres',
  maxlength: 'El nombre no puede superar 100 caracteres',
};
