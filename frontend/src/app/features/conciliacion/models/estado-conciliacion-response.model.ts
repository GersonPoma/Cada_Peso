import { TransaccionNoConciliada } from './transaccion-no-conciliada.model';

/**
 * Estado de la conciliación de una cuenta (`EstadoConciliacionResponse` del backend). Montos en
 * milésimas; `diferencia = saldoExtracto - saldoConciliadoAlCorte`.
 */
export interface EstadoConciliacionResponse {
  cuentaId: number;
  /** Fecha del extracto, `yyyy-MM-dd`. */
  fecha: string;
  saldoExtracto: number;
  /** Saldo conciliado de la cuenta, sin límite de fecha. */
  saldoConciliado: number;
  /** Saldo conciliado hasta la fecha del extracto. */
  saldoConciliadoAlCorte: number;
  diferencia: number;
  /** Cuántas `NO_CONCILIADA` tiene la cuenta, aunque la lista venga recortada. */
  totalNoConciliadas: number;
  /** Las `NO_CONCILIADA` de la cuenta (de cualquier fecha), las más recientes, como máximo 100. */
  noConciliadas: TransaccionNoConciliada[];
}
