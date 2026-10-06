import { TransaccionResponse } from './transaccion-response.model';

/** Página de la lista (`PaginaResponse<TransaccionResponse>` del backend). */
export interface PaginaTransacciones {
  contenido: TransaccionResponse[];
  /** Desde 0. */
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}
