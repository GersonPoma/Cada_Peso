import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
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
import { MatOption } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { merge } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { CampoMontoComponent } from '../../../shared/calculadora/campo-monto.component';
import { deFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import {
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import {
  DatosDialogoConfirmacionMeta,
  DatosDialogoMeta,
  ResultadoDialogoMeta,
} from '../models/datos-dialogos-metas.model';
import { FrecuenciaMeta, MetaResponse, TipoMeta } from '../models/meta-response.model';
import { aGuardarMetaRequest } from '../services/guardar-meta';
import { MetaService } from '../services/meta.service';
import { DialogoConfirmacionMetaComponent } from './dialogo-confirmacion-meta.component';

export const MENSAJE_CONFLICTO_META = 'La meta cambió al mismo tiempo; vuelve a guardar';
export const MENSAJE_META_INEXISTENTE = 'La categoría o su meta ya no existe. Actualizamos el mes.';
export const MENSAJE_REGLA_META = 'No se pudo guardar la meta en este momento.';
export const AYUDA_META = 'La meta se aplica a todos los meses, también a los anteriores';

export const TIPOS_META: readonly { valor: TipoMeta; texto: string }[] = [
  { valor: 'MONTO_MENSUAL', texto: 'Monto cada cierto tiempo' },
  { valor: 'MONTO_PARA_FECHA', texto: 'Monto para una fecha' },
  { valor: 'SALDO_OBJETIVO', texto: 'Saldo objetivo' },
];

const FRECUENCIAS: readonly { valor: FrecuenciaMeta; texto: string }[] = [
  { valor: 'MENSUAL', texto: 'Mensual' },
  { valor: 'SEMANAL', texto: 'Semanal' },
  { valor: 'PERSONALIZADA', texto: 'Personalizada' },
];

/** Días de la semana como los numera el backend: 1 (lunes) a 7 (domingo). */
const DIAS_SEMANA: readonly { valor: number; texto: string }[] = [
  { valor: 1, texto: 'Lunes' },
  { valor: 2, texto: 'Martes' },
  { valor: 3, texto: 'Miércoles' },
  { valor: 4, texto: 'Jueves' },
  { valor: 5, texto: 'Viernes' },
  { valor: 6, texto: 'Sábado' },
  { valor: 7, texto: 'Domingo' },
];

const MENSAJES_MONTO: MensajesDeError = { noPositivo: 'El monto debe ser mayor que 0' };
const MENSAJE_INTERVALO = 'Entre 2 y 365 días';
const MENSAJES: Record<Campo, MensajesDeError> = {
  diaSemana: { required: 'Elige el día de la semana' },
  intervaloDias: {
    required: 'Escribe cada cuántos días',
    min: MENSAJE_INTERVALO,
    max: MENSAJE_INTERVALO,
    noEntero: MENSAJE_INTERVALO,
  },
  fechaInicio: {
    required: 'La fecha de inicio es obligatoria',
    matDatepickerParse: 'La fecha no es válida',
  },
  fechaObjetivo: {
    required: 'La fecha objetivo es obligatoria',
    matDatepickerParse: 'La fecha no es válida',
  },
};

type Campo = 'diaSemana' | 'intervaloDias' | 'fechaInicio' | 'fechaObjetivo';

const mayorQueCero = (control: AbstractControl): ValidationErrors | null =>
  control.value !== null && control.value <= 0 ? { noPositivo: true } : null;

const entero = (control: AbstractControl): ValidationErrors | null =>
  control.value !== null && control.value !== '' && !Number.isInteger(Number(control.value))
    ? { noEntero: true }
    : null;

/** Fecha local de hoy a medianoche (nunca a partir de UTC). */
function hoy(): Date {
  const ahora = new Date();
  return new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
}

/**
 * Agregar, editar o quitar la meta de una categoría. Solo muestra (y valida y envía) los campos
 * del tipo y de la frecuencia elegidos: los demás quedan deshabilitados. Hace la petición y solo
 * se cierra si tiene éxito, o pidiendo recargar si la categoría o su meta ya no existen.
 */
@Component({
  selector: 'app-dialogo-meta',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatError,
    MatFormField,
    MatInput,
    MatLabel,
    MatOption,
    MatProgressBar,
    MatSelect,
    MatSuffix,
    CampoMontoComponent,
  ],
  templateUrl: './dialogo-meta.component.html',
  styleUrl: './dialogo-meta.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoMetaComponent {
  protected readonly datos = inject<DatosDialogoMeta>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoMetaComponent, ResultadoDialogoMeta>>(MatDialogRef);
  private readonly dialog = inject(MatDialog);
  private readonly servicio = inject(MetaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly tipos = TIPOS_META;
  protected readonly frecuencias = FRECUENCIAS;
  protected readonly diasSemana = DIAS_SEMANA;
  protected readonly mensajesMonto = MENSAJES_MONTO;
  protected readonly ayuda = AYUDA_META;

  protected readonly formulario = new FormGroup({
    tipo: new FormControl<TipoMeta>('MONTO_MENSUAL', { nonNullable: true }),
    monto: new FormControl<number | null>(null, [Validators.required, mayorQueCero]),
    frecuencia: new FormControl<FrecuenciaMeta>('MENSUAL', { nonNullable: true }),
    diaSemana: new FormControl<number | null>(null, Validators.required),
    intervaloDias: new FormControl<number | null>(null, [
      Validators.required,
      Validators.min(2),
      Validators.max(365),
      entero,
    ]),
    fechaInicio: new FormControl<Date | null>(hoy(), Validators.required),
    fechaObjetivo: new FormControl<Date | null>(null, Validators.required),
  });

  protected readonly tipo = toSignal(this.formulario.controls.tipo.valueChanges, {
    initialValue: this.formulario.controls.tipo.value,
  });
  protected readonly frecuencia = toSignal(this.formulario.controls.frecuencia.valueChanges, {
    initialValue: this.formulario.controls.frecuencia.value,
  });

  protected readonly cargando = signal(this.datos.tieneMeta);
  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    this.aplicarTipo();
    const { tipo, frecuencia } = this.formulario.controls;
    merge(tipo.valueChanges, frecuencia.valueChanges)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.aplicarTipo());
    if (this.datos.tieneMeta) {
      this.cargar();
    }
  }

  protected mensaje(campo: Campo): string | null {
    return mensajeDeError(this.formulario.controls[campo].errors, MENSAJES[campo]);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando() || this.cargando()) {
      return;
    }
    this.empezarEnvio();
    this.servicio
      .guardar(this.presupuestoId(), this.datos.categoriaId, aGuardarMetaRequest(this.valor()))
      .subscribe({
        next: () => this.dialogRef.close({ tipo: 'guardada' }),
        error: (error: unknown) => {
          this.terminarEnvio();
          this.mostrarError(error);
        },
      });
  }

  protected quitar(): void {
    if (this.enviando() || this.cargando()) {
      return;
    }
    this.dialog
      .open<DialogoConfirmacionMetaComponent, DatosDialogoConfirmacionMeta, boolean>(
        DialogoConfirmacionMetaComponent,
        {
          data: {
            titulo: 'Quitar meta',
            mensaje:
              `Se quitará la meta de ${this.datos.nombre} y su estado en todos los meses. ` +
              'El dinero asignado no cambia.',
            confirmar: 'Quitar',
          },
        },
      )
      .afterClosed()
      .subscribe((confirmado) => {
        if (!confirmado) {
          return;
        }
        this.empezarEnvio();
        this.servicio.quitar(this.presupuestoId(), this.datos.categoriaId).subscribe({
          next: () => this.dialogRef.close({ tipo: 'quitada' }),
          error: (error: unknown) => {
            this.terminarEnvio();
            this.mostrarError(error);
          },
        });
      });
  }

  private cargar(): void {
    this.formulario.disable({ emitEvent: false });
    this.servicio.obtener(this.presupuestoId(), this.datos.categoriaId).subscribe({
      next: (meta) => {
        this.rellenar(meta);
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        this.formulario.enable({ emitEvent: false });
        this.aplicarTipo();
        this.cargando.set(false);
        this.mostrarError(error);
      },
    });
  }

  private rellenar(meta: MetaResponse): void {
    this.formulario.enable({ emitEvent: false });
    this.formulario.setValue({
      tipo: meta.tipo,
      monto: meta.monto,
      frecuencia: meta.frecuencia ?? 'MENSUAL',
      diaSemana: meta.diaSemana,
      intervaloDias: meta.intervaloDias,
      fechaInicio: deFechaNegocio(meta.fechaInicio) ?? hoy(),
      fechaObjetivo: deFechaNegocio(meta.fechaObjetivo),
    });
    this.aplicarTipo();
  }

  /** Habilita solo los controles del tipo y la frecuencia: los demás no validan ni se envían. */
  private aplicarTipo(): void {
    const { tipo, frecuencia, diaSemana, intervaloDias, fechaInicio, fechaObjetivo } =
      this.formulario.controls;
    const mensual = tipo.value === 'MONTO_MENSUAL';
    const personalizada = mensual && frecuencia.value === 'PERSONALIZADA';
    this.habilitar(frecuencia, mensual);
    this.habilitar(diaSemana, mensual && frecuencia.value === 'SEMANAL');
    this.habilitar(intervaloDias, personalizada);
    this.habilitar(fechaInicio, personalizada);
    this.habilitar(fechaObjetivo, tipo.value === 'MONTO_PARA_FECHA');
  }

  private habilitar(control: AbstractControl, habilitado: boolean): void {
    if (habilitado && control.disabled) {
      control.enable({ emitEvent: false });
    } else if (!habilitado && control.enabled) {
      control.disable({ emitEvent: false });
    }
  }

  private valor() {
    return this.formulario.getRawValue();
  }

  private presupuestoId(): number {
    return this.presupuestoActivo.presupuesto()?.id ?? 0;
  }

  private empezarEnvio(): void {
    this.enviando.set(true);
    this.errorGeneral.set(null);
    this.dialogRef.disableClose = true;
  }

  private terminarEnvio(): void {
    this.enviando.set(false);
    this.dialogRef.disableClose = false;
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.CONFLICTO) {
      this.errorGeneral.set(MENSAJE_CONFLICTO_META);
      return;
    }
    if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
      this.errorGeneral.set(MENSAJE_REGLA_META);
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      if (aplicarErroresDeCampos(this.formulario, problema.errores).length > 0) {
        this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
      }
      return;
    }
    if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.snackBar.open(MENSAJE_META_INEXISTENTE, 'Cerrar', { duration: 6000 });
      this.dialogRef.close({ tipo: 'recargar' });
      return;
    }
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }
}
