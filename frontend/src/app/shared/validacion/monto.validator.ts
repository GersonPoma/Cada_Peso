import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { leerMonto } from '../formato/milliunits';

/**
 * Valida un monto escrito como texto, con los separadores de la región del usuario (o de
 * `region`, para los tests). Vacío es válido (se combina con `Validators.required` si hace
 * falta). Errores: `montoFormato`, `montoDecimales` (más de 3) y `montoRango`.
 */
export function montoValido(region?: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const valor: unknown = control.value;
    if (valor === null || valor === undefined) {
      return null;
    }
    const lectura = leerMonto(String(valor), region);
    if (lectura.estado !== 'invalido') {
      return null;
    }
    switch (lectura.motivo) {
      case 'decimales':
        return { montoDecimales: true };
      case 'rango':
        return { montoRango: true };
      default:
        return { montoFormato: true };
    }
  };
}
