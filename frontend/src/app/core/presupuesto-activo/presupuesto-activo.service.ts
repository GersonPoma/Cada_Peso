import { Injectable, signal } from '@angular/core';
import { PresupuestoActivo } from './presupuesto-activo.model';

/**
 * Presupuesto actual de la app. Lo fija el layout del presupuesto según la URL y lo leen las
 * features (cuentas, categorías, transacciones) para saber el id y la moneda, sin depender de la
 * feature de presupuestos. Se vacía al cerrar la sesión.
 */
@Injectable({ providedIn: 'root' })
export class PresupuestoActivoService {
  private readonly estado = signal<PresupuestoActivo | null>(null);

  readonly presupuesto = this.estado.asReadonly();

  fijar(presupuesto: PresupuestoActivo): void {
    this.estado.set({ id: presupuesto.id, nombre: presupuesto.nombre, moneda: presupuesto.moneda });
  }

  limpiar(): void {
    this.estado.set(null);
  }
}
