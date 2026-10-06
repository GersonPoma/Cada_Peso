/** Presupuesto en el que trabaja la persona: lo que necesitan las features que cuelgan de él. */
export interface PresupuestoActivo {
  id: number;
  nombre: string;
  /** Código ISO 4217 de la moneda del presupuesto (ej. `BOB`). */
  moneda: string;
}
