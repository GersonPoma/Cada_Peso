import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * La fecha de nacimiento (`Date` del datepicker) corresponde a una persona con al menos
 * `edadMinima` años cumplidos a la fecha local de hoy; el día del aniversario cuenta como
 * cumplido. Da el mismo resultado que `Period.between(...).getYears()` del backend, incluido el 29
 * de febrero. `null` o una fecha inválida es válido: de eso se encargan `required` y el error
 * `matDatepickerParse`.
 */
export function mayorDeEdad(edadMinima = 18): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const nacimiento: unknown = control.value;
    if (!(nacimiento instanceof Date) || isNaN(nacimiento.getTime())) {
      return null;
    }
    const hoy = new Date();
    const aunNoCumple =
      hoy.getMonth() < nacimiento.getMonth() ||
      (hoy.getMonth() === nacimiento.getMonth() && hoy.getDate() < nacimiento.getDate());
    const edad = hoy.getFullYear() - nacimiento.getFullYear() - (aunNoCumple ? 1 : 0);
    return edad >= edadMinima ? null : { mayorDeEdad: { edadMinima } };
  };
}
