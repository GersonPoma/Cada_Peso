import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
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
import { CategoriaResponse } from '../models/categoria-response.model';
import { DatosDialogoCategoria } from '../models/datos-dialogos-categorias.model';
import { CategoriaService } from '../services/categoria.service';
import { LONGITUD_MAXIMA_NOMBRE, MENSAJES_NOMBRE } from './dialogo-grupo.component';

export const LONGITUD_MAXIMA_NOTA = 500;

/** Texto del 409 `CATEGORIA_YA_EXISTE` al crear o editar en un grupo. */
export const MENSAJE_CATEGORIA_REPETIDA = 'Ya hay una categoría con ese nombre en este grupo';

const MENSAJES_NOTA: MensajesDeError = {
  maxlength: `La nota no puede superar los ${LONGITUD_MAXIMA_NOTA} caracteres`,
};

/**
 * Diálogo para crear una categoría en un grupo o editar su nombre y su nota. Hace la petición y
 * solo se cierra si tiene éxito, con la `CategoriaResponse` como resultado. Una nota vacía se
 * envía como `null`.
 */
@Component({
  selector: 'app-dialogo-categoria',
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
  ],
  templateUrl: './dialogo-categoria.component.html',
  styleUrl: './dialogo-categoria.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoCategoriaComponent {
  private readonly datos = inject<DatosDialogoCategoria>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoCategoriaComponent, CategoriaResponse>>(MatDialogRef);
  private readonly categorias = inject(CategoriaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly esCrear = this.datos.modo === 'crear';
  protected readonly maximoNota = LONGITUD_MAXIMA_NOTA;

  protected readonly formulario = new FormGroup({
    nombre: new FormControl(this.datos.modo === 'editar' ? this.datos.categoria.nombre : '', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        sobreTextoRecortado(Validators.maxLength(LONGITUD_MAXIMA_NOMBRE)),
      ],
    }),
    nota: new FormControl(this.datos.modo === 'editar' ? (this.datos.categoria.nota ?? '') : '', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.maxLength(LONGITUD_MAXIMA_NOTA))],
    }),
  });

  /** Caracteres de la nota sin los espacios de los extremos, como los mide el backend. */
  protected readonly largoNota = toSignal(this.formulario.controls.nota.valueChanges, {
    initialValue: this.formulario.controls.nota.value,
  });

  protected readonly enviando = signal(false);

  protected caracteresNota(): number {
    return this.largoNota().trim().length;
  }

  protected mensajeNombre(): string | null {
    return mensajeDeError(this.formulario.controls.nombre.errors, MENSAJES_NOMBRE);
  }

  protected mensajeNota(): string | null {
    return mensajeDeError(this.formulario.controls.nota.errors, MENSAJES_NOTA);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.dialogRef.disableClose = true;

    this.peticion().subscribe({
      next: (categoria) => this.dialogRef.close(categoria),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.dialogRef.disableClose = false;
        this.mostrarError(error);
      },
    });
  }

  private peticion(): Observable<CategoriaResponse> {
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    const nombre = this.formulario.controls.nombre.value.trim();
    const nota = this.formulario.controls.nota.value.trim() || null;
    if (this.datos.modo === 'editar') {
      return this.categorias.editarCategoria(presupuestoId, this.datos.categoria.id, {
        nombre,
        nota,
      });
    }
    return this.categorias.crearCategoria(presupuestoId, {
      grupoId: this.datos.grupoId,
      nombre,
      nota,
    });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió (el diálogo se cierra al navegar).
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.CATEGORIA_YA_EXISTE) {
      const nombre = this.formulario.controls.nombre;
      nombre.setErrors({ ...nombre.errors, [CLAVE_ERROR_SERVIDOR]: MENSAJE_CATEGORIA_REPETIDA });
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
