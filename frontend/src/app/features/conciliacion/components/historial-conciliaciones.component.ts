import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { ConciliacionResponse } from '../models/conciliacion-response.model';

/** Estado de la carga del historial, independiente del resto de la pantalla. */
export type EstadoHistorial = 'cargando' | 'listo' | 'error';

/**
 * Historial de conciliaciones de una cuenta, en el orden recibido (la API lo da de la más
 * reciente a la más antigua): fecha, saldo del extracto, ajuste (`—` si no hubo) y cantidad
 * reconciliada. En pantallas estrechas cada conciliación se apila.
 */
@Component({
  selector: 'app-historial-conciliaciones',
  imports: [FechaPipe, MatButton, MatProgressSpinner, MontoPipe],
  templateUrl: './historial-conciliaciones.component.html',
  styleUrl: './historial-conciliaciones.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistorialConciliacionesComponent {
  readonly conciliaciones = input.required<ConciliacionResponse[]>();
  readonly estado = input.required<EstadoHistorial>();
  /** Código ISO 4217 de la moneda del presupuesto. */
  readonly moneda = input.required<string>();
  /** Se pidió volver a cargar después de un error. */
  readonly reintentar = output<void>();
}
