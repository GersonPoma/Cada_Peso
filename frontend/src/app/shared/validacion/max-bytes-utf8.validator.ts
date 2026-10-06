import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * El texto no puede ocupar más de `maximo` bytes en UTF-8 (una `ñ` o una vocal con tilde ocupan
 * 2). A diferencia de `Validators.maxLength`, cuenta bytes y no caracteres: replica
 * `@MaximoBytesUtf8` del backend. Un valor vacío o que no es texto es válido.
 */
export function maxBytesUtf8(maximo: number): ValidatorFn {
  const codificador = new TextEncoder();
  return (control: AbstractControl): ValidationErrors | null => {
    const valor: unknown = control.value;
    if (typeof valor !== 'string' || valor === '') {
      return null;
    }
    const actual = codificador.encode(valor).length;
    return actual > maximo ? { maxBytesUtf8: { maximo, actual } } : null;
  };
}
