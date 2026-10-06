import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import {
  CLAVE_ERROR_SERVIDOR,
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { DatosDialogoPresupuesto } from '../models/datos-dialogo-presupuesto.model';
import { PresupuestoResponse } from '../models/presupuesto-response.model';
import { PresupuestoService } from '../services/presupuesto.service';

const LONGITUD_MAXIMA_NOMBRE = 100;

const MENSAJE_NOMBRE_REPETIDO = 'Ya tienes un presupuesto con ese nombre';

const MENSAJES_NOMBRE: MensajesDeError = {
  required: 'El nombre es obligatorio',
  maxlength: `El nombre no puede superar los ${LONGITUD_MAXIMA_NOMBRE} caracteres`,
};

/** Valor de la opción "Moneda de mi perfil": la petición se envía sin `moneda`. */
export const MONEDA_DEL_PERFIL = '';

/** Monedas que se ofrecen al crear un presupuesto, además de la del perfil. */
export const MONEDAS = ['BOB', 'USD', 'EUR', 'ARS', 'BRL', 'CLP', 'PEN'] as const;

/**
 * Diálogo para crear un presupuesto (nombre y moneda) o renombrarlo (solo nombre). Hace la
 * petición él mismo y solo se cierra si tiene éxito, con el `PresupuestoResponse` como resultado,
 * para mostrar los errores sin perder lo escrito.
 */
@Component({
  selector: 'app-dialogo-presupuesto',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatError,
    MatFormField,
    MatInput,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './dialogo-presupuesto.component.html',
  styleUrl: './dialogo-presupuesto.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoPresupuestoComponent {
  private readonly datos = inject<DatosDialogoPresupuesto>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoPresupuestoComponent, PresupuestoResponse>>(MatDialogRef);
  private readonly presupuestos = inject(PresupuestoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly monedaDelPerfil = MONEDA_DEL_PERFIL;
  protected readonly monedas = MONEDAS;
  protected readonly esCrear = this.datos.modo === 'crear';

  protected readonly formulario = new FormGroup({
    nombre: new FormControl(this.datos.modo === 'renombrar' ? this.datos.presupuesto.nombre : '', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        sobreTextoRecortado(Validators.maxLength(LONGITUD_MAXIMA_NOMBRE)),
      ],
    }),
    moneda: new FormControl<string>(MONEDA_DEL_PERFIL, { nonNullable: true }),
  });

  protected readonly enviando = signal(false);

  protected mensajeNombre(): string | null {
    return mensajeDeError(this.formulario.controls.nombre.errors, MENSAJES_NOMBRE);
  }

  protected mensajeMoneda(): string | null {
    return mensajeDeError(this.formulario.controls.moneda.errors, {});
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.dialogRef.disableClose = true;

    this.peticion().subscribe({
      next: (presupuesto) => this.dialogRef.close(presupuesto),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.dialogRef.disableClose = false;
        this.mostrarError(error);
      },
    });
  }

  private peticion(): Observable<PresupuestoResponse> {
    const { nombre, moneda } = this.formulario.getRawValue();
    const nombreRecortado = nombre.trim();
    if (this.datos.modo === 'renombrar') {
      return this.presupuestos.renombrar(this.datos.presupuesto.id, { nombre: nombreRecortado });
    }
    return this.presupuestos.crear(
      moneda === MONEDA_DEL_PERFIL
        ? { nombre: nombreRecortado }
        : { nombre: nombreRecortado, moneda },
    );
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió (el diálogo se cierra al navegar).
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.PRESUPUESTO_YA_EXISTE) {
      const nombre = this.formulario.controls.nombre;
      nombre.setErrors({ ...nombre.errors, [CLAVE_ERROR_SERVIDOR]: MENSAJE_NOMBRE_REPETIDO });
      nombre.markAsTouched();
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
