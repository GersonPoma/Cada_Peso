import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatIcon } from '@angular/material/icon';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
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
import { MatTooltip } from '@angular/material/tooltip';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { ETIQUETAS_ESTADO, EstadoTransaccion } from '../models/estado-transaccion.model';
import { TransaccionResponse } from '../models/transaccion-response.model';
import { AccionesTransaccion, accionesDe, esTransferencia } from '../services/acciones-transaccion';

/** Acción pedida desde el menú de una fila. */
export type TipoAccion = 'editar' | 'duplicar' | 'mover' | 'aprobar' | 'estado' | 'borrar';

export interface AccionFila {
  tipo: TipoAccion;
  transaccion: TransaccionResponse;
}

const ICONOS_ESTADO: Readonly<Record<EstadoTransaccion, string>> = {
  NO_CONCILIADA: 'radio_button_unchecked',
  CONCILIADA: 'check_circle',
  RECONCILIADA: 'lock',
};

/**
 * Tabla de transacciones de una página: selección, columnas Salida y Entrada, estado, "sin
 * aprobar", insignia de transferencia y menú con las acciones que admite cada fila. Solo
 * presenta y emite: la página hace las peticiones.
 */
@Component({
  selector: 'app-tabla-transacciones',
  imports: [
    MatCell,
    MatCellDef,
    MatCheckbox,
    MatColumnDef,
    MatHeaderCell,
    MatHeaderCellDef,
    MatHeaderRow,
    MatHeaderRowDef,
    MatIcon,
    MatIconButton,
    MatMenu,
    MatMenuContent,
    MatMenuItem,
    MatMenuTrigger,
    MatRow,
    MatRowDef,
    MatTable,
    MatTooltip,
    FechaPipe,
    MontoPipe,
  ],
  templateUrl: './tabla-transacciones.component.html',
  styleUrl: './tabla-transacciones.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablaTransaccionesComponent {
  readonly transacciones = input.required<TransaccionResponse[]>();
  readonly nombresCuenta = input<ReadonlyMap<number, string>>(new Map());
  readonly nombresCategoria = input<ReadonlyMap<number, string>>(new Map());
  readonly cuentasCerradas = input<ReadonlySet<number>>(new Set());
  readonly moneda = input.required<string>();
  readonly seleccion = input<ReadonlySet<number>>(new Set());

  readonly alternarSeleccion = output<number>();
  /** `true` para seleccionar toda la página, `false` para vaciar la selección. */
  readonly seleccionarPagina = output<boolean>();
  readonly accion = output<AccionFila>();

  protected readonly columnas = [
    'seleccion',
    'fecha',
    'cuenta',
    'beneficiario',
    'categoria',
    'memo',
    'salida',
    'entrada',
    'estado',
    'acciones',
  ];
  protected readonly esTransferencia = esTransferencia;

  protected readonly todaLaPagina = computed(
    () =>
      this.transacciones().length > 0 &&
      this.transacciones().every((t) => this.seleccion().has(t.id)),
  );
  protected readonly parteDeLaPagina = computed(
    () => !this.todaLaPagina() && this.transacciones().some((t) => this.seleccion().has(t.id)),
  );

  protected acciones(transaccion: TransaccionResponse): AccionesTransaccion {
    return accionesDe(transaccion, this.cuentasCerradas().has(transaccion.cuentaId));
  }

  /** Nombre de la categoría, "Dividida: ..." con las partes, o vacío sin categoría. */
  protected categoria(transaccion: TransaccionResponse): string {
    if (transaccion.subtransacciones.length > 0) {
      const partes = transaccion.subtransacciones.map((parte) =>
        parte.categoriaId === null ? 'Sin categoría' : this.nombreCategoria(parte.categoriaId),
      );
      return `Dividida: ${partes.join(', ')}`;
    }
    return transaccion.categoriaId === null ? '' : this.nombreCategoria(transaccion.categoriaId);
  }

  protected etiquetaEstado(transaccion: TransaccionResponse): string {
    return ETIQUETAS_ESTADO[transaccion.estado];
  }

  protected iconoEstado(transaccion: TransaccionResponse): string {
    return ICONOS_ESTADO[transaccion.estado];
  }

  protected emitir(tipo: TipoAccion, transaccion: TransaccionResponse): void {
    this.accion.emit({ tipo, transaccion });
  }

  private nombreCategoria(id: number): string {
    return this.nombresCategoria().get(id) ?? 'Categoría desconocida';
  }
}
