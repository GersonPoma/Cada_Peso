import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatTableModule } from '@angular/material/table';
import { textoMes } from '../../../shared/fecha/mes';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { RangoMeses } from '../models/rango-meses.model';
import { cargarReporte, mientrasActiva } from '../services/carga-reporte';
import { NOTA_SALDO_INICIAL_INGRESOS } from '../services/mensajes-reporte';
import { textoRango } from '../services/rango-reporte';
import { ReporteService } from '../services/reporte.service';
import { EstadoReporteComponent } from './estado-reporte.component';
import { GraficoBarrasMensualesComponent, SerieBarras } from './grafico-barras-mensuales.component';

/**
 * Ingresos contra gastos: barras mensuales de ingresos (lisas) y gastos (rayadas) con el neto
 * como línea, y la tabla mes a mes con la fila `Total` de la API. Un neto negativo dice
 * `Déficit` además del color.
 */
@Component({
  selector: 'app-ingresos-gastos-reporte',
  imports: [EstadoReporteComponent, GraficoBarrasMensualesComponent, MatTableModule, MontoPipe],
  templateUrl: './ingresos-gastos-reporte.component.html',
  styleUrl: './ingresos-gastos-reporte.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IngresosGastosReporteComponent {
  private readonly servicio = inject(ReporteService);

  readonly presupuestoId = input.required<number>();
  readonly rango = input.required<RangoMeses>();
  readonly moneda = input.required<string>();
  readonly activa = input(true);

  protected readonly nota = NOTA_SALDO_INICIAL_INGRESOS;
  protected readonly columnas = ['mes', 'ingresos', 'gastos', 'neto'];
  protected readonly textoMes = textoMes;
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
        this.servicio.ingresosGastos(p.presupuestoId, p.rango),
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
    return datos !== null && datos.ingresos === 0 && datos.gastos === 0;
  });

  protected readonly meses = computed(() => (this.datos()?.meses ?? []).map((m) => m.mes));

  protected readonly series = computed<SerieBarras[]>(() => {
    const meses = this.datos()?.meses ?? [];
    return [
      { nombre: 'Ingresos', valores: meses.map((m) => m.ingresos), patron: 'solido' },
      { nombre: 'Gastos', valores: meses.map((m) => m.gastos), patron: 'rayado' },
    ];
  });

  protected readonly neto = computed(() => ({
    nombre: 'Neto',
    valores: (this.datos()?.meses ?? []).map((m) => m.neto),
  }));

  protected readonly descripcion = computed(
    () =>
      `Ingresos contra gastos por mes, ${textoRango(this.rango())}. ` +
      'Las cifras están en la tabla siguiente.',
  );

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }
}
