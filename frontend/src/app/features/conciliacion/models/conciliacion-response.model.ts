/** Una conciliación del historial (`ConciliacionResponse` del backend). Montos en milésimas. */
export interface ConciliacionResponse {
  id: number;
  cuentaId: number;
  /** Fecha del extracto, `yyyy-MM-dd`. */
  fecha: string;
  saldoExtracto: number;
  /** Monto del ajuste creado; `0` si no hubo. */
  ajuste: number;
  transaccionAjusteId: number | null;
  cantidadReconciliadas: number;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaCreacion: string;
}
