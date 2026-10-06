import { MesPresupuestoResponse } from './mes-presupuesto-response.model';

/** Datos del diálogo de mover dinero: el mes mostrado y lo que se preselecciona. */
export interface DatosDialogoMoverDinero {
  mes: MesPresupuestoResponse;
  origenId?: number;
  destinoId?: number;
  /** Monto propuesto en milésimas (por ejemplo, el sobregasto a cubrir). */
  monto?: number;
}
