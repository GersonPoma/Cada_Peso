import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { MatCheckbox } from '@angular/material/checkbox';
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
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { aMilliunits, deMilliunits, leerMonto } from '../../../shared/formato/milliunits';
import {
  CLAVE_ERROR_SERVIDOR,
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { montoValido } from '../../../shared/validacion/monto.validator';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { CuentaResponse } from '../models/cuenta-response.model';
import { DatosDialogoCuenta } from '../models/datos-dialogo-cuenta.model';
import {
  ETIQUETAS_TIPO_CUENTA,
  TIPOS_CON_SALDO_NEGATIVO,
  TIPOS_CUENTA,
  TipoCuenta,
} from '../models/tipo-cuenta.model';
import { CuentaService } from '../services/cuenta.service';

const LONGITUD_MAXIMA_NOMBRE = 100;

export const MENSAJE_SALDO_NEGATIVO =
  'El saldo inicial negativo solo se permite en tarjetas de crédito y préstamos';

const MENSAJE_NOMBRE_REPETIDO = 'Ya tienes una cuenta con ese nombre';

const MENSAJES_NOMBRE: MensajesDeError = {
  required: 'El nombre es obligatorio',
  maxlength: `El nombre no puede superar los ${LONGITUD_MAXIMA_NOMBRE} caracteres`,
};

const MENSAJES_TIPO: MensajesDeError = {
  required: 'El tipo es obligatorio',
  tipoNoAdmiteSaldoNegativo:
    'Esta cuenta tiene saldo inicial negativo: solo puede ser tarjeta de crédito o préstamo',
};

const MENSAJES_SALDO: MensajesDeError = {
  montoFormato: 'Escribe un monto válido',
  montoRango: 'Escribe un monto válido',
  montoDecimales: 'Usa como máximo 3 decimales',
  saldoNegativoNoPermitido: MENSAJE_SALDO_NEGATIVO,
};

const admiteSaldoNegativo = (tipo: TipoCuenta | null): boolean =>
  tipo !== null && TIPOS_CON_SALDO_NEGATIVO.includes(tipo);

/** Saldo inicial negativo solo si el tipo elegido (control hermano `tipo`) lo admite. */
const saldoNegativoPermitido: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const lectura = leerMonto(String(control.value ?? ''));
  if (lectura.estado !== 'valido' || lectura.milliunits >= 0) {
    return null;
  }
  const tipo = control.parent?.get('tipo')?.value as TipoCuenta | null | undefined;
  return admiteSaldoNegativo(tipo ?? null) ? null : { saldoNegativoNoPermitido: true };
};

/** Al editar: con saldo inicial negativo, el tipo tiene que seguir admitiéndolo. */
function tipoAdmiteSaldoExistente(saldoInicial: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const tipo = control.value as TipoCuenta | null;
    return saldoInicial < 0 && tipo !== null && !admiteSaldoNegativo(tipo)
      ? { tipoNoAdmiteSaldoNegativo: true }
      : null;
  };
}

/**
 * Diálogo para crear una cuenta (nombre, tipo, si está en el presupuesto y saldo inicial) o
 * editarla (solo nombre y tipo). Replica las reglas del backend, hace la petición y solo se cierra
 * si tiene éxito, con la `CuentaResponse` como resultado.
 */
@Component({
  selector: 'app-dialogo-cuenta',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatCheckbox,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatError,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './dialogo-cuenta.component.html',
  styleUrl: './dialogo-cuenta.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoCuentaComponent {
  private readonly datos = inject<DatosDialogoCuenta>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoCuentaComponent, CuentaResponse>>(MatDialogRef);
  private readonly cuentas = inject(CuentaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly esCrear = this.datos.modo === 'crear';
  protected readonly tipos = TIPOS_CUENTA;
  protected readonly etiquetas = ETIQUETAS_TIPO_CUENTA;
  /** Ejemplo de monto en el formato de la región, para la ayuda del campo. */
  protected readonly ejemploMonto = deMilliunits(1234500);

  protected readonly nombre = new FormControl(
    this.datos.modo === 'editar' ? this.datos.cuenta.nombre : '',
    {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        sobreTextoRecortado(Validators.maxLength(LONGITUD_MAXIMA_NOMBRE)),
      ],
    },
  );
  protected readonly tipo = new FormControl<TipoCuenta | null>(
    this.datos.modo === 'editar' ? this.datos.cuenta.tipo : null,
    this.datos.modo === 'editar'
      ? [Validators.required, tipoAdmiteSaldoExistente(this.datos.cuenta.saldoInicial)]
      : [Validators.required],
  );
  protected readonly enPresupuesto = new FormControl(true, { nonNullable: true });
  protected readonly saldoInicial = new FormControl(deMilliunits(0), {
    nonNullable: true,
    validators: [montoValido(), saldoNegativoPermitido],
  });

  // Al editar, `enPresupuesto` y `saldoInicial` no forman parte del formulario: no se envían.
  protected readonly formulario = new FormGroup<{
    nombre: FormControl<string>;
    tipo: FormControl<TipoCuenta | null>;
    enPresupuesto?: FormControl<boolean>;
    saldoInicial?: FormControl<string>;
  }>(
    this.esCrear
      ? {
          nombre: this.nombre,
          tipo: this.tipo,
          enPresupuesto: this.enPresupuesto,
          saldoInicial: this.saldoInicial,
        }
      : { nombre: this.nombre, tipo: this.tipo },
  );

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    // La regla del saldo negativo depende del tipo: se reevalúa cada vez que cambia.
    this.tipo.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.saldoInicial.updateValueAndValidity());
  }

  protected mensajeNombre(): string | null {
    return mensajeDeError(this.nombre.errors, MENSAJES_NOMBRE);
  }

  protected mensajeTipo(): string | null {
    return mensajeDeError(this.tipo.errors, MENSAJES_TIPO);
  }

  protected mensajeSaldo(): string | null {
    return mensajeDeError(this.saldoInicial.errors, MENSAJES_SALDO);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set(null);
    this.dialogRef.disableClose = true;

    this.peticion().subscribe({
      next: (cuenta) => this.dialogRef.close(cuenta),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.dialogRef.disableClose = false;
        this.mostrarError(error);
      },
    });
  }

  private peticion(): Observable<CuentaResponse> {
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    const nombre = this.nombre.value.trim();
    const tipo = this.tipo.value as TipoCuenta;
    if (this.datos.modo === 'editar') {
      return this.cuentas.actualizar(presupuestoId, this.datos.cuenta.id, { nombre, tipo });
    }
    return this.cuentas.crear(presupuestoId, {
      nombre,
      tipo,
      enPresupuesto: this.enPresupuesto.value,
      saldoInicial: aMilliunits(this.saldoInicial.value) ?? 0,
    });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió (el diálogo se cierra al navegar).
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.CUENTA_YA_EXISTE) {
      this.nombre.setErrors({
        ...this.nombre.errors,
        [CLAVE_ERROR_SERVIDOR]: MENSAJE_NOMBRE_REPETIDO,
      });
      this.nombre.markAsTouched();
      return;
    }
    if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
      this.errorGeneral.set(MENSAJE_SALDO_NEGATIVO);
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      const sinCampo = aplicarErroresDeCampos(this.formulario, problema.errores);
      if (sinCampo.length > 0) {
        this.avisarErrorGenerico();
      }
      return;
    }
    this.avisarErrorGenerico();
  }

  private avisarErrorGenerico(): void {
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }
}
