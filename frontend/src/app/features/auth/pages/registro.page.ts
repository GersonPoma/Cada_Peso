import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatError, MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { CabeceraComponent } from '../../../shared/cabecera/cabecera.component';
import { aFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import {
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { maxBytesUtf8 } from '../../../shared/validacion/max-bytes-utf8.validator';
import { mayorDeEdad } from '../../../shared/validacion/mayor-de-edad.validator';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { CampoContrasenaComponent } from '../components/campo-contrasena.component';
import { RegistroRequest } from '../models/registro-request.model';
import { AuthService } from '../services/auth.service';

const MENSAJE_EMAIL_REGISTRADO = 'Ya existe una cuenta con ese email';

/** Monedas ofrecidas al registrarse; todas las acepta `@MonedaValida` del backend. */
const MONEDAS = [
  { codigo: 'BOB', nombre: 'Boliviano' },
  { codigo: 'USD', nombre: 'Dólar estadounidense' },
  { codigo: 'EUR', nombre: 'Euro' },
  { codigo: 'ARS', nombre: 'Peso argentino' },
  { codigo: 'BRL', nombre: 'Real brasileño' },
  { codigo: 'CLP', nombre: 'Peso chileno' },
  { codigo: 'PEN', nombre: 'Sol peruano' },
] as const;

const MENSAJES_EMAIL: MensajesDeError = {
  required: 'El email es obligatorio',
  maxlength: 'El email no puede superar 254 caracteres',
  email: 'Ingresa un email válido',
};

const MENSAJES_NOMBRE: MensajesDeError = {
  required: 'El nombre es obligatorio',
  minlength: 'El nombre debe tener al menos 2 caracteres',
  maxlength: 'El nombre no puede superar 100 caracteres',
};

const MENSAJES_APELLIDO: MensajesDeError = {
  required: 'El apellido es obligatorio',
  minlength: 'El apellido debe tener al menos 2 caracteres',
  maxlength: 'El apellido no puede superar 100 caracteres',
};

const MENSAJES_FECHA: MensajesDeError = {
  matDatepickerParse: 'La fecha no es válida',
  required: 'La fecha de nacimiento es obligatoria',
  mayorDeEdad: 'Debe tener 18 años o más',
};

const MENSAJES_TELEFONO: MensajesDeError = {
  maxlength: 'El teléfono no puede superar 20 caracteres',
};

/** Registro de una cuenta nueva; replica las validaciones del backend. */
@Component({
  selector: 'app-registro',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButton,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatError,
    MatFormField,
    MatInput,
    MatLabel,
    MatOption,
    MatSelect,
    MatSuffix,
    CabeceraComponent,
    CampoContrasenaComponent,
  ],
  templateUrl: './registro.page.html',
  styleUrl: './registro.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistroPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly monedas = MONEDAS;

  protected readonly formulario = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        sobreTextoRecortado(Validators.maxLength(254)),
        sobreTextoRecortado(Validators.email),
      ],
    }),
    // La contraseña nunca se recorta: solo `required` ignora los espacios de los extremos.
    contrasena: new FormControl('', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        Validators.minLength(8),
        maxBytesUtf8(72),
      ],
    }),
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        sobreTextoRecortado(Validators.minLength(2)),
        sobreTextoRecortado(Validators.maxLength(100)),
      ],
    }),
    apellido: new FormControl('', {
      nonNullable: true,
      validators: [
        sobreTextoRecortado(Validators.required),
        sobreTextoRecortado(Validators.minLength(2)),
        sobreTextoRecortado(Validators.maxLength(100)),
      ],
    }),
    fechaNacimiento: new FormControl<Date | null>(null, [Validators.required, mayorDeEdad()]),
    telefono: new FormControl('', {
      nonNullable: true,
      validators: [sobreTextoRecortado(Validators.maxLength(20))],
    }),
    monedaPredeterminada: new FormControl('BOB', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected readonly enviando = signal(false);

  protected mensajeEmail(): string | null {
    return mensajeDeError(this.formulario.controls.email.errors, MENSAJES_EMAIL);
  }

  protected mensajeNombre(): string | null {
    return mensajeDeError(this.formulario.controls.nombre.errors, MENSAJES_NOMBRE);
  }

  protected mensajeApellido(): string | null {
    return mensajeDeError(this.formulario.controls.apellido.errors, MENSAJES_APELLIDO);
  }

  protected mensajeFecha(): string | null {
    return mensajeDeError(this.formulario.controls.fechaNacimiento.errors, MENSAJES_FECHA);
  }

  protected mensajeTelefono(): string | null {
    return mensajeDeError(this.formulario.controls.telefono.errors, MENSAJES_TELEFONO);
  }

  protected enviar(): void {
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);

    this.auth.registrar(this.armarSolicitud()).subscribe({
      next: () => void this.router.navigateByUrl('/'),
      error: (error: unknown) => {
        this.enviando.set(false);
        this.mostrarError(error);
      },
    });
  }

  private armarSolicitud(): RegistroRequest {
    const valores = this.formulario.getRawValue();
    const telefono = valores.telefono.trim();
    return {
      email: valores.email.trim().toLowerCase(),
      contrasena: valores.contrasena,
      nombre: valores.nombre.trim(),
      apellido: valores.apellido.trim(),
      fechaNacimiento: aFechaNegocio(valores.fechaNacimiento) as string,
      ...(telefono ? { telefono } : {}),
      monedaPredeterminada: valores.monedaPredeterminada,
    };
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.codigo === CODIGOS_API.EMAIL_YA_REGISTRADO) {
      aplicarErroresDeCampos(this.formulario, { email: MENSAJE_EMAIL_REGISTRADO });
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
