import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatOptgroup, MatOption } from '@angular/material/core';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { deMilliunits, leerMonto } from '../../../shared/formato/milliunits';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import {
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { montoValido } from '../../../shared/validacion/monto.validator';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { CategoriaMesResponse } from '../models/categoria-mes-response.model';
import { DatosDialogoMoverDinero } from '../models/datos-dialogo-mover-dinero.model';
import { ResultadoMoverDinero } from '../models/resultado-mover-dinero.model';
import { MesPresupuestoService } from '../services/mes-presupuesto.service';

export const MENSAJE_SIN_DISPONIBLE = 'El origen no tiene suficiente disponible';
export const MENSAJE_CATEGORIA_INEXISTENTE =
  'Una de las categorías ya no existe. Actualizamos el mes.';

const MENSAJES_ORIGEN: MensajesDeError = { required: 'Elige el origen' };
const MENSAJES_DESTINO: MensajesDeError = {
  required: 'Elige el destino',
  mismaCategoria: 'El destino debe ser distinto del origen',
};
const MENSAJES_MONTO: MensajesDeError = {
  required: 'El monto es obligatorio',
  montoFormato: 'Escribe un monto válido',
  montoRango: 'Escribe un monto válido',
  montoDecimales: 'Usa como máximo 3 decimales',
  noPositivo: 'El monto debe ser mayor que 0',
  superaDisponible: 'Supera el disponible del origen',
};

/** Destino distinto del origen (control hermano `origenId`). */
const distintoDelOrigen: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const origen = control.parent?.get('origenId')?.value as number | null | undefined;
  return control.value !== null && control.value === origen ? { mismaCategoria: true } : null;
};

/**
 * Mover dinero entre dos categorías del mes. Replica las reglas del backend (monto mayor que 0,
 * origen distinto del destino, sin superar el disponible del origen), hace la petición y se
 * cierra con el mes actualizado, o pidiendo recargarlo si una categoría ya no existe.
 */
@Component({
  selector: 'app-dialogo-mover-dinero',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatError,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    MatOptgroup,
    MatOption,
    MatSelect,
    MontoPipe,
  ],
  templateUrl: './dialogo-mover-dinero.component.html',
  styleUrl: './dialogo-mover-dinero.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoMoverDineroComponent {
  private readonly datos = inject<DatosDialogoMoverDinero>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoMoverDineroComponent, ResultadoMoverDinero>>(MatDialogRef);
  private readonly servicio = inject(MesPresupuestoService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly moneda = this.presupuestoActivo.presupuesto()?.moneda ?? 'USD';

  /** Grupos visibles con sus categorías visibles. */
  protected readonly grupos = this.datos.mes.grupos
    .filter((grupo) => !grupo.oculto)
    .map((grupo) => ({ ...grupo, categorias: grupo.categorias.filter((c) => !c.oculta) }))
    .filter((grupo) => grupo.categorias.length > 0);

  private readonly categorias = new Map<number, CategoriaMesResponse>(
    this.datos.mes.grupos.flatMap((g) => g.categorias.map((c) => [c.categoriaId, c] as const)),
  );

  /** El monto no puede superar el disponible del origen (control hermano `origenId`). */
  private readonly dentroDelDisponible: ValidatorFn = (control) => {
    const lectura = leerMonto(String(control.value ?? ''));
    if (lectura.estado !== 'valido') {
      return null;
    }
    if (lectura.milliunits <= 0) {
      return { noPositivo: true };
    }
    const origenId = control.parent?.get('origenId')?.value as number | null | undefined;
    const origen = origenId != null ? this.categorias.get(origenId) : undefined;
    return origen && lectura.milliunits > origen.disponible ? { superaDisponible: true } : null;
  };

  protected readonly formulario = new FormGroup({
    origenId: new FormControl<number | null>(this.datos.origenId ?? null, Validators.required),
    destinoId: new FormControl<number | null>(this.datos.destinoId ?? null, [
      Validators.required,
      distintoDelOrigen,
    ]),
    monto: new FormControl(this.datos.monto ? deMilliunits(this.datos.monto) : '', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        montoValido(),
        this.dentroDelDisponible,
      ],
    }),
  });

  private readonly origenElegido = toSignal(this.formulario.controls.origenId.valueChanges, {
    initialValue: this.formulario.controls.origenId.value,
  });

  /** Disponible del origen elegido, para la ayuda del campo monto. */
  protected readonly disponibleOrigen = computed(() => {
    const id = this.origenElegido();
    return id != null ? (this.categorias.get(id)?.disponible ?? null) : null;
  });

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    // Las reglas del destino y del monto dependen del origen: se reevalúan al cambiarlo.
    this.formulario.controls.origenId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.formulario.controls.destinoId.updateValueAndValidity();
      this.formulario.controls.monto.updateValueAndValidity();
    });
    if (this.datos.monto) {
      // Un monto propuesto (cubrir sobregasto) se valida a la vista desde el principio.
      this.formulario.controls.monto.markAsTouched();
    }
  }

  protected mensaje(campo: 'origenId' | 'destinoId' | 'monto'): string | null {
    const mensajes = {
      origenId: MENSAJES_ORIGEN,
      destinoId: MENSAJES_DESTINO,
      monto: MENSAJES_MONTO,
    };
    return mensajeDeError(this.formulario.controls[campo].errors, mensajes[campo]);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    const { origenId, destinoId, monto } = this.formulario.getRawValue();
    const lectura = leerMonto(monto);
    if (origenId === null || destinoId === null || lectura.estado !== 'valido') {
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set(null);
    this.dialogRef.disableClose = true;
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;

    this.servicio
      .moverDinero(presupuestoId, this.datos.mes.mes, {
        origenId,
        destinoId,
        monto: lectura.milliunits,
      })
      .subscribe({
        next: (mes) => this.dialogRef.close({ tipo: 'movido', mes }),
        error: (error: unknown) => {
          this.enviando.set(false);
          this.dialogRef.disableClose = false;
          this.mostrarError(error);
        },
      });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
      this.errorGeneral.set(MENSAJE_SIN_DISPONIBLE);
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      if (aplicarErroresDeCampos(this.formulario, problema.errores).length > 0) {
        this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
      }
      return;
    }
    if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.snackBar.open(MENSAJE_CATEGORIA_INEXISTENTE, 'Cerrar', { duration: 6000 });
      this.dialogRef.close({ tipo: 'recargar' });
      return;
    }
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }
}
