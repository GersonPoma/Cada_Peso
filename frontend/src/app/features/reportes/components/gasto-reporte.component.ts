import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import {
  CategoriaGastoResponse,
  GastoPorCategoriaResponse,
} from '../models/gasto-por-categoria-response.model';
import { RangoMeses } from '../models/rango-meses.model';
import { cargarReporte, mientrasActiva } from '../services/carga-reporte';
import { textoPorcentaje } from '../services/formato-reporte';
import { NOTA_PAGOS_TARJETA, NOTA_REEMBOLSOS } from '../services/mensajes-reporte';
import { fechasDelRango, textoRango } from '../services/rango-reporte';
import { ReporteService } from '../services/reporte.service';
import { EstadoReporteComponent } from './estado-reporte.component';
import {
  FilaBarra,
  GraficoBarrasHorizontalesComponent,
} from './grafico-barras-horizontales.component';

/**
 * Gasto por categoría del rango: total, cada grupo con sus categorías en barras horizontales
 * (con monto y porcentaje en texto) y el gasto sin categoría aparte. Los porcentajes son los de
 * la API, sin recalcular. Pide el reporte solo mientras su pestaña está activa.
 */
@Component({
  selector: 'app-gasto-reporte',
  imports: [EstadoReporteComponent, GraficoBarrasHorizontalesComponent, MontoPipe],
  templateUrl: './gasto-reporte.component.html',
  styleUrl: './gasto-reporte.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GastoReporteComponent {
  private readonly servicio = inject(ReporteService);

  readonly presupuestoId = input.required<number>();
  readonly rango = input.required<RangoMeses>();
  readonly moneda = input.required<string>();
  readonly activa = input(true);

  protected readonly notaPagos = NOTA_PAGOS_TARJETA;
  private readonly intento = signal(0);

  private readonly parametros = mientrasActiva(
    this.activa,
    computed(() => ({
      presupuestoId: this.presupuestoId(),
      rango: this.rango(),
      intento: this.intento(),
    })),
  );

  protected readonly estado = toSignal(
    toObservable(this.parametros).pipe(
      cargarReporte((p: { presupuestoId: number; rango: RangoMeses }) =>
        this.servicio.gastoPorCategoria(p.presupuestoId, p.rango),
      ),
    ),
    { initialValue: null },
  );

  protected readonly datos = computed(() => {
    const estado = this.estado();
    return estado?.tipo === 'listo' ? estado.datos : null;
  });

  protected readonly vacio = computed(() => {
    const datos = this.datos();
    return datos !== null && esVacio(datos);
  });

  /** El mayor gasto positivo: ocupa todo el ancho de su barra. */
  protected readonly maximoBarras = computed(() => {
    const datos = this.datos();
    if (datos === null) {
      return 0;
    }
    return Math.max(
      0,
      datos.sinCategoria.total,
      ...datos.grupos.flatMap((g) => g.categorias.map((c) => c.total)),
    );
  });

  protected readonly grupos = computed(() =>
    (this.datos()?.grupos ?? []).map((grupo) => ({
      grupo,
      porcentaje: textoPorcentaje(grupo.porcentaje),
      filas: grupo.categorias.map((categoria) => this.fila(categoria)),
    })),
  );

  protected readonly filaSinCategoria = computed<FilaBarra[]>(() => {
    const datos = this.datos();
    if (datos === null) {
      return [];
    }
    return [
      {
        clave: 'sin-categoria',
        etiqueta: 'Sin categoría',
        valor: datos.sinCategoria.total,
        porcentaje: textoPorcentaje(datos.sinCategoria.porcentaje),
        nota: datos.sinCategoria.total < 0 ? NOTA_REEMBOLSOS : undefined,
      },
    ];
  });

  protected readonly textoDelRango = computed(() => textoRango(this.rango()));

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }

  private fila(categoria: CategoriaGastoResponse): FilaBarra {
    return {
      clave: categoria.categoriaId,
      etiqueta: categoria.nombre,
      marca: categoria.oculta ? 'Oculta' : undefined,
      valor: categoria.total,
      porcentaje: textoPorcentaje(categoria.porcentaje),
      nota: categoria.total < 0 ? NOTA_REEMBOLSOS : undefined,
      enlace: {
        texto: 'Ver transacciones',
        ruta: ['/presupuestos', this.presupuestoId(), 'transacciones'],
        queryParams: { categoriaId: categoria.categoriaId, ...fechasDelRango(this.rango()) },
      },
    };
  }
}

function esVacio(datos: GastoPorCategoriaResponse): boolean {
  return datos.total === 0 && datos.grupos.length === 0 && datos.sinCategoria.total === 0;
}
