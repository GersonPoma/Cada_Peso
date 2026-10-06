import { AbstractControl, FormControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Aplica un validador de Angular al valor **recortado** (`trim()`) del control, sin modificar el
 * control. Reutiliza los validadores nativos y sus claves de error (`required`, `email`,
 * `minlength`, `maxlength`), pero midiendo igual que el backend: sin los espacios sobrantes de los
 * extremos. Con `Validators.required`, un texto de solo espacios cuenta como vacío (`@NotBlank`).
 */
export function sobreTextoRecortado(validador: ValidatorFn): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor: unknown = control.value;
    const recortado = typeof valor === 'string' ? valor.trim() : valor;
    return validador(new FormControl(recortado));
  };
}
