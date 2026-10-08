import { TransaccionResponse } from './transaccion-response.model';

/** Transferencia (`TransferenciaResponse` del backend): sus dos patas enlazadas. */
export interface TransferenciaResponse {
  /** Pata de la cuenta origen (monto negativo). */
  salida: TransaccionResponse;
  /** Pata de la cuenta destino (monto positivo). */
  entrada: TransaccionResponse;
}
