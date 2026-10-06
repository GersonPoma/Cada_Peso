import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { merge } from 'rxjs';
import { deMilliunits } from '../formato/milliunits';
import { MensajesDeError, mensajeDeError } from '../formulario/errores-formulario';
import { evaluarMonto } from './evaluar-monto';

const MENSAJES_POR_DEFECTO: MensajesDeError = {
  montoInvalido: 'Monto no válido',
  required: 'El monto es obligatorio',
};

/** Hay una operación después del primer carácter (el primero puede ser el signo). */
const CON_OPERADOR = /.[+\-*/]/;

/**
 * Campo de monto con calculadora, reutilizable en cualquier formulario. Su `control` guarda el
 * monto en milésimas. Se puede escribir una expresión (`30+20,5`): mientras tanto se ve su
 * resultado como pista y, al salir del campo o con Enter, el texto se reemplaza por el resultado.
 * Una expresión inválida muestra "Monto no válido" sin borrar lo escrito; vacío deja `null`.
 */
@Component({
  selector: 'app-campo-monto',
  imports: [ReactiveFormsModule, MatError, MatFormField, MatHint, MatInput, MatLabel],
  templateUrl: './campo-monto.component.html',
  styleUrl: './campo-monto.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampoMontoComponent {
  /** Monto en milésimas; sus validadores (obligatorio, mayor que 0...) los pone quien lo usa. */
  readonly control = input.required<FormControl<number | null>>();
  readonly etiqueta = input('Monto');
  readonly requerido = input(false);
  /** Mensajes extra por clave de error del control (ej. `{ noPositivo: 'Debe ser mayor que 0' }`). */
  readonly mensajes = input<MensajesDeError>({});

  protected readonly texto = new FormControl('', { nonNullable: true });
  protected readonly mensaje = signal<string | null>(null);

  private readonly textoActual = toSignal(this.texto.valueChanges, { initialValue: '' });
  private enEdicion = false;
  private invalido = false;

  /** Resultado de la expresión que se está escribiendo, si tiene operadores y es válida. */
  protected readonly pista = computed(() => {
    const texto = this.textoActual().trim();
    if (!CON_OPERADOR.test(texto)) {
      return null;
    }
    const resultado = evaluarMonto(texto);
    return resultado === null ? null : deMilliunits(resultado);
  });

  constructor() {
    effect((alLimpiar) => {
      const control = this.control();
      untracked(() => {
        this.escribir(control.value);
        this.reflejarErrores();
      });
      const suscripcion = merge(control.valueChanges, control.statusChanges).subscribe(() => {
        if (!this.enEdicion) {
          this.escribir(control.value);
        }
        this.reflejarErrores();
      });
      alLimpiar(() => suscripcion.unsubscribe());
    });
  }

  protected alEscribir(): void {
    this.enEdicion = true;
  }

  /** Evalúa lo escrito y lo pasa al control (al salir del campo o con Enter). */
  protected confirmar(): void {
    this.enEdicion = false;
    const control = this.control();
    const texto = this.texto.value.trim();
    if (texto === '') {
      this.invalido = false;
      control.setValue(null);
    } else {
      const resultado = evaluarMonto(texto);
      this.invalido = resultado === null;
      if (resultado !== null) {
        control.setValue(resultado);
        this.escribir(resultado);
      }
    }
    control.markAsTouched();
    this.reflejarErrores();
  }

  private escribir(valor: number | null): void {
    this.texto.setValue(valor === null ? '' : deMilliunits(valor), { emitEvent: false });
    this.invalido = false;
  }

  /** El campo de texto muestra el error de la expresión o los del control. */
  private reflejarErrores(): void {
    const control = this.control();
    const errores = this.invalido ? { montoInvalido: true } : control.errors;
    this.texto.setErrors(errores);
    if (control.touched) {
      this.texto.markAsTouched();
    }
    this.mensaje.set(mensajeDeError(errores, { ...MENSAJES_POR_DEFECTO, ...this.mensajes() }));
  }
}
