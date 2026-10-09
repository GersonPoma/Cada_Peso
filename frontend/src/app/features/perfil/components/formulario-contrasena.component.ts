import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { SesionService } from '../../../core/sesion/sesion.service';
import { CampoContrasenaComponent } from '../../../shared/campo-contrasena/campo-contrasena.component';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import {
  MensajesDeError,
  aplicarErroresDeCampos,
} from '../../../shared/formulario/errores-formulario';
import { validadoresContrasenaNueva } from '../../../shared/validacion/contrasena-nueva.validator';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { PerfilService } from '../services/perfil.service';
import {
  coincideCon,
  distintaDe,
  revalidarAlCambiar,
} from '../services/cambio-contrasena.validator';

const MENSAJE_ACTUAL_INCORRECTA = 'La contraseña actual es incorrecta';
const MENSAJE_CONTRASENA_CAMBIADA = 'Contraseña cambiada, inicia sesión de nuevo';

/**
 * Cambio de contraseña: actual, nueva y confirmación (la confirmación no se envía). Las
 * contraseñas viven solo en este formulario: se vacían al terminar bien y la actual también al
 * fallar. Tras el `204` cierra la sesión local; el token anterior sigue válido en el servidor
 * hasta expirar. Nunca escribe en la consola.
 */
@Component({
  selector: 'app-formulario-contrasena',
  imports: [ReactiveFormsModule, MatButton, CampoContrasenaComponent],
  templateUrl: './formulario-contrasena.component.html',
  styleUrl: './formulario-contrasena.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioContrasenaComponent {
  /** Correo de la cuenta: va en un campo de usuario oculto para los gestores de contraseñas. */
  readonly email = input.required<string>();

  private readonly perfil = inject(PerfilService);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly formulario = new FormGroup({
    contrasenaActual: new FormControl('', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.required)],
    }),
    contrasenaNueva: new FormControl('', {
      nonNullable: true,
      validators: [...validadoresContrasenaNueva(), distintaDe('contrasenaActual')],
    }),
    confirmacion: new FormControl('', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.required), coincideCon('contrasenaNueva')],
    }),
  });
  protected readonly enviando = signal(false);

  protected readonly mensajesNueva: MensajesDeError = {
    igualALaActual: 'La contraseña nueva debe ser distinta de la actual',
  };
  protected readonly mensajesConfirmacion: MensajesDeError = {
    noCoincide: 'Las contraseñas no coinciden',
  };

  constructor() {
    const { contrasenaActual, contrasenaNueva, confirmacion } = this.formulario.controls;
    const enlaces = [
      revalidarAlCambiar(contrasenaActual, contrasenaNueva),
      revalidarAlCambiar(contrasenaNueva, confirmacion),
    ];
    inject(DestroyRef).onDestroy(() => enlaces.forEach((enlace) => enlace.unsubscribe()));
  }

  protected deshabilitado(): boolean {
    return this.formulario.invalid || this.enviando();
  }

  protected enviar(): void {
    if (this.deshabilitado()) {
      return;
    }
    this.enviando.set(true);
    const { contrasenaActual, contrasenaNueva } = this.formulario.getRawValue();

    this.perfil.cambiarContrasena({ contrasenaActual, contrasenaNueva }).subscribe({
      next: () => this.alCambiar(),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.mostrarError(error);
      },
    });
  }

  /**
   * Éxito: vacía el formulario, cierra la sesión local (el mismo `SesionService.cerrar()` del botón
   * `Cerrar sesión`), lleva a `/login` y, solo si llegó, avisa. El aviso es del `MatSnackBar` de la
   * raíz, que no depende de este componente.
   */
  private alCambiar(): void {
    this.formulario.reset();
    this.enviando.set(false);
    this.sesion.cerrar();
    void this.router.navigateByUrl('/login').then((llego) => {
      if (llego) {
        this.snackBar.open(MENSAJE_CONTRASENA_CAMBIADA, 'Cerrar', { duration: 8000 });
      }
    });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
      this.rechazarActual(MENSAJE_ACTUAL_INCORRECTA);
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS && problema.errores) {
      if ('contrasenaActual' in problema.errores) {
        this.formulario.controls.contrasenaActual.reset('');
      }
      const sinCampo = aplicarErroresDeCampos(this.formulario, problema.errores);
      if (sinCampo.length === 0) {
        this.enfocarPrimerError();
        return;
      }
    }
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }

  /** Vacía la contraseña actual y le pone el mensaje; nueva y confirmación se conservan. */
  private rechazarActual(mensaje: string): void {
    const actual = this.formulario.controls.contrasenaActual;
    actual.reset('');
    actual.setErrors({ servidor: mensaje });
    actual.markAsTouched();
    this.enfocarPrimerError();
  }

  /** Pone el foco en el primer campo con error cuando ya se pintó el estado inválido. */
  private enfocarPrimerError(): void {
    afterNextRender(
      () => this.anfitrion.nativeElement.querySelector<HTMLElement>('input.ng-invalid')?.focus(),
      { injector: this.injector },
    );
  }
}
