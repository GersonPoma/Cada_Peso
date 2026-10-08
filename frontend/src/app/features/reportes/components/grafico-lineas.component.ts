import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { textoMes } from '../../../shared/fecha/mes';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import {
  escalaVertical,
  pasoRotulos,
  posicionY,
  posicionesX,
  textoEje,
  textoMesCorto,
} from '../services/formato-reporte';
import { LIENZO } from './grafico-barras-mensuales.component';

/** Trazo y marcador de una serie: la distinguen sin depender del color. */
export type TrazoLinea = 'solido' | 'discontinuo' | 'punteado';
export type MarcadorLinea = 'circulo' | 'cuadrado' | 'triangulo';

export interface SerieLineas {
  nombre: string;
  /** Milésimas, una por mes. */
  valores: number[];
  trazo: TrazoLinea;
  marcador: MarcadorLinea;
}

const ANCHO_UTIL = LIENZO.ancho - LIENZO.izquierda - LIENZO.derecha;
const ALTO_UTIL = LIENZO.alto - LIENZO.arriba - LIENZO.abajo;

/**
 * Líneas mensuales en SVG propio: una por serie, con trazo y marcador distintos y leyenda en
 * texto; la línea del cero se resalta cuando hay valores negativos. Un solo mes dibuja un punto.
 * `role="img"` con su descripción y un `<title>` por punto; la tabla la pone la pestaña.
 */
@Component({
  selector: 'app-grafico-lineas',
  templateUrl: './grafico-lineas.component.html',
  styleUrl: './grafico-lineas.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraficoLineasComponent {
  readonly meses = input.required<string[]>();
  readonly series = input.required<SerieLineas[]>();
  readonly moneda = input.required<string>();
  readonly descripcion = input.required<string>();

  protected readonly lienzo = LIENZO;
  private readonly monto = new MontoPipe();

  private readonly escala = computed(() =>
    escalaVertical(this.series().flatMap((serie) => serie.valores)),
  );

  private readonly xs = computed(() =>
    posicionesX(this.meses().length, LIENZO.izquierda, ANCHO_UTIL),
  );

  protected readonly hayNegativos = computed(() => this.escala().minimo < 0);
  protected readonly ceroY = computed(() => this.y(0));

  protected readonly marcas = computed(() =>
    this.escala().marcas.map((valor) => ({ y: this.y(valor), texto: textoEje(valor) })),
  );

  protected readonly rotulos = computed(() => {
    const paso = pasoRotulos(this.meses().length);
    return this.meses()
      .map((mes, i) => ({ x: this.xs()[i], texto: textoMesCorto(mes), i }))
      .filter(({ i }) => i % paso === 0);
  });

  protected readonly trazos = computed(() =>
    this.series().map((serie) => {
      const puntos = this.meses().map((mes, i) => {
        const valor = serie.valores[i] ?? 0;
        return {
          x: this.xs()[i],
          y: this.y(valor),
          titulo: `${serie.nombre}, ${textoMes(mes)}: ${this.monto.transform(
            valor,
            this.moneda(),
          )}`,
        };
      });
      return {
        serie,
        puntos,
        recorrido: puntos.map(({ x, y }) => `${x},${y}`).join(' '),
      };
    }),
  );

  /** Puntos del triángulo de un marcador centrado en `x, y`. */
  protected triangulo(x: number, y: number): string {
    return `${x},${y - 5} ${x - 5},${y + 4} ${x + 5},${y + 4}`;
  }

  private y(valor: number): number {
    return posicionY(valor, this.escala(), LIENZO.arriba, ALTO_UTIL);
  }
}
