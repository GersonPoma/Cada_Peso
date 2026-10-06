import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { deMilliunits, leerMonto } from '../../../shared/formato/milliunits';
import { MontoPipe } from '../../../shared/formato/monto.pipe';

/**
 * Asignado de una categoría, editable en el lugar. Se muestra como un botón; al pulsarlo pasa a
 * un campo con el valor seleccionado. `Enter` o salir del campo guarda, `Escape` cancela y `Tab`
 * guarda y pasa a la celda siguiente. El texto se lee con `leerMonto` (sin coma flotante, con el
 * separador de la región); vacío vale 0. Solo emite `guardar` si el valor es válido y cambió: la
 * página hace la petición y decide qué mostrar.
 */
@Component({
  selector: 'app-celda-asignado',
  imports: [ReactiveFormsModule, MatError, MatFormField, MatInput, MontoPipe],
  templateUrl: './celda-asignado.component.html',
  styleUrl: './celda-asignado.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CeldaAsignadoComponent {
  /** Milésimas. */
  readonly asignado = input.required<number>();
  readonly moneda = input.required<string>();
  /** Nombre de la categoría, para las etiquetas accesibles. */
  readonly nombre = input.required<string>();
  /** Hay un guardado en curso de esta celda: no se emite otro. */
  readonly guardando = input(false);
  /** Mensaje del backend para esta celda (un 400), si lo hay. */
  readonly errorServidor = input<string | null>(null);

  /** Nuevo asignado en milésimas. */
  readonly guardar = output<number>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly boton = viewChild<ElementRef<HTMLButtonElement>>('boton');
  private readonly entrada = viewChild<ElementRef<HTMLInputElement>>('entrada');

  protected readonly editando = signal(false);
  protected readonly texto = new FormControl('', { nonNullable: true });

  protected abrir(): void {
    this.texto.reset(deMilliunits(this.asignado()));
    this.editando.set(true);
    afterNextRender(
      () => {
        const campo = this.entrada()?.nativeElement;
        campo?.focus();
        campo?.select();
      },
      { injector: this.injector },
    );
  }

  protected alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Enter') {
      evento.preventDefault();
      if (this.confirmar()) {
        this.enfocarBoton();
      }
    } else if (evento.key === 'Escape') {
      evento.preventDefault();
      this.editando.set(false);
      this.enfocarBoton();
    } else if (evento.key === 'Tab' && !evento.shiftKey) {
      evento.preventDefault();
      if (this.confirmar()) {
        this.enfocarSiguiente();
      }
    }
  }

  /** Al salir del campo se guarda; tras `Enter`, `Tab` o `Escape` ya no está editando. */
  protected alSalir(): void {
    this.confirmar();
  }

  /** Cierra la edición y emite si el valor es válido y cambió. Devuelve si se pudo cerrar. */
  private confirmar(): boolean {
    if (!this.editando()) {
      return true;
    }
    const lectura = leerMonto(this.texto.value);
    if (lectura.estado === 'invalido') {
      this.texto.setErrors({ montoInvalido: true });
      this.texto.markAsTouched();
      return false;
    }
    const valor = lectura.estado === 'vacio' ? 0 : lectura.milliunits;
    this.editando.set(false);
    if (valor !== this.asignado() && !this.guardando()) {
      this.guardar.emit(valor);
    }
    return true;
  }

  /** Devuelve el foco al botón de esta celda cuando vuelve a pintarse. */
  private enfocarBoton(): void {
    afterNextRender(() => this.boton()?.nativeElement.focus(), { injector: this.injector });
  }

  /** Lleva el foco al botón de la siguiente celda de asignado del documento, si la hay. */
  private enfocarSiguiente(): void {
    afterNextRender(
      () => {
        const celdas = Array.from(document.querySelectorAll('app-celda-asignado'));
        const siguiente = celdas[celdas.indexOf(this.host.nativeElement) + 1];
        siguiente?.querySelector<HTMLButtonElement>('.boton-asignado')?.focus();
      },
      { injector: this.injector },
    );
  }
}
