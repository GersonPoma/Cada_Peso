import { MesPresupuestoResponse } from './mes-presupuesto-response.model';

/** Cómo terminó el diálogo de mover dinero: con el mes actualizado o pidiendo recargarlo. */
export type ResultadoMoverDinero =
  { tipo: 'movido'; mes: MesPresupuestoResponse } | { tipo: 'recargar' };
