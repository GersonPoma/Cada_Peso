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
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
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
import { DatosDialogoGrupo } from '../models/datos-dialogos-categorias.model';
import { GrupoCategoriaResponse } from '../models/grupo-categoria-response.model';
import { CategoriaService } from '../services/categoria.service';

export const LONGITUD_MAXIMA_NOMBRE = 100;

export const MENSAJES_NOMBRE: MensajesDeError = {
  required: 'El nombre es obligatorio',
  maxlength: `El nombre no puede superar los ${LONGITUD_MAXIMA_NOMBRE} caracteres`,
};

const MENSAJE_GRUPO_REPETIDO = 'Ya tienes un grupo con ese nombre';

/**
 * Diálogo para crear un grupo de categorías o renombrarlo. Hace la petición y solo se cierra si
 * tiene éxito, con el `GrupoCategoriaResponse` como resultado.
 */
@Component({
  selector: 'app-dialogo-grupo',
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
  ],
  templateUrl: './dialogo-grupo.component.html',
  styleUrl: './dialogo-grupo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoGrupoComponent {
  private readonly datos = inject<DatosDialogoGrupo>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoGrupoComponent, GrupoCategoriaResponse>>(MatDialogRef);
  private readonly categorias = inject(CategoriaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly esCrear = this.datos.modo === 'crear';

  protected readonly formulario = new FormGroup({
    nombre: new FormControl(this.datos.modo === 'renombrar' ? this.datos.grupo.nombre : '', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        sobreTextoRecortado(Validators.maxLength(LONGITUD_MAXIMA_NOMBRE)),
      ],
    }),
  });

  protected readonly enviando = signal(false);

  protected mensajeNombre(): string | null {
    return mensajeDeError(this.formulario.controls.nombre.errors, MENSAJES_NOMBRE);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.dialogRef.disableClose = true;

    this.peticion().subscribe({
      next: (grupo) => this.dialogRef.close(grupo),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.dialogRef.disableClose = false;
        this.mostrarError(error);
      },
    });
  }

  private peticion(): Observable<GrupoCategoriaResponse> {
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    const nombre = this.formulario.controls.nombre.value.trim();
    if (this.datos.modo === 'renombrar') {
      return this.categorias.renombrarGrupo(presupuestoId, this.datos.grupo.id, { nombre });
    }
    return this.categorias.crearGrupo(presupuestoId, { nombre });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió (el diálogo se cierra al navegar).
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.GRUPO_CATEGORIA_YA_EXISTE) {
      const nombre = this.formulario.controls.nombre;
      nombre.setErrors({ ...nombre.errors, [CLAVE_ERROR_SERVIDOR]: MENSAJE_GRUPO_REPETIDO });
      nombre.markAsTouched();
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      if (aplicarErroresDeCampos(this.formulario, problema.errores).length > 0) {
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
