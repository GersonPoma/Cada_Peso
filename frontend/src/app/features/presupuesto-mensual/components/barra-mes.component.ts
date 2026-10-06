import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MES_MAXIMO, MES_MINIMO, mesActual, sumarMeses, textoMes } from '../services/mes';

/** Barra para cambiar de mes: anterior, el mes en texto largo, siguiente y "Hoy". */
@Component({
  selector: 'app-barra-mes',
  imports: [MatButton, MatIcon, MatIconButton],
  templateUrl: './barra-mes.component.html',
  styleUrl: './barra-mes.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarraMesComponent {
  /** Mes mostrado, `yyyy-MM`. */
  readonly mes = input.required<string>();

  /** Emite el mes al que hay que ir. */
  readonly cambiar = output<string>();

  protected readonly texto = computed(() => textoMes(this.mes()));
  protected readonly esPrimero = computed(() => this.mes() <= MES_MINIMO);
  protected readonly esUltimo = computed(() => this.mes() >= MES_MAXIMO);

  protected anterior(): void {
    if (!this.esPrimero()) {
      this.cambiar.emit(sumarMeses(this.mes(), -1));
    }
  }

  protected siguiente(): void {
    if (!this.esUltimo()) {
      this.cambiar.emit(sumarMeses(this.mes(), 1));
    }
  }

  protected hoy(): void {
    this.cambiar.emit(mesActual());
  }
}
