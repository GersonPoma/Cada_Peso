/**
 * Lo que esta feature usa de una transacción `NO_CONCILIADA` del estado de la conciliación
 * (`TransaccionResponse` del backend).
 */
export interface TransaccionNoConciliada {
  id: number;
  cuentaId: number;
  /** `LocalDate`, `yyyy-MM-dd`. */
  fecha: string;
  /** Milésimas con signo. */
  monto: number;
  beneficiario: string | null;
  estado: 'NO_CONCILIADA' | 'CONCILIADA' | 'RECONCILIADA';
}
