import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import { nombreDeMoneda } from '../../../shared/formato/nombre-moneda';
import { PerfilResponse } from '../models/perfil-response.model';

/**
 * Datos de la persona en solo lectura: correo, apellido, fecha de nacimiento, teléfono, moneda
 * predeterminada y el presupuesto actual. Nada aquí se edita (el nombre tiene su formulario).
 */
@Component({
  selector: 'app-datos-perfil',
  imports: [FechaPipe],
  templateUrl: './datos-perfil.component.html',
  styleUrl: './datos-perfil.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DatosPerfilComponent {
  readonly perfil = input.required<PerfilResponse>();

  private readonly presupuestoActivo = inject(PresupuestoActivoService);

  protected readonly presupuesto = this.presupuestoActivo.presupuesto;
  protected readonly moneda = computed(() => nombreDeMoneda(this.perfil().monedaPredeterminada));
}
