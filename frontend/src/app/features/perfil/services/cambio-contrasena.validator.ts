import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { Subscription } from 'rxjs';

/**
 * La confirmación debe ser igual a otra contraseña del mismo formulario (`noCoincide`). Un valor
 * vacío es válido aquí: de eso se ocupa `required`. Compara sin recortar ni normalizar nada.
 */
export function coincideCon(nombreOtro: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const otro = control.parent?.get(nombreOtro);
    if (!otro || control.value === '' || control.value === null) {
      return null;
    }
    return control.value === otro.value ? null : { noCoincide: true };
  };
}

/**
 * La contraseña nueva debe ser distinta de otra del mismo formulario (`igualALaActual`). La
 * comparación es exacta, igual que la del backend (`equals`). Un valor vacío es válido aquí.
 */
export function distintaDe(nombreOtro: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const otro = control.parent?.get(nombreOtro);
    if (!otro || control.value === '' || control.value === null) {
      return null;
    }
    return control.value === otro.value ? { igualALaActual: true } : null;
  };
}

/** Revalida `destino` cada vez que cambia el valor de `origen`; devuelve la suscripción. */
export function revalidarAlCambiar(
  origen: AbstractControl,
  destino: AbstractControl,
): Subscription {
  return origen.valueChanges.subscribe(() => destino.updateValueAndValidity());
}
