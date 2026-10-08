import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatOptgroup, MatOption } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { map, merge, startWith } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { CampoMontoComponent } from '../../../shared/calculadora/campo-monto.component';
import { deFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import {
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import {
  DatosDialogoProgramada,
  ResultadoDialogoProgramada,
} from '../models/datos-dialogo-programada.model';
import {
  FrecuenciaProgramada,
  TransaccionProgramadaResponse,
} from '../models/transaccion-programada-response.model';
import { accionError } from '../services/errores-programada';
import {
  ValorFormularioProgramada,
  aActualizarRequest,
  aCrearRequest,
  finNoAnteriorAInicio,
  opcionesCategoria,
} from '../services/formulario-programada';
import {
  AVISO_FIN_DE_MES,
  NOTA_CREAR,
  NOTA_EDITAR,
  NOTA_SOLO_LECTURA,
} from '../services/mensajes-programada';
import {
  FRECUENCIAS,
  TEXTOS_FRECUENCIA,
  TipoMonto,
  montoAbsoluto,
  necesitaAvisoFinDeMes,
  tipoMonto,
} from '../services/presentacion-programada';
import { TransaccionProgramadaService } from '../services/transaccion-programada.service';

export const MAXIMO_BENEFICIARIO = 100;
export const MAXIMO_MEMO = 500;

const MENSAJES_MONTO = { noPositivo: 'El monto debe ser mayor que 0' };
const MENSAJES: Readonly<Record<string, MensajesDeError>> = {
  cuentaId: { required: 'La cuenta es obligatoria' },
  fechaInicio: {
    required: 'La fecha de inicio es obligatoria',
    matDatepickerParse: 'La fecha no es válida',
  },
  fechaFin: {
    matDatepickerParse: 'La fecha no es válida',
    finAnterior: 'La fecha de fin no puede ser anterior a la de inicio',
  },
  beneficiario: {
    maxlength: `El beneficiario no puede superar los ${MAXIMO_BENEFICIARIO} caracteres`,
  },
  memo: { maxlength: `El memo no puede superar los ${MAXIMO_MEMO} caracteres` },
};

const mayorQueCero = (control: AbstractControl): ValidationErrors | null =>
  control.value !== null && control.value <= 0 ? { noPositivo: true } : null;

/** Fecha local de hoy a medianoche (nunca a partir de UTC). */
function hoy(): Date {
  const ahora = new Date();
  return new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
}

/**
 * Crear o editar una transacción programada. El monto se escribe en positivo y el tipo (Salida o
 * Entrada) le da el signo. Al editar, la cuenta y la fecha de inicio se muestran sin poder
 * cambiarse (el `PUT` no las lleva). Hace la petición y solo se cierra si tiene éxito o si algo
 * ya no existe (`recargar`).
 */
@Component({
  selector: 'app-dialogo-programada',
  imports: [
    ReactiveFormsModule,
    CampoMontoComponent,
    FechaPipe,
    MatButton,
    MatButtonToggle,
    MatButtonToggleGroup,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
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
    MatSuffix,
  ],
  templateUrl: './dialogo-programada.component.html',
  styleUrl: './dialogo-programada.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoProgramadaComponent {
  private readonly datos = inject<DatosDialogoProgramada>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoProgramadaComponent, ResultadoDialogoProgramada>>(MatDialogRef);
  private readonly servicio = inject(TransaccionProgramadaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly original: TransaccionProgramadaResponse | null = this.datos.original;
  protected readonly esCrear = this.original === null;
  protected readonly frecuencias = FRECUENCIAS;
  protected readonly textosFrecuencia = TEXTOS_FRECUENCIA;
  protected readonly mensajesMonto = MENSAJES_MONTO;
  protected readonly maximoMemo = MAXIMO_MEMO;
  protected readonly avisoFinDeMes = AVISO_FIN_DE_MES;
  protected readonly nota = this.esCrear ? NOTA_CREAR : NOTA_EDITAR;
  protected readonly notaSoloLectura = NOTA_SOLO_LECTURA;

  /** Al crear, solo las cuentas abiertas, agrupadas como en Transacciones. */
  protected readonly gruposCuentas = (() => {
    const abiertas = this.datos.cuentas.filter((c) => !c.cerrada);
    return [
      { etiqueta: 'En el presupuesto', cuentas: abiertas.filter((c) => c.enPresupuesto) },
      { etiqueta: 'Seguimiento', cuentas: abiertas.filter((c) => !c.enPresupuesto) },
    ].filter((grupo) => grupo.cuentas.length > 0);
  })();

  /** Al editar, el nombre de la cuenta de la plantilla, aunque esté cerrada. */
  protected readonly nombreCuenta =
    this.datos.cuentas.find((c) => c.id === this.original?.cuentaId)?.nombre ?? '—';

  protected readonly grupos = opcionesCategoria(
    this.datos.grupos,
    this.original?.categoriaId ?? null,
  );

  protected readonly formulario = new FormGroup({
    cuentaId: new FormControl<number | null>(
      { value: this.original?.cuentaId ?? null, disabled: !this.esCrear },
      Validators.required,
    ),
    tipo: new FormControl<TipoMonto>(this.original ? tipoMonto(this.original.monto) : 'salida', {
      nonNullable: true,
    }),
    monto: new FormControl<number | null>(
      this.original ? montoAbsoluto(this.original.monto) : null,
      [Validators.required, mayorQueCero],
    ),
    frecuencia: new FormControl<FrecuenciaProgramada>(this.original?.frecuencia ?? 'MENSUAL', {
      nonNullable: true,
      validators: Validators.required,
    }),
    fechaInicio: new FormControl<Date | null>(
      {
        value: this.original ? deFechaNegocio(this.original.fechaInicio) : hoy(),
        disabled: !this.esCrear,
      },
      Validators.required,
    ),
    fechaFin: new FormControl<Date | null>(
      deFechaNegocio(this.original?.fechaFin),
      finNoAnteriorAInicio,
    ),
    categoriaId: new FormControl<number | null>(this.original?.categoriaId ?? null),
    beneficiario: new FormControl(this.original?.beneficiario ?? '', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.maxLength(MAXIMO_BENEFICIARIO))],
    }),
    memo: new FormControl(this.original?.memo ?? '', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.maxLength(MAXIMO_MEMO))],
    }),
  });

  /** El aviso de fin de mes, según la frecuencia y el día de inicio elegidos. */
  protected readonly muestraAvisoFinDeMes = toSignal(
    merge(
      this.formulario.controls.frecuencia.valueChanges,
      this.formulario.controls.fechaInicio.valueChanges,
    ).pipe(
      startWith(null),
      map(() =>
        necesitaAvisoFinDeMes(
          this.formulario.controls.frecuencia.value,
          this.formulario.controls.fechaInicio.value,
        ),
      ),
    ),
    { initialValue: false },
  );

  private readonly memoActual = toSignal(this.formulario.controls.memo.valueChanges, {
    initialValue: this.formulario.controls.memo.value,
  });
  protected readonly largoMemo = computed(() => this.memoActual().trim().length);

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    // La fecha de fin se compara con la de inicio: se revalida al cambiarla.
    this.formulario.controls.fechaInicio.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const { fechaFin } = this.formulario.controls;
      fechaFin.updateValueAndValidity();
      if (fechaFin.value !== null) {
        fechaFin.markAsTouched();
      }
    });
  }

  protected mensaje(campo: keyof typeof MENSAJES): string | null {
    return mensajeDeError(this.formulario.get(campo)?.errors ?? null, MENSAJES[campo]);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set(null);
    this.dialogRef.disableClose = true;
    const valor = this.formulario.getRawValue() as ValorFormularioProgramada;
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    const peticion = this.original
      ? this.servicio.actualizar(presupuestoId, this.original.id, aActualizarRequest(valor))
      : this.servicio.crear(presupuestoId, aCrearRequest(valor));
    peticion.subscribe({
      next: () => this.dialogRef.close('guardada'),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.dialogRef.disableClose = false;
        this.mostrarError(error);
      },
    });
  }

  private mostrarError(error: unknown): void {
    const accion = accionError(error);
    switch (accion.accion) {
      case 'campos':
        if (aplicarErroresDeCampos(this.formulario, accion.errores).length > 0) {
          this.errorGeneral.set('Revisa los datos del formulario');
        }
        return;
      case 'mensaje':
        this.errorGeneral.set(accion.mensaje);
        return;
      case 'recargar':
        this.snackBar.open(accion.mensaje, 'Cerrar', { duration: 6000 });
        this.dialogRef.close('recargar');
        return;
      case 'generico':
        this.snackBar.open(accion.mensaje, 'Cerrar', { duration: 6000 });
        return;
      case 'ninguna':
        return;
    }
  }
}
