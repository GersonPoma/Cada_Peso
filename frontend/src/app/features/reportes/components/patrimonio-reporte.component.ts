import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatTableModule } from '@angular/material/table';
import { textoMes } from '../../../shared/fecha/mes';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { RangoMeses } from '../models/rango-meses.model';
import { cargarReporte, mientrasActiva } from '../services/carga-reporte';
import { NOTA_PATRIMONIO, NOTA_SALDO_INICIAL } from '../services/mensajes-reporte';
import { textoRango } from '../services/rango-reporte';
import { ReporteService } from '../services/reporte.service';
import { EstadoReporteComponent } from './estado-reporte.component';
import { GraficoLineasComponent, SerieLineas } from './grafico-lineas.component';

/**
 * Patrimonio al cierre de cada mes: líneas de patrimonio, activos y pasivos (con trazos y
 * marcadores distintos) y su tabla, con las notas sobre qué cuentas entran y el saldo inicial.
 */
@Component({
  selector: 'app-patrimonio-reporte',
  imports: [EstadoReporteComponent, GraficoLineasComponent, MatTableModule, MontoPipe],
  templateUrl: './patrimonio-reporte.component.html',
  styleUrl: './patrimonio-reporte.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatrimonioReporteComponent {
  private readonly servicio = inject(ReporteService);

  readonly presupuestoId = input.required<number>();
  readonly rango = input.required<RangoMeses>();
  readonly moneda = input.required<string>();
  readonly activa = input(true);

  protected readonly notas = [NOTA_PATRIMONIO, NOTA_SALDO_INICIAL];
  protected readonly columnas = ['mes', 'activos', 'pasivos', 'patrimonio'];
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
        this.servicio.patrimonio(p.presupuestoId, p.rango),
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
    return (
      datos !== null &&
      datos.meses.every((m) => m.activos === 0 && m.pasivos === 0 && m.patrimonio === 0)
    );
  });

  protected readonly meses = computed(() => (this.datos()?.meses ?? []).map((m) => m.mes));

  protected readonly series = computed<SerieLineas[]>(() => {
    const meses = this.datos()?.meses ?? [];
    return [
      {
        nombre: 'Patrimonio',
        valores: meses.map((m) => m.patrimonio),
        trazo: 'solido',
        marcador: 'circulo',
      },
      {
        nombre: 'Activos',
        valores: meses.map((m) => m.activos),
        trazo: 'discontinuo',
        marcador: 'cuadrado',
      },
      {
        nombre: 'Pasivos',
        valores: meses.map((m) => m.pasivos),
        trazo: 'punteado',
        marcador: 'triangulo',
      },
    ];
  });

  protected readonly descripcion = computed(
    () =>
      `Patrimonio, activos y pasivos al cierre de cada mes, ${textoRango(this.rango())}. ` +
      'Las cifras están en la tabla siguiente.',
  );

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }
}
