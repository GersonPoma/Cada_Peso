import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { EstadoCarga } from '../models/estado-carga.model';
import { MENSAJE_SIN_MOVIMIENTOS } from '../services/mensajes-reporte';

/**
 * Envoltorio de un reporte: indicador de carga, aviso de vacío y error con `Reintentar` (o
 * `Recargar` si el presupuesto ya no existe). Con datos, muestra su contenido proyectado.
 */
@Component({
  selector: 'app-estado-reporte',
  imports: [MatButton, MatProgressSpinner],
  templateUrl: './estado-reporte.component.html',
  styleUrl: './estado-reporte.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EstadoReporteComponent {
  private readonly documento = inject(DOCUMENT);

  readonly estado = input.required<EstadoCarga<unknown> | null>();
  /** Con datos, si el reporte no tiene nada que mostrar. */
  readonly vacio = input(false);
  readonly mensajeVacio = input(MENSAJE_SIN_MOVIMIENTOS);
  readonly reintentar = output<void>();

  protected readonly vista = computed(() => {
    const estado = this.estado();
    if (estado === null) {
      return 'nada';
    }
    if (estado.tipo === 'listo') {
      return this.vacio() ? 'vacio' : 'listo';
    }
    return estado.tipo;
  });

  protected readonly aviso = computed(() => {
    const estado = this.estado();
    return estado?.tipo === 'error' ? estado.aviso : null;
  });

  protected recargar(): void {
    this.documento.defaultView?.location.reload();
  }
}
