import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatIcon } from '@angular/material/icon';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatRow,
  MatRowDef,
  MatTable,
} from '@angular/material/table';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { EstadoFila, VistaPrevia } from '../models/vista-previa-response.model';
import {
  FILTROS_ESTADO,
  FiltroEstado,
  PRESENTACION_ESTADO,
  TAMANOS_PAGINA_VISTA_PREVIA,
  TAMANO_PAGINA_POR_DEFECTO,
  textosTotales,
} from '../services/presentacion-importacion';

/**
 * Paso 3: qué pasaría con cada fila. Totales, filtro por estado y tabla paginada EN EL CLIENTE (el
 * servidor devuelve todas las filas, hasta 5000). Fechas y montos con el formato regional y la
 * moneda del presupuesto; el estado con texto e ícono. La tabla se desplaza dentro de su región.
 */
@Component({
  selector: 'app-paso-vista-previa',
  imports: [
    FechaPipe,
    MatButton,
    MatButtonToggle,
    MatButtonToggleGroup,
    MatCell,
    MatCellDef,
    MatColumnDef,
    MatHeaderCell,
    MatHeaderCellDef,
    MatHeaderRow,
    MatHeaderRowDef,
    MatIcon,
    MatPaginator,
    MatRow,
    MatRowDef,
    MatTable,
    MontoPipe,
  ],
  templateUrl: './paso-vista-previa.component.html',
  styleUrl: './paso-vista-previa.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasoVistaPreviaComponent {
  readonly vistaPrevia = input.required<VistaPrevia>();
  readonly moneda = input.required<string>();

  readonly volver = output<void>();
  readonly continuar = output<void>();

  protected readonly columnas = [
    'fila',
    'fecha',
    'monto',
    'beneficiario',
    'memo',
    'estado',
    'motivo',
  ];
  protected readonly filtros = FILTROS_ESTADO;
  protected readonly tamanos = TAMANOS_PAGINA_VISTA_PREVIA;

  protected readonly filtro = signal<FiltroEstado>('TODAS');
  protected readonly indicePagina = signal(0);
  protected readonly tamanoPagina = signal<number>(TAMANO_PAGINA_POR_DEFECTO);

  protected readonly totales = computed(() => textosTotales(this.vistaPrevia().totales));

  /** Las filas del estado elegido. */
  protected readonly filtradas = computed(() => {
    const filtro = this.filtro();
    const filas = this.vistaPrevia().filas;
    return filtro === 'TODAS' ? filas : filas.filter((fila) => fila.estado === filtro);
  });

  /** La página visible: se recorta en el cliente, sin pedir nada al servidor. */
  protected readonly visibles = computed(() => {
    const inicio = this.indicePagina() * this.tamanoPagina();
    return this.filtradas().slice(inicio, inicio + this.tamanoPagina());
  });

  protected presentar(estado: EstadoFila): { texto: string; icono: string; clase: string } {
    return PRESENTACION_ESTADO[estado];
  }

  protected cambiarFiltro(filtro: FiltroEstado): void {
    this.filtro.set(filtro);
    this.indicePagina.set(0);
  }

  protected paginar(evento: PageEvent): void {
    this.tamanoPagina.set(evento.pageSize);
    this.indicePagina.set(evento.pageIndex);
  }
}
