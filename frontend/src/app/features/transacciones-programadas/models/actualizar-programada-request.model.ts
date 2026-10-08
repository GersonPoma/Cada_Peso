import { FrecuenciaProgramada } from './transaccion-programada-response.model';

/**
 * Cuerpo de `PUT .../transacciones-programadas/{id}` (`ActualizarProgramadaRequest`): sin la
 * cuenta ni la fecha de inicio, que no se editan.
 */
export interface ActualizarProgramadaRequest {
  monto: number;
  categoriaId: number | null;
  beneficiario: string | null;
  memo: string | null;
  frecuencia: FrecuenciaProgramada;
  fechaFin: string | null;
}
