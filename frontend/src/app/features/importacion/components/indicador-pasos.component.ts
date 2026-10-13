import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';

/**
 * Los pasos del asistente: el actual lleva `aria-current="step"` y los hechos, un ícono de
 * verificación. Solo presenta; la página decide en qué paso está.
 */
@Component({
  selector: 'app-indicador-pasos',
  imports: [MatIcon],
  templateUrl: './indicador-pasos.component.html',
  styleUrl: './indicador-pasos.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IndicadorPasosComponent {
  readonly pasos = input.required<readonly string[]>();
  /** Índice del paso actual, desde 0. */
  readonly actual = input.required<number>();
}
