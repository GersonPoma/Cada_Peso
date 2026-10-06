import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatCheckbox } from '@angular/material/checkbox';
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
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { CampoMontoComponent } from '../../../shared/calculadora/campo-monto.component';
import { aFechaNegocio, deFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import {
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import {
  DatosDialogoTransaccion,
  ResultadoDialogoTransaccion,
} from '../models/datos-dialogos-transacciones.model';
import { GrupoCategoriasResumen } from '../models/grupo-categorias-resumen.model';
import { SubtransaccionRequest } from '../models/subtransaccion-request.model';
import { TransaccionResponse } from '../models/transaccion-response.model';
import { TransaccionService } from '../services/transaccion.service';
import {
  EditorDivisionComponent,
  FormularioParte,
  crearParte,
  divisionExacta,
} from './editor-division.component';

export const MAXIMO_BENEFICIARIO = 100;
export const MAXIMO_MEMO = 500;
export const MENSAJE_REGLA_TRANSACCION =
  'No se pudo guardar: la cuenta está cerrada, la transacción está reconciliada o las partes no ' +
  'suman el monto.';
export const MENSAJE_REFERENCIA_INEXISTENTE =
  'Una cuenta o categoría ya no existe. Actualizamos las listas.';

type Tipo = 'salida' | 'entrada';

const MENSAJES_MONTO = { noPositivo: 'El monto debe ser mayor que 0' };
const MENSAJES_TEXTO = (maximo: number, campo: string): MensajesDeError => ({
  maxlength: `${campo} no puede superar los ${maximo} caracteres`,
});

const mayorQueCero = (control: AbstractControl): ValidationErrors | null =>
  control.value !== null && control.value <= 0 ? { noPositivo: true } : null;

/** Fecha local de hoy a medianoche (nunca a partir de UTC). */
function hoy(): Date {
  const ahora = new Date();
  return new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
}

/**
 * Crear o editar una transacción. El monto se escribe en positivo y el tipo (Salida o Entrada)
 * le da el signo al enviar. Con "Dividir", la categoría se reemplaza por partes que deben sumar
 * exactamente el monto. Hace la petición y solo se cierra si tiene éxito.
 */
@Component({
  selector: 'app-dialogo-transaccion',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatButtonToggle,
    MatButtonToggleGroup,
    MatCheckbox,
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
    MatSlideToggle,
    MatSuffix,
    CampoMontoComponent,
    EditorDivisionComponent,
  ],
  templateUrl: './dialogo-transaccion.component.html',
  styleUrl: './dialogo-transaccion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoTransaccionComponent {
  private readonly datos = inject<DatosDialogoTransaccion>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoTransaccionComponent, ResultadoDialogoTransaccion>>(MatDialogRef);
  private readonly servicio = inject(TransaccionService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly original: TransaccionResponse | null = this.datos.transaccion;
  protected readonly esCrear = this.original === null;
  protected readonly moneda = this.presupuestoActivo.presupuesto()?.moneda ?? 'USD';
  protected readonly maximoBeneficiario = MAXIMO_BENEFICIARIO;
  protected readonly maximoMemo = MAXIMO_MEMO;
  protected readonly mensajesMonto = MENSAJES_MONTO;

  /** Al crear, solo las cuentas abiertas; al editar, la de la transacción (no se cambia). */
  protected readonly gruposCuentas = (() => {
    const cuentas = this.esCrear
      ? this.datos.cuentas.filter((c) => !c.cerrada)
      : this.datos.cuentas.filter((c) => c.id === this.original?.cuentaId);
    return [
      { etiqueta: 'En el presupuesto', cuentas: cuentas.filter((c) => c.enPresupuesto) },
      { etiqueta: 'Seguimiento', cuentas: cuentas.filter((c) => !c.enPresupuesto) },
    ].filter((grupo) => grupo.cuentas.length > 0);
  })();

  /** Categorías visibles, más las ocultas que la transacción ya usa. */
  protected readonly grupos: GrupoCategoriasResumen[] = (() => {
    const usadas = new Set<number>();
    if (this.original?.categoriaId != null) {
      usadas.add(this.original.categoriaId);
    }
    for (const parte of this.original?.subtransacciones ?? []) {
      if (parte.categoriaId !== null) {
        usadas.add(parte.categoriaId);
      }
    }
    return this.datos.grupos
      .map((grupo) => ({
        ...grupo,
        categorias: grupo.categorias.filter(
          (c) => usadas.has(c.id) || (!c.oculta && !grupo.oculto),
        ),
      }))
      .filter((grupo) => grupo.categorias.length > 0);
  })();

  private readonly signo = this.original && this.original.monto > 0 ? 1 : -1;

  /** Monto en positivo, en milésimas; lo usa también la validación de las partes. */
  private readonly controlMonto = new FormControl<number | null>(
    this.original ? Math.abs(this.original.monto) : null,
    [Validators.required, mayorQueCero],
  );

  protected readonly partes: FormArray<FormularioParte> = new FormArray<FormularioParte>(
    (this.original?.subtransacciones ?? []).map((parte) =>
      crearParte({
        categoriaId: parte.categoriaId,
        monto: parte.monto * this.signo,
        memo: parte.memo,
      }),
    ),
    divisionExacta(() => this.controlMonto.value),
  );

  protected readonly formulario = new FormGroup({
    cuentaId: new FormControl<number | null>(
      { value: this.original?.cuentaId ?? null, disabled: !this.esCrear },
      Validators.required,
    ),
    fecha: new FormControl<Date | null>(
      this.original ? deFechaNegocio(this.original.fecha) : hoy(),
      Validators.required,
    ),
    beneficiario: new FormControl(this.original?.beneficiario ?? '', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.maxLength(MAXIMO_BENEFICIARIO))],
    }),
    tipo: new FormControl<Tipo>(this.original && this.original.monto > 0 ? 'entrada' : 'salida', {
      nonNullable: true,
    }),
    monto: this.controlMonto,
    categoriaId: new FormControl<number | null>(this.original?.categoriaId ?? null),
    dividir: new FormControl((this.original?.subtransacciones.length ?? 0) > 0, {
      nonNullable: true,
    }),
    partes: this.partes,
    memo: new FormControl(this.original?.memo ?? '', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.maxLength(MAXIMO_MEMO))],
    }),
    aprobada: new FormControl(true, { nonNullable: true }),
  });

  protected readonly dividida = toSignal(this.formulario.controls.dividir.valueChanges, {
    initialValue: this.formulario.controls.dividir.value,
  });
  protected readonly montoActual = toSignal(this.formulario.controls.monto.valueChanges, {
    initialValue: this.formulario.controls.monto.value,
  });
  private readonly beneficiarioActual = toSignal(
    this.formulario.controls.beneficiario.valueChanges,
    { initialValue: this.formulario.controls.beneficiario.value },
  );
  private readonly memoActual = toSignal(this.formulario.controls.memo.valueChanges, {
    initialValue: this.formulario.controls.memo.value,
  });
  protected readonly largoBeneficiario = computed(() => this.beneficiarioActual().trim().length);
  protected readonly largoMemo = computed(() => this.memoActual().trim().length);

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    this.aplicarDivision(this.formulario.controls.dividir.value);
    this.formulario.controls.dividir.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((dividir) => this.aplicarDivision(dividir));
    // La suma de las partes se compara con el monto: se revalida al cambiarlo.
    this.formulario.controls.monto.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.partes.updateValueAndValidity());
  }

  protected mensaje(campo: 'beneficiario' | 'memo' | 'cuentaId' | 'fecha'): string | null {
    const control = this.formulario.controls[campo];
    const mensajes: Record<typeof campo, MensajesDeError> = {
      beneficiario: MENSAJES_TEXTO(MAXIMO_BENEFICIARIO, 'El beneficiario'),
      memo: MENSAJES_TEXTO(MAXIMO_MEMO, 'El memo'),
      cuentaId: { required: 'La cuenta es obligatoria' },
      fecha: { required: 'La fecha es obligatoria', matDatepickerParse: 'La fecha no es válida' },
    };
    return mensajeDeError(control.errors, mensajes[campo]);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set(null);
    this.dialogRef.disableClose = true;
    this.peticion().subscribe({
      next: () => this.dialogRef.close({ tipo: 'guardada' }),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.dialogRef.disableClose = false;
        this.mostrarError(error);
      },
    });
  }

  /** Con "Dividir", las partes reemplazan a la categoría (y al revés). */
  private aplicarDivision(dividir: boolean): void {
    const { categoriaId } = this.formulario.controls;
    if (dividir) {
      while (this.partes.length < 2) {
        this.partes.push(crearParte());
      }
      this.partes.enable({ emitEvent: false });
      categoriaId.disable({ emitEvent: false });
    } else {
      this.partes.disable({ emitEvent: false });
      categoriaId.enable({ emitEvent: false });
    }
    this.partes.updateValueAndValidity();
  }

  private peticion(): Observable<TransaccionResponse> {
    const valor = this.formulario.getRawValue();
    const signo = valor.tipo === 'salida' ? -1 : 1;
    const dividir = valor.dividir;
    const subtransacciones: SubtransaccionRequest[] = dividir
      ? this.partes.getRawValue().map((parte) => ({
          categoriaId: parte.categoriaId,
          monto: (parte.monto ?? 0) * signo,
          memo: parte.memo.trim() || null,
        }))
      : [];
    const comun = {
      fecha: aFechaNegocio(valor.fecha) as string,
      monto: (valor.monto ?? 0) * signo,
      categoriaId: dividir ? null : valor.categoriaId,
      beneficiario: valor.beneficiario.trim() || null,
      memo: valor.memo.trim() || null,
      subtransacciones,
    };
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    if (this.original) {
      return this.servicio.actualizar(presupuestoId, this.original.id, comun);
    }
    return this.servicio.crear(presupuestoId, {
      cuentaId: valor.cuentaId as number,
      aprobada: valor.aprobada,
      ...comun,
    });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
      this.errorGeneral.set(MENSAJE_REGLA_TRANSACCION);
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      if (aplicarErroresDeCampos(this.formulario, problema.errores).length > 0) {
        this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
      }
      return;
    }
    if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.snackBar.open(MENSAJE_REFERENCIA_INEXISTENTE, 'Cerrar', { duration: 6000 });
      this.dialogRef.close({ tipo: 'recargar' });
      return;
    }
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }
}
