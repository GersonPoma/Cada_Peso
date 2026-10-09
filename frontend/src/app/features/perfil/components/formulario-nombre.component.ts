import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { startWith } from 'rxjs';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import {
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import {
  MENSAJES_NOMBRE_PERSONA,
  validadoresNombrePersona,
} from '../../../shared/validacion/nombre-persona.validator';
import { PerfilResponse } from '../models/perfil-response.model';
import { PerfilService } from '../services/perfil.service';

const LIMITE_NOMBRE = 100;
const MENSAJE_NOMBRE_ACTUALIZADO = 'Nombre actualizado';

/**
 * Cambio de nombre. Valida con las reglas del registro, muestra el largo del nombre ya recortado
 * y solo permite guardar si es válido, distinto del actual y no hay un envío en curso.
 */
@Component({
  selector: 'app-formulario-nombre',
  imports: [ReactiveFormsModule, MatButton, MatError, MatFormField, MatInput, MatLabel],
  templateUrl: './formulario-nombre.component.html',
  styleUrl: './formulario-nombre.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioNombreComponent {
  /** Nombre guardado; al cambiar (tras guardar), el campo se alinea con él. */
  readonly nombreActual = input.required<string>();
  /** Perfil devuelto por el servidor tras guardar el nombre. */
  readonly guardado = output<PerfilResponse>();

  private readonly perfil = inject(PerfilService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly limite = LIMITE_NOMBRE;
  protected readonly formulario = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: validadoresNombrePersona() }),
  });
  protected readonly enviando = signal(false);

  private readonly valor = toSignal(
    this.formulario.controls.nombre.valueChanges.pipe(startWith('')),
    { initialValue: '' },
  );
  private readonly recortado = computed(() => this.valor().trim());
  protected readonly largo = computed(() => this.recortado().length);
  private readonly sinCambios = computed(() => this.recortado() === this.nombreActual().trim());

  constructor() {
    effect(() => {
      const nombre = this.nombreActual();
      untracked(() => this.formulario.controls.nombre.setValue(nombre));
    });
  }

  protected mensaje(): string | null {
    return mensajeDeError(this.formulario.controls.nombre.errors, MENSAJES_NOMBRE_PERSONA);
  }

  protected deshabilitado(): boolean {
    return this.formulario.invalid || this.sinCambios() || this.enviando();
  }

  protected guardar(): void {
    if (this.deshabilitado()) {
      return;
    }
    this.enviando.set(true);

    this.perfil.cambiarNombre({ nombre: this.recortado() }).subscribe({
      next: (actualizado) => {
        this.enviando.set(false);
        this.snackBar.open(MENSAJE_NOMBRE_ACTUALIZADO, 'Cerrar', { duration: 4000 });
        this.guardado.emit(actualizado);
      },
      error: (error: unknown) => {
        this.enviando.set(false);
        this.mostrarError(error);
      },
    });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      const sinCampo = aplicarErroresDeCampos(this.formulario, problema.errores);
      if (sinCampo.length === 0) {
        this.enfocarPrimerError();
        return;
      }
    }
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }

  /** Pone el foco en el primer campo con error cuando ya se pintó el estado inválido. */
  private enfocarPrimerError(): void {
    afterNextRender(
      () => this.anfitrion.nativeElement.querySelector<HTMLElement>('input.ng-invalid')?.focus(),
      { injector: this.injector },
    );
  }
}
