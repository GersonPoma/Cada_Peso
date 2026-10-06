import { OperacionLote } from './operacion-lote.model';

/** Cuerpo de `POST /transacciones/lote`: de 1 a 100 ids. */
export interface LoteRequest {
  ids: number[];
  operacion: OperacionLote;
  categoriaId?: number;
}
