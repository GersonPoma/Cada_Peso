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
import { MatButton, MatIconButton } from '@angular/material/button';
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
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { Observable, merge } from 'rxjs';
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
import { CuentaResumen } from '../models/cuenta-resumen.model';
import {
  DatosDialogoTransferencia,
  ResultadoDialogoTransaccion,
} from '../models/datos-dialogos-transacciones.model';
import { GrupoCategoriasResumen } from '../models/grupo-categorias-resumen.model';
import { TransferenciaResponse } from '../models/transferencia-response.model';
import {
  ReglaCategoriaTransferencia,
  reglaCategoriaTransferencia,
} from '../services/regla-categoria-transferencia';
import { TransferenciaService } from '../services/transferencia.service';

export const MAXIMO_MEMO_TRANSFERENCIA = 500;
export const MENSAJE_REGLA_TRANSFERENCIA =
  'No se pudo guardar: una de las cuentas está cerrada, una de las transacciones está ' +
  'reconciliada o la categoría no corresponde.';
export const MENSAJE_TRANSFERENCIA_INEXISTENTE =
  'La transferencia, una cuenta o una categoría ya no existe. Actualizamos las listas.';

/** Texto que acompaña a la categoría según la regla (en las ocultas, en lugar del campo). */
export const TEXTOS_REGLA_CATEGORIA: Readonly<Record<ReglaCategoriaTransferencia, string>> = {
  'oculta-presupuesto': 'Mover dinero entre cuentas del presupuesto no afecta a ninguna categoría',
  'oculta-seguimiento': 'Mover dinero entre cuentas de seguimiento no afecta al presupuesto',
  obligatoria: 'Este dinero sale del presupuesto',
  opcional: 'Sin categoría cuenta como ingreso; con categoría, como reembolso de esa categoría',
  pendiente: '',
};

const MENSAJES_MONTO = { noPositivo: 'El monto debe ser mayor que 0' };
const MENSAJES: Readonly<Record<string, MensajesDeError>> = {
  cuentaOrigenId: { required: 'La cuenta origen es obligatoria' },
  cuentaDestinoId: {
    required: 'La cuenta destino es obligatoria',
    mismaCuenta: 'Elige una cuenta distinta del origen',
  },
  fecha: { required: 'La fecha es obligatoria', matDatepickerParse: 'La fecha no es válida' },
  categoriaId: { required: 'La categoría es obligatoria' },
  memo: { maxlength: `El memo no puede superar los ${MAXIMO_MEMO_TRANSFERENCIA} caracteres` },
};

const mayorQueCero = (control: AbstractControl): ValidationErrors | null =>
  control.value !== null && control.value <= 0 ? { noPositivo: true } : null;

/** Origen y destino distintos; el error queda en el destino para mostrarse en su campo. */
const origenDistintoDeDestino: ValidatorFn = (grupo: AbstractControl): ValidationErrors | null => {
  const origen = grupo.get('cuentaOrigenId');
  const destino = grupo.get('cuentaDestinoId');
  const iguales = origen?.value !== null && origen?.value === destino?.value;
  if (destino) {
    const { mismaCuenta: _, ...resto } = destino.errors ?? {};
    const errores = iguales ? { ...resto, mismaCuenta: true } : resto;
    destino.setErrors(Object.keys(errores).length > 0 ? errores : null, { emitEvent: false });
  }
  return iguales ? { mismaCuenta: true } : null;
};

/** Fecha local de hoy a medianoche (nunca a partir de UTC). */
function hoy(): Date {
  const ahora = new Date();
  return new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
}

/** Cuentas agrupadas como en el diálogo de transacción. */
function agrupar(cuentas: CuentaResumen[]): { etiqueta: string; cuentas: CuentaResumen[] }[] {
  return [
    { etiqueta: 'En el presupuesto', cuentas: cuentas.filter((c) => c.enPresupuesto) },
    { etiqueta: 'Seguimiento', cuentas: cuentas.filter((c) => !c.enPresupuesto) },
  ].filter((grupo) => grupo.cuentas.length > 0);
}

/**
 * Crear o editar una transferencia entre dos cuentas. La categoría aparece, es obligatoria u
 * opcional según `enPresupuesto` de las cuentas (`reglaCategoriaTransferencia`). Al editar, pide
 * la transferencia por el id de la pata y las cuentas no se cambian. Hace la petición y solo se
 * cierra si tiene éxito.
 */
@Component({
  selector: 'app-dialogo-transferencia',
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
    MatHint,
    MatIcon,
    MatIconButton,
    MatInput,
    MatLabel,
    MatOptgroup,
    MatOption,
    MatProgressSpinner,
    MatSelect,
    MatSuffix,
    MatTooltip,
    CampoMontoComponent,
  ],
  templateUrl: './dialogo-transferencia.component.html',
  styleUrl: './dialogo-transferencia.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoTransferenciaComponent {
  private readonly datos = inject<DatosDialogoTransferencia>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoTransferenciaComponent, ResultadoDialogoTransaccion>>(MatDialogRef);
  private readonly servicio = inject(TransferenciaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly esCrear = this.datos.transaccionId === null;
  protected readonly maximoMemo = MAXIMO_MEMO_TRANSFERENCIA;
  protected readonly mensajesMonto = MENSAJES_MONTO;
  protected readonly textos = TEXTOS_REGLA_CATEGORIA;

  protected readonly formulario = new FormGroup(
    {
      cuentaOrigenId: new FormControl<number | null>(
        { value: this.esCrear ? this.origenInicial() : null, disabled: !this.esCrear },
        Validators.required,
      ),
      cuentaDestinoId: new FormControl<number | null>(
        { value: null, disabled: !this.esCrear },
        Validators.required,
      ),
      fecha: new FormControl<Date | null>(hoy(), Validators.required),
      monto: new FormControl<number | null>(null, [Validators.required, mayorQueCero]),
      categoriaId: new FormControl<number | null>(null),
      memo: new FormControl('', {
        nonNullable: true,
        validators: [sobreTextoRecortado(Validators.maxLength(MAXIMO_MEMO_TRANSFERENCIA))],
      }),
    },
    { validators: origenDistintoDeDestino },
  );

  /** Al crear, las cuentas abiertas; al editar, las dos de la transferencia (aunque cerradas). */
  private readonly cuentasVisibles = signal<CuentaResumen[]>(
    this.esCrear ? this.datos.cuentas.filter((c) => !c.cerrada) : [],
  );
  private readonly origenId = toSignal(this.formulario.controls.cuentaOrigenId.valueChanges, {
    initialValue: this.formulario.controls.cuentaOrigenId.value,
  });
  private readonly destinoId = toSignal(this.formulario.controls.cuentaDestinoId.valueChanges, {
    initialValue: this.formulario.controls.cuentaDestinoId.value,
  });
  private readonly memoActual = toSignal(this.formulario.controls.memo.valueChanges, {
    initialValue: '',
  });

  /** Cada select excluye la cuenta elegida en el otro. */
  protected readonly opcionesOrigen = computed(() =>
    agrupar(this.cuentasVisibles().filter((c) => c.id !== this.destinoId())),
  );
  protected readonly opcionesDestino = computed(() =>
    agrupar(this.cuentasVisibles().filter((c) => c.id !== this.origenId())),
  );

  protected readonly regla = computed(() =>
    reglaCategoriaTransferencia(this.cuenta(this.origenId()), this.cuenta(this.destinoId())),
  );
  protected readonly muestraCategoria = computed(
    () => this.regla() === 'obligatoria' || this.regla() === 'opcional',
  );

  /** Categoría ya elegida al abrir (la única oculta que se ofrece). */
  private readonly categoriaInicial = signal<number | null>(null);
  protected readonly grupos = computed<GrupoCategoriasResumen[]>(() => {
    const elegida = this.categoriaInicial();
    return this.datos.grupos
      .map((grupo) => ({
        ...grupo,
        categorias: grupo.categorias.filter(
          (c) => c.id === elegida || (!c.oculta && !grupo.oculto),
        ),
      }))
      .filter((grupo) => grupo.categorias.length > 0);
  });

  protected readonly largoMemo = computed(() => this.memoActual().trim().length);
  protected readonly cargando = signal(!this.esCrear);
  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    merge(
      this.formulario.controls.cuentaOrigenId.valueChanges,
      this.formulario.controls.cuentaDestinoId.valueChanges,
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.aplicarRegla());
    this.aplicarRegla();
    if (this.datos.transaccionId !== null) {
      this.cargar(this.datos.transaccionId);
    }
  }

  protected invertir(): void {
    const { cuentaOrigenId, cuentaDestinoId } = this.formulario.getRawValue();
    this.formulario.patchValue({
      cuentaOrigenId: cuentaDestinoId,
      cuentaDestinoId: cuentaOrigenId,
    });
  }

  protected mensaje(campo: keyof typeof MENSAJES): string | null {
    return mensajeDeError(this.formulario.get(campo)?.errors ?? null, MENSAJES[campo]);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando() || this.cargando()) {
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

  private origenInicial(): number | null {
    const id = this.datos.cuentaOrigenId;
    return this.datos.cuentas.some((c) => c.id === id && !c.cerrada) ? id : null;
  }

  private cuenta(id: number | null): CuentaResumen | null {
    return this.cuentasVisibles().find((c) => c.id === id) ?? null;
  }

  /** La categoría es obligatoria, opcional o se vacía según las cuentas elegidas. */
  private aplicarRegla(): void {
    const { categoriaId } = this.formulario.controls;
    const regla = reglaCategoriaTransferencia(
      this.cuenta(this.formulario.controls.cuentaOrigenId.value),
      this.cuenta(this.formulario.controls.cuentaDestinoId.value),
    );
    categoriaId.setValidators(regla === 'obligatoria' ? Validators.required : null);
    if (regla !== 'obligatoria' && regla !== 'opcional') {
      categoriaId.setValue(null, { emitEvent: false });
    }
    categoriaId.updateValueAndValidity();
  }

  private cargar(transaccionId: number): void {
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    this.servicio.obtener(presupuestoId, transaccionId).subscribe({
      next: (transferencia) => this.rellenar(transferencia),
      error: (error: unknown) => {
        if (leerProblemaApi(error)?.status === 401) {
          return;
        }
        if (leerProblemaApi(error)?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
          this.avisar(MENSAJE_TRANSFERENCIA_INEXISTENTE);
          this.dialogRef.close({ tipo: 'recargar' });
          return;
        }
        this.avisar(MENSAJE_ERROR_GENERICO);
        this.dialogRef.close();
      },
    });
  }

  private rellenar({ salida, entrada }: TransferenciaResponse): void {
    const cuentas = [salida.cuentaId, entrada.cuentaId]
      .map((id) => this.datos.cuentas.find((c) => c.id === id))
      .filter((c): c is CuentaResumen => c !== undefined);
    this.cuentasVisibles.set(cuentas);
    const categoria = salida.categoriaId ?? entrada.categoriaId;
    this.categoriaInicial.set(categoria);
    this.formulario.patchValue({
      cuentaOrigenId: salida.cuentaId,
      cuentaDestinoId: entrada.cuentaId,
      fecha: deFechaNegocio(salida.fecha),
      monto: Math.abs(entrada.monto),
      memo: salida.memo ?? '',
    });
    // Después de las cuentas: la regla ya decidió si la categoría aplica.
    if (this.muestraCategoria()) {
      this.formulario.controls.categoriaId.setValue(categoria);
    }
    this.cargando.set(false);
  }

  private peticion(): Observable<unknown> {
    const valor = this.formulario.getRawValue();
    const comun = {
      fecha: aFechaNegocio(valor.fecha) as string,
      monto: valor.monto ?? 0,
      memo: valor.memo.trim() || null,
      // Sin la clave cuando la categoría no aplica.
      ...(this.muestraCategoria() ? { categoriaId: valor.categoriaId } : {}),
    };
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    if (this.datos.transaccionId !== null) {
      return this.servicio.actualizar(presupuestoId, this.datos.transaccionId, comun);
    }
    return this.servicio.crear(presupuestoId, {
      cuentaOrigenId: valor.cuentaOrigenId as number,
      cuentaDestinoId: valor.cuentaDestinoId as number,
      ...comun,
    });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
      this.errorGeneral.set(MENSAJE_REGLA_TRANSFERENCIA);
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      if (aplicarErroresDeCampos(this.formulario, problema.errores).length > 0) {
        this.avisar(MENSAJE_ERROR_GENERICO);
      }
      return;
    }
    if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.avisar(MENSAJE_TRANSFERENCIA_INEXISTENTE);
      this.dialogRef.close({ tipo: 'recargar' });
      return;
    }
    this.avisar(MENSAJE_ERROR_GENERICO);
  }

  private avisar(mensaje: string): void {
    this.snackBar.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
