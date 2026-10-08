import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { textoMes } from '../../../shared/fecha/mes';
import {
  escalaVertical,
  pasoRotulos,
  posicionY,
  posicionesX,
  textoEje,
  textoMesCorto,
} from '../services/formato-reporte';

/** Una serie de barras: el relleno (además del color) la distingue de la otra. */
export interface SerieBarras {
  nombre: string;
  /** Milésimas, una por mes; un valor negativo dibuja la barra hacia abajo. */
  valores: number[];
  patron: 'solido' | 'rayado';
}

/** Una línea con marcadores sobre las barras (ej. el neto o el saldo). */
export interface SerieLinea {
  nombre: string;
  valores: number[];
}

/** Medidas del lienzo SVG (unidades del `viewBox`; el gráfico escala al ancho disponible). */
export const LIENZO = { ancho: 640, alto: 280, izquierda: 56, derecha: 12, arriba: 12, abajo: 32 };
const ANCHO_UTIL = LIENZO.ancho - LIENZO.izquierda - LIENZO.derecha;
const ALTO_UTIL = LIENZO.alto - LIENZO.arriba - LIENZO.abajo;

let siguienteId = 0;

/**
 * Barras agrupadas por mes en SVG propio, con una línea opcional. Coordenadas enteras, colores
 * de los tokens de Material, relleno rayado para no depender del color, `role="img"` con su
 * descripción y un `<title>` por barra y punto. La tabla con las mismas cifras la pone la pestaña.
 */
@Component({
  selector: 'app-grafico-barras-mensuales',
  templateUrl: './grafico-barras-mensuales.component.html',
  styleUrl: './grafico-barras-mensuales.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GraficoBarrasMensualesComponent {
  readonly meses = input.required<string[]>();
  readonly series = input.required<SerieBarras[]>();
  readonly linea = input<SerieLinea | null>(null);
  readonly moneda = input.required<string>();
  /** Descripción accesible del gráfico (reporte y rango). */
  readonly descripcion = input.required<string>();

  protected readonly lienzo = LIENZO;
  protected readonly idPatron = `rayado-${siguienteId++}`;
  private readonly monto = new MontoPipe();

  private readonly escala = computed(() =>
    escalaVertical([
      ...this.series().flatMap((serie) => serie.valores),
      ...(this.linea()?.valores ?? []),
    ]),
  );

  private readonly xs = computed(() =>
    posicionesX(this.meses().length, LIENZO.izquierda, ANCHO_UTIL),
  );

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

  protected readonly barras = computed(() => {
    const meses = this.meses();
    const series = this.series();
    const grupo = Math.floor(ANCHO_UTIL / Math.max(1, meses.length));
    const ancho = Math.max(2, Math.floor((grupo * 4) / 5 / Math.max(1, series.length)));
    return meses.flatMap((mes, i) =>
      series.map((serie, s) => {
        const valor = serie.valores[i] ?? 0;
        const y = this.y(Math.max(valor, 0));
        return {
          clave: `${mes}-${s}`,
          x: this.xs()[i] - Math.floor((series.length * ancho) / 2) + s * ancho,
          y,
          ancho,
          alto: Math.max(0, this.y(Math.min(valor, 0)) - y),
          patron: serie.patron,
          titulo: this.titulo(serie.nombre, mes, valor),
        };
      }),
    );
  });

  protected readonly puntos = computed(() => {
    const linea = this.linea();
    if (linea === null) {
      return [];
    }
    return this.meses().map((mes, i) => ({
      x: this.xs()[i],
      y: this.y(linea.valores[i] ?? 0),
      titulo: this.titulo(linea.nombre, mes, linea.valores[i] ?? 0),
    }));
  });

  protected readonly trazoLinea = computed(() =>
    this.puntos()
      .map(({ x, y }) => `${x},${y}`)
      .join(' '),
  );

  /** Texto del `<title>` de una barra o un punto: serie, mes y monto. */
  private titulo(serie: string, mes: string, valor: number): string {
    return `${serie}, ${textoMes(mes)}: ${this.monto.transform(valor, this.moneda())}`;
  }

  private y(valor: number): number {
    return posicionY(valor, this.escala(), LIENZO.arriba, ALTO_UTIL);
  }
}
