import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatIcon } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { mesActual, textoMes } from '../../../shared/fecha/mes';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import {
  MesMetaResponse,
  MetaCumplimientoResponse,
} from '../models/cumplimiento-metas-response.model';
import { RangoMeses } from '../models/rango-meses.model';
import { cargarReporte, mientrasActiva } from '../services/carga-reporte';
import {
  TEXTOS_TIPO_META,
  presentacionEstadoMeta,
  proporcion,
  textoPorcentaje,
} from '../services/formato-reporte';
import { MENSAJE_SIN_METAS, NOTA_HISTORIAL_METAS } from '../services/mensajes-reporte';
import { ReporteService } from '../services/reporte.service';
import { EstadoReporteComponent } from './estado-reporte.component';

/** Un porcentaje de cumplimiento listo para mostrar. */
interface Cumplimiento {
  texto: string;
  /** Ancho de la barra en por mil (tope 1000). */
  ancho: number;
  /** Más de 100 %: se asignó de más. */
  excedido: boolean;
}

/**
 * Cumplimiento de las metas: por meta, sus totales del rango y, mes a mes, necesidad, asignado,
 * gastado, disponible, faltante, estado (texto e ícono además de color) y porcentaje (`Sin
 * necesidad` si es `null`). `FALTA` en un mes pasado se lee `Faltaron`, en tono neutro.
 */
@Component({
  selector: 'app-metas-reporte',
  imports: [EstadoReporteComponent, MatIcon, MatTableModule, MontoPipe],
  templateUrl: './metas-reporte.component.html',
  styleUrl: './metas-reporte.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetasReporteComponent {
  private readonly servicio = inject(ReporteService);

  readonly presupuestoId = input.required<number>();
  readonly rango = input.required<RangoMeses>();
  readonly moneda = input.required<string>();
  readonly activa = input(true);
  /** Mes local de hoy: decide `Faltaron` en los meses pasados. */
  readonly mesLocal = input<string>(mesActual());

  protected readonly nota = NOTA_HISTORIAL_METAS;
  protected readonly sinMetas = MENSAJE_SIN_METAS;
  protected readonly tipos = TEXTOS_TIPO_META;
  protected readonly columnas = [
    'mes',
    'necesidad',
    'asignado',
    'gastado',
    'disponible',
    'faltante',
    'estado',
    'porcentaje',
  ];
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
        this.servicio.metas(p.presupuestoId, p.rango),
      ),
    ),
    { initialValue: null },
  );

  protected readonly datos = computed(() => {
    const estado = this.estado();
    return estado?.tipo === 'listo' ? estado.datos : null;
  });

  protected readonly vacio = computed(() => this.datos()?.metas.length === 0);

  protected readonly metas = computed(() =>
    (this.datos()?.metas ?? []).map((meta) => ({
      meta,
      cumplimiento: cumplimiento(meta.porcentaje),
      meses: meta.meses.map((mes) => this.mes(mes)),
    })),
  );

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }

  protected claveMeta(meta: MetaCumplimientoResponse): number {
    return meta.categoriaId;
  }

  private mes(mes: MesMetaResponse) {
    return {
      ...mes,
      presentacion: presentacionEstadoMeta(mes.estado, mes.mes, this.mesLocal()),
      textoPorcentaje: textoPorcentaje(mes.porcentaje),
    };
  }
}

function cumplimiento(porcentaje: number | null): Cumplimiento {
  return {
    texto: textoPorcentaje(porcentaje),
    ancho: porcentaje === null ? 0 : proporcion(Math.min(porcentaje, 10000), 10000),
    excedido: porcentaje !== null && porcentaje > 10000,
  };
}
