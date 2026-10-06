import { GrupoMesResponse } from './grupo-mes-response.model';

/**
 * El presupuesto de un mes (`MesPresupuestoResponse` del backend). Los totales suman las
 * categorías incluidas; `listoParaAsignar` considera todo el presupuesto. Montos en milésimas.
 */
export interface MesPresupuestoResponse {
  /** `yyyy-MM`. */
  mes: string;
  listoParaAsignar: number;
  totalAsignado: number;
  totalActividad: number;
  totalDisponible: number;
  grupos: GrupoMesResponse[];
}
