import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { MetaMesResponse } from '../models/metas-mes-response.model';
import { presentacionMeta } from '../services/presentacion-meta';

/**
 * Estado de la meta de una categoría en el mes, debajo de su nombre: texto con ícono, la
 * necesidad del mes y una barra con `asignado / necesidad`. El color acompaña al texto, nunca lo
 * reemplaza.
 */
@Component({
  selector: 'app-indicador-meta',
  imports: [MatIcon, MatProgressBar, MontoPipe],
  templateUrl: './indicador-meta.component.html',
  styleUrl: './indicador-meta.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IndicadorMetaComponent {
  readonly meta = input.required<MetaMesResponse>();
  readonly moneda = input.required<string>();
  /** El mes mostrado es anterior al actual: `Falta` se ve en tono neutro. */
  readonly mesPasado = input(false);

  protected readonly presentacion = computed(() =>
    presentacionMeta(this.meta(), this.mesPasado()),
  );
}
