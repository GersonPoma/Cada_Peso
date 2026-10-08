import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
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
import { BeneficiarioResponse } from '../models/beneficiario-response.model';
import {
  DatosDialogoBeneficiario,
  ResultadoDialogoBeneficiario,
} from '../models/datos-dialogo-beneficiario.model';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import { BeneficiarioService } from '../services/beneficiario.service';

export const MAXIMO_NOMBRE = 100;
export const MENSAJE_NOMBRE_REPETIDO = 'Ya existe un beneficiario con ese nombre';
export const MENSAJE_BENEFICIARIO_INEXISTENTE =
  'El beneficiario o la categoría ya no existe. Actualizamos la lista.';
export const PISTA_RENOMBRAR =
  'Las transacciones antiguas conservan el texto original, pero se mostrarán con el nombre nuevo';

const MENSAJES_NOMBRE: MensajesDeError = {
  required: 'El nombre es obligatorio',
  maxlength: `El nombre no puede superar los ${MAXIMO_NOMBRE} caracteres`,
};

/**
 * Crear o editar un beneficiario: nombre y categoría predeterminada (o `Ninguna`). Las categorías
 * de pago de tarjeta no se ofrecen (el backend las rechaza) y las ocultas solo si ya era la
 * elegida. Hace la petición y solo se cierra si tiene éxito.
 */
@Component({
  selector: 'app-dialogo-beneficiario',
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
  ],
  templateUrl: './dialogo-beneficiario.component.html',
  styleUrl: './dialogo-beneficiario.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoBeneficiarioComponent {
  private readonly datos = inject<DatosDialogoBeneficiario>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoBeneficiarioComponent, ResultadoDialogoBeneficiario>>(MatDialogRef);
  private readonly servicio = inject(BeneficiarioService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  private readonly original: BeneficiarioResponse | null = this.datos.beneficiario;
  protected readonly esCrear = this.original === null;
  protected readonly maximoNombre = MAXIMO_NOMBRE;
  protected readonly pistaRenombrar = PISTA_RENOMBRAR;

  /** Categorías visibles sin las de pago, más la oculta que ya era la predeterminada. */
  protected readonly grupos: GrupoCategoriasLectura[] = (() => {
    const elegida = this.original?.categoriaPredeterminadaId ?? null;
    return this.datos.grupos
      .map((grupo) => ({
        ...grupo,
        categorias: grupo.categorias.filter(
          (c) => c.id === elegida || (!c.esPagoTarjeta && !c.oculta && !grupo.oculto),
        ),
      }))
      .filter((grupo) => grupo.categorias.length > 0);
  })();

  protected readonly formulario = new FormGroup({
    nombre: new FormControl(this.original?.nombre ?? '', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        sobreTextoRecortado(Validators.maxLength(MAXIMO_NOMBRE)),
      ],
    }),
    categoriaId: new FormControl<number | null>(this.original?.categoriaPredeterminadaId ?? null),
  });

  private readonly nombreActual = toSignal(this.formulario.controls.nombre.valueChanges, {
    initialValue: this.formulario.controls.nombre.value,
  });
  protected readonly largoNombre = computed(() => this.nombreActual().trim().length);
  /** Al editar, el nombre recortado cambió. */
  protected readonly renombrando = computed(
    () => this.original !== null && this.nombreActual().trim() !== this.original.nombre,
  );

  protected readonly enviando = signal(false);

  protected mensajeNombre(): string | null {
    return mensajeDeError(this.formulario.controls.nombre.errors, MENSAJES_NOMBRE);
  }

  protected mensajeCategoria(): string | null {
    return mensajeDeError(this.formulario.controls.categoriaId.errors, {});
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.dialogRef.disableClose = true;
    this.peticion().subscribe({
      next: () => this.dialogRef.close({ tipo: 'guardado' }),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.dialogRef.disableClose = false;
        this.mostrarError(error);
      },
    });
  }

  private peticion(): Observable<BeneficiarioResponse> {
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    const valor = this.formulario.getRawValue();
    const solicitud = { nombre: valor.nombre.trim(), categoriaId: valor.categoriaId };
    if (this.original) {
      return this.servicio.actualizar(presupuestoId, this.original.id, solicitud);
    }
    return this.servicio.crear(presupuestoId, solicitud);
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió (el diálogo se cierra al navegar).
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.BENEFICIARIO_YA_EXISTE) {
      const { nombre } = this.formulario.controls;
      nombre.setErrors({ ...nombre.errors, [CLAVE_ERROR_SERVIDOR]: MENSAJE_NOMBRE_REPETIDO });
      nombre.markAsTouched();
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      if (aplicarErroresDeCampos(this.formulario, problema.errores).length > 0) {
        this.avisar(MENSAJE_ERROR_GENERICO);
      }
      return;
    }
    if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.avisar(MENSAJE_BENEFICIARIO_INEXISTENTE);
      this.dialogRef.close({ tipo: 'recargar' });
      return;
    }
    this.avisar(MENSAJE_ERROR_GENERICO);
  }

  private avisar(mensaje: string): void {
    this.snackBar.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
