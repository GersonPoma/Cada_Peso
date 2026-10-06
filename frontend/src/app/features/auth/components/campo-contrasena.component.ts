import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatIconButton } from '@angular/material/button';
import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { switchMap } from 'rxjs';
import { MensajesDeError, mensajeDeError } from '../../../shared/formulario/errores-formulario';

/**
 * Campo de contraseña con botón para mostrarla u ocultarla. Recibe el control por entrada y no
 * modifica su valor. Los mensajes dependen de los validadores que tenga el control: en el login
 * solo `required`, así que no se muestra ninguna regla de longitud.
 */
@Component({
  selector: 'app-campo-contrasena',
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatSuffix,
    MatIconButton,
    MatIcon,
  ],
  templateUrl: './campo-contrasena.component.html',
  styleUrl: './campo-contrasena.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampoContrasenaComponent {
  readonly control = input.required<FormControl<string>>();
  readonly etiqueta = input('Contraseña');
  readonly autocompletar = input<'current-password' | 'new-password'>('current-password');

  protected readonly visible = signal(false);

  /**
   * Cambia con cada evento del control (valor, estado, tocado). Con `OnPush`, es lo que refresca
   * el campo cuando el padre pone un error a mano, por ejemplo el que llega del backend.
   */
  private readonly eventosDelControl = toSignal(
    toObservable(this.control).pipe(switchMap((control) => control.events)),
  );

  protected readonly mensaje = computed(() => {
    this.eventosDelControl();
    return mensajeDeError(this.control().errors, MENSAJES);
  });

  protected alternar(): void {
    this.visible.update((visible) => !visible);
  }
}

const MENSAJES: MensajesDeError = {
  required: 'La contraseña es obligatoria',
  minlength: (error) =>
    `La contraseña debe tener al menos ${(error as { requiredLength: number }).requiredLength} caracteres`,
  maxBytesUtf8: (error) =>
    `La contraseña no puede ocupar más de ${(error as { maximo: number }).maximo} bytes ` +
    '(la ñ y las vocales con tilde ocupan 2)',
};
