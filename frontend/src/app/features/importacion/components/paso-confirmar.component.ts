import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatIcon } from '@angular/material/icon';
import { TotalesVistaPrevia } from '../models/vista-previa-response.model';
import {
  MENSAJE_FILAS_INVALIDAS,
  MENSAJE_SIN_FILAS_NUEVAS,
} from '../services/mensajes-importacion';
import { textosTotales } from '../services/presentacion-importacion';

/**
 * Paso 4: confirmar. Resume los totales de la vista previa vigente. Con filas inválidas el
 * servidor rechaza todo (422) salvo `omitirInvalidas`, así que `Importar` espera a que la persona
 * marque omitirlas; sin filas nuevas no hay nada que importar. Emite `importar` con ese valor.
 */
@Component({
  selector: 'app-paso-confirmar',
  imports: [MatButton, MatCheckbox, MatIcon],
  templateUrl: './paso-confirmar.component.html',
  styleUrl: './paso-confirmar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasoConfirmarComponent {
  readonly totales = input.required<TotalesVistaPrevia>();
  readonly enviando = input(false);
  /** Mensaje del último intento fallido, ya redactado por la página. */
  readonly error = input<string | null>(null);

  readonly importar = output<boolean>();
  readonly volver = output<void>();

  protected readonly omitir = signal(false);
  protected readonly mensajeInvalidas = MENSAJE_FILAS_INVALIDAS;
  protected readonly mensajeSinNuevas = MENSAJE_SIN_FILAS_NUEVAS;

  protected readonly textos = computed(() => textosTotales(this.totales()));
  protected readonly hayInvalidas = computed(() => this.totales().invalidas > 0);
  protected readonly sinNuevas = computed(() => this.totales().nuevas === 0);
  protected readonly invalidasSinOmitir = computed(() => this.hayInvalidas() && !this.omitir());
  protected readonly puedeImportar = computed(
    () => !this.enviando() && !this.sinNuevas() && !this.invalidasSinOmitir(),
  );

  protected cambiarOmitir(valor: boolean): void {
    this.omitir.set(valor);
  }

  protected confirmar(): void {
    if (this.puedeImportar()) {
      this.importar.emit(this.hayInvalidas() && this.omitir());
    }
  }
}
