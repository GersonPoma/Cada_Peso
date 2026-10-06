import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { CabeceraComponent } from '../../../shared/cabecera/cabecera.component';
import {
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { CampoContrasenaComponent } from '../components/campo-contrasena.component';
import { AuthService } from '../services/auth.service';

const MENSAJE_CREDENCIALES_INCORRECTAS = 'Email o contraseña incorrectos';

const MENSAJES_EMAIL: MensajesDeError = {
  required: 'El email es obligatorio',
};

/** Inicio de sesión: email y contraseña obligatorios, sin reglas de formato ni de longitud. */
@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatError,
    MatFormField,
    MatInput,
    MatLabel,
    CabeceraComponent,
    CampoContrasenaComponent,
  ],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly formulario = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.required)],
    }),
    // La contraseña nunca se recorta: solo se rechaza un valor vacío o de solo espacios.
    contrasena: new FormControl('', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.required)],
    }),
  });

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  protected mensajeEmail(): string | null {
    return mensajeDeError(this.formulario.controls.email.errors, MENSAJES_EMAIL);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set(null);
    const { email, contrasena } = this.formulario.getRawValue();

    this.auth.iniciarSesion({ email: email.trim().toLowerCase(), contrasena }).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.mostrarError(error);
      },
    });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.codigo === CODIGOS_API.CREDENCIALES_INVALIDAS) {
      this.errorGeneral.set(MENSAJE_CREDENCIALES_INCORRECTAS);
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
