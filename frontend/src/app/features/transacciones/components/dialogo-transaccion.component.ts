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
import { Observable, of, retry, throwError } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { CampoMontoComponent } from '../../../shared/calculadora/campo-monto.component';
import { aFechaNegocio, deFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import {
  CLAVE_ERROR_SERVIDOR,
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { BeneficiarioSugerido } from '../models/beneficiario-sugerido.model';
import {
  DatosDialogoTransaccion,
  ResultadoDialogoTransaccion,
} from '../models/datos-dialogos-transacciones.model';
import { GrupoCategoriasResumen } from '../models/grupo-categorias-resumen.model';
import { SubtransaccionRequest } from '../models/subtransaccion-request.model';
import { TransaccionResponse } from '../models/transaccion-response.model';
import { categoriaRecordada } from '../services/categoria-recordada';
import { TransaccionService } from '../services/transaccion.service';
import { CampoBeneficiarioComponent, MAXIMO_BENEFICIARIO } from './campo-beneficiario.component';
import {
  EditorDivisionComponent,
  FormularioParte,
  crearParte,
  divisionExacta,
} from './editor-division.component';

export const MAXIMO_MEMO = 500;
export const MENSAJE_REGLA_TRANSACCION =
  'No se pudo guardar: la cuenta está cerrada, la transacción está reconciliada o las partes no ' +
  'suman el monto.';
export const MENSAJE_REFERENCIA_INEXISTENTE =
  'Una cuenta o categoría ya no existe. Actualizamos las listas.';
export const MENSAJE_BENEFICIARIO_DUPLICADO = 'Ya existe un beneficiario con ese nombre';
export const PISTA_CATEGORIA_SUGERIDA = 'Sugerida por el beneficiario';

type Tipo = 'salida' | 'entrada';

const MENSAJES_MONTO = { noPositivo: 'El monto debe ser mayor que 0' };
const MENSAJES_TEXTO = (maximo: number, campo: string): MensajesDeError => ({
  maxlength: `${campo} no puede superar los ${maximo} caracteres`,
});

const mayorQueCero = (control: AbstractControl): ValidationErrors | null =>
  control.value !== null && control.value <= 0 ? { noPositivo: true } : null;

/** Dos peticiones crearon a la vez el mismo beneficiario: repetir la petición lo resuelve. */
const esBeneficiarioDuplicado = (error: unknown): boolean =>
  leerProblemaApi(error)?.codigo === CODIGOS_API.BENEFICIARIO_YA_EXISTE;

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
    CampoBeneficiarioComponent,
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

  /** Árbol completo (ocultas incluidas), para el autocompletado del beneficiario. */
  protected readonly arbol = this.datos.grupos;
  private readonly idsCategorias = new Set(
    this.datos.grupos.flatMap((grupo) => grupo.categorias.map((c) => c.id)),
  );

  /** Categoría que puso el beneficiario elegido; `null` si no hay o la persona la cambió. */
  private readonly categoriaSugerida = signal<number | null>(null);
  protected readonly hayCategoriaSugerida = computed(() => this.categoriaSugerida() !== null);
  protected readonly pistaCategoriaSugerida = PISTA_CATEGORIA_SUGERIDA;

  /** Categorías visibles, más las ocultas que la transacción ya usa o sugirió el beneficiario. */
  protected readonly grupos = computed<GrupoCategoriasResumen[]>(() => {
    const usadas = new Set<number>();
    if (this.original?.categoriaId != null) {
      usadas.add(this.original.categoriaId);
    }
    for (const parte of this.original?.subtransacciones ?? []) {
      if (parte.categoriaId !== null) {
        usadas.add(parte.categoriaId);
      }
    }
    const sugerida = this.categoriaSugerida();
    if (sugerida !== null) {
      usadas.add(sugerida);
    }
    return this.datos.grupos
      .map((grupo) => ({
        ...grupo,
        categorias: grupo.categorias.filter(
          (c) => usadas.has(c.id) || (!c.oculta && !grupo.oculto),
        ),
      }))
      .filter((grupo) => grupo.categorias.length > 0);
  });

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
  private readonly memoActual = toSignal(this.formulario.controls.memo.valueChanges, {
    initialValue: this.formulario.controls.memo.value,
  });
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
    // La pista de categoría sugerida desaparece en cuanto la persona elige otra.
    this.formulario.controls.categoriaId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((valor) => {
        if (valor !== this.categoriaSugerida()) {
          this.categoriaSugerida.set(null);
        }
      });
  }

  /** Al elegir una sugerencia, rellena la categoría con la del beneficiario si corresponde. */
  protected alElegirBeneficiario(beneficiario: BeneficiarioSugerido): void {
    const { categoriaId, dividir } = this.formulario.controls;
    const id = categoriaRecordada({
      creando: this.esCrear,
      dividida: dividir.value,
      categoriaPristine: categoriaId.pristine,
      categoriaActual: categoriaId.value,
      sugerida: beneficiario.categoriaPredeterminadaId,
      idsExistentes: this.idsCategorias,
    });
    if (id !== null) {
      // Primero la señal, para que el select ya tenga la opción si está oculta. El control sigue
      // `pristine`: la persona no lo tocó.
      this.categoriaSugerida.set(id);
      categoriaId.setValue(id);
    }
  }

  protected mensaje(campo: 'memo' | 'cuentaId' | 'fecha'): string | null {
    const control = this.formulario.controls[campo];
    const mensajes: Record<typeof campo, MensajesDeError> = {
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
    // Ante `BENEFICIARIO_YA_EXISTE`, una sola repetición de la misma petición.
    const reintentar = (error: unknown) =>
      esBeneficiarioDuplicado(error) ? of(0) : throwError(() => error);
    this.peticion()
      .pipe(retry({ count: 1, delay: reintentar }))
      .subscribe({
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
    if (problema?.codigo === CODIGOS_API.BENEFICIARIO_YA_EXISTE) {
      const { beneficiario } = this.formulario.controls;
      beneficiario.setErrors({
        ...beneficiario.errors,
        [CLAVE_ERROR_SERVIDOR]: MENSAJE_BENEFICIARIO_DUPLICADO,
      });
      beneficiario.markAsTouched();
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
