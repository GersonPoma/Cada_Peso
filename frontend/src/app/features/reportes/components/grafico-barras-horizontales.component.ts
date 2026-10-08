import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { proporcion } from '../services/formato-reporte';

/** Una fila del gráfico: el monto y su porcentaje van siempre en texto junto a la barra. */
export interface FilaBarra {
  clave: string | number;
  etiqueta: string;
  /** Marca junto a la etiqueta (ej. `Oculta`). */
  marca?: string;
  /** Milésimas; un valor negativo no tiene barra. */
  valor: number;
  /** Porcentaje ya en texto. */
  porcentaje: string;
  /** Nota en texto bajo la fila (ej. para un total negativo). */
  nota?: string;
  enlace?: { texto: string; ruta: unknown[]; queryParams: Record<string, string | number> };
}

/**
 * Barras horizontales de proporción. Cada barra es decorativa: la fila ya dice su etiqueta, su
 * monto y su porcentaje, así que la lista es su propia alternativa en texto. El ancho se calcula
 * en por mil con enteros respecto del mayor valor.
 */
@Component({
  selector: 'app-grafico-barras-horizontales',
  imports: [MontoPipe, RouterLink],
  templateUrl: './grafico-barras-horizontales.component.html',
  styleUrl: './grafico-barras-horizontales.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraficoBarrasHorizontalesComponent {
  readonly filas = input.required<FilaBarra[]>();
  readonly moneda = input.required<string>();
  /** Valor que ocupa todo el ancho; por defecto, el mayor de las filas. */
  readonly maximo = input<number | null>(null);

  private readonly referencia = computed(
    () => this.maximo() ?? Math.max(0, ...this.filas().map((fila) => fila.valor)),
  );

  /** Ancho de la barra en por mil (0 a 1000). */
  protected ancho(valor: number): number {
    return proporcion(valor, this.referencia());
  }
}
