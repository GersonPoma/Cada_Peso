import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MontoPipe } from '../../../shared/formato/monto.pipe';

type EstadoListo = 'positivo' | 'cero' | 'negativo';

const PRESENTACION: Record<EstadoListo, { icono: string; texto: string }> = {
  positivo: { icono: 'check_circle', texto: 'Listo para asignar' },
  cero: { icono: 'done_all', texto: 'Todo asignado' },
  negativo: { icono: 'error', texto: 'Asignaste de más' },
};

/**
 * Tarjeta destacada con el "Listo para asignar" del mes. Cada estado lleva su ícono y su texto,
 * así no depende solo del color.
 */
@Component({
  selector: 'app-resumen-listo-para-asignar',
  imports: [MatIcon, MontoPipe],
  templateUrl: './resumen-listo-para-asignar.component.html',
  styleUrl: './resumen-listo-para-asignar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResumenListoParaAsignarComponent {
  /** Milésimas. */
  readonly listoParaAsignar = input.required<number>();
  readonly moneda = input.required<string>();

  protected readonly estado = computed<EstadoListo>(() => {
    const valor = this.listoParaAsignar();
    return valor > 0 ? 'positivo' : valor < 0 ? 'negativo' : 'cero';
  });

  protected readonly presentacion = computed(() => PRESENTACION[this.estado()]);
}
