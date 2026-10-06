import { CategoriaMesResponse } from './categoria-mes-response.model';

/** Respuesta de asignar (`AsignacionActualizadaResponse` del backend). */
export interface AsignacionActualizadaResponse {
  categoria: CategoriaMesResponse;
  listoParaAsignar: number;
}
