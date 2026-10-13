import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MENSAJE_ERROR_TARJETA } from '../services/mensajes-inicio';
import { EstadoTarjeta } from '../services/cargador-tarjeta';

/**
 * Marco de una tarjeta de resumen: región con nombre, estado de carga y de error con `Reintentar`.
 * El contenido (cifras, vacío y enlaces) lo proyecta la página cuando el estado es `listo`.
 */
@Component({
  selector: 'app-tarjeta-resumen',
  imports: [MatButton, MatIcon, MatProgressSpinner],
  templateUrl: './tarjeta-resumen.component.html',
  styleUrl: './tarjeta-resumen.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TarjetaResumenComponent {
  readonly titulo = input.required<string>();
  /** Identifica la tarjeta: arma el `id` del título al que apunta `aria-labelledby`. */
  readonly clave = input.required<string>();
  readonly estado = input.required<EstadoTarjeta>();

  readonly reintentar = output<void>();

  protected readonly idTitulo = computed(() => `titulo-tarjeta-${this.clave()}`);
  protected readonly mensajeError = MENSAJE_ERROR_TARJETA;
}
