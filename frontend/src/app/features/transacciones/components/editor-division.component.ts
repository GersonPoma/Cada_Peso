import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatOptgroup, MatOption } from '@angular/material/core';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { CampoMontoComponent } from '../../../shared/calculadora/campo-monto.component';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { GrupoCategoriasResumen } from '../models/grupo-categorias-resumen.model';

export const MINIMO_PARTES = 2;
export const MAXIMO_PARTES = 20;

/** Una parte de la división: monto en milésimas, positivo en el sentido de la transacción. */
export type FormularioParte = FormGroup<{
  categoriaId: FormControl<number | null>;
  monto: FormControl<number | null>;
  memo: FormControl<string>;
}>;

const distintoDeCero: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.value === 0 ? { cero: true } : null;

/** Crea una parte del formulario de división. */
export function crearParte(
  valores: { categoriaId?: number | null; monto?: number | null; memo?: string | null } = {},
): FormularioParte {
  return new FormGroup({
    categoriaId: new FormControl<number | null>(valores.categoriaId ?? null),
    monto: new FormControl<number | null>(valores.monto ?? null, [
      Validators.required,
      distintoDeCero,
    ]),
    memo: new FormControl(valores.memo ?? '', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.maxLength(500))],
    }),
  });
}

/** Suma entera, en milésimas, de los montos de las partes. */
export function sumaDePartes(partes: FormArray<FormularioParte>): number {
  return partes.controls.reduce((suma, parte) => suma + (parte.controls.monto.value ?? 0), 0);
}

/**
 * Validador de la lista de partes: entre 2 y 20, y que su suma sea exactamente `montoTotal()`.
 * Recibe una función porque el monto de la transacción vive en otro control.
 */
export function divisionExacta(montoTotal: () => number | null): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const partes = control as FormArray<FormularioParte>;
    if (partes.length < MINIMO_PARTES || partes.length > MAXIMO_PARTES) {
      return { cantidadPartes: true };
    }
    const total = montoTotal();
    return total !== null && sumaDePartes(partes) !== total ? { sumaDistinta: true } : null;
  };
}

/**
 * Editor de las partes de una transacción dividida: categoría, monto (con calculadora) y memo de
 * cada parte, agregar y quitar (de 2 a 20), y cuánto falta o sobra para llegar al monto.
 */
@Component({
  selector: 'app-editor-division',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatError,
    MatFormField,
    MatIcon,
    MatIconButton,
    MatInput,
    MatLabel,
    MatOptgroup,
    MatOption,
    MatSelect,
    MontoPipe,
    CampoMontoComponent,
  ],
  templateUrl: './editor-division.component.html',
  styleUrl: './editor-division.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditorDivisionComponent {
  readonly partes = input.required<FormArray<FormularioParte>>();
  /** Monto de la transacción en milésimas (positivo). */
  readonly montoTotal = input<number | null>(null);
  readonly grupos = input<GrupoCategoriasResumen[]>([]);
  readonly moneda = input.required<string>();

  protected readonly maximo = MAXIMO_PARTES;
  protected readonly minimo = MINIMO_PARTES;
  protected readonly mensajesParte = { cero: 'La parte no puede ser 0' };

  protected suma(): number {
    return sumaDePartes(this.partes());
  }

  /** Lo que falta (positivo) o sobra (negativo) para llegar al monto de la transacción. */
  protected diferencia(): number {
    return (this.montoTotal() ?? 0) - this.suma();
  }

  protected agregar(): void {
    if (this.partes().length < MAXIMO_PARTES) {
      this.partes().push(crearParte());
    }
  }

  protected quitar(indice: number): void {
    if (this.partes().length > MINIMO_PARTES) {
      this.partes().removeAt(indice);
    }
  }
}
