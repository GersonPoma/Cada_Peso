import { EstadoTransaccion } from './estado-transaccion.model';
import { SubtransaccionResponse } from './subtransaccion-response.model';

/** Transacción (`TransaccionResponse` del backend). */
export interface TransaccionResponse {
  id: number;
  cuentaId: number;
  /** `LocalDate`, `yyyy-MM-dd`. */
  fecha: string;
  /** Milésimas con signo: negativo es salida, positivo es entrada. */
  monto: number;
  categoriaId: number | null;
  /** Texto del beneficiario tal como se guardó en la transacción. */
  beneficiario: string | null;
  /** Beneficiario vinculado; `null` en transacciones sin vínculo. */
  beneficiarioId: number | null;
  memo: string | null;
  estado: EstadoTransaccion;
  aprobada: boolean;
  subtransacciones: SubtransaccionResponse[];
  /** Id de la otra pata si es parte de una transferencia; `null` si no. */
  transaccionParId: number | null;
  /** Transacción programada que la generó; `null` si se creó a mano. */
  programadaId: number | null;
  fechaCreacion: string;
  fechaActualizacion: string;
}
